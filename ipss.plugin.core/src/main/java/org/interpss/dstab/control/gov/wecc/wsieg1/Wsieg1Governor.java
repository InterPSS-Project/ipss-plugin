package org.interpss.dstab.control.gov.wecc.wsieg1;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.interpss.dstab.control.util.IntegrationStateAccess;
import org.interpss.dstab.control.util.IntegrationStateMonitor;
import org.interpss.dstab.control.util.IntegrationStateMonitor.Stage;
import org.interpss.dstab.control.util.StaggeredEulerParticipant;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.ICMLStateProvider;
import com.interpss.dstab.controller.deqn.AbstractGovernor;
import com.interpss.dstab.mach.Machine;

/** Extended WECC steam governor: two deadbands, valve curve and four turbine stages. */
public final class Wsieg1Governor extends AbstractGovernor implements ICMLStateProvider, StaggeredEulerParticipant, IntegrationStateAccess,
        org.interpss.dstab.control.util.IntegrationStepAware {
    private static final String[] STATE_NAMES={"Speed lag","Valve","Turbine 1","Turbine 2","Turbine 3","Turbine 4"};
    /** Constant order after the secondary bus and machine identifier. */
    public static final java.util.List<String> CONSTANT_NAMES=java.util.List.of(
            "K","T1","T2","T3","Uo","Uc","Pmax","Pmin","T4","K1","K2","T5","K3","K4",
            "T6","K5","K6","T7","K7","K8","Db1","Err","Db2","Gv1","Pgv1","Gv2","Pgv2",
            "Gv3","Pgv3","Gv4","Pgv4","Gv5","Pgv5","Iblock");
    private final double[] c;
    private final double[] x = new double[6], old = new double[6], d0 = new double[6], d1 = new double[6];
    private final double[] curveX = new double[5], curveY = new double[5];
    private final double[] turbineTimes = new double[4];
    private double integrationStep, speedTime;
    private final IntegrationStateMonitor monitor=new IntegrationStateMonitor(STATE_NAMES);
    private int points, mode;
    private double reference, lower, upper, gateMemory;
    private boolean initialized, predicted;
    private Machine initializedMachine;
    private long staggeredEvaluationCount;
    private double initialReference, backlashInputMemory;
    private double secondaryScale=1;
    private Machine secondaryMachine;
    public void attachSecondaryShaft(Machine machine) {
        if(secondaryMachine!=null || machine==null || machine==getMachine() || machine.getGovernor()!=null)
            throw new IllegalArgumentException("Only one distinct secondary shaft without an existing governor can be attached");
        var companion=new SecondaryShaft(this);companion.setMachine(machine);
        secondaryMachine=machine;
    }
    /** The companion owns an output binding, never another copy of the turbine states. */
    public static boolean isSecondaryShaft(Object controller) { return controller instanceof SecondaryShaft; }
    private static final class SecondaryShaft extends AbstractGovernor implements ICMLStateProvider, StaggeredEulerParticipant {
        private final Wsieg1Governor owner;
        private Machine initializedMachine;
        SecondaryShaft(Wsieg1Governor owner) {super(owner.getId()+"_LP","WSIEG1_LP","WECC");this.owner=owner;}
        @Override public boolean initStates(BaseDStabBus<?,?> bus,Machine machine) {
            double base=bus.getNetwork().getBaseKva();
            owner.secondaryScale=owner.getMachine().getRating(org.interpss.numeric.datatype.Unit.UnitType.mVA,base)
                    /machine.getRating(org.interpss.numeric.datatype.Unit.UnitType.mVA,base);
            boolean valid=Double.isFinite(owner.secondaryScale)&&owner.secondaryScale>0;
            initializedMachine=valid?machine:null;
            return valid;
        }
        @Override public boolean nextStep(double dt,DynamicSimuMethod method,Machine machine,int flag) {
            // The shared turbine is integrated by its high-pressure owner exactly once per stage.
            return Double.isFinite(dt)&&dt>=0&&method==DynamicSimuMethod.MODIFIED_EULER&&(flag==0||flag==1);
        }
        @Override public double getOutput(Machine machine) { return owner.secondaryScale*owner.shaftOutput(1); }
        @Override public Map<String,Double> getNamedStates() {return Map.of("Shared turbine output",getOutput(getMachine()));}
        @Override public int staggeredStateCount() { return 0; }
        private void requireBuffer(double[] values,int offset) {
            owner.requireSharedState();
            if(initializedMachine!=getMachine() || getMachine().getGovernor()!=this)
                throw new IllegalStateException("Reinitialize after replacing the secondary-shaft owner");
            java.util.Objects.checkFromIndexSize(offset,0,values.length);
        }
        @Override public void readStaggeredState(double[] values,int offset) { requireBuffer(values,offset); }
        @Override public void evaluateStaggered(double step,double[] rates,int offset) { requireBuffer(rates,offset);requireStep(step); }
        @Override public void applyStaggeredState(double[] values,int offset,double step) { requireBuffer(values,offset);requireStep(step); }
    }

    public Wsieg1Governor(String id, double[] constants) {
        super(id, "WSIEG1", "WECC");
        if (constants == null || constants.length != 34) throw new IllegalArgumentException("WSIEG1 requires 34 constants");
        c = constants.clone();
        for (double v : c) if (!Double.isFinite(v)) throw new IllegalArgumentException("WSIEG1 constants must be finite");
        if (c[1]<0 || c[2]<0 || (c[1]==0 && c[2]!=0) || c[3]<=0 || c[4]<0 || c[5]>0
                || (c[6]<c[7] && !((c[33]==2||c[33]==3)&&c[6]==0)) || c[20]<0 || c[21]<0 || c[21]>c[20] || c[22]<0
                || c[33]!=(int)c[33] || c[33]<0 || c[33]>3)
            throw new IllegalArgumentException("Invalid WSIEG1 time, rate, deadband or limit settings");
        for (int i : new int[]{8,11,14,17}) if(c[i]<0) throw new IllegalArgumentException("Negative turbine time constant");
        for (int i=0;i<5;i++) {
            double a=c[23+2*i], b=c[24+2*i];
            if (points>0 && a==0 && b==0) continue; // unused trailing curve slots
            if (points>0 && a==curveX[points-1] && b==curveY[points-1]) continue;
            if (points>0 && (a<=curveX[points-1] || b<curveY[points-1]))
                throw new IllegalArgumentException("WSIEG1 valve curve must be increasing");
            curveX[points]=a;curveY[points++]=b;
        }
        if (points<2) { points=2;curveX[0]=curveY[0]=0;curveX[1]=curveY[1]=1; }
    }

    /** The outer interval is prepared at initialization, never inferred from a trial/local step. */
    @Override public void configureIntegrationStep(double seconds) {
        if(!Double.isFinite(seconds)||seconds<0.)throw new IllegalArgumentException("WSIEG1 outer interval must be finite and nonnegative");
        integrationStep=seconds;
    }
    /** Entered constants remain immutable; zero means an algebraic prepared lag. */
    public double getPreparedTimeConstant(int constantIndex) {
        if(!initialized)throw new IllegalStateException("Initialize before reading prepared time constants");
        if(constantIndex==1)return speedTime;
        if(constantIndex==3)return c[3];
        if(constantIndex>=8&&constantIndex<=17&&(constantIndex-8)%3==0)return turbineTimes[(constantIndex-8)/3];
        throw new IllegalArgumentException("Not a WSIEG1 lag or servo time index");
    }
    private double preparedLag(double entered) {return entered<=2.*integrationStep?0.:entered;}

    @Override public boolean initStates(BaseDStabBus<?,?> bus, Machine machine) {
        initialized=false;predicted=false;monitor.reset();staggeredEvaluationCount=0;
        if(machine==null || machine!=getMachine() || !Double.isFinite(machine.getPm()) || !Double.isFinite(machine.getSpeed()))return false;
        double sum=c[9]+c[12]+c[15]+c[18];
        if(!Double.isFinite(sum)||sum<=0) return false;
        double p=machine.getPm()/sum, gate=interpolate(p,curveY,curveX);
        if(!Double.isFinite(p)||!Double.isFinite(gate))return false;
        speedTime=preparedLag(c[1]);
        for(int i=0;i<4;i++)turbineTimes[i]=preparedLag(c[8+3*i]);
        upper=c[6];lower=c[7];
        int block=(int)c[33];
        if((block==1||block==3)&&lower==0) lower=gate;
        if((block==2||block==3)&&upper==0) upper=gate;
        // Initialization accommodates the solved operating point without moving it.
        lower=Math.min(lower,gate);upper=Math.max(upper,gate);
        Arrays.fill(x,p);x[0]=0;x[1]=gate;
        System.arraycopy(x,0,old,0,6);Arrays.fill(d0,0);
        gateMemory=gate;reference=gate;mode=0;predicted=false;initialized=true;
        initializedMachine=machine;initialReference=gate;backlashInputMemory=gate;
        derivatives(x,d1,machine.getSpeed()-1.);retainRates(x,d1,Stage.INITIALIZATION);
        return true;
    }
    @Override public boolean nextStep(double dt,DynamicSimuMethod method,Machine machine,int flag) {
        if(!initialized || !Double.isFinite(dt)||dt<0 || method!=DynamicSimuMethod.MODIFIED_EULER
                || (flag!=0&&flag!=1) || (flag==1&&!predicted)) return false;
        if(dt==0) {
            derivatives(x,d1,machine.getSpeed()-1.);retainRates(x,d1,Stage.PREDICTOR);
            return true; // Event-boundary algebraic refresh cannot advance state/history.
        }
        if(flag==0) {
            System.arraycopy(x,0,old,0,6);
            derivatives(old,d0,machine.getSpeed()-1);
            retainRates(old,d0,Stage.PREDICTOR);
            for(int i=0;i<6;i++) x[i]=old[i]+dt*d0[i];
            predicted=true;
        } else {
            derivatives(x,d1,machine.getSpeed()-1);
            retainRates(x,d1,Stage.CORRECTOR);
            for(int i=0;i<6;i++) x[i]=old[i]+.5*dt*(d0[i]+d1[i]);
            predicted=false;
        }
        x[1]=clamp(x[1],lower,upper);
        synchronizeBypasses(x);
        if(flag==1) commitHistories();
        return true;
    }

    private void commitHistories() {
        double output=backlash(x[1]);if(c[22]>0. && output!=gateMemory)backlashInputMemory=x[1];
        gateMemory=output;mode=deadbandMode(getMachine().getSpeed()-1);
    }
    private static void requireStep(double step) {
        if(!Double.isFinite(step)||step<=0.)throw new IllegalArgumentException("Shared integration step must be finite and positive");
    }
    private void requireSharedState() {
        if(!initialized||initializedMachine!=getMachine()||getMachine().getGovernor()!=this)
            throw new IllegalStateException("Initialize the governor with its current machine owner before shared integration");
        if(predicted)throw new IllegalStateException("Finish the modified-Euler predictor before shared integration");
    }
    private void requireBuffer(double[] values,int offset) {requireSharedState();java.util.Objects.checkFromIndexSize(offset,6,values.length);}
    @Override public int staggeredStateCount() { return 6; }
    @Override public void readStaggeredState(double[] values,int offset) {requireBuffer(values,offset);System.arraycopy(x,0,values,offset,6);}
    @Override public void evaluateStaggered(double step,double[] rates,int offset) {
        requireBuffer(rates,offset);requireStep(step);derivatives(x,d1,getMachine().getSpeed()-1.);
        retainRates(x,d1,Stage.STAGGERED);System.arraycopy(d1,0,rates,offset,6);staggeredEvaluationCount++;
    }
    @Override public void applyStaggeredState(double[] values,int offset,double step) {
        requireBuffer(values,offset);requireStep(step);
        for(int i=0;i<6;i++)if(!Double.isFinite(values[offset+i]))throw new IllegalArgumentException("Trial states must be finite");
        System.arraycopy(values,offset,x,0,6);x[1]=clamp(x[1],lower,upper);synchronizeBypasses(x);
    }
    @Override public void completeStaggeredStep(double step) {requireSharedState();requireStep(step);commitHistories();}
    public long getStaggeredEvaluationCount() {return staggeredEvaluationCount;}
    /** Published compensation coordinate; bypassed slots expose their algebraic input. */
    public void readPublishedState(double[] values,int offset) {
        requireBuffer(values,offset);
        values[offset]=speedTime>0?(1.-c[2]/speedTime)*x[0]:c[0]*speedInput(getMachine().getSpeed()-1.);
        System.arraycopy(x,1,values,offset+1,5);
    }
    private void retainRates(double[] state,double[] rates,Stage stage) {
        monitor.begin(stage);for(int i=0;i<6;i++)monitor.record(i,state[i],rates[i]);
    }
    @Override public Map<String,Double> getIntegrationStates() {
        var states=new LinkedHashMap<String,Double>();for(int i=0;i<6;i++)states.put(STATE_NAMES[i],x[i]);return Map.copyOf(states);
    }
    @Override public IntegrationStateMonitor.Snapshot getStateDerivativeSnapshot() {return monitor.snapshot();}
    @Override public Map<String,Double> getIntegrationHistories() {
        return Map.of("Valve deadband memory",gateMemory,"Speed deadband mode",(double)mode,"Valve input reference",backlashInputMemory);
    }
    private int deadbandMode(double u) {
        if(mode==0) return u>c[20]?1:u< -c[20]?-1:0;
        if(mode>0 && u<=c[21]) return u< -c[20]?-1:0;
        if(mode<0 && u>= -c[21]) return u>c[20]?1:0;
        return mode;
    }
    private double speedInput(double u) {
        int m=deadbandMode(u);
        return m>0?u-c[21]:m<0?u+c[21]:0;
    }
    private double backlash(double u) { return clamp(gateMemory,u-c[22],u+c[22]); }
    private void derivatives(double[] s,double[] d,double speed) {
        double u=c[0]*speedInput(speed);
        double feedback=speedTime>0?s[0]+c[2]/speedTime*(u-s[0]):u;
        d[0]=speedTime>0?(u-s[0])/speedTime:0;
        double rate=clamp((reference-feedback-backlash(s[1]))/c[3],c[5],c[4]);
        d[1]=(s[1]>=upper&&rate>0)||(s[1]<=lower&&rate<0)?0:rate;
        double input=interpolate(backlash(s[1]),curveX,curveY);
        for(int i=0;i<4;i++) {
            double t=turbineTimes[i];
            d[2+i]=t>0?(input-s[2+i])/t:0;
            input=t>0?s[2+i]:input;
        }
    }
    private void synchronizeBypasses(double[] s) {
        double input=interpolate(backlash(s[1]),curveX,curveY);
        for(int i=0;i<4;i++) { if(turbineTimes[i]==0)s[2+i]=input;input=s[2+i]; }
    }
    @Override public double getOutput(Machine machine) { return shaftOutput(0); }
    private double shaftOutput(int shaft) {
        double input=interpolate(backlash(x[1]),curveX,curveY),output=0;
        for(int i=0;i<4;i++) { if(turbineTimes[i]>0) input=x[2+i];output+=c[9+3*i+shaft]*input; }
        return output;
    }
    @Override public void setRefPoint(double value) { if(!Double.isFinite(value))throw new IllegalArgumentException("Nonfinite reference");reference=value; }
    public double getRefPoint() { return reference; }
    public double getInitialReference() { return initialReference; }
    public double getSecondaryBaseScale() { return secondaryMachine==null?0.:secondaryScale; }
    public double getSpeedDeadbandOutput() { return speedInput(getMachine().getSpeed()-1.); }
    public double getValveOutput() { return backlash(x[1]); }
    public double getBacklashInputHistory() { return backlashInputMemory; }
    public double getValvePosition() { return x[1]; }
    public double valvePower(double gate) { return interpolate(gate,curveX,curveY); }
    public double getEffectiveMinimum() { return lower; }
    public double getEffectiveMaximum() { return upper; }
    public double[] getConstants() { return c.clone(); }
    @Override public Map<String,Double> getNamedStates() {
        var states=new LinkedHashMap<String,Double>();
        for(int i=0;i<6;i++)states.put(STATE_NAMES[i],x[i]);
        states.put("Valve deadband memory",gateMemory);states.put("Speed deadband mode",(double)mode);
        return Map.copyOf(states);
    }
    private double interpolate(double value,double[] a,double[] b) {
        int i=1;while(i<points-1&&value>a[i])i++;
        if(a[i]==a[i-1])return b[i];
        return b[i-1]+(value-a[i-1])*(b[i]-b[i-1])/(a[i]-a[i-1]);
    }
    private static double clamp(double value,double low,double high) { return Math.max(low,Math.min(high,value)); }
}
