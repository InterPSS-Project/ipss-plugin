package org.interpss.dstab.hvdc;

import java.util.Map;
import com.interpss.dstab.controller.runtime.IntegrationStateAccess;
import com.interpss.dstab.controller.runtime.IntegrationStateMonitor;
import com.interpss.dstab.controller.runtime.StaggeredEulerParticipant;

/** DC T-circuit in volts, amperes, ohms, henries and farads.
 * One shared RHS serves both integrators. This component does not attach a converter
 * or infer a source-specific capacitance bypass/time-preparation policy. */
public final class Chvdc2DcLine implements StaggeredEulerParticipant, IntegrationStateAccess {
    private final double resistance, inductance, capacitance;
    private final double inverseInductance, inverseHalfInductance, inverseCapacitance, halfResistance;
    private final boolean dynamic;
    private final double[] state, origin, firstRate, rate, candidate;
    private final String[] names;
    private final IntegrationStateMonitor monitor;
    private double rectifierVolts, inverterVolts, pendingStep;
    private boolean initialized, pending;

    public Chvdc2DcLine(double resistanceOhms, double effectiveHenries, double farads, boolean dynamic) {
        finite(resistanceOhms); finite(effectiveHenries); finite(farads);
        if (resistanceOhms <= 0 || effectiveHenries < 0 || farads < 0 || dynamic && effectiveHenries == 0)
            throw new IllegalArgumentException("Positive resistance and dynamic inductance; nonnegative capacitance required");
        resistance=resistanceOhms; inductance=effectiveHenries; capacitance=farads; this.dynamic=dynamic;
        inverseInductance=dynamic?1/effectiveHenries:0;
        inverseHalfInductance=dynamic&&farads>0?2/effectiveHenries:0;
        inverseCapacitance=dynamic&&farads>0?1/farads:0;
        halfResistance=.5*resistanceOhms;
        finite(inverseInductance);finite(inverseHalfInductance);finite(inverseCapacitance);
        int count=dynamic?(farads>0?3:1):0;
        state=new double[count]; origin=new double[count]; firstRate=new double[count];
        rate=new double[count]; candidate=new double[count];
        names=count==3?new String[]{"Rectifier line current (A)","Line capacitor voltage (V)","Inverter line current (A)"}
                :count==1?new String[]{"Line current (A)"}:new String[0];
        monitor=new IntegrationStateMonitor(names);
    }
    /** Initialize the line equilibrium; controls remain responsible for consistency with solved current. */
    public void initialize(double rectifierVolts,double inverterVolts) {
        finite(rectifierVolts); finite(inverterVolts);
        double current=(rectifierVolts-inverterVolts)/resistance;
        double midpoint=.5*rectifierVolts+.5*inverterVolts;
        finite(current);finite(midpoint);
        setInput(rectifierVolts,inverterVolts);
        if(dynamic)state[0]=current;
        if(state.length==3){state[1]=midpoint;state[2]=current;}
        pending=false;initialized=true;monitor.reset();
    }
    /** Refresh terminal voltage for each network trial without changing line state/history. */
    public void setInput(double rectifierVolts,double inverterVolts) {
        finite(rectifierVolts);finite(inverterVolts);
        this.rectifierVolts=rectifierVolts;this.inverterVolts=inverterVolts;
    }
    public double rectifierAmperes(){ready();return dynamic?state[0]:(rectifierVolts-inverterVolts)/resistance;}
    public double inverterAmperes(){ready();return state.length==3?state[2]:rectifierAmperes();}
    public double midpointVolts(){ready();return state.length==3?state[1]:.5*rectifierVolts+.5*inverterVolts;}
    public double effectiveHenries(){return inductance;}
    public double capacitanceFarads(){return capacitance;}
    double resistanceOhms(){return resistance;}
    void restoreCoordinates(double[] values,int offset){System.arraycopy(values,offset,state,0,state.length);pending=false;monitor.reset();}
    private void rates() {
        if(state.length==1)rate[0]=(rectifierVolts-inverterVolts-resistance*state[0])*inverseInductance;
        else if(state.length==3){
            rate[0]=(rectifierVolts-state[1]-halfResistance*state[0])*inverseHalfInductance;
            rate[1]=(state[0]-state[2])*inverseCapacitance;
            rate[2]=(state[1]-inverterVolts-halfResistance*state[2])*inverseHalfInductance;
        }
        for(double value:rate)finite(value);
    }
    public void step(double seconds,int stage) {
        ready();interval(seconds);
        if(stage!=0&&stage!=1)throw new IllegalArgumentException("Predictor or corrector required");
        if(seconds==0)return;
        if(stage==0&&pending||stage==1&&(!pending||seconds!=pendingStep))
            throw new IllegalStateException("Matching line predictor/corrector required");
        rates();
        for(int i=0;i<state.length;i++){
            candidate[i]=stage==0?state[i]+seconds*rate[i]:origin[i]+.5*seconds*(firstRate[i]+rate[i]);
            finite(candidate[i]);
        }
        record(stage==0?IntegrationStateMonitor.Stage.PREDICTOR:IntegrationStateMonitor.Stage.CORRECTOR);
        if(stage==0){System.arraycopy(state,0,origin,0,state.length);System.arraycopy(rate,0,firstRate,0,state.length);pendingStep=seconds;}
        System.arraycopy(candidate,0,state,0,state.length);pending=stage==0;
    }
    public void cancelPrediction(){ready();if(pending){System.arraycopy(origin,0,state,0,state.length);pending=false;monitor.reset();}}
    @Override public int staggeredStateCount(){return state.length;}
    @Override public void readStaggeredState(double[] values,int offset){ready();buffer(values,offset);System.arraycopy(state,0,values,offset,state.length);}
    @Override public void evaluateStaggered(double seconds,double[] values,int offset){
        ready();exclusive();interval(seconds);buffer(values,offset);rates();
        record(IntegrationStateMonitor.Stage.STAGGERED);System.arraycopy(rate,0,values,offset,state.length);
    }
    @Override public void applyStaggeredState(double[] values,int offset,double seconds){
        ready();exclusive();interval(seconds);buffer(values,offset);
        for(int i=0;i<state.length;i++)finite(values[offset+i]);
        System.arraycopy(values,offset,state,0,state.length);
    }
    @Override public Map<String,Double> getIntegrationStates(){
        ready();return switch(state.length){
            case 3 -> Map.of(names[0],state[0],names[1],state[1],names[2],state[2]);
            case 1 -> Map.of(names[0],state[0]);
            default -> Map.of();
        };
    }
    @Override public IntegrationStateMonitor.Snapshot getStateDerivativeSnapshot(){return monitor.snapshot();}
    private void record(IntegrationStateMonitor.Stage stage){monitor.begin(stage);for(int i=0;i<state.length;i++)monitor.record(i,state[i],rate[i]);}
    private void buffer(double[] values,int offset){java.util.Objects.checkFromIndexSize(offset,state.length,values.length);}
    private void ready(){if(!initialized)throw new IllegalStateException("Initialize DC line first");}
    private void exclusive(){if(pending)throw new IllegalStateException("Cancel or finish local prediction before using shared integration");}
    private static void finite(double value){if(!Double.isFinite(value))throw new IllegalArgumentException("Finite DC line value required");}
    private static void interval(double value){finite(value);if(value<0)throw new IllegalArgumentException("Nonnegative interval required");}
}
