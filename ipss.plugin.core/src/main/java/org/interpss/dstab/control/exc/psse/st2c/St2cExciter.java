package org.interpss.dstab.control.exc.psse.st2c;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;

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

/** Native PSS/E implementation of the IEEE 421.5-2016 ST2C exciter. */
@AnController(input="mach.vt",output="this.outputSignal",refPoint="this.reference",display={})
public final class St2cExciter extends AnnotateExciter implements IntegrationStepAware, ICMLStateProvider {
    private static final double EPS=1e-12;
    private static final int SENSED=0,PID_I=1,DERIV_LAG=2,VR=3,EFD=4,FB_LAG=5;
    private final St2cData data;
    private final double[] state=new double[6],trial=new double[6],oldDerivative=new double[6];
    private double[] active=state;
    private boolean initialized,hasVuel,hasVoel;
    private double integrationStep,minimumTimeConstantMultiplier=1,vuel,voel;

    public int oel,uel,scl;
    public double tr,kpr,kir,kdr,tdr,vpidmax,vpidmin,ka,ta,vrmax,vrmin;
    public double te,efdmax,ke,kf,tf,kp,ki,xl,thetaP,kc,vbmax,reference,outputSignal;

    public St2cExciter(String id,St2cData data,Machine machine){
        super(id,"ST2C","PSS/E");this.data=data;this._data=data;setMachine(machine);
    }
    public St2cData getData(){return data;}
    public void setVuel(double v){vuel=v;hasVuel=true;} public void clearVuel(){hasVuel=false;}
    public void setVoel(double v){voel=v;hasVoel=true;} public void clearVoel(){hasVoel=false;}
    @Override public void configureIntegrationStep(double step){configureIntegrationStep(step,1);}
    public void configureIntegrationStep(double step,double multiplier){integrationStep=step;minimumTimeConstantMultiplier=multiplier;}

    @Override public boolean initStates(BaseDStabBus<?,?> bus,Machine machine){
        loadAndCorrectParameters();
        if(tr<0||tdr<0||ta<0||te<0||tf<0||ka<=EPS||vbmax<0
                ||(te<=EPS&&Math.abs(ke)<=EPS)||!finiteParameters())return false;
        double efd0=machine.getEfd(),sensed=sensingVoltage(machine),vb0=availableBridge(machine);
        if(!Double.isFinite(efd0)||efd0<0||!Double.isFinite(sensed)||sensed<=EPS||vb0<=EPS)return false;
        double vr0=ke*efd0/vb0,pid0=vr0/ka;
        double error0=Math.abs(kir)>EPS?0:pid0/kpr;
        vpidmax=Math.max(vpidmax,pid0);vpidmin=Math.min(vpidmin,pid0);
        vrmax=Math.max(vrmax,vr0);vrmin=Math.min(vrmin,vr0);efdmax=Math.max(efdmax,efd0);
        state[SENSED]=sensed;state[PID_I]=pid0-kpr*error0;state[DERIV_LAG]=error0;
        state[VR]=vr0;state[EFD]=efd0;state[FB_LAG]=efd0;
        System.arraycopy(state,0,trial,0,state.length);active=state;
        reference=sensed+error0-stabilizerSignal(machine)-directUel()+directOel();
        outputSignal=efd0;initialized=true;return true;
    }

    private void loadAndCorrectParameters(){
        oel=normalizedSelector(data.getOel());uel=normalizedSelector(data.getUel());scl=data.getScl();
        tr=correctedBypass(data.getTr());kpr=data.getKpr();kir=data.getKir();
        if(Math.abs(kpr)<=EPS&&Math.abs(kir)<=EPS)kpr=40;
        kdr=data.getKdr();tdr=data.getTdr();
        vpidmax=Math.max(data.getVpidmax(),data.getVpidmin());vpidmin=Math.min(data.getVpidmax(),data.getVpidmin());
        ka=Math.abs(data.getKa())<=EPS?minimumTimeConstantMultiplier*integrationStep:data.getKa();
        ta=correctedBypass(data.getTa());vrmax=Math.max(data.getVrmax(),data.getVrmin());
        vrmin=Math.min(data.getVrmax(),data.getVrmin());te=correctedBypass(data.getTe());
        efdmax=data.getEfdmax();ke=data.getKe();kf=data.getKf();tf=correctedBypass(data.getTf());
        kp=data.getKp();ki=data.getKi();xl=data.getXl();thetaP=data.getThetaP();kc=data.getKc();vbmax=data.getVbmax();
    }
    private boolean finiteParameters(){
        double[] p={kpr,kir,kdr,tdr,vpidmax,vpidmin,ka,vrmax,vrmin,efdmax,ke,kf,kp,ki,xl,thetaP,kc,vbmax};
        for(double v:p)if(!Double.isFinite(v))return false;return true;
    }
    private double correctedBypass(double v){double min=minimumTimeConstantMultiplier*integrationStep;
        if(v>0&&v<.5*min)return 0;if(v>.5*min&&v<min)return min;return v;}

    @Override public boolean nextStep(double dt,DynamicSimuMethod method,Machine machine,int flag){
        if(!initialized||dt<0)return false;if(dt==0)return true;int stage=method==DynamicSimuMethod.MODIFIED_EULER?flag:2;
        if(stage==0){derivatives(state,oldDerivative,machine);add(state,oldDerivative,dt,trial);constrain(trial);active=trial;}
        else if(stage==1){double[] d=new double[6];derivatives(trial,d,machine);for(int i=0;i<6;i++)state[i]+=.5*(oldDerivative[i]+d[i])*dt;constrain(state);active=state;}
        else{double[] d=new double[6];derivatives(state,d,machine);add(state,d,dt,state);constrain(state);active=state;}
        outputSignal=algebraics(active,machine).efd;return true;
    }
    private void derivatives(double[] x,double[] dx,Machine machine){
        Arrays.fill(dx,0);Algebraic a=algebraics(x,machine);
        dx[SENSED]=lagDerivative(sensingVoltage(machine),x[SENSED],tr);
        dx[DERIV_LAG]=lagDerivative(a.error,x[DERIV_LAG],tdr);
        dx[PID_I]=nonWindup(kir*a.error,a.pidRaw,vpidmin,vpidmax);
        dx[VR]=nonWindup(lagDerivative(ka*a.gatedPid,x[VR],ta),x[VR],vrmin,vrmax);
        double efdRate=te>EPS?(a.drive-ke*x[EFD])/te:0;
        dx[EFD]=nonWindup(efdRate,x[EFD],0,efdmax);
        dx[FB_LAG]=lagDerivative(a.efd,x[FB_LAG],tf);
    }
    private Algebraic algebraics(double[] x,Machine machine){
        double efd=te>EPS?clamp(x[EFD],0,efdmax):solveAlgebraicField(x,machine);
        return algebraicsForField(x,machine,efd);
    }
    private Algebraic algebraicsForField(double[] x,Machine machine,double efd){
        double sensed=tr>EPS?x[SENSED]:sensingVoltage(machine);
        double feedback=tf>EPS?kf*(efd-x[FB_LAG])/tf:0;
        double error=reference-sensed+stabilizerSignal(machine)+directUel()-directOel()-feedback;
        double derivative=tdr>EPS?kdr*(error-x[DERIV_LAG])/tdr:0;
        double pidRaw=kpr*error+x[PID_I]+derivative,pid=clamp(pidRaw,vpidmin,vpidmax),gated=pid;
        if(uel==2&&hasVuel)gated=Math.max(gated,vuel);if(oel==2&&hasVoel)gated=Math.min(gated,voel);
        double vr=ta>EPS?clamp(x[VR],vrmin,vrmax):clamp(ka*gated,vrmin,vrmax);
        double bridge=availableBridge(machine),drive=vr*bridge;
        return new Algebraic(sensed,efd,feedback,error,derivative,pidRaw,pid,gated,vr,bridge,drive);
    }
    private double solveAlgebraicField(double[] x,Machine machine){
        double lo=0,hi=Math.max(0,efdmax);
        if(fieldResidual(lo,x,machine)>=0)return lo;if(fieldResidual(hi,x,machine)<=0)return hi;
        for(int i=0;i<100;i++){double mid=.5*(lo+hi),fm=fieldResidual(mid,x,machine);
            if(Math.abs(fm)<1e-13)return mid;if(fm<0)lo=mid;else hi=mid;}
        return .5*(lo+hi);
    }
    private double fieldResidual(double efd,double[] x,Machine machine){
        return efd-clamp(algebraicsForField(x,machine,efd).drive/ke,0,efdmax);
    }
    private double compoundSource(Machine machine){
        Complex angle=new Complex(Math.cos(Math.toRadians(thetaP)),Math.sin(Math.toRadians(thetaP)));
        Complex kpPhasor=angle.multiply(kp),vt=machine.getDStabBus().getVoltage();
        Complex it=machine.getIxy().divide(machine.getIMultiFactor());
        return kpPhasor.multiply(vt).add(Complex.I.multiply(new Complex(ki,0).add(kpPhasor.multiply(xl))).multiply(it)).abs();
    }
    private double availableBridge(Machine machine){
        if(Math.abs(kp)<=EPS&&Math.abs(ki)<=EPS)return Math.min(1,vbmax);
        double source=compoundSource(machine);if(source<=EPS)return 0;double ifd=exciterIfd(machine);
        return clamp(source*Exac1Exciter.rectifierFactor(kc*ifd/source),0,vbmax);
    }
    private void constrain(double[] x){if(ta>EPS)x[VR]=clamp(x[VR],vrmin,vrmax);if(te>EPS)x[EFD]=clamp(x[EFD],0,efdmax);
        for(int i=0;i<x.length;i++)if(!Double.isFinite(x[i]))x[i]=0;}
    private double directUel(){return uel==1&&hasVuel?vuel:0;} private double directOel(){return oel==1&&hasVoel?voel:0;}
    private static int normalizedSelector(int v){return v==2?2:1;}
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
    public double getPidError(){return algebraics(active,getMachine()).error;}
    public double getPidRawOutput(){return algebraics(active,getMachine()).pidRaw;}
    public double getPidOutput(){return algebraics(active,getMachine()).pid;}
    public double getGatedPidOutput(){return algebraics(active,getMachine()).gatedPid;}
    public double getRegulatorOutput(){return algebraics(active,getMachine()).vr;}
    public double getFeedbackOutput(){return algebraics(active,getMachine()).feedback;}
    public double getCompoundSource(){return compoundSource(getMachine());}
    public double getAvailableBridge(){return availableBridge(getMachine());}
    public double[] getStateSnapshot(){return active.clone();}
    @Override public Map<String,Double> getNamedStates(){return Map.of(
            "Sensed Vt",getSensedVoltage(),"IntegratorKIr",active[PID_I],
            "Derivative",algebraics(active,getMachine()).derivative,
            "VR",getRegulatorOutput(),"EFD",active[EFD],
            "VF",getFeedbackOutput());}
    @Override public double getOutput(Machine machine){outputSignal=algebraics(active,machine).efd;return outputSignal;}
    @Override public void setRefPoint(double v){reference=v;} @Override public double getRefPoint(){return reference;}
    @Override public AnController getAnController(){return getClass().getAnnotation(AnController.class);}
    @Override public Field getField(String name)throws Exception{return getClass().getField(name);}
    @Override public Object getFieldObject(Field field)throws Exception{return field.get(this);}
    private record Algebraic(double sensed,double efd,double feedback,double error,double derivative,
            double pidRaw,double pid,double gatedPid,double vr,double bridge,double drive){}
}
