package org.interpss.dstab.control.exc.psse.ac9c;

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

/** IEEE 421.5-2016 / PSS/E AC9C cascaded voltage/field-current regulator. */
@AnController(input="mach.vt",output="this.outputSignal",refPoint="this.reference",display={})
public final class Ac9cExciter extends AnnotateExciter implements IntegrationStepAware {
    public static final int INPUT_UNUSED=0,INPUT_SUMMATION=1,GATE_1=2,GATE_2=3;
    public static final int SWITCH_A=1,SWITCH_B=2;
    public static final int CHOPPER=0,THYRISTOR=1;

    private static final double EPS=1e-12;
    private static final int VE=0,VSENSE=1,PID_DERIVATIVE_LAG=2,PID_INTEGRAL=3,
            CURRENT_INTEGRAL=4,VR=5,VF=6;
    private final Ac9cData data;
    private final double[] state=new double[7],trial=new double[7],oldDerivative=new double[7];
    private double[] active=state;
    private boolean initialized,hasVuel,hasVoel,hasVsclSum,hasVsclUel,hasVsclOel;
    private double integrationStep,minimumTimeConstantMultiplier=1;
    private double vuel,voel,vsclSum,vsclUel,vsclOel;

    public int oelLocation,uelLocation,sclLocation,sw1,sct;
    public double tr,kpr,kir,kdr,tdr,vpidmax,vpidmin,kpa,kia,vamax,vamin;
    public double ka,ta,vrmax,vrmin,kf,tf,kfw,vfwmax,vfwmin;
    public double kc,kd,ke,te,vfemax,vemin,e1,se1,e2,se2;
    public double kp,ki1,ki2,kc1,kc2,xl,thetaP,vbmax1,vbmax2,vlim1,vlim2,spdmlt;
    public double reference,outputSignal;

    public Ac9cExciter(String id,Ac9cData data,Machine machine){
        super(id,"AC9C","PSS/E");this.data=data;this._data=data;setMachine(machine);
    }
    public Ac9cData getData(){return data;}
    public void setVuel(double v){vuel=v;hasVuel=true;}
    public void setVoel(double v){voel=v;hasVoel=true;}
    public void setVsclSum(double v){vsclSum=v;hasVsclSum=true;}
    public void setVsclUel(double v){vsclUel=v;hasVsclUel=true;}
    public void setVsclOel(double v){vsclOel=v;hasVsclOel=true;}
    @Override public void configureIntegrationStep(double seconds){configureIntegrationStep(seconds,1);}
    public void configureIntegrationStep(double seconds,double multiplier){integrationStep=seconds;minimumTimeConstantMultiplier=multiplier;}

    @Override public boolean initStates(BaseDStabBus<?,?> bus,Machine machine){
        loadAndCorrect();
        if(!validLocation(oelLocation)||!validLocation(uelLocation)||!validLocation(sclLocation)
                ||(sw1!=SWITCH_A&&sw1!=SWITCH_B)||tr<0||tdr<0||ta<0||tf<0||te<0
                ||kc<0||kd<0||kc1<0||kc2<0||vbmax1<0||vbmax2<0
                ||Math.abs(ka)<=EPS||vlim1<vlim2)return false;
        double vt0=bus.getVoltageMag(),ifd0=exciterIfd(machine);
        double ve0=Exac1Exciter.solveInternalVoltage(machine.getEfd(),kc*ifd0);
        if(!Double.isFinite(vt0)||vt0<=EPS||!Double.isFinite(ve0))return false;
        double vfe0=fieldFeedback(ve0,ifd0),vf0=kf*vfe0,vfw0=kfw*vfe0;
        vfwmax=Math.max(vfwmax,vfw0);vfwmin=Math.min(vfwmin,vfw0);
        double vct0=controlledAvailableVoltage(machine,vfe0);
        double vavr0=initialCurrentRegulatorOutput(vfe0,vct0,vfw0);
        if(!Double.isFinite(vavr0))return false;
        double vr0=ka*vavr0;
        vpidmax=Math.max(vpidmax,vf0);vpidmin=Math.min(vpidmin,vf0);
        vamax=Math.max(vamax,vavr0);vamin=Math.min(vamin,vavr0);
        vrmax=Math.max(vrmax,vr0);vrmin=Math.min(vrmin,vr0);
        state[VE]=ve0;state[VSENSE]=vt0;state[PID_DERIVATIVE_LAG]=0;
        state[PID_INTEGRAL]=vf0;state[CURRENT_INTEGRAL]=vavr0;
        state[VR]=vr0;state[VF]=vf0;
        System.arraycopy(state,0,trial,0,state.length);active=state;
        reference=vt0-summationLimiterInput()-stabilizerSignal(machine);
        outputSignal=speedAdjusted(rectifierOutput(ve0,ifd0),machine);initialized=true;return true;
    }

    private void loadAndCorrect(){
        oelLocation=data.getOelLocation();uelLocation=data.getUelLocation();sclLocation=data.getSclLocation();
        sw1=data.getSw1();sct=data.getSct();tr=correctedTransducer(data.getTr());
        kpr=data.getKpr();kir=data.getKir();kdr=data.getKdr();tdr=correctedBypass(data.getTdr());
        vpidmax=Math.max(data.getVpidmax(),data.getVpidmin());vpidmin=Math.min(data.getVpidmax(),data.getVpidmin());
        kpa=data.getKpa();kia=data.getKia();vamax=Math.max(data.getVamax(),data.getVamin());
        vamin=Math.min(data.getVamax(),data.getVamin());ka=data.getKa();ta=correctedBypass(data.getTa());
        vrmax=Math.max(data.getVrmax(),data.getVrmin());vrmin=Math.min(data.getVrmax(),data.getVrmin());
        kf=data.getKf();tf=correctedBypass(data.getTf());kfw=data.getKfw();
        vfwmax=Math.max(data.getVfwmax(),data.getVfwmin());vfwmin=Math.min(data.getVfwmax(),data.getVfwmin());
        kc=data.getKc();kd=data.getKd();ke=data.getKe();te=correctedMinimum(data.getTe());
        vfemax=data.getVfemax();vemin=data.getVemin();e1=data.getE1();se1=data.getSe1();
        e2=data.getE2();se2=data.getSe2();kp=data.getKp();ki1=data.getKi1();ki2=data.getKi2();
        kc1=data.getKc1();kc2=data.getKc2();xl=data.getXl();thetaP=data.getThetaP();
        vbmax1=data.getVbmax1();vbmax2=data.getVbmax2();vlim1=data.getVlim1();
        vlim2=data.getVlim2();spdmlt=data.getSpdmlt();
    }
    private double minimumResolvedTimeConstant(){return minimumTimeConstantMultiplier*integrationStep;}
    private double correctedTransducer(double v){double m=minimumResolvedTimeConstant();if(v>0&&v<.25*m)return 0;if(v>.25*m&&v<.5*m)return .5*m;return v;}
    private double correctedBypass(double v){double m=minimumResolvedTimeConstant();if(v>0&&v<.5*m)return 0;if(v>.5*m&&v<m)return m;return v;}
    private double correctedMinimum(double v){double m=minimumResolvedTimeConstant();return v>0&&v<m?m:v;}

    @Override public boolean nextStep(double dt,DynamicSimuMethod method,Machine machine,int flag){
        if(!initialized||dt<0)return false;if(dt==0){outputSignal=speedAdjusted(rawOutput(active,machine),machine);return true;}
        int stage=method==DynamicSimuMethod.MODIFIED_EULER?flag:2;
        if(stage==0){derivatives(state,oldDerivative,machine);add(state,oldDerivative,dt,trial);constrain(trial,machine);active=trial;}
        else if(stage==1){double[] d=new double[7];derivatives(trial,d,machine);for(int i=0;i<7;i++)state[i]+=.5*(oldDerivative[i]+d[i])*dt;constrain(state,machine);active=state;}
        else{double[] d=new double[7];derivatives(state,d,machine);add(state,d,dt,state);constrain(state,machine);active=state;}
        outputSignal=speedAdjusted(rawOutput(active,machine),machine);return true;
    }
    private void derivatives(double[] x,double[] dx,Machine machine){
        Arrays.fill(dx,0);Algebraic a=algebraics(x,machine);
        dx[VSENSE]=lagDerivative(machine.getDStabBus().getVoltageMag(),x[VSENSE],tr);
        dx[PID_DERIVATIVE_LAG]=lagDerivative(a.voltageError,x[PID_DERIVATIVE_LAG],tdr);
        double pidRate=kir*a.voltageError;
        if((a.ifdRefUnlimited>=vpidmax-EPS&&pidRate>0)||(a.ifdRefUnlimited<=vpidmin+EPS&&pidRate<0))pidRate=0;
        dx[PID_INTEGRAL]=pidRate;
        double currentRate=kia*a.currentError;
        if((a.vavrUnlimited>=vamax-EPS&&currentRate>0)||(a.vavrUnlimited<=vamin+EPS&&currentRate<0))currentRate=0;
        dx[CURRENT_INTEGRAL]=currentRate;
        double vrRate=ta>EPS?(ka*a.gatedVavr-x[VR])/ta:0;
        if((x[VR]>=vrmax-EPS&&vrRate>0)||(x[VR]<=vrmin+EPS&&vrRate<0))vrRate=0;
        dx[VR]=vrRate;
        dx[VF]=lagDerivative(kf*a.vfe,x[VF],tf);
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
        double voltageError=reference-sensed+summationLimiterInput()+stabilizerSignal(machine);
        double derivative=tdr>EPS?kdr*(voltageError-x[PID_DERIVATIVE_LAG])/tdr:0;
        double ifdRefUnlimited=kpr*voltageError+x[PID_INTEGRAL]+derivative;
        double ifdRef=clamp(ifdRefUnlimited,vpidmin,vpidmax);
        double vfe=fieldFeedback(field,ifd),vf=tf>EPS?x[VF]:kf*vfe;
        double currentError=lvGate(hvGate(ifdRef-vf,GATE_1),GATE_1);
        double vavrUnlimited=kpa*currentError+x[CURRENT_INTEGRAL];
        double vavr=clamp(vavrUnlimited,vamin,vamax);
        double gatedVavr=lvGate(hvGate(vavr,GATE_2),GATE_2);
        double vr=ta>EPS?clamp(x[VR],vrmin,vrmax):clamp(ka*gatedVavr,vrmin,vrmax);
        double vct=controlledAvailableVoltage(machine,vfe),vfw=clamp(kfw*vfe,vfwmin,vfwmax);
        double vb=powerStageBias(vavr,vct,vfw),efe=vr+vb;
        return new Algebraic(sensed,field,vfe,voltageError,ifdRefUnlimited,ifdRef,vf,
                currentError,vavrUnlimited,vavr,gatedVavr,vr,vct,vfw,vb,efe);
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
    private double fieldResidual(double field,double[] x,Machine machine,double ifd){return algebraicsAtField(x,machine,field,ifd).efe-fieldFeedback(field,ifd);}

    private double initialCurrentRegulatorOutput(double vfe,double vct,double vfw){
        if(sct!=CHOPPER)return (vfe-vct)/ka;
        double[] candidates={(vfe-vct)/ka,vfe/ka,(vfe+vfw)/ka};double best=Double.NaN,bestError=Double.POSITIVE_INFINITY;
        for(double candidate:candidates){double error=Math.abs(ka*candidate+powerStageBias(candidate,vct,vfw)-vfe);
            if(error<bestError){bestError=error;best=candidate;}}
        return best;
    }
    private double summationLimiterInput(){double v=0;if(oelLocation<2&&hasVoel)v+=voel;if(uelLocation<2&&hasVuel)v+=vuel;if(sclLocation<2&&hasVsclSum)v+=vsclSum;return v;}
    private double hvGate(double v,int gate){if(uelLocation==gate&&hasVuel)v=Math.max(v,vuel);if(sclLocation==gate&&hasVsclUel)v=Math.max(v,vsclUel);return v;}
    private double lvGate(double v,int gate){if(oelLocation==gate&&hasVoel)v=Math.min(v,voel);if(sclLocation==gate&&hasVsclOel)v=Math.min(v,vsclOel);return v;}
    private Complex machineBaseCurrent(Machine machine){return machine.getIxy().divide(machine.getIMultiFactor());}
    private double compoundSource(Machine machine){
        Complex angle=new Complex(Math.cos(Math.toRadians(thetaP)),Math.sin(Math.toRadians(thetaP)));
        Complex kpPhasor=angle.multiply(kp),vt=machine.getDStabBus().getVoltage(),it=machineBaseCurrent(machine);
        return kpPhasor.multiply(vt).add(Complex.I.multiply(new Complex(ki1,0).add(kpPhasor.multiply(xl))).multiply(it)).abs();
    }
    private double currentSource(Machine machine){return machineBaseCurrent(machine).multiply(ki2).abs();}
    private double selectedSource1(Machine machine){return sw1==SWITCH_A?compoundSource(machine):kp;}
    private static double bridgeComponent(double source,double vfe,double loading,double maximum){
        if(Math.abs(source)<=EPS)return 0;return clamp(source*Exac1Exciter.rectifierFactor(loading*vfe/source),0,maximum);
    }
    private static double currentBridgeComponent(double source,double vfe,double loading,double maximum){
        if(Math.abs(source)<=EPS)return 0;
        double denominator=Math.max(Math.abs(source),.001);
        return clamp(source*Exac1Exciter.rectifierFactor(loading*vfe/denominator),0,maximum);
    }
    private double controlledAvailableVoltage(Machine machine,double vfe){
        return bridgeComponent(selectedSource1(machine),vfe,kc1,vbmax1)
                +currentBridgeComponent(currentSource(machine),vfe,kc2,vbmax2);
    }
    private double powerStageBias(double vavr,double vct,double vfw){
        if(sct!=CHOPPER||vavr>vlim1)return vct;
        if(vavr>vlim2)return 0;
        return -vfw;
    }
    private void constrain(double[] x,Machine machine){
        if(ta>EPS)x[VR]=clamp(x[VR],vrmin,vrmax);if(tf>EPS)x[VF]=finite(x[VF]);
        if(te>EPS)x[VE]=clamp(x[VE],vemin,fieldUpperLimit(x[VE],exciterIfd(machine)));
        for(int i=0;i<x.length;i++)if(!Double.isFinite(x[i]))x[i]=0;
    }
    private double fieldUpperLimit(double field,double ifd){double den=ke+Exac1Exciter.saturation(Math.max(field,0),e1,se1,e2,se2);if(den<=EPS)return Double.POSITIVE_INFINITY;return Math.max(vemin,(vfemax-kd*ifd)/den);}
    private double fieldFeedback(double field,double ifd){return field*(ke+Exac1Exciter.saturation(field,e1,se1,e2,se2))+kd*ifd;}
    private double rectifierOutput(double field,double ifd){if(Math.abs(field)<=EPS)return 0;return field*Exac1Exciter.rectifierFactor(kc*ifd/field);}
    private double rawOutput(double[] x,Machine machine){return rectifierOutput(algebraics(x,machine).field,exciterIfd(machine));}
    private double speedAdjusted(double raw,Machine machine){return spdmlt!=0?raw*machine.getSpeed():raw;}
    private static double lagDerivative(double input,double stateValue,double time){return time>EPS?(input-stateValue)/time:0;}
    private static void add(double[] x,double[] d,double dt,double[] y){for(int i=0;i<x.length;i++)y[i]=x[i]+d[i]*dt;}
    private static double clamp(double v,double lo,double hi){return Math.max(lo,Math.min(hi,v));}
    private static double finite(double v){return Double.isFinite(v)?v:0;}
    private static boolean validLocation(int v){return v>=INPUT_UNUSED&&v<=GATE_2;}
    private static double stabilizerSignal(Machine m){return m.getStabilizer()==null?0:m.getStabilizer().getOutput(m);}
    private static double exciterIfd(Machine m){double v=m.calculateIfd(MachineIfdBase.EXCITER);return Double.isFinite(v)?v:0;}

    public double getSensedVoltage(){return algebraics(active,getMachine()).sensed;}
    public double getInternalFieldVoltage(){return algebraics(active,getMachine()).field;}
    public double getFieldFeedback(){return algebraics(active,getMachine()).vfe;}
    public double getVoltageError(){return algebraics(active,getMachine()).voltageError;}
    public double getPidIntegralState(){return active[PID_INTEGRAL];}
    public double getIfdReference(){return algebraics(active,getMachine()).ifdRef;}
    public double getCurrentError(){return algebraics(active,getMachine()).currentError;}
    public double getCurrentIntegralState(){return active[CURRENT_INTEGRAL];}
    public double getCurrentRegulatorOutput(){return algebraics(active,getMachine()).vavr;}
    public double getGatedCurrentRegulatorOutput(){return algebraics(active,getMachine()).gatedVavr;}
    public double getBridgeOutput(){return algebraics(active,getMachine()).vr;}
    public double getFilteredFieldCurrent(){return algebraics(active,getMachine()).vf;}
    public double getFreeWheelFeedback(){return algebraics(active,getMachine()).vfw;}
    public double getCompoundSource(){return compoundSource(getMachine());}
    public double getCurrentSource(){return currentSource(getMachine());}
    public double getControlledAvailableVoltage(){return algebraics(active,getMachine()).vct;}
    public double getPowerStageBias(){return algebraics(active,getMachine()).vb;}
    public double getExciterInput(){return algebraics(active,getMachine()).efe;}
    public double getDynamicFieldUpperLimit(){return fieldUpperLimit(active[VE],exciterIfd(getMachine()));}
    @Override public double getOutput(Machine machine){outputSignal=speedAdjusted(rawOutput(active,machine),machine);return outputSignal;}
    @Override public void setRefPoint(double value){reference=value;} @Override public double getRefPoint(){return reference;}
    private record Algebraic(double sensed,double field,double vfe,double voltageError,
            double ifdRefUnlimited,double ifdRef,double vf,double currentError,
            double vavrUnlimited,double vavr,double gatedVavr,double vr,double vct,
            double vfw,double vb,double efe){}
    @Override public AnController getAnController(){return getClass().getAnnotation(AnController.class);}
    @Override public Field getField(String name)throws Exception{return getClass().getField(name);}
    @Override public Object getFieldObject(Field field)throws Exception{return field.get(this);}
}
