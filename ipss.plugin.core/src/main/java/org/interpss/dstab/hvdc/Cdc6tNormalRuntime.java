package org.interpss.dstab.hvdc;

import java.util.Map;
import org.interpss.dstab.control.util.IntegrationStateAccess;
import org.interpss.dstab.control.util.IntegrationStateMonitor;
import org.interpss.dstab.control.util.StaggeredEulerParticipant;

/** Shared conducting runtime. Protection/event coordination and network attachment are separate owners. */
public final class Cdc6tNormalRuntime implements StaggeredEulerParticipant, IntegrationStateAccess {
    private final Cdc6tMeasurements measurements;
    private final Cdc6tCurrentOrder command;
    private final Cdc6tVoltageCurrentLimit limit;
    private final Cdc6tConductingLink link;
    private final double[] states=new double[3],boundary=new double[8],currents=new double[4];
    private boolean boundaryReady;

    /** Takes exclusive ownership of the mutable measurements and link scratch buffers. */
    public Cdc6tNormalRuntime(Cdc6tMeasurements measurements,Cdc6tCurrentOrder command,
            Cdc6tVoltageCurrentLimit limit,Cdc6tConductingLink link) {
        this.measurements=java.util.Objects.requireNonNull(measurements);
        this.command=java.util.Objects.requireNonNull(command);this.limit=java.util.Objects.requireNonNull(limit);
        this.link=java.util.Objects.requireNonNull(link);
    }
    public void initialize(double inverterVolts,double currentAmperes,double rectifierVolts) {
        measurements.initialize(inverterVolts,currentAmperes,rectifierVolts);boundaryReady=false;
    }
    /** Recompute voltage-dependent injections and transducer inputs for each network trial.
     * Returns rectifier real/imaginary injection then inverter real/imaginary injection on network base. */
    public void evaluateBoundary(double rectifierReal,double rectifierImag,double inverterReal,double inverterImag,
            double auxiliaryMegawatts,double[] output,int offset) {
        java.util.Objects.checkFromIndexSize(offset,4,output.length);
        boundaryReady=false;
        double vr=Math.hypot(rectifierReal,rectifierImag),vi=Math.hypot(inverterReal,inverterImag);
        if(!Double.isFinite(vr)||!Double.isFinite(vi)||vr<=0||vi<=0)
            throw new IllegalArgumentException("Positive finite terminal voltage magnitudes required");
        measurements.readStaggeredState(states,0);
        double current=limit.limit(command.amperes(states[0],states[2],auxiliaryMegawatts),states[0]);
        link.evaluate(vr,vi,current,command.inverterVoltage(current),boundary,0);
        injection(boundary[0],boundary[1],rectifierReal,rectifierImag,vr,currents,0);
        injection(boundary[3],boundary[4],inverterReal,inverterImag,vi,currents,2);
        measurements.setInput(boundary[7],current,boundary[6]);
        System.arraycopy(currents,0,output,offset,4);boundaryReady=true;
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
