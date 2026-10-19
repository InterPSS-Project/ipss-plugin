package org.interpss.dstab.renewable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * WTDTB1 torsional-emulation kernel with explicit published/native conventions.
 * Native precision/profile limitations remain documented separately from DYR
 * loadability; see docs/benchmarks/wtdtb1-native-import-2026-10-18.md.
 * The published shaft state is angle, not shaft torque. Electrical-power
 * filtering emulates pitch recovery; an attached aerodynamic model selects
 * the documented WTDTA1-equation fallback and leaves the fifth state inactive.
 */
public final class Wtdtb1Model implements WindDriveTrain {
    /** Do not silently mix the published diagram and the observed native 36.7 interface. */
    public enum EquationConvention { PUBLISHED_DIAGRAM, PSSE_36_7 }
    private final Wtdtb1Data data;
    private final double nominalFrequency;
    private final EquationConvention equationConvention;
    private final double[] state=new double[5];
    private final TwoStepHistory twoStep=new TwoStepHistory(5);
    void rebaseNextTwoStep() { twoStep.rebaseNextStep(); }
    private double[] start,initialRate;
    private double initialPower,initialSpeed=1;
    private boolean aerodynamicPresent;
    private org.interpss.dstab.control.util.IntegrationStateMonitor integrationObserver;
    private org.interpss.dstab.control.util.IntegrationStateMonitor.Stage observationStage;

    /** Optional observation port; it does not participate in the mechanical equations. */
    void setIntegrationObserver(org.interpss.dstab.control.util.IntegrationStateMonitor observer) { integrationObserver=observer; }
    void setSinglePrecisionStates(boolean enabled) { twoStep.setSinglePrecision(enabled); }

    public Wtdtb1Model(Wtdtb1Data data,double nominalFrequency) {
        this(data,nominalFrequency,EquationConvention.PUBLISHED_DIAGRAM);
    }

    /** Native convention independently discriminated with generator/turbine speed perturbations. */
    public static Wtdtb1Model forNativePsse367(Wtdtb1Data data,double nominalFrequency) {
        return new Wtdtb1Model(data,nominalFrequency,EquationConvention.PSSE_36_7);
    }

    private Wtdtb1Model(Wtdtb1Data data,double nominalFrequency,EquationConvention convention) {
        this.data=java.util.Objects.requireNonNull(data);
        this.equationConvention=java.util.Objects.requireNonNull(convention);
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
        if(isNativePsse()&&!aerodynamicPresent&&speed!=1)
            throw new IllegalArgumentException("Native WTDTB1 emulation without aerodynamics initializes at nominal speed");
        initialSpeed=speed;
        // Native damping acts on absolute speed deviation, unlike the published
        // WTGT_B diagram's deviation from W0. Balance the shaft and aero input
        // backwards from Pe; do not change the solved electrical operating point.
        double initialDamping=isNativePsse()?data.damp()*(speed-1):0;
        double initialTorque=power/speed+initialDamping;
        initialPower=initialTorque*speed;
        java.util.Arrays.fill(state,0);
        // Internally retain perturbations from initialized speed; observation
        // adds the native deviation-from-one offset without changing integration.
        state[0]=isSingleMass()?0:initialTorque/shaftStiffness();
        state[4]=aerodynamicPresent?0:power;
        start=null;initialRate=null;twoStep.reset();
        if(integrationObserver!=null)integrationObserver.reset();
    }

    @Override public void step(double dt,double mechanicalPower,double electricalPower) {
        step(dt,mechanicalPower,electricalPower,0);
        step(dt,mechanicalPower,electricalPower,1);
    }

    @Override public void step(double dt,double mechanicalPower,double electricalPower,int flag) {
        validate(dt,mechanicalPower,electricalPower);
        if(flag!=0&&flag!=1)throw new IllegalArgumentException("WTDTB1 stage must be 0 or 1");
        if(dt==0){algebraicElectricalPower(electricalPower);return;}
        observationStage=flag==0?org.interpss.dstab.control.util.IntegrationStateMonitor.Stage.PREDICTOR
                :org.interpss.dstab.control.util.IntegrationStateMonitor.Stage.CORRECTOR;
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
        observationStage=org.interpss.dstab.control.util.IntegrationStateMonitor.Stage.TWO_STEP;
        double[] derivative=rate(state,mechanicalPower,electricalPower);
        twoStep.begin(dt);
        for(int i=0;i<5;i++)state[i]=twoStep.advance(i,state[i],derivative[i]);
        if(integrationObserver!=null)twoStep.observeRates(integrationObserver);
        algebraicElectricalPower(electricalPower);
        requireFinite();
    }

    private double[] rate(double[] x,double mechanicalPower,double electricalPower) {
        double wt=initialSpeed+x[1],wg=initialSpeed+x[2];
        if(wt<=0||wg<=0)throw new IllegalStateException("WTDTB1 speed crossed its physical positive-speed domain");
        double pm=aerodynamicPresent?mechanicalPower:data.tp()==0?electricalPower:x[4];
        double dampingDeviation=isNativePsse()?wg-1:x[2];
        double[] result=new double[5];
        if(isSingleMass()) {
            // Inactive turbine/shaft perturbations retain their initialized values.
            // Native one-mass mode retains the initialized turbine coordinate
            // for Tm, rather than substituting the changing generator speed.
            double mechanicalTorque=pm/(isNativePsse()?wt:wg);
            result[2]=(mechanicalTorque-electricalPower/wg-data.damp()*dampingDeviation)/(2*data.h());
        } else {
            double torque=shaftStiffness()*x[0];
            double shaftDamping=data.dshaft()*(x[1]-x[2]);
            result[0]=angularFrequency()*(x[1]-x[2]);
            result[1]=(pm/wt-torque-shaftDamping)/(2*data.h()*data.htfrac());
            double generatorFeedback=(torque+shaftDamping)*(isNativePsse()?wg/wt:1);
            result[2]=(generatorFeedback-electricalPower/wg-data.damp()*dampingDeviation)
                    /(2*data.h()*(1-data.htfrac()));
        }
        result[3]=angularFrequency()*x[2];
        if(!aerodynamicPresent&&data.tp()>0)result[4]=(electricalPower-x[4])/data.tp();
        if(integrationObserver!=null) {
            integrationObserver.begin(observationStage);
            for(int i=0;i<5;i++)integrationObserver.record(i,x[i]+(i==1||i==2?initialSpeed-1:0),result[i]);
        }
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
    public EquationConvention getEquationConvention() { return equationConvention; }
    private boolean isNativePsse() { return equationConvention==EquationConvention.PSSE_36_7; }
    public double[] getNativeStates() {
        double[] result=state.clone();
        result[1]+=initialSpeed-1;
        result[2]+=initialSpeed-1;
        return result;
    }
    public double getInitialSpeed() { return initialSpeed; }
    @Override public double getInitialInputPower() { return initialPower; }
    @Override public double getGeneratorSpeed() { return initialSpeed+state[2]; }
    @Override public double getTurbineSpeed() { return isSingleMass()?getGeneratorSpeed():initialSpeed+state[1]; }
    public double getFilteredElectricalPower() { return state[4]; }
    public Map<String,Double> getNamedStates() {
        double[] observed=getNativeStates();
        Map<String,Double> values=new LinkedHashMap<>();
        values.put("Shaft Twist Angle",observed[0]);
        values.put("Turbine Speed Deviation",observed[1]);
        values.put("Generator Speed Deviation",observed[2]);
        values.put("Generator Rotor Angle Deviation",observed[3]);
        values.put("Electrical Power Measurement Filter",observed[4]);
        return Collections.unmodifiableMap(values);
    }
}
