package org.interpss.dstab.control.gov.psse.wpidhy;

import org.interpss.dstab.control.util.AsymmetricDeadbandBlock;
import org.interpss.dstab.control.util.IntegrationStepAware;
import org.interpss.numeric.datatype.Unit.UnitType;

import com.interpss.common.exp.InterpssRuntimeException;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.deqn.AbstractGovernor;
import com.interpss.dstab.mach.Machine;

/**
 * PSS/E WPIDHYD Woodward PID hydro turbine-governor.
 *
 * <p>The explicit state realization follows the published PSS/E/PowerWorld
 * diagram. In particular, the ideal PID precedes the squared {@code Ta}
 * filter, the nonlinear gate-to-power curve precedes the nonminimum-phase
 * water column, and the turbine power limiter precedes speed damping.</p>
 */
public class PsseWpidhydGovernor extends AbstractGovernor implements IntegrationStepAware {
    private static final double EPS = 1.0e-9;

    private State state = State.zero();
    private State oldState = State.zero();
    private Derivatives oldDerivatives = Derivatives.zero();
    private double reference;
    private double auxiliaryInput;
    private double governorToMachineBase = 1.0;
    private double integrationStep;
    private double minimumTimeConstantMultiplier = 1.0;
    private double effectiveTreg, effectiveTa, effectiveTb, effectiveTw;
    private double effectiveVelmax, effectiveVelmin, effectiveGmax, effectiveGmin;
    private double effectivePmax, effectivePmin;
    private double committedControlError;
    private double currentOutput;
    private boolean initialized;

    public PsseWpidhydGovernor(String id, String name, String category) {
        super(id, name, category);
        _data = new PsseWpidhydGovernorData();
    }

    public PsseWpidhydGovernorData getData() {
        return (PsseWpidhydGovernorData) _data;
    }

    @Override public void configureIntegrationStep(double timeStepSec) {
        configureIntegrationStep(timeStepSec, 1.0);
    }

    public void configureIntegrationStep(double timeStepSec, double multiplier) {
        if (!Double.isFinite(timeStepSec) || timeStepSec < 0.0
                || !Double.isFinite(multiplier) || multiplier < 0.0) {
            throw new IllegalArgumentException(
                    "WPIDHYD integration settings must be finite and non-negative");
        }
        integrationStep = timeStepSec;
        minimumTimeConstantMultiplier = multiplier;
    }

    @Override public boolean initStates(BaseDStabBus<?, ?> bus, Machine mach) {
        if (!validateParameters()) return false;
        prepareEffectiveParameters();
        PsseWpidhydGovernorData d = getData();
        double machineMva = mach.getRating(UnitType.mVA, bus.getNetwork().getBaseKva());
        governorToMachineBase = d.getTrate() > EPS && machineMva > EPS
                ? d.getTrate() / machineMva : 1.0;
        double pm0 = mach.getPm() / governorToMachineBase;
        double pe0 = mach.getPe() / governorToMachineBase;
        double speed0 = speedSignal();
        double turbinePower0 = pm0 + d.getD() * speed0;
        double gate0 = inversePowerCurve(turbinePower0);
        if (!finite(pm0, pe0, turbinePower0, gate0)) return false;

        effectiveGmax = Math.max(effectiveGmax, gate0);
        effectiveGmin = Math.min(effectiveGmin, gate0);
        effectivePmax = Math.max(effectivePmax, turbinePower0);
        effectivePmin = Math.min(effectivePmin, turbinePower0);
        reference = pe0 + auxiliaryInput;
        double measuredPower0 = 0.0;
        if (Math.abs(d.getReg()) > EPS && Math.abs(speed0) > EPS) {
            reference -= speed0 / d.getReg();
            measuredPower0 = speed0;
        }
        state = new State(measuredPower0, 0.0, 0.0, 0.0, 0.0,
                gate0, turbinePower0);
        oldState = state;
        committedControlError = 0.0;
        currentOutput = pm0;
        initialized = true;
        return true;
    }

    @Override public boolean nextStep(double dt, DynamicSimuMethod method,
            Machine mach, int flag) {
        if (method != DynamicSimuMethod.MODIFIED_EULER) {
            throw new InterpssRuntimeException("WPIDHYD supports MODIFIED_EULER only");
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
            throw new InterpssRuntimeException("WPIDHYD invalid integration flag: " + flag);
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
    public double getMeasuredPowerDeviation() { return measuredPower(state); }
    public double getControlError() {
        return algebraic(state, Math.max(integrationStep, EPS)).controlError;
    }
    public double getPidOutput() {
        return pidOutput(state, algebraic(state, Math.max(integrationStep, EPS)).controlError,
                Math.max(integrationStep, EPS));
    }
    public double getFirstLagOutput() {
        double dt = Math.max(integrationStep, EPS);
        double error = algebraic(state, dt).controlError;
        return effectiveTa > EPS ? firstLagOutput(state, error)
                : pidOutput(state, error, dt);
    }
    public double getSecondLagOutput() { return controllerOutput(state,
            algebraic(state, Math.max(integrationStep, EPS)).controlError,
            Math.max(integrationStep, EPS)); }
    public double getServoRate() { return servoRate(state,
            algebraic(state, Math.max(integrationStep, EPS)),
            Math.max(integrationStep, EPS)); }
    public double getGatePosition() { return state.gate; }
    public double getWaterColumnOutput() { return waterColumnOutput(state); }
    public double getEffectiveTreg() { return effectiveTreg; }
    public double getEffectiveTa() { return effectiveTa; }
    public double getEffectiveTb() { return effectiveTb; }
    public double getEffectiveTw() { return effectiveTw; }
    public double getEffectiveVelmax() { return effectiveVelmax; }
    public double getEffectiveVelmin() { return effectiveVelmin; }
    public double getEffectiveGmax() { return effectiveGmax; }
    public double getEffectiveGmin() { return effectiveGmin; }
    public double getEffectivePmax() { return effectivePmax; }
    public double getEffectivePmin() { return effectivePmin; }
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
        PsseWpidhydGovernorData d = getData();
        return finite(d.getTreg(), d.getReg(), d.getKp(), d.getKi(), d.getKd(),
                        d.getTa(), d.getTb(), d.getVelmax(), d.getVelmin(),
                        d.getGmax(), d.getGmin(), d.getTw(), d.getPmax(),
                        d.getPmin(), d.getD(), d.getG0(), d.getG1(), d.getP1(),
                        d.getG2(), d.getP2(), d.getP3(), d.getDbH(), d.getDbL(),
                        d.getTrate())
                && d.getTreg() >= 0.0 && d.getKp() >= 0.0 && d.getKi() >= 0.0
                && d.getKd() >= 0.0 && d.getTa() >= 0.0 && d.getTb() >= 0.0
                && d.getTw() >= 0.0 && d.getG0() < d.getG1()
                && d.getG1() < d.getG2() && d.getG2() < 1.0
                && d.getP1() > 0.0 && d.getP2() > d.getP1()
                && d.getP3() > d.getP2() && d.getDbH() >= 0.0
                && d.getDbL() <= 0.0 && d.getDbL() <= d.getDbH()
                && d.getTrate() >= 0.0;
    }

    private Algebraic algebraic(State s, double dt) {
        double deltaPower = machinePowerOnGovernorBase() - reference + auxiliaryInput;
        double measured = measuredPower(s);
        double controlError = measured - speedSignal();
        double controller = controllerOutput(s, controlError, dt);
        double servo = effectiveTb > EPS ? s.servoRate : controller;
        return new Algebraic(deltaPower, measured, controlError, controller, servo);
    }

    private Derivatives derivatives(State s, Algebraic a) {
        PsseWpidhydGovernorData d = getData();
        double measurementDot = effectiveTreg > EPS
                ? (d.getReg() * a.deltaPower - s.powerMeasurement) / effectiveTreg : 0.0;
        double integralDot = d.getKi() * a.controlError;
        double firstLagDot = effectiveTa > EPS
                ? ((d.getKp() - d.getKd() / effectiveTa) * a.controlError
                        + s.piIntegrator - s.firstLagState) / effectiveTa : 0.0;
        double secondLagDot = effectiveTa > EPS
                ? (firstLagOutput(s, a.controlError) - s.secondLagState) / effectiveTa : 0.0;
        double servoDot = effectiveTb > EPS
                ? (a.controllerOutput - s.servoRate) / effectiveTb : 0.0;
        double gateDot = clamp(a.servoRate, effectiveVelmin, effectiveVelmax);
        if ((s.gate >= effectiveGmax && gateDot > 0.0)
                || (s.gate <= effectiveGmin && gateDot < 0.0)) gateDot = 0.0;
        double waterDot = effectiveTw > EPS
                ? (powerCurve(s.gate) - s.waterState) / (0.5 * effectiveTw) : 0.0;
        return new Derivatives(measurementDot, integralDot, firstLagDot,
                secondLagDot, servoDot, gateDot, waterDot);
    }

    private State normalize(State s) {
        double measurement = effectiveTreg > EPS ? s.powerMeasurement : 0.0;
        double firstLag = effectiveTa > EPS ? s.firstLagState : 0.0;
        double secondLag = effectiveTa > EPS ? s.secondLagState : 0.0;
        double servo = effectiveTb > EPS ? s.servoRate : 0.0;
        double gate = clamp(s.gate, effectiveGmin, effectiveGmax);
        double water = effectiveTw > EPS ? s.waterState : powerCurve(gate);
        return new State(measurement, s.piIntegrator, firstLag, secondLag,
                servo, gate, water);
    }

    private double output(State s) {
        double turbinePower = clamp(waterColumnOutput(s), effectivePmin, effectivePmax);
        return turbinePower - getData().getD() * speedSignal();
    }

    private double measuredPower(State s) {
        double deltaPower = machinePowerOnGovernorBase() - reference + auxiliaryInput;
        return effectiveTreg > EPS ? s.powerMeasurement : getData().getReg() * deltaPower;
    }

    private double pidOutput(State s, double error, double dt) {
        double derivative = dt > EPS
                ? getData().getKd() * (error - committedControlError) / dt : 0.0;
        return getData().getKp() * error + s.piIntegrator + derivative;
    }

    private double firstLagOutput(State s, double error) {
        return s.firstLagState + getData().getKd() / effectiveTa * error;
    }

    private double controllerOutput(State s, double error, double dt) {
        return effectiveTa > EPS ? s.secondLagState : pidOutput(s, error, dt);
    }

    private double servoRate(State s, Algebraic a, double dt) {
        return effectiveTb > EPS ? s.servoRate
                : controllerOutput(s, a.controlError, dt);
    }

    private double waterColumnOutput(State s) {
        double turbinePower = powerCurve(s.gate);
        return effectiveTw > EPS ? -2.0 * turbinePower + 3.0 * s.waterState
                : turbinePower;
    }

    private double speedSignal() {
        return applySpeedDeadband(getMachine().getSpeed() - 1.0);
    }

    private double machinePowerOnGovernorBase() {
        return getMachine().getPe() / governorToMachineBase;
    }

    private double powerCurve(double gate) {
        PsseWpidhydGovernorData d = getData();
        if (gate <= d.getG1()) return interpolate(gate, d.getG0(), 0.0,
                d.getG1(), d.getP1());
        if (gate <= d.getG2()) return interpolate(gate, d.getG1(), d.getP1(),
                d.getG2(), d.getP2());
        return interpolate(gate, d.getG2(), d.getP2(), 1.0, d.getP3());
    }

    private double inversePowerCurve(double power) {
        PsseWpidhydGovernorData d = getData();
        if (power <= d.getP1()) return interpolate(power, 0.0, d.getG0(),
                d.getP1(), d.getG1());
        if (power <= d.getP2()) return interpolate(power, d.getP1(), d.getG1(),
                d.getP2(), d.getG2());
        return interpolate(power, d.getP2(), d.getG2(), d.getP3(), 1.0);
    }

    private void prepareEffectiveParameters() {
        PsseWpidhydGovernorData d = getData();
        double minimum = minimumTimeConstantMultiplier * integrationStep;
        effectiveTreg = correctedOptionalLag(d.getTreg(), minimum);
        effectiveTa = d.getTa() > 0.0 && d.getTa() < 0.5 * minimum
                ? 0.5 * minimum : d.getTa();
        effectiveTb = correctedPositiveTime(d.getTb(), minimum);
        effectiveTw = correctedPositiveTime(d.getTw(), minimum);
        double vmax = Math.max(d.getVelmax(), d.getVelmin());
        double vmin = Math.min(d.getVelmax(), d.getVelmin());
        effectiveVelmax = Math.abs(vmax);
        effectiveVelmin = -Math.abs(vmin);
        effectiveGmax = Math.max(d.getGmax(), d.getGmin());
        effectiveGmin = Math.min(d.getGmax(), d.getGmin());
        effectivePmax = Math.max(d.getPmax(), d.getPmin());
        effectivePmin = Math.min(d.getPmax(), d.getPmin());
    }

    private static double correctedOptionalLag(double value, double minimum) {
        if (value > 0.0 && value < 0.5 * minimum) return 0.0;
        return value > 0.0 && value < minimum ? minimum : value;
    }

    private static double correctedPositiveTime(double value, double minimum) {
        return value > 0.0 && value < minimum ? minimum : value;
    }

    private static double interpolate(double x, double x0, double y0,
            double x1, double y1) {
        return y0 + (x - x0) * (y1 - y0) / (x1 - x0);
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Algebraic(double deltaPower, double measuredPower,
            double controlError, double controllerOutput, double servoRate) { }

    private record Derivatives(double powerMeasurement, double piIntegrator,
            double firstLagState, double secondLagState, double servoRate,
            double gate, double waterState) {
        static Derivatives zero() { return new Derivatives(0, 0, 0, 0, 0, 0, 0); }
    }

    private record State(double powerMeasurement, double piIntegrator,
            double firstLagState, double secondLagState, double servoRate,
            double gate, double waterState) {
        static State zero() { return new State(0, 0, 0, 0, 0, 0, 0); }

        State plus(Derivatives d, double dt) {
            return new State(powerMeasurement + d.powerMeasurement * dt,
                    piIntegrator + d.piIntegrator * dt,
                    firstLagState + d.firstLagState * dt,
                    secondLagState + d.secondLagState * dt,
                    servoRate + d.servoRate * dt, gate + d.gate * dt,
                    waterState + d.waterState * dt);
        }

        State plusAverage(Derivatives a, Derivatives b, double dt) {
            return plus(new Derivatives((a.powerMeasurement + b.powerMeasurement) / 2.0,
                    (a.piIntegrator + b.piIntegrator) / 2.0,
                    (a.firstLagState + b.firstLagState) / 2.0,
                    (a.secondLagState + b.secondLagState) / 2.0,
                    (a.servoRate + b.servoRate) / 2.0,
                    (a.gate + b.gate) / 2.0,
                    (a.waterState + b.waterState) / 2.0), dt);
        }
    }
}
