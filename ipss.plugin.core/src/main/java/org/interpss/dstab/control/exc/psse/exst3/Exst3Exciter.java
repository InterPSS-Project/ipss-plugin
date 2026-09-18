package org.interpss.dstab.control.exc.psse.exst3;

import java.lang.reflect.Field;
import java.util.Arrays;

import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.control.exc.psse.esst2a.Esst2aExciter;
import org.interpss.dstab.control.util.IntegrationStepAware;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnnotateExciter;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.MachineIfdBase;

/** Native PSS/E IEEE Type ST3 excitation system. */
@AnController(input="mach.vt", output="this.outputSignal",
        refPoint="this.reference", display={})
public final class Exst3Exciter extends AnnotateExciter implements IntegrationStepAware {
    private static final double EPS=1.0e-12;
    private static final int VR=0, VSENSE=1, LEAD_LAG=2;
    private final Exst3Data data;
    private final double[] state=new double[3],trial=new double[3],oldDerivative=new double[3];
    private double[] active=state;
    private boolean initialized,hasVuel,hasVoel;
    private double integrationStep,minimumTimeConstantMultiplier=1.0,vuel,voel;

    public double tr,vimax,vimin,kj,tc,tb,ka,ta,vrmax,vrmin;
    public double kg,kp,ki,efdmax,kc,xl,vgmax,thetaP;
    public double reference,outputSignal;

    public Exst3Exciter(String id,Exst3Data data,Machine machine){
        super(id,"EXST3","PSS/E");this.data=data;this._data=data;setMachine(machine);
    }
    public Exst3Data getData(){return data;}
    public void setVuel(double value){vuel=value;hasVuel=true;}
    public void setVoel(double value){voel=value;hasVoel=true;}
    public double getVuel(){return vuel;} public double getVoel(){return voel;}
    @Override public void configureIntegrationStep(double value){configureIntegrationStep(value,1.0);}
    public void configureIntegrationStep(double value,double multiplier){
        integrationStep=value;minimumTimeConstantMultiplier=multiplier;
    }

    @Override public boolean initStates(BaseDStabBus<?,?> bus,Machine machine){
        loadAndCorrectParameters();
        if(tr<0||ta<0||tb<0||Math.abs(ka)<=EPS||Math.abs(kj)<=EPS)return false;
        double efd0=machine.getEfd(),vt0=machine.getDStabBus().getVoltageMag();
        double vb0=bridgeVoltage(machine);
        if(!Double.isFinite(efd0)||!Double.isFinite(vt0)||vb0<=EPS)return false;
        efdmax=Math.max(efdmax,efd0);
        double vr0=efd0/vb0;
        double vg0=Math.min(vgmax,kg*efd0);
        double vi0=(vr0/ka+vg0)/kj;
        vrmax=Math.max(vrmax,vr0);vrmin=Math.min(vrmin,vr0);
        vimax=Math.max(vimax,vi0);vimin=Math.min(vimin,vi0);
        state[VR]=vr0;state[VSENSE]=vt0;state[LEAD_LAG]=vi0;
        System.arraycopy(state,0,trial,0,state.length);active=state;
        reference=vi0+vt0-stabilizerSignal(machine)
                -(hasVuel?vuel:0.0)-(hasVoel?voel:0.0);
        outputSignal=efd0;initialized=true;return true;
    }

    private void loadAndCorrectParameters(){
        tr=Math.min(correctedBypass(data.getTr()),.5);
        vimax=Math.max(data.getVimax(),data.getVimin());vimin=Math.min(data.getVimax(),data.getVimin());
        kj=correctedGain(data.getKj());tc=data.getTc();tb=correctedBypass(data.getTb());
        ka=correctedGain(data.getKa());ta=correctedBypass(data.getTa());
        vrmax=Math.max(data.getVrmax(),data.getVrmin());vrmin=Math.min(data.getVrmax(),data.getVrmin());
        kg=data.getKg();kp=data.getKp();ki=data.getKi();efdmax=data.getEfdmax();kc=data.getKc();
        xl=data.getXl();vgmax=data.getVgmax();thetaP=data.getThetaP();
    }
    private double minimum(){return minimumTimeConstantMultiplier*integrationStep;}
    private double correctedGain(double value){return Math.abs(value)<=EPS?minimum():value;}
    private double correctedBypass(double value){double min=minimum();
        if(value>0&&value<.5*min)return 0;if(value>=.5*min&&value<min)return min;return value;}

    @Override public boolean nextStep(double dt,DynamicSimuMethod method,Machine machine,int flag){
        if(!initialized||dt<0)return false;if(dt==0)return true;
        int stage=method==DynamicSimuMethod.MODIFIED_EULER?flag:2;
        if(stage==0){derivatives(state,oldDerivative,machine);for(int i=0;i<3;i++)trial[i]=state[i]+oldDerivative[i]*dt;
            constrain(trial);active=trial;
        }else if(stage==1){double[] d=new double[3];derivatives(trial,d,machine);
            for(int i=0;i<3;i++)state[i]+=.5*(oldDerivative[i]+d[i])*dt;constrain(state);active=state;
        }else{double[] d=new double[3];derivatives(state,d,machine);
            for(int i=0;i<3;i++)state[i]+=d[i]*dt;constrain(state);active=state;}
        outputSignal=algebraics(active,machine).efd;return true;
    }
    private void derivatives(double[] x,double[] dx,Machine machine){
        Arrays.fill(dx,0);Algebraic a=algebraics(x,machine);
        dx[VSENSE]=lag(machine.getDStabBus().getVoltageMag(),x[VSENSE],tr);
        dx[LEAD_LAG]=lag(a.vi,x[LEAD_LAG],tb);
        double rate=ta>EPS?(ka*(a.va-a.vg)-x[VR])/ta:0;
        if((x[VR]>=vrmax-EPS&&rate>0)||(x[VR]<=vrmin+EPS&&rate<0))rate=0;dx[VR]=rate;
    }
    private Algebraic algebraics(double[] x,Machine machine){
        double sensed=tr>EPS?x[VSENSE]:machine.getDStabBus().getVoltageMag();
        double error=reference-sensed+stabilizerSignal(machine)+(hasVuel?vuel:0)+(hasVoel?voel:0);
        double vi=clamp(error,vimin,vimax);
        double va=tb>EPS?kj*((tc/tb)*vi+(1-tc/tb)*x[LEAD_LAG]):kj*vi;
        double vb=bridgeVoltage(machine);
        double vr=ta>EPS?clamp(x[VR],vrmin,vrmax):solveAlgebraicVr(va,vb,x[VR]);
        double efd=Math.min(efdmax,vr*vb),vg=Math.min(vgmax,kg*efd);
        return new Algebraic(sensed,error,vi,va,vg,vr,vb,efd);
    }
    private double solveAlgebraicVr(double va,double vb,double initial){
        double value=clamp(initial,vrmin,vrmax);
        for(int n=0;n<30;n++){
            double efd=Math.min(efdmax,value*vb),vg=Math.min(vgmax,kg*efd);
            double residual=value-clamp(ka*(va-vg),vrmin,vrmax);
            if(Math.abs(residual)<1e-11)break;
            double h=1e-6*Math.max(1,Math.abs(value));
            double rp=residual(value+h,va,vb),rm=residual(value-h,va,vb),slope=(rp-rm)/(2*h);
            if(!Double.isFinite(slope)||Math.abs(slope)<EPS)break;
            double next=clamp(value-residual/slope,vrmin,vrmax);if(!Double.isFinite(next))break;value=next;
        }
        return value;
    }
    private double residual(double vr,double va,double vb){double efd=Math.min(efdmax,vr*vb);
        return vr-clamp(ka*(va-Math.min(vgmax,kg*efd)),vrmin,vrmax);}
    private void constrain(double[] x){if(ta>EPS)x[VR]=clamp(x[VR],vrmin,vrmax);
        for(int i=0;i<x.length;i++)if(!Double.isFinite(x[i]))x[i]=0;}

    public double getCompoundSourceVoltage(){return compoundSourceVoltage(getMachine());}
    public double getBridgeVoltage(){return bridgeVoltage(getMachine());}
    private double compoundSourceVoltage(Machine machine){double angle=Math.toRadians(thetaP);
        Complex kpComplex=new Complex(kp*Math.cos(angle),kp*Math.sin(angle));
        Complex vt=machine.getParentGen().getParentBus().getVoltage(),it=machine.getIxy();
        return vt.multiply(kpComplex).add(Complex.I.multiply(kpComplex.multiply(xl).add(ki)).multiply(it)).abs();}
    private double bridgeVoltage(Machine machine){double ve=compoundSourceVoltage(machine);if(ve<=EPS)return 0;
        double ifd=machine.calculateIfd(MachineIfdBase.EXCITER);if(!Double.isFinite(ifd))ifd=0;
        return Math.max(0,ve*Esst2aExciter.rectifierFactor(kc*ifd/ve));}
    private static double lag(double input,double value,double time){return time>EPS?(input-value)/time:0;}
    private static double clamp(double value,double low,double high){return Math.max(low,Math.min(high,value));}
    private static double stabilizerSignal(Machine machine){return machine.getStabilizer()==null?0:machine.getStabilizer().getOutput(machine);}

    public double getSensedVoltage(){return algebraics(active,getMachine()).sensed;}
    public double getLimitedError(){return algebraics(active,getMachine()).vi;}
    public double getLeadLagOutput(){return algebraics(active,getMachine()).va;}
    public double getFeedbackVoltage(){return algebraics(active,getMachine()).vg;}
    public double getRegulatorOutput(){return algebraics(active,getMachine()).vr;}
    public double[] getStateSnapshot(){return active.clone();}
    @Override public double getOutput(Machine machine){outputSignal=algebraics(active,machine).efd;return outputSignal;}
    @Override public void setRefPoint(double value){reference=value;} @Override public double getRefPoint(){return reference;}
    private record Algebraic(double sensed,double error,double vi,double va,double vg,double vr,double vb,double efd){}
    @Override public AnController getAnController(){return getClass().getAnnotation(AnController.class);}
    @Override public Field getField(String name)throws Exception{return getClass().getField(name);}
    @Override public Object getFieldObject(Field field)throws Exception{return field.get(this);}
}
