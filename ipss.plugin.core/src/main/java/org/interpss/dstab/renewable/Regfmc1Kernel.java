package org.interpss.dstab.renewable;

import static org.interpss.dstab.renewable.Regfmc1Data.Parameter.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import com.interpss.dstab.controller.cml.ICMLStateProvider;

/** Native 36.7 REGFMC1U eleven-state hybrid controller, without parser/device attachment.
 * Powers, currents and ZSOURCE use machine base; angles use the network reference.
 * Feedback uses the raw GFM branch, not the jointly current-limited network injection. */
public final class Regfmc1Kernel implements ICMLStateProvider {
    private static final List<String> NAMES=List.of("Vmeas","GfmVrefMeas","GfmIqMeas","VoltageIntegral",
            "GfmInternalVoltage","SpeedMeas","SpeedReferenceMeas","GfmPmeas","VsmSpeedDeviation","DampingFilter","GfmAngle");
    private final Regfmc1Data data;
    private final double omegaBase;
    private final double[] x=new double[11];
    private double[] old,first;
    private double predictorStep;
    private double initialP,initialQ,initialVoltage,gflVoltageReference,voltageTime;
    private double pStep,qStep,vStep,wStep,gfmPower,gfmIq;
    private boolean initialized;

    public Regfmc1Kernel(Regfmc1Data data,double frequencyHz) {
        this.data=Objects.requireNonNull(data);
        if(!Double.isFinite(frequencyHz)||frequencyHz<=0)throw new IllegalArgumentException("Positive base frequency required");
        omegaBase=2*Math.PI*frequencyHz;
    }
    public void initialize(Complex terminalVoltage,Complex machinePower,double nominalStep) {
        finite(terminalVoltage);finite(machinePower);
        double voltage=terminalVoltage.abs();
        if(voltage<=0)throw new IllegalArgumentException("REGFMC1U initial voltage must be positive");
        double te=data.internalVoltageTimeConstant(nominalStep),vref=data.gflVoltageReference(voltage);
        if(value(DEMIN)>0||value(DEMAX)<0||value(DWMIN)>0||value(DWMAX)<0)
            throw new IllegalArgumentException("REGFMC1U equilibrium requires zero within PI/speed bounds");
        var gfl=Regfmc1NetworkBoundary.gridFollowing(data,machinePower.getReal(),machinePower.getImaginary(),voltage,vref,terminalVoltage.getArgument());
        Complex actual=terminalVoltage.multiply(gfl.current().conjugate());
        if(actual.subtract(machinePower).abs()>1e-9||gfl.current().abs()>value(IMAX))
            throw new IllegalArgumentException("REGFMC1U initial GFL power must be feasible without clipping or voltage injection");
        Arrays.fill(x,0);old=first=null;pStep=qStep=vStep=wStep=gfmPower=gfmIq=0;
        initialP=machinePower.getReal();initialQ=machinePower.getImaginary();initialVoltage=voltage;
        gflVoltageReference=vref;voltageTime=te;
        x[0]=x[1]=x[4]=voltage;x[5]=x[6]=data.enabled(Regfmc1Data.Flag.FFLAG)?1:0;x[10]=terminalVoltage.getArgument();initialized=true;
    }
    /** Plant P/Q/V/speed deviations, followed by the native manual GFM P and Iq inputs.
     * GFM steady-state P/Iq default to zero, independently of GFL power references. */
    public void setReferenceSteps(double activeGfl,double reactiveGfl,double voltage,double speedDeviation,
                                  double activeGfm,double reactiveGfmCurrent) {
        requireInitialized();finite(activeGfl,reactiveGfl,voltage,speedDeviation,activeGfm,reactiveGfmCurrent);
        pStep=activeGfl;qStep=reactiveGfl;vStep=voltage;wStep=speedDeviation;gfmPower=activeGfm;gfmIq=reactiveGfmCurrent;
    }
    public void step(double dt,Complex terminalVoltage){step(dt,terminalVoltage,0);step(dt,terminalVoltage,1);}
    public void step(double dt,Complex terminalVoltage,int stage) {
        requireInitialized();finite(dt);finite(terminalVoltage);
        if(dt<0||stage<0||stage>1)throw new IllegalArgumentException("REGFMC1U requires dt >= 0 and stage 0/1");
        if(dt==0){refresh(terminalVoltage);return;}
        if(stage==1&&old==null)throw new IllegalStateException("REGFMC1U corrector without predictor");
        if(stage==1&&dt!=predictorStep)throw new IllegalArgumentException("REGFMC1U predictor/corrector steps must match");
        refresh(terminalVoltage);
        if(stage==0){predictorStep=dt;old=x.clone();first=rates(terminalVoltage);advance(old,first,dt);}
        else {
            double[] end=rates(terminalVoltage),average=new double[11];
            for(int i=0;i<11;i++)average[i]=.5*(first[i]+end[i]);
            advance(old,average,dt);old=first=null;
        }
        refresh(terminalVoltage);finite(x);
    }
    private double[] rates(Complex terminal) {
        var feedback=feedback(terminal);double error=reactiveCommand()-x[2];
        double droop=clip((x[6]-x[5])/value(MP),value(DPGFMMIN),value(DPGFMMAX));
        double damping=clip(value(D1)*x[8]+value(D2)*x[8]-x[9],value(DPDMIN),value(DPDMAX));
        double correction=clip(x[3]+value(KPE)*error,value(DEMIN),value(DEMAX));
        double[] rate=new double[11];
        rate[0]=lag(x[0],terminal.abs(),value(TVF));rate[1]=lag(x[1],initialVoltage+vStep,value(TVR));
        rate[2]=lag(x[2],feedback.iq,value(TIF));
        rate[3]=boundedRate(x[3],value(KIE)*error,value(DEMIN),value(DEMAX));
        rate[4]=(x[1]+correction-x[4])/voltageTime;
        // Native 36.7 leaves both unused frequency-filter states at zero when Fflag=0.
        if(data.enabled(Regfmc1Data.Flag.FFLAG)) {
            rate[5]=lag(x[5],1+x[8],value(TWM));rate[6]=lag(x[6],1+wStep,value(TWREF));
        }
        rate[7]=lag(x[7],feedback.p,value(TPF));
        rate[8]=boundedRate(x[8],(gfmPower+(data.enabled(Regfmc1Data.Flag.FFLAG)?droop:0)-x[7]-damping)/(2*value(H)),value(DWMIN),value(DWMAX));
        // The washout stores the low-pass of D2*speed, in its published coordinate.
        rate[9]=value(WD)*(value(D2)*x[8]-x[9]);rate[10]=omegaBase*x[8];
        return rate;
    }
    private void advance(double[] start,double[] rate,double dt) {
        for(int i=0;i<11;i++)x[i]=start[i]+dt*rate[i];
        x[3]=clip(x[3],value(DEMIN),value(DEMAX));x[8]=clip(x[8],value(DWMIN),value(DWMAX));
    }
    private void refresh(Complex terminal) {
        if(value(TVF)==0)x[0]=terminal.abs();if(value(TVR)==0)x[1]=initialVoltage+vStep;
        if(data.enabled(Regfmc1Data.Flag.FFLAG)) {
            if(value(TWM)==0)x[5]=1+x[8];if(value(TWREF)==0)x[6]=1+wStep;
        }
        Feedback f=feedback(terminal);if(value(TIF)==0)x[2]=f.iq;if(value(TPF)==0)x[7]=f.p;
    }
    private record Feedback(double p,double iq){}
    private Feedback feedback(Complex terminal) {
        Complex current=rawInternalVoltage().subtract(terminal).divide(data.sourceImpedance());
        Complex dq=current.multiply(polar(1,-terminal.getArgument()));
        return new Feedback(terminal.multiply(current.conjugate()).getReal(),-dq.getImaginary());
    }
    public double reactiveCommand(){requireInitialized();return gfmIq+(data.enabled(Regfmc1Data.Flag.VFLAG)?(x[1]-x[0])/value(MQ):0);}
    public Complex rawInternalVoltage(){requireInitialized();return polar(x[4],x[10]);}
    public Regfmc1NetworkBoundary.Hybrid boundary(Complex terminal) {
        requireInitialized();finite(terminal);
        var gfl=Regfmc1NetworkBoundary.gridFollowing(data,initialP+pStep,initialQ+qStep,x[0],gflVoltageReference,terminal.getArgument());
        return Regfmc1NetworkBoundary.hybrid(data,rawInternalVoltage(),terminal,gfl.current());
    }
    public double speedDeviation(){requireInitialized();return x[8];}
    public double[] stateSnapshot(){requireInitialized();return x.clone();}
    @Override public Map<String,Double> getNamedStates(){requireInitialized();Map<String,Double> states=new LinkedHashMap<>();for(int i=0;i<11;i++)states.put(NAMES.get(i),x[i]);return Collections.unmodifiableMap(states);}
    private double value(Regfmc1Data.Parameter p){return data.value(p);}
    private static double lag(double state,double input,double time){return time==0?0:(input-state)/time;}
    private static double boundedRate(double state,double rate,double lower,double upper){return state>=upper&&rate>0||state<=lower&&rate<0?0:rate;}
    private static double clip(double v,double lower,double upper){return Math.max(lower,Math.min(upper,v));}
    private static Complex polar(double r,double angle){return new Complex(Math.cos(angle),Math.sin(angle)).multiply(r);}
    private static void finite(double... values){for(double v:values)if(!Double.isFinite(v))throw new IllegalArgumentException("REGFMC1U requires finite values");}
    private static void finite(Complex value){Objects.requireNonNull(value);finite(value.getReal(),value.getImaginary());}
    private void requireInitialized(){if(!initialized)throw new IllegalStateException("REGFMC1U is not initialized");}
}
