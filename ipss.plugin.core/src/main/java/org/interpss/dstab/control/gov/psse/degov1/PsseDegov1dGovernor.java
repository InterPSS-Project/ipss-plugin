package org.interpss.dstab.control.gov.psse.degov1;

import java.util.ArrayDeque;
import java.util.Deque;

import org.interpss.dstab.control.util.AsymmetricDeadbandBlock;
import org.interpss.dstab.control.util.IntegrationStepAware;
import org.interpss.numeric.datatype.Unit.UnitType;

import com.interpss.common.exp.InterpssRuntimeException;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.deqn.AbstractGovernor;
import com.interpss.dstab.mach.Machine;

/**
 * PSS/E DEGOV1D Woodward diesel governor.
 *
 * <p>The realization follows the published PowerWorld/PSS/E diagram. It keeps
 * the two electric-control-box states, three actuator states, optional electric
 * power transducer, and a time-stamped engine transport delay. Continuous
 * states use the InterPSS modified-Euler predictor/corrector.</p>
 */
public class PsseDegov1dGovernor extends AbstractGovernor implements IntegrationStepAware {
    private static final double EPS = 1.0e-9;

    private State state = State.zero();
    private State oldState = State.zero();
    private Derivatives oldDerivatives = Derivatives.zero();
    private final Deque<DelaySample> engineHistory = new ArrayDeque<>();
    private double reference;
    private double auxiliaryInput;
    private double governorToMachineBase = 1.0;
    private double effectiveT1, effectiveT2, effectiveT4, effectiveT5;
    private double effectiveT6, effectiveTd, effectiveTe;
    private double effectiveTmax, effectiveTmin;
    private double integrationStep;
    private double minimumTimeConstantMultiplier = 1.0;
    private double simulationTime;
    private double committedError;
    private double currentActuator;
    private double currentOutput;
    private boolean initialized;

    public PsseDegov1dGovernor(String id, String name, String category) {
        super(id, name, category);
        _data = new PsseDegov1dGovernorData();
    }

    public PsseDegov1dGovernorData getData() {
        return (PsseDegov1dGovernorData) _data;
    }

    @Override public void configureIntegrationStep(double timeStepSec) {
        configureIntegrationStep(timeStepSec, 1.0);
    }

    public void configureIntegrationStep(double timeStepSec, double multiplier) {
        if (!Double.isFinite(timeStepSec) || timeStepSec < 0.0
                || !Double.isFinite(multiplier) || multiplier < 0.0) {
            throw new IllegalArgumentException(
                    "DEGOV1D integration settings must be finite and non-negative");
        }
        integrationStep = timeStepSec;
        minimumTimeConstantMultiplier = multiplier;
    }

    @Override public boolean initStates(BaseDStabBus<?, ?> bus, Machine mach) {
        if (!validateParameters()) return false;
        prepareEffectiveParameters();
        double machineMva = mach.getRating(UnitType.mVA, bus.getNetwork().getBaseKva());
        governorToMachineBase = getData().getTrate() > EPS && machineMva > EPS
                ? getData().getTrate() / machineMva : 1.0;
        double absoluteSpeed = Math.max(EPS, mach.getSpeed());
        double actuator0 = mach.getPm() / governorToMachineBase / absoluteSpeed;
        double pe0 = mach.getPe() / governorToMachineBase;
        if (!finite(actuator0, pe0)) return false;

        effectiveTmax = Math.max(effectiveTmax, actuator0);
        effectiveTmin = Math.min(effectiveTmin, actuator0);
        state = new State(0.0, 0.0, actuator0, actuator0, actuator0, pe0);
        oldState = state;
        currentActuator = actuator0;
        currentOutput = actuator0 * absoluteSpeed;
        committedError = 0.0;
        reference = applySpeedDeadband(mach.getSpeed() - 1.0)
                + getData().getDroop() * selectedDroop(actuator0, pe0)
                - auxiliaryInput;
        simulationTime = 0.0;
        engineHistory.clear();
        engineHistory.addLast(new DelaySample(0.0, actuator0));
        initialized = true;
        return true;
    }

    @Override public boolean nextStep(double dt, DynamicSimuMethod method,
            Machine mach, int flag) {
        if (method != DynamicSimuMethod.MODIFIED_EULER) {
            throw new InterpssRuntimeException("DEGOV1D supports MODIFIED_EULER only");
        }
        if (!initialized) return false;
        if (flag == 0) {
            oldState = state;
            Algebraic old = algebraic(oldState, dt, 0.0);
            oldDerivatives = derivatives(oldState, old);
            state = normalize(oldState.plus(oldDerivatives, dt), old.error);
            Algebraic predicted = algebraic(state, dt, dt);
            currentActuator = predicted.actuator;
            currentOutput = predicted.pmech;
        } else if (flag == 1) {
            Algebraic predicted = algebraic(state, dt, dt);
            Derivatives corrected = derivatives(state, predicted);
            state = normalize(oldState.plusAverage(oldDerivatives, corrected, dt),
                    predicted.error);
            Algebraic result = algebraic(state, dt, dt);
            currentActuator = result.actuator;
            currentOutput = result.pmech;
            simulationTime += dt;
            committedError = result.error;
            engineHistory.addLast(new DelaySample(simulationTime, result.actuator));
            trimEngineHistory(dt);
        } else {
            throw new InterpssRuntimeException("DEGOV1D invalid integration flag: " + flag);
        }
        return true;
    }

    @Override public double getOutput(Machine mach) {
        return currentOutput * governorToMachineBase;
    }

    @Override public void setRefPoint(double value) {
        reference = value / governorToMachineBase;
    }

    public void setAuxiliaryInput(double value) { auxiliaryInput = value; }
    public double getReference() { return reference; }
    public double getActuatorOutput() { return currentActuator; }
    public double getMeasuredElectricalPower() {
        return effectiveTe > EPS ? state.peMeasured : machinePowerOnGovernorBase();
    }
    public double getControlBoxOutput() {
        return controlBoxOutput(state, committedError, Math.max(integrationStep, EPS));
    }
    public double getEffectiveT1() { return effectiveT1; }
    public double getEffectiveT2() { return effectiveT2; }
    public double getEffectiveT4() { return effectiveT4; }
    public double getEffectiveT5() { return effectiveT5; }
    public double getEffectiveT6() { return effectiveT6; }
    public double getEffectiveTd() { return effectiveTd; }
    public double getEffectiveTe() { return effectiveTe; }
    public double getEffectiveTmax() { return effectiveTmax; }
    public double getEffectiveTmin() { return effectiveTmin; }
    public double getGovernorBaseMva(Machine mach) {
        return governorToMachineBase * mach.getRating(UnitType.mVA,
                mach.getDStabBus().getNetwork().getBaseKva());
    }
    public double applySpeedDeadband(double speedDeviation) {
        return AsymmetricDeadbandBlock.apply(speedDeviation,
                getData().getDbH(), getData().getDbL());
    }

    public boolean validateParameters() {
        PsseDegov1dGovernorData d = getData();
        return (d.getDroopControl() == 0 || d.getDroopControl() == 1)
                && finite(d.getT1(), d.getT2(), d.getT3(), d.getK(), d.getT4(),
                        d.getT5(), d.getT6(), d.getTd(), d.getTmax(), d.getTmin(),
                        d.getDroop(), d.getTe(), d.getDbH(), d.getDbL(), d.getTrate())
                && d.getT1() >= 0.0 && d.getT2() >= 0.0 && d.getT3() >= 0.0
                && d.getK() > 0.0 && d.getT4() >= 0.0 && d.getT5() >= 0.0
                && d.getT6() >= 0.0 && d.getTd() >= 0.0 && d.getDroop() >= 0.0
                && d.getTe() >= 0.0 && d.getDbH() >= 0.0 && d.getDbL() <= 0.0
                && d.getDbL() <= d.getDbH() && d.getTrate() >= 0.0;
    }

    private Algebraic algebraic(State s, double dt, double evaluationOffset) {
        double actuatorRaw = actuatorRaw(s);
        double actuator = clamp(actuatorRaw, effectiveTmin, effectiveTmax);
        double pe = effectiveTe > EPS ? s.peMeasured : machinePowerOnGovernorBase();
        double error = reference + auxiliaryInput
                - applySpeedDeadband(getMachine().getSpeed() - 1.0)
                - getData().getDroop() * selectedDroop(actuator, pe);
        double control = controlBoxOutput(s, error, dt);
        double delayed = delayedEngineInput(actuator, evaluationOffset);
        return new Algebraic(error, control, actuatorRaw, actuator,
                delayed * Math.max(EPS, getMachine().getSpeed()));
    }

    private Derivatives derivatives(State s, Algebraic a) {
        double controlPositionDot = 0.0;
        double controlRateDot = 0.0;
        if (effectiveT1 > EPS && effectiveT2 > EPS) {
            controlPositionDot = s.controlRate;
            controlRateDot = (a.error - s.controlPosition
                    - effectiveT1 * s.controlRate) / (effectiveT1 * effectiveT2);
        } else if (effectiveT1 > EPS) {
            controlPositionDot = (a.error - s.controlPosition) / effectiveT1;
        }

        double integratorDot = getData().getK() * a.control;
        if ((a.actuatorRaw >= effectiveTmax && integratorDot > 0.0)
                || (a.actuatorRaw <= effectiveTmin && integratorDot < 0.0)) {
            integratorDot = 0.0;
        }
        double leadLagDot = effectiveT5 > EPS
                ? (s.integrator - s.leadLag) / effectiveT5 : 0.0;
        double leadLag = leadLagOutput(s);
        double actuatorLagDot = effectiveT6 > EPS
                ? (leadLag - s.actuatorLag) / effectiveT6 : 0.0;
        double peDot = effectiveTe > EPS
                ? (machinePowerOnGovernorBase() - s.peMeasured) / effectiveTe : 0.0;
        return new Derivatives(controlPositionDot, controlRateDot, integratorDot,
                leadLagDot, actuatorLagDot, peDot);
    }

    private State normalize(State s, double error) {
        double controlPosition = effectiveT1 > EPS ? s.controlPosition : error;
        double controlRate = effectiveT1 > EPS && effectiveT2 > EPS ? s.controlRate : 0.0;
        double leadLag = effectiveT5 > EPS ? s.leadLag : s.integrator;
        double actuatorLag = effectiveT6 > EPS ? s.actuatorLag : leadLagOutput(
                new State(controlPosition, controlRate, s.integrator, leadLag,
                        s.actuatorLag, s.peMeasured));
        double pe = effectiveTe > EPS ? s.peMeasured : machinePowerOnGovernorBase();
        return new State(controlPosition, controlRate, s.integrator, leadLag,
                actuatorLag, pe);
    }

    private double controlBoxOutput(State s, double error, double dt) {
        if (effectiveT1 > EPS && effectiveT2 > EPS) {
            return s.controlPosition + getData().getT3() * s.controlRate;
        }
        if (effectiveT1 > EPS) {
            return s.controlPosition + getData().getT3() / effectiveT1
                    * (error - s.controlPosition);
        }
        return error + (getData().getT3() > EPS && dt > EPS
                ? getData().getT3() * (error - committedError) / dt : 0.0);
    }

    private double actuatorRaw(State s) {
        return effectiveT6 > EPS ? s.actuatorLag : leadLagOutput(s);
    }

    private double leadLagOutput(State s) {
        return effectiveT5 > EPS
                ? s.leadLag + effectiveT4 / effectiveT5 * (s.integrator - s.leadLag)
                : s.integrator;
    }

    private double selectedDroop(double actuator, double pe) {
        return getData().getDroopControl() == 1 ? pe : actuator;
    }

    private double machinePowerOnGovernorBase() {
        return getMachine().getPe() / governorToMachineBase;
    }

    private double delayedEngineInput(double currentInput, double evaluationOffset) {
        if (effectiveTd <= EPS) return currentInput;
        double target = simulationTime + evaluationOffset - effectiveTd;
        DelaySample first = engineHistory.getFirst();
        if (target <= first.time) return first.value;
        DelaySample previous = first;
        for (DelaySample sample : engineHistory) {
            if (sample.time >= target) return interpolate(previous, sample, target);
            previous = sample;
        }
        return interpolate(previous,
                new DelaySample(simulationTime + evaluationOffset, currentInput), target);
    }

    private void trimEngineHistory(double dt) {
        double retainAfter = simulationTime - effectiveTd - Math.max(dt, EPS);
        while (engineHistory.size() > 2) {
            DelaySample first = engineHistory.removeFirst();
            if (engineHistory.getFirst().time >= retainAfter) {
                engineHistory.addFirst(first);
                break;
            }
        }
    }

    private void prepareEffectiveParameters() {
        PsseDegov1dGovernorData d = getData();
        double minimum = integrationStep * minimumTimeConstantMultiplier;
        effectiveT1 = correctedOptionalTime(d.getT1(), minimum);
        effectiveT2 = correctedOptionalTime(d.getT2(), minimum);
        effectiveT5 = correctedOptionalTime(d.getT5(), minimum);
        effectiveT4 = effectiveT5 > EPS ? d.getT4() : 0.0;
        effectiveT6 = correctedOptionalTime(d.getT6(), minimum);
        effectiveTe = correctedOptionalTime(d.getTe(), minimum);
        effectiveTd = correctedDelay(d.getTd(), integrationStep, minimum);
        effectiveTmax = Math.max(d.getTmax(), d.getTmin());
        effectiveTmin = Math.min(d.getTmax(), d.getTmin());
    }

    private static double correctedOptionalTime(double value, double minimum) {
        if (value <= 0.0 || minimum <= 0.0) return value;
        if (value < 0.5 * minimum) return 0.0;
        return value < minimum ? minimum : value;
    }

    private static double correctedDelay(double value, double dt, double minimum) {
        double corrected = dt > EPS ? Math.min(value, 12.0 * dt) : value;
        if (corrected <= 0.0 || minimum <= 0.0) return corrected;
        if (corrected < 0.25 * minimum) return 0.0;
        return corrected < 0.5 * minimum ? 0.5 * minimum : corrected;
    }

    private static double interpolate(DelaySample lower, DelaySample upper, double time) {
        if (upper.time <= lower.time + EPS) return upper.value;
        double fraction = clamp((time - lower.time) / (upper.time - lower.time), 0.0, 1.0);
        return lower.value + fraction * (upper.value - lower.value);
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record DelaySample(double time, double value) { }
    private record Algebraic(double error, double control, double actuatorRaw,
            double actuator, double pmech) { }
    private record State(double controlPosition, double controlRate,
            double integrator, double leadLag, double actuatorLag, double peMeasured) {
        static State zero() { return new State(0, 0, 0, 0, 0, 0); }
        State plus(Derivatives d, double dt) {
            return new State(controlPosition + d.controlPosition * dt,
                    controlRate + d.controlRate * dt,
                    integrator + d.integrator * dt,
                    leadLag + d.leadLag * dt,
                    actuatorLag + d.actuatorLag * dt,
                    peMeasured + d.peMeasured * dt);
        }
        State plusAverage(Derivatives a, Derivatives b, double dt) {
            return plus(new Derivatives(
                    (a.controlPosition + b.controlPosition) / 2.0,
                    (a.controlRate + b.controlRate) / 2.0,
                    (a.integrator + b.integrator) / 2.0,
                    (a.leadLag + b.leadLag) / 2.0,
                    (a.actuatorLag + b.actuatorLag) / 2.0,
                    (a.peMeasured + b.peMeasured) / 2.0), dt);
        }
    }
    private record Derivatives(double controlPosition, double controlRate,
            double integrator, double leadLag, double actuatorLag, double peMeasured) {
        static Derivatives zero() { return new Derivatives(0, 0, 0, 0, 0, 0); }
    }
}
