package org.interpss.dstab.renewable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WTDTB1 torsional-emulation source-diagram kernel (PSS/E 36.7).
 * Native trajectory conformance and DYR importer attachment are not yet accepted;
 * see docs/benchmarks/wtdtb1-foundation-2026-10-18.md.
 * The published shaft state is angle, not shaft torque. Electrical-power
 * filtering emulates pitch recovery; an attached aerodynamic model selects
 * the documented WTDTA1-equation fallback and leaves the fifth state inactive.
 */
public final class Wtdtb1Model implements WindDriveTrain {
    private final Wtdtb1Data data;
    private final double nominalFrequency;
    private final double[] state=new double[5];
    private final TwoStepHistory twoStep=new TwoStepHistory(5);
    private double[] start,initialRate;
    private double initialPower,initialSpeed=1;
    private boolean aerodynamicPresent;

    public Wtdtb1Model(Wtdtb1Data data,double nominalFrequency) {
        this.data=java.util.Objects.requireNonNull(data);
        if(!Double.isFinite(nominalFrequency)||nominalFrequency<=0)
            throw new IllegalArgumentException("WTDTB1 nominal frequency must be positive");
        this.nominalFrequency=nominalFrequency;
    }

    /** Configure only before initialization; changing modes at runtime requires reinitialization. */
    public void setAerodynamicPresent(boolean present) { aerodynamicPresent=present; }
    public void initialize(double power) { initialize(power,1); }
    @Override public void initialize(double power,double speed) {
        if(!Double.isFinite(power)||!Double.isFinite(speed)||speed<=0)
            throw new IllegalArgumentException("WTDTB1 initial power/speed must be finite and speed positive");
        initialPower=power;initialSpeed=speed;
        java.util.Arrays.fill(state,0);
        // Native deviations start at zero; initial slip carries the speed offset.
        state[0]=isSingleMass()?0:power/(speed*shaftStiffness());
        state[4]=aerodynamicPresent?0:power;
        start=null;initialRate=null;twoStep.reset();
    }

    @Override public void step(double dt,double mechanicalPower,double electricalPower) {
        step(dt,mechanicalPower,electricalPower,0);
        step(dt,mechanicalPower,electricalPower,1);
    }

    @Override public void step(double dt,double mechanicalPower,double electricalPower,int flag) {
        validate(dt,mechanicalPower,electricalPower);
        if(flag!=0&&flag!=1)throw new IllegalArgumentException("WTDTB1 stage must be 0 or 1");
        if(dt==0){algebraicElectricalPower(electricalPower);return;}
        if(flag==0) {
            start=state.clone();initialRate=rate(start,mechanicalPower,electricalPower);
            for(int i=0;i<5;i++)state[i]=start[i]+dt*initialRate[i];
        } else {
            if(start==null)throw new IllegalStateException("WTDTB1 corrector without predictor");
            double[] corrected=rate(state,mechanicalPower,electricalPower);
            for(int i=0;i<5;i++)state[i]=start[i]+.5*dt*(initialRate[i]+corrected[i]);
            start=null;initialRate=null;
        }
        algebraicElectricalPower(electricalPower);
        requireFinite();
    }

    @Override public void stepTwoStep(double dt,double mechanicalPower,double electricalPower) {
        validate(dt,mechanicalPower,electricalPower);
        if(dt==0){algebraicElectricalPower(electricalPower);return;}
        double[] derivative=rate(state,mechanicalPower,electricalPower);
        twoStep.begin(dt);
        for(int i=0;i<5;i++)state[i]=twoStep.advance(i,state[i],derivative[i]);
        algebraicElectricalPower(electricalPower);
        requireFinite();
    }

    private double[] rate(double[] x,double mechanicalPower,double electricalPower) {
        double wt=initialSpeed+x[1],wg=initialSpeed+x[2];
        if(wt<=0||wg<=0)throw new IllegalStateException("WTDTB1 speed crossed its physical positive-speed domain");
        double pm=aerodynamicPresent?mechanicalPower:data.tp()==0?electricalPower:x[4];
        double[] result=new double[5];
        if(isSingleMass()) {
            // Native inactive turbine/shaft coordinates stay zero in one-mass mode.
            result[2]=(pm/wg-electricalPower/wg-data.damp()*x[2])/(2*data.h());
        } else {
            double torque=shaftStiffness()*x[0];
            double shaftDamping=data.dshaft()*(x[1]-x[2]);
            result[0]=angularFrequency()*(x[1]-x[2]);
            result[1]=(pm/wt-torque-shaftDamping)/(2*data.h()*data.htfrac());
            result[2]=(torque+shaftDamping-electricalPower/wg-data.damp()*x[2])
                    /(2*data.h()*(1-data.htfrac()));
        }
        result[3]=angularFrequency()*(x[2]+initialSpeed-1);
        if(!aerodynamicPresent&&data.tp()>0)result[4]=(electricalPower-x[4])/data.tp();
        return result;
    }

    private void algebraicElectricalPower(double electricalPower) {
        if(aerodynamicPresent)state[4]=0;
        else if(data.tp()==0)state[4]=electricalPower;
    }
    private static void validate(double dt,double pm,double pe) {
        if(!Double.isFinite(dt)||dt<0||!Double.isFinite(pm)||!Double.isFinite(pe))
            throw new IllegalArgumentException("WTDTB1 inputs must be finite and dt nonnegative");
    }
    private void requireFinite() {
        for(double value:state)if(!Double.isFinite(value))throw new IllegalStateException("Nonfinite WTDTB1 state");
    }

    public double shaftStiffness() {
        return isSingleMass()?0:2*data.h()*data.htfrac()*(1-data.htfrac())
                *Math.pow(2*Math.PI*data.freq1(),2)/angularFrequency();
    }
    public double angularFrequency() { return 2*Math.PI*nominalFrequency; }
    public boolean isSingleMass() { return data.htfrac()==0; }
    public boolean isAerodynamicPresent() { return aerodynamicPresent; }
    public Wtdtb1Data getData() { return data; }
    public double[] getNativeStates() { return state.clone(); }
    public double getInitialSpeed() { return initialSpeed; }
    @Override public double getInitialInputPower() { return initialPower; }
    @Override public double getGeneratorSpeed() { return initialSpeed+state[2]; }
    @Override public double getTurbineSpeed() { return initialSpeed+state[1]; }
    public double getFilteredElectricalPower() { return state[4]; }
    public Map<String,Double> getNamedStates() {
        Map<String,Double> values=new LinkedHashMap<>();
        values.put("Shaft Twist Angle",state[0]);
        values.put("Turbine Speed Deviation",state[1]);
        values.put("Generator Speed Deviation",state[2]);
        values.put("Generator Rotor Angle Deviation",state[3]);
        values.put("Electrical Power Measurement Filter",state[4]);
        return Collections.unmodifiableMap(values);
    }
}
