package org.interpss.dstab.control.exc.psse.esac6a;

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
 * PSS/E ESAC6A implementation of the IEEE Type AC6A excitation system.
 *
 * <p>The five states follow the published PowerWorld diagram: exciter internal
 * voltage, sensed terminal voltage, voltage-regulator block, control-element
 * lead-lag, and field-current feedback. The regulator output limits are scaled
 * by terminal voltage, as specified for AC6A.</p>
 */
@AnController(input="mach.vt", output="this.outputSignal", refPoint="this.reference", display={})
public final class Esac6aExciter extends AnnotateExciter implements IntegrationStepAware {
    private static final double EPS=1e-12;
    private static final int VE=0, VSENSE=1, TA_BLOCK=2, VLL=3, VF=4;
    private final Esac6aData data;
    private final double[] state=new double[5], trial=new double[5], oldDerivative=new double[5];
    private double[] active=state;
    private boolean initialized;
    private double integrationStep, minimumTimeConstantMultiplier=1.0;

    public double tr,ka,ta,tk,tb,tc,vamax,vamin,vrmax,vrmin,te;
    public double vfelim,kh,vhmax,th,tj,kc,kd,ke,e1,se1,e2,se2,spdmlt;
    public double reference,outputSignal;

    public Esac6aExciter(String id, Esac6aData data, Machine machine) {
        super(id,"ESAC6A","PSS/E"); this.data=data; this._data=data; setMachine(machine);
    }
    public Esac6aData getData(){return data;}
    @Override public void configureIntegrationStep(double dt){configureIntegrationStep(dt,1.0);}
    public void configureIntegrationStep(double dt,double multiplier){
        integrationStep=dt; minimumTimeConstantMultiplier=multiplier;
    }

    @Override public boolean initStates(BaseDStabBus<?,?> bus, Machine machine) {
        loadAndCorrectParameters();
        if (ka<=0||tr<0||ta<0||tk<0||tb<0||tc<0||te<=EPS||th<0||tj<0
                || kc<0||kd<0||vhmax<0) return false;
        double ifd=exciterIfd(machine);
        double ve0=Exac1Exciter.solveInternalVoltage(machine.getEfd(),kc*ifd);
        if(!Double.isFinite(ve0)) return false;
        double vfe0=fieldFeedback(ve0,ifd);
        double vh0=clamp(kh*(vfe0-vfelim),0,vhmax);
        double vf0=vh0;
        double va0=vfe0+vf0;
        vamax=Math.max(vamax,va0); vamin=Math.min(vamin,va0);
        double vt0=bus.getVoltageMag();
        if(vt0<=EPS) return false;
        vrmax=Math.max(vrmax,vfe0/vt0); vrmin=Math.min(vrmin,vfe0/vt0);
        double error0=va0/ka;
        state[VE]=ve0; state[VSENSE]=vt0;
        state[TA_BLOCK]=transferState(error0,va0,ka,tk,ta);
        state[VLL]=transferState(va0,va0,1,tc,tb);
        state[VF]=transferState(vh0,vf0,1,tj,th);
        System.arraycopy(state,0,trial,0,state.length); active=state;
        reference=error0+vt0-stabilizerSignal(machine);
        outputSignal=rectifierOutput(ve0,ifd,machine); initialized=true; return true;
    }

    private void loadAndCorrectParameters(){
        tr=correctedTransducer(data.getTr());ka=data.getKa();ta=data.getTa();tk=data.getTk();
        tb=data.getTb();tc=data.getTc();
        vamax=Math.max(data.getVamax(),data.getVamin());vamin=Math.min(data.getVamax(),data.getVamin());
        vrmax=Math.max(data.getVrmax(),data.getVrmin());vrmin=Math.min(data.getVrmax(),data.getVrmin());
        te=correctedMinimum(data.getTe());vfelim=data.getVfelim();kh=data.getKh();vhmax=data.getVhmax();
        th=data.getTh();tj=data.getTj();kc=data.getKc();kd=data.getKd();ke=data.getKe();
        e1=data.getE1();se1=data.getSe1();e2=data.getE2();se2=data.getSe2();spdmlt=data.getSpdmlt();
    }
    private double minTime(){return minimumTimeConstantMultiplier*integrationStep;}
    private double correctedTransducer(double v){
        double m=minTime();if(v>0&&v<.5*m)return 0;if(v>.5*m&&v<m)return m;return v;
    }
    private double correctedMinimum(double v){double m=minTime();return v>0&&v<m?m:v;}

    @Override public boolean nextStep(double dt, DynamicSimuMethod method, Machine machine, int flag) {
        if(!initialized||dt<0)return false;if(dt==0)return true;
        int stage=method==DynamicSimuMethod.MODIFIED_EULER?flag:2;
        if(stage==0){
            derivatives(state,oldDerivative,machine);add(state,oldDerivative,dt,trial);constrain(trial);active=trial;
        } else if(stage==1){
            double[] d=new double[5];derivatives(trial,d,machine);
            for(int i=0;i<state.length;i++)state[i]+=.5*(oldDerivative[i]+d[i])*dt;
            constrain(state);active=state;
        } else {
            double[] d=new double[5];derivatives(state,d,machine);add(state,d,dt,state);constrain(state);active=state;
        }
        outputSignal=rectifierOutput(active[VE],exciterIfd(machine),machine);return true;
    }

    private void derivatives(double[] x,double[] dx,Machine machine){
        Arrays.fill(dx,0); Algebraic a=algebraics(x,machine);
        dx[VSENSE]=lagDerivative(machine.getDStabBus().getVoltageMag(),x[VSENSE],tr);
        dx[TA_BLOCK]=transferDerivative(a.error,x[TA_BLOCK],ka,tk,ta);
        dx[VLL]=transferDerivative(a.taOutput,x[VLL],1,tc,tb);
        dx[VF]=transferDerivative(a.vh,x[VF],1,tj,th);
        double veRate=(a.vr-a.vfe)/te;
        dx[VE]=x[VE]<=0&&veRate<0?0:veRate;
    }

    private Algebraic algebraics(double[] x,Machine machine){
        double vt=machine.getDStabBus().getVoltageMag();
        double sensed=tr>EPS?x[VSENSE]:vt;
        double error=reference+stabilizerSignal(machine)-sensed;
        double taOutput=transferOutput(error,x[TA_BLOCK],ka,tk,ta);
        double va=clamp(transferOutput(taOutput,x[VLL],1,tc,tb),vamin,vamax);
        double ifd=exciterIfd(machine),vfe=fieldFeedback(x[VE],ifd);
        double vh=clamp(kh*(vfe-vfelim),0,vhmax);
        double vf=transferOutput(vh,x[VF],1,tj,th);
        double vr=clamp(va-vf,vt*vrmin,vt*vrmax);
        return new Algebraic(sensed,error,taOutput,va,vfe,vh,vf,vr);
    }

    private double fieldFeedback(double ve,double ifd){
        return ve*(ke+Exac1Exciter.saturation(ve,e1,se1,e2,se2))+kd*ifd;
    }
    private double rectifierOutput(double ve,double ifd,Machine machine){
        if(Math.abs(ve)<=EPS)return 0;
        double efd=ve*Exac1Exciter.rectifierFactor(kc*ifd/ve);
        return spdmlt!=0?efd*machine.getSpeed():efd;
    }
    private static double transferState(double input,double output,double gain,double numeratorT,double denominatorT){
        return denominatorT>EPS?output-gain*numeratorT/denominatorT*input:0;
    }
    private static double transferOutput(double input,double state,double gain,double numeratorT,double denominatorT){
        return denominatorT>EPS?gain*numeratorT/denominatorT*input+state:gain*input;
    }
    private static double transferDerivative(double input,double state,double gain,double numeratorT,double denominatorT){
        return denominatorT>EPS?(gain*(1-numeratorT/denominatorT)*input-state)/denominatorT:0;
    }
    private static double lagDerivative(double input,double state,double t){return t>EPS?(input-state)/t:0;}
    private static void add(double[] x,double[] d,double dt,double[] y){for(int i=0;i<x.length;i++)y[i]=x[i]+d[i]*dt;}
    private static void constrain(double[] x){if(x[VE]<0)x[VE]=0;}
    private static double clamp(double value,double min,double max){return Math.max(min,Math.min(max,value));}
    private static double stabilizerSignal(Machine machine){
        return machine.getStabilizer()==null?0:machine.getStabilizer().getOutput(machine);
    }
    private static double exciterIfd(Machine machine){
        double value=machine.calculateIfd(MachineIfdBase.EXCITER);return Double.isFinite(value)?value:0;
    }

    public double getInternalFieldVoltage(){return active[VE];}
    public double getSensedVoltage(){return algebraics(active,getMachine()).sensed;}
    public double getTaOutput(){return algebraics(active,getMachine()).taOutput;}
    public double getVaOutput(){return algebraics(active,getMachine()).va;}
    public double getFieldFeedback(){return algebraics(active,getMachine()).vfe;}
    public double getVhOutput(){return algebraics(active,getMachine()).vh;}
    public double getFeedbackOutput(){return algebraics(active,getMachine()).vf;}
    public double getRegulatorOutput(){return algebraics(active,getMachine()).vr;}
    @Override public double getOutput(Machine machine){
        outputSignal=rectifierOutput(active[VE],exciterIfd(machine),machine);return outputSignal;
    }
    @Override public void setRefPoint(double value){reference=value;}
    @Override public double getRefPoint(){return reference;}
    private record Algebraic(double sensed,double error,double taOutput,double va,double vfe,double vh,double vf,double vr){}
    @Override public AnController getAnController(){return getClass().getAnnotation(AnController.class);}
    @Override public Field getField(String name)throws Exception{return getClass().getField(name);}
    @Override public Object getFieldObject(Field field)throws Exception{return field.get(this);}
}
