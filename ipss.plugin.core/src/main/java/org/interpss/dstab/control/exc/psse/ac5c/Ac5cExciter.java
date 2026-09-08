package org.interpss.dstab.control.exc.psse.ac5c;

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

/** IEEE 421.5-2016 / PSS/E AC5C simplified rotating-rectifier exciter. */
@AnController(input="mach.vt",output="this.outputSignal",refPoint="this.reference",display={})
public class Ac5cExciter extends AnnotateExciter implements IntegrationStepAware {
    public static final int INPUT_UNUSED=0,INPUT_SUMMATION=1,INPUT_TAKEOVER=2;
    private static final double EPS=1e-12;
    private static final int VE=0,VSENSE=1,VR=2,FEEDBACK_1=3,FEEDBACK_2=4;
    private final Ac5cData data;
    private final boolean simplifiedEsac5a;
    private final double[] state=new double[5],trial=new double[5],oldDerivative=new double[5];
    private double[] active=state;
    private boolean initialized,hasVuel,hasVoel,hasVsclSum,hasVsclUel,hasVsclOel;
    private double integrationStep,minimumTimeConstantMultiplier=1;
    private double vuel,voel,vsclSum,vsclUel,vsclOel;

    public int oelLocation,uelLocation,sclLocation;
    public double tr,ka,ta,vamax,vamin,ke,te,kf,tf1,tf2,tf3;
    public double e1,se1,e2,se2,kc,kd,vfemax,vemin,spdmlt;
    public double reference,outputSignal;

    public Ac5cExciter(String id,Ac5cData data,Machine machine){
        this(id,"AC5C",data,machine,false);
    }
    protected Ac5cExciter(String id,String modelName,Ac5cData data,Machine machine,
            boolean simplifiedEsac5a){
        super(id,modelName,"PSS/E");this.data=data;this.simplifiedEsac5a=simplifiedEsac5a;
        this._data=data;setMachine(machine);
    }
    public Ac5cData getData(){return data;}
    public void setVuel(double v){vuel=v;hasVuel=true;}public void setVoel(double v){voel=v;hasVoel=true;}
    public void setVsclSum(double v){vsclSum=v;hasVsclSum=true;}
    public void setVsclUel(double v){vsclUel=v;hasVsclUel=true;}
    public void setVsclOel(double v){vsclOel=v;hasVsclOel=true;}
    @Override public void configureIntegrationStep(double seconds){configureIntegrationStep(seconds,1);}
    public void configureIntegrationStep(double seconds,double multiplier){integrationStep=seconds;minimumTimeConstantMultiplier=multiplier;}

    @Override public boolean initStates(BaseDStabBus<?,?> bus,Machine machine){
        loadAndCorrect();
        if(!validLocation(oelLocation)||!validLocation(uelLocation)||!validLocation(sclLocation)
                ||tr<0||ta<0||te<=EPS||tf1<=EPS||tf2<0||kc<0||kd<0||Math.abs(ka)<=EPS)return false;
        double vt0=bus.getVoltageMag(),ifd0=exciterIfd(machine);
        double targetEfd=machine.getEfd()/(spdmlt!=0?safeSpeed(machine):1);
        double ve0=Exac1Exciter.solveInternalVoltage(targetEfd,kc*ifd0);
        if(!Double.isFinite(vt0)||!Double.isFinite(ve0))return false;
        double upper=fieldUpperLimit(ve0,ifd0);
        if(ve0<vemin-EPS||ve0>upper+EPS)return false;
        double vfe0=fieldFeedback(ve0,ifd0),error0=vfe0/ka;
        vamax=Math.max(vamax,vfe0);vamin=Math.min(vamin,vfe0);
        state[VE]=ve0;state[VSENSE]=vt0;state[VR]=vfe0;
        state[FEEDBACK_1]=vfe0;state[FEEDBACK_2]=vfe0;
        System.arraycopy(state,0,trial,0,state.length);active=state;
        reference=error0+vt0-stabilizerSignal(machine)-summationLimiterInput();
        outputSignal=machine.getEfd();initialized=true;return true;
    }

    private void loadAndCorrect(){
        oelLocation=simplifiedEsac5a?INPUT_UNUSED:data.getOelLocation();
        uelLocation=simplifiedEsac5a?INPUT_UNUSED:data.getUelLocation();
        sclLocation=simplifiedEsac5a?INPUT_UNUSED:data.getSclLocation();
        tr=correctedBypass(data.getTr());ka=data.getKa();ta=correctedBypass(data.getTa());
        vamax=Math.max(data.getVamax(),data.getVamin());vamin=Math.min(data.getVamax(),data.getVamin());
        ke=data.getKe();te=correctedRequired(data.getTe());kf=data.getKf();
        tf1=correctedRequired(data.getTf1());tf2=correctedBypass(data.getTf2());tf3=data.getTf3();
        e1=data.getE1();se1=data.getSe1();e2=data.getE2();se2=data.getSe2();
        kc=simplifiedEsac5a?0:data.getKc();kd=simplifiedEsac5a?0:data.getKd();
        vfemax=simplifiedEsac5a?Double.POSITIVE_INFINITY:data.getVfemax();
        vemin=simplifiedEsac5a?Double.NEGATIVE_INFINITY:data.getVemin();spdmlt=data.getSpdmlt();
    }
    private double minimumResolvedTimeConstant(){return minimumTimeConstantMultiplier*integrationStep;}
    private double correctedBypass(double v){double m=minimumResolvedTimeConstant();if(v>0&&v<.5*m)return 0;if(v>.5*m&&v<m)return m;return v;}
    private double correctedRequired(double v){double m=minimumResolvedTimeConstant();return v>0&&v<m?m:v;}

    @Override public boolean nextStep(double dt,DynamicSimuMethod method,Machine machine,int flag){
        if(!initialized||dt<0)return false;if(dt==0){outputSignal=fieldOutput(active,machine);return true;}
        int stage=method==DynamicSimuMethod.MODIFIED_EULER?flag:2;
        if(stage==0){derivatives(state,oldDerivative,machine);add(state,oldDerivative,dt,trial);constrain(trial,machine);active=trial;}
        else if(stage==1){double[] d=new double[5];derivatives(trial,d,machine);for(int i=0;i<5;i++)state[i]+=.5*(oldDerivative[i]+d[i])*dt;constrain(state,machine);active=state;}
        else{double[] d=new double[5];derivatives(state,d,machine);add(state,d,dt,state);constrain(state,machine);active=state;}
        outputSignal=fieldOutput(active,machine);return true;
    }
    private void derivatives(double[] x,double[] dx,Machine machine){
        Arrays.fill(dx,0);Algebraic a=algebraics(x,machine);
        dx[VSENSE]=lagDerivative(machine.getDStabBus().getVoltageMag(),x[VSENSE],tr);
        double vrRate=ta>EPS?(ka*a.error-x[VR])/ta:0;
        if((x[VR]>=vamax-EPS&&vrRate>0)||(x[VR]<=vamin+EPS&&vrRate<0))vrRate=0;
        dx[VR]=vrRate;
        dx[FEEDBACK_1]=lagDerivative(a.va,x[FEEDBACK_1],tf2);
        dx[FEEDBACK_2]=lagDerivative(a.feedbackLeadLag,x[FEEDBACK_2],tf1);
        double veRate=(a.efe-a.vfe)/te,upper=fieldUpperLimit(x[VE],exciterIfd(machine));
        if((x[VE]>=upper-EPS&&veRate>0)||(x[VE]<=vemin+EPS&&veRate<0))veRate=0;
        dx[VE]=veRate;
    }
    private Algebraic algebraics(double[] x,Machine machine){
        double vt=machine.getDStabBus().getVoltageMag(),sensed=tr>EPS?x[VSENSE]:vt;
        double base=reference-sensed+stabilizerSignal(machine)+summationLimiterInput();
        double direct=tf2>EPS?tf3/tf2:1,stored=tf2>EPS?(1-tf3/tf2)*x[FEEDBACK_1]:0;
        double va;
        if(ta>EPS)va=clamp(x[VR],vamin,vamax);
        else{
            double feedbackOffset=kf*(stored-x[FEEDBACK_2])/tf1;
            va=clamp(ka*(base-feedbackOffset)/(1+ka*kf*direct/tf1),vamin,vamax);
        }
        double lead=direct*va+stored,feedback=kf*(lead-x[FEEDBACK_2])/tf1,error=base-feedback;
        double efe=takeoverLimiterOutput(va),ifd=exciterIfd(machine);
        double field=clamp(x[VE],vemin,fieldUpperLimit(x[VE],ifd)),vfe=fieldFeedback(field,ifd);
        return new Algebraic(sensed,error,va,lead,feedback,efe,field,vfe);
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
        if(ta>EPS)x[VR]=clamp(x[VR],vamin,vamax);
        double ifd=exciterIfd(machine);x[VE]=clamp(x[VE],vemin,fieldUpperLimit(x[VE],ifd));
        for(int i=0;i<x.length;i++)if(!Double.isFinite(x[i]))x[i]=0;
    }
    private double fieldUpperLimit(double field,double ifd){
        double denominator=ke+Exac1Exciter.saturation(Math.max(field,0),e1,se1,e2,se2);
        if(denominator<=EPS)return Double.POSITIVE_INFINITY;
        return Math.max(vemin,(vfemax-kd*ifd)/denominator);
    }
    private double fieldFeedback(double field,double ifd){return field*(ke+Exac1Exciter.saturation(field,e1,se1,e2,se2))+kd*ifd;}
    private double fieldOutput(double[] x,Machine machine){double field=algebraics(x,machine).field,ifd=exciterIfd(machine);double efd=field*Exac1Exciter.rectifierFactor(Math.abs(field)>EPS?kc*ifd/field:0);return spdmlt!=0?efd*safeSpeed(machine):efd;}
    private static boolean validLocation(int v){return v>=INPUT_UNUSED&&v<=INPUT_TAKEOVER;}
    private static double lagDerivative(double input,double value,double time){return time>EPS?(input-value)/time:0;}
    private static void add(double[] x,double[] d,double dt,double[] y){for(int i=0;i<x.length;i++)y[i]=x[i]+d[i]*dt;}
    private static double clamp(double v,double lo,double hi){return Math.max(lo,Math.min(hi,v));}
    private static double stabilizerSignal(Machine m){return m.getStabilizer()==null?0:m.getStabilizer().getOutput(m);}
    private static double exciterIfd(Machine m){double v=m.calculateIfd(MachineIfdBase.EXCITER);return Double.isFinite(v)?v:0;}
    private static double safeSpeed(Machine m){double v=m.getSpeed();return Double.isFinite(v)&&Math.abs(v)>EPS?v:1;}

    public double getSensedVoltage(){return algebraics(active,getMachine()).sensed;}
    public double getRegulatorState(){return active[VR];}public double getRegulatorOutput(){return algebraics(active,getMachine()).va;}
    public double getFeedback1State(){return active[FEEDBACK_1];}public double getFeedback1Output(){return algebraics(active,getMachine()).feedbackLeadLag;}
    public double getFeedback2State(){return active[FEEDBACK_2];}public double getRateFeedback(){return algebraics(active,getMachine()).feedback;}
    public double getExciterInput(){return algebraics(active,getMachine()).efe;}public double getInternalFieldVoltage(){return algebraics(active,getMachine()).field;}
    public double getFieldFeedback(){return algebraics(active,getMachine()).vfe;}public double getDynamicFieldUpperLimit(){return fieldUpperLimit(active[VE],exciterIfd(getMachine()));}
    @Override public double getOutput(Machine machine){outputSignal=fieldOutput(active,machine);return outputSignal;}
    @Override public void setRefPoint(double value){reference=value;}@Override public double getRefPoint(){return reference;}
    private record Algebraic(double sensed,double error,double va,double feedbackLeadLag,double feedback,double efe,double field,double vfe){}
    @Override public AnController getAnController(){return getClass().getAnnotation(AnController.class);}
    @Override public Field getField(String name)throws Exception{return getClass().getField(name);}
    @Override public Object getFieldObject(Field field)throws Exception{return field.get(this);}
}
