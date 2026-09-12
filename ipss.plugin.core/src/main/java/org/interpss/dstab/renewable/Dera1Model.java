package org.interpss.dstab.renewable;

import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.control.util.IntegrationStepAware;

import com.interpss.common.exp.InterpssRuntimeException;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.dstab.controller.cml.ICMLStateProvider;
import com.interpss.dstab.device.DynamicGenDevice;
import com.interpss.dstab.device.impl.DynamicBusDeviceImpl;

/** Published aggregate distributed-energy-resource generator/converter model. */
public final class Dera1Model extends DynamicBusDeviceImpl
        implements DynamicGenDevice, IntegrationStepAware, ICMLStateProvider {
    private static final double EPS = 1.0e-9;
    private static final int V=0, P=1, Q_CURRENT=2, IQ=3, MULTIPLIER=4,
            FREQUENCY=5, POWER_PI=6, RATE=7, P_ORDER=8, IP=9;

    private final Dera1Data data;
    private final Hashtable<String,Object> outputStates = new Hashtable<>();
    private final double[] state = new double[10];
    private final double[] trial = new double[10];
    private final double[] oldDerivative = new double[10];
    private double[] active = state;
    private DStabGen parentGen;
    private double systemBaseMva;
    private double deviceBaseMva;
    private double p;
    private double q;
    private double pref;
    private double qref;
    private double pfAngle;
    private double vref;
    private double frequencyReference;
    private double integrationStep;
    private double lowTimer;
    private double lowFractionTimer;
    private double highTimer;
    private double highFractionTimer;
    private double lowFrequencyTimer;
    private double highFrequencyTimer;
    private double vmin;
    private double vmax;
    private boolean lowFractionLatched;
    private boolean highFractionLatched;
    private boolean lowTripLatched;
    private boolean highTripLatched;
    private boolean frequencyTripLatched;
    private boolean lowVoltageTripEnabled;
    private boolean highVoltageTripEnabled;
    private boolean lowFrequencyTripEnabled;
    private boolean highFrequencyTripEnabled;

    public Dera1Model(DStabGen parentGen, BaseDStabBus<?,?> bus, String id, Dera1Data data) {
        if (parentGen == null || bus == null || data == null) {
            throw new IllegalArgumentException("DERA1 requires a generator, bus, and data");
        }
        this.data = data;
        setParentGen(parentGen);
        setDStabBus(bus);
        setId(id);
        setExtendedDeviceId("DERA1_" + id + "@" + bus.getId());
    }

    @Override public void configureIntegrationStep(double timeStepSec) { integrationStep=timeStepSec; }

    @Override
    public boolean initStates(BaseDStabBus<?,?> bus) {
        if (bus == null || parentGen == null || parentGen.getGen() == null) return false;
        systemBaseMva = bus.getNetwork().getBaseMva();
        deviceBaseMva = parentGen.getMvaBase() > EPS ? parentGen.getMvaBase() : systemBaseMva;
        double scale = systemBaseMva / deviceBaseMva;
        Complex initial = parentGen.getGen();
        initializeWithSignals(bus.getVoltageMag(), initial.getReal()*scale,
                initial.getImaginary()*scale, 1.0);
        outputStates.put(DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID, getExtendedDeviceId());
        return finite(state);
    }

    /** Initializes the ten published states from prescribed device-base signals. */
    public void initializeWithSignals(double voltage, double realPower,
            double reactivePower, double frequencyPu) {
        double safeVoltage = nonzero(voltage);
        pref = realPower;
        qref = reactivePower;
        pfAngle = Math.atan2(reactivePower, realPower);
        frequencyReference = 0.0;
        double requestedVref = data.vref0();
        double initialError = requestedVref - voltage;
        vref = requestedVref <= 0.0 || (data.kqv() > 0.0
                && (initialError < data.dbd1() || initialError > data.dbd2()))
                ? voltage : requestedVref;
        state[V] = voltage;
        state[P] = realPower;
        state[Q_CURRENT] = reactivePower / safeVoltage;
        state[IQ] = reactivePower / safeVoltage;
        state[MULTIPLIER] = 1.0;
        state[FREQUENCY] = frequencyPu - 1.0;
        state[POWER_PI] = realPower;
        state[RATE] = realPower;
        state[P_ORDER] = realPower;
        state[IP] = realPower / safeVoltage;
        p = realPower;
        q = reactivePower;
        resetProtection(voltage, frequencyPu);
        System.arraycopy(state,0,trial,0,state.length);
        active=state;
    }

    private void resetProtection(double voltage,double frequencyPu) {
        lowTimer=lowFractionTimer=highTimer=highFractionTimer=0.0;
        lowFrequencyTimer=highFrequencyTimer=0.0;
        lowFractionLatched=highFractionLatched=lowTripLatched=highTripLatched=false;
        frequencyTripLatched=false;
        vmin=data.vl1();vmax=data.vh1();
        lowVoltageTripEnabled=data.vtripFlag()==1&&data.vl0()<=voltage&&data.vl1()<=voltage;
        highVoltageTripEnabled=data.vtripFlag()==1&&data.vh0()>=voltage&&data.vh1()>=voltage;
        double hz=frequencyPu*getDStabBus().getNetwork().getFrequency();
        lowFrequencyTripEnabled=data.ftripFlag()==1&&data.fl()<=hz;
        highFrequencyTripEnabled=data.ftripFlag()==1&&data.fh()>=hz;
    }

    @Override public boolean nextStep(double dt,DynamicSimuMethod method,int flag) {
        Signals signals=currentSignals();
        return nextStepWithSignals(dt,method,flag,signals.voltage,signals.p,signals.q,signals.frequency);
    }

    public boolean nextStepWithSignals(double dt,DynamicSimuMethod method,int flag,
            double voltage,double realPower,double reactivePower,double frequencyPu) {
        if(method!=DynamicSimuMethod.MODIFIED_EULER)
            throw new InterpssRuntimeException("DERA1 supports MODIFIED_EULER only");
        if(dt<0||!Double.isFinite(dt))return false;
        Signals signals=new Signals(voltage,realPower,reactivePower,frequencyPu);
        if(flag==0){
            derivatives(state,signals,oldDerivative);
            for(int i=0;i<state.length;i++)trial[i]=state[i]+dt*oldDerivative[i];
            normalize(trial,signals);active=trial;
        }else if(flag==1){
            double[] corrected=new double[state.length];derivatives(trial,signals,corrected);
            for(int i=0;i<state.length;i++)state[i]+=.5*dt*(oldDerivative[i]+corrected[i]);
            normalize(state,signals);active=state;
        }else throw new InterpssRuntimeException("DERA1 invalid integration flag: "+flag);
        return finite(active);
    }

    private void derivatives(double[] x,Signals s,double[] dx) {
        java.util.Arrays.fill(dx,0.0);
        Algebraic a=algebraic(x,s);
        dx[V]=lagRate(s.voltage,x[V],data.trv());
        dx[P]=lagRate(x[P_ORDER],x[P],data.tp());
        dx[Q_CURRENT]=lagRate(a.selectedQ/nonzero(a.filteredVoltage),x[Q_CURRENT],data.tiq());
        dx[IQ]=lagRate(a.limitedIqCommand*x[MULTIPLIER],x[IQ],data.tg());
        dx[MULTIPLIER]=lagRate(a.tripTarget,x[MULTIPLIER],data.tv());
        dx[FREQUENCY]=lagRate(s.frequency-1.0,x[FREQUENCY],data.trf());
        dx[POWER_PI]=data.kig()*a.powerError;
        dx[RATE]=clamp(a.selectedPower-x[RATE],data.dpmin(),data.dpmax());
        dx[P_ORDER]=lagRate(clamp(x[RATE],data.pmin(),data.pmax()),x[P_ORDER],data.tpord());
        double ipRate=lagRate(a.limitedIpCommand*x[MULTIPLIER],x[IP],data.tg());
        dx[IP]=data.rrpwr()>0?clamp(ipRate,-data.rrpwr(),data.rrpwr()):ipRate;
    }

    private Algebraic algebraic(double[] x,Signals s) {
        double filteredVoltage=data.trv()>EPS?x[V]:s.voltage;
        double measuredPower=data.tp()>EPS?x[P]:s.p;
        double selectedQ=data.pfFlag()==1?measuredPower*Math.tan(pfAngle):qref;
        double qCurrent=data.tiq()>EPS?x[Q_CURRENT]:selectedQ/nonzero(filteredVoltage);
        double voltageError=deadband(vref-filteredVoltage,data.dbd1(),data.dbd2());
        double iqInjection=clamp(data.kqv()*voltageError,data.iqll(),data.iqhl());
        double rawIq=qCurrent+iqInjection;
        double filteredFrequency=data.trf()>EPS?x[FREQUENCY]:s.frequency-1.0;
        double frequencyError=deadband(frequencyReference-filteredFrequency,
                data.fdbd1(),data.fdbd2());
        double droop=frequencyError>=0?data.ddn()*frequencyError:data.dup()*frequencyError;
        double powerError=clamp(pref+droop-measuredPower,data.femin(),data.femax());
        double piOutput=data.kpg()*powerError+x[POWER_PI];
        double selectedPower=data.freqFlag()==1?piOutput:pref;
        double pOrder=clamp(data.tpord()>EPS?x[P_ORDER]:clamp(x[RATE],data.pmin(),data.pmax()),
                data.pmin(),data.pmax());
        double rawIp=pOrder/nonzero(filteredVoltage);
        CurrentCommands limited=limitCurrent(rawIp,rawIq);
        double tripTarget=voltageMultiplierTarget(filteredVoltage)
                *frequencyMultiplierTarget(filteredVoltage,s.frequency);
        return new Algebraic(filteredVoltage,selectedQ,powerError,selectedPower,
                limited.ip,limited.iq,tripTarget);
    }

    private CurrentCommands limitCurrent(double ip,double iq) {
        double ipMin=data.genFlag()==1?0.0:-data.imax();
        if(data.pqFlag()==1){
            double limitedIp=clamp(ip,ipMin,data.imax());
            double iqMax=Math.sqrt(Math.max(0,data.imax()*data.imax()-limitedIp*limitedIp));
            return new CurrentCommands(limitedIp,clamp(iq,-iqMax,iqMax));
        }
        double limitedIq=clamp(iq,-data.imax(),data.imax());
        double ipMax=Math.sqrt(Math.max(0,data.imax()*data.imax()-limitedIq*limitedIq));
        return new CurrentCommands(clamp(ip,Math.max(ipMin,-ipMax),ipMax),limitedIq);
    }

    private void normalize(double[] x,Signals s) {
        if(data.trv()<=EPS)x[V]=s.voltage;if(data.tp()<=EPS)x[P]=s.p;
        Algebraic a=algebraic(x,s);
        if(data.tiq()<=EPS)x[Q_CURRENT]=a.selectedQ/nonzero(a.filteredVoltage);
        if(data.tv()<=EPS)x[MULTIPLIER]=a.tripTarget;
        if(data.trf()<=EPS)x[FREQUENCY]=s.frequency-1.0;
        if(data.tpord()<=EPS)x[P_ORDER]=clamp(x[RATE],data.pmin(),data.pmax());
        x[P_ORDER]=clamp(x[P_ORDER],data.pmin(),data.pmax());
    }

    @Override public boolean afterStep(double dt) {
        if(dt<0||!Double.isFinite(dt))return false;
        updateVoltageProtection(state[V],dt);
        updateFrequencyProtection(state[V],currentSignals().frequency,dt);
        return true;
    }

    private void updateVoltageProtection(double voltage,double dt) {
        if(lowVoltageTripEnabled){
            lowTimer=voltage<data.vl0()?lowTimer+dt:0;
            lowFractionTimer=voltage<data.vl1()?lowFractionTimer+dt:0;
            if(lowTimer+EPS>=data.tvl0())lowTripLatched=true;
            if(lowFractionTimer+EPS>=data.tvl1())lowFractionLatched=true;
            if(lowFractionLatched)vmin=Math.max(data.vl0(),Math.min(vmin,voltage));
        }
        if(highVoltageTripEnabled){
            highTimer=voltage>data.vh0()?highTimer+dt:0;
            highFractionTimer=voltage>data.vh1()?highFractionTimer+dt:0;
            if(highTimer+EPS>=data.tvh0())highTripLatched=true;
            if(highFractionTimer+EPS>=data.tvh1())highFractionLatched=true;
            if(highFractionLatched)vmax=Math.min(data.vh0(),Math.max(vmax,voltage));
        }
    }

    private void updateFrequencyProtection(double voltage,double frequencyPu,double dt) {
        if(data.ftripFlag()==0||frequencyTripLatched)return;
        if(voltage<data.vpr()){lowFrequencyTimer=highFrequencyTimer=0;return;}
        double hz=frequencyPu*getDStabBus().getNetwork().getFrequency();
        lowFrequencyTimer=lowFrequencyTripEnabled&&hz<data.fl()?lowFrequencyTimer+dt:0;
        highFrequencyTimer=highFrequencyTripEnabled&&hz>data.fh()?highFrequencyTimer+dt:0;
        if(lowFrequencyTimer+EPS>=data.tfl()||highFrequencyTimer+EPS>=data.tfh())
            frequencyTripLatched=true;
    }

    private double voltageMultiplierTarget(double voltage) {
        return lowVoltageMultiplier(voltage)*highVoltageMultiplier(voltage);
    }
    private double lowVoltageMultiplier(double voltage) {
        if(!lowVoltageTripEnabled)return 1;if(lowTripLatched||voltage<=data.vl0())return 0;
        double span=data.vl1()-data.vl0();if(span<=EPS)return voltage<data.vl0()?0:1;
        if(!lowFractionLatched)return voltage>=data.vl1()?1:clamp((voltage-data.vl0())/span,0,1);
        double recovery=Math.min(voltage,data.vl1());
        if(recovery<=vmin)return clamp((recovery-data.vl0())/span,0,1);
        return clamp((vmin-data.vl0()+data.vrfrac()*(recovery-vmin))/span,0,1);
    }
    private double highVoltageMultiplier(double voltage) {
        if(!highVoltageTripEnabled)return 1;if(highTripLatched||voltage>=data.vh0())return 0;
        double span=data.vh0()-data.vh1();if(span<=EPS)return voltage>data.vh0()?0:1;
        if(!highFractionLatched)return voltage<=data.vh1()?1:clamp((data.vh0()-voltage)/span,0,1);
        double recovery=Math.max(voltage,data.vh1());
        if(recovery>=vmax)return clamp((data.vh0()-recovery)/span,0,1);
        return clamp((data.vh0()-vmax+data.vrfrac()*(vmax-recovery))/span,0,1);
    }
    private double frequencyMultiplierTarget(double voltage,double frequencyPu) {
        if(data.ftripFlag()==0||voltage<data.vpr())return 1;
        return frequencyTripLatched?0:1;
    }

    private Signals currentSignals() {
        BaseDStabBus<?,?> bus=getDStabBus();
        return new Signals(bus.getVoltageMag(),p,q,bus.getFreq());
    }

    private Complex injectedCurrent(Complex voltage) {
        double scale=deviceBaseMva/systemBaseMva;double theta=voltage.getArgument();
        double ip=active[IP];double iq=active[IQ];
        return new Complex(scale*(ip*Math.cos(theta)+iq*Math.sin(theta)),
                scale*(ip*Math.sin(theta)-iq*Math.cos(theta)));
    }

    @Override public Object getOutputObject() {
        Complex voltage=getDStabBus().getVoltage();Complex injected=injectedCurrent(voltage);
        Complex z=parentGen.getPosGenZ();
        if(z!=null){z=z.multiply(parentGen.getZMultiFactor());if(z.abs()>EPS)injected=injected.add(voltage.divide(z));}
        return injected;
    }
    @Override public boolean updateAttributes(boolean netChange) {
        if(systemBaseMva<=EPS||deviceBaseMva<=EPS)return false;
        Complex power=getDStabBus().getVoltage().multiply(injectedCurrent(getDStabBus().getVoltage()).conjugate())
                .multiply(systemBaseMva/deviceBaseMva);
        if(netChange){p=.5*(p+power.getReal());q=.5*(q+power.getImaginary());}
        else{p=power.getReal();q=power.getImaginary();}
        return Double.isFinite(p)&&Double.isFinite(q);
    }
    @Override public Hashtable<String,Object> getStates(Object ref) {
        outputStates.put("DERA1_P",p);outputStates.put("DERA1_Q",q);
        outputStates.put("DERA1_TRIPPED_FRACTION",1-active[MULTIPLIER]);return outputStates;
    }
    @Override public Map<String,Double> getNamedStates() {
        Map<String,Double> result=new LinkedHashMap<>();
        result.put("Voltage measurement lag",active[V]);
        result.put("Generator power measurement lag",active[P]);
        result.put("Reactive current lag",active[Q_CURRENT]);
        result.put("Reactive inner-current lag",active[IQ]);
        result.put("Trip multiplier lag",active[MULTIPLIER]);
        result.put("Frequency measurement lag",active[FREQUENCY]);
        result.put("Real-power PI integrator",active[POWER_PI]);
        result.put("Power-reference rate limiter",active[RATE]);
        result.put("Power-order lag",active[P_ORDER]);
        result.put("Active inner-current lag",active[IP]);
        return Map.copyOf(result);
    }
    @Override public DStabGen getParentGen(){return parentGen;}
    @Override public void setParentGen(DStabGen gen){parentGen=gen;if(gen!=null)gen.setDynamicGenDevice(this);}
    public Dera1Data getData(){return data;}public double[] getStateSnapshot(){return active.clone();}
    public double getTripMultiplier(){return active[MULTIPLIER];}
    public double getP(){return p;}
    public double getQ(){return q;}
    public double getIpcmd(double voltage,double realPower,double reactivePower,double frequencyPu){return algebraic(active,new Signals(voltage,realPower,reactivePower,frequencyPu)).limitedIpCommand;}
    public double getIqcmd(double voltage,double realPower,double reactivePower,double frequencyPu){return algebraic(active,new Signals(voltage,realPower,reactivePower,frequencyPu)).limitedIqCommand;}

    private static double deadband(double value,double low,double high){return value<low?value-low:value>high?value-high:0;}
    private static double lagRate(double input,double state,double time){return time>EPS?(input-state)/time:0;}
    private static double nonzero(double value){return Math.max(.01,Math.abs(value));}
    private static double clamp(double value,double min,double max){return Math.max(min,Math.min(max,value));}
    private static boolean finite(double[] values){for(double value:values)if(!Double.isFinite(value))return false;return true;}
    private record Signals(double voltage,double p,double q,double frequency){}
    private record CurrentCommands(double ip,double iq){}
    private record Algebraic(double filteredVoltage,double selectedQ,double powerError,
            double selectedPower,double limitedIpCommand,double limitedIqCommand,double tripTarget){}
}
