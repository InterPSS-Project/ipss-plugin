package org.interpss.dstab.control.exc.psse.esac3a;

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

/** IEEE 421.5 / native PSS/E ESAC3A alternator-rectifier excitation system. */
@AnController(input="mach.vt",output="this.outputSignal",refPoint="this.reference",display={})
public final class Esac3aExciter extends AnnotateExciter implements IntegrationStepAware {
    private static final double EPS=1e-12;
    private static final int VE=0,VSENSE=1,VA=2,LEAD_LAG=3,VN_LAG=4;
    private final Esac3aData data;
    private final double[] state=new double[5],trial=new double[5],oldDerivative=new double[5];
    private double[] active=state;
    private boolean initialized,hasVuel,hasVoel;
    private double integrationStep,minimumTimeConstantMultiplier=1,vuel,voel;

    public double tr,tb,tc,ka,ta,vamax,vamin,te,vemin,kr,kf,tf,kn,efdn;
    public double kc,kd,ke,vfemax,e1,se1,e2,se2,spdmlt,reference,outputSignal;

    public Esac3aExciter(String id,Esac3aData data,Machine machine){
        super(id,"ESAC3A","PSS/E");this.data=data;this._data=data;setMachine(machine);
    }
    public Esac3aData getData(){return data;}
    public void setVuel(double v){vuel=v;hasVuel=true;}public void clearVuel(){hasVuel=false;}
    public void setVoel(double v){voel=v;hasVoel=true;}public void clearVoel(){hasVoel=false;}
    @Override public void configureIntegrationStep(double seconds){configureIntegrationStep(seconds,1);}
    public void configureIntegrationStep(double seconds,double multiplier){integrationStep=seconds;minimumTimeConstantMultiplier=multiplier;}

    @Override public boolean initStates(BaseDStabBus<?,?> bus,Machine machine){
        loadAndCorrectParameters();
        if(!finiteParameters()||tr<0||tb<0||ta<0||te<=EPS||tf<0||kc<0||kd<0
                ||ka<=EPS||kr<=EPS)return false;
        double ifd=exciterIfd(machine),ve0=Exac1Exciter.solveInternalVoltage(machine.getEfd(),kc*ifd);
        if(!Double.isFinite(ve0))return false;
        vemin=Math.min(vemin,ve0);
        if(ve0>fieldUpperLimit(ve0,ifd)+EPS)return false;
        double efd0=rectifierOutput(ve0,ifd),vfe0=fieldFeedback(ve0,ifd);
        double supply=kr*efd0;if(Math.abs(supply)<=EPS)return false;
        double va0=vfe0/supply;
        vamax=Math.max(vamax,va0);vamin=Math.min(vamin,va0);
        double regulatorInput0=va0/ka;
        state[VE]=ve0;state[VSENSE]=machine.getDStabBus().getVoltageMag();state[VA]=va0;
        state[LEAD_LAG]=regulatorInput0;state[VN_LAG]=nonlinearFeedback(efd0);
        System.arraycopy(state,0,trial,0,state.length);active=state;
        reference=state[VSENSE]+regulatorInput0-stabilizerSignal(machine)-oelSignal();
        outputSignal=efd0;initialized=true;return true;
    }

    private void loadAndCorrectParameters(){
        tr=correctedTr(data.getTr());tb=correctedBypass(data.getTb());tc=data.getTc();
        ka=Math.abs(data.getKa())<=EPS?minimumTime():data.getKa();ta=correctedBypass(data.getTa());
        vamax=Math.max(data.getVamax(),data.getVamin());vamin=Math.min(data.getVamax(),data.getVamin());
        te=correctedMinimum(data.getTe());vemin=data.getVemin();
        kr=Math.abs(data.getKr())<=EPS?minimumTime():data.getKr();
        kf=data.getKf();tf=correctedMinimum(data.getTf());kn=data.getKn();efdn=data.getEfdn();
        kc=data.getKc();kd=data.getKd();ke=data.getKe();vfemax=data.getVfemax();
        e1=data.getE1();se1=data.getSe1();e2=data.getE2();se2=data.getSe2();
        // Spdmlt is a PowerWorld/PSLF property, not a native PSS/E ESAC3A CON.
        spdmlt=0;
    }
    private double minimumTime(){return minimumTimeConstantMultiplier*integrationStep;}
    private double correctedTr(double v){double m=minimumTime();if(v>0&&v<.25*m)return 0;if(v>=.25*m&&v<.5*m)return .5*m;return v;}
    private double correctedBypass(double v){double m=minimumTime();if(v>0&&v<.5*m)return 0;if(v>=.5*m&&v<m)return m;return v;}
    private double correctedMinimum(double v){double m=minimumTime();return v>0&&v<m?m:v;}
    private boolean finiteParameters(){
        double[] values={tr,tb,tc,ka,ta,vamax,vamin,te,vemin,kr,kf,tf,kn,efdn,kc,kd,ke,vfemax,e1,se1,e2,se2};
        for(double value:values)if(!Double.isFinite(value))return false;return true;
    }

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
        outputSignal=rectifierOutput(active[VE],exciterIfd(machine));return true;
    }

    private void derivatives(double[] x,double[] dx,Machine machine){
        Arrays.fill(dx,0);Algebraic a=algebraics(x,machine);
        dx[VSENSE]=lagDerivative(machine.getDStabBus().getVoltageMag(),x[VSENSE],tr);
        dx[LEAD_LAG]=lagDerivative(a.error,x[LEAD_LAG],tb);
        double vaRate=ta>EPS?(ka*a.regulatorInput-x[VA])/ta:0;
        if((x[VA]>=vamax-EPS&&vaRate>0)||(x[VA]<=vamin+EPS&&vaRate<0))vaRate=0;
        dx[VA]=vaRate;dx[VN_LAG]=lagDerivative(a.vn,x[VN_LAG],tf);
        double veRate=(a.efe-a.vfe)/te,upper=fieldUpperLimit(x[VE],exciterIfd(machine));
        if((x[VE]>=upper-EPS&&veRate>0)||(x[VE]<=vemin+EPS&&veRate<0))veRate=0;
        dx[VE]=veRate;
    }

    private Algebraic algebraics(double[] x,Machine machine){
        double vt=machine.getDStabBus().getVoltageMag(),sensed=tr>EPS?x[VSENSE]:vt;
        double error=reference-sensed+stabilizerSignal(machine)+oelSignal();
        double leadLag=tb>EPS?(tc/tb)*error+(1-tc/tb)*x[LEAD_LAG]:error;
        double ifd=exciterIfd(machine),field=x[VE],efd=rectifierOutput(field,ifd);
        double vn=nonlinearFeedback(efd),feedback=tf>EPS?(vn-x[VN_LAG])/tf:0;
        double command=leadLag-feedback,regulatorInput=hasVuel?Math.max(command,vuel):command;
        double va=ta>EPS?clamp(x[VA],vamin,vamax):clamp(ka*regulatorInput,vamin,vamax);
        double efe=va*kr*efd,vfe=fieldFeedback(field,ifd);
        return new Algebraic(sensed,error,leadLag,regulatorInput,va,efd,efe,vfe,vn,feedback);
    }

    private double oelSignal(){return hasVoel?voel:0;}
    double nonlinearFeedback(double efd){
        return efd<=efdn?kf*efd:kf*efdn+kn*(efd-efdn);
    }
    double fieldUpperLimit(double field,double ifd){
        double denominator=ke+Exac1Exciter.saturation(Math.max(field,0),e1,se1,e2,se2);
        if(denominator<=EPS)return Double.POSITIVE_INFINITY;
        return Math.max(vemin,(vfemax-kd*ifd)/denominator);
    }
    double fieldFeedback(double field,double ifd){
        return field*(ke+Exac1Exciter.saturation(field,e1,se1,e2,se2))+kd*ifd;
    }
    private double rectifierOutput(double field,double ifd){
        if(Math.abs(field)<=EPS)return 0;
        double efd=field*Exac1Exciter.rectifierFactor(kc*ifd/field);
        return spdmlt!=0?efd*getMachine().getSpeed():efd;
    }
    private void constrain(double[] x,Machine machine){
        if(ta>EPS)x[VA]=clamp(x[VA],vamin,vamax);
        double ifd=exciterIfd(machine);x[VE]=clamp(x[VE],vemin,fieldUpperLimit(x[VE],ifd));
        for(int i=0;i<x.length;i++)if(!Double.isFinite(x[i]))x[i]=0;
    }
    private static double lagDerivative(double input,double value,double time){return time>EPS?(input-value)/time:0;}
    private static double clamp(double v,double lo,double hi){return Math.max(lo,Math.min(hi,v));}
    private static double stabilizerSignal(Machine machine){return machine.getStabilizer()==null?0:machine.getStabilizer().getOutput(machine);}
    private static double exciterIfd(Machine machine){double value=machine.calculateIfd(MachineIfdBase.EXCITER);return Double.isFinite(value)?value:0;}

    public double[] getStateSnapshot(){return active.clone();}
    public double getSensedVoltage(){return algebraics(active,getMachine()).sensed;}
    public double getLeadLagOutput(){return algebraics(active,getMachine()).leadLag;}
    public double getRegulatorInput(){return algebraics(active,getMachine()).regulatorInput;}
    public double getRegulatorOutput(){return algebraics(active,getMachine()).va;}
    public double getExciterFieldVoltage(){return algebraics(active,getMachine()).efe;}
    public double getInternalFieldVoltage(){return active[VE];}
    public double getFieldFeedback(){return algebraics(active,getMachine()).vfe;}
    public double getNonlinearFeedback(){return algebraics(active,getMachine()).vn;}
    public double getRateFeedback(){return algebraics(active,getMachine()).feedback;}
    public double getDynamicUpperLimit(){return fieldUpperLimit(active[VE],exciterIfd(getMachine()));}
    @Override public double getOutput(Machine machine){outputSignal=rectifierOutput(active[VE],exciterIfd(machine));return outputSignal;}
    @Override public void setRefPoint(double value){reference=value;}@Override public double getRefPoint(){return reference;}
    private record Algebraic(double sensed,double error,double leadLag,double regulatorInput,double va,
            double efd,double efe,double vfe,double vn,double feedback){}
    @Override public AnController getAnController(){return getClass().getAnnotation(AnController.class);}
    @Override public Field getField(String name)throws Exception{return getClass().getField(name);}
    @Override public Object getFieldObject(Field field)throws Exception{return field.get(this);}
}
