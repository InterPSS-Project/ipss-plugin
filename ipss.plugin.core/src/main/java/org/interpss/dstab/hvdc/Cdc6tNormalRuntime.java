package org.interpss.dstab.hvdc;

import java.util.Map;
import org.interpss.dstab.control.util.IntegrationStateAccess;
import org.interpss.dstab.control.util.IntegrationStateMonitor;
import org.interpss.dstab.control.util.StaggeredEulerParticipant;

/** Shared electrical/transducer runtime. Protection decisions and committed event history have a separate owner. */
public final class Cdc6tNormalRuntime implements StaggeredEulerParticipant, IntegrationStateAccess {
    /** The event owner selects a regime; evaluating it never changes protection history. */
    public enum OperatingMode { NORMAL, BLOCKED, BYPASSED, RECOVERING, POWER_TO_CURRENT }
    private final Cdc6tMeasurements measurements;
    private final Cdc6tCurrentOrder command;
    private final Cdc6tVoltageCurrentLimit limit;
    private final Cdc6tConductingLink link;
    private final double minimumCurrentAmperes;
    private double boundaryAmperes;
    private final double[] states=new double[3],boundary=new double[8],currents=new double[4];
    private boolean boundaryReady;
    private double scheduledAmperes;

    /** Takes exclusive ownership of the mutable measurements and link scratch buffers. */
    public Cdc6tNormalRuntime(Cdc6tMeasurements measurements,Cdc6tCurrentOrder command,
            Cdc6tVoltageCurrentLimit limit,Cdc6tConductingLink link) {
        this(measurements,command,limit,link,0);
    }
    /** Minimum demand applies to conducting orders, including recovery, before the current ceiling.
     * Blocking remains zero current and bypass retains its distinct scheduled-current regime. */
    public Cdc6tNormalRuntime(Cdc6tMeasurements measurements,Cdc6tCurrentOrder command,
            Cdc6tVoltageCurrentLimit limit,Cdc6tConductingLink link,double minimumCurrentAmperes) {
        this.measurements=java.util.Objects.requireNonNull(measurements);
        this.command=java.util.Objects.requireNonNull(command);this.limit=java.util.Objects.requireNonNull(limit);
        this.link=java.util.Objects.requireNonNull(link);
        if(!Double.isFinite(minimumCurrentAmperes)||minimumCurrentAmperes<0
                ||minimumCurrentAmperes>limit.ceilingAmperes(0))
            throw new IllegalArgumentException("Nonnegative minimum demand no greater than the lowest current ceiling required");
        this.minimumCurrentAmperes=minimumCurrentAmperes;
    }
    public void initialize(double inverterVolts,double currentAmperes,double rectifierVolts) {
        if(!Double.isFinite(currentAmperes)||currentAmperes<=0)throw new IllegalArgumentException("Positive scheduled current required");
        measurements.initialize(inverterVolts,currentAmperes,rectifierVolts);scheduledAmperes=currentAmperes;boundaryReady=false;
    }
    public void configureIntegrationStep(double outerStep){measurements.configureIntegrationStep(outerStep);}
    public void sampleCommandMeasurements(){measurements.sampleCommandMeasurements();}
    public void acceptAlgebraicMeasurements(){ready();measurements.acceptAlgebraicInputs();}
    /** Recompute voltage-dependent injections and transducer inputs for each network trial.
     * Returns rectifier real/imaginary injection then inverter real/imaginary injection on network base. */
    public void evaluateBoundary(double rectifierReal,double rectifierImag,double inverterReal,double inverterImag,
            double auxiliaryMegawatts,double[] output,int offset) {
        evaluateBoundary(rectifierReal,rectifierImag,inverterReal,inverterImag,auxiliaryMegawatts,
                OperatingMode.NORMAL,1,1,output,offset);
    }
    /** Electrical evaluation with an externally selected mode and prepared recovery factors.
     * This overload does not select transitions or commit timers. Auxiliary modulation
     * outside normal operation remains unqualified and is rejected explicitly. */
    public void evaluateBoundary(double rectifierReal,double rectifierImag,double inverterReal,double inverterImag,
            double auxiliaryMegawatts,OperatingMode mode,double voltageFactor,double currentFactor,double[] output,int offset) {
        java.util.Objects.checkFromIndexSize(offset,4,output.length);
        java.util.Objects.requireNonNull(mode);
        boundaryReady=false;
        if(!Double.isFinite(voltageFactor)||voltageFactor<0||voltageFactor>1
                ||!Double.isFinite(currentFactor)||currentFactor<0||currentFactor>1)
            throw new IllegalArgumentException("Recovery factors must be in [0,1]");
        if(mode!=OperatingMode.NORMAL&&auxiliaryMegawatts!=0)
            throw new IllegalArgumentException("Auxiliary modulation outside normal operation is not yet qualified");
        if(mode!=OperatingMode.RECOVERING&&mode!=OperatingMode.BLOCKED&&(voltageFactor!=1||currentFactor!=1))
            throw new IllegalArgumentException("Recovery factors require the recovery regime");
        double vr=Math.hypot(rectifierReal,rectifierImag),vi=Math.hypot(inverterReal,inverterImag);
        if(!Double.isFinite(vr)||!Double.isFinite(vi)||mode!=OperatingMode.BLOCKED&&(vr<=0||mode!=OperatingMode.BYPASSED&&vi<=0))
            throw new IllegalArgumentException("Positive finite terminal voltage magnitudes required");
        measurements.readCommandMeasurements(states,0);
        double current;
        if(mode==OperatingMode.BLOCKED){
            current=0;java.util.Arrays.fill(boundary,0);boundary[2]=Math.PI/2;boundary[5]=Math.PI/2;
            java.util.Arrays.fill(currents,0);
        } else if(mode==OperatingMode.BYPASSED){
            current=scheduledAmperes;link.evaluateBypassed(vr,current,boundary,0);
            injection(boundary[0],boundary[1],rectifierReal,rectifierImag,vr,currents,0);currents[2]=0;currents[3]=0;
        } else {
            double desired=mode==OperatingMode.RECOVERING?scheduledAmperes*currentFactor
                    :mode==OperatingMode.POWER_TO_CURRENT?scheduledAmperes:command.amperes(states[0],states[2],auxiliaryMegawatts);
            current=limit.limit(Math.max(minimumCurrentAmperes,desired),states[0]);
            link.evaluate(vr,vi,current,command.inverterVoltage(current,voltageFactor),boundary,0);
            injection(boundary[0],boundary[1],rectifierReal,rectifierImag,vr,currents,0);
            injection(boundary[3],boundary[4],inverterReal,inverterImag,vi,currents,2);
        }
        measurements.setInput(boundary[7],current,boundary[6]);
        boundaryAmperes=current;
        System.arraycopy(currents,0,output,offset,4);boundaryReady=true;
    }
    /** Zero-time initialization only: equilibrate measurements and current against
     * the freshly evaluated electrical boundary, without advancing a history. */
    void initializeFromBoundary(){
        ready();initialize(boundary[7],boundaryAmperes,boundary[6]);
    }
    private static void injection(double p,double q,double real,double imag,double magnitude,double[] output,int offset) {
        double cosine=real/magnitude,sine=imag/magnitude;
        double ir=(-p*cosine-q*sine)/magnitude,ii=(-p*sine+q*cosine)/magnitude;
        if(!Double.isFinite(ir)||!Double.isFinite(ii))throw new IllegalArgumentException("Finite terminal current required");
        output[offset]=ir;output[offset+1]=ii;
    }
    public void readBoundary(double[] output,int offset) {
        ready();java.util.Objects.checkFromIndexSize(offset,8,output.length);System.arraycopy(boundary,0,output,offset,8);
    }
    public void step(double seconds,int stage) {ready();measurements.step(seconds,stage);boundaryReady=false;}
    public void cancelPrediction() {measurements.cancelPrediction();boundaryReady=false;}
    @Override public int staggeredStateCount(){return 3;}
    @Override public void readStaggeredState(double[] values,int offset){measurements.readStaggeredState(values,offset);}
    @Override public void evaluateStaggered(double seconds,double[] derivatives,int offset){ready();measurements.evaluateStaggered(seconds,derivatives,offset);}
    @Override public void applyStaggeredState(double[] values,int offset,double seconds){measurements.applyStaggeredState(values,offset,seconds);boundaryReady=false;}
    @Override public Map<String,Double> getIntegrationStates(){return measurements.getIntegrationStates();}
    @Override public IntegrationStateMonitor.Snapshot getStateDerivativeSnapshot(){return measurements.getStateDerivativeSnapshot();}
    private void ready(){if(!boundaryReady)throw new IllegalStateException("Refresh terminal boundary after initialization or state changes");}
}
