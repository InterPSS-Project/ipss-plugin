package org.interpss.dstab.renewable;

import com.interpss.dstab.controller.runtime.IntegrationStateAccess;
import com.interpss.dstab.controller.runtime.IntegrationStateMonitor;
import com.interpss.dstab.controller.runtime.IntegrationStateMonitor.Stage;

/**
 * WECC WTDTA1 two-mass wind drive train.
 *
 * <p>The native reader uses the independently verified four-state mechanical
 * kernel with hertz frequency and synchronous damping. The direct typed
 * constructor retains the older angular-frequency/initial-speed convention
 * for explicit legacy benchmarks. A zero turbine-inertia fraction selects
 * the documented one-mass representation.</p>
 */
public final class Wtdta1Model implements WindDriveTrain, IntegrationStateAccess, com.interpss.dstab.controller.cml.ICMLStateProvider {
    private static final double EPS = 1.0e-9;
    private static final double MIN_SPEED = 0.01;

    private final IntegrationStateMonitor stateMonitor = new IntegrationStateMonitor(
            "Turbine Speed", "Generator Speed", "Shaft Torque");
    private Stage observationStage = Stage.NOT_EVALUATED;
    private final Wtdta1Data data;
    private final WtgtAData powerWorldData;
    private final double inputPowerScale;
    private final Wtdtb1Model nativeKernel;
    private final IntegrationStateMonitor nativeMonitor;
    private double nativeElectricalPower;
    private static final String[] NATIVE_NAMES={"Shaft Twist Angle","Turbine Speed Deviation",
            "Generator Speed Deviation","Generator Rotor Angle Deviation","Electrical Power Measurement Filter"};
    private double initialSpeed = 1.0;
    private double initialInputPower;
    private double initialPower;
    private double turbineSpeed = 1.0;
    private double generatorSpeed = 1.0;
    private double shaftTorque;
    private double dampingPower;
    private State predictorStart;
    private Derivative predictorDerivative;
    private double predictorSingleMassDerivative;
    private final TwoStepHistory twoStep = new TwoStepHistory(3);
    void setSinglePrecisionStates(boolean enabled) { if(nativeKernel!=null)nativeKernel.setSinglePrecisionStates(enabled);else twoStep.setSinglePrecision(enabled); }
    void rebaseNextTwoStep() { if(nativeKernel!=null)nativeKernel.rebaseNextTwoStep();else twoStep.rebaseNextStep(); }

    /** Legacy typed angular-frequency realization; native records use fromNativeRecord. */
    public Wtdta1Model(Wtdta1Data data) {
        this(data,null);
    }

    private Wtdta1Model(Wtdta1Data data,Wtdtb1Model kernel) {
        this.data = data;
        this.powerWorldData = null;
        this.inputPowerScale = 1.0;
        this.nativeKernel=kernel;
        this.nativeMonitor=kernel==null?null:new IntegrationStateMonitor(NATIVE_NAMES);
        if(kernel!=null)kernel.setIntegrationObserver(nativeMonitor);
    }

    /** Exact five-value native input; the emulation/filter branch is absent. */
    public static Wtdta1Model fromNativeRecord(Wtdta1Data data,double nominalFrequency) {
        var kernel=Wtdtb1Model.forNativePsse367(new Wtdtb1Data(data.h(),data.damp(),data.htfrac(),data.freq1(),data.dshaft(),0),nominalFrequency);
        kernel.setAerodynamicPresent(true);
        return new Wtdta1Model(data,kernel);
    }

    /** Build the equivalent kernel while retaining PowerWorld source/base semantics. */
    public Wtdta1Model(WtgtAData data, double machineBaseMva) {
        this.data = data.toWtdta1Data();
        this.powerWorldData = data;
        this.inputPowerScale = machineBaseMva / data.effectiveModelBaseMva(machineBaseMva);
        this.nativeKernel=null;this.nativeMonitor=null;
    }

    public void initialize(double power) {
        initialize(power, 1.0);
    }

    /** WTTQA1 supplies its initialized power/speed characteristic when present. */
    public void initialize(double power, double speed) {
        if(nativeKernel!=null) {
            nativeKernel.initialize(power,speed);nativeElectricalPower=power;
            initialSpeed=nativeKernel.getInitialSpeed();initialPower=initialInputPower=nativeKernel.getInitialInputPower();return;
        }
        stateMonitor.reset();
        twoStep.reset();
        clearPredictor();
        if (!Double.isFinite(power) || !Double.isFinite(speed) || speed <= 0.0) {
            throw new IllegalArgumentException("WTDTA1 initial power and speed must be finite; speed must be positive");
        }
        initialInputPower = power;
        initialPower = scalePower(power);
        double initializedSpeed = powerWorldData == null ? speed : powerWorldData.w0();
        initialSpeed = turbineSpeed = generatorSpeed = initializedSpeed;
        shaftTorque = initialPower / nonzeroSpeed(initializedSpeed);
        dampingPower = 0.0;
    }

    /** Advances one complete modified-Euler predictor/corrector step. */
    public void step(double dt, double mechanicalPower, double electricalPower) {
        step(dt, mechanicalPower, electricalPower, 0);
        step(dt, mechanicalPower, electricalPower, 1);
    }

    /**
     * Advances one stage of the enclosing modified-Euler step. Flag 0 exposes
     * the explicit predictor; flag 1 reevaluates the equations with corrected
     * endpoint powers and commits the trapezoidal state. This lets WTDTA1
     * participate in a staged renewable-control/network solve without losing
     * the standalone complete-step API above.
     */
    public void step(double dt, double mechanicalPower, double electricalPower, int flag) {
        if(nativeKernel!=null){nativeKernel.step(dt,mechanicalPower,electricalPower,flag);nativeElectricalPower=electricalPower;return;}
        if (!Double.isFinite(dt) || dt < 0.0
                || !Double.isFinite(mechanicalPower) || !Double.isFinite(electricalPower)) {
            throw new IllegalArgumentException("WTDTA1 step inputs must be finite and dt must be non-negative");
        }
        observationStage = flag == 0 ? Stage.PREDICTOR : Stage.CORRECTOR;
        if (flag != 0 && flag != 1) {
            throw new IllegalArgumentException("WTDTA1 integration flag must be 0 or 1");
        }
        if (dt == 0.0) return;
        mechanicalPower = scalePower(mechanicalPower);
        electricalPower = scalePower(electricalPower);
        if (isSingleMass()) {
            stepSingleMass(dt, mechanicalPower, electricalPower, flag);
            return;
        }
        if (flag == 0) {
            predictorStart = state();
            predictorDerivative = derivatives(predictorStart, mechanicalPower,
                    electricalPower);
            apply(predictorStart.advance(dt, predictorDerivative));
        } else {
            requirePredictor();
            Derivative corrected = derivatives(state(), mechanicalPower, electricalPower);
            turbineSpeed = predictorStart.turbineSpeed() + 0.5 * dt
                    * (predictorDerivative.turbineSpeed() + corrected.turbineSpeed());
            generatorSpeed = predictorStart.generatorSpeed() + 0.5 * dt
                    * (predictorDerivative.generatorSpeed() + corrected.generatorSpeed());
            shaftTorque = predictorStart.shaftTorque() + 0.5 * dt
                    * (predictorDerivative.shaftTorque() + corrected.shaftTorque());
            clearPredictor();
        }
        dampingPower = data.dshaft() * (turbineSpeed - generatorSpeed);
        requireFiniteState();
    }

    private void stepSingleMass(double dt, double mechanicalPower,
            double electricalPower, int flag) {
        if (flag == 0) {
            predictorStart = state();
            predictorSingleMassDerivative = singleMassDerivative(generatorSpeed,
                    mechanicalPower, electricalPower);
            generatorSpeed = turbineSpeed = generatorSpeed
                    + dt * predictorSingleMassDerivative;
        } else {
            requirePredictor();
            double corrected = singleMassDerivative(generatorSpeed, mechanicalPower,
                    electricalPower);
            generatorSpeed = turbineSpeed = predictorStart.generatorSpeed()
                    + 0.5 * dt * (predictorSingleMassDerivative + corrected);
            clearPredictor();
        }
        shaftTorque = electricalPower / nonzeroSpeed(generatorSpeed);
        dampingPower = 0.0;
        requireFiniteState();
    }

    public void stepTwoStep(double dt, double mechanicalPower, double electricalPower) {
        if(nativeKernel!=null){nativeKernel.stepTwoStep(dt,mechanicalPower,electricalPower);nativeElectricalPower=electricalPower;return;}
        observationStage = Stage.TWO_STEP;
        if (dt <= 0.0) return;
        mechanicalPower = scalePower(mechanicalPower);
        electricalPower = scalePower(electricalPower);
        State start = state();
        twoStep.begin(dt);
        if (isSingleMass()) {
            generatorSpeed = turbineSpeed = twoStep.advance(0, start.generatorSpeed(),
                    singleMassDerivative(start.generatorSpeed(), mechanicalPower, electricalPower));
            twoStep.observeRate(stateMonitor, 0, 1);
            shaftTorque = electricalPower / nonzeroSpeed(generatorSpeed);
            dampingPower = 0.0;
        } else {
            Derivative rate = derivatives(start, mechanicalPower, electricalPower);
            apply(new State(
                    twoStep.advance(0, start.turbineSpeed(), rate.turbineSpeed()),
                    twoStep.advance(1, start.generatorSpeed(), rate.generatorSpeed()),
                    twoStep.advance(2, start.shaftTorque(), rate.shaftTorque())));
            twoStep.observeRates(stateMonitor);
            dampingPower = data.dshaft() * (turbineSpeed - generatorSpeed);
        }
        requireFiniteState();
    }


    @Override public java.util.Map<String, Double> getIntegrationStates() {
        if(nativeKernel!=null)return nativeStates(true);
        if (isSingleMass()) return java.util.Map.of("Generator Speed", generatorSpeed);
        return java.util.Map.of("Turbine Speed", turbineSpeed,
                "Generator Speed", generatorSpeed,
                "Shaft Torque", shaftTorque);
    }
    @Override public IntegrationStateMonitor.Snapshot getStateDerivativeSnapshot() {
        if(nativeKernel!=null) {
            var snapshot=nativeMonitor.snapshot();var keys=getIntegrationStates().keySet();
            var x=new java.util.LinkedHashMap<String,Double>();var dx=new java.util.LinkedHashMap<String,Double>();
            for(String key:keys)if(snapshot.rates().containsKey(key)){x.put(key,snapshot.evaluationStates().get(key));dx.put(key,snapshot.rates().get(key));}
            return new IntegrationStateMonitor.Snapshot(snapshot.stage(),x,dx);
        }
        var snapshot = stateMonitor.snapshot();
        if (!isSingleMass() || snapshot.rates().isEmpty()) return snapshot;
        return new IntegrationStateMonitor.Snapshot(snapshot.stage(),
                java.util.Map.of("Generator Speed", snapshot.evaluationStates().get("Generator Speed")),
                java.util.Map.of("Generator Speed", snapshot.rates().get("Generator Speed")));
    }
    @Override public java.util.Map<String, Double> getNamedStates() {
        if(nativeKernel!=null) {
            var values=new java.util.LinkedHashMap<>(nativeStates(false));
            values.put("Turbine Speed",getTurbineSpeed());values.put("Generator Speed",getGeneratorSpeed());values.put("Shaft Torque",getShaftTorque());
            return java.util.Collections.unmodifiableMap(values);
        }
        return getIntegrationStates();
    }

    private java.util.Map<String,Double> nativeStates(boolean activeOnly) {
        double[] state=getNativeStates();var result=new java.util.LinkedHashMap<String,Double>();
        for(int i=0;i<4;i++)if(!activeOnly||!isSingleMass()||i>=2)result.put(NATIVE_NAMES[i],state[i]);
        return java.util.Collections.unmodifiableMap(result);
    }

    /** Published four native coordinates, returned without changing integration state. */
    public double[] getNativeStates() {
        if(nativeKernel==null)throw new IllegalStateException("Native coordinates require the native-record convention");
        return java.util.Arrays.copyOf(nativeKernel.getNativeStates(),4);
    }
    public boolean isNativeRecordConvention() { return nativeKernel!=null; }
    private void retainRates(State s, Derivative d) {
        stateMonitor.begin(observationStage);
        stateMonitor.record(0, s.turbineSpeed(), d.turbineSpeed());
        stateMonitor.record(1, s.generatorSpeed(), d.generatorSpeed());
        stateMonitor.record(2, s.shaftTorque(), d.shaftTorque());
    }
    private State state() {
        return new State(turbineSpeed, generatorSpeed, shaftTorque);
    }

    private void apply(State state) {
        turbineSpeed = state.turbineSpeed();
        generatorSpeed = state.generatorSpeed();
        shaftTorque = state.shaftTorque();
    }

    private void requirePredictor() {
        if (predictorStart == null) {
            throw new IllegalStateException("WTDTA1 corrector called without predictor");
        }
    }

    private void clearPredictor() {
        predictorStart = null;
        predictorDerivative = null;
        predictorSingleMassDerivative = 0.0;
    }

    private Derivative derivatives(State state, double mechanicalPower,
            double electricalPower) {
        double speedDifference = state.turbineSpeed() - state.generatorSpeed();
        double shaftDamping = data.dshaft() * speedDifference;
        double turbineDerivative = (mechanicalPower / nonzeroSpeed(state.turbineSpeed())
                - state.shaftTorque() - shaftDamping) / (2.0 * turbineInertia());
        double generatorDerivative = (-electricalPower / nonzeroSpeed(state.generatorSpeed())
                + state.shaftTorque()
                - data.damp() * (state.generatorSpeed() - initialSpeed)
                + shaftDamping) / (2.0 * generatorInertia());
        double torqueDerivative = shaftStiffness() * speedDifference;
        Derivative rate = new Derivative(turbineDerivative, generatorDerivative, torqueDerivative);
        retainRates(state, rate);
        return rate;
    }

    private double singleMassDerivative(double speed, double mechanicalPower,
            double electricalPower) {
        double rate = (mechanicalPower / nonzeroSpeed(speed)
                - electricalPower / nonzeroSpeed(speed)
                - data.damp() * (speed - initialSpeed)) / (2.0 * data.h());
        retainRates(new State(speed, speed, shaftTorque), new Derivative(rate, rate, 0.0));
        return rate;
    }

    private static double nonzeroSpeed(double speed) {
        if (Math.abs(speed) >= MIN_SPEED) return speed;
        return Math.copySign(MIN_SPEED, speed == 0.0 ? 1.0 : speed);
    }

    private void requireFiniteState() {
        if (!Double.isFinite(turbineSpeed) || !Double.isFinite(generatorSpeed)
                || !Double.isFinite(shaftTorque)) {
            throw new IllegalStateException("WTDTA1 produced a non-finite state");
        }
    }

    public boolean isSingleMass() { return nativeKernel!=null?nativeKernel.isSingleMass():data.htfrac() <= EPS; }
    public double turbineInertia() { return data.htfrac() * data.h(); }
    public double generatorInertia() { return (1.0 - data.htfrac()) * data.h(); }

    /** Torque-state derivative stiffness; native frequency is Hz, legacy typed frequency is angular. */
    public double shaftStiffness() {
        if(nativeKernel!=null)return nativeKernel.shaftStiffness()*nativeKernel.angularFrequency();
        if (isSingleMass()) return 0.0;
        return 2.0 * turbineInertia() * generatorInertia() / data.h()
                * data.freq1() * data.freq1();
    }

    public Wtdta1Data getData() { return data; }
    public WtgtAData getPowerWorldData() { return powerWorldData; }
    public double getInputPowerScale() { return inputPowerScale; }
    public double getInitialInputPower() { return initialInputPower; }
    public double getInitialPower() { return initialPower; }
    public double getInitialSpeed() { return initialSpeed; }
    public double getTurbineSpeed() { return nativeKernel!=null?nativeKernel.getTurbineSpeed():turbineSpeed; }
    public double getGeneratorSpeed() { return nativeKernel!=null?nativeKernel.getGeneratorSpeed():generatorSpeed; }
    public double getShaftTorque() { return nativeKernel==null?shaftTorque:isSingleMass()?nativeElectricalPower/getGeneratorSpeed():nativeKernel.shaftStiffness()*getNativeStates()[0]; }
    public double getDampingPower() { return nativeKernel!=null?data.dshaft()*(getTurbineSpeed()-getGeneratorSpeed()):dampingPower; }

    private double scalePower(double power) { return power * inputPowerScale; }

    private record State(double turbineSpeed, double generatorSpeed, double shaftTorque) {
        State advance(double dt, Derivative derivative) {
            return new State(turbineSpeed + dt * derivative.turbineSpeed(),
                    generatorSpeed + dt * derivative.generatorSpeed(),
                    shaftTorque + dt * derivative.shaftTorque());
        }
    }

    private record Derivative(double turbineSpeed, double generatorSpeed,
            double shaftTorque) { }
}
