package org.interpss.dstab.control.exc.psse.ac8c;

import java.lang.reflect.Field;
import java.util.Arrays;

import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.control.exc.psse.exac1.Exac1Exciter;
import org.interpss.dstab.control.util.IntegrationStepAware;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnnotateExciter;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.MachineIfdBase;

/** IEEE 421.5-2016 / PSS/E AC8C controlled-rectifier brushless exciter. */
@AnController(input="mach.vt",output="this.outputSignal",refPoint="this.reference",display={})
public final class Ac8cExciter extends AnnotateExciter implements IntegrationStepAware {
    public static final int INPUT_UNUSED=0,INPUT_SUMMATION=1;
    public static final int GATE_1=2,GATE_2=3;
    public static final int PSS_AT_ERROR=1,PSS_AFTER_HV_GATE=2;
    public static final int SWITCH_A=1,SWITCH_B=2;

    private static final double EPS=1e-12,PID_TRACKING_GAIN=2;
    private static final int VE=0,VSENSE=1,PID_DERIVATIVE_LAG=2,PID_INTEGRAL=3,VR=4;
    private final Ac8cData data;
    private final double[] state=new double[5],trial=new double[5],oldDerivative=new double[5];
    private double[] active=state;
    private boolean initialized,hasVuel,hasVoel,hasVsclSum,hasVsclUel,hasVsclOel;
    private double integrationStep,minimumTimeConstantMultiplier=1;
    private double vuel,voel,vsclSum,vsclUel,vsclOel;

    public int oelLocation,uelLocation,sclLocation,vosLocation,sw1;
    public double tr,kpr,kir,kdr,tdr,vpidmax,vpidmin,ka,ta,vrmax,vrmin;
    public double kc,kd,ke,te,vfemax,vemin,e1,se1,e2,se2;
    public double kp,ki,xl,thetaP,kc1,vbmax,spdmlt,reference,outputSignal;

    public Ac8cExciter(String id,Ac8cData data,Machine machine){
        super(id,"AC8C","PSS/E");this.data=data;this._data=data;setMachine(machine);
    }
    public Ac8cData getData(){return data;}
    public void setVuel(double v){vuel=v;hasVuel=true;} public void setVoel(double v){voel=v;hasVoel=true;}
    public void setVsclSum(double v){vsclSum=v;hasVsclSum=true;}
    public void setVsclUel(double v){vsclUel=v;hasVsclUel=true;}
    public void setVsclOel(double v){vsclOel=v;hasVsclOel=true;}
    @Override public void configureIntegrationStep(double seconds){configureIntegrationStep(seconds,1);}
    public void configureIntegrationStep(double seconds,double multiplier){integrationStep=seconds;minimumTimeConstantMultiplier=multiplier;}

    @Override public boolean initStates(BaseDStabBus<?,?> bus,Machine machine){
        loadAndCorrect();
        if(!validLocation(oelLocation)||!validLocation(uelLocation)||!validLocation(sclLocation)
                ||(vosLocation!=PSS_AT_ERROR&&vosLocation!=PSS_AFTER_HV_GATE)
                ||(sw1!=SWITCH_A&&sw1!=SWITCH_B)||tr<0||tdr<0||ta<0||te<0
                ||kc<0||kd<0||kc1<0||vbmax<0||Math.abs(ka)<=EPS)return false;
        double vt0=bus.getVoltageMag(),ifd0=exciterIfd(machine);
        double ve0=Exac1Exciter.solveInternalVoltage(machine.getEfd(),kc*ifd0);
        if(!Double.isFinite(vt0)||vt0<=EPS||!Double.isFinite(ve0))return false;
        double vfe0=fieldFeedback(ve0,ifd0),supply0=selectedSupply(machine,vfe0);
        if(!Double.isFinite(supply0)||Math.abs(supply0)<=EPS)return false;
        double vr0=vfe0/supply0,pid0=vr0/ka;
        if(pid0>vpidmax+EPS||pid0<vpidmin-EPS)return false;
        // PowerWorld's AC8C initialization treatment expands only the regulator
        // output limits. The rotating-exciter field limits remain model data.
        vrmax=Math.max(vrmax,vr0);vrmin=Math.min(vrmin,vr0);
        state[VE]=ve0;state[VSENSE]=vt0;state[PID_DERIVATIVE_LAG]=0;
        state[PID_INTEGRAL]=pid0;state[VR]=vr0;
        System.arraycopy(state,0,trial,0,state.length);active=state;
        double pss=stabilizerSignal(machine);
        reference=vt0-summationLimiterInput()-(vosLocation==PSS_AT_ERROR?pss:0);
        outputSignal=speedAdjusted(rectifierOutput(ve0,ifd0),machine);initialized=true;return true;
    }

    private void loadAndCorrect(){
        oelLocation=data.getOelLocation();uelLocation=data.getUelLocation();sclLocation=data.getSclLocation();
        vosLocation=data.getVosLocation();sw1=data.getSw1();tr=correctedTransducer(data.getTr());
        kpr=data.getKpr();kir=data.getKir();kdr=data.getKdr();tdr=correctedBypass(data.getTdr());
        vpidmax=Math.max(data.getVpidmax(),data.getVpidmin());vpidmin=Math.min(data.getVpidmax(),data.getVpidmin());
        ka=data.getKa();ta=correctedBypass(data.getTa());vrmax=Math.max(data.getVrmax(),data.getVrmin());
        vrmin=Math.min(data.getVrmax(),data.getVrmin());kc=data.getKc();kd=data.getKd();ke=data.getKe();
        te=correctedMinimum(data.getTe());vfemax=data.getVfemax();vemin=data.getVemin();
        e1=data.getE1();se1=data.getSe1();e2=data.getE2();se2=data.getSe2();kp=data.getKp();
        ki=data.getKi();xl=data.getXl();thetaP=data.getThetaP();kc1=data.getKc1();vbmax=data.getVbmax();
        spdmlt=data.getSpdmlt();
    }
    private double minimumResolvedTimeConstant(){return minimumTimeConstantMultiplier*integrationStep;}
    private double correctedTransducer(double v){double m=minimumResolvedTimeConstant();if(v>0&&v<.25*m)return 0;if(v>.25*m&&v<.5*m)return .5*m;return v;}
    private double correctedBypass(double v){double m=minimumResolvedTimeConstant();if(v>0&&v<.5*m)return 0;if(v>.5*m&&v<m)return m;return v;}
    private double correctedMinimum(double v){double m=minimumResolvedTimeConstant();return v>0&&v<m?m:v;}

    @Override public boolean nextStep(double dt,DynamicSimuMethod method,Machine machine,int flag){
        if(!initialized||dt<0)return false;if(dt==0){outputSignal=speedAdjusted(rawOutput(active,machine),machine);return true;}
        int stage=method==DynamicSimuMethod.MODIFIED_EULER?flag:2;
        if(stage==0){derivatives(state,oldDerivative,machine);add(state,oldDerivative,dt,trial);constrain(trial,machine);active=trial;}
        else if(stage==1){double[] d=new double[5];derivatives(trial,d,machine);for(int i=0;i<5;i++)state[i]+=.5*(oldDerivative[i]+d[i])*dt;constrain(state,machine);active=state;}
        else{double[] d=new double[5];derivatives(state,d,machine);add(state,d,dt,state);constrain(state,machine);active=state;}
        outputSignal=speedAdjusted(rawOutput(active,machine),machine);return true;
    }
    private void derivatives(double[] x,double[] dx,Machine machine){
        Arrays.fill(dx,0);Algebraic a=algebraics(x,machine);
        dx[VSENSE]=lagDerivative(machine.getDStabBus().getVoltageMag(),x[VSENSE],tr);
        dx[PID_DERIVATIVE_LAG]=lagDerivative(a.pidError,x[PID_DERIVATIVE_LAG],tdr);
        dx[PID_INTEGRAL]=kir*(a.pidError-PID_TRACKING_GAIN*(a.pidUnlimited-a.pidLimited));
        double vrRate=ta>EPS?(ka*a.pidOutput-x[VR])/ta:0;
        if((x[VR]>=vrmax-EPS&&vrRate>0)||(x[VR]<=vrmin+EPS&&vrRate<0))vrRate=0;
        dx[VR]=vrRate;
        double fieldRate=te>EPS?(a.efe-a.vfe)/te:0,upper=fieldUpperLimit(x[VE],exciterIfd(machine));
        if((x[VE]>=upper-EPS&&fieldRate>0)||(x[VE]<=vemin+EPS&&fieldRate<0))fieldRate=0;
        dx[VE]=fieldRate;
    }
    private Algebraic algebraics(double[] x,Machine machine){
        double ifd=exciterIfd(machine),field=te>EPS?clamp(x[VE],vemin,fieldUpperLimit(x[VE],ifd))
                :solveAlgebraicField(x[VE],x,machine,ifd);
        return algebraicsAtField(x,machine,field,ifd);
    }
    private Algebraic algebraicsAtField(double[] x,Machine machine,double field,double ifd){
        double vt=machine.getDStabBus().getVoltageMag(),sensed=tr>EPS?x[VSENSE]:vt;
        double pss=stabilizerSignal(machine),pidError=reference-sensed+summationLimiterInput()
                +(vosLocation==PSS_AT_ERROR?pss:0);
        pidError=hvGate(pidError,GATE_1);if(vosLocation==PSS_AFTER_HV_GATE)pidError+=pss;
        pidError=lvGate(pidError,GATE_1);
        double derivative=tdr>EPS?kdr*(pidError-x[PID_DERIVATIVE_LAG])/tdr:0;
        double pidUnlimited=kpr*pidError+x[PID_INTEGRAL]+derivative;
        double pidLimited=clamp(pidUnlimited,vpidmin,vpidmax);
        double pidOutput=lvGate(hvGate(pidLimited,GATE_2),GATE_2);
        double regulator=ta>EPS?clamp(x[VR],vrmin,vrmax):clamp(ka*pidOutput,vrmin,vrmax);
        double vfe=fieldFeedback(field,ifd),supply=selectedSupply(machine,vfe),efe=regulator*supply;
        return new Algebraic(sensed,field,vfe,pidError,pidUnlimited,pidLimited,pidOutput,regulator,supply,efe);
    }
    private double solveAlgebraicField(double initial,double[] x,Machine machine,double ifd){
        double value=Double.isFinite(initial)?initial:0;
        for(int i=0;i<30;i++){
            value=clamp(value,vemin,fieldUpperLimit(value,ifd));double residual=fieldResidual(value,x,machine,ifd);
            if(Math.abs(residual)<1e-11)break;double h=1e-6*Math.max(1,Math.abs(value));
            double slope=(fieldResidual(value+h,x,machine,ifd)-fieldResidual(value-h,x,machine,ifd))/(2*h);
            if(!Double.isFinite(slope)||Math.abs(slope)<=EPS)break;double next=value-residual/slope;
            if(!Double.isFinite(next))break;value=next;
        }
        return clamp(value,vemin,fieldUpperLimit(value,ifd));
    }
    private double fieldResidual(double field,double[] x,Machine machine,double ifd){Algebraic a=algebraicsAtField(x,machine,field,ifd);return a.efe-a.vfe;}

    private double summationLimiterInput(){double v=0;if(uelLocation<2&&hasVuel)v+=vuel;if(oelLocation<2&&hasVoel)v+=voel;if(sclLocation<2&&hasVsclSum)v+=vsclSum;return v;}
    private double hvGate(double v,int gate){if(uelLocation==gate&&hasVuel)v=Math.max(v,vuel);if(sclLocation==gate&&hasVsclUel)v=Math.max(v,vsclUel);return v;}
    private double lvGate(double v,int gate){if(oelLocation==gate&&hasVoel)v=Math.min(v,voel);if(sclLocation==gate&&hasVsclOel)v=Math.min(v,vsclOel);return v;}
    private double potentialSource(Machine machine){
        Complex angle=new Complex(Math.cos(Math.toRadians(thetaP)),Math.sin(Math.toRadians(thetaP)));
        Complex kpPhasor=angle.multiply(kp),vt=machine.getDStabBus().getVoltage();
        Complex it=machine.getIxy().divide(machine.getIMultiFactor());
        return kpPhasor.multiply(vt).add(Complex.I.multiply(new Complex(ki,0).add(kpPhasor.multiply(xl))).multiply(it)).abs();
    }
    private double selectedSupply(Machine machine,double vfe){double ve1=sw1==SWITCH_A?potentialSource(machine):kp;return potentialBridge(ve1,vfe);}
    private double potentialBridge(double ve1,double vfe){if(Math.abs(ve1)<=EPS)return 0;return clamp(ve1*Exac1Exciter.rectifierFactor(kc1*vfe/ve1),0,vbmax);}
    private void constrain(double[] x,Machine machine){if(ta>EPS)x[VR]=clamp(x[VR],vrmin,vrmax);if(te>EPS)x[VE]=clamp(x[VE],vemin,fieldUpperLimit(x[VE],exciterIfd(machine)));for(int i=0;i<x.length;i++)if(!Double.isFinite(x[i]))x[i]=0;}
    private double fieldUpperLimit(double field,double ifd){double den=ke+Exac1Exciter.saturation(Math.max(field,0),e1,se1,e2,se2);if(den<=EPS)return Double.POSITIVE_INFINITY;return Math.max(vemin,(vfemax-kd*ifd)/den);}
    private double fieldFeedback(double field,double ifd){return field*(ke+Exac1Exciter.saturation(field,e1,se1,e2,se2))+kd*ifd;}
    private double rectifierOutput(double field,double ifd){if(Math.abs(field)<=EPS)return 0;return field*Exac1Exciter.rectifierFactor(kc*ifd/field);}
    private double rawOutput(double[] x,Machine machine){Algebraic a=algebraics(x,machine);return rectifierOutput(a.field,exciterIfd(machine));}
    private double speedAdjusted(double raw,Machine machine){return spdmlt!=0?raw*machine.getSpeed():raw;}
    private static double lagDerivative(double input,double stateValue,double time){return time>EPS?(input-stateValue)/time:0;}
    private static void add(double[] x,double[] d,double dt,double[] y){for(int i=0;i<x.length;i++)y[i]=x[i]+d[i]*dt;}
    private static double clamp(double v,double lo,double hi){return Math.max(lo,Math.min(hi,v));}
    private static boolean validLocation(int v){return v>=INPUT_UNUSED&&v<=GATE_2;}
    private static double stabilizerSignal(Machine m){return m.getStabilizer()==null?0:m.getStabilizer().getOutput(m);}
    private static double exciterIfd(Machine m){double v=m.calculateIfd(MachineIfdBase.EXCITER);return Double.isFinite(v)?v:0;}

    public double getSensedVoltage(){return algebraics(active,getMachine()).sensed;}
    public double getInternalFieldVoltage(){return algebraics(active,getMachine()).field;}
    public double getFieldFeedback(){return algebraics(active,getMachine()).vfe;}
    public double getPidError(){return algebraics(active,getMachine()).pidError;}
    public double getPidOutput(){return algebraics(active,getMachine()).pidOutput;}
    public double getRegulatorOutput(){return algebraics(active,getMachine()).regulator;}
    public double getPotentialSource(){return potentialSource(getMachine());}
    public double getSelectedSupply(){return algebraics(active,getMachine()).supply;}
    public double getExciterInput(){return algebraics(active,getMachine()).efe;}
    public double getDynamicFieldUpperLimit(){return fieldUpperLimit(active[VE],exciterIfd(getMachine()));}
    @Override public double getOutput(Machine machine){outputSignal=speedAdjusted(rawOutput(active,machine),machine);return outputSignal;}
    @Override public void setRefPoint(double value){reference=value;} @Override public double getRefPoint(){return reference;}
    private record Algebraic(double sensed,double field,double vfe,double pidError,double pidUnlimited,
            double pidLimited,double pidOutput,double regulator,double supply,double efe){}
    @Override public AnController getAnController(){return getClass().getAnnotation(AnController.class);}
    @Override public Field getField(String name)throws Exception{return getClass().getField(name);}
    @Override public Object getFieldObject(Field field)throws Exception{return field.get(this);}
}
