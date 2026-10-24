package org.interpss.dstab.renewable;

import org.interpss.dstab.control.util.IntegrationStateAccess;
import org.interpss.dstab.control.util.IntegrationStateMonitor;
import org.interpss.dstab.control.util.IntegrationStateMonitor.Stage;

/** WECC WTTQ_A torque/active-power-reference controller. */
public final class Wttqa1Model implements IntegrationStateAccess, com.interpss.dstab.controller.cml.ICMLStateProvider {
    /** Suppress only algebraic partitioning roundoff at an initialized equilibrium. */
    private static final double EQUILIBRIUM_RESIDUAL = 1.0e-8;
    private final IntegrationStateMonitor stateMonitor = new IntegrationStateMonitor(
            "Filtered Power", "Speed Reference", "Torque Integral");
    private Stage observationStage = Stage.NOT_EVALUATED;
    private final Wttqa1Data data;
    private double initialPower;
    private double filteredPower;
    private double speedReference;
    private double torqueIntegral;
    private double torque;
    private double pref;
    private TorqueState predictorStart;
    private TorqueDerivative predictorDerivative;
    private final TwoStepHistory twoStep = new TwoStepHistory(3);
    void setSinglePrecisionStates(boolean enabled) { twoStep.setSinglePrecision(enabled); }
    void rebaseNextTwoStep() { twoStep.rebaseNextStep(); }

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
        stateMonitor.reset();
        twoStep.reset();
        predictorStart = null;
        predictorDerivative = null;
        speedReference = speedForPower(power);
        double initialError = data.isPowerControl() ? 0.0
                : generatorSpeed - speedReference;
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
        TorqueState observed = state();
        if (!Double.isFinite(powerReference)) {
            throw new IllegalArgumentException("WTTQA1 Pref0 must be finite");
        }
        filteredPower = Repca1Model.lag(filteredPower, electricalPower, data.tp(), dt);
        speedReference = Repca1Model.lag(speedReference, speedForPower(filteredPower),
                data.twref(), dt);
        // The native block diagram selects (Pref0-filtered Pe)/wg for TFLAG=1 and
        // (wg-wref) for TFLAG=0: an overspeed increases electrical torque.
        double error = data.isPowerControl()
                ? (powerReference - filteredPower) / nonzero(generatorSpeed)
                : generatorSpeed - speedReference;
        if (Math.abs(error) <= EQUILIBRIUM_RESIDUAL) error = 0.0;
        torqueIntegral = Repca1Model.integrateWithAntiWindup(torqueIntegral, data.kip(), error,
                dt, data.kpp(), data.teMin(), data.teMax(), voltageDip);
        torque = Repca1Model.limit(data.kpp() * error + torqueIntegral,
                data.teMin(), data.teMax());
        pref = torque * generatorSpeed;
        if (dt > 0.0) {
            observationStage = Stage.APPLIED_STEP;
            retainRates(observed, new TorqueDerivative(
                    data.tp() <= 0 ? 0 : (filteredPower-observed.filteredPower())/dt,
                    data.twref() <= 0 ? 0 : (speedReference-observed.speedReference())/dt,
                    (torqueIntegral-observed.torqueIntegral())/dt));
        }
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
        observationStage = flag == 0 ? Stage.PREDICTOR : Stage.CORRECTOR;
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
        TorqueDerivative rate = new TorqueDerivative(powerRate, speedReferenceRate, integralRate);
        retainRates(state, rate);
        return rate;
    }

    void stepTwoStep(double dt, double electricalPower, double generatorSpeed,
            double powerReference, boolean voltageDip) {
        observationStage = Stage.TWO_STEP;
        TorqueState start = applyBypasses(state(), electricalPower);
        if (dt > 0.0) {
            TorqueDerivative rate = derivatives(start, electricalPower, generatorSpeed,
                    powerReference, voltageDip);
            twoStep.begin(dt);
            apply(new TorqueState(
                    twoStep.advance(0, start.filteredPower(), rate.filteredPower()),
                    twoStep.advance(1, start.speedReference(), rate.speedReference()),
                    voltageDip ? twoStep.hold(2, start.torqueIntegral())
                            : twoStep.advance(2, start.torqueIntegral(), rate.torqueIntegral())));
            twoStep.observeRates(stateMonitor);
        } else {
            apply(start);
        }
        apply(applyBypasses(state(), electricalPower));
        updateStageOutputs(generatorSpeed, powerReference);
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
        double error = data.isPowerControl()
                ? (powerReference - state.filteredPower()) / nonzero(generatorSpeed)
                : generatorSpeed - state.speedReference();
        return Math.abs(error) <= EQUILIBRIUM_RESIDUAL ? 0.0 : error;
    }


    @Override public java.util.Map<String, Double> getIntegrationStates() {
        return java.util.Map.of("Filtered Power", filteredPower,
                "Speed Reference", speedReference,
                "Torque Integral", torqueIntegral);
    }
    @Override public IntegrationStateMonitor.Snapshot getStateDerivativeSnapshot() {
        return stateMonitor.snapshot();
    }
    @Override public java.util.Map<String, Double> getNamedStates() {
        return getIntegrationStates();
    }
    private void retainRates(TorqueState s, TorqueDerivative d) {
        stateMonitor.begin(observationStage);
        stateMonitor.record(0, s.filteredPower(), d.filteredPower());
        stateMonitor.record(1, s.speedReference(), d.speedReference());
        stateMonitor.record(2, s.torqueIntegral(), d.torqueIntegral());
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
        if(data.hasDefaultSpeedCurve())return 1;
        if (power < data.p1()) return data.sp1();
        if (power >= data.p4()) return data.sp4();
        if (power < data.p2()) return interpolate(power,data.p1(),data.sp1(),data.p2(),data.sp2());
        if (power < data.p3()) return interpolate(power,data.p2(),data.sp2(),data.p3(),data.sp3());
        return interpolate(power,data.p3(),data.sp3(),data.p4(),data.sp4());
    }

    private static double interpolate(double power,double p1,double s1,double p2,double s2) {
        return s1+(power-p1)*(s2-s1)/(p2-p1);
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
