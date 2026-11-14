package org.interpss.dstab.hvdc;

import static org.interpss.dstab.hvdc.Chvdc2Parameters.Constant.*;
import java.util.Map;
import com.interpss.dstab.controller.runtime.IntegrationStateAccess;
import com.interpss.dstab.controller.runtime.IntegrationStateMonitor;
import com.interpss.dstab.controller.runtime.StaggeredEulerParticipant;

/** Shared rectifier/inverter current PI and firing-angle states, in degrees.
 * Measured currents and externally calculated angle bounds belong to the caller.
 * Neither input refresh nor observation advances state or commits a limiter history. */
public final class Chvdc2CurrentControls implements StaggeredEulerParticipant, IntegrationStateAccess {
    private static final int SIZE=4;
    private static final String[] NAMES={"Rectifier current PI (deg)","Rectifier firing angle (deg)",
            "Inverter current PI (deg)","Inverter firing angle (deg)"};
    private final double kpR,kiR,inverseTimeR,kpI,kiI,inverseTimeI,inverseRatedCurrent,margin,
            baseMinR,maxR,minI,minRate,maxRate;
    private final double[] state=new double[SIZE],origin=new double[SIZE],firstRate=new double[SIZE],
            rate=new double[SIZE],candidate=new double[SIZE];
    private final IntegrationStateMonitor monitor=new IntegrationStateMonitor(NAMES);
    private double minR,maxI,errorR,errorI,pendingStep;
    private boolean inputReady,initialized,pending;

    public Chvdc2CurrentControls(Chvdc2Parameters parameters) {
        java.util.Objects.requireNonNull(parameters);
        kpR=parameters.value(KP_R);kiR=parameters.value(KI_R);inverseTimeR=1/parameters.value(T_ALPHA_R_SEC);
        kpI=parameters.value(KP_I);kiI=parameters.value(KI_I);inverseTimeI=1/parameters.value(T_ALPHA_I_SEC);
        inverseRatedCurrent=1/parameters.value(I_DC_RATED_A);margin=parameters.value(I_MARGIN_PU);
        baseMinR=parameters.value(ALPHA_MIN_R_DEG);maxR=parameters.value(ALPHA_MAX_R_DEG);
        minI=parameters.value(ALPHA_MIN_I_DEG);minRate=parameters.value(MIN_RATE_DEG);maxRate=parameters.value(MAX_RATE_DEG);
        finite(inverseTimeR);finite(inverseTimeI);finite(inverseRatedCurrent);
        if(baseMinR<0||maxR>180||minI<0||minI>180)
            throw new IllegalArgumentException("Firing-angle limits must be in [0,180] degrees");
    }
    /** Measured currents use rated-current pu; orders and auxiliary signals use amperes.
     * Independent auxiliary inputs preserve routing as an explicit outer-model responsibility. */
    public void setInput(double measuredRectifierPu,double measuredInverterPu,double rectifierOrderAmperes,
            double inverterOrderAmperes,double rectifierAuxAmperes,double inverterAuxAmperes,
            double rectifierMinimumDegrees,double inverterMaximumDegrees) {
        finite(measuredRectifierPu);finite(measuredInverterPu);finite(rectifierOrderAmperes);finite(inverterOrderAmperes);
        finite(rectifierAuxAmperes);finite(inverterAuxAmperes);finite(rectifierMinimumDegrees);finite(inverterMaximumDegrees);
        if(rectifierMinimumDegrees<baseMinR||rectifierMinimumDegrees>maxR
                ||inverterMaximumDegrees<minI||inverterMaximumDegrees>180)
            throw new IllegalArgumentException("Calculated CHVDC2 angle bounds are inconsistent with source limits");
        double er=measuredRectifierPu-(rectifierOrderAmperes+rectifierAuxAmperes)*inverseRatedCurrent;
        double ei=measuredInverterPu-(inverterOrderAmperes+inverterAuxAmperes)*inverseRatedCurrent+margin;
        finite(er);finite(ei);
        errorR=er;errorI=ei;minR=rectifierMinimumDegrees;maxI=inverterMaximumDegrees;inputReady=true;
    }
    /** Source initial PI/angle coordinates use the solved firing angles. Check residuals separately;
     * a nonzero current error is not silently removed by rebasing the controller command. */
    public void initialize(double rectifierDegrees,double inverterDegrees) {
        if(!inputReady)throw new IllegalStateException("Supply current inputs and angle bounds before initialization");
        finite(rectifierDegrees);finite(inverterDegrees);
        if(rectifierDegrees<minR||rectifierDegrees>maxR||inverterDegrees<minI||inverterDegrees>maxI)
            throw new IllegalArgumentException("Solved firing angles lie outside controller limits");
        state[0]=state[1]=rectifierDegrees;state[2]=state[3]=inverterDegrees;
        initialized=true;pending=false;monitor.reset();
    }
    public double rectifierDegrees(){ready();return state[1];}
    public double inverterDegrees(){ready();return state[3];}
    private void rates() {
        // RAML changes the firing-angle floor without rebasing the current PI memory.
        double piR=clip(state[0],baseMinR,maxR),alphaR=clip(state[1],minR,maxR);
        double piI=clip(state[2],minI,maxI),alphaI=clip(state[3],minI,maxI);
        rate[0]=nonWindup(piR,kiR*errorR,baseMinR,maxR);
        rate[1]=nonWindup(alphaR,clip(clip(piR+kpR*errorR,minR,maxR)-alphaR,minRate,maxRate)*inverseTimeR,minR,maxR);
        rate[2]=nonWindup(piI,kiI*errorI,minI,maxI);
        rate[3]=nonWindup(alphaI,clip(clip(piI+kpI*errorI,minI,maxI)-alphaI,minRate,maxRate)*inverseTimeI,minI,maxI);
        for(double f:rate)finite(f);
    }
    public void step(double seconds,int stage) {
        ready();interval(seconds);
        if(stage!=0&&stage!=1)throw new IllegalArgumentException("Predictor or corrector required");
        if(seconds==0)return;
        if(stage==0&&pending||stage==1&&(!pending||seconds!=pendingStep))
            throw new IllegalStateException("Matching current-controller predictor/corrector required");
        rates();
        for(int i=0;i<SIZE;i++){
            candidate[i]=stage==0?state[i]+seconds*rate[i]:origin[i]+.5*seconds*(firstRate[i]+rate[i]);finite(candidate[i]);
        }
        project(candidate,0);record(stage==0?IntegrationStateMonitor.Stage.PREDICTOR:IntegrationStateMonitor.Stage.CORRECTOR);
        if(stage==0){System.arraycopy(state,0,origin,0,SIZE);System.arraycopy(rate,0,firstRate,0,SIZE);pendingStep=seconds;}
        System.arraycopy(candidate,0,state,0,SIZE);pending=stage==0;
    }
    public void cancelPrediction(){ready();if(pending){System.arraycopy(origin,0,state,0,SIZE);pending=false;monitor.reset();}}
    /** Exact rollback for the enclosing controller owner; saved states may precede a changed bound. */
    void restoreCoordinates(double[] values,int offset){System.arraycopy(values,offset,state,0,SIZE);pending=false;monitor.reset();}
    @Override public int staggeredStateCount(){return SIZE;}
    @Override public void readStaggeredState(double[] values,int offset){ready();buffer(values,offset);System.arraycopy(state,0,values,offset,SIZE);}
    @Override public void evaluateStaggered(double seconds,double[] values,int offset){
        ready();exclusive();interval(seconds);buffer(values,offset);rates();record(IntegrationStateMonitor.Stage.STAGGERED);
        System.arraycopy(rate,0,values,offset,SIZE);
    }
    @Override public void applyStaggeredState(double[] values,int offset,double seconds){
        ready();exclusive();interval(seconds);buffer(values,offset);
        for(int i=0;i<SIZE;i++)finite(values[offset+i]);
        System.arraycopy(values,offset,candidate,0,SIZE);project(candidate,0);System.arraycopy(candidate,0,state,0,SIZE);
    }
    @Override public Map<String,Double> getIntegrationStates(){ready();return Map.of(NAMES[0],state[0],NAMES[1],state[1],NAMES[2],state[2],NAMES[3],state[3]);}
    @Override public IntegrationStateMonitor.Snapshot getStateDerivativeSnapshot(){return monitor.snapshot();}
    private void project(double[] x,int offset){x[offset]=clip(x[offset],baseMinR,maxR);x[offset+1]=clip(x[offset+1],minR,maxR);x[offset+2]=clip(x[offset+2],minI,maxI);x[offset+3]=clip(x[offset+3],minI,maxI);}
    private void record(IntegrationStateMonitor.Stage stage){monitor.begin(stage);for(int i=0;i<SIZE;i++)monitor.record(i,state[i],rate[i]);}
    private static double nonWindup(double x,double f,double lo,double hi){return x<=lo&&f<0||x>=hi&&f>0?0:f;}
    private static double clip(double x,double lo,double hi){return Math.max(lo,Math.min(hi,x));}
    private void ready(){if(!initialized)throw new IllegalStateException("Initialize current controls first");}
    private void exclusive(){if(pending)throw new IllegalStateException("Cancel or finish local prediction before shared integration");}
    private static void buffer(double[] values,int offset){java.util.Objects.checkFromIndexSize(offset,SIZE,values.length);}
    private static void finite(double value){if(!Double.isFinite(value))throw new IllegalArgumentException("Finite current-controller value required");}
    private static void interval(double value){finite(value);if(value<0)throw new IllegalArgumentException("Nonnegative interval required");}
}
