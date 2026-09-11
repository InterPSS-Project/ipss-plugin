package org.interpss.dstab.control.gov.psse.pidgov;

import java.util.LinkedHashMap;
import java.util.Map;

import org.interpss.dstab.control.util.AsymmetricDeadbandBlock;
import org.interpss.dstab.control.util.IntegrationStepAware;
import org.interpss.numeric.datatype.Unit.UnitType;

import com.interpss.common.exp.InterpssRuntimeException;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.ICMLStateProvider;
import com.interpss.dstab.controller.deqn.AbstractGovernor;
import com.interpss.dstab.mach.Machine;

/**
 * PSS/E PIDGOV/PIDGOVD hydro turbine-governor.
 *
 * <p>This explicit state realization follows the published PowerWorld/PSS/E
 * block diagram. Continuous states are integrated with the InterPSS modified
 * Euler predictor/corrector.</p>
 */
public class PssePidgovdGovernor extends AbstractGovernor
        implements IntegrationStepAware, ICMLStateProvider {
    private static final double EPS = 1.0e-9;

    private State state = State.zero();
    private State oldState = State.zero();
    private Derivatives oldDerivatives = Derivatives.zero();
    private double reference;
    private double auxiliaryInput;
    private double governorToMachineBase = 1.0;
    private double integrationStep;
    private double minimumTimeConstantMultiplier = 1.0;
    private double effectiveTa, effectiveTb, effectiveTw;
    private double effectiveGmax, effectiveGmin, effectiveVelmax, effectiveVelmin;
    private double committedControlError;
    private double currentOutput;
    private boolean initialized;

    public PssePidgovdGovernor(String id, String name, String category) {
        super(id, name, category);
        _data = new PssePidgovdGovernorData();
    }

    public PssePidgovdGovernorData getData() {
        return (PssePidgovdGovernorData) _data;
    }

    @Override public void configureIntegrationStep(double timeStepSec) {
        configureIntegrationStep(timeStepSec, 1.0);
    }

    public void configureIntegrationStep(double timeStepSec, double multiplier) {
        if (!Double.isFinite(timeStepSec) || timeStepSec < 0.0
                || !Double.isFinite(multiplier) || multiplier < 0.0) {
            throw new IllegalArgumentException(
                    getName() + " integration settings must be finite and non-negative");
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
        double pm0 = mach.getPm() / governorToMachineBase;
        double pe0 = mach.getPe() / governorToMachineBase;
        double turbinePower0 = pm0
                + getData().getDturb() * (mach.getSpeed() - 1.0);
        double gate0 = inversePowerCurve(turbinePower0);
        if (!finite(pm0, pe0, turbinePower0, gate0)) return false;

        effectiveGmax = Math.max(effectiveGmax, gate0);
        effectiveGmin = Math.min(effectiveGmin, gate0);
        state = new State(0.0, gate0, gate0, 0.0, gate0, gate0, turbinePower0);
        oldState = state;
        committedControlError = 0.0;
        reference = selectedFeedback(gate0, pe0) - auxiliaryInput;
        currentOutput = pm0;
        initialized = true;
        return true;
    }

    @Override public boolean nextStep(double dt, DynamicSimuMethod method,
            Machine mach, int flag) {
        if (method != DynamicSimuMethod.MODIFIED_EULER) {
            throw new InterpssRuntimeException(getName() + " supports MODIFIED_EULER only");
        }
        if (!initialized) return false;
        if (flag == 0) {
            oldState = state;
            Algebraic old = algebraic(oldState, dt);
            oldDerivatives = derivatives(oldState, old);
            state = normalize(oldState.plus(oldDerivatives, dt));
            currentOutput = output(state);
        } else if (flag == 1) {
            Algebraic predicted = algebraic(state, dt);
            Derivatives corrected = derivatives(state, predicted);
            state = normalize(oldState.plusAverage(oldDerivatives, corrected, dt));
            Algebraic result = algebraic(state, dt);
            committedControlError = result.controlError;
            currentOutput = output(state);
        } else {
            throw new InterpssRuntimeException(getName() + " invalid integration flag: " + flag);
        }
        return true;
    }

    @Override public double getOutput(Machine mach) {
        return currentOutput * governorToMachineBase;
    }

    @Override public void setRefPoint(double value) {
        double governorValue = value / governorToMachineBase;
        reference = getData().getFeedback() == 1
                ? inversePowerCurve(governorValue) : governorValue;
    }

    public void setAuxiliaryInput(double value) { auxiliaryInput = value; }
    public double getReference() { return reference; }
    public double getGatePosition() { return state.gate; }
    public double getDroopFilterOutput() { return state.droopFilter; }
    public double getPiOutput() {
        return getData().getKp() * algebraic(state, Math.max(integrationStep, EPS)).controlError
                + state.piIntegrator;
    }
    public double getDerivativeOutput() {
        double error = algebraic(state, Math.max(integrationStep, EPS)).controlError;
        return derivativeOutput(state, error, Math.max(integrationStep, EPS));
    }
    public double getRegulatorOutput() {
        return regulator2Output(state, algebraic(state,
                Math.max(integrationStep, EPS)), Math.max(integrationStep, EPS));
    }
    public double getWaterColumnOutput() { return waterColumnOutput(state); }
    public double getWaterState() { return state.waterState; }
    public double getInputSensorState() { return state.droopFilter; }
    public double getPiControllerState() { return state.piIntegrator; }
    public double getFirstRegulatorState() { return state.reg1; }
    public double getDerivativeControllerState() { return getDerivativeOutput(); }
    public double getSecondRegulatorState() { return state.reg2; }
    public double getWaterInertiaState() { return 3.0 * state.waterState; }
    public double getEffectiveTa() { return effectiveTa; }
    public double getEffectiveTb() { return effectiveTb; }
    public double getEffectiveTw() { return effectiveTw; }
    public double getEffectiveGmax() { return effectiveGmax; }
    public double getEffectiveGmin() { return effectiveGmin; }
    public double getEffectiveVelmax() { return effectiveVelmax; }
    public double getEffectiveVelmin() { return effectiveVelmin; }
    @Override
    public Map<String, Double> getNamedStates() {
        Map<String, Double> states = new LinkedHashMap<>();
        states.put("Input Sensor", getInputSensorState());
        states.put("PI Controller", getPiControllerState());
        states.put("First Regulator", getFirstRegulatorState());
        states.put("Derivative Controller", getDerivativeControllerState());
        states.put("Second Regulator", getSecondRegulatorState());
        states.put("Gate Position", getGatePosition());
        states.put("Water Inertia", getWaterInertiaState());
        // Preserve the PowerWorld-oriented semantic names used by older clients.
        states.put("Mechanical Output", currentOutput);
        states.put("Measured Delta P", getInputSensorState());
        states.put("Integral", getPiControllerState());
        states.put("Regulator 1", getFirstRegulatorState());
        states.put("Derivative", getDerivativeControllerState());
        states.put("Regulator 2", getSecondRegulatorState());
        states.put("Gate", getGatePosition());
        return Map.copyOf(states);
    }
    public double getGovernorBaseMva(Machine mach) {
        return governorToMachineBase * mach.getRating(UnitType.mVA,
                mach.getDStabBus().getNetwork().getBaseKva());
    }
    public double applySpeedDeadband(double speedDeviation) {
        return AsymmetricDeadbandBlock.apply(speedDeviation,
                getData().getDbH(), getData().getDbL());
    }
    public double powerFromGate(double gate) { return powerCurve(gate); }
    public double gateFromPower(double power) { return inversePowerCurve(power); }

    public boolean validateParameters() {
        PssePidgovdGovernorData d = getData();
        return (d.getFeedback() == 0 || d.getFeedback() == 1)
                && finite(d.getRperm(), d.getTreg(), d.getKp(), d.getKi(), d.getKd(),
                        d.getTa(), d.getTb(), d.getDturb(), d.getG0(), d.getG1(),
                        d.getP1(), d.getG2(), d.getP2(), d.getP3(), d.getGmax(),
                        d.getGmin(), d.getAtw(), d.getTw(), d.getVelmax(),
                        d.getVelmin(), d.getDbH(), d.getDbL(), d.getTrate())
                && d.getRperm() >= 0.0 && d.getTreg() >= 0.0
                && d.getKp() >= 0.0 && d.getKi() >= 0.0 && d.getKd() >= 0.0
                && d.getTa() >= 0.0 && d.getTb() >= 0.0 && d.getAtw() >= 0.0
                && d.getTw() >= 0.0 && d.getG0() < d.getG1()
                && d.getG1() < d.getG2() && d.getG2() < 1.0
                && d.getP1() > 0.0 && d.getP2() > d.getP1()
                && d.getP3() > d.getP2() && d.getDbH() >= 0.0
                && d.getDbL() <= 0.0 && d.getDbL() <= d.getDbH()
                && d.getTrate() >= 0.0;
    }

    private Algebraic algebraic(State s, double dt) {
        double pe = machinePowerOnGovernorBase();
        double deltaPower = reference + auxiliaryInput
                - selectedFeedback(s.gate, pe);
        double droop = getData().getTreg() > EPS
                ? s.droopFilter : getData().getRperm() * deltaPower;
        double controlError = droop
                - applySpeedDeadband(getMachine().getSpeed() - 1.0);
        double pi = getData().getKp() * controlError + s.piIntegrator;
        double reg1 = effectiveTa > EPS ? s.reg1 : pi;
        double derivative = derivativeOutput(s, controlError, dt);
        double regulatorInput = reg1 + derivative;
        double reg2 = effectiveTa > EPS ? s.reg2 : regulatorInput;
        return new Algebraic(deltaPower, droop, controlError, pi, reg1,
                derivative, regulatorInput, reg2);
    }

    private Derivatives derivatives(State s, Algebraic a) {
        PssePidgovdGovernorData d = getData();
        double droopDot = d.getTreg() > EPS
                ? (d.getRperm() * a.deltaPower - s.droopFilter) / d.getTreg() : 0.0;
        double piDot = d.getKi() * a.controlError;
        double reg1Dot = effectiveTa > EPS ? (a.pi - s.reg1) / effectiveTa : 0.0;
        double derivativeDot = effectiveTa > EPS
                ? (a.controlError - s.derivativeFilter) / effectiveTa : 0.0;
        double reg2Dot = effectiveTa > EPS
                ? (a.regulatorInput - s.reg2) / effectiveTa : 0.0;

        double gateError = a.reg2 - s.gate;
        double rawGateRate = effectiveTb > EPS
                ? gateError / effectiveTb
                : gateError > EPS ? effectiveVelmax : gateError < -EPS ? effectiveVelmin : 0.0;
        double gateDot = clamp(rawGateRate, effectiveVelmin, effectiveVelmax);
        if ((s.gate >= effectiveGmax && gateDot > 0.0)
                || (s.gate <= effectiveGmin && gateDot < 0.0)) gateDot = 0.0;

        double turbinePower = powerCurve(s.gate);
        double waterTime = waterTimeConstant();
        double waterDot = waterTime > EPS
                ? (turbinePower - s.waterState) / waterTime : 0.0;
        return new Derivatives(droopDot, piDot, reg1Dot, derivativeDot,
                reg2Dot, gateDot, waterDot);
    }

    private State normalize(State s) {
        double droop = getData().getTreg() > EPS ? s.droopFilter : 0.0;
        double reg1 = effectiveTa > EPS ? s.reg1 : 0.0;
        double derivative = effectiveTa > EPS ? s.derivativeFilter : 0.0;
        double reg2 = effectiveTa > EPS ? s.reg2 : 0.0;
        double gate = clamp(s.gate, effectiveGmin, effectiveGmax);
        double water = waterTimeConstant() > EPS ? s.waterState : powerCurve(gate);
        return new State(droop, s.piIntegrator, reg1, derivative, reg2, gate, water);
    }

    private double output(State s) {
        return waterColumnOutput(s)
                - getData().getDturb() * (getMachine().getSpeed() - 1.0);
    }

    private double derivativeOutput(State s, double error, double dt) {
        if (effectiveTa > EPS) {
            return getData().getKd() / effectiveTa * (error - s.derivativeFilter);
        }
        return dt > EPS ? getData().getKd() * (error - committedControlError) / dt : 0.0;
    }

    private double regulator2Output(State s, Algebraic a, double dt) {
        if (effectiveTa > EPS) return s.reg2;
        return a.pi + derivativeOutput(s, a.controlError, dt);
    }

    private double waterColumnOutput(State s) {
        double turbinePower = powerCurve(s.gate);
        return waterTimeConstant() > EPS ? -2.0 * turbinePower + 3.0 * s.waterState
                : turbinePower;
    }

    private double selectedFeedback(double gate, double pe) {
        return getData().getFeedback() == 1 ? gate : pe;
    }

    private double machinePowerOnGovernorBase() {
        return getMachine().getPe() / governorToMachineBase;
    }

    private double powerCurve(double gate) {
        PssePidgovdGovernorData d = getData();
        if (gate <= d.getG1()) return interpolate(gate, d.getG0(), 0.0, d.getG1(), d.getP1());
        if (gate <= d.getG2()) return interpolate(gate, d.getG1(), d.getP1(), d.getG2(), d.getP2());
        return interpolate(gate, d.getG2(), d.getP2(), 1.0, d.getP3());
    }

    private double inversePowerCurve(double power) {
        PssePidgovdGovernorData d = getData();
        if (power <= d.getP1()) return interpolate(power, 0.0, d.getG0(), d.getP1(), d.getG1());
        if (power <= d.getP2()) return interpolate(power, d.getP1(), d.getG1(), d.getP2(), d.getG2());
        return interpolate(power, d.getP2(), d.getG2(), d.getP3(), 1.0);
    }

    private void prepareEffectiveParameters() {
        PssePidgovdGovernorData d = getData();
        double minimum = minimumTimeConstantMultiplier * integrationStep;
        effectiveTa = d.getTa() > 0.0 && d.getTa() < 0.5 * minimum
                ? 0.5 * minimum : d.getTa();
        effectiveTb = correctedPositiveTime(d.getTb(), minimum);
        effectiveTw = correctedPositiveTime(d.getTw(), minimum);
        effectiveGmax = Math.max(d.getGmax(), d.getGmin());
        effectiveGmin = Math.min(d.getGmax(), d.getGmin());
        double vmax = Math.max(d.getVelmax(), d.getVelmin());
        double vmin = Math.min(d.getVelmax(), d.getVelmin());
        effectiveVelmax = Math.abs(vmax);
        effectiveVelmin = -Math.abs(vmin);
    }

    private double waterTimeConstant() {
        return 0.5 * getData().getAtw() * effectiveTw;
    }

    private static double correctedPositiveTime(double value, double minimum) {
        return value > 0.0 && value < minimum ? minimum : value;
    }

    private static double interpolate(double x, double x0, double y0, double x1, double y1) {
        return y0 + (x - x0) * (y1 - y0) / (x1 - x0);
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Algebraic(double deltaPower, double droop, double controlError,
            double pi, double reg1, double derivative, double regulatorInput,
            double reg2) { }
    private record Derivatives(double droopFilter, double piIntegrator, double reg1,
            double derivativeFilter, double reg2, double gate, double waterState) {
        static Derivatives zero() { return new Derivatives(0, 0, 0, 0, 0, 0, 0); }
    }
    private record State(double droopFilter, double piIntegrator, double reg1,
            double derivativeFilter, double reg2, double gate, double waterState) {
        static State zero() { return new State(0, 0, 0, 0, 0, 0, 0); }
        State plus(Derivatives d, double dt) {
            return new State(droopFilter + d.droopFilter * dt,
                    piIntegrator + d.piIntegrator * dt, reg1 + d.reg1 * dt,
                    derivativeFilter + d.derivativeFilter * dt,
                    reg2 + d.reg2 * dt, gate + d.gate * dt,
                    waterState + d.waterState * dt);
        }
        State plusAverage(Derivatives a, Derivatives b, double dt) {
            return plus(new Derivatives((a.droopFilter + b.droopFilter) / 2.0,
                    (a.piIntegrator + b.piIntegrator) / 2.0,
                    (a.reg1 + b.reg1) / 2.0,
                    (a.derivativeFilter + b.derivativeFilter) / 2.0,
                    (a.reg2 + b.reg2) / 2.0, (a.gate + b.gate) / 2.0,
                    (a.waterState + b.waterState) / 2.0), dt);
        }
    }
}
