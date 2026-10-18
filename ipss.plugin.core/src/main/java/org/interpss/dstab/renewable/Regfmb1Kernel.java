package org.interpss.dstab.renewable;

import static org.interpss.dstab.renewable.Regfmb1Data.Flag.*;
import static org.interpss.dstab.renewable.Regfmb1Data.Parameter.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import com.interpss.dstab.controller.cml.ICMLStateProvider;

/** Fourteen-state native REGFMB1U kernel. No DYR alias or device attachment.
 * All measurements, powers and currents use machine MVA base. */
public final class Regfmb1Kernel implements ICMLStateProvider {
    private static final List<String> NAMES=List.of("Pdroop","VsmSpeedDeviation","DampingFilter",
            "InternalRelativeAngle","Pmeas","PllIntegral","PllAngle","Idmeas","Qmeas","Vmeas",
            "Iqmeas","VoltageIntegral","AngleUpper","AngleLower");
    public record Terminal(Complex voltage,Complex current) {
        public Terminal {finite(voltage);finite(current);}
        public double p(){return voltage.multiply(current.conjugate()).getReal();}
        public double q(){return voltage.multiply(current.conjugate()).getImaginary();}
        public static Terminal fromPower(Complex voltage,Complex machinePower) {
            finite(voltage);finite(machinePower);
            if(voltage.abs()==0)throw new IllegalArgumentException("Use actual current at zero voltage");
            return new Terminal(voltage,machinePower.divide(voltage).conjugate());
        }
    }
    private final Regfmb1Data data;
    private final double omegaBase;
    private final double[] x=new double[14];
    private double[] old,first;
    private double initialP,initialQ,initialVref,deltaMax,pStep,qvStep,frequencyStep;
    private boolean initialized;
    public Regfmb1Kernel(Regfmb1Data data,double frequencyHz) {
        this.data=Objects.requireNonNull(data);
        if(!Double.isFinite(frequencyHz)||frequencyHz<=0)throw new IllegalArgumentException("Positive base frequency required");
        omegaBase=2*Math.PI*frequencyHz;
    }
    public void initialize(Terminal terminal) {
        if(terminal.voltage.abs()==0)throw new IllegalArgumentException("REGFMB1U initial voltage must be positive");
        if(terminal.current.abs()>=data.steadyCurrentLimit())throw new IllegalArgumentException("REGFMB1U initial current must be below ImaxSS");
        Arrays.fill(x,0);old=first=null;pStep=qvStep=frequencyStep=0;
        Complex v=terminal.voltage,e=v.add(data.sourceImpedance().multiply(terminal.current));
        x[3]=Math.atan2(Math.sin(e.getArgument()-v.getArgument()),Math.cos(e.getArgument()-v.getArgument()));
        x[4]=initialP=terminal.p();x[6]=v.getArgument();
        Complex dq=Regfmb1NetworkBoundary.toDq(terminal.current,x[6]);
        x[7]=dq.getReal();x[8]=initialQ=terminal.q();x[9]=v.abs();x[10]=dq.getImaginary();x[11]=e.abs();
        initialVref=x[9]+(data.enabled(QVFLAG)?value(MQ)*(data.currentDroop()?-x[10]:x[8]):0);
        deltaMax=Math.asin(Math.min(1,data.sourceImpedance().getImaginary()*data.steadyCurrentLimit()));
        x[12]=deltaMax;x[13]=data.enabled(ESFLAG)?-deltaMax:0;initialized=true;
    }
    public void setReferenceSteps(double activePower,double reactiveOrVoltage,double frequencyDeviation) {
        requireInitialized();finite(activePower,reactiveOrVoltage,frequencyDeviation);pStep=activePower;qvStep=reactiveOrVoltage;frequencyStep=frequencyDeviation;
    }
    public void step(double dt,Terminal terminal) {step(dt,terminal,0);step(dt,terminal,1);}
    public void step(double dt,Terminal terminal,int stage) {
        requireInitialized();Objects.requireNonNull(terminal);finite(dt);
        if(dt<0||stage<0||stage>1)throw new IllegalArgumentException("REGFMB1U requires dt >= 0 and stage 0/1");
        if(dt==0){refresh(terminal);return;}
        if(stage==1&&old==null)throw new IllegalStateException("REGFMB1U corrector without predictor");
        if(stage==0) {
            refresh(terminal);old=x.clone();first=rates(terminal);advance(old,first,dt,terminal);
        } else {
            refresh(terminal);double[] end=rates(terminal),average=new double[14];
            for(int i=0;i<14;i++)average[i]=.5*(first[i]+end[i]);
            advance(old,average,dt,terminal);old=first=null;
        }
        refresh(terminal);
        finite(x);
    }
    private double[] rates(Terminal t) {
        Signals s=signals(t);double[] rate=new double[14];
        rate[0]=lag(x[0],droopInput(s.pll),value(TP));
        double damping=value(D1)*x[1]+value(D2)*x[1]-x[2];
        rate[1]=boundedRate(x[1],(initialP+pStep+(data.enabled(FFLAG)?x[0]:0)-x[4]-damping)/(2*value(H)),value(DWMIN),value(DWMAX));
        // Native washout stores the low-pass of D2*speed, not speed itself.
        rate[2]=value(WD)*(value(D2)*x[1]-x[2]);
        rate[3]=boundedRate(x[3],omegaBase*(x[1]-s.pll),data.enabled(ESFLAG)?x[13]:0,x[12]);
        rate[4]=lag(x[4],t.p(),value(TPF));
        rate[5]=pllFrozen(t)?0:boundedRate(x[5],value(KIPLL)*s.vq,value(DWPLLMIN),value(DWPLLMAX));
        rate[6]=pllFrozen(t)?0:omegaBase*s.pll;
        Complex current=Regfmb1NetworkBoundary.toDq(t.current,x[6]);
        rate[7]=lag(x[7],current.getReal(),value(TIF));rate[8]=lag(x[8],t.q(),value(TQF));
        rate[9]=lag(x[9],t.voltage.abs(),value(TVF));rate[10]=lag(x[10],current.getImaginary(),value(TIF));
        rate[11]=boundedRate(x[11],value(KIV)*s.voltageError,s.limits.emin(),s.limits.emax());
        rate[12]=boundedRate(x[12],value(KI)*(s.limits.idmax()-x[7]),0,deltaMax);
        rate[13]=data.enabled(ESFLAG)?boundedRate(x[13],value(KI)*(-value(KE)*s.limits.idmax()-x[7]),-deltaMax,0):0;
        return rate;
    }
    private void advance(double[] start,double[] rate,double dt,Terminal t) {
        for(int i=0;i<14;i++)x[i]=start[i]+dt*rate[i];
        x[1]=clip(x[1],value(DWMIN),value(DWMAX));x[5]=clip(x[5],value(DWPLLMIN),value(DWPLLMAX));
        x[12]=clip(x[12],0,deltaMax);x[13]=clip(x[13],-deltaMax,0);
        x[3]=clip(x[3],data.enabled(ESFLAG)?x[13]:0,x[12]);
        var limits=Regfmb1NetworkBoundary.limits(data,x[7],x[10],x[9]);x[11]=clip(x[11],limits.emin(),limits.emax());
    }
    private void refresh(Terminal t) {
        if(value(TPF)==0)x[4]=t.p();if(value(TQF)==0)x[8]=t.q();if(value(TVF)==0)x[9]=t.voltage.abs();
        if(value(TIF)==0){Complex dq=Regfmb1NetworkBoundary.toDq(t.current,x[6]);x[7]=dq.getReal();x[10]=dq.getImaginary();}
        if(value(TP)==0)x[0]=droopInput(signals(t).pll);
    }
    private double droopInput(double pll) {return (frequencyStep-(data.enabled(WFLAG)?pll:x[1]))/value(MP);}
    private Signals signals(Terminal t) {
        double vq=Regfmb1NetworkBoundary.toDq(t.voltage,x[6]).getImaginary();
        // Native low-voltage logic zeroes the proportional input; it does not
        // hold the previous complete PI output. The integral and angle freeze.
        double pll=clip(x[5]+(pllFrozen(t)?0:value(KPPLL)*vq),value(DWPLLMIN),value(DWPLLMAX));
        double reactive=data.currentDroop()?x[10]:data.enabled(QVFLAG)?-x[8]:initialQ+qvStep-x[8];
        double error=initialVref+(data.enabled(QVFLAG)?qvStep:0)+value(MQ)*reactive-x[9];
        var limits=Regfmb1NetworkBoundary.limits(data,x[7],x[10],x[9]);
        return new Signals(pll,vq,error,clip(x[11]+value(KPV)*error,limits.emin(),limits.emax()),limits);
    }
    public Complex internalVoltage(Terminal t) {
        requireInitialized();double angle=x[3]+x[6],magnitude=signals(t).voltage;
        return new Complex(Math.cos(angle),Math.sin(angle)).multiply(magnitude);
    }
    public Complex current(Terminal t){return Regfmb1NetworkBoundary.current(data,internalVoltage(t),t.voltage);}
    public double speedDeviation(){requireInitialized();return x[1];}
    public double pllSpeedDeviation(Terminal t){requireInitialized();return signals(t).pll;}
    public double[] stateSnapshot(){requireInitialized();return x.clone();}
    @Override public Map<String,Double> getNamedStates(){requireInitialized();Map<String,Double> states=new LinkedHashMap<>();for(int i=0;i<14;i++)states.put(NAMES.get(i),x[i]);return Collections.unmodifiableMap(states);}
    private record Signals(double pll,double vq,double voltageError,double voltage,Regfmb1NetworkBoundary.Limits limits){}
    private double value(Regfmb1Data.Parameter p){return data.value(p);}
    private boolean pllFrozen(Terminal t){return data.pllFreezeVoltage()>0&&t.voltage.abs()<data.pllFreezeVoltage();}
    private static double lag(double state,double input,double time){return time==0?0:(input-state)/time;}
    private static double boundedRate(double state,double rate,double lower,double upper){return state>=upper&&rate>0||state<=lower&&rate<0?0:rate;}
    private static double clip(double value,double lower,double upper){return Math.max(lower,Math.min(upper,value));}
    private static void finite(double... values){for(double v:values)if(!Double.isFinite(v))throw new IllegalArgumentException("REGFMB1U requires finite values");}
    private static void finite(Complex value){Objects.requireNonNull(value);finite(value.getReal(),value.getImaginary());}
    private void requireInitialized(){if(!initialized)throw new IllegalStateException("REGFMB1U is not initialized");}
}
