package org.interpss.dstab.renewable;

/** WECC WTTQ_A torque/active-power-reference controller. */
public final class Wttqa1Model {
    /** Suppress only algebraic partitioning roundoff at an initialized equilibrium. */
    private static final double EQUILIBRIUM_RESIDUAL = 1.0e-8;
    private final Wttqa1Data data;
    private double initialPower;
    private double filteredPower;
    private double speedReference;
    private double torqueIntegral;
    private double torque;
    private double pref;
    private TorqueState predictorStart;
    private TorqueDerivative predictorDerivative;

    public Wttqa1Model(Wttqa1Data data) {
        this.data = data;
    }

    public void initialize(double power) {
        initialize(power, speedForPower(power));
    }

    /** Initialize torque and PI bias at the connected drive train's speed. */
    public void initialize(double power, double generatorSpeed) {
        if (!Double.isFinite(power) || !Double.isFinite(generatorSpeed)
                || generatorSpeed <= 0.0) {
            throw new IllegalArgumentException(
                    "WTTQA1 initial power and generator speed must be finite and positive");
        }
        initialPower = filteredPower = power;
        speedReference = speedForPower(power);
        double initialError = data.tFlag() == 1 ? 0.0
                : speedReference - generatorSpeed;
        torque = power / nonzero(generatorSpeed);
        torqueIntegral = torque - data.kpp() * initialError;
        pref = power;
    }

    public void step(double dt, double electricalPower, double generatorSpeed) {
        step(dt, electricalPower, generatorSpeed, false);
    }

    public void step(double dt, double electricalPower, double generatorSpeed,
            boolean voltageDip) {
        step(dt, electricalPower, generatorSpeed, initialPower, voltageDip);
    }

    /**
     * Advance the controller with the current plant-level active-power
     * reference. WECC connects REPCA1's active output to WTTQA1 {@code Pref0};
     * it is therefore an input to the power-error branch rather than an
     * increment added after the torque controller.
     */
    public void step(double dt, double electricalPower, double generatorSpeed,
            double powerReference, boolean voltageDip) {
        if (!Double.isFinite(powerReference)) {
            throw new IllegalArgumentException("WTTQA1 Pref0 must be finite");
        }
        filteredPower = Repca1Model.lag(filteredPower, electricalPower, data.tp(), dt);
        speedReference = Repca1Model.lag(speedReference, speedForPower(filteredPower),
                data.twref(), dt);
        // WECC Figure 3-6 selects (Pref0-filtered Pe)/wg for TFLAG=1 and
        // (wref-wg) for TFLAG=0. This sign is also the physically negative-
        // feedback form of the active-power loop.
        double error = data.tFlag() == 1
                ? (powerReference - filteredPower) / nonzero(generatorSpeed)
                : speedReference - generatorSpeed;
        if (Math.abs(error) <= EQUILIBRIUM_RESIDUAL) error = 0.0;
        torqueIntegral = Repca1Model.integrateWithAntiWindup(torqueIntegral, data.kip(), error,
                dt, data.kpp(), data.teMin(), data.teMax(), voltageDip);
        torque = Repca1Model.limit(data.kpp() * error + torqueIntegral,
                data.teMin(), data.teMax());
        pref = torque * generatorSpeed;
    }

    /**
     * Advances one modified-Euler stage without changing the established
     * complete-step API. Flag 0 exposes a predictor; flag 1 accepts corrected
     * electrical-power, speed, reference, and dip inputs and commits the
     * trapezoidal state. The staged renewable-stack coordinator uses this path.
     */
    public void step(double dt, double electricalPower, double generatorSpeed,
            double powerReference, boolean voltageDip, int flag) {
        if (!Double.isFinite(dt) || dt < 0.0
                || !Double.isFinite(electricalPower)
                || !Double.isFinite(generatorSpeed) || generatorSpeed <= 0.0
                || !Double.isFinite(powerReference)) {
            throw new IllegalArgumentException(
                    "WTTQA1 staged inputs must be finite; dt non-negative and speed positive");
        }
        if (flag != 0 && flag != 1) {
            throw new IllegalArgumentException("WTTQA1 integration flag must be 0 or 1");
        }
        if (dt <= 0.0) {
            apply(applyBypasses(state(), electricalPower));
            updateStageOutputs(generatorSpeed, powerReference);
            return;
        }
        if (flag == 0) {
            predictorStart = state();
            predictorDerivative = derivatives(predictorStart, electricalPower,
                    generatorSpeed, powerReference, voltageDip);
            apply(applyBypasses(add(predictorStart, predictorDerivative, dt),
                    electricalPower));
        } else {
            if (predictorStart == null || predictorDerivative == null) {
                throw new IllegalStateException("WTTQA1 corrector called without predictor");
            }
            TorqueDerivative corrected = derivatives(state(), electricalPower,
                    generatorSpeed, powerReference, voltageDip);
            apply(applyBypasses(correct(predictorStart, predictorDerivative,
                    corrected, dt), electricalPower));
            predictorStart = null;
            predictorDerivative = null;
        }
        updateStageOutputs(generatorSpeed, powerReference);
    }

    private TorqueDerivative derivatives(TorqueState state, double electricalPower,
            double generatorSpeed, double powerReference, boolean voltageDip) {
        double powerRate = lagRate(state.filteredPower(), electricalPower, data.tp());
        double speedReferenceRate = lagRate(state.speedReference(),
                speedForPower(state.filteredPower()), data.twref());
        double error = controlError(state, generatorSpeed, powerReference);
        double integralRate = integralRate(state.torqueIntegral(), data.kip(), error,
                data.kpp(), data.teMin(), data.teMax(), voltageDip);
        return new TorqueDerivative(powerRate, speedReferenceRate, integralRate);
    }

    private TorqueState applyBypasses(TorqueState state, double electricalPower) {
        double filtered = data.tp() <= 0.0 ? electricalPower : state.filteredPower();
        double speedRef = data.twref() <= 0.0
                ? speedForPower(filtered) : state.speedReference();
        return new TorqueState(filtered, speedRef, state.torqueIntegral());
    }

    private void updateStageOutputs(double generatorSpeed, double powerReference) {
        double error = controlError(state(), generatorSpeed, powerReference);
        torque = Repca1Model.limit(data.kpp() * error + torqueIntegral,
                data.teMin(), data.teMax());
        pref = torque * generatorSpeed;
    }

    private double controlError(TorqueState state, double generatorSpeed,
            double powerReference) {
        double error = data.tFlag() == 1
                ? (powerReference - state.filteredPower()) / nonzero(generatorSpeed)
                : state.speedReference() - generatorSpeed;
        return Math.abs(error) <= EQUILIBRIUM_RESIDUAL ? 0.0 : error;
    }

    private TorqueState state() {
        return new TorqueState(filteredPower, speedReference, torqueIntegral);
    }

    private void apply(TorqueState state) {
        filteredPower = state.filteredPower();
        speedReference = state.speedReference();
        torqueIntegral = state.torqueIntegral();
    }

    private static TorqueState add(TorqueState state, TorqueDerivative derivative,
            double dt) {
        return new TorqueState(
                state.filteredPower() + dt * derivative.filteredPower(),
                state.speedReference() + dt * derivative.speedReference(),
                state.torqueIntegral() + dt * derivative.torqueIntegral());
    }

    private static TorqueState correct(TorqueState start, TorqueDerivative first,
            TorqueDerivative second, double dt) {
        return new TorqueState(
                start.filteredPower() + .5 * dt
                        * (first.filteredPower() + second.filteredPower()),
                start.speedReference() + .5 * dt
                        * (first.speedReference() + second.speedReference()),
                start.torqueIntegral() + .5 * dt
                        * (first.torqueIntegral() + second.torqueIntegral()));
    }

    private static double lagRate(double state, double input, double timeConstant) {
        return timeConstant <= 0.0 ? 0.0 : (input - state) / timeConstant;
    }

    private static double integralRate(double integral, double gain, double error,
            double proportionalGain, double lower, double upper, boolean frozen) {
        if (frozen) return 0.0;
        double output = proportionalGain * error + integral;
        if ((output >= upper && error > 0.0) || (output <= lower && error < 0.0)) return 0.0;
        return gain * error;
    }

    public double speedForPower(double power) {
        double[] p = {data.p1(), data.p2(), data.p3(), data.p4()};
        double[] s = {data.sp1(), data.sp2(), data.sp3(), data.sp4()};
        if (power <= p[0]) return s[0];
        for (int i = 1; i < p.length; i++) {
            if (power <= p[i]) {
                return s[i - 1] + (power - p[i - 1]) * (s[i] - s[i - 1])
                        / (p[i] - p[i - 1]);
            }
        }
        return s[s.length - 1];
    }

    private static double nonzero(double value) {
        return Math.max(0.01, Math.abs(value));
    }

    public Wttqa1Data getData() { return data; }
    public double getPref() { return pref; }
    public double getTorque() { return torque; }
    public double getFilteredPower() { return filteredPower; }
    public double getSpeedReference() { return speedReference; }
    public double getTorqueIntegral() { return torqueIntegral; }
    public double getInitialPower() { return initialPower; }

    private record TorqueState(double filteredPower, double speedReference,
            double torqueIntegral) { }

    private record TorqueDerivative(double filteredPower, double speedReference,
            double torqueIntegral) { }
}
