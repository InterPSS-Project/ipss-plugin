package org.interpss.dstab.control.exc.psse.st1c;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;

import org.interpss.dstab.control.util.IntegrationStepAware;
import org.interpss.dstab.control.exc.UnderExcitationLimiterTarget;
import org.interpss.dstab.control.exc.OverExcitationLimiterTarget;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.ICMLStateProvider;
import com.interpss.dstab.controller.cml.ICMLMachineVoltageProvider;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnnotateExciter;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.MachineIfdBase;

/** Native PSS/E implementation of the IEEE 421.5-2016 ST1C exciter. */
@AnController(input="mach.vt",output="this.outputSignal",refPoint="this.reference",display={})
public final class St1cExciter extends AnnotateExciter
        implements IntegrationStepAware, ICMLStateProvider, UnderExcitationLimiterTarget,
        OverExcitationLimiterTarget {
    private static final double EPS=1e-12;
    private static final int SENSED=0,LL1=1,LL2=2,VA=3,FEEDBACK_LAG=4;
    private final St1cData data;
    private final double[] state=new double[5],trial=new double[5],oldDerivative=new double[5];
    private double[] active=state;
    private boolean initialized,hasVuel,hasVoel;
    private double integrationStep,minimumTimeConstantMultiplier=1;

    public int uel,vos,oel;
    public double tr,vimax,vimin,tc,tb,tc1,tb1,ka,ta,vamax,vamin;
    public double vrmax,vrmin,kc,kf,tf,klr,ilr,reference,outputSignal;
    private double vuel,voel;

    public St1cExciter(String id,St1cData data,Machine machine){
        super(id,"ST1C","PSS/E");this.data=data;this._data=data;setMachine(machine);
    }
    public St1cData getData(){return data;}

    @Override public void configureIntegrationStep(double stepSeconds){configureIntegrationStep(stepSeconds,1);}
    public void configureIntegrationStep(double stepSeconds,double multiplier){integrationStep=stepSeconds;minimumTimeConstantMultiplier=multiplier;}

    @Override public boolean initStates(BaseDStabBus<?,?> bus,Machine machine){
        loadAndCorrectParameters();
        if(uel<0||uel>3||oel<0||oel>3||vos<1||vos>2||tr<0||tb<0||tb1<0||ta<0||tf<0||Math.abs(ka)<=EPS)return false;
        double vt=sensingVoltage(machine),ifd=ifd(machine),efd=machine.getEfd();
        if(!Double.isFinite(vt)||vt<=EPS||!Double.isFinite(ifd)||!Double.isFinite(efd))return false;
        vrmax=Math.max(vrmax,(efd+kc*ifd)/vt);vrmin=Math.min(vrmin,efd/vt);
        double pss=stabilizer(machine),lr=fieldCurrentLimiter(ifd),va=efd+lr-(vos==2?pss:0);
        vamax=Math.max(vamax,va);vamin=Math.min(vamin,va);
        double vi=va/ka;vimax=Math.max(vimax,vi);vimin=Math.min(vimin,vi);
        state[SENSED]=vt;state[LL1]=vi;state[LL2]=vi;state[VA]=va;state[FEEDBACK_LAG]=efd;
        reference=vt+vi-(vos==1?pss:0);
        System.arraycopy(state,0,trial,0,state.length);active=state;outputSignal=efd;initialized=true;return true;
    }

    private void loadAndCorrectParameters(){
        uel=data.getUel();vos=data.getVos();oel=data.getOel();
        tr=correctTr(data.getTr());vimax=Math.max(data.getVimax(),data.getVimin());vimin=Math.min(data.getVimax(),data.getVimin());
        tc=data.getTc();tb=correctBypass(data.getTb());tc1=data.getTc1();tb1=correctBypass(data.getTb1());
        ka=data.getKa();double minimum=minimumTimeConstantMultiplier*integrationStep;if(Math.abs(ka)<=EPS&&minimum>0)ka=minimum;
        ta=data.getTa();vamax=Math.max(data.getVamax(),data.getVamin());vamin=Math.min(data.getVamax(),data.getVamin());
        vrmax=Math.max(data.getVrmax(),data.getVrmin());vrmin=Math.min(data.getVrmax(),data.getVrmin());
        kc=data.getKc();kf=data.getKf();tf=correctBypass(data.getTf());klr=data.getKlr();ilr=data.getIlr();
    }
    private double correctBypass(double v){double m=minimumTimeConstantMultiplier*integrationStep;if(v>0&&v<.5*m)return 0;if(v>.5*m&&v<m)return m;return v;}
    private double correctTr(double v){double m=minimumTimeConstantMultiplier*integrationStep;if(v>0&&v<.25*m)return 0;if(v>.25*m&&v<.5*m)return .5*m;return v;}

    @Override public boolean nextStep(double dt,DynamicSimuMethod method,Machine machine,int flag){
        if(!initialized||dt<0)return false;if(dt==0)return true;int stage=method==DynamicSimuMethod.MODIFIED_EULER?flag:2;
        if(stage==0){derivatives(state,oldDerivative,machine);add(state,oldDerivative,dt,trial);limitState(trial);active=trial;}
        else if(stage==1){double[] d=new double[5];derivatives(trial,d,machine);for(int i=0;i<5;i++)state[i]+=.5*(oldDerivative[i]+d[i])*dt;limitState(state);active=state;}
        else{double[] d=new double[5];derivatives(state,d,machine);add(state,d,dt,state);limitState(state);active=state;}
        outputSignal=algebraics(active,machine).efd;return true;
    }
    private void derivatives(double[] x,double[] d,Machine machine){
        Arrays.fill(d,0);Algebraic a=algebraics(x,machine);
        d[SENSED]=lagDerivative(sensingVoltage(machine),x[SENSED],tr);
        d[LL1]=lagDerivative(a.firstLeadLagInput,x[LL1],tb);
        d[LL2]=lagDerivative(a.firstLeadLagOutput,x[LL2],tb1);
        if(ta>EPS){double raw=(ka*a.secondLeadLagOutput-x[VA])/ta;d[VA]=nonWindup(raw,x[VA],vamin,vamax);}
        d[FEEDBACK_LAG]=lagDerivative(a.preField,x[FEEDBACK_LAG],tf);
    }

    private Algebraic algebraics(double[] x,Machine machine){
        double feedback=solveFeedback(x,machine);return algebraicsForFeedback(x,machine,feedback);
    }
    private Algebraic algebraicsForFeedback(double[] x,Machine machine,double feedback){
        double sensed=tr>EPS?x[SENSED]:sensingVoltage(machine),pss=stabilizer(machine);
        double error=reference-sensed-feedback+(vos==1?pss:0)+sumVuel()+sumVoel();
        double vi=clamp(error,vimin,vimax);double firstInput=uel==2?Math.max(vi,gateVuel()):vi;
        if(oel==2)firstInput=Math.min(firstInput,gateVoel());
        double firstOutput=leadLagOutput(firstInput,x[LL1],tc,tb);
        double secondOutput=leadLagOutput(firstOutput,x[LL2],tc1,tb1);
        double va=ta>EPS?clamp(x[VA],vamin,vamax):clamp(ka*secondOutput,vamin,vamax);
        double pre=va+(vos==2?pss:0)-fieldCurrentLimiter(ifd(machine));
        if(uel==3)pre=Math.max(pre,gateVuel());if(oel==3)pre=Math.min(pre,gateVoel());
        double lower=sensingVoltage(machine)*vrmin,upper=sensingVoltage(machine)*vrmax-kc*ifd(machine);
        double efd=clamp(pre,lower,upper);
        return new Algebraic(sensed,feedback,error,vi,firstInput,firstOutput,secondOutput,va,pre,lower,upper,efd);
    }
    private double solveFeedback(double[] x,Machine machine){
        if(tf<=EPS||Math.abs(kf)<=EPS)return 0;double gain=kf/tf;
        java.util.function.DoubleUnaryOperator residual=f->f-gain*(algebraicsForFeedbackNoSolve(x,machine,f)-x[FEEDBACK_LAG]);
        double lo=-1,hi=1,flo=residual.applyAsDouble(lo),fhi=residual.applyAsDouble(hi);
        for(int i=0;i<60&&(flo>0||fhi<0);i++){if(flo>0){lo*=2;flo=residual.applyAsDouble(lo);}if(fhi<0){hi*=2;fhi=residual.applyAsDouble(hi);}}
        if(flo>0||fhi<0)return gain*(algebraicsForFeedbackNoSolve(x,machine,0)-x[FEEDBACK_LAG]);
        for(int i=0;i<80;i++){double mid=.5*(lo+hi),fm=residual.applyAsDouble(mid);if(fm>0)hi=mid;else lo=mid;}return .5*(lo+hi);
    }
    private double algebraicsForFeedbackNoSolve(double[] x,Machine machine,double feedback){
        double sensed=tr>EPS?x[SENSED]:sensingVoltage(machine),pss=stabilizer(machine);
        double error=reference-sensed-feedback+(vos==1?pss:0)+sumVuel()+sumVoel();
        double vi=clamp(error,vimin,vimax),first=uel==2?Math.max(vi,gateVuel()):vi;if(oel==2)first=Math.min(first,gateVoel());
        double y1=leadLagOutput(first,x[LL1],tc,tb),y2=leadLagOutput(y1,x[LL2],tc1,tb1);
        double va=ta>EPS?clamp(x[VA],vamin,vamax):clamp(ka*y2,vamin,vamax);
        double pre=va+(vos==2?pss:0)-fieldCurrentLimiter(ifd(machine));if(uel==3)pre=Math.max(pre,gateVuel());if(oel==3)pre=Math.min(pre,gateVoel());return pre;
    }

    private double sumVuel(){return hasVuel&&uel<2?vuel:0;}private double sumVoel(){return hasVoel&&oel<2?voel:0;}
    private double gateVuel(){return hasVuel?vuel:Double.NEGATIVE_INFINITY;}private double gateVoel(){return hasVoel?voel:Double.POSITIVE_INFINITY;}
    private double fieldCurrentLimiter(double ifd){return Math.max(0,klr*(ifd-ilr));}
    private static double leadLagOutput(double input,double state,double lead,double lag){return lag>EPS?(lead/lag)*input+(1-lead/lag)*state:input;}
    private static double lagDerivative(double input,double state,double time){return time>EPS?(input-state)/time:0;}
    private static double nonWindup(double rate,double state,double lower,double upper){if(state>=upper&&rate>0)return 0;if(state<=lower&&rate<0)return 0;return rate;}
    private static double clamp(double v,double lo,double hi){return Math.max(lo,Math.min(hi,v));}
    private static void add(double[] x,double[] d,double dt,double[] y){for(int i=0;i<x.length;i++)y[i]=x[i]+d[i]*dt;}
    private void limitState(double[] x){if(ta>EPS)x[VA]=clamp(x[VA],vamin,vamax);for(int i=0;i<x.length;i++)if(!Double.isFinite(x[i]))x[i]=0;}
    private static double sensingVoltage(Machine machine){if(machine instanceof ICMLMachineVoltageProvider p){double v=p.getCmlMachineVoltage();if(Double.isFinite(v))return v;}return machine.getDStabBus().getVoltageMag();}
    private static double ifd(Machine machine){double v=machine.calculateIfd(MachineIfdBase.EXCITER);return Double.isFinite(v)?v:0;}
    private static double stabilizer(Machine machine){return machine.getStabilizer()==null?0:machine.getStabilizer().getOutput(machine);}

    public void setVuel(double v){vuel=v;hasVuel=true;}public void clearVuel(){hasVuel=false;}
    public void setVoel(double v){voel=v;hasVoel=true;}public void clearVoel(){hasVoel=false;}
    public double getSensedVoltage(){return algebraics(active,getMachine()).sensed;}
    public double getRateFeedback(){return algebraics(active,getMachine()).feedback;}
    public double getVoltageError(){return algebraics(active,getMachine()).error;}
    public double getLimitedVoltageError(){return algebraics(active,getMachine()).vi;}
    public double getFirstGateOutput(){return algebraics(active,getMachine()).firstLeadLagInput;}
    public double getFirstLeadLagOutput(){return algebraics(active,getMachine()).firstLeadLagOutput;}
    public double getSecondLeadLagOutput(){return algebraics(active,getMachine()).secondLeadLagOutput;}
    public double getRegulatorOutput(){return algebraics(active,getMachine()).va;}
    public double getPreFieldSignal(){return algebraics(active,getMachine()).preField;}
    public double getFieldLowerLimit(){return algebraics(active,getMachine()).lower;}
    public double getFieldUpperLimit(){return algebraics(active,getMachine()).upper;}
    public double[] getStateSnapshot(){return active.clone();}
    @Override public Map<String,Double> getNamedStates(){return Map.of(
            "VA",getRegulatorOutput(),"Sensed Vt",getSensedVoltage(),
            "LL",getFirstLeadLagOutput(),"LL1",getSecondLeadLagOutput(),
            "Feedback",getRateFeedback());}
    @Override public double getOutput(Machine machine){outputSignal=algebraics(active,machine).efd;return outputSignal;}
    @Override public void setRefPoint(double v){reference=v;}@Override public double getRefPoint(){return reference;}
    @Override public AnController getAnController(){return getClass().getAnnotation(AnController.class);}
    @Override public Field getField(String name)throws Exception{return getClass().getField(name);}
    @Override public Object getFieldObject(Field field)throws Exception{return field.get(this);}

    private record Algebraic(double sensed,double feedback,double error,double vi,double firstLeadLagInput,
            double firstLeadLagOutput,double secondLeadLagOutput,double va,double preField,double lower,double upper,double efd){}
}
