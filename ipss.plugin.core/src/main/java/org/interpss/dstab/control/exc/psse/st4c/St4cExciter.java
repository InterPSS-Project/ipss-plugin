package org.interpss.dstab.control.exc.psse.st4c;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import java.util.function.DoubleUnaryOperator;

import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.control.exc.psse.exac1.Exac1Exciter;
import org.interpss.dstab.control.util.IntegrationStepAware;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.ICMLMachineVoltageProvider;
import com.interpss.dstab.controller.cml.ICMLStateProvider;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnnotateExciter;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.MachineIfdBase;

/** Native PSS/E implementation of the IEEE 421.5-2016 ST4C exciter. */
@AnController(input="mach.vt",output="this.outputSignal",refPoint="this.reference",display={})
public final class St4cExciter extends AnnotateExciter implements IntegrationStepAware, ICMLStateProvider {
    private static final double EPS=1e-12;
    private static final int SENSED=0,VR_I=1,VM_I=2,VG=3,VA=4;
    private final St4cData data;
    private final double[] state=new double[5],trial=new double[5],oldDerivative=new double[5];
    private double[] active=state;
    private boolean initialized,hasVoel,hasVuel,hasVsclOel,hasVsclUel;
    private double integrationStep,minimumTimeConstantMultiplier=1,voel,vuel,vsclOel,vsclUel;

    public int vos,oel,uel,scl,sw1;
    public double tr,kpr,kir,vrmax,vrmin,kpm,kim,vmmax,vmmin,ta,vamax,vamin;
    public double kg,tg,vgmax,kp,ki,xl,thetaP,kc,vbmax,reference,outputSignal;

    public St4cExciter(String id,St4cData data,Machine machine){
        super(id,"ST4C","PSS/E");this.data=data;this._data=data;setMachine(machine);
    }
    public St4cData getData(){return data;}
    @Override public void configureIntegrationStep(double step){configureIntegrationStep(step,1);}
    public void configureIntegrationStep(double step,double multiplier){integrationStep=step;minimumTimeConstantMultiplier=multiplier;}
    public void setVoel(double v){voel=v;hasVoel=true;} public void clearVoel(){hasVoel=false;}
    public void setVuel(double v){vuel=v;hasVuel=true;} public void clearVuel(){hasVuel=false;}
    public void setVsclOel(double v){vsclOel=v;hasVsclOel=true;} public void clearVsclOel(){hasVsclOel=false;}
    public void setVsclUel(double v){vsclUel=v;hasVsclUel=true;} public void clearVsclUel(){hasVsclUel=false;}

    @Override public boolean initStates(BaseDStabBus<?,?> bus,Machine machine){
        loadAndCorrectParameters();
        if(vos<1||vos>2||oel<1||oel>3||uel<1||uel>3||scl<1||scl>3||sw1<1||sw1>2
                ||tr<0||ta<0||tg<0||!finiteParameters())return false;
        double sensed=sensingVoltage(machine),ifd=exciterIfd(machine),supply=selectedSupply(machine);
        double vb=availableBridge(supply,ifd),efd0=machine.getEfd();
        if(!Double.isFinite(sensed)||sensed<=EPS||!Double.isFinite(vb)||vb<=EPS||!Double.isFinite(efd0))return false;
        double va0=efd0/vb,vg0=Math.min(vgmax,kg*efd0);
        double innerInput0=Math.abs(kim)>EPS?0:va0/kpm;
        double vr0=vg0+innerInput0;
        double outerInput0=Math.abs(kir)>EPS?0:vr0/kpr;
        vrmax=Math.max(vrmax,vr0);vrmin=Math.min(vrmin,vr0);
        vmmax=Math.max(vmmax,va0);vmmin=Math.min(vmmin,va0);
        vamax=Math.max(vamax,va0);vamin=Math.min(vamin,va0);
        state[SENSED]=sensed;state[VR_I]=vr0-kpr*outerInput0;
        state[VM_I]=va0-kpm*innerInput0;state[VG]=vg0;state[VA]=va0;
        reference=sensed+outerInput0-stabilizerSignal(machine);
        System.arraycopy(state,0,trial,0,state.length);active=state;
        outputSignal=efd0;initialized=true;return true;
    }

    private void loadAndCorrectParameters(){
        vos=data.getVos();oel=data.getOel();uel=data.getUel();scl=data.getScl();sw1=data.getSw1();
        tr=correctedBypass(data.getTr());kpr=data.getKpr();kir=data.getKir();
        if(Math.abs(kpr)<=EPS&&Math.abs(kir)<=EPS)kpr=40;
        vrmax=Math.max(data.getVrmax(),data.getVrmin());vrmin=Math.min(data.getVrmax(),data.getVrmin());
        kpm=data.getKpm();kim=data.getKim();if(Math.abs(kpm)<=EPS&&Math.abs(kim)<=EPS)kpm=1;
        vmmax=Math.max(data.getVmmax(),data.getVmmin());vmmin=Math.min(data.getVmmax(),data.getVmmin());
        ta=correctedBypass(data.getTa());vamax=Math.max(data.getVamax(),data.getVamin());vamin=Math.min(data.getVamax(),data.getVamin());
        kg=data.getKg();tg=correctedBypass(data.getTg());vgmax=data.getVgmax();kp=data.getKp();ki=data.getKi();
        xl=data.getXl();thetaP=data.getThetaP();kc=data.getKc();vbmax=data.getVbmax();
    }
    private boolean finiteParameters(){
        double[] p={kpr,kir,vrmax,vrmin,kpm,kim,vmmax,vmmin,vamax,vamin,kg,vgmax,kp,ki,xl,thetaP,kc,vbmax};
        for(double v:p)if(!Double.isFinite(v))return false;
        return true;
    }
    private double correctedBypass(double value){double min=minimumTimeConstantMultiplier*integrationStep;
        if(value>0&&value<.5*min)return 0;if(value>.5*min&&value<min)return min;return value;}

    @Override public boolean nextStep(double dt,DynamicSimuMethod method,Machine machine,int flag){
        if(!initialized||dt<0)return false;if(dt==0)return true;int stage=method==DynamicSimuMethod.MODIFIED_EULER?flag:2;
        if(stage==0){derivatives(state,oldDerivative,machine);add(state,oldDerivative,dt,trial);constrain(trial);active=trial;}
        else if(stage==1){double[] d=new double[5];derivatives(trial,d,machine);for(int i=0;i<5;i++)state[i]+=.5*(oldDerivative[i]+d[i])*dt;constrain(state);active=state;}
        else{double[] d=new double[5];derivatives(state,d,machine);add(state,d,dt,state);constrain(state);active=state;}
        outputSignal=algebraics(active,machine).efd;return true;
    }
    private void derivatives(double[] x,double[] dx,Machine machine){
        Arrays.fill(dx,0);Algebraic a=algebraics(x,machine);
        dx[SENSED]=lagDerivative(sensingVoltage(machine),x[SENSED],tr);
        dx[VR_I]=nonWindup(kir*a.outerInput,a.vrRaw,vrmin,vrmax);
        dx[VM_I]=nonWindup(kim*a.innerInput,a.vmRaw,vmmin,vmmax);
        double vgRate=lagDerivative(kg*a.efd,x[VG],tg);if(x[VG]>=vgmax-EPS&&vgRate>0)vgRate=0;dx[VG]=vgRate;
        dx[VA]=nonWindup(lagDerivative(a.vaInput,x[VA],ta),x[VA],vamin,vamax);
    }

    private Algebraic algebraics(double[] x,Machine machine){
        double feedback=tg>EPS?Math.min(vgmax,x[VG]):solveAlgebraicFeedback(x,machine);
        return algebraicsForFeedback(x,machine,feedback);
    }
    private Algebraic algebraicsForFeedback(double[] x,Machine machine,double feedback){
        double sensed=tr>EPS?x[SENSED]:sensingVoltage(machine),pss=stabilizerSignal(machine);
        double first=reference-sensed+(vos==1?pss:0)+sumAtOne();
        first=firstHighGate(first);if(vos==2)first+=pss;first=firstLowGate(first);
        double vrRaw=kpr*first+x[VR_I],vr=clamp(vrRaw,vrmin,vrmax),innerInput=vr-feedback;
        double vmRaw=kpm*innerInput+x[VM_I],vm=clamp(vmRaw,vmmin,vmmax);
        double vaInput=secondLowGate(secondHighGate(vm));
        double va=ta>EPS?clamp(x[VA],vamin,vamax):clamp(vaInput,vamin,vamax);
        double ifd=exciterIfd(machine),supply=selectedSupply(machine),vb=availableBridge(supply,ifd),efd=va*vb;
        return new Algebraic(sensed,first,vrRaw,vr,feedback,innerInput,vmRaw,vm,vaInput,va,supply,vb,efd);
    }
    private double solveAlgebraicFeedback(double[] x,Machine machine){
        DoubleUnaryOperator residual=g->g-Math.min(vgmax,kg*algebraicsForFeedback(x,machine,g).efd);
        double lo=-1,hi=Math.max(1,vgmax),flo=residual.applyAsDouble(lo),fhi=residual.applyAsDouble(hi);
        for(int i=0;i<60&&Math.signum(flo)==Math.signum(fhi);i++){
            lo=lo*2-1;hi=hi*2+1;flo=residual.applyAsDouble(lo);fhi=residual.applyAsDouble(hi);
        }
        if(Math.signum(flo)==Math.signum(fhi)){
            double g=0;for(int i=0;i<100;i++){double next=Math.min(vgmax,kg*algebraicsForFeedback(x,machine,g).efd);
                if(Math.abs(next-g)<1e-12)return next;g=.5*(g+next);}return g;
        }
        for(int i=0;i<100;i++){double mid=.5*(lo+hi),fm=residual.applyAsDouble(mid);
            if(Math.abs(fm)<1e-13)return mid;if(Math.signum(fm)==Math.signum(flo)){lo=mid;flo=fm;}else hi=mid;}
        return .5*(lo+hi);
    }

    private double sumAtOne(){double v=0;if(oel<2&&hasVoel)v+=voel;if(uel<2&&hasVuel)v+=vuel;
        if(scl<2){if(hasVsclOel)v+=vsclOel;if(hasVsclUel)v+=vsclUel;}return v;}
    private double firstHighGate(double v){if(uel==2&&hasVuel)v=Math.max(v,vuel);if(scl==2&&hasVsclUel)v=Math.max(v,vsclUel);return v;}
    private double firstLowGate(double v){if(oel==2&&hasVoel)v=Math.min(v,voel);if(scl==2&&hasVsclOel)v=Math.min(v,vsclOel);return v;}
    private double secondHighGate(double v){if(uel==3&&hasVuel)v=Math.max(v,vuel);if(scl==3&&hasVsclUel)v=Math.max(v,vsclUel);return v;}
    private double secondLowGate(double v){if(oel==3&&hasVoel)v=Math.min(v,voel);if(scl==3&&hasVsclOel)v=Math.min(v,vsclOel);return v;}
    private double selectedSupply(Machine machine){return sw1==1?potentialSource(machine):kp;}
    private double potentialSource(Machine machine){
        Complex angle=new Complex(Math.cos(Math.toRadians(thetaP)),Math.sin(Math.toRadians(thetaP)));
        Complex kpPhasor=angle.multiply(kp),vt=machine.getDStabBus().getVoltage();
        Complex it=machine.getIxy().divide(machine.getIMultiFactor());
        return kpPhasor.multiply(vt).add(Complex.I.multiply(new Complex(ki,0).add(kpPhasor.multiply(xl))).multiply(it)).abs();
    }
    private double availableBridge(double supply,double ifd){if(supply<=EPS)return 0;
        return Math.min(vbmax,supply*Exac1Exciter.rectifierFactor(kc*ifd/supply));}
    private void constrain(double[] x){x[VA]=clamp(x[VA],vamin,vamax);x[VG]=Math.min(x[VG],vgmax);
        for(int i=0;i<x.length;i++)if(!Double.isFinite(x[i]))x[i]=0;}
    private static double lagDerivative(double in,double x,double t){return t>EPS?(in-x)/t:0;}
    private static double nonWindup(double rate,double value,double lo,double hi){
        if((value>=hi-EPS&&rate>0)||(value<=lo+EPS&&rate<0))return 0;return rate;}
    private static void add(double[] x,double[] d,double dt,double[] y){for(int i=0;i<x.length;i++)y[i]=x[i]+d[i]*dt;}
    private static double clamp(double v,double lo,double hi){return Math.max(lo,Math.min(hi,v));}
    private static double sensingVoltage(Machine machine){if(machine instanceof ICMLMachineVoltageProvider p){double v=p.getCmlMachineVoltage();
        if(Double.isFinite(v))return v;}return machine.getDStabBus().getVoltageMag();}
    private static double stabilizerSignal(Machine m){return m.getStabilizer()==null?0:m.getStabilizer().getOutput(m);}
    private static double exciterIfd(Machine m){double v=m.calculateIfd(MachineIfdBase.EXCITER);return Double.isFinite(v)?v:0;}

    public double getSensedVoltage(){return algebraics(active,getMachine()).sensed;}
    public double getOuterInput(){return algebraics(active,getMachine()).outerInput;}
    public double getVrOutput(){return algebraics(active,getMachine()).vr;}
    public double getVgOutput(){return algebraics(active,getMachine()).feedback;}
    public double getInnerInput(){return algebraics(active,getMachine()).innerInput;}
    public double getVmOutput(){return algebraics(active,getMachine()).vm;}
    public double getVmIntegratorState(){return active[VM_I];}
    public double getVaInput(){return algebraics(active,getMachine()).vaInput;}
    public double getVaOutput(){return algebraics(active,getMachine()).va;}
    public double getPotentialSource(){return potentialSource(getMachine());}
    public double getAvailableBridge(){return algebraics(active,getMachine()).vb;}
    public double[] getStateSnapshot(){return active.clone();}
    @Override public Map<String,Double> getNamedStates(){return Map.of(
            "VM",getVmIntegratorState(),"Sensed Vt",getSensedVoltage(),"VA",getVaOutput(),
            "VR",getVrOutput(),"VG",getVgOutput());}
    @Override public double getOutput(Machine machine){outputSignal=algebraics(active,machine).efd;return outputSignal;}
    @Override public void setRefPoint(double v){reference=v;} @Override public double getRefPoint(){return reference;}
    @Override public AnController getAnController(){return getClass().getAnnotation(AnController.class);}
    @Override public Field getField(String name)throws Exception{return getClass().getField(name);}
    @Override public Object getFieldObject(Field field)throws Exception{return field.get(this);}
    private record Algebraic(double sensed,double outerInput,double vrRaw,double vr,double feedback,double innerInput,
            double vmRaw,double vm,double vaInput,double va,double supply,double vb,double efd){}
}
