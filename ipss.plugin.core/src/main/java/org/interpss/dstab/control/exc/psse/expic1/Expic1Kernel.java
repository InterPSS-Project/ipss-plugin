package org.interpss.dstab.control.exc.psse.expic1;

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.control.exc.psse.exac1.Exac1Exciter;
import com.interpss.dstab.controller.runtime.IntegrationStateAccess;
import com.interpss.dstab.controller.runtime.IntegrationStateMonitor;
import com.interpss.dstab.controller.runtime.IntegrationStateMonitor.Stage;
import com.interpss.dstab.controller.runtime.StaggeredEulerParticipant;

/** Seven-coordinate numerical kernel with explicit algebraic bypasses and integration stages. */
public final class Expic1Kernel implements IntegrationStateAccess,StaggeredEulerParticipant {
    private static final double EPS=1e-12;
    private static final String[] NAMES={"Sensed Voltage","Regulator Integral","Lead-Lag State",
            "Regulator Lag","Field Voltage","Feedback Lowpass","Feedback Lag"};
    private final Expic1Data data;
    private final double[] state=new double[7],trial=new double[7],oldRate=new double[7],rate=new double[7],a=new double[8];
    private final IntegrationStateMonitor monitor=new IntegrationStateMonitor(NAMES);
    private double[] active=state;
    private double piMax,piMin,vrMax,vrMin,efdMax,efdMin,reference,ec,vb,vs;
    private boolean initialized,predicting;

    public Expic1Kernel(Expic1Data data){this.data=java.util.Objects.requireNonNull(data);}
    public Expic1Data getData(){return data;}
    public void initialize(double fieldVoltage,double sensedVoltage,double bridgeVoltage,double stabilizer) {
        if(!Double.isFinite(fieldVoltage)||!Double.isFinite(sensedVoltage)||!Double.isFinite(bridgeVoltage)
                ||!Double.isFinite(stabilizer)||sensedVoltage<=0||bridgeVoltage<0)
            throw new IllegalArgumentException("Invalid EXPIC1 operating point");
        ec=sensedVoltage;vb=bridgeVoltage;vs=stabilizer;
        double e0=data.te()>0?fieldVoltage*(data.ke()+saturation(fieldVoltage)):fieldVoltage;
        if(vb<=EPS&&Math.abs(e0)>EPS)throw new IllegalArgumentException("Nonzero excitation requires a bridge source");
        double vr0=vb>EPS?e0/vb:0;
        piMax=Math.max(Math.max(data.vr1(),data.vr2()),vr0);piMin=Math.min(Math.min(data.vr1(),data.vr2()),vr0);
        vrMax=Math.max(Math.max(data.vrmax(),data.vrmin()),vr0);vrMin=Math.min(Math.min(data.vrmax(),data.vrmin()),vr0);
        efdMax=Math.max(Math.max(data.efdmax(),data.efdmin()),e0);efdMin=Math.min(Math.min(data.efdmax(),data.efdmin()),e0);
        reference=ec-vs;
        state[0]=ec;state[1]=vr0;state[2]=data.ta2()>0?(data.ta2()-data.ta3())*vr0:vr0;
        state[3]=vr0;state[4]=fieldVoltage;state[5]=data.kf()*vr0;state[6]=0;
        System.arraycopy(state,0,trial,0,7);active=state;predicting=false;initialized=true;
        derivatives(active,rate);record(Stage.INITIALIZATION,active,rate);
    }
    public void step(double dt,int flag,double sensedVoltage,double bridgeVoltage,double stabilizer) {
        requireInitialized();observe(sensedVoltage,bridgeVoltage,stabilizer);
        if(!Double.isFinite(dt)||dt<0||(flag!=0&&flag!=1))throw new IllegalArgumentException("Invalid EXPIC1 integration boundary");
        if(dt==0){refreshBypassObservations();return;}
        if(flag==0) {
            if(predicting)throw new IllegalStateException("Unaccepted EXPIC1 predictor");
            derivatives(state,oldRate);record(Stage.PREDICTOR,state,oldRate);
            for(int i=0;i<7;i++)trial[i]=state[i]+dt*oldRate[i];
            trial[1]=clamp(trial[1],piMin,piMax);active=trial;predicting=true;
        } else {
            if(!predicting)throw new IllegalStateException("EXPIC1 corrector without predictor");
            derivatives(trial,rate);record(Stage.CORRECTOR,trial,rate);
            for(int i=0;i<7;i++)state[i]+=.5*dt*(oldRate[i]+rate[i]);
            state[1]=clamp(state[1],piMin,piMax);active=state;predicting=false;
        }
        refreshBypassObservations();
    }
    public void euler(double dt,double sensedVoltage,double bridgeVoltage,double stabilizer) {
        requireInitialized();observe(sensedVoltage,bridgeVoltage,stabilizer);
        if(!Double.isFinite(dt)||dt<0||predicting)throw new IllegalArgumentException("Invalid EXPIC1 Euler boundary");
        derivatives(state,rate);record(Stage.APPLIED_STEP,state,rate);
        for(int i=0;i<7;i++)state[i]+=dt*rate[i];state[1]=clamp(state[1],piMin,piMax);active=state;refreshBypassObservations();
    }
    @Override public int staggeredStateCount(){return state.length;}
    @Override public void readStaggeredState(double[] values,int offset){
        requireInitialized();System.arraycopy(active,0,values,offset,state.length);
    }
    @Override public void evaluateStaggered(double stepSec,double[] rates,int offset){
        requireInitialized();
        if(predicting)throw new IllegalStateException("Unaccepted EXPIC1 predictor");
        derivatives(state,rate);record(Stage.STAGGERED,state,rate);
        System.arraycopy(rate,0,rates,offset,state.length);
    }
    @Override public void applyStaggeredState(double[] values,int offset,double stepSec){
        requireInitialized();
        if(predicting)throw new IllegalStateException("Unaccepted EXPIC1 predictor");
        System.arraycopy(values,offset,state,0,state.length);
        state[1]=clamp(state[1],piMin,piMax);active=state;refreshBypassObservations();
    }
    public void observe(double sensedVoltage,double bridgeVoltage,double stabilizer) {
        if(!Double.isFinite(sensedVoltage)||!Double.isFinite(bridgeVoltage)||!Double.isFinite(stabilizer)||bridgeVoltage<0)
            throw new IllegalArgumentException("Finite EXPIC1 boundary required");
        ec=sensedVoltage;vb=bridgeVoltage;vs=stabilizer;
    }
    private void derivatives(double[] x,double[] dx) {
        algebraics(x);
        dx[0]=data.tr()>0?(ec-x[0])/data.tr():0;
        double integralRate=data.ka()*a[1];
        dx[1]=(x[1]>=piMax&&integralRate>0||x[1]<=piMin&&integralRate<0)?0:integralRate;
        dx[2]=data.ta2()>0?a[2]-a[3]:0;
        dx[3]=data.ta4()>0?(a[3]-x[3])/data.ta4():0;
        dx[4]=data.te()>0?(a[5]-x[4]*(data.ke()+saturation(x[4])))/data.te():0;
        dx[5]=data.kf()>0?(data.kf()*a[4]-x[5])/data.tf1():0;
        dx[6]=data.kf()>0&&data.tf2()>0?(a[6]-x[6])/data.tf2():0;
    }
    // a: sensed, error, VA, lead-lag output, limited VR, E0, first feedback output, EFD.
    private void algebraics(double[] x) {
        a[0]=data.tr()>0?x[0]:ec;
        double error0=reference+vs-a[0],proportional=data.ka()*data.ta1();
        double leadGain=data.ta2()>0?data.ta3()/data.ta2():1;
        double leadBias=data.ta2()>0?x[2]/data.ta2():0;
        double feedbackGain=data.tf2()==0?data.kf()/data.tf1():0;
        double feedbackBias=data.tf2()>0?x[6]:-x[5]/data.tf1();
        double vr;
        if(data.ta4()>0)vr=clamp(x[3],vrMin,vrMax);
        else {
            // Solve the instantaneous PI/lead-lag/rate-feedback loop analytically.
            double base=x[1]+proportional*(error0-feedbackBias);
            double candidate=(leadBias+leadGain*base)/(1+leadGain*proportional*feedbackGain);
            double limitedPi=clamp(base-proportional*feedbackGain*candidate,piMin,piMax);
            vr=clamp(leadBias+leadGain*limitedPi,vrMin,vrMax);
        }
        a[6]=(data.kf()*vr-x[5])/data.tf1();
        double feedback=data.tf2()>0?x[6]:a[6];
        a[1]=error0-feedback;a[2]=clamp(x[1]+proportional*a[1],piMin,piMax);
        a[3]=leadBias+leadGain*a[2];a[4]=vr;a[5]=clamp(vb*vr,efdMin,efdMax);
        a[7]=data.te()>0?x[4]:a[5];
    }
    private void record(Stage stage,double[] x,double[] dx) {
        monitor.begin(stage);for(int i=0;i<7;i++)if(isIntegrated(i))monitor.record(i,x[i],dx[i]);
    }
    private boolean isIntegrated(int i) {
        return switch(i){case 0->data.tr()>0;case 2->data.ta2()>0;case 3->data.ta4()>0;
            case 4->data.te()>0;case 5->data.kf()>0;case 6->data.kf()>0&&data.tf2()>0;default->true;};
    }
    public double output(){requireInitialized();algebraics(active);return a[7];}
    public double getReference(){return reference;}
    public void setReference(double value){if(!Double.isFinite(value))throw new IllegalArgumentException("Finite reference required");reference=value;}
    public double getRegulatorOutput(){algebraics(active);return a[4];}
    public double getPiOutput(){algebraics(active);return a[2];}
    public double[] getNativeStates(){
        requireInitialized();return active.clone();
    }
    @Override public Map<String,Double> getIntegrationStates(){
        var result=new LinkedHashMap<String,Double>();for(int i=0;i<7;i++)if(isIntegrated(i))result.put(NAMES[i],active[i]);return java.util.Collections.unmodifiableMap(result);
    }
    @Override public IntegrationStateMonitor.Snapshot getStateDerivativeSnapshot(){return monitor.snapshot();}
    public Map<String,Double> getNamedStates(){
        var result=new LinkedHashMap<String,Double>();double[] values=getNativeStates();
        for(int i=0;i<7;i++)result.put(NAMES[i],values[i]);result.put("Field Output",output());result.put("Regulator Output",getRegulatorOutput());
        return java.util.Collections.unmodifiableMap(result);
    }
    public double saturation(double fieldVoltage){return Exac1Exciter.saturation(fieldVoltage,data.e1(),data.se1(),data.e2(),data.se2());}
    public double bridgeVoltage(Complex voltage,Complex current,double fieldCurrent) {
        if(data.kp()==0&&data.ki()==0)return 1;
        double ve=voltage.multiply(data.kp()).add(current.multiply(new Complex(0,data.ki()))).abs();
        return ve>EPS?ve*Exac1Exciter.rectifierFactor(data.kc()*fieldCurrent/ve):0;
    }
    private void refreshBypassObservations(){
        algebraics(active);if(data.tr()==0)active[0]=ec;if(data.ta2()==0)active[2]=a[2];if(data.ta4()==0)active[3]=a[3];
        if(data.tf2()==0)active[6]=a[6];
    }
    private void requireInitialized(){if(!initialized)throw new IllegalStateException("Initialize EXPIC1 first");}
    private static double clamp(double value,double low,double high){return Math.max(low,Math.min(high,value));}
}
