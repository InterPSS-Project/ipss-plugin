package org.interpss.dstab.control.oel.psse.oel3c;

import java.util.Map;
import java.util.Objects;
import java.util.Collections;
import java.util.LinkedHashMap;

/** Two-coordinate OEL3C equation kernel. Network routing and native initialization are separate. */
public final class Oel3cKernel {
    public record State(double inputLag,double integral) { }
    public record Signals(double scaledInput,double reference,double error,double gainedError,
            double proportional,double output) { }
    private final Oel3cData data;
    private State state,start,firstRate;
    private double pendingStep;
    public Oel3cKernel(Oel3cData data){this.data=Objects.requireNonNull(data);}
    public Oel3cData data(){return data;}
    public void initialize(double selectedRawInput) {
        finite(selectedRawInput);State initial=new State(data.scale()*selectedRawInput,
            clip(0,data.lowerIntegral(),data.upperIntegral()));
        signals(initial,selectedRawInput);state=initial;start=firstRate=null;
    }
    public State state(){requireInitialized();return state;}
    public Map<String,Double> namedStates() {
        requireInitialized();Map<String,Double> named=new LinkedHashMap<>();
        named.put("Input lag storage",state.inputLag());named.put("OEL integral",state.integral());
        return Collections.unmodifiableMap(named);
    }
    public Signals signals(double selectedRawInput){requireInitialized();return signals(state,selectedRawInput);}
    public State rates(double selectedRawInput){requireInitialized();return rates(state,selectedRawInput);}
    /** Discard an unaccepted trial when an owner is disabled between integration stages. */
    public void cancelPrediction(){if(start!=null){state=start;start=firstRate=null;}}

    /** Atomic predictor/corrector stages; observation never advances integration history. */
    public boolean step(double seconds,int flag,double selectedRawInput) {
        requireInitialized();
        if(!Double.isFinite(seconds)||seconds<0||(flag!=0&&flag!=1)||!Double.isFinite(selectedRawInput))return false;
        if(seconds==0){signals(selectedRawInput);return true;}
        if((flag==0 && start!=null)||(flag==1 && (start==null||seconds!=pendingStep)))return false;
        try {
            if(flag==0) {
                State derivative=rates(state,selectedRawInput),trial=project(add(state,derivative,seconds));
                signals(trial,selectedRawInput);start=state;firstRate=derivative;state=trial;pendingStep=seconds;
            } else {
                State last=rates(state,selectedRawInput);
                State average=new State(.5*(firstRate.inputLag()+last.inputLag()),.5*(firstRate.integral()+last.integral()));
                State corrected=project(add(start,average,seconds));signals(corrected,selectedRawInput);
                state=corrected;start=firstRate=null;
            }
            return true;
        } catch(IllegalArgumentException ex){return false;}
    }
    private Signals signals(State x,double raw) {
        finite(raw);double sensed=data.filterTime()==0?data.scale()*raw:x.inputLag();
        double reference=Math.pow(data.pickup(),data.exponent());
        double error=reference-Math.pow(sensed,data.exponent()),gained=data.gain()*error;
        // Strict switch: zero (including signed zero) selects the zero branch.
        double proportional=x.integral()<0?data.proportionalGain()*gained:0;
        double output=clip(x.integral()+proportional,data.lowerOutput(),data.upperOutput());
        for(double v:new double[]{x.inputLag(),x.integral(),sensed,reference,error,gained,proportional,output})finite(v);
        return new Signals(sensed,reference,error,gained,proportional,output);
    }
    private State rates(State x,double raw) {
        Signals a=signals(x,raw);double integralRate=a.gainedError()/data.integralTime();
        if((x.integral()>=data.upperIntegral()&&integralRate>0)
                ||(x.integral()<=data.lowerIntegral()&&integralRate<0))integralRate=0;
        double filterRate=data.filterTime()==0?0:(data.scale()*raw-x.inputLag())/data.filterTime();
        finite(filterRate);finite(integralRate);return new State(filterRate,integralRate);
    }
    private State project(State x){
        finite(x.inputLag());finite(x.integral());
        return new State(x.inputLag(),clip(x.integral(),data.lowerIntegral(),data.upperIntegral()));
    }
    private static State add(State x,State f,double h){return new State(x.inputLag()+h*f.inputLag(),x.integral()+h*f.integral());}
    private void requireInitialized(){if(state==null)throw new IllegalStateException("Initialize OEL3C first");}
    private static double clip(double x,double lo,double hi){return Math.max(lo,Math.min(hi,x));}
    private static void finite(double x){if(!Double.isFinite(x))throw new IllegalArgumentException("Nonfinite OEL3C signal");}
}
