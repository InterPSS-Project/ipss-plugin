package org.interpss.dstab.control.exc.psse.ac11c;

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

/** IEEE 421.5-2016 / PSS/E AC11C brushless excitation system. */
@AnController(input="mach.vt",output="this.outputSignal",refPoint="this.reference",display={})
public final class Ac11cExciter extends AnnotateExciter implements IntegrationStepAware {
    public static final int INPUT_UNUSED=0,INPUT_SUMMATION=1,GATE_1=2,GATE_2=3;
    public static final int VOS_ERROR=1,VOS_SWITCHED=2,VOS_AVR_OUTPUT=3;
    public static final int SWITCH_A=1,SWITCH_B=2;

    private static final double EPS=1e-12;
    private static final int VE=0,VSENSE=1,MAIN_Q=2,MAIN_I=3,PSS_Q=4,PSS_I=5,
            UEL_Q=6,UEL_I=7,OEL_I=8;
    private final Ac11cData data;
    private final double[] state=new double[9],trial=new double[9],oldDerivative=new double[9];
    private double[] active=state;
    private boolean initialized,hasVuel,hasVoel,hasVsclSum,hasVsclUel,hasVsclOel;
    private double integrationStep,minimumTimeConstantMultiplier=1;
    private double vuel,voel,vsclSum,vsclUel,vsclOel;

    public int oelLocation,uelLocation,vosLocation,sclLocation,sw1;
    public double tr,kpa,tia,kpu,tiu,kb,tb,kpo,tio;
    public double vrsmax,vrsmin,vrmax,vrmin,vamax,vamin;
    public double te,kc,kd,ke,vfemax,vemin,e1,se1,e2,se2;
    public double kp,ki,xl,thetaP,kc1,vbmax1,ki2,kc2,vbmax2,kboost,vboost;
    public double reference,outputSignal;

    public Ac11cExciter(String id,Ac11cData data,Machine machine){
        super(id,"AC11C","PSS/E");this.data=data;this._data=data;setMachine(machine);
    }
    public Ac11cData getData(){return data;}
    public void setVuel(double v){vuel=v;hasVuel=true;}
    public void setVoel(double v){voel=v;hasVoel=true;}
    public void setVsclSum(double v){vsclSum=v;hasVsclSum=true;}
    public void setVsclUel(double v){vsclUel=v;hasVsclUel=true;}
    public void setVsclOel(double v){vsclOel=v;hasVsclOel=true;}
    @Override public void configureIntegrationStep(double seconds){configureIntegrationStep(seconds,1);}
    public void configureIntegrationStep(double seconds,double multiplier){
        integrationStep=seconds;minimumTimeConstantMultiplier=multiplier;
    }

    @Override public boolean initStates(BaseDStabBus<?,?> bus,Machine machine){
        loadAndCorrect();
        if(!validLocation(oelLocation)||!validLocation(uelLocation)||!validLocation(sclLocation)
                ||vosLocation<VOS_ERROR||vosLocation>VOS_AVR_OUTPUT
                ||(sw1!=SWITCH_A&&sw1!=SWITCH_B)||tr<0||tia<0||tiu<0||tb<=0||tio<0||te<0
                ||kpa<=0||kpu<=0||kb<=0||kpo<=0||kc<0||kd<0||kc1<0||kc2<0
                ||vbmax1<0||vbmax2<0)return false;
        double vt0=bus.getVoltageMag(),ifd0=exciterIfd(machine);
        double ve0=Exac1Exciter.solveInternalVoltage(machine.getEfd(),kc*ifd0);
        if(!Double.isFinite(vt0)||vt0<=EPS||!Double.isFinite(ve0))return false;
        double vfe0=fieldFeedback(ve0,ifd0),vb10=bridge1(machine,vfe0);
        double boost0=boostOutput(machine,vfe0);
        if(Math.abs(vb10)<=EPS&&Math.abs(vfe0-boost0)>1e-9)return false;
        double va0=Math.abs(vb10)>EPS?(vfe0-boost0)/vb10:0;
        if(!Double.isFinite(va0))return false;
        vrmax=Math.max(vrmax,va0);vrmin=Math.min(vrmin,va0);
        vamax=Math.max(vamax,va0);vamin=Math.min(vamin,va0);
        vrsmax=Math.max(vrsmax,0);vrsmin=Math.min(vrsmin,0);
        state[VE]=ve0;state[VSENSE]=vt0;
        state[MAIN_Q]=va0;state[MAIN_I]=va0;
        state[PSS_Q]=0;state[PSS_I]=0;
        state[UEL_Q]=va0;state[UEL_I]=va0;state[OEL_I]=va0;
        System.arraycopy(state,0,trial,0,state.length);active=state;
        double vs=stabilizerSignal(machine);
        reference=vt0-summationLimiterInput()-(vosLocation==VOS_ERROR?vs:0);
        outputSignal=rectifierOutput(ve0,ifd0);initialized=true;return true;
    }

    private void loadAndCorrect(){
        oelLocation=data.getOelLocation();uelLocation=data.getUelLocation();
        vosLocation=data.getVosLocation();sclLocation=data.getSclLocation();sw1=data.getSw1();
        tr=correctedTransducer(data.getTr());
        kpa=correctedPositive(data.getKpa());tia=correctedMinimum(data.getTia());
        kpu=correctedPositive(data.getKpu());tiu=correctedMinimum(data.getTiu());
        kb=correctedPositive(data.getKb());tb=correctedPositive(data.getTb());
        kpo=correctedPositive(data.getKpo());tio=correctedMinimum(data.getTio());
        vrsmax=Math.max(data.getVrsmax(),data.getVrsmin());vrsmin=Math.min(data.getVrsmax(),data.getVrsmin());
        vrmax=Math.max(data.getVrmax(),data.getVrmin());vrmin=Math.min(data.getVrmax(),data.getVrmin());
        vamax=Math.max(data.getVamax(),data.getVamin());vamin=Math.min(data.getVamax(),data.getVamin());
        te=correctedMinimum(data.getTe());kc=data.getKc();kd=data.getKd();ke=data.getKe();
        vfemax=data.getVfemax();vemin=data.getVemin();e1=data.getE1();se1=data.getSe1();
        e2=data.getE2();se2=data.getSe2();kp=data.getKp();ki=data.getKi();xl=data.getXl();
        thetaP=data.getThetaP();kc1=data.getKc1();vbmax1=data.getVbmax1();ki2=data.getKi2();
        kc2=data.getKc2();vbmax2=data.getVbmax2();kboost=data.getKboost();vboost=data.getVboost();
    }
    private double minimumResolvedTimeConstant(){return minimumTimeConstantMultiplier*integrationStep;}
    private double correctedTransducer(double v){double m=minimumResolvedTimeConstant();
        if(v>0&&v<.25*m)return 0;if(v>.25*m&&v<.5*m)return .5*m;return v;}
    private double correctedMinimum(double v){double m=minimumResolvedTimeConstant();return v>0&&v<m?m:v;}
    private double correctedPositive(double v){return v<=0?minimumResolvedTimeConstant():v;}

    @Override public boolean nextStep(double dt,DynamicSimuMethod method,Machine machine,int flag){
        if(!initialized||dt<0)return false;
        if(dt==0){outputSignal=rawOutput(active,machine);return true;}
        int stage=method==DynamicSimuMethod.MODIFIED_EULER?flag:2;
        if(stage==0){derivatives(state,oldDerivative,machine);add(state,oldDerivative,dt,trial);
            constrain(trial,machine);active=trial;}
        else if(stage==1){double[] d=new double[9];derivatives(trial,d,machine);
            for(int i=0;i<9;i++)state[i]+=.5*(oldDerivative[i]+d[i])*dt;
            constrain(state,machine);active=state;}
        else{double[] d=new double[9];derivatives(state,d,machine);add(state,d,dt,state);
            constrain(state,machine);active=state;}
        outputSignal=rawOutput(active,machine);return true;
    }

    private void derivatives(double[] x,double[] dx,Machine machine){
        Arrays.fill(dx,0);Algebraic a=algebraics(x,machine);
        dx[VSENSE]=lagDerivative(machine.getDStabBus().getVoltageMag(),x[VSENSE],tr);
        dx[MAIN_Q]=altPidLagDerivative(a.mainPi,x[MAIN_Q]);
        dx[MAIN_I]=altPidIntegralDerivative(a.gatedError,a.mainUnlimited,kpa,tia,vrmin,vrmax);
        dx[PSS_Q]=altPidLagDerivative(a.pssPi,x[PSS_Q]);
        dx[PSS_I]=altPidIntegralDerivative(a.pssInput,a.pssUnlimited,kpa,tia,vrsmin,vrsmax);
        dx[UEL_Q]=altPidLagDerivative(a.uelPi,x[UEL_Q]);
        dx[UEL_I]=altPidIntegralDerivative(a.gatedError,a.uelUnlimited,kpu,tiu,vrmin,vrmax);
        dx[OEL_I]=piIntegralDerivative(a.gatedError,a.oelUnlimited,kpo,tio,vrmin,vrmax);
        double fieldRate=te>EPS?(a.efe-a.vfe)/te:0;
        double upper=fieldUpperLimit(x[VE],exciterIfd(machine));
        if((x[VE]>=upper-EPS&&fieldRate>0)||(x[VE]<=vemin+EPS&&fieldRate<0))fieldRate=0;
        dx[VE]=fieldRate;
    }
    private double altPidLagDerivative(double pi,double q){return (kb/tb)*(pi-q);}
    private static double altPidIntegralDerivative(double input,double unlimited,double gain,
            double ti,double lower,double upper){
        if(ti<=EPS||unlimited>=upper||unlimited<=lower)return 0;return (gain/ti)*input;
    }
    private static double piIntegralDerivative(double input,double unlimited,double gain,
            double ti,double lower,double upper){
        if(ti<=EPS||unlimited>=upper||unlimited<=lower)return 0;return (gain/ti)*input;
    }

    private Algebraic algebraics(double[] x,Machine machine){
        double ifd=exciterIfd(machine);
        double field=te>EPS?clamp(x[VE],vemin,fieldUpperLimit(x[VE],ifd))
                :solveAlgebraicField(x[VE],x,machine,ifd);
        return algebraicsAtField(x,machine,field,ifd);
    }
    private Algebraic algebraicsAtField(double[] x,Machine machine,double field,double ifd){
        double vt=machine.getDStabBus().getVoltageMag(),sensed=tr>EPS?x[VSENSE]:vt;
        double vs=stabilizerSignal(machine),vs1=0,vs2=0;
        double error=reference-sensed+summationLimiterInput();
        if(vosLocation==VOS_ERROR)error+=vs;
        double afterUel=uelLocation==GATE_1&&hasVuel?Math.max(error,vuel):error;
        boolean uelActive=uelLocation==GATE_1&&hasVuel&&vuel>=error;
        double afterOel=oelLocation==GATE_1&&hasVoel?Math.min(afterUel,voel):afterUel;
        boolean oelActive=oelLocation==GATE_1&&hasVoel&&voel<=afterUel;
        double gatedError=afterOel;
        if(sclLocation==GATE_1&&hasVsclUel)gatedError=Math.max(gatedError,vsclUel);
        if(sclLocation==GATE_1&&hasVsclOel)gatedError=Math.min(gatedError,vsclOel);
        if(vosLocation==VOS_SWITCHED){if(uelActive||oelActive)vs2=vs;else vs1=vs;}
        gatedError+=vs1;

        double mainPi=kpa*gatedError+x[MAIN_I];
        double mainUnlimited=altPidOutput(mainPi,x[MAIN_Q]);
        double uelPi=kpu*gatedError+x[UEL_I];
        double uelUnlimited=altPidOutput(uelPi,x[UEL_Q]);
        double oelUnlimited=kpo*gatedError+x[OEL_I];
        double selected=uelActive?uelUnlimited:mainUnlimited;
        if(oelActive)selected=oelUnlimited;
        double vr=clamp(selected,vrmin,vrmax);
        double gatedVr=vr;
        if(uelLocation==GATE_2&&hasVuel)gatedVr=Math.max(gatedVr,vuel);
        if(sclLocation==GATE_2&&hasVsclUel)gatedVr=Math.max(gatedVr,vsclUel);
        if(oelLocation==GATE_2&&hasVoel)gatedVr=Math.min(gatedVr,voel);
        if(sclLocation==GATE_2&&hasVsclOel)gatedVr=Math.min(gatedVr,vsclOel);

        double pssInput=vs2+(vosLocation==VOS_AVR_OUTPUT?vs:0);
        double pssPi=kpa*pssInput+x[PSS_I];
        double pssUnlimited=altPidOutput(pssPi,x[PSS_Q]);
        double vrs=clamp(pssUnlimited,vrsmin,vrsmax);
        double vaUnlimited=gatedVr+vrs,va=clamp(vaUnlimited,vamin,vamax);
        double vfe=fieldFeedback(field,ifd),vb1=bridge1(machine,vfe),vb2=bridge2(machine,vfe);
        double boost=vt<=vboost?vb2:0,efe=va*vb1+boost;
        return new Algebraic(sensed,field,vfe,error,gatedError,uelActive,oelActive,
                mainPi,mainUnlimited,uelPi,uelUnlimited,oelUnlimited,selected,vr,gatedVr,
                pssInput,pssPi,pssUnlimited,vrs,vaUnlimited,va,vb1,vb2,boost,efe);
    }
    private double altPidOutput(double pi,double q){return pi+kb*(pi-q);}

    private double solveAlgebraicField(double initial,double[] x,Machine machine,double ifd){
        double value=Double.isFinite(initial)?initial:0;
        for(int i=0;i<30;i++){
            value=clamp(value,vemin,fieldUpperLimit(value,ifd));
            double residual=fieldResidual(value,x,machine,ifd);if(Math.abs(residual)<1e-11)break;
            double h=1e-6*Math.max(1,Math.abs(value));
            double slope=(fieldResidual(value+h,x,machine,ifd)-fieldResidual(value-h,x,machine,ifd))/(2*h);
            if(!Double.isFinite(slope)||Math.abs(slope)<=EPS)break;
            double next=value-residual/slope;if(!Double.isFinite(next))break;value=next;
        }
        return clamp(value,vemin,fieldUpperLimit(value,ifd));
    }
    private double fieldResidual(double field,double[] x,Machine machine,double ifd){
        return algebraicsAtField(x,machine,field,ifd).efe-fieldFeedback(field,ifd);
    }

    private double summationLimiterInput(){double v=0;
        if(oelLocation==INPUT_SUMMATION&&hasVoel)v+=voel;
        if(uelLocation==INPUT_SUMMATION&&hasVuel)v+=vuel;
        if(sclLocation==INPUT_SUMMATION&&hasVsclSum)v+=vsclSum;return v;}
    private Complex machineBaseCurrent(Machine machine){return machine.getIxy().divide(machine.getIMultiFactor());}
    private double compoundSource(Machine machine){
        Complex angle=new Complex(Math.cos(Math.toRadians(thetaP)),Math.sin(Math.toRadians(thetaP)));
        Complex kpPhasor=angle.multiply(kp),vt=machine.getDStabBus().getVoltage(),it=machineBaseCurrent(machine);
        return kpPhasor.multiply(vt).add(Complex.I.multiply(new Complex(ki,0)
                .add(kpPhasor.multiply(xl))).multiply(it)).abs();
    }
    private double selectedSource1(Machine machine){return sw1==SWITCH_A?compoundSource(machine):kp;}
    private double currentSource2(Machine machine){return machineBaseCurrent(machine).multiply(ki2).abs();}
    private static double bridge(double source,double vfe,double loading,double maximum,double denominatorFloor){
        double nonnegative=Math.max(0,source);if(nonnegative<=EPS)return 0;
        double denominator=Math.max(nonnegative,denominatorFloor);
        return clamp(nonnegative*Exac1Exciter.rectifierFactor(loading*vfe/denominator),0,maximum);
    }
    private double bridge1(Machine machine,double vfe){return bridge(selectedSource1(machine),vfe,kc1,vbmax1,EPS);}
    private double bridge2(Machine machine,double vfe){return bridge(currentSource2(machine)+kboost,vfe,kc2,vbmax2,.001);}
    private double boostOutput(Machine machine,double vfe){
        return machine.getDStabBus().getVoltageMag()<=vboost?bridge2(machine,vfe):0;
    }
    private void constrain(double[] x,Machine machine){
        if(te>EPS)x[VE]=clamp(x[VE],vemin,fieldUpperLimit(x[VE],exciterIfd(machine)));
        for(int i=0;i<x.length;i++)if(!Double.isFinite(x[i]))x[i]=0;
    }
    private double fieldUpperLimit(double field,double ifd){
        double den=ke+Exac1Exciter.saturation(Math.max(field,0),e1,se1,e2,se2);
        if(den<=EPS)return Double.POSITIVE_INFINITY;
        return Math.max(vemin,(vfemax-kd*ifd)/den);
    }
    private double fieldFeedback(double field,double ifd){
        return field*(ke+Exac1Exciter.saturation(field,e1,se1,e2,se2))+kd*ifd;
    }
    private double rectifierOutput(double field,double ifd){
        if(Math.abs(field)<=EPS)return 0;return field*Exac1Exciter.rectifierFactor(kc*ifd/field);
    }
    private double rawOutput(double[] x,Machine machine){return rectifierOutput(algebraics(x,machine).field,exciterIfd(machine));}
    private static double lagDerivative(double input,double stateValue,double time){return time>EPS?(input-stateValue)/time:0;}
    private static void add(double[] x,double[] d,double dt,double[] y){for(int i=0;i<x.length;i++)y[i]=x[i]+d[i]*dt;}
    private static double clamp(double v,double lo,double hi){return Math.max(lo,Math.min(hi,v));}
    private static boolean validLocation(int v){return v>=INPUT_UNUSED&&v<=GATE_2;}
    private static double stabilizerSignal(Machine m){return m.getStabilizer()==null?0:m.getStabilizer().getOutput(m);}
    private static double exciterIfd(Machine m){double v=m.calculateIfd(MachineIfdBase.EXCITER);return Double.isFinite(v)?v:0;}

    public double getSensedVoltage(){return algebraics(active,getMachine()).sensed;}
    public double getInternalFieldVoltage(){return algebraics(active,getMachine()).field;}
    public double getFieldFeedback(){return algebraics(active,getMachine()).vfe;}
    public double getVoltageError(){return algebraics(active,getMachine()).error;}
    public double getGatedVoltageError(){return algebraics(active,getMachine()).gatedError;}
    public boolean isUelControllerActive(){return algebraics(active,getMachine()).uelActive;}
    public boolean isOelControllerActive(){return algebraics(active,getMachine()).oelActive;}
    public double getMainLagState(){return active[MAIN_Q];} public double getMainIntegralState(){return active[MAIN_I];}
    public double getPssLagState(){return active[PSS_Q];} public double getPssIntegralState(){return active[PSS_I];}
    public double getUelLagState(){return active[UEL_Q];} public double getUelIntegralState(){return active[UEL_I];}
    public double getOelIntegralState(){return active[OEL_I];}
    public double getMainRegulatorOutput(){return algebraics(active,getMachine()).mainUnlimited;}
    public double getUelRegulatorOutput(){return algebraics(active,getMachine()).uelUnlimited;}
    public double getOelRegulatorOutput(){return algebraics(active,getMachine()).oelUnlimited;}
    public double getSelectedRegulatorOutput(){return algebraics(active,getMachine()).selected;}
    public double getLimitedRegulatorOutput(){return algebraics(active,getMachine()).vr;}
    public double getGatedRegulatorOutput(){return algebraics(active,getMachine()).gatedVr;}
    public double getPssRegulatorInput(){return algebraics(active,getMachine()).pssInput;}
    public double getPssRegulatorOutput(){return algebraics(active,getMachine()).vrs;}
    public double getControlElementOutput(){return algebraics(active,getMachine()).va;}
    public double getCompoundSource(){return compoundSource(getMachine());}
    public double getCurrentSource2(){return currentSource2(getMachine());}
    public double getBridge1Output(){return algebraics(active,getMachine()).vb1;}
    public double getBridge2Output(){return algebraics(active,getMachine()).vb2;}
    public double getBoostOutput(){return algebraics(active,getMachine()).boost;}
    public double getExciterInput(){return algebraics(active,getMachine()).efe;}
    public double getDynamicFieldUpperLimit(){return fieldUpperLimit(active[VE],exciterIfd(getMachine()));}
    @Override public double getOutput(Machine machine){outputSignal=rawOutput(active,machine);return outputSignal;}
    @Override public void setRefPoint(double value){reference=value;} @Override public double getRefPoint(){return reference;}
    private record Algebraic(double sensed,double field,double vfe,double error,double gatedError,
            boolean uelActive,boolean oelActive,double mainPi,double mainUnlimited,
            double uelPi,double uelUnlimited,double oelUnlimited,double selected,double vr,
            double gatedVr,double pssInput,double pssPi,double pssUnlimited,double vrs,
            double vaUnlimited,double va,double vb1,double vb2,double boost,double efe){}
    @Override public AnController getAnController(){return getClass().getAnnotation(AnController.class);}
    @Override public Field getField(String name)throws Exception{return getClass().getField(name);}
    @Override public Object getFieldObject(Field field)throws Exception{return field.get(this);}
}
