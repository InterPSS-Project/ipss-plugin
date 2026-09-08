package org.interpss.dstab.control.exc.psse.st6c;

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

/** Native PSS/E implementation of the IEEE 421.5-2016 ST6C exciter. */
@AnController(input="mach.vt",output="this.outputSignal",refPoint="this.reference",display={})
public final class St6cExciter extends AnnotateExciter implements IntegrationStepAware {
    private static final double EPS=1e-12;
    private static final int VSENSE=0,PID_I=1,PID_D=2,VG=3,VM=4;
    private final St6cData data;
    private final double[] state=new double[5],trial=new double[5],oldDerivative=new double[5];
    private double[] active=state;
    private boolean initialized,hasVoel,hasVuel,hasVsclOel,hasVsclUel;
    private double integrationStep,minimumTimeConstantMultiplier=1,voel,vuel,vsclOel,vsclUel;

    public int oel,uel,scl,sw1;
    public double tr,kpa,kia,kda,tda,vamax,vamin,kff,km,kci,klr,ilr,vrmax,vrmin;
    public double kg,tg,vmmax,vmmin,ta,kp,ki,xl,thetaP,kc,vbmax,reference,outputSignal;

    public St6cExciter(String id,St6cData data,Machine machine){
        super(id,"ST6C","PSS/E");this.data=data;this._data=data;setMachine(machine);
    }
    public St6cData getData(){return data;}
    @Override public void configureIntegrationStep(double step){configureIntegrationStep(step,1);}
    public void configureIntegrationStep(double step,double multiplier){integrationStep=step;minimumTimeConstantMultiplier=multiplier;}
    public void setVoel(double v){voel=v;hasVoel=true;} public void setVuel(double v){vuel=v;hasVuel=true;}
    public void setVsclOel(double v){vsclOel=v;hasVsclOel=true;} public void setVsclUel(double v){vsclUel=v;hasVsclUel=true;}

    @Override public boolean initStates(BaseDStabBus<?,?> bus,Machine machine){
        loadAndCorrectParameters();
        if(tr<0||tda<0||tg<0||ta<0||kpa<=0||Math.abs(kff+km)<=EPS||sw1<1||sw1>2)return false;
        double vt=bus.getVoltageMag(),ifd=exciterIfd(machine),supply=selectedSupply(machine);
        double vb=availableBridge(supply,ifd),efd0=machine.getEfd();
        if(!Double.isFinite(vb)||vb<=EPS||!Double.isFinite(efd0))return false;
        double vm0=efd0/vb,vg0=kg*efd0,vr0=vm0,va0=(vr0+km*vg0)/(kff+km);
        vrmax=Math.max(vrmax,vr0);vrmin=Math.min(vrmin,vr0);
        vmmax=Math.max(vmmax,vm0);vmmin=Math.min(vmmin,vm0);
        vamax=Math.max(vamax,va0);vamin=Math.min(vamin,va0);
        state[VSENSE]=vt;state[PID_I]=va0;state[PID_D]=0;state[VG]=vg0;state[VM]=vm0;
        System.arraycopy(state,0,trial,0,state.length);active=state;
        reference=vt-stabilizerSignal(machine);outputSignal=efd0;initialized=true;return true;
    }
    private void loadAndCorrectParameters(){
        oel=data.getOel();uel=data.getUel();scl=data.getScl();sw1=data.getSw1();
        tr=correctedBypass(data.getTr());kpa=data.getKpa();kia=data.getKia();kda=data.getKda();tda=data.getTda();
        if(kpa==0&&kia==0)kpa=40;
        vamax=Math.max(data.getVamax(),data.getVamin());vamin=Math.min(data.getVamax(),data.getVamin());
        kff=data.getKff();km=data.getKm();kci=data.getKci();klr=data.getKlr();ilr=data.getIlr();
        vrmax=Math.max(data.getVrmax(),data.getVrmin());vrmin=Math.min(data.getVrmax(),data.getVrmin());
        kg=data.getKg();tg=correctedBypass(data.getTg());vmmax=Math.max(data.getVmmax(),data.getVmmin());
        vmmin=Math.min(data.getVmmax(),data.getVmmin());ta=correctedBypass(data.getTa());kp=data.getKp();
        ki=data.getKi();xl=data.getXl();thetaP=data.getThetaP();kc=data.getKc();vbmax=data.getVbmax();
    }
    private double correctedBypass(double v){double min=minimumTimeConstantMultiplier*integrationStep;if(v>0&&v<.5*min)return 0;if(v>=.5*min&&v<min)return min;return v;}

    @Override public boolean nextStep(double dt,DynamicSimuMethod method,Machine machine,int flag){
        if(!initialized||dt<0)return false;if(dt==0)return true;int stage=method==DynamicSimuMethod.MODIFIED_EULER?flag:2;
        if(stage==0){derivatives(state,oldDerivative,machine);add(state,oldDerivative,dt,trial);constrain(trial);active=trial;}
        else if(stage==1){double[] d=new double[5];derivatives(trial,d,machine);for(int i=0;i<5;i++)state[i]+=.5*(oldDerivative[i]+d[i])*dt;constrain(state);active=state;}
        else{double[] d=new double[5];derivatives(state,d,machine);add(state,d,dt,state);constrain(state);active=state;}
        outputSignal=algebraics(active,machine).efd;return true;
    }
    private void derivatives(double[] x,double[] dx,Machine machine){
        Arrays.fill(dx,0);Algebraic a=algebraics(x,machine);double vt=machine.getDStabBus().getVoltageMag();
        dx[VSENSE]=lagDerivative(vt,x[VSENSE],tr);dx[PID_D]=lagDerivative(a.vi,x[PID_D],tda);
        double irate=kia*a.vi;if((a.va>=a.vaUpper-EPS&&irate>0)||(a.va<=vamin+EPS&&irate<0))irate=0;dx[PID_I]=irate;
        dx[VG]=lagDerivative(kg*a.efd,x[VG],tg);
        double vmRate=lagDerivative(a.vmInput,x[VM],ta);if((x[VM]>=vmmax-EPS&&vmRate>0)||(x[VM]<=vmmin+EPS&&vmRate<0))vmRate=0;dx[VM]=vmRate;
    }
    private Algebraic algebraics(double[] x,Machine machine){
        double sensed=tr>EPS?x[VSENSE]:machine.getDStabBus().getVoltageMag();
        double first=reference-sensed+sumAtOne(),gate1=lvGate1(hvGate1(first));
        double vi=gate1+stabilizerSignal(machine)+sumAtThree();
        double derivative=tda>EPS?kda*(vi-x[PID_D])/tda:0,ifd=exciterIfd(machine);
        double currentLimit=Math.max(vrmin,klr*(kci*ilr-ifd));
        double vaUpper=Math.min(vamax,currentLimit),va=clamp(kpa*vi+x[PID_I]+derivative,vamin,vaUpper);
        double vg=tg>EPS?x[VG]:kg*x[VM]*availableBridge(selectedSupply(machine),ifd);
        double inner=0,after=0,vmInput=0,vm=ta>EPS?x[VM]:0,efd=0;
        for(int i=0;i<32;i++){
            inner=clamp(kff*va+km*(va-vg),vrmin,vrmax);after=lvGate2(hvGate2(inner));
            vmInput=Math.min(after,currentLimit);vm=ta>EPS?x[VM]:clamp(vmInput,vmmin,vmmax);
            efd=vm*availableBridge(selectedSupply(machine),ifd);if(tg>EPS)break;
            double next=kg*efd;if(Math.abs(next-vg)<1e-12){vg=next;break;}vg=next;
        }
        return new Algebraic(sensed,vi,va,vaUpper,vg,inner,currentLimit,vmInput,vm,efd);
    }
    private double sumAtOne(){double v=0;if(oel<2&&hasVoel)v+=voel;if(uel<2&&hasVuel)v+=vuel;if(scl<2){if(hasVsclOel)v+=vsclOel;if(hasVsclUel)v+=vsclUel;}return v;}
    private double sumAtThree(){double v=0;if(oel==3&&hasVoel)v+=voel;if(uel==3&&hasVuel)v+=vuel;if(scl==3){if(hasVsclOel)v+=vsclOel;if(hasVsclUel)v+=vsclUel;}return v;}
    private double hvGate1(double v){if(uel==2&&hasVuel)v=Math.max(v,vuel);if(scl==2&&hasVsclUel)v=Math.max(v,vsclUel);return v;}
    private double lvGate1(double v){if(oel==2&&hasVoel)v=Math.min(v,voel);if(scl==2&&hasVsclOel)v=Math.min(v,vsclOel);return v;}
    private double hvGate2(double v){if(uel==4&&hasVuel)v=Math.max(v,vuel);if(scl==4&&hasVsclUel)v=Math.max(v,vsclUel);return v;}
    private double lvGate2(double v){if(oel==4&&hasVoel)v=Math.min(v,voel);if(scl==4&&hasVsclOel)v=Math.min(v,vsclOel);return v;}
    private double selectedSupply(Machine machine){return sw1==1?potentialSource(machine):kp;}
    private double potentialSource(Machine machine){
        Complex angle=new Complex(Math.cos(Math.toRadians(thetaP)),Math.sin(Math.toRadians(thetaP)));
        Complex kpPhasor=angle.multiply(kp),vt=machine.getDStabBus().getVoltage();
        Complex it=machine.getIxy().divide(machine.getIMultiFactor());
        return kpPhasor.multiply(vt).add(Complex.I.multiply(new Complex(ki,0).add(kpPhasor.multiply(xl))).multiply(it)).abs();
    }
    private double availableBridge(double supply,double ifd){if(supply<=EPS)return 0;return Math.min(vbmax,supply*Exac1Exciter.rectifierFactor(kc*ifd/supply));}
    private void constrain(double[] x){x[VM]=clamp(x[VM],vmmin,vmmax);for(int i=0;i<x.length;i++)if(!Double.isFinite(x[i]))x[i]=0;}
    private static double lagDerivative(double in,double x,double t){return t>EPS?(in-x)/t:0;}
    private static void add(double[] x,double[] d,double dt,double[] y){for(int i=0;i<x.length;i++)y[i]=x[i]+d[i]*dt;}
    private static double clamp(double v,double lo,double hi){return Math.max(lo,Math.min(hi,v));}
    private static double stabilizerSignal(Machine m){return m.getStabilizer()==null?0:m.getStabilizer().getOutput(m);}
    private static double exciterIfd(Machine m){double v=m.calculateIfd(MachineIfdBase.EXCITER);return Double.isFinite(v)?v:0;}
    public double getSensedVoltage(){return algebraics(active,getMachine()).sensed;} public double getRegulatorInput(){return algebraics(active,getMachine()).vi;}
    public double getVaOutput(){return algebraics(active,getMachine()).va;} public double getVgOutput(){return algebraics(active,getMachine()).vg;}
    public double getInnerRegulatorOutput(){return algebraics(active,getMachine()).inner;} public double getCurrentLimitOutput(){return algebraics(active,getMachine()).currentLimit;}
    public double getVmInput(){return algebraics(active,getMachine()).vmInput;} public double getVmOutput(){return algebraics(active,getMachine()).vm;}
    public double getPotentialSource(){return potentialSource(getMachine());} public double getAvailableBridge(){return availableBridge(selectedSupply(getMachine()),exciterIfd(getMachine()));}
    @Override public double getOutput(Machine machine){outputSignal=algebraics(active,machine).efd;return outputSignal;}
    @Override public void setRefPoint(double v){reference=v;} @Override public double getRefPoint(){return reference;}
    private record Algebraic(double sensed,double vi,double va,double vaUpper,double vg,double inner,double currentLimit,double vmInput,double vm,double efd){}
    @Override public AnController getAnController(){return getClass().getAnnotation(AnController.class);}
    @Override public Field getField(String n)throws Exception{return getClass().getField(n);}
    @Override public Object getFieldObject(Field f)throws Exception{return f.get(this);}
}
