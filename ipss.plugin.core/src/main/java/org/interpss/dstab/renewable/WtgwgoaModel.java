package org.interpss.dstab.renewable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * WTGWGOA weak-grid option kernel, PSS/E 36.7 conventions. Native DYR attachment
 * and controller coupling are separate acceptance steps. Recovery uses the
 * captured pre-disturbance plant reference, not a moving target. Native ramp
 * STATEs are power increments; its timer VAR is hold-start time (999 when idle).
 */
public final class WtgwgoaModel {
    public enum EquationConvention { PUBLISHED_RESTART, PSSE_36_7 }
    public enum Mode { BYPASS, FAULT_HOLD, FIRST_RAMP, TIMED_HOLD, SECOND_RAMP }
    private final WtgwgoaData data;
    private final EquationConvention convention;
    private final double[] state=new double[3];
    private final TwoStepHistory twoStep=new TwoStepHistory(3);
    void rebaseNextTwoStep() { twoStep.rebaseNextStep(); }
    private Mode mode=Mode.BYPASS;
    private double clock,capturedReference,holdStart=999,storedLimitedReference,reference;
    private Snapshot start;
    private double[] firstRate;
    private boolean initialized;

    public WtgwgoaModel(WtgwgoaData data){this(data,EquationConvention.PUBLISHED_RESTART);}
    public static WtgwgoaModel forNativePsse367(WtgwgoaData data){return new WtgwgoaModel(data,EquationConvention.PSSE_36_7);}
    private WtgwgoaModel(WtgwgoaData data,EquationConvention convention){this.data=Objects.requireNonNull(data);this.convention=convention;}
    public void initialize(double voltage,double plantReference){initialize(voltage,plantReference,0);}
    public void initialize(double voltage,double plantReference,double initialTime){
        validate(0,voltage,plantReference);
        if(!Double.isFinite(initialTime))throw new IllegalArgumentException("WTGWGOA time must be finite");
        state[0]=voltage;state[1]=state[2]=0;
        clock=initialTime;capturedReference=reference=plantReference;holdStart=999;storedLimitedReference=0;
        mode=Mode.BYPASS;start=null;firstRate=null;twoStep.reset();initialized=true;
        if(!data.enabled()){capturedReference=0;holdStart=0;}
    }
    public void step(double dt,double voltage,double plantReference){
        step(dt,voltage,plantReference,0);step(dt,voltage,plantReference,1);
    }
    /** Modified-Euler stages; predictor-only discrete changes are rolled back before commit. */
    public void step(double dt,double voltage,double plantReference,int flag){
        requireInitialized();validate(dt,voltage,plantReference);
        if(flag!=0 && flag!=1)throw new IllegalArgumentException("WTGWGOA stage must be 0 or 1");
        if(!data.enabled()){reference=plantReference;return;}
        if(dt==0){refreshVoltage(voltage);resolve(plantReference);return;}
        if(flag==0){
            refreshVoltage(voltage);resolve(plantReference);
            start=snapshot();firstRate=rates(voltage);
            for(int i=0;i<3;i++)state[i]=start.state()[i]+dt*firstRate[i];
            clock=start.clock()+dt;refreshVoltage(voltage);resolve(plantReference);
        }else{
            if(start==null)throw new IllegalStateException("WTGWGOA corrector without predictor");
            refreshVoltage(voltage);resolve(plantReference);
            double[] lastRate=rates(voltage),corrected=new double[3];
            corrected[0]=start.state()[0]+.5*dt*(firstRate[0]+lastRate[0]);
            // Ramp derivatives are constant over the sampled mode interval.
            // A provisional endpoint transition must not retrospectively halve
            // the ramp that reached that endpoint. Commit the mode afterwards.
            for(int i=1;i<3;i++)corrected[i]=start.state()[i]+dt*firstRate[i];
            restore(start);System.arraycopy(corrected,0,state,0,3);clock=start.clock()+dt;
            refreshVoltage(voltage);resolve(plantReference);start=null;firstRate=null;
        }
        requireFinite();
    }
    /** Separate opt-in explicit two-step path; zero-duration calls consume no history. */
    void stepTwoStep(double dt,double voltage,double plantReference){
        requireInitialized();validate(dt,voltage,plantReference);
        if(!data.enabled()){reference=plantReference;return;}
        refreshVoltage(voltage);resolve(plantReference);
        if(dt==0)return;
        double[] derivative=rates(voltage);twoStep.begin(dt);
        for(int i=0;i<3;i++)state[i]=i>0 && derivative[i]==0?twoStep.hold(i,state[i])
                :twoStep.advance(i,state[i],derivative[i]);
        clock+=dt;refreshVoltage(voltage);resolve(plantReference);requireFinite();
    }
    private double[] rates(double voltage){
        return new double[]{data.voltageFilterTime()==0?0:(voltage-state[0])/data.voltageFilterTime(),
                mode==Mode.FIRST_RAMP?data.firstRampRate():0,mode==Mode.SECOND_RAMP?data.secondRampRate():0};
    }
    private void refreshVoltage(double voltage){if(data.voltageFilterTime()==0)state[0]=voltage;}
    private void resolve(double plantReference){
        // The published option restarts on a second fault. Cold native 36.7
        // discriminators instead keep an already active profile running. Do
        // not silently replace the published rule with this vendor behavior.
        boolean mayTrigger=mode==Mode.BYPASS || convention==EquationConvention.PUBLISHED_RESTART && mode!=Mode.FAULT_HOLD;
        if(state[0]<data.voltageThreshold() && mayTrigger){
            capturedReference=plantReference;storedLimitedReference=Math.min(data.faultPower(),capturedReference);
            state[1]=state[2]=0;twoStep.hold(1,0);twoStep.hold(2,0);holdStart=999;
            mode=storedLimitedReference<capturedReference?Mode.FAULT_HOLD:Mode.BYPASS;
        }
        if(mode==Mode.BYPASS){capturedReference=reference=plantReference;return;}
        if(mode==Mode.FAULT_HOLD && state[0]>data.voltageThreshold()+data.hysteresis()){
            mode=Mode.FIRST_RAMP;state[1]=0;
        }
        if(mode==Mode.FIRST_RAMP){
            state[1]=Math.max(0,Math.min(state[1],data.holdPower()-data.faultPower()));
            if(data.faultPower()+state[1]>=capturedReference){finish(plantReference);return;}
            if(state[1]>=data.holdPower()-data.faultPower()){
                mode=Mode.TIMED_HOLD;holdStart=clock;twoStep.hold(1,state[1]);
            }
        }
        if(mode==Mode.TIMED_HOLD && clock-holdStart>=data.holdTime()){
            mode=Mode.SECOND_RAMP;holdStart=999;state[2]=0;
        }
        if(mode==Mode.SECOND_RAMP && data.holdPower()+state[2]>=capturedReference){finish(plantReference);return;}
        storedLimitedReference=Math.min(capturedReference,data.faultPower()+state[1]+state[2]);
        reference=storedLimitedReference;
    }
    private void finish(double plantReference){
        storedLimitedReference=capturedReference;capturedReference=reference=plantReference;
        mode=Mode.BYPASS;state[1]=state[2]=0;holdStart=999;twoStep.hold(1,0);twoStep.hold(2,0);
    }
    private Snapshot snapshot(){return new Snapshot(state.clone(),mode,clock,capturedReference,holdStart,storedLimitedReference,reference);}
    private void restore(Snapshot s){System.arraycopy(s.state(),0,state,0,3);mode=s.mode();clock=s.clock();capturedReference=s.captured();holdStart=s.hold();storedLimitedReference=s.limited();reference=s.reference();}
    private record Snapshot(double[] state,Mode mode,double clock,double captured,double hold,double limited,double reference){}
    private static void validate(double dt,double voltage,double reference){
        if(!Double.isFinite(dt)||dt<0||!Double.isFinite(voltage)||voltage<0||!Double.isFinite(reference))
            throw new IllegalArgumentException("WTGWGOA needs finite inputs, dt >= 0 and voltage >= 0");
    }
    private void requireInitialized(){if(!initialized)throw new IllegalStateException("WTGWGOA must be initialized");}
    private void requireFinite(){for(double value:state)if(!Double.isFinite(value))throw new IllegalStateException("Nonfinite WTGWGOA state");}
    public WtgwgoaData getData(){return data;}
    public EquationConvention getEquationConvention(){return convention;}
    public Mode getMode(){return mode;}
    public double getReference(){return reference;}
    public double[] getNativeStates(){return state.clone();}
    public double[] getNativeVars(){return new double[]{capturedReference,holdStart,storedLimitedReference};}
    public Map<String,Double> getNamedStates(){
        Map<String,Double> map=new LinkedHashMap<>();
        map.put("Terminal Voltage Filter",state[0]);map.put("First Recovery Ramp Increment",state[1]);map.put("Second Recovery Ramp Increment",state[2]);
        map.put("Captured Plant Power Reference",capturedReference);map.put("Hold Start Time",holdStart);
        map.put("Stored Limited Power Reference",storedLimitedReference);map.put("Active Power Reference",reference);
        return Collections.unmodifiableMap(map);
    }
}
