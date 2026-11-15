package org.interpss.dstab.hvdc;

import java.util.LinkedHashMap;
import java.util.Map;
import org.interpss.dstab.control.util.IntegrationStateAccess;
import org.interpss.dstab.control.util.IntegrationStateMonitor;
import org.interpss.dstab.control.util.StaggeredEulerParticipant;

/** Joint controller state owner. The electrical owner supplies live terminal inputs,
 * selects local integration intervals and explicitly accepts protection evaluations.
 * The same dependency order and numerical coordinates serve both integration methods.
 * This assembly does not attach a converter or substitute for its network equations. */
public final class Chvdc2ControlAssembly implements StaggeredEulerParticipant, IntegrationStateAccess {
    public static final int STATE_COUNT=15;
    private static final String[] NAMES={"Measured rectifier current (pu)","Measured inverter current (pu)",
            "Rectifier DC limiter voltage (pu)","Inverter DC limiter voltage (pu)",
            "Rectifier current PI (deg)","Inverter current PI (deg)","Rectifier firing angle (deg)","Inverter firing angle (deg)",
            "Inverter order lag (pu)","Inverter error lag (pu)","Measured rectifier AC voltage (pu)",
            "Measured inverter AC voltage (pu)","Rectifier AC current limit (pu)","Inverter AC current limit (pu)",
            "RAML washout lag (deg)"};
    private final Chvdc2OrderControls order;
    private final Chvdc2CurrentControls current;
    private final Chvdc2Protection protection;
    private final double[] inputs=new double[9],orderValues=new double[10],currentValues=new double[4],
            protectionValue=new double[1],state=new double[STATE_COUNT],rate=new double[STATE_COUNT],
            firstRate=new double[STATE_COUNT],candidate=new double[STATE_COUNT],leftRate=new double[STATE_COUNT];
    private final Checkpoint prediction=new Checkpoint(),transaction=new Checkpoint(),write=new Checkpoint();
    private final IntegrationStateMonitor monitor=new IntegrationStateMonitor(NAMES);
    private boolean inputReady,initialized,pending,inTransaction;
    private double pendingStep;
    private long revision;

    static final class Checkpoint {
        final double[] order=new double[10],current=new double[4],inputs=new double[9],left=new double[STATE_COUNT];
        final Chvdc2Protection.Checkpoint protection=new Chvdc2Protection.Checkpoint();
        long revision;
        double raml,acceptedGamma;
    }
    public Chvdc2ControlAssembly(Chvdc2Parameters parameters,double transformerRatio,double tap) {
        order=parameters.prepareOrderControls(transformerRatio,tap);
        current=parameters.prepareCurrentControls();protection=parameters.prepareProtection();
    }
    /** DC current/order/auxiliary use amperes, DC voltage kV, terminal AC voltage pu,
     * and freshly calculated inverter extinction angle degrees. One auxiliary signal
     * feeds both current controls, as specified for this model. */
    public void setInput(double ir,double ii,double vr,double vi,double vacr,double vaci,
            double reference,double auxiliary,double gamma) {
        finite(gamma);finite(vacr);finite(vaci);
        if(vacr<0||vaci<0)throw new IllegalArgumentException("Nonnegative terminal AC voltage required");
        order.setInput(ir,ii,vr,vi,vacr,vaci,reference,auxiliary);
        inputs[0]=ir;inputs[1]=ii;inputs[2]=vr;inputs[3]=vi;inputs[4]=vacr;inputs[5]=vaci;
        inputs[6]=reference;inputs[7]=auxiliary;inputs[8]=gamma;inputReady=true;
    }
    public void initialize(double rectifierDegrees,double inverterDegrees,double initialTime) {
        exclusive();if(inTransaction)throw new IllegalStateException("Finish the network transaction before initialization");
        if(!inputReady)throw new IllegalStateException("Supply terminal inputs before initialization");
        finite(rectifierDegrees);finite(inverterDegrees);finite(initialTime);initialized=false;
        order.initialize(inverterDegrees);
        protection.initialize(inputs[4],inputs[5],inputs[8],initialTime);
        bindCurrent();current.initialize(rectifierDegrees,inverterDegrees);
        revision=0;java.util.Arrays.fill(leftRate,0);initialized=true;monitor.reset();
    }
    private void bindCurrent() {
        order.readPublishedStates(orderValues,0);
        current.setInput(orderValues[0],orderValues[1],order.rectifierCurrentOrderAmperes(),
                order.inverterCurrentOrderAmperes(),inputs[7],inputs[7],protection.rectifierMinimumDegrees(),order.inverterMaximumDegrees());
    }
    private void bindProtection() {
        order.readPublishedStates(orderValues,0);
        // Native commutation recovery observes the inverter AC measurement
        // after T_MEAS filtering. A fast terminal-voltage rebound can cross
        // V_AC_UCF before the measured voltage permits release.
        protection.setInput(orderValues[6], orderValues[7], inputs[8]);
    }
    /** Commit a converged boundary. Distinct event sides require distinct sequence numbers. */
    public void acceptBoundary(double time,long sequence){ready();exclusive();accept(time,sequence,true);}
    void acceptProtectionStage(double time,long sequence){ready();exclusive();accept(time,sequence,false);}
    /** Evaluate the predictor's protection branch within the current trial. Canceling
     * the prediction restores the previous mode, clock, input and extinction history.
     * The revision becomes externally visible only after the corrector succeeds. */
    public void evaluatePredictorProtection(double time,long sequence) {
        ready();if(!pending)throw new IllegalStateException("A controller predictor is required");accept(time,sequence,false);
    }
    private void accept(double time,long sequence,boolean publishElectricalAngle) {
        acceptNetworkStage(time,sequence,publishElectricalAngle,true,true);
    }
    void acceptNetworkStage(double time,long sequence,boolean publishElectricalAngle,boolean allowRaml,boolean allowCommutation) {
        ready();
        rates();bindProtection();long before=protection.staggeredInputDiscontinuityVersion();
        protection.acceptNetworkStage(time,sequence,publishElectricalAngle,allowRaml,allowCommutation);
        long after=protection.staggeredInputDiscontinuityVersion();
        if(after!=before){System.arraycopy(rate,0,leftRate,0,STATE_COUNT);revision=after;}
        bindCurrent();
    }
    public double rectifierDegrees(){ready();return current.rectifierDegrees();}
    public double inverterDegrees(){ready();return current.inverterDegrees();}
    public double rectifierMinimumDegrees(){ready();return protection.rectifierMinimumDegrees();}
    public double inverterMaximumDegrees(){ready();return order.inverterMaximumDegrees();}
    public double rectifierCurrentOrderAmperes(){ready();return order.rectifierCurrentOrderAmperes();}
    public double inverterCurrentOrderAmperes(){ready();return order.inverterCurrentOrderAmperes();}
    public boolean inverterShorted(){ready();return protection.inverterShorted();}
    public boolean ramlActive(){ready();return protection.ramlActive();}
    public double acceptedInverterExtinctionDegrees(){ready();return pending?prediction.acceptedGamma:protection.acceptedInverterExtinctionDegrees();}

    private void rates() {
        bindCurrent();order.evaluateStaggered(0,orderValues,0);current.evaluateStaggered(0,currentValues,0);
        protection.evaluateStaggered(0,protectionValue,0);pack(rate);
    }
    private void pack(double[] destination) {
        System.arraycopy(orderValues,0,destination,0,4);System.arraycopy(orderValues,4,destination,8,6);
        destination[4]=currentValues[0];destination[5]=currentValues[2];destination[6]=currentValues[1];destination[7]=currentValues[3];
        destination[14]=protectionValue[0];
    }
    private void read(double[] destination) {
        order.readStaggeredState(orderValues,0);current.readStaggeredState(currentValues,0);
        protection.readStaggeredState(protectionValue,0);pack(destination);
    }
    /** Published observations in source state order. Bypassed measurement and order
     * lags expose their live algebraic outputs; integration coordinates and committed
     * histories are unchanged. Never use this buffer as a numerical state checkpoint. */
    public void readPublishedStates(double[] values,int offset) {
        ready();buffer(values,offset);
        order.readPublishedStates(orderValues,0);current.readStaggeredState(currentValues,0);
        protection.readStaggeredState(protectionValue,0);pack(state);
        System.arraycopy(state,0,values,offset,STATE_COUNT);
    }
    /** Project dependencies in order: measurements, RAML coordinate, then current-controller bounds. */
    private void apply(double[] values,int offset,double seconds) {
        for(int i=0;i<STATE_COUNT;i++)finite(values[offset+i]);save(write);
        try {
            System.arraycopy(values,offset,orderValues,0,4);System.arraycopy(values,offset+8,orderValues,4,6);
            order.applyStaggeredState(orderValues,0,seconds);
            protectionValue[0]=values[offset+14];protection.applyStaggeredState(protectionValue,0,seconds);
            bindCurrent();
            currentValues[0]=values[offset+4];currentValues[1]=values[offset+6];currentValues[2]=values[offset+5];currentValues[3]=values[offset+7];
            current.applyStaggeredState(currentValues,0,seconds);
        }catch(RuntimeException failure){restore(write);throw failure;}
    }
    /** Simultaneous modified-Euler stages using the same RHS as the shared participant. */
    public void step(double seconds,int stage) {
        ready();interval(seconds);if(stage!=0&&stage!=1)throw new IllegalArgumentException("Predictor or corrector required");if(seconds==0)return;
        if(stage==0&&pending||stage==1&&(!pending||seconds!=pendingStep))throw new IllegalStateException("Matching controller stages required");
        read(state);rates();
        if(stage==0){save(prediction);System.arraycopy(rate,0,firstRate,0,STATE_COUNT);}
        for(int i=0;i<STATE_COUNT;i++)candidate[i]=stage==0?state[i]+seconds*rate[i]:savedCoordinate(prediction,i)+.5*seconds*(firstRate[i]+rate[i]);
        apply(candidate,0,seconds);record(stage==0?IntegrationStateMonitor.Stage.PREDICTOR:IntegrationStateMonitor.Stage.CORRECTOR);
        pendingStep=seconds;pending=stage==0;
    }
    private static double savedCoordinate(Checkpoint c,int i) {
        if(i<4)return c.order[i];if(i<8)return c.current[switch(i){case 4->0;case 5->2;case 6->1;default->3;}];
        if(i<14)return c.order[i-4];return c.raml;
    }
    public void cancelPrediction(){ready();if(pending){restore(prediction);pending=false;}}
    public void beginTransaction(){ready();exclusive();if(inTransaction)throw new IllegalStateException("Controller transaction already open");save(transaction);inTransaction=true;}
    public void commitTransaction(){ready();exclusive();if(!inTransaction)throw new IllegalStateException("No controller transaction");inTransaction=false;}
    public void rollbackTransaction(){ready();if(!inTransaction)throw new IllegalStateException("No controller transaction");restore(transaction);pending=false;inTransaction=false;}
    void saveOwnerCheckpoint(Checkpoint destination){ready();exclusive();save(destination);}
    void restoreOwnerCheckpoint(Checkpoint source){restore(source);pending=false;}
    private void save(Checkpoint c) {
        order.readStaggeredState(c.order,0);current.readStaggeredState(c.current,0);protection.saveCheckpoint(c.protection);
        protection.readStaggeredState(protectionValue,0);c.raml=protectionValue[0];c.acceptedGamma=protection.acceptedInverterExtinctionDegrees();
        System.arraycopy(inputs,0,c.inputs,0,9);System.arraycopy(leftRate,0,c.left,0,STATE_COUNT);c.revision=revision;
    }
    private void restore(Checkpoint c) {
        setInput(c.inputs[0],c.inputs[1],c.inputs[2],c.inputs[3],c.inputs[4],c.inputs[5],c.inputs[6],c.inputs[7],c.inputs[8]);
        order.restoreCoordinates(c.order,0);protection.restoreCheckpoint(c.protection);bindCurrent();current.restoreCoordinates(c.current,0);
        System.arraycopy(c.left,0,leftRate,0,STATE_COUNT);revision=c.revision;monitor.reset();
    }
    @Override public int staggeredStateCount(){return STATE_COUNT;}
    @Override public void readStaggeredState(double[] values,int offset){ready();buffer(values,offset);read(state);System.arraycopy(state,0,values,offset,STATE_COUNT);}
    @Override public void evaluateStaggered(double seconds,double[] values,int offset){ready();exclusive();interval(seconds);buffer(values,offset);read(state);rates();record(IntegrationStateMonitor.Stage.STAGGERED);System.arraycopy(rate,0,values,offset,STATE_COUNT);}
    @Override public void applyStaggeredState(double[] values,int offset,double seconds){ready();exclusive();interval(seconds);buffer(values,offset);apply(values,offset,seconds);}
    @Override public long staggeredInputDiscontinuityVersion(){return pending?prediction.revision:revision;}
    @Override public boolean readStaggeredInputDiscontinuityLeftRates(double[] values,int offset){ready();buffer(values,offset);if(staggeredInputDiscontinuityVersion()==0)return false;System.arraycopy(pending?prediction.left:leftRate,0,values,offset,STATE_COUNT);return true;}
    @Override public Map<String,Double> getIntegrationStates(){ready();read(state);var result=new LinkedHashMap<String,Double>();for(int i=0;i<STATE_COUNT;i++)result.put(NAMES[i],state[i]);return java.util.Collections.unmodifiableMap(result);}
    @Override public Map<String,Double> getIntegrationHistories(){ready();return protection.getIntegrationHistories();}
    @Override public IntegrationStateMonitor.Snapshot getStateDerivativeSnapshot(){return monitor.snapshot();}
    private void record(IntegrationStateMonitor.Stage stage){monitor.begin(stage);for(int i=0;i<STATE_COUNT;i++)monitor.record(i,state[i],rate[i]);}
    private void ready(){if(!initialized)throw new IllegalStateException("Initialize joint controls first");}
    private void exclusive(){if(pending)throw new IllegalStateException("Finish or cancel the joint predictor first");}
    private static void finite(double value){if(!Double.isFinite(value))throw new IllegalArgumentException("Finite controller value required");}
    private static void interval(double value){finite(value);if(value<0)throw new IllegalArgumentException("Nonnegative integration interval required");}
    private static void buffer(double[] values,int offset){java.util.Objects.checkFromIndexSize(offset,STATE_COUNT,values.length);}
}
