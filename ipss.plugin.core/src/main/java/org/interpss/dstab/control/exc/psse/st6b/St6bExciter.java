package org.interpss.dstab.control.exc.psse.st6b;

import java.lang.reflect.Field;
import java.util.Arrays;
import org.interpss.dstab.control.util.IntegrationStepAware;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnnotateExciter;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.MachineIfdBase;

/** IEEE 421.5-2005 ST6B / PSLF ESST6B static excitation system. */
@AnController(input="mach.vt",output="this.outputSignal",refPoint="this.reference",display={})
public final class St6bExciter extends AnnotateExciter implements IntegrationStepAware {
    private static final double EPS=1e-12;
    private static final int EFD=0,VSENSE=1,PID_I=2,PID_D_LAG=3,VG=4;
    private final St6bData data;private final String modelName;
    private final double[] state=new double[5],trial=new double[5],oldDerivative=new double[5];
    private double[] active=state;private boolean initialized,hasVuel,hasVoel;
    private double integrationStep,minimumTimeConstantMultiplier=1,vuel,voel;
    public int oel,vrmult;public double tr,kpa,kia,kda,tda,vamax,vamin,kff,km,kcl,klr,ilr,vrmax,vrmin,kg,tg,ts;
    public double reference,outputSignal;
    public St6bExciter(String id,String modelName,St6bData data,Machine machine){super(id,modelName,"IEEE");this.data=data;this._data=data;this.modelName=modelName;setMachine(machine);}
    public St6bData getData(){return data;}public String getModelName(){return modelName;}
    public void setVuel(double v){vuel=v;hasVuel=true;}public double getVuel(){return vuel;}
    public void setVoel(double v){voel=v;hasVoel=true;}public double getVoel(){return voel;}
    @Override public void configureIntegrationStep(double step){configureIntegrationStep(step,1);}
    public void configureIntegrationStep(double step,double multiplier){integrationStep=step;minimumTimeConstantMultiplier=multiplier;}

    @Override public boolean initStates(BaseDStabBus<?,?> bus,Machine machine){
        loadAndCorrectParameters();double denominator=kff+km;
        if(tr<0||tda<0||tg<0||ts<0||Math.abs(denominator)<=EPS)return false;
        double vt=machine.getDStabBus().getVoltageMag(),efd0=machine.getEfd(),vb=vrmult!=0?vt:1;
        if(!Double.isFinite(efd0)||vb<=EPS)return false;
        double vr0=efd0/vb,vg0=kg*efd0,va0=(vr0+km*vg0)/denominator;
        vrmax=Math.max(vrmax,vr0);vrmin=Math.min(vrmin,vr0);
        if(va0>vamax+EPS||va0<vamin-EPS)return false;
        state[EFD]=efd0;state[VSENSE]=vt;state[PID_I]=va0;state[PID_D_LAG]=0;state[VG]=vg0;
        System.arraycopy(state,0,trial,0,5);active=state;
        reference=vt-stabilizerSignal(machine)+oelBefore()+oelAfter();outputSignal=efd0;initialized=true;return true;
    }
    private void loadAndCorrectParameters(){oel=data.getOel();vrmult=data.getVrmult();tr=correctedBypass(data.getTr());
        kpa=data.getKpa()==0?1:data.getKpa();kia=data.getKia()==0?1:data.getKia();kda=data.getKda();tda=data.getTda();
        vamax=Math.max(data.getVamax(),data.getVamin());vamin=Math.min(data.getVamax(),data.getVamin());
        kff=data.getKff();km=data.getKm();kcl=data.getKcl();klr=data.getKlr();ilr=data.getIlr();
        vrmax=Math.max(data.getVrmax(),data.getVrmin());vrmin=Math.min(data.getVrmax(),data.getVrmin());
        kg=data.getKg();tg=correctedBypass(data.getTg());ts=correctedBypass(data.getTs());}
    private double correctedBypass(double v){double min=minimumTimeConstantMultiplier*integrationStep;if(v>0&&v<.5*min)return 0;if(v>.5*min&&v<min)return min;return v;}

    @Override public boolean nextStep(double dt,DynamicSimuMethod method,Machine machine,int flag){if(!initialized||dt<0)return false;if(dt==0)return true;
        int stage=method==DynamicSimuMethod.MODIFIED_EULER?flag:2;
        if(stage==0){derivatives(state,oldDerivative,machine);for(int i=0;i<5;i++)trial[i]=state[i]+oldDerivative[i]*dt;constrain(trial);active=trial;}
        else if(stage==1){double[] d=new double[5];derivatives(trial,d,machine);for(int i=0;i<5;i++)state[i]+=.5*(oldDerivative[i]+d[i])*dt;constrain(state);active=state;}
        else{double[] d=new double[5];derivatives(state,d,machine);for(int i=0;i<5;i++)state[i]+=d[i]*dt;constrain(state);active=state;}
        outputSignal=algebraics(active,machine).efd;return true;}
    private void derivatives(double[] x,double[] dx,Machine machine){Arrays.fill(dx,0);Algebraic a=algebraics(x,machine);double vt=machine.getDStabBus().getVoltageMag();
        dx[VSENSE]=lagDerivative(vt,x[VSENSE],tr);dx[PID_D_LAG]=lagDerivative(a.error,x[PID_D_LAG],tda);
        double iRate=kia*a.error;if((a.va>=vamax-EPS&&iRate>0)||(a.va<=vamin+EPS&&iRate<0))iRate=0;dx[PID_I]=iRate;
        dx[VG]=lagDerivative(kg*a.exciterInput,x[VG],tg);dx[EFD]=lagDerivative(a.exciterInput,x[EFD],ts);}
    private Algebraic algebraics(double[] x,Machine machine){double vt=machine.getDStabBus().getVoltageMag(),sensed=tr>EPS?x[VSENSE]:vt;
        double before=reference-sensed-oelBefore(),high=hasVuel?Math.max(before,vuel):before;
        double error=high+stabilizerSignal(machine)-oelAfter();double derivative=tda>EPS?kda*(error-x[PID_D_LAG])/tda:0;
        double va=clamp(kpa*error+x[PID_I]+derivative,vamin,vamax),vb=vrmult!=0?vt:1;
        double currentLimit=Math.max(vrmin,klr*(kcl*ilr-exciterIfd(machine))),vg=x[VG],inner=0,vr=0,input=0;
        for(int iteration=0;iteration<(tg>EPS?1:30);iteration++){
            inner=clamp(kff*va+km*(va-vg),vrmin,vrmax);vr=Math.min(inner,currentLimit);input=vr*vb;
            if(tg>EPS)break;double next=kg*input;if(Math.abs(next-vg)<1e-12){vg=next;break;}vg=next;
        }
        double efd=ts>EPS?x[EFD]:input;
        return new Algebraic(sensed,error,va,vg,inner,currentLimit,vr,input,efd);}
    private void constrain(double[] x){
        x[EFD]=Double.isFinite(x[EFD])?x[EFD]:0;
        // Va limits constrain the summed PID output, not the integral state by itself.
        // Anti-windup in derivatives() freezes the integrator when that output saturates.
        x[PID_I]=Double.isFinite(x[PID_I])?x[PID_I]:0;
    }
    private double oelBefore(){return oel==1&&hasVoel?voel:0;}private double oelAfter(){return oel==2&&hasVoel?voel:0;}
    private static double lagDerivative(double in,double state,double t){return t>EPS?(in-state)/t:0;}
    private static double clamp(double v,double lo,double hi){return Math.max(lo,Math.min(hi,v));}
    private static double stabilizerSignal(Machine m){return m.getStabilizer()==null?0:m.getStabilizer().getOutput(m);}
    private static double exciterIfd(Machine m){double v=m.calculateIfd(MachineIfdBase.EXCITER);return Double.isFinite(v)?v:0;}
    public double getSensedVoltage(){return algebraics(active,getMachine()).sensed;}public double getError(){return algebraics(active,getMachine()).error;}
    public double getVaOutput(){return algebraics(active,getMachine()).va;}public double getVgOutput(){return algebraics(active,getMachine()).vg;}
    public double getInnerRegulatorOutput(){return algebraics(active,getMachine()).inner;}public double getCurrentLimitOutput(){return algebraics(active,getMachine()).currentLimit;}
    public double getVrOutput(){return algebraics(active,getMachine()).vr;}public double getExciterInput(){return algebraics(active,getMachine()).exciterInput;}
    @Override public double getOutput(Machine machine){outputSignal=algebraics(active,machine).efd;return outputSignal;}
    @Override public void setRefPoint(double v){reference=v;}@Override public double getRefPoint(){return reference;}
    private record Algebraic(double sensed,double error,double va,double vg,double inner,double currentLimit,double vr,double exciterInput,double efd){}
    @Override public AnController getAnController(){return getClass().getAnnotation(AnController.class);}@Override public Field getField(String n)throws Exception{return getClass().getField(n);}
    @Override public Object getFieldObject(Field f)throws Exception{return f.get(this);}
}
