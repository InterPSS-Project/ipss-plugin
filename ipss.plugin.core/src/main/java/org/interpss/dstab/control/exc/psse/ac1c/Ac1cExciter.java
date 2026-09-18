package org.interpss.dstab.control.exc.psse.ac1c;

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

/** IEEE 421.5-2016 / PSS/E AC1C rotating AC excitation system. */
@AnController(input="mach.vt", output="this.outputSignal",
        refPoint="this.reference", display={})
public final class Ac1cExciter extends AnnotateExciter implements IntegrationStepAware {
    public static final int INPUT_UNUSED=0, INPUT_SUMMATION=1, INPUT_TAKEOVER=2;
    private static final double EPS=1.0e-12;
    private static final int VE=0, VSENSE=1, LEAD_LAG=2, VA=3, VFE_LAG=4;

    private final Ac1cData data;
    private final double[] state=new double[5], trial=new double[5], oldDerivative=new double[5];
    private double[] active=state;
    private boolean initialized, hasVuel, hasVoel, hasVsclSum, hasVsclUel, hasVsclOel;
    private double integrationStep, minimumTimeConstantMultiplier=1.0;
    private double vuel, voel, vsclSum, vsclUel, vsclOel;

    public int oelLocation, uelLocation, sclLocation;
    public double tr,tb,tc,ka,ta,vamax,vamin,te,kf,tf,kc,kd,ke;
    public double e1,se1,e2,se2,efemax,efemin,vfemax,vemin;
    public double reference, outputSignal;

    public Ac1cExciter(String id,Ac1cData data,Machine machine){
        super(id,"AC1C","PSS/E");this.data=data;this._data=data;setMachine(machine);
    }

    public Ac1cData getData(){return data;}
    public void setVuel(double v){vuel=v;hasVuel=true;} public double getVuel(){return vuel;}
    public void setVoel(double v){voel=v;hasVoel=true;} public double getVoel(){return voel;}
    public void setVsclSum(double v){vsclSum=v;hasVsclSum=true;}
    public void setVsclUel(double v){vsclUel=v;hasVsclUel=true;}
    public void setVsclOel(double v){vsclOel=v;hasVsclOel=true;}

    @Override public void configureIntegrationStep(double seconds){configureIntegrationStep(seconds,1.0);}
    public void configureIntegrationStep(double seconds,double multiplier){
        integrationStep=seconds;minimumTimeConstantMultiplier=multiplier;
    }

    @Override public boolean initStates(BaseDStabBus<?,?> bus,Machine machine){
        loadAndCorrectParameters();
        if(!validLocation(oelLocation)||!validLocation(uelLocation)||!validLocation(sclLocation)
                ||tr<0||tb<0||ta<0||te<0||tf<0||kc<0||kd<0||Math.abs(ka)<=EPS)return false;
        double ifd=exciterIfd(machine);
        double ve0=Exac1Exciter.solveInternalVoltage(machine.getEfd(),kc*ifd);
        if(!Double.isFinite(ve0))return false;
        double upper=fieldUpperLimit(ve0,ifd);
        if(ve0<vemin-EPS||ve0>upper+EPS)return false;
        double vfe0=fieldFeedback(ve0,ifd);
        vamax=Math.max(vamax,vfe0);vamin=Math.min(vamin,vfe0);
        efemax=Math.max(efemax,vfe0);efemin=Math.min(efemin,vfe0);
        double error0=vfe0/ka;
        state[VE]=ve0;state[VSENSE]=machine.getDStabBus().getVoltageMag();
        state[LEAD_LAG]=error0;state[VA]=vfe0;state[VFE_LAG]=vfe0;
        System.arraycopy(state,0,trial,0,state.length);active=state;
        reference=error0+state[VSENSE]-stabilizerSignal(machine)-summationLimiterInput();
        outputSignal=rectifierOutput(ve0,ifd);initialized=true;return true;
    }

    private void loadAndCorrectParameters(){
        oelLocation=data.getOelLocation();uelLocation=data.getUelLocation();sclLocation=data.getSclLocation();
        tr=correctedTr(data.getTr());tb=correctedBypass(data.getTb());tc=data.getTc();
        ka=data.getKa()==0.0?minimumResolvedTimeConstant():data.getKa();
        ta=correctedBypass(data.getTa());
        vamax=Math.max(data.getVamax(),data.getVamin());vamin=Math.min(data.getVamax(),data.getVamin());
        te=correctedMinimum(data.getTe());kf=data.getKf();tf=correctedMinimum(data.getTf());
        kc=data.getKc();kd=data.getKd();ke=data.getKe();e1=data.getE1();se1=data.getSe1();
        e2=data.getE2();se2=data.getSe2();
        efemax=Math.max(data.getEfemax(),data.getEfemin());efemin=Math.min(data.getEfemax(),data.getEfemin());
        vfemax=data.getVfemax();vemin=data.getVemin();
    }

    private double minimumResolvedTimeConstant(){return minimumTimeConstantMultiplier*integrationStep;}
    private double correctedTr(double v){
        double m=minimumResolvedTimeConstant();
        if(v>0&&v<.25*m)return 0;if(v>=.25*m&&v<.5*m)return .5*m;return v;
    }
    private double correctedBypass(double v){
        double m=minimumResolvedTimeConstant();
        if(v>0&&v<.5*m)return 0;if(v>=.5*m&&v<m)return m;return v;
    }
    private double correctedMinimum(double v){double m=minimumResolvedTimeConstant();return v>0&&v<m?m:v;}

    @Override public boolean nextStep(double dt,DynamicSimuMethod method,Machine machine,int flag){
        if(!initialized||dt<0)return false;if(dt==0)return true;
        int stage=method==DynamicSimuMethod.MODIFIED_EULER?flag:2;
        if(stage==0){
            derivatives(state,oldDerivative,machine);
            for(int i=0;i<state.length;i++)trial[i]=state[i]+oldDerivative[i]*dt;
            constrain(trial,machine);active=trial;
        }else if(stage==1){
            double[] corrected=new double[state.length];derivatives(trial,corrected,machine);
            for(int i=0;i<state.length;i++)state[i]+=.5*(oldDerivative[i]+corrected[i])*dt;
            constrain(state,machine);active=state;
        }else{
            double[] derivative=new double[state.length];derivatives(state,derivative,machine);
            for(int i=0;i<state.length;i++)state[i]+=derivative[i]*dt;
            constrain(state,machine);active=state;
        }
        outputSignal=rectifierOutput(algebraics(active,machine).field,exciterIfd(machine));return true;
    }

    private void derivatives(double[] x,double[] dx,Machine machine){
        Arrays.fill(dx,0);Algebraic a=algebraics(x,machine);
        dx[VSENSE]=lagDerivative(machine.getDStabBus().getVoltageMag(),x[VSENSE],tr);
        dx[LEAD_LAG]=lagDerivative(a.error,x[LEAD_LAG],tb);
        double vaRate=ta>EPS?(ka*a.leadLag-x[VA])/ta:0;
        if((x[VA]>=vamax-EPS&&vaRate>0)||(x[VA]<=vamin+EPS&&vaRate<0))vaRate=0;
        dx[VA]=vaRate;
        dx[VFE_LAG]=lagDerivative(a.vfe,x[VFE_LAG],tf);
        double veRate=te>EPS?(a.efe-a.vfe)/te:0;
        double upper=fieldUpperLimit(x[VE],exciterIfd(machine));
        if((x[VE]>=upper-EPS&&veRate>0)||(x[VE]<=vemin+EPS&&veRate<0))veRate=0;
        dx[VE]=veRate;
    }

    private Algebraic algebraics(double[] x,Machine machine){
        double vt=machine.getDStabBus().getVoltageMag(), sensed=tr>EPS?x[VSENSE]:vt;
        if(te>EPS)return algebraicsForField(x,machine,sensed,x[VE]);
        double field=solveAlgebraicField(x,machine,sensed);
        return algebraicsForField(x,machine,sensed,field);
    }

    private Algebraic algebraicsForField(double[] x,Machine machine,double sensed,double field){
        double ifd=exciterIfd(machine),vfe=fieldFeedback(field,ifd);
        double feedback=tf>EPS?kf*(vfe-x[VFE_LAG])/tf:0;
        double error=reference-sensed+stabilizerSignal(machine)+summationLimiterInput()-feedback;
        double leadLag=tb>EPS?(tc/tb)*error+(1-tc/tb)*x[LEAD_LAG]:error;
        double va=ta>EPS?clamp(x[VA],vamin,vamax):clamp(ka*leadLag,vamin,vamax);
        double takeover=takeoverLimiterOutput(va);
        double efe=clamp(takeover,efemin,efemax);
        double limitedField=clamp(field,vemin,fieldUpperLimit(field,ifd));
        return new Algebraic(sensed,error,leadLag,va,efe,limitedField,vfe,feedback);
    }

    private double solveAlgebraicField(double[] x,Machine machine,double sensed){
        double value=Double.isFinite(x[VE])?x[VE]:vemin;
        for(int i=0;i<30;i++){
            double residual=algebraicResidual(value,x,machine,sensed);
            if(Math.abs(residual)<1e-11)break;
            double h=1e-6*Math.max(1,Math.abs(value));
            double slope=(algebraicResidual(value+h,x,machine,sensed)-algebraicResidual(value-h,x,machine,sensed))/(2*h);
            if(!Double.isFinite(slope)||Math.abs(slope)<=EPS)break;
            double next=value-residual/slope;if(!Double.isFinite(next))break;value=next;
        }
        double ifd=exciterIfd(machine);return clamp(value,vemin,fieldUpperLimit(value,ifd));
    }
    private double algebraicResidual(double field,double[] x,Machine machine,double sensed){
        Algebraic a=algebraicsForField(x,machine,sensed,field);return a.vfe-a.efe;
    }

    private double summationLimiterInput(){
        double value=0;
        if(uelLocation==INPUT_SUMMATION&&hasVuel)value+=vuel;
        if(oelLocation==INPUT_SUMMATION&&hasVoel)value+=voel;
        if(sclLocation==INPUT_SUMMATION&&hasVsclSum)value+=vsclSum;
        return value;
    }
    private double takeoverLimiterOutput(double va){
        double value=va;
        if(uelLocation==INPUT_TAKEOVER&&hasVuel)value=Math.max(value,vuel);
        if(sclLocation==INPUT_TAKEOVER&&hasVsclUel)value=Math.max(value,vsclUel);
        if(oelLocation==INPUT_TAKEOVER&&hasVoel)value=Math.min(value,voel);
        if(sclLocation==INPUT_TAKEOVER&&hasVsclOel)value=Math.min(value,vsclOel);
        return value;
    }

    private void constrain(double[] x,Machine machine){
        if(ta>EPS)x[VA]=clamp(x[VA],vamin,vamax);
        if(te>EPS){double ifd=exciterIfd(machine);x[VE]=clamp(x[VE],vemin,fieldUpperLimit(x[VE],ifd));}
        for(int i=0;i<x.length;i++)if(!Double.isFinite(x[i]))x[i]=0;
    }
    private double fieldUpperLimit(double field,double ifd){
        double denominator=ke+Exac1Exciter.saturation(Math.max(field,0),e1,se1,e2,se2);
        if(denominator<=EPS)return Double.POSITIVE_INFINITY;
        return Math.max(vemin,(vfemax-kd*ifd)/denominator);
    }
    private double fieldFeedback(double field,double ifd){
        return field*(ke+Exac1Exciter.saturation(field,e1,se1,e2,se2))+kd*ifd;
    }
    private double rectifierOutput(double field,double ifd){
        if(Math.abs(field)<=EPS)return 0;return field*Exac1Exciter.rectifierFactor(kc*ifd/field);
    }
    private static boolean validLocation(int v){return v>=INPUT_UNUSED&&v<=INPUT_TAKEOVER;}
    private static double lagDerivative(double input,double value,double time){return time>EPS?(input-value)/time:0;}
    private static double clamp(double value,double low,double high){return Math.max(low,Math.min(high,value));}
    private static double stabilizerSignal(Machine machine){return machine.getStabilizer()==null?0:machine.getStabilizer().getOutput(machine);}
    private static double exciterIfd(Machine machine){double v=machine.calculateIfd(MachineIfdBase.EXCITER);return Double.isFinite(v)?v:0;}

    public double getSensedVoltage(){return algebraics(active,getMachine()).sensed;}
    public double getLeadLagState(){return active[LEAD_LAG];}
    public double getLeadLagOutput(){return algebraics(active,getMachine()).leadLag;}
    public double getRegulatorState(){return active[VA];}
    public double getRegulatorOutput(){return algebraics(active,getMachine()).va;}
    public double getExciterFieldVoltage(){return algebraics(active,getMachine()).efe;}
    public double getInternalFieldVoltage(){return algebraics(active,getMachine()).field;}
    public double getFieldFeedback(){return algebraics(active,getMachine()).vfe;}
    public double getFieldFeedbackLagState(){return active[VFE_LAG];}
    public double getRateFeedback(){return algebraics(active,getMachine()).feedback;}
    @Override public double getOutput(Machine machine){Algebraic a=algebraics(active,machine);outputSignal=rectifierOutput(a.field,exciterIfd(machine));return outputSignal;}
    @Override public void setRefPoint(double value){reference=value;} @Override public double getRefPoint(){return reference;}

    private record Algebraic(double sensed,double error,double leadLag,double va,double efe,double field,double vfe,double feedback){}
    @Override public AnController getAnController(){return getClass().getAnnotation(AnController.class);}
    @Override public Field getField(String name)throws Exception{return getClass().getField(name);}
    @Override public Object getFieldObject(Field field)throws Exception{return field.get(this);}
}
