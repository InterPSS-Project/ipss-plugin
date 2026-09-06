package org.interpss.dstab.control.exc.psse.rexsys;

import java.lang.reflect.Field;
import java.util.Arrays;

import org.interpss.dstab.control.exc.psse.exac1.Exac1Exciter;
import org.interpss.dstab.control.util.IntegrationStepAware;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnnotateExciter;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.MachineIfdBase;

/**
 * PSS/E REXSYS general-purpose rotating excitation system.
 *
 * <p>The ten states and signal order implement the published PowerWorld/WECC
 * block diagram: voltage transducer, voltage PI, two lead-lags, regulator lag,
 * selectable stabilizing feedback, field-current PI, bridge lag, rotating
 * exciter and commutating rectifier.</p>
 */
@AnController(input="mach.vt",output="this.outputSignal",refPoint="this.reference",display={})
public final class RexsysExciter extends AnnotateExciter implements IntegrationStepAware {
    private static final double EPS=1e-12;
    private static final int VE=0, VSENSE=1, VR=2, CURRENT_I=3, VF=4,
            VOLTAGE_I=5, LL1=6, LL2=7, FB_LAG=8, FB_LL=9;
    private final RexsysData data;
    private final double[] state=new double[10],trial=new double[10],oldDerivative=new double[10];
    private double[] active=state;
    private boolean initialized;
    private double integrationStep,minimumTimeConstantMultiplier=1;

    public double tr,kvp,kvi,vimax,ta,tb1,tc1,tb2,tc2,vrmax,vrmin;
    public double kf,tf,tf1,tf2,kip,kii,tp,vfmax,vfmin,kh,ke,te,kc,kd;
    public double e1,se1,e2,se2,reference,outputSignal;
    public int fbf,flimf;

    public RexsysExciter(String id,RexsysData data,Machine machine){
        super(id,"REXSYS","PSS/E"); this.data=data; this._data=data; setMachine(machine);
    }
    public RexsysData getData(){return data;}
    @Override public void configureIntegrationStep(double dt){configureIntegrationStep(dt,1);}
    public void configureIntegrationStep(double dt,double multiplier){
        integrationStep=dt; minimumTimeConstantMultiplier=multiplier;
    }

    @Override public boolean initStates(BaseDStabBus<?,?> bus,Machine machine){
        loadAndCorrectParameters();
        if(tr<0||ta<0||tb1<0||tb2<0||tf<0||tf2<0||tp<0||te<=EPS
                || fbf<0||fbf>2||flimf<0||flimf>1) return false;
        double ifd=exciterIfd(machine);
        double ve0=Exac1Exciter.solveInternalVoltage(machine.getEfd(),kc*ifd);
        if(!Double.isFinite(ve0)) return false;
        double vf0=fieldFeedback(ve0,ifd);
        double vr0=kh*vf0;
        double scale=limitScale(machine);
        vrmax=Math.max(vrmax,vr0/scale); vrmin=Math.min(vrmin,vr0/scale);
        vfmax=Math.max(vfmax,vf0/scale); vfmin=Math.min(vfmin,vf0/scale);
        vimax=Math.max(Math.abs(vimax),Math.abs(vr0));
        state[VE]=ve0; state[VSENSE]=bus.getVoltageMag(); state[VR]=vr0;
        state[CURRENT_I]=vf0; state[VF]=vf0; state[VOLTAGE_I]=vr0;
        state[LL1]=leadLagState(vr0,vr0,tc1,tb1);
        state[LL2]=leadLagState(vr0,vr0,tc2,tb2);
        double selected=feedbackInput(state,machine);
        state[FB_LAG]=selected; state[FB_LL]=0;
        System.arraycopy(state,0,trial,0,state.length); active=state;
        reference=state[VSENSE]-stabilizerSignal(machine);
        outputSignal=rectifierOutput(ve0,ifd); initialized=true; return true;
    }

    private void loadAndCorrectParameters(){
        tr=correctedTransducer(data.getTr()); kvp=data.getKvp(); kvi=data.getKvi();
        vimax=Math.abs(data.getVimax()); ta=correctedBypass(data.getTa());
        tb1=correctedBypass(data.getTb1()); tc1=data.getTc1();
        tb2=correctedBypass(data.getTb2()); tc2=data.getTc2();
        vrmax=Math.max(data.getVrmax(),data.getVrmin()); vrmin=Math.min(data.getVrmax(),data.getVrmin());
        kf=data.getKf(); tf=correctedBypass(data.getTf()); tf1=data.getTf1();
        tf2=correctedBypass(data.getTf2()); fbf=data.getFbf(); kip=data.getKip(); kii=data.getKii();
        tp=correctedMinimum(data.getTp());
        vfmax=Math.max(data.getVfmax(),data.getVfmin()); vfmin=Math.min(data.getVfmax(),data.getVfmin());
        kh=data.getKh(); ke=data.getKe(); te=correctedMinimum(data.getTe()); kc=data.getKc(); kd=data.getKd();
        e1=data.getE1();se1=data.getSe1();e2=data.getE2();se2=data.getSe2();flimf=data.getFlimf();
    }
    private double minTime(){return minimumTimeConstantMultiplier*integrationStep;}
    private double correctedTransducer(double v){double m=minTime();if(v>0&&v<.5*m)return 0;if(v>.5*m&&v<m)return m;return v;}
    private double correctedBypass(double v){double m=minTime();if(v>0&&v<.5*m)return 0;if(v>.5*m&&v<m)return m;return v;}
    private double correctedMinimum(double v){double m=minTime();return v>0&&v<m?m:v;}

    @Override public boolean nextStep(double dt,DynamicSimuMethod method,Machine machine,int flag){
        if(!initialized||dt<0)return false;if(dt==0)return true;
        int stage=method==DynamicSimuMethod.MODIFIED_EULER?flag:2;
        if(stage==0){derivatives(state,oldDerivative,machine);add(state,oldDerivative,dt,trial);constrain(trial,machine);active=trial;}
        else if(stage==1){double[] d=new double[10];derivatives(trial,d,machine);for(int i=0;i<10;i++)state[i]+=.5*(oldDerivative[i]+d[i])*dt;constrain(state,machine);active=state;}
        else {double[] d=new double[10];derivatives(state,d,machine);add(state,d,dt,state);constrain(state,machine);active=state;}
        outputSignal=rectifierOutput(active[VE],exciterIfd(machine));return true;
    }
    private void derivatives(double[] x,double[] dx,Machine machine){
        Arrays.fill(dx,0); Algebraic a=algebraics(x,machine);
        dx[VSENSE]=lagDerivative(machine.getDStabBus().getVoltageMag(),x[VSENSE],tr);
        dx[VOLTAGE_I]=limitedIntegratorDerivative(kvi*a.voltageError,x[VOLTAGE_I],a.voltagePi,-vimax,vimax);
        dx[LL1]=leadLagDerivative(a.voltagePi,x[LL1],tc1,tb1);
        dx[LL2]=leadLagDerivative(a.ll1,x[LL2],tc2,tb2);
        double vrRate=lagDerivative(a.ll2,x[VR],ta);
        dx[VR]=blockedRate(x[VR],vrRate,a.vrMin,a.vrMax);
        double selected=feedbackInput(x,machine);
        dx[FB_LAG]=lagDerivative(selected,x[FB_LAG],tf);
        dx[FB_LL]=leadLagDerivative(a.feedbackWashout,x[FB_LL],tf1,tf2);
        dx[CURRENT_I]=limitedIntegratorDerivative(kii*a.currentError,x[CURRENT_I],a.currentPi,a.vfMin,a.vfMax);
        dx[VF]=blockedRate(x[VF],lagDerivative(a.currentPi,x[VF],tp),a.vfMin,a.vfMax);
        dx[VE]=(a.bridge-fieldFeedback(x[VE],exciterIfd(machine)))/te;
    }
    private Algebraic algebraics(double[] x,Machine machine){
        double vt=machine.getDStabBus().getVoltageMag();
        double sensed=tr>EPS?x[VSENSE]:vt;
        double selected=feedbackInput(x,machine);
        double washout=tf>EPS?kf*(selected-x[FB_LAG])/tf:0;
        double feedback=leadLagOutput(washout,x[FB_LL],tf1,tf2);
        double error=reference+stabilizerSignal(machine)-sensed-feedback;
        double voltagePi=clamp(kvp*error+x[VOLTAGE_I],-vimax,vimax);
        double ll1=leadLagOutput(voltagePi,x[LL1],tc1,tb1);
        double ll2=leadLagOutput(ll1,x[LL2],tc2,tb2);
        double scale=limitScale(machine),rmin=scale*vrmin,rmax=scale*vrmax;
        double regulator=ta>EPS?clamp(x[VR],rmin,rmax):clamp(ll2,rmin,rmax);
        double currentError=regulator-kh*fieldFeedback(x[VE],exciterIfd(machine));
        double fmin=scale*vfmin,fmax=scale*vfmax;
        double currentPi=clamp(kip*currentError+x[CURRENT_I],fmin,fmax);
        double bridge=tp>EPS?clamp(x[VF],fmin,fmax):currentPi;
        return new Algebraic(sensed,error,voltagePi,ll1,ll2,washout,feedback,regulator,currentError,currentPi,bridge,rmin,rmax,fmin,fmax);
    }
    private double feedbackInput(double[] x,Machine machine){
        double ifd=exciterIfd(machine);
        return switch(fbf){case 0->x[VR];case 1->fieldFeedback(x[VE],ifd);
            case 2->rectifierOutput(x[VE],ifd);default->0;};
    }
    private double limitScale(Machine machine){return flimf==1?machine.getDStabBus().getVoltageMag():1;}
    private double fieldFeedback(double ve,double ifd){return ve*(ke+Exac1Exciter.saturation(ve,e1,se1,e2,se2))+kd*ifd;}
    private double rectifierOutput(double ve,double ifd){return Math.abs(ve)<=EPS?0:ve*Exac1Exciter.rectifierFactor(kc*ifd/ve);}
    private static double leadLagState(double input,double output,double tc,double tb){return tb>EPS?output-(tc/tb)*input:0;}
    private static double leadLagOutput(double input,double state,double tc,double tb){return tb>EPS?(tc/tb)*input+state:input;}
    private static double leadLagDerivative(double input,double state,double tc,double tb){return tb>EPS?((1-tc/tb)*input-state)/tb:0;}
    private static double lagDerivative(double input,double state,double t){return t>EPS?(input-state)/t:0;}
    private static double blockedRate(double x,double rate,double min,double max){return (x>=max&&rate>0)||(x<=min&&rate<0)?0:rate;}
    private static double limitedIntegratorDerivative(double rate,double state,double output,double min,double max){return (output>=max&&rate>0)||(output<=min&&rate<0)?0:rate;}
    private static void add(double[] x,double[] d,double dt,double[] y){for(int i=0;i<x.length;i++)y[i]=x[i]+d[i]*dt;}
    private void constrain(double[] x,Machine machine){Algebraic a=algebraics(x,machine);x[VR]=clamp(x[VR],a.vrMin,a.vrMax);x[VF]=clamp(x[VF],a.vfMin,a.vfMax);}
    private static double clamp(double v,double lo,double hi){return Math.max(lo,Math.min(hi,v));}
    private static double stabilizerSignal(Machine m){return m.getStabilizer()==null?0:m.getStabilizer().getOutput(m);}
    private static double exciterIfd(Machine m){double v=m.calculateIfd(MachineIfdBase.EXCITER);return Double.isFinite(v)?v:0;}

    public double getSensedVoltage(){return algebraics(active,getMachine()).sensed;}
    public double getVoltagePiOutput(){return algebraics(active,getMachine()).voltagePi;}
    public double getRegulatorOutput(){return algebraics(active,getMachine()).regulator;}
    public double getCurrentPiOutput(){return algebraics(active,getMachine()).currentPi;}
    public double getBridgeOutput(){return algebraics(active,getMachine()).bridge;}
    public double getInternalFieldVoltage(){return active[VE];}
    @Override public double getOutput(Machine machine){outputSignal=rectifierOutput(active[VE],exciterIfd(machine));return outputSignal;}
    @Override public void setRefPoint(double v){reference=v;} @Override public double getRefPoint(){return reference;}
    private record Algebraic(double sensed,double voltageError,double voltagePi,double ll1,double ll2,
            double feedbackWashout,double feedback,double regulator,double currentError,double currentPi,double bridge,
            double vrMin,double vrMax,double vfMin,double vfMax){}
    @Override public AnController getAnController(){return getClass().getAnnotation(AnController.class);}
    @Override public Field getField(String name)throws Exception{return getClass().getField(name);}
    @Override public Object getFieldObject(Field field)throws Exception{return field.get(this);}
}
