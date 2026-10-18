package org.interpss.dstab.renewable;

import java.util.Map;
import java.util.Objects;
import static org.interpss.dstab.renewable.Reece1Data.Parameter.*;

/** REEC_E local QFLAG=2 and PEFLAG=1 PI blocks only, not a complete controller.
 * References, filtered power and current limits are prepared by the outer model.
 * Stored coordinates are the integral contributions to the respective PI outputs. */
public final class Reece1LocalPi {
    public record Input(double activeReference,double measuredPower,double reactiveReference,
            double measuredReactivePower,double iqMinimum,double iqMaximum,
            double powerMinimum,double powerMaximum,boolean voltageDip) {
        public Input {
            for(double value:new double[]{activeReference,measuredPower,reactiveReference,
                    measuredReactivePower,iqMinimum,iqMaximum,powerMinimum,powerMaximum})
                if(!Double.isFinite(value))throw new IllegalArgumentException("REECE PI inputs must be finite");
            if(iqMinimum>iqMaximum||powerMinimum>powerMaximum)throw new IllegalArgumentException("REECE PI reversed limits");
        }
    }
    private final Reece1Data data;
    private final double[] state=new double[2];
    private double[] start,firstRate;
    private Input input;
    public Reece1LocalPi(Reece1Data data){this.data=Objects.requireNonNull(data);}

    /** Initial outputs derive from solved Q/V and P, not observed PI state fitting. */
    public void initialize(double reactiveCurrent,double activePower,Input input) {
        requireFinite(reactiveCurrent);requireFinite(activePower);this.input=Objects.requireNonNull(input);
        state[0]=data.selectors().reactiveControlFlag()==2?reactiveCurrent-data.value(KQP)*qError(input):0;
        state[1]=data.selectors().activePowerPiFlag()==1?activePower-data.value(KPP)*pError(input):0;
        start=null;firstRate=null;checkStates();
    }
    public void step(double dt,Input input){step(dt,input,0);step(dt,input,1);}
    public void step(double dt,Input input,int stage) {
        if(this.input==null)throw new IllegalStateException("REECE local PI must be initialized");
        if(!Double.isFinite(dt)||dt<0||(stage!=0&&stage!=1))throw new IllegalArgumentException("REECE PI requires finite dt >= 0 and stages 0/1");
        if(dt>0&&stage==1&&start==null)throw new IllegalStateException("REECE PI corrector without predictor");
        this.input=Objects.requireNonNull(input);
        if(dt==0)return;
        if(stage==0) {
            start=state.clone();firstRate=rates(input);
            for(int i=0;i<2;i++)state[i]=advance(i,start[i],dt*firstRate[i],input);
        } else {
            double[] lastRate=rates(input);
            for(int i=0;i<2;i++)state[i]=advance(i,start[i],.5*dt*(firstRate[i]+lastRate[i]),input);
            start=null;firstRate=null;
        }
        checkStates();
    }
    private double[] rates(Input input) {
        return new double[]{data.selectors().reactiveControlFlag()==2
                ? rate(state[0],data.value(KQI),qError(input),input.iqMinimum(),input.iqMaximum(),input.voltageDip()):0,
            data.selectors().activePowerPiFlag()==1
                ? rate(state[1],data.value(KPI),pError(input),input.powerMinimum(),input.powerMaximum(),input.voltageDip()):0};
    }
    static double rate(double state,double ki,double error,double min,double max,boolean frozen) {
        double derivative=ki*error;
        // Native 36.7 limits the integral contribution independently from
        // clipping the proportional-plus-integral output. Active-boundary
        // probes are required: inactive-limit profiles cannot distinguish it.
        return frozen||state>=max&&derivative>0||state<=min&&derivative<0?0:derivative;
    }
    private double advance(int index,double old,double increment,Input input) {
        if(input.voltageDip())return old;
        double min=index==0?input.iqMinimum():input.powerMinimum(),max=index==0?input.iqMaximum():input.powerMaximum();
        return project(old+increment,min,max);
    }
    static double project(double candidate,double min,double max) {
        // Proportional gain never changes the stored-state limiter coordinate.
        return clamp(candidate,min,max);
    }
    private static double qError(Input input){return input.reactiveReference()-input.measuredReactivePower();}
    private static double pError(Input input){return input.activeReference()-input.measuredPower();}
    private static double clamp(double x,double min,double max){return Math.max(min,Math.min(max,x));}
    public double reactiveOutput() {
        requireInitialized();
        if(data.selectors().reactiveControlFlag()!=2)throw new IllegalStateException("Other QFLAG paths belong to the outer controller");
        return clamp(state[0]+data.value(KQP)*qError(input),input.iqMinimum(),input.iqMaximum());
    }
    public double activeOutput() {
        requireInitialized();double value=data.selectors().activePowerPiFlag()==0?input.activeReference():state[1]+data.value(KPP)*pError(input);
        return clamp(value,input.powerMinimum(),input.powerMaximum());
    }
    public int currentPriority(){requireInitialized();return data.selectors().currentPriority(input.voltageDip());}
    public Map<String,Double> getNamedStates(){requireInitialized();return Map.of("Local Reactive PI Integral",state[0],"Local Active PI Integral",state[1]);}
    public double[] getIntegralContributions(){requireInitialized();return state.clone();}
    private void requireInitialized(){if(input==null)throw new IllegalStateException("REECE local PI must be initialized");}
    private static void requireFinite(double value){if(!Double.isFinite(value))throw new IllegalArgumentException("REECE PI initial output must be finite");}
    private void checkStates(){for(double value:state)if(!Double.isFinite(value))throw new IllegalStateException("REECE PI state overflow");}
}
