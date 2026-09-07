package org.interpss.dstab.renewable;

/**
 * WECC WTDTA1 two-mass wind drive train.
 *
 * <p>The equations follow the public WECC/ANDES model. PSS/E exports
 * {@code Freq1} in per-unit frequency, despite older documentation labeling it
 * as hertz. A zero turbine-inertia fraction selects the documented one-mass
 * representation.</p>
 */
public final class Wtdta1Model {
    private static final double EPS = 1.0e-9;
    private static final double MIN_SPEED = 0.01;

    private final Wtdta1Data data;
    private final WtgtAData powerWorldData;
    private final double inputPowerScale;
    private double initialSpeed = 1.0;
    private double initialInputPower;
    private double initialPower;
    private double turbineSpeed = 1.0;
    private double generatorSpeed = 1.0;
    private double shaftTorque;
    private double dampingPower;

    public Wtdta1Model(Wtdta1Data data) {
        this.data = data;
        this.powerWorldData = null;
        this.inputPowerScale = 1.0;
    }

    /** Build the equivalent kernel while retaining PowerWorld source/base semantics. */
    public Wtdta1Model(WtgtAData data, double machineBaseMva) {
        this.data = data.toWtdta1Data();
        this.powerWorldData = data;
        this.inputPowerScale = machineBaseMva / data.effectiveModelBaseMva(machineBaseMva);
    }

    public void initialize(double power) {
        initialize(power, 1.0);
    }

    /** WTTQA1 supplies its initialized power/speed characteristic when present. */
    public void initialize(double power, double speed) {
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
        if (!Double.isFinite(dt) || dt < 0.0
                || !Double.isFinite(mechanicalPower) || !Double.isFinite(electricalPower)) {
            throw new IllegalArgumentException("WTDTA1 step inputs must be finite and dt must be non-negative");
        }
        if (dt == 0.0) return;
        mechanicalPower = scalePower(mechanicalPower);
        electricalPower = scalePower(electricalPower);
        if (isSingleMass()) {
            stepSingleMass(dt, mechanicalPower, electricalPower);
            return;
        }

        State old = new State(turbineSpeed, generatorSpeed, shaftTorque);
        Derivative d0 = derivatives(old, mechanicalPower, electricalPower);
        State predicted = old.advance(dt, d0);
        Derivative d1 = derivatives(predicted, mechanicalPower, electricalPower);
        turbineSpeed = old.turbineSpeed() + 0.5 * dt
                * (d0.turbineSpeed() + d1.turbineSpeed());
        generatorSpeed = old.generatorSpeed() + 0.5 * dt
                * (d0.generatorSpeed() + d1.generatorSpeed());
        shaftTorque = old.shaftTorque() + 0.5 * dt
                * (d0.shaftTorque() + d1.shaftTorque());
        dampingPower = data.dshaft() * (turbineSpeed - generatorSpeed);
        requireFiniteState();
    }

    private void stepSingleMass(double dt, double mechanicalPower, double electricalPower) {
        double oldSpeed = generatorSpeed;
        double d0 = singleMassDerivative(oldSpeed, mechanicalPower, electricalPower);
        double predicted = oldSpeed + dt * d0;
        double d1 = singleMassDerivative(predicted, mechanicalPower, electricalPower);
        generatorSpeed = turbineSpeed = oldSpeed + 0.5 * dt * (d0 + d1);
        shaftTorque = electricalPower / nonzeroSpeed(generatorSpeed);
        dampingPower = 0.0;
        requireFiniteState();
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
        return new Derivative(turbineDerivative, generatorDerivative, torqueDerivative);
    }

    private double singleMassDerivative(double speed, double mechanicalPower,
            double electricalPower) {
        return (mechanicalPower / nonzeroSpeed(speed)
                - electricalPower / nonzeroSpeed(speed)
                - data.damp() * (speed - initialSpeed)) / (2.0 * data.h());
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

    public boolean isSingleMass() { return data.htfrac() <= EPS; }
    public double turbineInertia() { return data.htfrac() * data.h(); }
    public double generatorInertia() { return (1.0 - data.htfrac()) * data.h(); }

    /** ANDES/PSS/E per-unit-frequency form: 2*Ht*Hg/H * Freq1^2. */
    public double shaftStiffness() {
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
    public double getTurbineSpeed() { return turbineSpeed; }
    public double getGeneratorSpeed() { return generatorSpeed; }
    public double getShaftTorque() { return shaftTorque; }
    public double getDampingPower() { return dampingPower; }

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
