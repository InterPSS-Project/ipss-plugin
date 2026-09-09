package org.interpss.dstab.control.exc.psse.ac7c;

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

/** IEEE 421.5-2016 / PSS/E AC7C alternator-rectifier excitation system. */
@AnController(input="mach.vt",output="this.outputSignal",refPoint="this.reference",display={})
public final class Ac7cExciter extends AnnotateExciter implements IntegrationStepAware {
    public static final int INPUT_UNUSED=0, INPUT_SUMMATION=1;
    public static final int OEL_LV_GATE_1=2, OEL_LV_GATE_2=3, OEL_LV_GATE_3=4;
    public static final int UEL_HV_GATE_1=2, UEL_HV_GATE_2=3;
    public static final int PSS_AT_ERROR=1, PSS_AFTER_HV_GATE_1=2;
    public static final int SWITCH_A=1, SWITCH_B=2;

    private static final double EPS=1e-12;
    private static final int VE=0,VSENSE=1,PID_INTEGRAL=2,PID_DERIVATIVE_LAG=3,
            PI_INTEGRAL=4,RATE_LAG=5;
    private final Ac7cData data;
    private final double[] state=new double[6],trial=new double[6],oldDerivative=new double[6];
    private double[] active=state;
    private boolean initialized,hasVuel,hasVoel,hasVsclSum,hasVsclUel,hasVsclOel;
    private double integrationStep,minimumTimeConstantMultiplier=1;
    private double vuel,voel,vsclSum,vsclUel,vsclOel;

    public int oelLocation,uelLocation,sclLocation,vosLocation,sw1,sw2;
    public double tr,kpr,kir,kdr,tdr,vrmax,vrmin,kpa,kia,vamax,vamin,kp,kl;
    public double kf1,kf2,kf3,tf,kc,kd,ke,te,vfemax,vemin,e1,se1,e2,se2;
    public double ki,xl,thetaP,kc1,vbmax,kr,spdmlt,reference,outputSignal;

    public Ac7cExciter(String id,Ac7cData data,Machine machine){
        super(id,"AC7C","PSS/E");this.data=data;this._data=data;setMachine(machine);
    }
    public Ac7cData getData(){return data;}
    public void setVuel(double v){vuel=v;hasVuel=true;} public void setVoel(double v){voel=v;hasVoel=true;}
    public void setVsclSum(double v){vsclSum=v;hasVsclSum=true;}
    public void setVsclUel(double v){vsclUel=v;hasVsclUel=true;}
    public void setVsclOel(double v){vsclOel=v;hasVsclOel=true;}
    @Override public void configureIntegrationStep(double seconds){configureIntegrationStep(seconds,1);}
    public void configureIntegrationStep(double seconds,double multiplier){integrationStep=seconds;minimumTimeConstantMultiplier=multiplier;}

    @Override public boolean initStates(BaseDStabBus<?,?> bus,Machine machine){
        loadAndCorrect();
        if(!validOel(oelLocation)||!validUel(uelLocation)||!validUel(sclLocation)
                ||(vosLocation!=PSS_AT_ERROR&&vosLocation!=PSS_AFTER_HV_GATE_1)
                ||(sw1!=SWITCH_A&&sw1!=SWITCH_B)||(sw2!=SWITCH_A&&sw2!=SWITCH_B)
                ||tr<0||tdr<0||tf<0||te<0||kc<0||kd<0||kc1<0||vbmax<0)return false;
        double vt0=bus.getVoltageMag(),ifd0=exciterIfd(machine);
        double ve0=Exac1Exciter.solveInternalVoltage(machine.getEfd(),kc*ifd0);
        if(!Double.isFinite(vt0)||vt0<=EPS||!Double.isFinite(ve0))return false;
        double rawEfd0=rectifierOutput(ve0,ifd0),vfe0=fieldFeedback(ve0,ifd0);
        double supply0=selectedSupply(machine,ve0,vfe0,rawEfd0);
        if(!Double.isFinite(supply0)||Math.abs(supply0)<=EPS)return false;
        double va0=vfe0/supply0,vr0=kf1*rawEfd0+kf2*vfe0;

        vrmax=Math.max(vrmax,vr0);vrmin=Math.min(vrmin,vr0);
        vamax=Math.max(vamax,va0);vamin=Math.min(vamin,va0);
        vfemax=Math.max(vfemax,vfe0);vemin=Math.min(vemin,ve0);
        state[VE]=ve0;state[VSENSE]=vt0;state[PID_INTEGRAL]=vr0;
        state[PID_DERIVATIVE_LAG]=0;state[PI_INTEGRAL]=va0;state[RATE_LAG]=vfe0;
        System.arraycopy(state,0,trial,0,state.length);active=state;
        double pss=stabilizerSignal(machine);
        reference=vt0-summationLimiterInput()-(vosLocation==PSS_AT_ERROR?pss:0);
        outputSignal=speedAdjusted(rawEfd0,machine);initialized=true;return true;
    }

    private void loadAndCorrect(){
        oelLocation=data.getOelLocation();uelLocation=data.getUelLocation();sclLocation=data.getSclLocation();
        vosLocation=data.getVosLocation();sw1=data.getSw1();sw2=data.getSw2();
        tr=correctedTransducer(data.getTr());kpr=data.getKpr();kir=data.getKir();
        if(Math.abs(kpr)<=EPS&&Math.abs(kir)<=EPS)kpr=40;
        kdr=data.getKdr();tdr=correctedDerivative(data.getTdr());
        vrmax=Math.max(data.getVrmax(),data.getVrmin());vrmin=Math.min(data.getVrmax(),data.getVrmin());
        kpa=data.getKpa();kia=data.getKia();vamax=Math.max(data.getVamax(),data.getVamin());
        vamin=Math.min(data.getVamax(),data.getVamin());kp=data.getKp();kl=data.getKl();
        kf1=data.getKf1();kf2=data.getKf2();kf3=data.getKf3();tf=correctedMinimum(data.getTf());
        kc=data.getKc();kd=data.getKd();ke=data.getKe();te=correctedMinimum(data.getTe());
        vfemax=data.getVfemax();vemin=data.getVemin();e1=data.getE1();se1=data.getSe1();
        e2=data.getE2();se2=data.getSe2();ki=data.getKi();xl=data.getXl();thetaP=data.getThetaP();
        kc1=data.getKc1();vbmax=data.getVbmax();kr=data.getKr();spdmlt=data.getSpdmlt();
    }
    private double minimumResolvedTimeConstant(){return minimumTimeConstantMultiplier*integrationStep;}
    private double correctedTransducer(double v){double m=minimumResolvedTimeConstant();if(v>0&&v<.25*m)return 0;if(v>.25*m&&v<.5*m)return .5*m;return v;}
    private double correctedDerivative(double v){double m=minimumResolvedTimeConstant();if(v>0&&v<.5*m)return 0;if(v>.5*m&&v<m)return m;return v;}
    private double correctedMinimum(double v){double m=minimumResolvedTimeConstant();return v>0&&v<m?m:v;}

    @Override public boolean nextStep(double dt,DynamicSimuMethod method,Machine machine,int flag){
        if(!initialized||dt<0)return false;if(dt==0){outputSignal=speedAdjusted(rawOutput(active,machine),machine);return true;}
        int stage=method==DynamicSimuMethod.MODIFIED_EULER?flag:2;
        if(stage==0){derivatives(state,oldDerivative,machine);add(state,oldDerivative,dt,trial);constrain(trial,machine);active=trial;}
        else if(stage==1){double[] d=new double[6];derivatives(trial,d,machine);for(int i=0;i<6;i++)state[i]+=.5*(oldDerivative[i]+d[i])*dt;constrain(state,machine);active=state;}
        else{double[] d=new double[6];derivatives(state,d,machine);add(state,d,dt,state);constrain(state,machine);active=state;}
        outputSignal=speedAdjusted(rawOutput(active,machine),machine);return true;
    }
    private void derivatives(double[] x,double[] dx,Machine machine){
        Arrays.fill(dx,0);Algebraic a=algebraics(x,machine);
        dx[VSENSE]=lagDerivative(machine.getDStabBus().getVoltageMag(),x[VSENSE],tr);
        dx[PID_DERIVATIVE_LAG]=lagDerivative(a.pidError,x[PID_DERIVATIVE_LAG],tdr);
        double pidRate=kir*a.pidError;
        if((a.pidUnlimited>=vrmax&&pidRate>0)||(a.pidUnlimited<=vrmin&&pidRate<0))pidRate=0;
        dx[PID_INTEGRAL]=pidRate;
        double piRate=kia*a.piError;
        if((a.piUnlimited>=vamax&&piRate>0)||(a.piUnlimited<=vamin&&piRate<0))piRate=0;
        dx[PI_INTEGRAL]=piRate;
        dx[RATE_LAG]=lagDerivative(a.vfe,x[RATE_LAG],tf);
        double fieldRate=te>EPS?(a.efe-a.vfe)/te:0,upper=fieldUpperLimit(x[VE],exciterIfd(machine));
        if((x[VE]>=upper-EPS&&fieldRate>0)||(x[VE]<=vemin+EPS&&fieldRate<0))fieldRate=0;
        dx[VE]=fieldRate;
    }
    private Algebraic algebraics(double[] x,Machine machine){
        double ifd=exciterIfd(machine);
        double field=te>EPS?clamp(x[VE],vemin,fieldUpperLimit(x[VE],ifd))
                :solveAlgebraicField(x[VE],x,machine,ifd);
        return algebraicsAtField(x,machine,field,ifd);
    }
    private Algebraic algebraicsAtField(double[] x,Machine machine,double field,double ifd){
        double vt=machine.getDStabBus().getVoltageMag(),sensed=tr>EPS?x[VSENSE]:vt;
        double rawEfd=rectifierOutput(field,ifd),vfe=fieldFeedback(field,ifd);
        double rateFeedback=tf>EPS?kf3*(vfe-x[RATE_LAG])/tf:0;
        double pss=stabilizerSignal(machine);
        double pidError=reference-sensed+summationLimiterInput()-rateFeedback
                +(vosLocation==PSS_AT_ERROR?pss:0);
        pidError=hvGate1(pidError);
        if(vosLocation==PSS_AFTER_HV_GATE_1)pidError+=pss;
        pidError=lvGate1(pidError);
        double derivative=tdr>EPS?kdr*(pidError-x[PID_DERIVATIVE_LAG])/tdr:0;
        double pidUnlimited=kpr*pidError+x[PID_INTEGRAL]+derivative;
        double pidOutput=lvGate2(hvGate2(clamp(pidUnlimited,vrmin,vrmax)));
        double feedback=kf1*rawEfd+kf2*vfe,piError=pidOutput-feedback;
        double piUnlimited=kpa*piError+x[PI_INTEGRAL];
        double piOutput=lvGate3(clamp(piUnlimited,vamin,vamax));
        double supply=selectedSupply(machine,field,vfe,rawEfd);
        double efe=Math.max(piOutput*supply,-kl*vfe);
        return new Algebraic(sensed,field,rawEfd,vfe,rateFeedback,pidError,pidUnlimited,
                pidOutput,feedback,piError,piUnlimited,piOutput,supply,efe);
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
    private double fieldResidual(double field,double[] x,Machine machine,double ifd){
        Algebraic a=algebraicsAtField(x,machine,field,ifd);
        return a.efe-a.vfe;
    }
    private double summationLimiterInput(){double v=0;if(uelLocation<2&&hasVuel)v+=vuel;if(oelLocation<2&&hasVoel)v+=voel;if(sclLocation<2&&hasVsclSum)v+=vsclSum;return v;}
    private double hvGate1(double v){if(uelLocation==UEL_HV_GATE_1&&hasVuel)v=Math.max(v,vuel);if(sclLocation==UEL_HV_GATE_1&&hasVsclUel)v=Math.max(v,vsclUel);return v;}
    private double lvGate1(double v){if(oelLocation==OEL_LV_GATE_1&&hasVoel)v=Math.min(v,voel);if(sclLocation==OEL_LV_GATE_1&&hasVsclOel)v=Math.min(v,vsclOel);return v;}
    private double hvGate2(double v){if(uelLocation==UEL_HV_GATE_2&&hasVuel)v=Math.max(v,vuel);if(sclLocation==UEL_HV_GATE_2&&hasVsclUel)v=Math.max(v,vsclUel);return v;}
    private double lvGate2(double v){if(oelLocation==OEL_LV_GATE_2&&hasVoel)v=Math.min(v,voel);if(sclLocation==OEL_LV_GATE_2&&hasVsclOel)v=Math.min(v,vsclOel);return v;}
    private double lvGate3(double v){if(oelLocation==OEL_LV_GATE_3&&hasVoel)v=Math.min(v,voel);return v;}

    private double potentialSource(Machine machine){
        Complex angle=new Complex(Math.cos(Math.toRadians(thetaP)),Math.sin(Math.toRadians(thetaP)));
        Complex kpPhasor=angle.multiply(kp),vt=machine.getDStabBus().getVoltage();
        Complex it=machine.getIxy().divide(machine.getIMultiFactor());
        return kpPhasor.multiply(vt).add(Complex.I.multiply(new Complex(ki,0).add(kpPhasor.multiply(xl))).multiply(it)).abs();
    }
    private double selectedSupply(Machine machine,double field,double vfe,double rawEfd){
        double ve1=sw1==SWITCH_A?potentialSource(machine):kp;
        double vb=potentialBridge(ve1,vfe);
        return sw2==SWITCH_A?vb:kr*rawEfd;
    }
    private double potentialBridge(double ve1,double vfe){
        if(Math.abs(ve1)<=EPS)return 0;
        return clamp(ve1*Exac1Exciter.rectifierFactor(kc1*vfe/ve1),0,vbmax);
    }
    private void constrain(double[] x,Machine machine){if(te>EPS)x[VE]=clamp(x[VE],vemin,fieldUpperLimit(x[VE],exciterIfd(machine)));for(int i=0;i<x.length;i++)if(!Double.isFinite(x[i]))x[i]=0;}
    private double fieldUpperLimit(double field,double ifd){double den=ke+Exac1Exciter.saturation(Math.max(field,0),e1,se1,e2,se2);if(den<=EPS)return Double.POSITIVE_INFINITY;return Math.max(vemin,(vfemax-kd*ifd)/den);}
    private double fieldFeedback(double field,double ifd){return field*(ke+Exac1Exciter.saturation(field,e1,se1,e2,se2))+kd*ifd;}
    private double rectifierOutput(double field,double ifd){if(Math.abs(field)<=EPS)return 0;return field*Exac1Exciter.rectifierFactor(kc*ifd/field);}
    private double rawOutput(double[] x,Machine machine){Algebraic a=algebraics(x,machine);return rectifierOutput(a.field,exciterIfd(machine));}
    private double speedAdjusted(double raw,Machine machine){return spdmlt!=0?raw*machine.getSpeed():raw;}
    private static double lagDerivative(double input,double stateValue,double time){return time>EPS?(input-stateValue)/time:0;}
    private static void add(double[] x,double[] d,double dt,double[] y){for(int i=0;i<x.length;i++)y[i]=x[i]+d[i]*dt;}
    private static double clamp(double v,double lo,double hi){return Math.max(lo,Math.min(hi,v));}
    private static boolean validOel(int v){return v>=INPUT_UNUSED&&v<=OEL_LV_GATE_3;}
    private static boolean validUel(int v){return v>=INPUT_UNUSED&&v<=UEL_HV_GATE_2;}
    private static double stabilizerSignal(Machine m){return m.getStabilizer()==null?0:m.getStabilizer().getOutput(m);}
    private static double exciterIfd(Machine m){double v=m.calculateIfd(MachineIfdBase.EXCITER);return Double.isFinite(v)?v:0;}

    public double getSensedVoltage(){return algebraics(active,getMachine()).sensed;}
    public double getInternalFieldVoltage(){return algebraics(active,getMachine()).field;}
    public double getFieldFeedback(){return algebraics(active,getMachine()).vfe;}
    public double getPidError(){return algebraics(active,getMachine()).pidError;}
    public double getPidOutput(){return algebraics(active,getMachine()).pidOutput;}
    public double getPidIntegralState(){return active[PID_INTEGRAL];}
    public double getDerivativeOutput(){Algebraic a=algebraics(active,getMachine());return tdr>EPS?kdr*(a.pidError-active[PID_DERIVATIVE_LAG])/tdr:0;}
    public double getPiOutput(){return algebraics(active,getMachine()).piOutput;}
    public double getRateFeedback(){return algebraics(active,getMachine()).rateFeedback;}
    public double getPotentialSource(){return potentialSource(getMachine());}
    public double getSelectedSupply(){return algebraics(active,getMachine()).supply;}
    public double getExciterInput(){return algebraics(active,getMachine()).efe;}
    public double getDynamicFieldUpperLimit(){return fieldUpperLimit(active[VE],exciterIfd(getMachine()));}
    @Override public double getOutput(Machine machine){outputSignal=speedAdjusted(rawOutput(active,machine),machine);return outputSignal;}
    @Override public void setRefPoint(double value){reference=value;} @Override public double getRefPoint(){return reference;}
    private record Algebraic(double sensed,double field,double rawEfd,double vfe,double rateFeedback,
            double pidError,double pidUnlimited,double pidOutput,double feedback,double piError,
            double piUnlimited,double piOutput,double supply,double efe){}
    @Override public AnController getAnController(){return getClass().getAnnotation(AnController.class);}
    @Override public Field getField(String name)throws Exception{return getClass().getField(name);}
    @Override public Object getFieldObject(Field field)throws Exception{return field.get(this);}
}
