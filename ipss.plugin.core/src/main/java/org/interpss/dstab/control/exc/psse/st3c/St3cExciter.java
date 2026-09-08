package org.interpss.dstab.control.exc.psse.st3c;

import java.lang.reflect.Field;
import java.util.Arrays;

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

/** Native PSS/E implementation of the IEEE 421.5-2016 ST3C exciter. */
@AnController(input="mach.vt",output="this.outputSignal",refPoint="this.reference",display={})
public final class St3cExciter extends AnnotateExciter implements IntegrationStepAware{
    private static final double EPS=1e-12;
    private static final int SENSED=0,PID_I=1,DERIV_LAG=2,LEAD_LAG=3,VR=4,VM=5;
    private final St3cData data;private final double[] state=new double[6],trial=new double[6],oldDerivative=new double[6];
    private double[] active=state;private boolean initialized,hasVuel,hasVoel;
    private double integrationStep,minimumTimeConstantMultiplier=1,vuel,voel;
    public int oel,uel,scl,sw1;
    public double tr,vimax,vimin,kpr,kir,kdr,tdr,vpidmax,vpidmin,tc,tb,ka,ta,vrmax,vrmin;
    public double km,tm,vmmax,vmmin,kg,vgmax,kp,ki,xl,thetaP,kc,vbmax,reference,outputSignal;

    public St3cExciter(String id,St3cData data,Machine machine){super(id,"ST3C","PSS/E");this.data=data;this._data=data;setMachine(machine);}
    public St3cData getData(){return data;}public void setVuel(double v){vuel=v;hasVuel=true;}public void clearVuel(){hasVuel=false;}
    public void setVoel(double v){voel=v;hasVoel=true;}public void clearVoel(){hasVoel=false;}
    @Override public void configureIntegrationStep(double step){configureIntegrationStep(step,1);}
    public void configureIntegrationStep(double step,double multiplier){integrationStep=step;minimumTimeConstantMultiplier=multiplier;}

    @Override public boolean initStates(BaseDStabBus<?,?> bus,Machine machine){
        loadAndCorrectParameters();if(tr<0||tdr<0||tb<0||ta<0||tm<0||ka<=EPS||km<=EPS||vbmax<0||vimax<vimin||!finiteParameters())return false;
        double sensed=sensingVoltage(machine),efd0=machine.getEfd(),bridge=availableBridge(machine);
        if(!Double.isFinite(sensed)||sensed<=EPS||!Double.isFinite(efd0)||!Double.isFinite(bridge)||bridge<=EPS)return false;
        double vm0=efd0/bridge,feedback=Math.min(vgmax,kg*efd0),vr0=feedback+vm0/km,pid0=vr0/ka;
        double error0=Math.abs(kir)>EPS?0:clamp(pid0/kpr,vimin,vimax);if(Math.abs(kir)>EPS&&clamp(0,vimin,vimax)!=0)return false;
        vpidmax=Math.max(vpidmax,pid0);vpidmin=Math.min(vpidmin,pid0);vrmax=Math.max(vrmax,vr0);vrmin=Math.min(vrmin,vr0);
        vmmax=Math.max(vmmax,vm0);vmmin=Math.min(vmmin,vm0);
        state[SENSED]=sensed;state[PID_I]=pid0-kpr*error0;state[DERIV_LAG]=error0;state[LEAD_LAG]=pid0;state[VR]=vr0;state[VM]=vm0;
        System.arraycopy(state,0,trial,0,state.length);active=state;
        reference=sensed+error0-stabilizerSignal(machine)-directUel()+directOel();outputSignal=efd0;initialized=true;return true;
    }
    private void loadAndCorrectParameters(){
        oel=normalizedLimiter(data.getOel());uel=normalizedLimiter(data.getUel());scl=data.getScl();sw1=data.getSw1()==1?1:2;
        tr=correctedBypass(data.getTr());vimax=data.getVimax();vimin=data.getVimin();kpr=data.getKpr();kir=data.getKir();
        if(Math.abs(kpr)<=EPS&&Math.abs(kir)<=EPS)kpr=40;kdr=data.getKdr();tdr=data.getTdr();
        vpidmax=Math.max(data.getVpidmax(),data.getVpidmin());vpidmin=Math.min(data.getVpidmax(),data.getVpidmin());
        tc=data.getTc();tb=correctedBypass(data.getTb());ka=Math.abs(data.getKa())<=EPS?minimumTimeConstantMultiplier*integrationStep:data.getKa();
        ta=correctedBypass(data.getTa());vrmax=Math.max(data.getVrmax(),data.getVrmin());vrmin=Math.min(data.getVrmax(),data.getVrmin());
        km=Math.abs(data.getKm())<=EPS?minimumTimeConstantMultiplier*integrationStep:data.getKm();tm=correctedBypass(data.getTm());
        vmmax=Math.max(data.getVmmax(),data.getVmmin());vmmin=Math.min(data.getVmmax(),data.getVmmin());kg=data.getKg();vgmax=data.getVgmax();
        kp=data.getKp();ki=data.getKi();xl=data.getXl();thetaP=data.getThetaP();kc=data.getKc();vbmax=data.getVbmax();
    }
    private boolean finiteParameters(){double[] p={tr,vimax,vimin,kpr,kir,kdr,tdr,vpidmax,vpidmin,tc,tb,ka,ta,vrmax,vrmin,km,tm,vmmax,vmmin,kg,vgmax,kp,ki,xl,thetaP,kc,vbmax};
        for(double v:p)if(!Double.isFinite(v))return false;return true;}
    private double correctedBypass(double v){double min=minimumTimeConstantMultiplier*integrationStep;if(v>0&&v<.5*min)return 0;if(v>.5*min&&v<min)return min;return v;}

    @Override public boolean nextStep(double dt,DynamicSimuMethod method,Machine machine,int flag){
        if(!initialized||dt<0)return false;if(dt==0)return true;int stage=method==DynamicSimuMethod.MODIFIED_EULER?flag:2;
        if(stage==0){derivatives(state,oldDerivative,machine);add(state,oldDerivative,dt,trial);constrain(trial);active=trial;}
        else if(stage==1){double[] d=new double[6];derivatives(trial,d,machine);for(int i=0;i<6;i++)state[i]+=.5*(oldDerivative[i]+d[i])*dt;constrain(state);active=state;}
        else{double[] d=new double[6];derivatives(state,d,machine);add(state,d,dt,state);constrain(state);active=state;}
        outputSignal=algebraics(active,machine).efd;return true;
    }
    private void derivatives(double[] x,double[] dx,Machine machine){Arrays.fill(dx,0);Algebraic a=algebraics(x,machine);
        dx[SENSED]=lagDerivative(sensingVoltage(machine),x[SENSED],tr);dx[DERIV_LAG]=lagDerivative(a.error,x[DERIV_LAG],tdr);
        dx[PID_I]=nonWindup(kir*a.error,a.pidRaw,vpidmin,vpidmax);dx[LEAD_LAG]=lagDerivative(a.pid,x[LEAD_LAG],tb);
        dx[VR]=nonWindup(lagDerivative(ka*a.leadLag,x[VR],ta),x[VR],vrmin,vrmax);
        dx[VM]=nonWindup(lagDerivative(km*(a.vr-a.feedback),x[VM],tm),x[VM],vmmin,vmmax);
    }
    private Algebraic algebraics(double[] x,Machine machine){double vr=ta>EPS?clamp(x[VR],vrmin,vrmax):algebraicVr(x,machine);
        double efd=tm>EPS?clamp(x[VM],vmmin,vmmax)*availableBridge(machine):solveAlgebraicField(x,machine,vr);
        return algebraicsForField(x,machine,vr,efd);}
    private Algebraic algebraicsForField(double[] x,Machine machine,double vr,double efd){
        double sensed=tr>EPS?x[SENSED]:sensingVoltage(machine),rawError=reference-sensed+stabilizerSignal(machine)+directUel()-directOel();
        double limitedError=clamp(rawError,vimin,vimax),gatedError=limitedError;if(uel==2&&hasVuel)gatedError=Math.max(gatedError,vuel);
        if(oel==2&&hasVoel)gatedError=Math.min(gatedError,voel);double derivative=tdr>EPS?kdr*(gatedError-x[DERIV_LAG])/tdr:0;
        double pidRaw=kpr*gatedError+x[PID_I]+derivative,pid=clamp(pidRaw,vpidmin,vpidmax);
        double leadLag=tb>EPS?(tc/tb)*pid+(1-tc/tb)*x[LEAD_LAG]:pid;
        double feedback=Math.min(vgmax,kg*efd),vm=tm>EPS?clamp(x[VM],vmmin,vmmax):clamp(km*(vr-feedback),vmmin,vmmax);
        double bridge=availableBridge(machine);return new Algebraic(sensed,rawError,gatedError,derivative,pidRaw,pid,leadLag,vr,feedback,vm,bridge,efd);}
    private double algebraicVr(double[] x,Machine machine){
        double sensed=tr>EPS?x[SENSED]:sensingVoltage(machine),error=clamp(reference-sensed+stabilizerSignal(machine)+directUel()-directOel(),vimin,vimax);
        if(uel==2&&hasVuel)error=Math.max(error,vuel);if(oel==2&&hasVoel)error=Math.min(error,voel);
        double derivative=tdr>EPS?kdr*(error-x[DERIV_LAG])/tdr:0,pid=clamp(kpr*error+x[PID_I]+derivative,vpidmin,vpidmax);
        double lead=tb>EPS?(tc/tb)*pid+(1-tc/tb)*x[LEAD_LAG]:pid;return clamp(ka*lead,vrmin,vrmax);}
    private double solveAlgebraicField(double[] x,Machine machine,double vr){double bridge=availableBridge(machine);
        double a=bridge*vmmin,b=bridge*vmmax,lo=Math.min(a,b),hi=Math.max(a,b),flo=fieldResidual(lo,x,machine,vr),fhi=fieldResidual(hi,x,machine,vr);
        if(flo>=0)return lo;if(fhi<=0)return hi;for(int i=0;i<100;i++){double mid=.5*(lo+hi),fm=fieldResidual(mid,x,machine,vr);
            if(Math.abs(fm)<1e-13)return mid;if(fm<0)lo=mid;else hi=mid;}return .5*(lo+hi);}
    private double fieldResidual(double efd,double[] x,Machine machine,double vr){
        return efd-algebraicsForField(x,machine,vr,efd).vm*availableBridge(machine);}
    private double compoundSource(Machine machine){Complex angle=new Complex(Math.cos(Math.toRadians(thetaP)),Math.sin(Math.toRadians(thetaP)));
        Complex kpPhasor=angle.multiply(kp),vt=machine.getDStabBus().getVoltage(),it=machine.getIxy().divide(machine.getIMultiFactor());
        return kpPhasor.multiply(vt).add(Complex.I.multiply(new Complex(ki,0).add(kpPhasor.multiply(xl))).multiply(it)).abs();}
    private double availableBridge(Machine machine){if(Math.abs(kp)<=EPS&&Math.abs(ki)<=EPS)return Math.min(1,vbmax);
        double source=sw1==1?compoundSource(machine):kp;if(source<=EPS)return 0;double ifd=exciterIfd(machine);
        return clamp(source*Exac1Exciter.rectifierFactor(kc*ifd/source),0,vbmax);}
    private void constrain(double[] x){if(ta>EPS)x[VR]=clamp(x[VR],vrmin,vrmax);if(tm>EPS)x[VM]=clamp(x[VM],vmmin,vmmax);
        for(int i=0;i<x.length;i++)if(!Double.isFinite(x[i]))x[i]=0;}
    private double directUel(){return uel==1&&hasVuel?vuel:0;}private double directOel(){return oel==1&&hasVoel?voel:0;}
    private static int normalizedLimiter(int v){return v==2?2:1;}private static double lagDerivative(double in,double x,double t){return t>EPS?(in-x)/t:0;}
    private static double nonWindup(double rate,double value,double lo,double hi){if((value>=hi-EPS&&rate>0)||(value<=lo+EPS&&rate<0))return 0;return rate;}
    private static void add(double[] x,double[] d,double dt,double[] y){for(int i=0;i<x.length;i++)y[i]=x[i]+d[i]*dt;}
    private static double clamp(double v,double lo,double hi){return Math.max(lo,Math.min(hi,v));}
    private static double sensingVoltage(Machine machine){if(machine instanceof ICMLMachineVoltageProvider p){double v=p.getCmlMachineVoltage();if(Double.isFinite(v))return v;}
        return machine.getDStabBus().getVoltageMag();}private static double stabilizerSignal(Machine m){return m.getStabilizer()==null?0:m.getStabilizer().getOutput(m);}
    private static double exciterIfd(Machine m){double v=m.calculateIfd(MachineIfdBase.EXCITER);return Double.isFinite(v)?v:0;}
    public double getSensedVoltage(){return algebraics(active,getMachine()).sensed;}public double getRawError(){return algebraics(active,getMachine()).rawError;}
    public double getGatedError(){return algebraics(active,getMachine()).error;}public double getPidOutput(){return algebraics(active,getMachine()).pid;}
    public double getLeadLagOutput(){return algebraics(active,getMachine()).leadLag;}public double getRegulatorOutput(){return algebraics(active,getMachine()).vr;}
    public double getFeedback(){return algebraics(active,getMachine()).feedback;}public double getVmOutput(){return algebraics(active,getMachine()).vm;}
    public double getCompoundSource(){return compoundSource(getMachine());}public double getAvailableBridge(){return availableBridge(getMachine());}
    public double[] getStateSnapshot(){return active.clone();}@Override public double getOutput(Machine machine){outputSignal=algebraics(active,machine).efd;return outputSignal;}
    @Override public void setRefPoint(double v){reference=v;}@Override public double getRefPoint(){return reference;}
    @Override public AnController getAnController(){return getClass().getAnnotation(AnController.class);}@Override public Field getField(String n)throws Exception{return getClass().getField(n);}
    @Override public Object getFieldObject(Field f)throws Exception{return f.get(this);}
    private record Algebraic(double sensed,double rawError,double error,double derivative,double pidRaw,double pid,double leadLag,
            double vr,double feedback,double vm,double bridge,double efd){}
}
