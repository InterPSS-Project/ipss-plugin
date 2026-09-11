package org.interpss.dstab.control.exc.psse.st8c;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.control.exc.psse.exac1.Exac1Exciter;
import org.interpss.dstab.control.util.IntegrationStepAware;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.ICMLMachineVoltageProvider;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnnotateExciter;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.MachineIfdBase;

/** Native PSS/E implementation of the IEEE 421.5-2016 ST8C exciter. */
@AnController(input="mach.vt",output="this.outputSignal",refPoint="this.reference",display={})
public final class St8cExciter extends AnnotateExciter implements IntegrationStepAware {
    public static final int INPUT_SUMMATION=1,INPUT_GATE=2,SWITCH_A=1,SWITCH_B=2;
    private static final double EPS=1e-12;
    private static final int SENSED=0,OUTER_I=1,INNER_I=2,BRIDGE=3,IFD_FEEDBACK=4;
    private final St8cData data;
    private final double[] state=new double[5],trial=new double[5],oldDerivative=new double[5];
    private double[] active=state;
    private boolean initialized,hasVuel,hasVoel,hasVsclSum,hasVsclUel,hasVsclOel;
    private double vuel,voel,vsclSum,vsclUel,vsclOel,integrationStep,minimumTimeConstantMultiplier=1;

    public int oel,uel,scl,sw1;
    public double tr,kpr,kir,vpimax,vpimin,kpa,kia,vamax,vamin,ka,ta,vrmax,vrmin,kf,tf;
    public double kc1,kp,ki1,xl,thetaP,vb1max,kc2,ki2,vb2max,reference,outputSignal;

    public St8cExciter(String id,St8cData data,Machine machine){
        super(id,"ST8C","PSS/E");this.data=data;this._data=data;setMachine(machine);
    }
    public St8cData getData(){return data;}
    @Override public void configureIntegrationStep(double seconds){configureIntegrationStep(seconds,1);}
    public void configureIntegrationStep(double seconds,double multiplier){integrationStep=seconds;minimumTimeConstantMultiplier=multiplier;}

    @Override public boolean initStates(BaseDStabBus<?,?> bus,Machine machine){
        loadAndCorrect();
        if(!validSelector(oel)||!validSelector(uel)||!validSelector(scl)||(sw1!=SWITCH_A&&sw1!=SWITCH_B)
                ||tr<0||ta<0||tf<0||kpr<0||kir<0||kpa<0||kia<0||ka<=EPS||kf<0
                ||kc1<0||kc2<0||vb1max<0||vb2max<0||!finiteParameters())return false;
        double sensed=sensingVoltage(machine),ifd=exciterIfd(machine),efd0=machine.getEfd();
        double vb1=bridge1(machine,ifd),vb2=bridge2(machine,ifd);
        if(!Double.isFinite(sensed)||sensed<=EPS||!Double.isFinite(efd0)||vb1<=EPS)return false;
        double vr0=(efd0-vb2)/vb1,va0=vr0/ka,feedback0=kf*ifd;
        double innerInput0=kia>EPS?0:va0/kpa;
        double ifdReference0=ifd+feedback0+innerInput0;
        double outerInput0=kir>EPS?0:ifdReference0/kpr;
        vpimax=Math.max(vpimax,ifdReference0);vpimin=Math.min(vpimin,ifdReference0);
        vamax=Math.max(vamax,va0);vamin=Math.min(vamin,va0);
        vrmax=Math.max(vrmax,vr0);vrmin=Math.min(vrmin,vr0);
        state[SENSED]=sensed;state[OUTER_I]=ifdReference0-kpr*outerInput0;
        state[INNER_I]=va0-kpa*innerInput0;state[BRIDGE]=vr0;state[IFD_FEEDBACK]=feedback0;
        reference=sensed+outerInput0-stabilizerSignal(machine)-directLimiterInput();
        System.arraycopy(state,0,trial,0,state.length);active=state;outputSignal=efd0;initialized=true;return true;
    }

    private void loadAndCorrect(){
        oel=normalizeSelector(data.getOel());uel=normalizeSelector(data.getUel());scl=normalizeSelector(data.getScl());
        sw1=data.getSw1();tr=correctedBypass(data.getTr());kpr=data.getKpr();kir=data.getKir();
        if(Math.abs(kpr)<=EPS&&Math.abs(kir)<=EPS)kpr=40;
        vpimax=Math.max(data.getVpimax(),data.getVpimin());vpimin=Math.min(data.getVpimax(),data.getVpimin());
        kpa=data.getKpa();kia=data.getKia();if(Math.abs(kpa)<=EPS&&Math.abs(kia)<=EPS)kpa=1;
        vamax=Math.max(data.getVamax(),data.getVamin());vamin=Math.min(data.getVamax(),data.getVamin());
        ka=data.getKa();if(Math.abs(ka)<=EPS)ka=minimumTime();ta=correctedBypass(data.getTa());
        vrmax=Math.max(data.getVrmax(),data.getVrmin());vrmin=Math.min(data.getVrmax(),data.getVrmin());
        kf=data.getKf();if(Math.abs(kf)<=EPS)kf=minimumTime();tf=correctedBypass(data.getTf());
        kc1=data.getKc1();kp=data.getKp();ki1=data.getKi1();xl=data.getXl();thetaP=data.getThetaP();vb1max=data.getVb1max();
        kc2=data.getKc2();ki2=data.getKi2();vb2max=data.getVb2max();
    }
    private double minimumTime(){return minimumTimeConstantMultiplier*integrationStep;}
    private double correctedBypass(double value){double minimum=minimumTime();
        if(value>0&&value<.5*minimum)return 0;if(value>.5*minimum&&value<minimum)return minimum;return value;}
    private boolean finiteParameters(){double[] p={tr,kpr,kir,vpimax,vpimin,kpa,kia,vamax,vamin,ka,ta,vrmax,vrmin,
            kf,tf,kc1,kp,ki1,xl,thetaP,vb1max,kc2,ki2,vb2max};for(double v:p)if(!Double.isFinite(v))return false;return true;}

    @Override public boolean nextStep(double dt,DynamicSimuMethod method,Machine machine,int flag){
        if(!initialized||dt<0)return false;if(dt==0){outputSignal=algebraics(active,machine).efd;return true;}
        int stage=method==DynamicSimuMethod.MODIFIED_EULER?flag:2;
        if(stage==0){derivatives(state,oldDerivative,machine);add(state,oldDerivative,dt,trial);constrain(trial);active=trial;}
        else if(stage==1){double[] corrected=new double[state.length];derivatives(trial,corrected,machine);
            for(int i=0;i<state.length;i++)state[i]+=.5*(oldDerivative[i]+corrected[i])*dt;constrain(state);active=state;}
        else{double[] derivative=new double[state.length];derivatives(state,derivative,machine);add(state,derivative,dt,state);constrain(state);active=state;}
        outputSignal=algebraics(active,machine).efd;return true;
    }
    private void derivatives(double[] x,double[] derivative,Machine machine){
        Arrays.fill(derivative,0);Algebraic a=algebraics(x,machine);
        derivative[SENSED]=lagDerivative(sensingVoltage(machine),x[SENSED],tr);
        derivative[OUTER_I]=piIntegralDerivative(kir*a.outerInput,a.ifdReferenceRaw,vpimin,vpimax);
        derivative[INNER_I]=piIntegralDerivative(kia*a.innerInput,a.vaRaw,vamin,vamax);
        derivative[BRIDGE]=nonWindup(lagDerivative(ka*a.va,x[BRIDGE],ta),x[BRIDGE],vrmin,vrmax);
        derivative[IFD_FEEDBACK]=lagDerivative(kf*a.ifd,x[IFD_FEEDBACK],tf);
    }
    private Algebraic algebraics(double[] x,Machine machine){
        double sensed=tr>EPS?x[SENSED]:sensingVoltage(machine);
        double outerInput=reference-sensed+stabilizerSignal(machine)+directLimiterInput();
        double ifdReferenceRaw=kpr*outerInput+x[OUTER_I],ifdReference=clamp(ifdReferenceRaw,vpimin,vpimax);
        double ifd=exciterIfd(machine),feedback=tf>EPS?x[IFD_FEEDBACK]:kf*ifd;
        double innerInput=ifdReference-ifd-feedback;
        if(uel==INPUT_GATE&&hasVuel)innerInput=Math.max(innerInput,vuel);
        if(scl==INPUT_GATE&&hasVsclUel)innerInput=Math.max(innerInput,vsclUel);
        if(oel==INPUT_GATE&&hasVoel)innerInput=Math.min(innerInput,voel);
        if(scl==INPUT_GATE&&hasVsclOel)innerInput=Math.min(innerInput,vsclOel);
        double vaRaw=kpa*innerInput+x[INNER_I],va=clamp(vaRaw,vamin,vamax);
        double vr=ta>EPS?clamp(x[BRIDGE],vrmin,vrmax):clamp(ka*va,vrmin,vrmax);
        double vb1=bridge1(machine,ifd),vb2=bridge2(machine,ifd),efd=vr*vb1+vb2;
        return new Algebraic(sensed,outerInput,ifdReferenceRaw,ifdReference,ifd,feedback,innerInput,vaRaw,va,vr,vb1,vb2,efd);
    }

    private double directLimiterInput(){double value=0;
        if(uel<INPUT_GATE&&hasVuel)value+=vuel;if(oel<INPUT_GATE&&hasVoel)value-=voel;
        if(scl<INPUT_GATE&&hasVsclSum)value+=vsclSum;return value;}
    private Complex machineBaseCurrent(Machine machine){return machine.getIxy().divide(machine.getIMultiFactor());}
    private double compoundSource(Machine machine){
        Complex angle=new Complex(Math.cos(Math.toRadians(thetaP)),Math.sin(Math.toRadians(thetaP)));
        Complex kpPhasor=angle.multiply(kp),vt=machine.getDStabBus().getVoltage(),it=machineBaseCurrent(machine);
        return kpPhasor.multiply(vt).add(Complex.I.multiply(new Complex(ki1,0).add(kpPhasor.multiply(xl))).multiply(it)).abs();
    }
    private double selectedSource1(Machine machine){return sw1==SWITCH_A?compoundSource(machine):kp;}
    private double currentSource2(Machine machine){return machineBaseCurrent(machine).multiply(ki2).abs();}
    private static double availableBridge(double source,double ifd,double loading,double maximum,double floor){
        double nonnegative=Math.max(0,source);if(nonnegative<=EPS)return 0;double denominator=Math.max(nonnegative,floor);
        return clamp(nonnegative*Exac1Exciter.rectifierFactor(loading*ifd/denominator),0,maximum);
    }
    private double bridge1(Machine machine,double ifd){return availableBridge(selectedSource1(machine),ifd,kc1,vb1max,EPS);}
    private double bridge2(Machine machine,double ifd){return availableBridge(currentSource2(machine),ifd,kc2,vb2max,.001);}
    private void constrain(double[] x){if(ta>EPS)x[BRIDGE]=clamp(x[BRIDGE],vrmin,vrmax);
        for(int i=0;i<x.length;i++)if(!Double.isFinite(x[i]))x[i]=0;}
    private static double piIntegralDerivative(double rate,double output,double lower,double upper){
        if((output>=upper-EPS&&rate>0)||(output<=lower+EPS&&rate<0))return 0;return rate;}
    private static double nonWindup(double rate,double value,double lower,double upper){
        if((value>=upper-EPS&&rate>0)||(value<=lower+EPS&&rate<0))return 0;return rate;}
    private static double lagDerivative(double input,double stateValue,double time){return time>EPS?(input-stateValue)/time:0;}
    private static void add(double[] x,double[] derivative,double dt,double[] result){for(int i=0;i<x.length;i++)result[i]=x[i]+derivative[i]*dt;}
    private static double clamp(double value,double lower,double upper){return Math.max(lower,Math.min(upper,value));}
    private static int normalizeSelector(int value){return value==0?INPUT_SUMMATION:value;}
    private static boolean validSelector(int value){return value==INPUT_SUMMATION||value==INPUT_GATE;}
    private static double sensingVoltage(Machine machine){if(machine instanceof ICMLMachineVoltageProvider provider){double value=provider.getCmlMachineVoltage();
        if(Double.isFinite(value))return value;}return machine.getDStabBus().getVoltageMag();}
    private static double stabilizerSignal(Machine machine){return machine.getStabilizer()==null?0:machine.getStabilizer().getOutput(machine);}
    private static double exciterIfd(Machine machine){double value=machine.calculateIfd(MachineIfdBase.EXCITER);return Double.isFinite(value)?value:0;}

    public void setVuel(double value){vuel=value;hasVuel=true;}public void setVoel(double value){voel=value;hasVoel=true;}
    public void setVsclSum(double value){vsclSum=value;hasVsclSum=true;}public void setVsclUel(double value){vsclUel=value;hasVsclUel=true;}
    public void setVsclOel(double value){vsclOel=value;hasVsclOel=true;}
    public double getSensedVoltage(){return algebraics(active,getMachine()).sensed;}public double getOuterInput(){return algebraics(active,getMachine()).outerInput;}
    public double getIfdReference(){return algebraics(active,getMachine()).ifdReference;}public double getIfdFeedback(){return algebraics(active,getMachine()).feedback;}
    public double getInnerInput(){return algebraics(active,getMachine()).innerInput;}public double getVaOutput(){return algebraics(active,getMachine()).va;}
    public double getBridgeControl(){return algebraics(active,getMachine()).vr;}public double getCompoundSource(){return compoundSource(getMachine());}
    public double getCurrentSource2(){return currentSource2(getMachine());}public double getBridge1(){return algebraics(active,getMachine()).vb1;}
    public double getBridge2(){return algebraics(active,getMachine()).vb2;}public double[] getStateSnapshot(){return active.clone();}
    /** Published PSS/E ST8C states in model-library order and semantics. */
    @Override public Map<String,Double> getNamedStates(){
        Map<String,Double> states=new LinkedHashMap<>();
        states.put("Sensed VT",active[SENSED]);
        states.put("Regulator integrator",active[OUTER_I]-exciterIfd(getMachine()));
        states.put("Field Current Regulator",active[INNER_I]);
        states.put("Controlled Rectifier Bridge",active[BRIDGE]);
        states.put("Feedback Gain KF",active[IFD_FEEDBACK]);
        return Collections.unmodifiableMap(states);
    }
    @Override public double getOutput(Machine machine){outputSignal=algebraics(active,machine).efd;return outputSignal;}
    @Override public void setRefPoint(double value){reference=value;}@Override public double getRefPoint(){return reference;}
    private record Algebraic(double sensed,double outerInput,double ifdReferenceRaw,double ifdReference,double ifd,
            double feedback,double innerInput,double vaRaw,double va,double vr,double vb1,double vb2,double efd){}
    @Override public AnController getAnController(){return getClass().getAnnotation(AnController.class);}
    @Override public Field getField(String name)throws Exception{return getClass().getField(name);}
    @Override public Object getFieldObject(Field field)throws Exception{return field.get(this);}
}
