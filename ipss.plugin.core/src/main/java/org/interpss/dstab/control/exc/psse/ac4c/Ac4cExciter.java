package org.interpss.dstab.control.exc.psse.ac4c;

import java.lang.reflect.Field;
import java.util.Arrays;

import org.interpss.dstab.control.util.IntegrationStepAware;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnnotateExciter;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.MachineIfdBase;

/** IEEE 421.5-2016 / PSS/E AC4C controlled-rectifier excitation system. */
@AnController(input="mach.vt",output="this.outputSignal",refPoint="this.reference",display={})
public final class Ac4cExciter extends AnnotateExciter implements IntegrationStepAware {
    public static final int INPUT_UNUSED=0,INPUT_SUMMATION=1,INPUT_TAKEOVER=2;
    private static final double EPS=1e-12;
    private static final int EFIELD_BEFORE_LIMIT=0,VSENSE=1,VLL=2;
    private final Ac4cData data;
    private final double[] state=new double[3],trial=new double[3],oldDerivative=new double[3];
    private double[] active=state;
    private boolean initialized,hasVuel,hasVoel,hasVsclSum,hasVsclUel,hasVsclOel;
    private double integrationStep,minimumTimeConstantMultiplier=1;
    private double vuel,voel,vsclSum,vsclUel,vsclOel;

    public int oelLocation,uelLocation,sclLocation;
    public double tr,vimax,vimin,tc,tb,ka,ta,vrmax,vrmin,kc;
    public double reference,outputSignal;

    public Ac4cExciter(String id,Ac4cData data,Machine machine) {
        super(id,"AC4C","PSS/E");this.data=data;this._data=data;setMachine(machine);
    }
    public Ac4cData getData(){return data;}
    public void setVuel(double v){vuel=v;hasVuel=true;} public void setVoel(double v){voel=v;hasVoel=true;}
    public void setVsclSum(double v){vsclSum=v;hasVsclSum=true;}
    public void setVsclUel(double v){vsclUel=v;hasVsclUel=true;}
    public void setVsclOel(double v){vsclOel=v;hasVsclOel=true;}
    @Override public void configureIntegrationStep(double seconds){configureIntegrationStep(seconds,1);}
    public void configureIntegrationStep(double seconds,double multiplier){integrationStep=seconds;minimumTimeConstantMultiplier=multiplier;}

    @Override public boolean initStates(BaseDStabBus<?,?> bus,Machine machine) {
        loadAndCorrect();
        if(!validLocation(oelLocation)||!validLocation(uelLocation)||!validLocation(sclLocation)
                ||tr<0||tb<0||ta<0||ka<=EPS||kc<0)return false;
        double vt0=bus.getVoltageMag(),ifd0=exciterIfd(machine),efd0=machine.getEfd();
        if(!Double.isFinite(vt0)||!Double.isFinite(efd0))return false;
        vrmax=Math.max(vrmax,efd0+kc*ifd0);vrmin=Math.min(vrmin,efd0);
        double vi0=efd0/ka;vimax=Math.max(vimax,vi0);vimin=Math.min(vimin,vi0);
        state[EFIELD_BEFORE_LIMIT]=efd0;state[VSENSE]=vt0;
        state[VLL]=transferState(vi0,vi0,tc,tb);
        System.arraycopy(state,0,trial,0,state.length);active=state;
        reference=vi0+vt0-stabilizerSignal(machine)-summationLimiterInput();
        outputSignal=efd0;initialized=true;return true;
    }

    private void loadAndCorrect() {
        oelLocation=data.getOelLocation();uelLocation=data.getUelLocation();sclLocation=data.getSclLocation();
        tr=correctedBypass(data.getTr());vimax=Math.max(data.getVimax(),data.getVimin());
        vimin=Math.min(data.getVimax(),data.getVimin());tc=data.getTc();tb=correctedBypass(data.getTb());
        ka=data.getKa();ta=correctedRequired(data.getTa());vrmax=Math.max(data.getVrmax(),data.getVrmin());
        vrmin=Math.min(data.getVrmax(),data.getVrmin());kc=data.getKc();
    }
    private double minimumResolvedTimeConstant(){return minimumTimeConstantMultiplier*integrationStep;}
    private double correctedBypass(double value){double m=minimumResolvedTimeConstant();if(value>0&&value<.5*m)return 0;if(value>.5*m&&value<m)return m;return value;}
    private double correctedRequired(double value){double m=minimumResolvedTimeConstant();return value>0&&value<m?m:value;}

    @Override public boolean nextStep(double dt,DynamicSimuMethod method,Machine machine,int flag) {
        if(!initialized||dt<0)return false;if(dt==0){outputSignal=fieldOutput(active,machine);return true;}
        int stage=method==DynamicSimuMethod.MODIFIED_EULER?flag:2;
        if(stage==0){derivatives(state,oldDerivative,machine);add(state,oldDerivative,dt,trial);sanitize(trial);active=trial;}
        else if(stage==1){double[] d=new double[3];derivatives(trial,d,machine);for(int i=0;i<3;i++)state[i]+=.5*(oldDerivative[i]+d[i])*dt;sanitize(state);active=state;}
        else{double[] d=new double[3];derivatives(state,d,machine);add(state,d,dt,state);sanitize(state);active=state;}
        outputSignal=fieldOutput(active,machine);return true;
    }
    private void derivatives(double[] x,double[] dx,Machine machine) {
        Arrays.fill(dx,0);Algebraic a=algebraics(x,machine);
        dx[VSENSE]=lagDerivative(machine.getDStabBus().getVoltageMag(),x[VSENSE],tr);
        dx[VLL]=transferDerivative(a.vi,x[VLL],tc,tb);
        dx[EFIELD_BEFORE_LIMIT]=lagDerivative(ka*a.gated,x[EFIELD_BEFORE_LIMIT],ta);
    }
    private Algebraic algebraics(double[] x,Machine machine) {
        double vt=machine.getDStabBus().getVoltageMag(),sensed=tr>EPS?x[VSENSE]:vt;
        double error=reference-sensed+stabilizerSignal(machine)+summationLimiterInput();
        double vi=clamp(error,vimin,vimax),vll=transferOutput(vi,x[VLL],tc,tb);
        double gated=takeoverLimiterOutput(vll),upper=Math.max(vrmin,vrmax-kc*exciterIfd(machine));
        return new Algebraic(sensed,error,vi,vll,gated,upper);
    }
    private double fieldOutput(double[] x,Machine machine) {
        Algebraic a=algebraics(x,machine);
        return clamp(ta>EPS?x[EFIELD_BEFORE_LIMIT]:ka*a.gated,vrmin,a.upper);
    }
    private double summationLimiterInput() {
        double v=0;if(uelLocation==INPUT_SUMMATION&&hasVuel)v+=vuel;
        if(oelLocation==INPUT_SUMMATION&&hasVoel)v-=voel;
        if(sclLocation==INPUT_SUMMATION&&hasVsclSum)v-=vsclSum;return v;
    }
    private double takeoverLimiterOutput(double command) {
        double v=command;if(uelLocation==INPUT_TAKEOVER&&hasVuel)v=Math.max(v,vuel);
        if(sclLocation==INPUT_TAKEOVER&&hasVsclUel)v=Math.max(v,vsclUel);
        if(oelLocation==INPUT_TAKEOVER&&hasVoel)v=Math.min(v,voel);
        if(sclLocation==INPUT_TAKEOVER&&hasVsclOel)v=Math.min(v,vsclOel);return v;
    }
    private static boolean validLocation(int v){return v>=INPUT_UNUSED&&v<=INPUT_TAKEOVER;}
    private static double lagDerivative(double input,double value,double time){return time>EPS?(input-value)/time:0;}
    private static double transferState(double input,double output,double tc,double tb){return tb>EPS?output-tc/tb*input:0;}
    private static double transferOutput(double input,double x,double tc,double tb){return tb>EPS?tc/tb*input+x:input;}
    private static double transferDerivative(double input,double x,double tc,double tb){return tb>EPS?((1-tc/tb)*input-x)/tb:0;}
    private static void add(double[] x,double[] d,double dt,double[] y){for(int i=0;i<x.length;i++)y[i]=x[i]+d[i]*dt;}
    private static void sanitize(double[] x){for(int i=0;i<x.length;i++)if(!Double.isFinite(x[i]))x[i]=0;}
    private static double clamp(double v,double lo,double hi){return Math.max(lo,Math.min(hi,v));}
    private static double stabilizerSignal(Machine m){return m.getStabilizer()==null?0:m.getStabilizer().getOutput(m);}
    private static double exciterIfd(Machine m){double v=m.calculateIfd(MachineIfdBase.EXCITER);return Double.isFinite(v)?v:0;}

    public double getSensedVoltage(){return algebraics(active,getMachine()).sensed;}
    public double getVoltageError(){return algebraics(active,getMachine()).error;}
    public double getLimitedError(){return algebraics(active,getMachine()).vi;}
    public double getLeadLagState(){return active[VLL];}
    public double getLeadLagOutput(){return algebraics(active,getMachine()).vll;}
    public double getGateOutput(){return algebraics(active,getMachine()).gated;}
    public double getDynamicLowerLimit(){return vrmin;}
    public double getDynamicUpperLimit(){return algebraics(active,getMachine()).upper;}
    public double getInternalFieldBeforeLimit(){return active[EFIELD_BEFORE_LIMIT];}
    @Override public double getOutput(Machine machine){outputSignal=fieldOutput(active,machine);return outputSignal;}
    @Override public void setRefPoint(double value){reference=value;} @Override public double getRefPoint(){return reference;}
    private record Algebraic(double sensed,double error,double vi,double vll,double gated,double upper){}
    @Override public AnController getAnController(){return getClass().getAnnotation(AnController.class);}
    @Override public Field getField(String name)throws Exception{return getClass().getField(name);}
    @Override public Object getFieldObject(Field field)throws Exception{return field.get(this);}
}
