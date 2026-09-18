package org.interpss.dstab.control.exc.psse.ac6c;

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

/** IEEE 421.5-2016 / PSS/E AC6C alternator-rectifier excitation system. */
@AnController(input="mach.vt",output="this.outputSignal",refPoint="this.reference",display={})
public final class Ac6cExciter extends AnnotateExciter implements IntegrationStepAware {
    public static final int INPUT_UNUSED=0,INPUT_SUMMATION=1,INPUT_TAKEOVER=2;
    private static final double EPS=1e-12;
    private static final int VE=0,VSENSE=1,TA_BLOCK=2,VLL=3,VF=4;
    private final Ac6cData data;
    private final double[] state=new double[5],trial=new double[5],oldDerivative=new double[5];
    private double[] active=state;
    private boolean initialized,hasVuel,hasVoel,hasVsclSum,hasVsclUel,hasVsclOel;
    private double integrationStep,minimumTimeConstantMultiplier=1;
    private double vuel,voel,vsclSum,vsclUel,vsclOel;

    public int oelLocation,uelLocation,sclLocation;
    public double tr,ka,ta,tk,tb,tc,vamax,vamin,efemax,efemin,te;
    public double vfelim,kh,vhmax,th,tj,kc,kd,ke,e1,se1,e2,se2,vfemax,vemin,spdmlt;
    public double reference,outputSignal;

    public Ac6cExciter(String id,Ac6cData data,Machine machine){
        super(id,"AC6C","PSS/E");this.data=data;this._data=data;setMachine(machine);
    }
    public Ac6cData getData(){return data;}
    public void setVuel(double v){vuel=v;hasVuel=true;}public void setVoel(double v){voel=v;hasVoel=true;}
    public void setVsclSum(double v){vsclSum=v;hasVsclSum=true;}
    public void setVsclUel(double v){vsclUel=v;hasVsclUel=true;}
    public void setVsclOel(double v){vsclOel=v;hasVsclOel=true;}
    @Override public void configureIntegrationStep(double seconds){configureIntegrationStep(seconds,1);}
    public void configureIntegrationStep(double seconds,double multiplier){integrationStep=seconds;minimumTimeConstantMultiplier=multiplier;}

    @Override public boolean initStates(BaseDStabBus<?,?> bus,Machine machine){
        loadAndCorrect();
        if(!validLocation(oelLocation)||!validLocation(uelLocation)||!validLocation(sclLocation)
                ||tr<0||ta<0||tk<0||tb<0||tc<0||te<=EPS||th<0||tj<0
                ||kc<0||kd<0||vhmax<0||Math.abs(ka)<=EPS)return false;
        double vt0=bus.getVoltageMag(),ifd0=exciterIfd(machine);
        double ve0=Exac1Exciter.solveInternalVoltage(machine.getEfd(),kc*ifd0);
        if(!Double.isFinite(vt0)||vt0<=EPS||!Double.isFinite(ve0))return false;
        double upper=fieldUpperLimit(ve0,ifd0);if(ve0<vemin-EPS||ve0>upper+EPS)return false;
        double vfe0=fieldFeedback(ve0,ifd0),vh0=clamp(kh*(vfe0-vfelim),0,vhmax),vf0=vh0;
        double va0=vfe0+vf0;
        vamax=Math.max(vamax,va0);vamin=Math.min(vamin,va0);
        efemax=Math.max(efemax,vfe0/vt0);efemin=Math.min(efemin,vfe0/vt0);
        double error0=va0/ka;
        state[VE]=ve0;state[VSENSE]=vt0;
        state[TA_BLOCK]=transferState(error0,va0,ka,tk,ta);
        state[VLL]=transferState(va0,va0,1,tc,tb);
        state[VF]=transferState(vh0,vf0,1,tj,th);
        System.arraycopy(state,0,trial,0,state.length);active=state;
        reference=error0+vt0-stabilizerSignal(machine)-summationLimiterInput();
        outputSignal=rectifierOutput(ve0,ifd0,machine);initialized=true;return true;
    }

    private void loadAndCorrect(){
        oelLocation=data.getOelLocation();uelLocation=data.getUelLocation();sclLocation=data.getSclLocation();
        tr=correctedTransducer(data.getTr());ka=data.getKa();ta=data.getTa();tk=data.getTk();
        tb=data.getTb();tc=data.getTc();vamax=Math.max(data.getVamax(),data.getVamin());
        vamin=Math.min(data.getVamax(),data.getVamin());efemax=Math.max(data.getEfemax(),data.getEfemin());
        efemin=Math.min(data.getEfemax(),data.getEfemin());te=correctedRequired(data.getTe());
        vfelim=data.getVfelim();kh=data.getKh();vhmax=data.getVhmax();th=data.getTh();tj=data.getTj();
        kc=data.getKc();kd=data.getKd();ke=data.getKe();e1=data.getE1();se1=data.getSe1();
        e2=data.getE2();se2=data.getSe2();vfemax=data.getVfemax();vemin=data.getVemin();spdmlt=data.getSpdmlt();
    }
    private double minimumResolvedTimeConstant(){return minimumTimeConstantMultiplier*integrationStep;}
    private double correctedTransducer(double v){double m=minimumResolvedTimeConstant();if(v>0&&v<.5*m)return 0;if(v>.5*m&&v<m)return m;return v;}
    private double correctedRequired(double v){double m=minimumResolvedTimeConstant();return v>0&&v<m?m:v;}

    @Override public boolean nextStep(double dt,DynamicSimuMethod method,Machine machine,int flag){
        if(!initialized||dt<0)return false;if(dt==0){outputSignal=rectifierOutput(active[VE],exciterIfd(machine),machine);return true;}
        int stage=method==DynamicSimuMethod.MODIFIED_EULER?flag:2;
        if(stage==0){derivatives(state,oldDerivative,machine);add(state,oldDerivative,dt,trial);constrain(trial,machine);active=trial;}
        else if(stage==1){double[] d=new double[5];derivatives(trial,d,machine);for(int i=0;i<5;i++)state[i]+=.5*(oldDerivative[i]+d[i])*dt;constrain(state,machine);active=state;}
        else{double[] d=new double[5];derivatives(state,d,machine);add(state,d,dt,state);constrain(state,machine);active=state;}
        outputSignal=rectifierOutput(active[VE],exciterIfd(machine),machine);return true;
    }
    private void derivatives(double[] x,double[] dx,Machine machine){
        Arrays.fill(dx,0);Algebraic a=algebraics(x,machine);
        dx[VSENSE]=lagDerivative(machine.getDStabBus().getVoltageMag(),x[VSENSE],tr);
        dx[TA_BLOCK]=transferDerivative(a.error,x[TA_BLOCK],ka,tk,ta);
        dx[VLL]=transferDerivative(a.taOutput,x[VLL],1,tc,tb);
        dx[VF]=transferDerivative(a.vh,x[VF],1,tj,th);
        double veRate=(a.efe-a.vfe)/te,upper=fieldUpperLimit(x[VE],exciterIfd(machine));
        if((x[VE]>=upper-EPS&&veRate>0)||(x[VE]<=vemin+EPS&&veRate<0))veRate=0;
        dx[VE]=veRate;
    }
    private Algebraic algebraics(double[] x,Machine machine){
        double vt=machine.getDStabBus().getVoltageMag(),sensed=tr>EPS?x[VSENSE]:vt;
        double error=reference+stabilizerSignal(machine)-sensed+summationLimiterInput();
        double taOutput=transferOutput(error,x[TA_BLOCK],ka,tk,ta);
        double va=clamp(transferOutput(taOutput,x[VLL],1,tc,tb),vamin,vamax);
        double gate=takeoverLimiterOutput(va),ifd=exciterIfd(machine),field=clamp(x[VE],vemin,fieldUpperLimit(x[VE],ifd));
        double vfe=fieldFeedback(field,ifd),vh=clamp(kh*(vfe-vfelim),0,vhmax);
        double vf=transferOutput(vh,x[VF],1,tj,th);
        double efe=clamp(gate-vf,vt*efemin,vt*efemax);
        return new Algebraic(sensed,error,taOutput,va,gate,field,vfe,vh,vf,efe);
    }
    private double summationLimiterInput(){
        double v=0;if(uelLocation==INPUT_SUMMATION&&hasVuel)v+=vuel;
        if(oelLocation==INPUT_SUMMATION&&hasVoel)v-=voel;
        if(sclLocation==INPUT_SUMMATION&&hasVsclSum)v-=vsclSum;return v;
    }
    private double takeoverLimiterOutput(double v){
        if(uelLocation==INPUT_TAKEOVER&&hasVuel)v=Math.max(v,vuel);
        if(sclLocation==INPUT_TAKEOVER&&hasVsclUel)v=Math.max(v,vsclUel);
        if(oelLocation==INPUT_TAKEOVER&&hasVoel)v=Math.min(v,voel);
        if(sclLocation==INPUT_TAKEOVER&&hasVsclOel)v=Math.min(v,vsclOel);return v;
    }
    private void constrain(double[] x,Machine machine){
        double ifd=exciterIfd(machine);x[VE]=clamp(x[VE],vemin,fieldUpperLimit(x[VE],ifd));
        for(int i=0;i<x.length;i++)if(!Double.isFinite(x[i]))x[i]=0;
    }
    private double fieldUpperLimit(double field,double ifd){
        double denominator=ke+Exac1Exciter.saturation(Math.max(field,0),e1,se1,e2,se2);
        if(denominator<=EPS)return Double.POSITIVE_INFINITY;
        return Math.max(vemin,(vfemax-kd*ifd)/denominator);
    }
    private double fieldFeedback(double field,double ifd){return field*(ke+Exac1Exciter.saturation(field,e1,se1,e2,se2))+kd*ifd;}
    private double rectifierOutput(double field,double ifd,Machine machine){if(Math.abs(field)<=EPS)return 0;double efd=field*Exac1Exciter.rectifierFactor(kc*ifd/field);return spdmlt!=0?efd*machine.getSpeed():efd;}
    private static double transferState(double input,double output,double gain,double numeratorT,double denominatorT){return denominatorT>EPS?output-gain*numeratorT/denominatorT*input:0;}
    private static double transferOutput(double input,double state,double gain,double numeratorT,double denominatorT){return denominatorT>EPS?gain*numeratorT/denominatorT*input+state:gain*input;}
    private static double transferDerivative(double input,double state,double gain,double numeratorT,double denominatorT){return denominatorT>EPS?(gain*(1-numeratorT/denominatorT)*input-state)/denominatorT:0;}
    private static double lagDerivative(double input,double state,double time){return time>EPS?(input-state)/time:0;}
    private static void add(double[] x,double[] d,double dt,double[] y){for(int i=0;i<x.length;i++)y[i]=x[i]+d[i]*dt;}
    private static double clamp(double v,double lo,double hi){return Math.max(lo,Math.min(hi,v));}
    private static boolean validLocation(int v){return v>=INPUT_UNUSED&&v<=INPUT_TAKEOVER;}
    private static double stabilizerSignal(Machine m){return m.getStabilizer()==null?0:m.getStabilizer().getOutput(m);}
    private static double exciterIfd(Machine m){double v=m.calculateIfd(MachineIfdBase.EXCITER);return Double.isFinite(v)?v:0;}

    public double getInternalFieldVoltage(){return algebraics(active,getMachine()).field;}
    public double getSensedVoltage(){return algebraics(active,getMachine()).sensed;}
    public double getTaOutput(){return algebraics(active,getMachine()).taOutput;}
    public double getVaOutput(){return algebraics(active,getMachine()).va;}
    public double getGateOutput(){return algebraics(active,getMachine()).gate;}
    public double getFieldFeedback(){return algebraics(active,getMachine()).vfe;}
    public double getVhOutput(){return algebraics(active,getMachine()).vh;}
    public double getFeedbackOutput(){return algebraics(active,getMachine()).vf;}
    public double getExciterInput(){return algebraics(active,getMachine()).efe;}
    public double getDynamicFieldUpperLimit(){return fieldUpperLimit(active[VE],exciterIfd(getMachine()));}
    @Override public double getOutput(Machine machine){outputSignal=rectifierOutput(algebraics(active,machine).field,exciterIfd(machine),machine);return outputSignal;}
    @Override public void setRefPoint(double value){reference=value;}@Override public double getRefPoint(){return reference;}
    private record Algebraic(double sensed,double error,double taOutput,double va,double gate,double field,double vfe,double vh,double vf,double efe){}
    @Override public AnController getAnController(){return getClass().getAnnotation(AnController.class);}
    @Override public Field getField(String name)throws Exception{return getClass().getField(name);}
    @Override public Object getFieldObject(Field field)throws Exception{return field.get(this);}
}
