package org.interpss.dstab.control.oel.psse.oel4c;

import java.util.Map;
import java.util.Objects;
import com.interpss.dstab.controller.cml.field.block.PIControlBlock;

/** Delayed machine-base Q-error and conditional PI foundation using core integration, without network attachment. */
public final class Oel4cKernel {
    /** Opaque accepted-state checkpoint owned by one kernel; trials are never checkpointed. */
    public static final class Checkpoint {
        private final Oel4cKernel owner;private final double integral,elapsed;
        private Checkpoint(Oel4cKernel owner,double integral,double elapsed){this.owner=owner;this.integral=integral;this.elapsed=elapsed;}
    }
    public record Signals(double reactiveError,double controlledError,double proportional,
            double integral,double output,boolean active,double elapsed) { }
    private final Oel4cData data;
    private final PIControlBlock pi;
    private double pickup,elapsed,startIntegral,startElapsed,pendingStep;
    private boolean initialized,predicted;
    public Oel4cKernel(Oel4cData data) {
        this.data=Objects.requireNonNull(data);
        pi=new PIControlBlock(data.proportionalGain(),data.integralGain());
    }
    public Oel4cData data(){return data;}
    public void initialize(double reactiveMaximum) {
        pickup=data.effectivePickup(reactiveMaximum);pi.initStateY0(0);elapsed=0;predicted=false;initialized=true;
    }
    public Map<String,Double> namedStates(){
        requireInitialized();return Map.of("OEL PI integral",pi.getStateX());
    }
    public Checkpoint checkpoint(){
        requireInitialized();if(predicted)throw new IllegalStateException("Cannot checkpoint an OEL4C predictor");
        return new Checkpoint(this,pi.getStateX(),elapsed);
    }
    public void restore(Checkpoint checkpoint){
        requireInitialized();if(checkpoint==null||checkpoint.owner!=this)throw new IllegalArgumentException("Foreign OEL4C checkpoint");
        pi.setStateX(checkpoint.integral);pi.setDX_dt(0);elapsed=checkpoint.elapsed;predicted=false;
        startIntegral=startElapsed=pendingStep=0;
    }
    public Signals signals(double reactivePower){requireInitialized();return signals(reactivePower,elapsed);}
    private Signals signals(double q,double timer){
        finite(q);double error=pickup-q;
        boolean active=error<0&&timer>=data.delay();
        double controlled=error>=0||active?error:0;
        double proportional=data.proportionalGain()*controlled;
        finite(pi.getStateX()+proportional);
        double output=Math.max(data.minimumOutput(),Math.min(0,pi.getStateX()+proportional));
        for(double value:new double[]{error,controlled,proportional,pi.getStateX(),output,timer})finite(value);
        return new Signals(error,controlled,proportional,pi.getStateX(),output,active,timer);
    }
    /** Trial timer and PI history are rolled back when an owner discards a prediction. */
    public void cancelPrediction(){
        requireInitialized();if(predicted){pi.setStateX(startIntegral);elapsed=startElapsed;predicted=false;}
    }
    /** Explicit modified-Euler stages; timer observation and zero-time calls are pure. */
    public boolean step(double seconds,int flag,double reactivePower){
        requireInitialized();
        if(!Double.isFinite(seconds)||seconds<0||!Double.isFinite(reactivePower)||(flag!=0&&flag!=1))return false;
        Signals current;
        try{current=signals(reactivePower);}catch(IllegalArgumentException failure){return false;}
        if(seconds==0)return true;
        if((flag==0&&predicted)||(flag==1&&(!predicted||seconds!=pendingStep)))return false;
        double originTimer=flag==0?elapsed:startElapsed;
        double advancedTimer=originTimer+seconds;if(!Double.isFinite(advancedTimer))return false;
        // Once enforcing, retain elapsed time through PI recovery; a short overload resets immediately.
        double nextTimer=current.reactiveError()<0||(originTimer>=data.delay()&&current.output()<0)
            ?advancedTimer:0;
        if(!Double.isFinite(nextTimer))return false;
        Signals input;
        try{input=signals(reactivePower,flag==0?elapsed:nextTimer);}catch(IllegalArgumentException failure){return false;}
        double rateInput=input.controlledError();
        double unconstrained=input.integral()+input.proportional();
        double rate=data.integralGain()*rateInput;
        if((unconstrained>=0&&rate>0)||(unconstrained<=data.minimumOutput()&&rate<0)){rateInput=0;rate=0;}
        // Match the core's arithmetic order so an overflowing corrector difference cannot mutate history.
        double increment=flag==0?rate*seconds:.5*(rate-pi.getDX_dt())*seconds;
        double candidate=pi.getStateX()+increment;
        if(!Double.isFinite(rate)||!Double.isFinite(increment)||!Double.isFinite(candidate))return false;
        try{if(!Double.isFinite(candidate+signals(reactivePower,nextTimer).proportional()))return false;}
        catch(IllegalArgumentException failure){return false;}
        if(flag==0){
            startIntegral=pi.getStateX();startElapsed=elapsed;pendingStep=seconds;
            pi.eulerStep1(rateInput,seconds);elapsed=nextTimer;predicted=true;
        }else{
            pi.eulerStep2(rateInput,seconds);
            // Expire accepted recovery history from the corrected PI output, never a trial overshoot.
            boolean enforcing=input.reactiveError()<0
                ||(startElapsed>=data.delay()&&signals(reactivePower,nextTimer).output()<0);
            elapsed=enforcing?advancedTimer:0;predicted=false;
        }
        finite(pi.getStateX());return true;
    }
    private void requireInitialized(){if(!initialized)throw new IllegalStateException("Initialize OEL4C first");}
    private static void finite(double value){if(!Double.isFinite(value))throw new IllegalArgumentException("Nonfinite OEL4C signal");}
}
