package org.interpss.dstab.control.oel.psse.oel2c;

import java.util.Arrays;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.control.exc.OverExcitationLimiterTarget;

import com.interpss.common.exp.InterpssRuntimeException;
import com.interpss.core.net.Network;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.dstab.controller.cml.ICMLStateProvider;
import com.interpss.dstab.dynLoad.impl.DynLoadModelImpl;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.MachineIfdBase;

/** Published OEL2C maximum-excitation limiter. */
public final class Oel2cOverExcitationLimiter extends DynLoadModelImpl
        implements ICMLStateProvider {
    private static final double EPS = 1.0e-12;
    private static final int PI=0, DERIVATIVE=1, LEAD_LAG_1=2, LEAD_LAG_2=3,
            REFERENCE_FILTER=4, CURRENT_REFERENCE=5, SENSED_INPUT=6, TIMER=7;
    private final Oel2cData data;
    private final Machine machine;
    private final OverExcitationLimiterTarget exciter;
    private final Hashtable<String,Object> states = new Hashtable<>();
    private final double[] state = new double[8], trial = new double[8], oldDerivative = new double[8];
    private double[] active = state;
    private double output, bias, setElapsed, resetElapsed;
    private double validationInput=Double.NaN;
    private boolean initialized, set, inputInitializationPending;

    public Oel2cOverExcitationLimiter(BaseDStabBus<?,?> bus, Machine machine,
            String generatorId, Oel2cData data) {
        if (bus == null || machine == null || data == null)
            throw new IllegalArgumentException("OEL2C requires a bus, machine, and data");
        if (!(machine.getExciter() instanceof OverExcitationLimiterTarget target))
            throw new IllegalArgumentException("OEL2C requires a compatible exciter");
        this.data=data; this.machine=machine; this.exciter=target;
        setId(generatorId); setName("OEL2C");
        setExtendedDeviceId("OEL2C_"+generatorId+"@"+bus.getId());
        setLoadPercent(0); setLoadFactor(0); setLoadPQ(Complex.ZERO); setInitLoadPQ(Complex.ZERO);
        setEquivY(Complex.ZERO); setNortonCurInj(Complex.ZERO); setCompensateShuntY(Complex.ZERO);
        setCurrInj2Net(Complex.ZERO); setDStabBus(bus);
    }

    @Override public boolean initStates(){return initStates(getDStabBus());}
    @Override public boolean initStates(BaseDStabBus<?,?> bus,Machine ignored){return initStates(bus);}
    @Override public boolean initStates(BaseDStabBus<?,?> bus,Network<?,?> ignored){return initStates(bus);}
    @Override public boolean initStates(BaseDStabBus<?,?> bus){
        if(bus==null||bus!=getDStabBus()||machine.getExciter()!=exciter)return false;
        double sensed=inputSignal();
        inputInitializationPending=Math.abs(sensed)<=EPS;
        initializeState(sensed);
        states.put(DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID,getExtendedDeviceId());
        initialized=finite(state)&&Double.isFinite(output); return initialized;
    }
    private void initializeState(double sensed){
        state[SENSED_INPUT]=sensed;
        state[CURRENT_REFERENCE]=data.instantaneousLimit();
        state[REFERENCE_FILTER]=data.instantaneousLimit();
        state[TIMER]=data.timerMin();
        bias=data.activationDelay()==0?0:data.resetReference();
        set=data.activationDelay()==0;
        state[DERIVATIVE]=0;
        state[PI]=0;
        state[LEAD_LAG_1]=0; state[LEAD_LAG_2]=0;
        System.arraycopy(state,0,trial,0,state.length); active=state;
        output=calculate(state).output(); exciter.setVoel(output);
    }
    /** Reset the published states for prescribed selected-input validation. */
    public void initializeWithInputSignal(double selectedInput){
        if(!Double.isFinite(selectedInput))throw new IllegalArgumentException("input must be finite");
        validationInput=selectedInput;inputInitializationPending=false;initializeState(selectedInput);initialized=true;
    }
    /** Advance using a prescribed selected input without changing model equations. */
    public boolean nextStepWithInputSignal(double dt,DynamicSimuMethod method,int flag,double selectedInput){
        if(!Double.isFinite(selectedInput))return false;validationInput=selectedInput;return nextStep(dt,method,flag);
    }

    @Override public boolean nextStep(double dt,DynamicSimuMethod method,int flag){
        if(method!=DynamicSimuMethod.MODIFIED_EULER)
            throw new InterpssRuntimeException("OEL2C supports MODIFIED_EULER only");
        if(!initialized||dt<0||!Double.isFinite(dt))return false;
        ensureInputInitialized();
        if(flag==0){derivatives(state,oldDerivative);add(state,oldDerivative,dt,trial);limit(trial);active=trial;}
        else if(flag==1){double[] corrected=new double[8];derivatives(trial,corrected);
            for(int i=0;i<state.length;i++)state[i]+=.5*dt*(oldDerivative[i]+corrected[i]);
            limit(state);active=state;
        }else throw new InterpssRuntimeException("OEL2C invalid integration flag: "+flag);
        output=calculate(active).output();exciter.setVoel(output);return finite(active)&&Double.isFinite(output);
    }

    @Override public boolean afterStep(double dt){
        if(!initialized||dt<0||!Double.isFinite(dt))return false;
        Algebraic a=calculate(state);
        boolean requestSet=a.timerError()<=0||data.activationDelay()==0;
        if(!requestSet&&a.actual()>state[CURRENT_REFERENCE]){
            setElapsed+=dt; requestSet=setElapsed+EPS>=data.activationDelay();
        }else if(a.actual()<=state[CURRENT_REFERENCE])setElapsed=0;
        if(requestSet){set=true;bias=0;setElapsed=0;}
        if(set&&Math.abs(state[CURRENT_REFERENCE]-data.instantaneousLimit())<=1e-9
                && state[CURRENT_REFERENCE]-a.actual()>data.resetThreshold()){
            resetElapsed+=dt;
            if(resetElapsed+EPS>=data.resetDelay()){
                set=false;bias=data.activationDelay()==0?0:data.resetReference();resetElapsed=0;
            }
        }else resetElapsed=0;
        output=calculate(state).output();exciter.setVoel(output);return true;
    }

    private void derivatives(double[] x,double[] d){
        Arrays.fill(d,0); Algebraic a=calculate(x);
        d[SENSED_INPUT]=lagRate(inputSignal(),x[SENSED_INPUT],data.inputFilterTime());
        d[CURRENT_REFERENCE]=nonWindup(a.ramp(),x[CURRENT_REFERENCE],data.thermalLimit(),data.instantaneousLimit());
        d[REFERENCE_FILTER]=lagRate(x[CURRENT_REFERENCE],x[REFERENCE_FILTER],data.referenceFilterTime());
        d[TIMER]=nonWindup(a.timerRate(),x[TIMER],data.timerMin(),data.timerMax());
        double piRate=data.ki()*a.error();
        if((a.pidUnlimited()>=data.pidMax()&&piRate>0)||(a.pidUnlimited()<=data.pidMin()&&piRate<0))piRate=0;
        d[PI]=piRate;
        d[DERIVATIVE]=data.td()>EPS
                ? (data.kd()/data.td()*a.error()-x[DERIVATIVE])/data.td():0;
        d[LEAD_LAG_1]=lagRate(a.pid(),x[LEAD_LAG_1],data.tb2());
        d[LEAD_LAG_2]=lagRate(a.first(),x[LEAD_LAG_2],data.tb1());
    }

    private Algebraic calculate(double[] x){
        double sensed=data.inputFilterTime()>EPS?x[SENSED_INPUT]:scaledInput();
        double actual=data.actualScale()*sensed;
        double ratio=Math.max(0,sensed/data.inverseReference());
        double inverse1=data.inverseGain1()*(Math.pow(ratio,data.inverseExponent1())-1);
        double inverse2=clamp(data.inverseGain2()*(Math.pow(ratio,data.inverseExponent2())-1),data.inverseMin(),data.inverseMax());
        double w=(sensed<=data.inverseReference()?data.fixedRampUp():data.fixedRampDown())+inverse2;
        double timerRate=w-data.timerFeedback()*x[TIMER];
        double timerError=data.timerReference()-x[TIMER];
        double up=data.rampMode()==1?data.rampUp():inverse1;
        double down=data.rampMode()==1?data.rampDown():inverse1;
        double ramp=timerError>=data.releaseThreshold()*data.timerReference()?up:timerError<=0?down:0;
        double filteredReference=data.referenceFilterTime()>EPS?x[REFERENCE_FILTER]:x[CURRENT_REFERENCE];
        double error=bias+filteredReference-actual;
        double derivative=data.td()>EPS?data.kd()/data.td()*error-x[DERIVATIVE]:0;
        double pidUnlimited=data.kp()*error+x[PI]+derivative;
        double pid=clamp(pidUnlimited,data.pidMin(),data.pidMax());
        double first=clamp(leadLag(pid,x[LEAD_LAG_1],data.tc2(),data.tb2()),data.leadLag1Min(),data.leadLag1Max());
        double result=clamp(leadLag(first,x[LEAD_LAG_2],data.tc1(),data.tb1()),data.outputMin(),data.outputMax());
        return new Algebraic(actual,error,inverse1,inverse2,w,timerRate,timerError,ramp,pidUnlimited,pid,first,result);
    }

    private double scaledInput(){
        double raw=switch(data.inputMode()){
            case 1->machine.getEfd();
            case 2->machine.calculateIfd(MachineIfdBase.EXCITER);
            default->machine.getEfd();
        };
        return data.inputScale()*raw;
    }
    private double inputSignal(){return Double.isFinite(validationInput)?validationInput:scaledInput();}
    private void ensureInputInitialized(){
        if(!inputInitializationPending)return;double sensed=inputSignal();if(Math.abs(sensed)<=EPS)return;
        state[SENSED_INPUT]=sensed;trial[SENSED_INPUT]=sensed;inputInitializationPending=false;
        output=calculate(active).output();exciter.setVoel(output);
    }
    private void limit(double[] x){
        x[CURRENT_REFERENCE]=clamp(x[CURRENT_REFERENCE],data.thermalLimit(),data.instantaneousLimit());
        x[TIMER]=clamp(x[TIMER],data.timerMin(),data.timerMax());
    }
    private static double lagRate(double input,double state,double time){return time>EPS?(input-state)/time:0;}
    private static double nonWindup(double rate,double state,double min,double max){return(state>=max&&rate>0)||(state<=min&&rate<0)?0:rate;}
    private static double leadLag(double input,double state,double lead,double lag){return lag>EPS?(lead/lag)*input+(1-lead/lag)*state:input;}
    private static double clamp(double value,double min,double max){return Math.max(min,Math.min(max,value));}
    private static void add(double[] x,double[] d,double dt,double[] y){for(int i=0;i<x.length;i++)y[i]=x[i]+dt*d[i];}
    private static boolean finite(double[] x){for(double v:x)if(!Double.isFinite(v))return false;return true;}

    @Override public double getOutput(){ensureInputInitialized();return output;}
    @Override public Object getOutputObject(){return Complex.ZERO;}
    @Override public boolean updateAttributes(boolean netChange){return true;}
    @Override public Hashtable<String,Object> getStates(Object ref){states.put("OEL2C_OUTPUT",output);return states;}
    @Override public Map<String,Double> getNamedStates(){ensureInputInitialized();Map<String,Double> result=new LinkedHashMap<>();
        result.put("PID integrator",active[PI]);result.put("PID derivative",active[DERIVATIVE]);
        result.put("First lead-lag",active[LEAD_LAG_1]);result.put("Second lead-lag",active[LEAD_LAG_2]);
        result.put("Reference filter",active[REFERENCE_FILTER]);result.put("Current reference",active[CURRENT_REFERENCE]);
        result.put("Sensed input",active[SENSED_INPUT]);result.put("Timer signal",active[TIMER]);return Map.copyOf(result);}
    public Oel2cData getData(){return data;} public double[] getStateSnapshot(){ensureInputInitialized();return active.clone();}
    public double getBias(){return bias;} public boolean isSet(){return set;}
    private record Algebraic(double actual,double error,double inverse1,double inverse2,double timerLogic,
            double timerRate,double timerError,double ramp,double pidUnlimited,double pid,double first,double output){}
}
