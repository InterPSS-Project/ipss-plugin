package org.interpss.dstab.hvdc;

import java.util.Map;
import org.interpss.dstab.control.util.IntegrationStateAccess;
import org.interpss.dstab.control.util.IntegrationStateMonitor;
import org.interpss.dstab.control.util.StaggeredEulerParticipant;

/** Shared CDC6T transducers in volts and amperes; converter controls are owned separately. */
public final class Cdc6tMeasurements implements StaggeredEulerParticipant, IntegrationStateAccess {
    private static final String[] NAMES={"Measured inverter voltage (V)","Measured inverter current (A)",
            "Measured rectifier voltage (V)"};
    private final double[] time=new double[3],state=new double[3],input=new double[3],origin=new double[3],
            firstRate=new double[3],rate=new double[3],candidate=new double[3];
    private final IntegrationStateMonitor monitor=new IntegrationStateMonitor(NAMES);
    private boolean initialized,pending;
    private double pendingStep;

    public Cdc6tMeasurements(double inverterVoltageTime,double currentTime,double rectifierVoltageTime) {
        time[0]=inverterVoltageTime;time[1]=currentTime;time[2]=rectifierVoltageTime;
        for(double t:time)if(!Double.isFinite(t)||t<=0)
            throw new IllegalArgumentException("CDC6T measurement times must be finite and positive");
    }
    public void initialize(double inverterVoltage,double current,double rectifierVoltage) {
        setInput(inverterVoltage,current,rectifierVoltage);
        System.arraycopy(input,0,state,0,3);initialized=true;pending=false;monitor.reset();
    }
    /** Refresh algebraic measurements without advancing state or committed integration history. */
    public void setInput(double inverterVoltage,double current,double rectifierVoltage) {
        finite(inverterVoltage);finite(current);finite(rectifierVoltage);
        input[0]=inverterVoltage;input[1]=current;input[2]=rectifierVoltage;
    }
    /** Explicit predictor/corrector using the same RHS as the shared staggered solver. */
    public void step(double seconds,int stage) {
        ready();interval(seconds);
        if(stage!=0&&stage!=1)throw new IllegalArgumentException("Predictor or corrector required");
        if(seconds==0)return;
        if(stage==0&&pending||stage==1&&(!pending||seconds!=pendingStep))
            throw new IllegalStateException("Matching measurement predictor/corrector required");
        rates();
        for(int i=0;i<3;i++) {
            candidate[i]=stage==0?state[i]+seconds*rate[i]:origin[i]+.5*seconds*(firstRate[i]+rate[i]);
            finite(candidate[i]);
        }
        record(stage==0?IntegrationStateMonitor.Stage.PREDICTOR:IntegrationStateMonitor.Stage.CORRECTOR);
        if(stage==0) {
            System.arraycopy(state,0,origin,0,3);System.arraycopy(rate,0,firstRate,0,3);pendingStep=seconds;
        }
        System.arraycopy(candidate,0,state,0,3);pending=stage==0;
    }
    public void cancelPrediction() {
        ready();if(pending){System.arraycopy(origin,0,state,0,3);pending=false;monitor.reset();}
    }
    @Override public int staggeredStateCount(){return 3;}
    @Override public void readStaggeredState(double[] values,int offset) {
        ready();buffer(values,offset);System.arraycopy(state,0,values,offset,3);
    }
    @Override public void evaluateStaggered(double seconds,double[] derivatives,int offset) {
        ready();interval(seconds);buffer(derivatives,offset);exclusive();rates();
        record(IntegrationStateMonitor.Stage.STAGGERED);System.arraycopy(rate,0,derivatives,offset,3);
    }
    @Override public void applyStaggeredState(double[] values,int offset,double seconds) {
        ready();interval(seconds);buffer(values,offset);exclusive();
        for(int i=0;i<3;i++)finite(values[offset+i]);
        System.arraycopy(values,offset,state,0,3);
    }
    @Override public Map<String,Double> getIntegrationStates() {
        ready();return Map.of(NAMES[0],state[0],NAMES[1],state[1],NAMES[2],state[2]);
    }
    @Override public IntegrationStateMonitor.Snapshot getStateDerivativeSnapshot(){return monitor.snapshot();}
    private void rates(){for(int i=0;i<3;i++){rate[i]=(input[i]-state[i])/time[i];finite(rate[i]);}}
    private void record(IntegrationStateMonitor.Stage stage) {
        monitor.begin(stage);for(int i=0;i<3;i++)monitor.record(i,state[i],rate[i]);
    }
    private void exclusive(){if(pending)throw new IllegalStateException("Cancel or finish the local predictor first");}
    private void ready(){if(!initialized)throw new IllegalStateException("Initialize measurements first");}
    private static void finite(double value){if(!Double.isFinite(value))throw new IllegalArgumentException("Finite measurement required");}
    private static void interval(double seconds){finite(seconds);if(seconds<0)throw new IllegalArgumentException("Nonnegative interval required");}
    private static void buffer(double[] values,int offset){java.util.Objects.checkFromIndexSize(offset,3,values.length);}
}
