package org.interpss.dstab.control.oel.psse.basoel2;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Continuous blocks with an explicitly chosen timer contract. Native runtime attachment is separate. */
public final class Basoel2Kernel {
    /** The current PowerWorld pseudocode adds positive memory absent from the WECC appendix. */
    public enum TimerSemantics { WECC_2026_DOCUMENTED, POWERWORLD_DOCUMENTED }
    private static final double RETURN_THRESHOLD_FACTOR=.999;
    private static final double ZERO_DELAY_MEMORY=1e-6;
    public record Inputs(double selectedInput,double frequency,double terminalVoltage,double voltageReference) {
        public Inputs {
            finite(selectedInput);finite(frequency);finite(terminalVoltage);finite(voltageReference);
            if(terminalVoltage<0||voltageReference<0)throw new IllegalArgumentException("Voltage magnitude/reference must be nonnegative");
        }
    }
    public record State(double inputLag,double integral,double speedLag) { }
    public record Timer(boolean above,boolean active,double elapsed) { }
    public record Signals(double sensedInput,double sensedFrequency,double reference,double error,
            double proportional,double pi,double oelOutput,double vhzOutput,double total,boolean oelGate) { }
    private final Basoel2Data data;
    private final TimerSemantics timerSemantics;
    private final double[] thresholds,delays;
    private State state,start,firstRate;
    private Timer[] timers;
    private boolean awaitingCommit;
    private double pendingStep;
    public Basoel2Kernel(Basoel2Data data,TimerSemantics timerSemantics){
        this.data=Objects.requireNonNull(data);this.timerSemantics=Objects.requireNonNull(timerSemantics);
        thresholds=data.thresholds();delays=data.cumulativeDelays();
    }
    public Basoel2Data data(){return data;}
    public TimerSemantics timerSemantics(){return timerSemantics;}
    public void initialize(Inputs input){
        Objects.requireNonNull(input);
        State initial=new State(input.selectedInput(),0,input.frequency());
        Timer[] initialTimers={new Timer(false,false,0),new Timer(false,false,0),new Timer(false,false,0)};
        signals(initial,initialTimers,input);
        state=initial;timers=initialTimers;start=firstRate=null;awaitingCommit=false;
    }
    public State state(){initialized();return state;}
    public List<Timer> timers(){initialized();return List.of(timers.clone());}
    public Map<String,Double> namedStates(){
        initialized();Map<String,Double> values=new LinkedHashMap<>();
        values.put("Input lag storage",state.inputLag());values.put("PI integral",state.integral());
        values.put("Speed lag storage",state.speedLag());return Collections.unmodifiableMap(values);
    }
    public Signals signals(Inputs input){initialized();return signals(state,timers,input);}
    public State rates(Inputs input){initialized();return rates(state,input);}
    /** All observations, including timer gating at trial inputs, are side-effect free. */
    private Signals signals(State x,Timer[] memory,Inputs input){
        Objects.requireNonNull(input);
        double sensed=data.inputFilterTime()==0?input.selectedInput():x.inputLag();
        double speed=data.speedFilterTime()==0?input.frequency():x.speedLag();
        double reference=thresholds[0];
        for(int i=1;i<3;i++)if(memory[i].active()&&reference>thresholds[i])reference=thresholds[i];
        double error=sensed-reference,proportional=data.proportionalGain()*error;
        double integral=clip(x.integral(),0,input.voltageReference());
        double sum=integral+proportional;
        // The minimum applies to the gain output, not to terminal-bus frequency.
        double frequencyVoltage=Math.max(data.vhzGain()*speed,data.minimumVhzInput())-input.terminalVoltage();
        for(double value:new double[]{sensed,speed,reference,error,proportional,integral,sum,frequencyVoltage})finite(value);
        boolean gate=false;
        for(int i=0;i<3;i++)gate|=memory[i].active()&&above(memory[i],sensed,thresholds[i]);
        gate&=data.oelEnabled();double pi=clip(sum,0,input.voltageReference());
        double oel=gate?-pi:0.,vhz=data.vhzEnabled()?Math.min(0,frequencyVoltage):0.;
        finite(oel+vhz);return new Signals(sensed,speed,reference,error,proportional,pi,oel,vhz,oel+vhz,gate);
    }
    private State rates(State x,Inputs input){
        // The published diagram feeds Ki/s from the Kp output, not directly from error.
        Signals s=signals(x,timers,input);double integral=data.integralGain()*s.proportional();finite(integral);
        if((x.integral()<=0&&integral<0)||(x.integral()>=input.voltageReference()&&integral>0))integral=0;
        double sensed=data.inputFilterTime()==0?0:(input.selectedInput()-x.inputLag())/data.inputFilterTime();
        double speed=data.speedFilterTime()==0?0:(input.frequency()-x.speedLag())/data.speedFilterTime();
        finite(sensed);finite(integral);finite(speed);return new State(sensed,integral,speed);
    }
    public boolean step(double dt,int flag,Inputs input){
        initialized();if(!Double.isFinite(dt)||dt<0||(flag!=0&&flag!=1)||input==null)return false;
        if(dt==0){try{signals(input);return true;}catch(IllegalArgumentException failure){return false;}}
        if((flag==0&&start!=null)||(flag==1&&(start==null||awaitingCommit||dt!=pendingStep)))return false;
        try{
            if(flag==0){
                State derivative=rates(state,input),predicted=project(add(state,derivative,dt),input.voltageReference());
                signals(predicted,timers,input);start=state;firstRate=derivative;state=predicted;pendingStep=dt;
            }else{
                State last=rates(state,input);
                State average=new State(.5*(firstRate.inputLag()+last.inputLag()),.5*(firstRate.integral()+last.integral()),.5*(firstRate.speedLag()+last.speedLag()));
                State corrected=project(add(start,average,dt),input.voltageReference());
                signals(corrected,timers,input);state=corrected;awaitingCommit=true;
            }
            return true;
        }catch(IllegalArgumentException failure){return false;}
    }
    /** Timer memory advances once per accepted endpoint, never during a trial stage. */
    public boolean acceptEndpoint(double dt,Inputs input){
        initialized();if(!Double.isFinite(dt)||dt<0||input==null)return false;
        if(dt==0){try{signals(input);return true;}catch(IllegalArgumentException failure){return false;}}
        if(!awaitingCommit||dt!=pendingStep)return false;
        try{
            double sensed=signals(input).sensedInput();Timer[] next=new Timer[3];
            for(int i=0;i<3;i++)next[i]=advance(timers[i],sensed,thresholds[i],delays[i],dt);
            signals(state,next,input);timers=next;start=firstRate=null;awaitingCommit=false;return true;
        }catch(IllegalArgumentException failure){return false;}
    }
    public void cancelStep(){
        if(start!=null){state=start;start=firstRate=null;awaitingCommit=false;}
    }
    private Timer advance(Timer previous,double sensed,double threshold,double delay,double dt){
        boolean above=above(previous,sensed,threshold),active=previous.active();double elapsed=previous.elapsed();
        if(above&&!active)elapsed+=dt;
        else if(!above&&active)elapsed-=dt;
        finite(elapsed);
        if(elapsed<=0){elapsed=0;active=false;}
        else if(elapsed>=delay){
            elapsed=timerSemantics==TimerSemantics.POWERWORLD_DOCUMENTED?Math.max(delay,ZERO_DELAY_MEMORY):delay;
            active=true;
        }
        return new Timer(above,active,elapsed);
    }
    private static boolean above(Timer previous,double sensed,double threshold){
        return sensed>threshold*(previous.above()?RETURN_THRESHOLD_FACTOR:1.);
    }
    private static State project(State x,double upper){
        finite(x.inputLag());finite(x.integral());finite(x.speedLag());
        return new State(x.inputLag(),clip(x.integral(),0,upper),x.speedLag());
    }
    private static State add(State x,State rate,double h){
        return new State(x.inputLag()+h*rate.inputLag(),x.integral()+h*rate.integral(),x.speedLag()+h*rate.speedLag());
    }
    private void initialized(){if(state==null)throw new IllegalStateException("Initialize BASOEL2 first");}
    private static double clip(double x,double lo,double hi){return Math.max(lo,Math.min(hi,x));}
    private static void finite(double x){if(!Double.isFinite(x))throw new IllegalArgumentException("Nonfinite BASOEL2 equation value");}
}
