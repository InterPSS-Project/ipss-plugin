package org.interpss.dstab.control.exc.psse.st7b;

import java.lang.reflect.Field;
import java.util.Arrays;

import org.interpss.dstab.control.util.IntegrationStepAware;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnnotateExciter;
import com.interpss.dstab.mach.Machine;

/** IEEE 421.5-2005 ST7B / PSLF ESST7B excitation system. */
@AnController(input="mach.vt",output="this.outputSignal",refPoint="this.reference",display={})
public final class St7bExciter extends AnnotateExciter implements IntegrationStepAware {
    private static final double EPS=1e-12;
    private static final int EFIELD=0,VSENSE=1,INPUT_LL=2,LL2=3,FEEDBACK_LAG=4;
    private final St7bData data;private final String modelName;
    private final double[] state=new double[5],trial=new double[5],oldDerivative=new double[5];
    private double[] active=state;private boolean initialized,vuelConfigured,voelConfigured;
    private double integrationStep,minimumTimeConstantMultiplier=1;
    public int oel,uel;public double tr,tg,tf,vmax,vmin,kpa,vrmax,vrmin,kh,kl,tc,tb,kia,tia,ts;
    public double reference,outputSignal,vuel,voel,vdroop,vscl;
    public St7bExciter(String id,String modelName,St7bData data,Machine machine){super(id,modelName,"IEEE");this.modelName=modelName;this.data=data;this._data=data;setMachine(machine);}
    public St7bData getData(){return data;}public String getModelName(){return modelName;}
    @Override public void configureIntegrationStep(double stepSeconds){configureIntegrationStep(stepSeconds,1);}
    public void configureIntegrationStep(double stepSeconds,double multiplier){integrationStep=stepSeconds;minimumTimeConstantMultiplier=multiplier;}
    @Override public boolean initStates(BaseDStabBus<?,?> bus,Machine machine){
        loadAndCorrect();if(tr<0||tf<0||tb<0||tia<=EPS||ts<0||kpa<=EPS||kia<0||kh<0||kl<0)return false;
        double vt0=bus.getVoltageMag(),efd0=machine.getEfd();if(vt0<=EPS||!Double.isFinite(efd0))return false;
        vrmax=Math.max(vrmax,efd0/vt0);vrmin=Math.min(vrmin,efd0/vt0);
        double error0=efd0/kpa,vref0=vt0+error0-stabilizerSignal(machine)-vdroop-vscl;
        vmax=Math.max(vmax,vref0);vmin=Math.min(vmin,vref0);reference=vref0;
        state[EFIELD]=efd0;state[VSENSE]=vt0;state[INPUT_LL]=transferState(vt0,vt0,tg,tf);
        state[LL2]=transferState(efd0,efd0,tc,tb);state[FEEDBACK_LAG]=efd0;
        System.arraycopy(state,0,trial,0,5);active=state;outputSignal=efd0;initialized=true;return true;
    }
    private void loadAndCorrect(){oel=normalizeSelector(data.getOel());uel=normalizeSelector(data.getUel());tr=correctedTransducer(data.getTr());
        tg=data.getTg();tf=data.getTf();vmax=Math.max(data.getVmax(),data.getVmin());vmin=Math.min(data.getVmax(),data.getVmin());
        kpa=data.getKpa();vrmax=Math.max(data.getVrmax(),data.getVrmin());vrmin=Math.min(data.getVrmax(),data.getVrmin());
        kh=data.getKh();kl=data.getKl();tc=data.getTc();tb=data.getTb();kia=data.getKia();tia=data.getTia();ts=correctedFiring(data.getTs());
        if(!voelConfigured)voel=oel>=2?Double.POSITIVE_INFINITY:0;if(!vuelConfigured)vuel=uel>=2?Double.NEGATIVE_INFINITY:0;}
    private static int normalizeSelector(int v){return v>=1&&v<=3?v:0;}
    private double minTime(){return minimumTimeConstantMultiplier*integrationStep;}
    private double correctedTransducer(double v){double m=minTime();if(v>0&&v<.25*m)return 0;if(v>.25*m&&v<.5*m)return .5*m;return v;}
    private double correctedFiring(double v){double m=minTime();if(v>0&&v<.5*m)return 0;if(v>.5*m&&v<m)return m;return v;}
    @Override public boolean nextStep(double dt,DynamicSimuMethod method,Machine machine,int flag){
        if(!initialized||dt<0)return false;if(dt==0){outputSignal=output(active,machine);return true;}int stage=method==DynamicSimuMethod.MODIFIED_EULER?flag:2;
        if(stage==0){derivatives(state,oldDerivative,machine);add(state,oldDerivative,dt,trial);constrain(trial,machine);active=trial;}
        else if(stage==1){double[] d=new double[5];derivatives(trial,d,machine);for(int i=0;i<5;i++)state[i]+=.5*(oldDerivative[i]+d[i])*dt;constrain(state,machine);active=state;}
        else{double[] d=new double[5];derivatives(state,d,machine);add(state,d,dt,state);constrain(state,machine);active=state;}
        outputSignal=output(active,machine);return true;
    }
    private void derivatives(double[] x,double[] dx,Machine machine){Arrays.fill(dx,0);Algebraic a=algebraics(x,machine);
        dx[VSENSE]=tr>EPS?(machine.getDStabBus().getVoltageMag()-x[VSENSE])/tr:0;
        dx[INPUT_LL]=transferDerivative(a.sensed,x[INPUT_LL],tg,tf);dx[LL2]=transferDerivative(a.regulator,x[LL2],tc,tb);
        dx[FEEDBACK_LAG]=(a.preField-x[FEEDBACK_LAG])/tia;if(ts>EPS)dx[EFIELD]=(a.preField-x[EFIELD])/ts;
    }
    private Algebraic algebraics(double[] x,Machine machine){
        double vt=machine.getDStabBus().getVoltageMag(),sensed=tr>EPS?x[VSENSE]:vt,inputLl=transferOutput(sensed,x[INPUT_LL],tg,tf);
        double rawRef=reference+vdroop+vscl;if(oel==1)rawRef+=voel;if(uel==1)rawRef+=vuel;
        double gatedRef=oel==2?Math.min(rawRef,voel):rawRef;if(uel==2)gatedRef=Math.max(gatedRef,vuel);
        double vrefFb=clamp(gatedRef,vmin,vmax),error=vrefFb+stabilizerSignal(machine)-inputLl,amplifier=kpa*error;
        double beta=kia/tia,feedback=0;
        for(int i=0;i<12;i++){FieldPath p=fieldPath(amplifier,x,vt,feedback);double residual=feedback-beta*(p.preField-x[FEEDBACK_LAG]);
            if(Math.abs(residual)<1e-12)break;double h=1e-7*(1+Math.abs(feedback));FieldPath shifted=fieldPath(amplifier,x,vt,feedback+h);
            double slope=1-beta*(shifted.preField-p.preField)/h;
            feedback=Math.abs(slope)>1e-10?feedback-residual/slope:beta*(p.preField-x[FEEDBACK_LAG]);if(!Double.isFinite(feedback)){feedback=0;break;}}
        FieldPath p=fieldPath(amplifier,x,vt,feedback);return new Algebraic(sensed,inputLl,vrefFb,error,amplifier,p.regulator,p.ll2,feedback,p.preField);
    }
    private FieldPath fieldPath(double amplifier,double[] x,double vt,double feedback){double lower=vt*vrmin-kh*feedback,upper=Math.max(lower,vt*vrmax-kl*feedback);
        double regulator=clamp(amplifier,lower,upper),ll2=transferOutput(regulator,x[LL2],tc,tb),pre=ll2+feedback;
        if(oel==3)pre=Math.min(pre,voel);if(uel==3)pre=Math.max(pre,vuel);pre=clamp(pre,vt*vrmin,vt*vrmax);return new FieldPath(regulator,ll2,pre);}
    private double output(double[] x,Machine machine){return ts>EPS?x[EFIELD]:algebraics(x,machine).preField;}
    private void constrain(double[] x,Machine machine){double vt=machine.getDStabBus().getVoltageMag();if(ts>EPS)x[EFIELD]=clamp(x[EFIELD],vt*vrmin,vt*vrmax);for(int i=0;i<5;i++)if(!Double.isFinite(x[i]))x[i]=0;}
    private static double transferState(double input,double output,double lead,double lag){return lag>EPS?output-lead/lag*input:0;}
    private static double transferOutput(double input,double x,double lead,double lag){return lag>EPS?lead/lag*input+x:input;}
    private static double transferDerivative(double input,double x,double lead,double lag){return lag>EPS?((1-lead/lag)*input-x)/lag:0;}
    private static void add(double[] x,double[] d,double dt,double[] y){for(int i=0;i<x.length;i++)y[i]=x[i]+d[i]*dt;}
    private static double clamp(double v,double lo,double hi){return Math.max(lo,Math.min(hi,v));}
    private static double stabilizerSignal(Machine m){return m.getStabilizer()==null?0:m.getStabilizer().getOutput(m);}
    public void setVuel(double v){vuel=v;vuelConfigured=true;}public void setVoel(double v){voel=v;voelConfigured=true;}public void setVdroop(double v){vdroop=v;}public void setVscl(double v){vscl=v;}
    public double getSensedVoltage(){return algebraics(active,getMachine()).sensed;}public double getInputLeadLag(){return algebraics(active,getMachine()).inputLl;}
    public double getReferenceFeedback(){return algebraics(active,getMachine()).vrefFb;}public double getVoltageError(){return algebraics(active,getMachine()).error;}
    public double getAmplifierOutput(){return algebraics(active,getMachine()).amplifier;}public double getRegulatorOutput(){return algebraics(active,getMachine()).regulator;}
    public double getSecondLeadLagOutput(){return algebraics(active,getMachine()).ll2;}public double getFeedbackOutput(){return algebraics(active,getMachine()).feedback;}
    public double getPreFiringField(){return algebraics(active,getMachine()).preField;}public double getInternalFieldVoltage(){return active[EFIELD];}
    @Override public double getOutput(Machine machine){outputSignal=output(active,machine);return outputSignal;}@Override public void setRefPoint(double v){reference=v;}@Override public double getRefPoint(){return reference;}
    private record Algebraic(double sensed,double inputLl,double vrefFb,double error,double amplifier,double regulator,double ll2,double feedback,double preField){}
    private record FieldPath(double regulator,double ll2,double preField){}
    @Override public AnController getAnController(){return getClass().getAnnotation(AnController.class);}@Override public Field getField(String n)throws Exception{return getClass().getField(n);}@Override public Object getFieldObject(Field f)throws Exception{return f.get(this);}
}
