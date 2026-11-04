package org.interpss.dstab.hvdc;

import java.util.LinkedHashMap;
import java.util.Map;
import org.interpss.dstab.control.util.IntegrationStateAccess;
import org.interpss.dstab.control.util.IntegrationStateMonitor;
import org.interpss.dstab.control.util.StaggeredEulerParticipant;

/** Joint controller/DC-line owner with live voltage-dependent converter injections.
 * Numerical integration and explicit protection acceptance are separate operations.
 * Automatic network attachment and complete source-trajectory qualification remain separate. */
public final class Chvdc2CoupledRuntime implements StaggeredEulerParticipant,IntegrationStateAccess {
    /** Boundary order: Vr, Vi (V), Ir, Ii (A), Pr, Qr, Pi, Qi (pu into converters), fresh/used gamma (rad). */
    public static final int BOUNDARY_SIZE=10;
    private final Chvdc2ControlAssembly controls;
    private final Chvdc2DcLine line;
    private final LccConverterBoundary rectifier,inverter;
    private final double resistance,conductingResistance,shortedResistance;
    private final int count;
    private final boolean laggedCurrentMeasurement;
    private final double[] inputs=new double[6],boundary=new double[BOUNDARY_SIZE],accepted=new double[BOUNDARY_SIZE],
            rectifierValues=new double[4],inverterValues=new double[3],injections=new double[4],
            state,rate,firstRate,candidate,leftRate,acceptedState,acceptedInputs=new double[6];
    private final Checkpoint prediction,transaction,write;
    private IntegrationStateMonitor monitor;
    private String[] names;
    private boolean inputReady,initialized,pending,inTransaction,acceptedReady,intervalActive,sharedPending;
    private double pendingStep,acceptedTime,algebraicCurrent,acceptedAlgebraicCurrent;
    private double intervalSeconds,intervalAdvanced,intervalRectifierVoltage,intervalInverterVoltage;
    private double intervalRectifierCurrent,intervalInverterCurrent;
    private double sharedStep;
    private long revision,acceptedSequence=-1,latestSequence=-1;
    private static final class Checkpoint {
        final Chvdc2ControlAssembly.Checkpoint controls=new Chvdc2ControlAssembly.Checkpoint();
        final double[] states,inputs=new double[6],accepted=new double[BOUNDARY_SIZE],left,
                acceptedState,acceptedInputs=new double[6];
        double gamma,acceptedTime,algebraicCurrent,acceptedAlgebraicCurrent;long revision,acceptedSequence,latestSequence;boolean acceptedReady;
        double intervalSeconds,intervalAdvanced,intervalRectifierVoltage,intervalInverterVoltage;boolean intervalActive;
        double intervalRectifierCurrent,intervalInverterCurrent;
        double sharedStep;boolean sharedPending;
        Checkpoint(int count){states=new double[count];left=new double[count];acceptedState=new double[count];}
    }
    /** Takes exclusive ownership of the mutable line and controller components. */
    public Chvdc2CoupledRuntime(Chvdc2Parameters parameters,LccConverterBoundary rectifier,LccConverterBoundary inverter,
            Chvdc2DcLine line,double inverterTransformerRatio,double inverterTap) {
        this.rectifier=java.util.Objects.requireNonNull(rectifier);this.inverter=java.util.Objects.requireNonNull(inverter);
        this.line=java.util.Objects.requireNonNull(line);controls=parameters.prepareControls(inverterTransformerRatio,inverterTap);
        laggedCurrentMeasurement=parameters.value(Chvdc2Parameters.Constant.T_MEAS_SEC)>0;
        if(!rectifier.isRectifier()||inverter.isRectifier()||parameters.lineDynamicsEnabled()!=(line.staggeredStateCount()>0))
            throw new IllegalArgumentException("Oriented converters and the selected line-dynamics mode required");
        resistance=line.resistanceOhms();shortedResistance=resistance+rectifier.commutatingResistanceOhms();
        conductingResistance=shortedResistance+inverter.commutatingResistanceOhms();finite(conductingResistance);
        count=15+line.staggeredStateCount();state=new double[count];rate=new double[count];firstRate=new double[count];candidate=new double[count];leftRate=new double[count];acceptedState=new double[count];
        prediction=new Checkpoint(count);transaction=new Checkpoint(count);write=new Checkpoint(count);
    }
    /** Phasors are on the AC network base; reference and common auxiliary are in amperes. */
    public void setBoundary(double vrReal,double vrImag,double viReal,double viImag,double referenceAmperes,double auxiliaryAmperes) {
        finite(vrReal);finite(vrImag);finite(viReal);finite(viImag);finite(referenceAmperes);finite(auxiliaryAmperes);
        positive(Math.hypot(vrReal,vrImag));positive(Math.hypot(viReal,viImag));
        if(referenceAmperes<0)throw new IllegalArgumentException("Nonnegative DC current reference required");
        inputs[0]=vrReal;inputs[1]=vrImag;inputs[2]=viReal;inputs[3]=viImag;inputs[4]=referenceAmperes;inputs[5]=auxiliaryAmperes;inputReady=true;
    }
    /** Initialize from a consistent solved line voltage drop. Firing angles and fresh
     * extinction margin are calculated from the same prepared converter geometry. */
    public void initialize(double rectifierDcVolts,double inverterDcVolts,double time) {
        exclusive();noSharedProposal();if(inTransaction||intervalActive)throw new IllegalStateException("Finish the coupled transaction and integration interval before initialization");
        if(!inputReady)throw new IllegalStateException("Supply terminal phasors and current reference first");
        finite(time);initialized=false;line.initialize(rectifierDcVolts,inverterDcVolts);
        double ir=line.rectifierAmperes(),ii=line.inverterAmperes(),vr=Math.hypot(inputs[0],inputs[1]),vi=Math.hypot(inputs[2],inputs[3]);
        double ar=rectifier.firingAngleForDcVoltage(vr,rectifierDcVolts,ir),ai=inverter.firingAngleForDcVoltage(vi,inverterDcVolts,ii);
        double gamma=inverter.extinctionAtFiringAngle(vi,ii,ai);
        controls.setInput(ir,ii,rectifierDcVolts*.001,inverterDcVolts*.001,vr,vi,inputs[4],inputs[5],Math.toDegrees(gamma));
        controls.initialize(Math.toDegrees(ar),Math.toDegrees(ai),time);
        algebraicCurrent=ir;intervalSeconds=intervalAdvanced=intervalRectifierVoltage=intervalInverterVoltage=0;
        intervalRectifierCurrent=intervalInverterCurrent=0;
        names=new String[count];int index=0;for(String name:controls.getIntegrationStates().keySet())names[index++]=name;
        if(count==16)names[15]="Line current (A)";
        if(count==18){names[15]="Rectifier line current (A)";names[16]="Line capacitor voltage (V)";names[17]="Inverter line current (A)";}
        monitor=new IntegrationStateMonitor(names);pending=false;revision=0;acceptedReady=false;latestSequence=-1;java.util.Arrays.fill(leftRate,0);initialized=true;
        refresh();
    }
    private double heldGamma(){return pending?prediction.gamma:Math.toRadians(controls.acceptedInverterExtinctionDegrees());}
    private void refresh(){refresh(heldGamma());}
    private void refresh(double heldGamma) {
        double vr=Math.hypot(inputs[0],inputs[1]),vi=Math.hypot(inputs[2],inputs[3]);
        double ar=Math.toRadians(controls.rectifierDegrees()),ai=Math.toRadians(controls.inverterDegrees());
        double ir,ii;
        if(count==15){ir=ii=algebraicCurrent;}else{ir=line.rectifierAmperes();ii=line.inverterAmperes();}
        if(ir<0||ii<0)throw new IllegalArgumentException("Reverse DC current is not a qualified conducting boundary");
        double dcR=rectifier.dcVoltageAtFiringAngle(vr,ir,ar),dcI=controls.inverterShorted()?0:inverter.dcVoltageAtFiringAngle(vi,ii,ai);
        double gamma=inverter.extinctionAtFiringAngle(vi,ii,ai);
        rectifier.evaluateFiringAngle(vr,ir,ar,rectifierValues,0);
        double electricalInverterVoltage=inverter.maximumDcVoltage(vi,ii,heldGamma);
        inverter.evaluateDcVoltage(vi,electricalInverterVoltage,ii,inverterValues,0);
        // Line-side shorting and the held-angle AC interface are intentionally distinct.
        line.setInput(dcR,dcI);
        // Lagged current transducers sample the line current once per outer
        // interval. Zero-lag observations, converter voltage and AC injection stay live.
        boolean sampled=intervalActive&&laggedCurrentMeasurement;
        controls.setInput(sampled?intervalRectifierCurrent:ir,sampled?intervalInverterCurrent:ii,
                dcR*.001,dcI*.001,vr,vi,inputs[4],inputs[5],Math.toDegrees(gamma));
        boundary[0]=dcR;boundary[1]=dcI;boundary[2]=ir;boundary[3]=ii;
        boundary[4]=rectifierValues[1];boundary[5]=rectifierValues[2];boundary[6]=inverterValues[0];boundary[7]=inverterValues[1];
        boundary[8]=gamma;boundary[9]=heldGamma;
    }
    /** The algebraic line is evaluated at a numerical-state boundary. Network
     * iteration consumes that current and still refreshes the AC power/current
     * transformation with live phasors. It must not resample the DC line implicitly. */
    private void advanceAlgebraicCurrent(double vr,double vi) {
        double source=rectifier.dcVoltageAtFiringAngle(vr,0,Math.toRadians(controls.rectifierDegrees()));
        if(!controls.inverterShorted())source-=inverter.dcVoltageAtFiringAngle(vi,0,Math.toRadians(controls.inverterDegrees()));
        double current=source/(controls.inverterShorted()?shortedResistance:conductingResistance);
        finite(current);if(current<0)throw new IllegalArgumentException("Reverse DC current is not a qualified conducting boundary");
        algebraicCurrent=current;
    }
    /** Start an outer electrical interval before local state integration. Local
     * steps retain its algebraic current, sampled voltages and transducer currents; live AC injections
     * still refresh at every network trial. Coefficient preparation is unchanged.
     * Without this bracket, current-transducer inputs remain live and each
     * completed numerical step publishes the algebraic line. */
    public void beginIntegrationInterval(double seconds) {
        ready();exclusive();noSharedProposal();positive(seconds);
        if(intervalActive)throw new IllegalStateException("An integration interval is already open");
        intervalRectifierVoltage=Math.hypot(inputs[0],inputs[1]);intervalInverterVoltage=Math.hypot(inputs[2],inputs[3]);
        intervalRectifierCurrent=count==15?algebraicCurrent:line.rectifierAmperes();
        intervalInverterCurrent=count==15?algebraicCurrent:line.inverterAmperes();
        intervalSeconds=seconds;intervalAdvanced=0;intervalActive=true;
    }
    /** Publish the algebraic line after all local steps, before the final network
     * solve. This is a numerical boundary, not protection/electrical acceptance. */
    public void completeIntegrationInterval() {
        ready();exclusive();noSharedProposal();
        if(!intervalActive||intervalAdvanced==0||Math.abs(intervalAdvanced-intervalSeconds)>64*Math.ulp(intervalSeconds))
            throw new IllegalStateException("Complete all local integration steps before publishing the interval");
        save(write);
        try {
            if(count==15)advanceAlgebraicCurrent(intervalRectifierVoltage,intervalInverterVoltage);
            refresh();intervalActive=false;
        }catch(RuntimeException failure){restore(write);throw failure;}
    }
    private void verifyAdvance(double seconds) {
        if(intervalActive&&intervalAdvanced+seconds-intervalSeconds>64*Math.ulp(intervalSeconds))
            throw new IllegalArgumentException("Local integration exceeds the open outer interval");
    }
    /** Fresh trial injection: rectifier real/imaginary, then inverter real/imaginary.
     * Failed evaluation never writes a partial caller buffer or accepts a history. */
    public void evaluateInjections(double[] output,int offset) {
        ready();java.util.Objects.checkFromIndexSize(offset,4,output.length);refresh();
        injection(boundary[4],boundary[5],inputs[0],inputs[1],injections,0);
        injection(boundary[6],boundary[7],inputs[2],inputs[3],injections,2);System.arraycopy(injections,0,output,offset,4);
    }
    private static void injection(double p,double q,double real,double imag,double[] output,int offset) {
        double magnitude=Math.hypot(real,imag),c=real/magnitude,s=imag/magnitude;
        double ir=(-p*c-q*s)/magnitude,ii=(-p*s+q*c)/magnitude;finite(ir);finite(ii);output[offset]=ir;output[offset+1]=ii;
    }
    public void readBoundary(double[] output,int offset){ready();java.util.Objects.checkFromIndexSize(offset,BOUNDARY_SIZE,output.length);refresh();System.arraycopy(boundary,0,output,offset,BOUNDARY_SIZE);}
    /** Live controller observations followed by the numerical line coordinates.
     * Refreshes voltage-dependent inputs without advancing integration or accepting an
     * electrical/protection epoch. Use readStaggeredState for integration and rollback. */
    public void readPublishedStates(double[] output,int offset) {
        ready();buffer(output,offset);refresh();
        controls.readPublishedStates(output,offset);line.readStaggeredState(output,offset+15);
    }
    /** Frozen accepted observation retains the angle used by that electrical evaluation,
     * even after the freshly calculated margin is committed for a subsequent boundary. */
    public void readAcceptedBoundary(double[] output,int offset){ready();java.util.Objects.checkFromIndexSize(offset,BOUNDARY_SIZE,output.length);if(!acceptedReady)throw new IllegalStateException("No accepted coupled boundary");System.arraycopy(accepted,0,output,offset,BOUNDARY_SIZE);}
    /** Repeating an unchanged electrical epoch is a no-op, including its published
     * power. Changed phasors or coordinates require a new caller-owned sequence. */
    public void acceptBoundary(double time,long sequence){
        ready();exclusive();noSharedProposal();
        if(acceptedReady&&sequence==acceptedSequence){
            read(state);
            if(sequence!=latestSequence||time!=acceptedTime||algebraicCurrent!=acceptedAlgebraicCurrent||!java.util.Arrays.equals(inputs,acceptedInputs)||!java.util.Arrays.equals(state,acceptedState))
                throw new IllegalStateException("Repeated electrical acceptance has changed time, inputs, states or current history");
            return;
        }
        accept(time,sequence,true);
    }
    public void evaluatePredictorProtection(double time,long sequence){ready();if(!pending)throw new IllegalStateException("A coupled predictor is required");accept(time,sequence,false);}
    void evaluatePredictorProtection(double time,long sequence,boolean allowRaml,boolean allowCommutation){
        ready();if(!pending||!inTransaction)throw new IllegalStateException("A transactional predictor is required");
        accept(time,sequence,false,allowRaml,allowCommutation);
    }
    /** Evaluate a converged network's protection branch without publishing its
     * next-boundary electrical angle. The caller owns a transaction and must
     * either accept the final boundary or roll this working history back. */
    public void evaluateProtectionBoundary(double time,long sequence){
        evaluateProtectionBoundary(time,sequence,true,true);
    }
    void evaluateProtectionBoundary(double time,long sequence,boolean allowRaml,boolean allowCommutation){
        ready();exclusive();noSharedProposal();
        if(!inTransaction)throw new IllegalStateException("Protection preview requires a coupled transaction");
        accept(time,sequence,false,allowRaml,allowCommutation);
    }
    /** Publish the converged electrical boundary after its protection preview.
     * This cannot introduce a new regime after the final network solve. */
    void acceptNetworkBoundary(double time,long sequence){
        ready();exclusive();noSharedProposal();
        if(!inTransaction)throw new IllegalStateException("Network acceptance requires a coupled transaction");
        accept(time,sequence,true,false,false);
    }
    private void accept(double time,long sequence,boolean publishElectricalAngle) {
        accept(time,sequence,publishElectricalAngle,true,true);
    }
    private void accept(double time,long sequence,boolean publishElectricalAngle,boolean allowRaml,boolean allowCommutation) {
        save(write);
        try {
            rates();double gamma=heldGamma();long before=controls.staggeredInputDiscontinuityVersion();
            controls.acceptNetworkStage(time,sequence,publishElectricalAngle,allowRaml,allowCommutation);
            latestSequence=sequence;
            if(controls.staggeredInputDiscontinuityVersion()!=before){
                System.arraycopy(rate,0,leftRate,0,count);revision=controls.staggeredInputDiscontinuityVersion();
                if(count==15)advanceAlgebraicCurrent(Math.hypot(inputs[0],inputs[1]),Math.hypot(inputs[2],inputs[3]));
            }
            refresh(gamma);if(!pending&&publishElectricalAngle){
                System.arraycopy(boundary,0,accepted,0,BOUNDARY_SIZE);read(acceptedState);System.arraycopy(inputs,0,acceptedInputs,0,6);
                acceptedTime=time;acceptedSequence=sequence;acceptedReady=true;acceptedAlgebraicCurrent=algebraicCurrent;
            }
        }catch(RuntimeException failure){restore(write);throw failure;}
    }
    public boolean inverterShorted(){ready();return controls.inverterShorted();}
    public boolean ramlActive(){ready();return controls.ramlActive();}
    private void rates(){refresh();controls.evaluateStaggered(0,rate,0);line.evaluateStaggered(0,rate,15);}
    private void read(double[] destination){controls.readStaggeredState(destination,0);line.readStaggeredState(destination,15);}
    private void apply(double[] values,int offset,double seconds,boolean advanceLine,double vr,double vi) {
        for(int i=0;i<count;i++)finite(values[offset+i]);save(write);
        try {
            line.applyStaggeredState(values,offset+15,seconds);refresh();controls.applyStaggeredState(values,offset,seconds);
            if(count==15&&advanceLine)advanceAlgebraicCurrent(vr,vi);
            refresh();
        }
        catch(RuntimeException failure){restore(write);throw failure;}
    }
    public void step(double seconds,int stage) {
        ready();noSharedProposal();interval(seconds);if(stage!=0&&stage!=1)throw new IllegalArgumentException("Predictor or corrector required");if(seconds==0)return;
        if(stage==0&&pending||stage==1&&(!pending||seconds!=pendingStep))throw new IllegalStateException("Matching coupled integration stages required");
        verifyAdvance(seconds);
        read(state);rates();if(stage==0){save(prediction);System.arraycopy(rate,0,firstRate,0,count);}
        for(int i=0;i<count;i++)candidate[i]=stage==0?state[i]+seconds*rate[i]:prediction.states[i]+.5*seconds*(firstRate[i]+rate[i]);
        boolean advanceLine=count==15&&stage==1&&!intervalActive;
        apply(candidate,0,seconds,advanceLine,advanceLine?Math.hypot(prediction.inputs[0],prediction.inputs[1]):0,advanceLine?Math.hypot(prediction.inputs[2],prediction.inputs[3]):0);
        record(stage==0?IntegrationStateMonitor.Stage.PREDICTOR:IntegrationStateMonitor.Stage.CORRECTOR);
        if(stage==1&&intervalActive)intervalAdvanced+=seconds;
        pendingStep=seconds;pending=stage==0;
    }
    public void cancelPrediction(){ready();if(pending){restore(prediction);pending=false;}}
    public void beginTransaction(){ready();exclusive();if(inTransaction)throw new IllegalStateException("Coupled transaction already open");save(transaction);inTransaction=true;}
    public void commitTransaction(){ready();exclusive();noSharedProposal();if(!inTransaction||intervalActive)throw new IllegalStateException("A complete coupled transaction is required");inTransaction=false;}
    public void rollbackTransaction(){ready();if(!inTransaction)throw new IllegalStateException("No coupled transaction");restore(transaction);pending=false;inTransaction=false;}
    private void save(Checkpoint c) {
        controls.saveOwnerCheckpoint(c.controls);read(c.states);System.arraycopy(inputs,0,c.inputs,0,6);System.arraycopy(accepted,0,c.accepted,0,BOUNDARY_SIZE);
        System.arraycopy(leftRate,0,c.left,0,count);c.gamma=Math.toRadians(controls.acceptedInverterExtinctionDegrees());c.revision=revision;c.acceptedReady=acceptedReady;
        System.arraycopy(acceptedState,0,c.acceptedState,0,count);System.arraycopy(acceptedInputs,0,c.acceptedInputs,0,6);c.acceptedTime=acceptedTime;c.acceptedSequence=acceptedSequence;c.latestSequence=latestSequence;
        c.algebraicCurrent=algebraicCurrent;c.acceptedAlgebraicCurrent=acceptedAlgebraicCurrent;
        c.intervalActive=intervalActive;c.intervalSeconds=intervalSeconds;c.intervalAdvanced=intervalAdvanced;
        c.intervalRectifierVoltage=intervalRectifierVoltage;c.intervalInverterVoltage=intervalInverterVoltage;
        c.intervalRectifierCurrent=intervalRectifierCurrent;c.intervalInverterCurrent=intervalInverterCurrent;
        c.sharedPending=sharedPending;c.sharedStep=sharedStep;
    }
    private void restore(Checkpoint c) {
        controls.restoreOwnerCheckpoint(c.controls);line.restoreCoordinates(c.states,15);System.arraycopy(c.inputs,0,inputs,0,6);
        System.arraycopy(c.accepted,0,accepted,0,BOUNDARY_SIZE);System.arraycopy(c.left,0,leftRate,0,count);revision=c.revision;acceptedReady=c.acceptedReady;monitor.reset();
        System.arraycopy(c.acceptedState,0,acceptedState,0,count);System.arraycopy(c.acceptedInputs,0,acceptedInputs,0,6);acceptedTime=c.acceptedTime;acceptedSequence=c.acceptedSequence;latestSequence=c.latestSequence;
        algebraicCurrent=c.algebraicCurrent;acceptedAlgebraicCurrent=c.acceptedAlgebraicCurrent;
        intervalActive=c.intervalActive;intervalSeconds=c.intervalSeconds;intervalAdvanced=c.intervalAdvanced;
        intervalRectifierVoltage=c.intervalRectifierVoltage;intervalInverterVoltage=c.intervalInverterVoltage;
        intervalRectifierCurrent=c.intervalRectifierCurrent;intervalInverterCurrent=c.intervalInverterCurrent;
        sharedPending=c.sharedPending;sharedStep=c.sharedStep;
    }
    @Override public int staggeredStateCount(){return count;}
    @Override public void readStaggeredState(double[] values,int offset){ready();buffer(values,offset);read(state);System.arraycopy(state,0,values,offset,count);}
    @Override public void evaluateStaggered(double seconds,double[] values,int offset){ready();exclusive();interval(seconds);buffer(values,offset);read(state);rates();record(IntegrationStateMonitor.Stage.STAGGERED);System.arraycopy(rate,0,values,offset,count);}
    @Override public void applyStaggeredState(double[] values,int offset,double seconds){
        ready();exclusive();interval(seconds);buffer(values,offset);verifyAdvance(seconds);
        apply(values,offset,seconds,false,0,0);
        sharedPending=seconds>0&&(count==15||intervalActive);sharedStep=seconds;
    }
    /** State proposals and derivative probes do not publish algebraic current or
     * consume an outer interval. The solver completes the accepted shared step
     * once, before solving its new electrical endpoint. */
    @Override public void completeStaggeredStep(double seconds) {
        ready();exclusive();interval(seconds);if(seconds==0||count!=15&&!intervalActive)return;
        if(!sharedPending||seconds!=sharedStep)throw new IllegalStateException("A matching shared state proposal is required");
        verifyAdvance(seconds);save(write);
        try {
            if(count==15&&!intervalActive)advanceAlgebraicCurrent(Math.hypot(inputs[0],inputs[1]),Math.hypot(inputs[2],inputs[3]));
            refresh();if(intervalActive)intervalAdvanced+=seconds;sharedPending=false;
        }catch(RuntimeException failure){restore(write);throw failure;}
    }
    @Override public long staggeredInputDiscontinuityVersion(){return pending?prediction.revision:revision;}
    @Override public boolean readStaggeredInputDiscontinuityLeftRates(double[] values,int offset){ready();buffer(values,offset);if(staggeredInputDiscontinuityVersion()==0)return false;System.arraycopy(pending?prediction.left:leftRate,0,values,offset,count);return true;}
    @Override public Map<String,Double> getIntegrationStates(){ready();read(state);var result=new LinkedHashMap<String,Double>();for(int i=0;i<count;i++)result.put(names[i],state[i]);return java.util.Collections.unmodifiableMap(result);}
    @Override public Map<String,Double> getIntegrationHistories(){
        ready();if(count!=15)return controls.getIntegrationHistories();
        var histories=new LinkedHashMap<String,Double>(controls.getIntegrationHistories());histories.put("Algebraic line current (A)",algebraicCurrent);return java.util.Collections.unmodifiableMap(histories);
    }
    @Override public IntegrationStateMonitor.Snapshot getStateDerivativeSnapshot(){ready();return monitor.snapshot();}
    private void record(IntegrationStateMonitor.Stage stage){monitor.begin(stage);for(int i=0;i<count;i++)monitor.record(i,state[i],rate[i]);}
    private void ready(){if(!initialized)throw new IllegalStateException("Initialize coupled converter runtime first");}
    private void exclusive(){if(pending)throw new IllegalStateException("Finish or cancel the coupled predictor first");}
    private void noSharedProposal(){if(sharedPending)throw new IllegalStateException("Complete or restore the shared state proposal first");}
    private static void finite(double value){if(!Double.isFinite(value))throw new IllegalArgumentException("Finite coupled boundary required");}
    private static void positive(double value){finite(value);if(value<=0)throw new IllegalArgumentException("Positive AC voltage magnitude required");}
    private static void interval(double value){finite(value);if(value<0)throw new IllegalArgumentException("Nonnegative integration interval required");}
    private void buffer(double[] values,int offset){java.util.Objects.checkFromIndexSize(offset,count,values.length);}
}
