package org.interpss.dstab.control.gov.psse.hygovr;

import com.interpss.common.exp.InterpssRuntimeException;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.deqn.AbstractGovernor;
import com.interpss.dstab.mach.Machine;
import org.interpss.dstab.control.util.IntegrationStepAware;
import org.interpss.numeric.datatype.Unit.UnitType;

/**
 * PSS/E HYGOVR1 fourth-order lead-lag digital hydro governor.
 *
 * <p>The implementation follows the published HYGOVR signal path, with the
 * native HYGOVR1 linear gate/power relationship and 26-CON PSS/E layout.
 * It includes the speed and gate deadbands, four lead-lag blocks, integral
 * governor, gate servo/rate/position limits, power feedback, nonlinear water
 * column, turbine damping, and turbine-to-machine base conversion.</p>
 */
public class PsseHygovrGovernor extends AbstractGovernor implements IntegrationStepAware {
    private static final double SMALL = 1.0e-9;
    private static final double MIN_FLOW = 1.0e-5;

    private State state = State.zero();
    private State oldState = State.zero();
    private Derivatives oldDerivatives = Derivatives.zero();
    private Effective effective;
    private double integrationStep;
    private double minimumTimeConstantMultiplier = 1.0;
    private double governorToMachineBase = 1.0;
    private double governorBaseMva;
    private double reference;
    private double currentGate;
    private double committedGate;
    private double currentHead = 1.0;
    private double currentOutput;
    private double currentProcessedSpeed;
    private DeadbandMode speedDeadbandMode = DeadbandMode.NEUTRAL;
    private boolean initialized;

    public PsseHygovrGovernor(String id, String name, String category) {
        super(id, name, category);
        _data = new PsseHygovrGovernorData();
    }

    public PsseHygovrGovernorData getData() { return (PsseHygovrGovernorData) _data; }

    @Override public void configureIntegrationStep(double timeStepSec) {
        configureIntegrationStep(timeStepSec, 1.0);
    }

    public void configureIntegrationStep(double timeStepSec, double multiplier) {
        if (!Double.isFinite(timeStepSec) || timeStepSec < 0.0
                || !Double.isFinite(multiplier) || multiplier < 0.0) {
            throw new IllegalArgumentException("HYGOVR1 integration settings must be finite and non-negative");
        }
        integrationStep = timeStepSec;
        minimumTimeConstantMultiplier = multiplier;
    }

    @Override public boolean initStates(BaseDStabBus<?, ?> bus, Machine mach) {
        if (!validateParameters()) return false;
        effective = correctedParameters();
        double machineMva = mach.getRating(UnitType.mVA, bus.getNetwork().getBaseKva());
        governorBaseMva = getData().getTrate() > SMALL ? getData().getTrate() : machineMva;
        governorToMachineBase = machineMva > SMALL ? governorBaseMva / machineMva : 1.0;
        double pm0 = mach.getPm() / governorToMachineBase;
        double pe0 = mach.getPe() / governorToMachineBase;
        if (!finite(pm0, pe0) || effective.at <= SMALL) return false;

        double gate0 = pm0 / effective.at + getData().getQnl();
        if (!Double.isFinite(gate0) || gate0 < MIN_FLOW) return false;
        effective = effective.withGateLimits(Math.max(effective.pmax, gate0),
                Math.min(effective.pmin, gate0));
        reference = getData().getR() * pe0;
        state = new State(0.0, 0.0, 0.0, 0.0, 0.0, gate0,
                0.0, gate0, pe0, gate0);
        oldState = state;
        committedGate = gate0;
        currentGate = gate0;
        currentHead = 1.0;
        currentOutput = pm0;
        speedDeadbandMode = DeadbandMode.NEUTRAL;
        initialized = true;
        return true;
    }

    @Override public boolean nextStep(double dt, DynamicSimuMethod method, Machine mach, int flag) {
        if (method != DynamicSimuMethod.MODIFIED_EULER) {
            throw new InterpssRuntimeException("HYGOVR1 supports MODIFIED_EULER only");
        }
        if (!initialized) return false;
        if (flag == 0) {
            oldState = state;
            Algebraic a = algebraic(oldState, committedGate, speedDeadbandMode);
            oldDerivatives = derivatives(oldState, a);
            state = constrain(oldState.plus(oldDerivatives, dt));
            updateCurrent(algebraic(state, committedGate, speedDeadbandMode));
        } else if (flag == 1) {
            Algebraic predicted = algebraic(state, committedGate, speedDeadbandMode);
            Derivatives corrected = derivatives(state, predicted);
            state = constrain(oldState.plusAverage(oldDerivatives, corrected, dt));
            Algebraic result = algebraic(state, committedGate, speedDeadbandMode);
            updateCurrent(result);
            committedGate = currentGate;
            speedDeadbandMode = result.deadbandMode;
        } else {
            throw new InterpssRuntimeException("HYGOVR1 invalid integration flag: " + flag);
        }
        return true;
    }

    @Override public double getOutput(Machine mach) { return currentOutput * governorToMachineBase; }
    @Override public void setRefPoint(double value) { reference = value; }

    public boolean validateParameters() {
        PsseHygovrGovernorData d = getData();
        return finite(d.getDb1(), d.getErr(), d.getTd(), d.getT1(), d.getT2(), d.getT3(),
                d.getT4(), d.getT5(), d.getT6(), d.getT7(), d.getT8(), d.getKp(),
                d.getR(), d.getTt(), d.getKg(), d.getTp(), d.getVelopen(),
                d.getVelclose(), d.getPmax(), d.getPmin(), d.getDb2(), d.getTw(),
                d.getAt(), d.getDturb(), d.getQnl(), d.getTrate())
                && d.getTd() >= 0.0 && d.getT2() >= 0.0 && d.getT4() >= 0.0
                && d.getT6() >= 0.0 && d.getT8() >= 0.0 && d.getTt() >= 0.0
                && d.getTp() >= 0.0 && d.getTw() >= 0.0 && d.getTrate() >= 0.0;
    }

    public double getGatePosition() { return state.gate; }
    public double getGateOutput() { return currentGate; }
    public double getWaterFlow() { return state.flow; }
    public double getHead() { return currentHead; }
    public double getProcessedSpeedDeviation() { return currentProcessedSpeed; }
    /** PowerWorld HYGOVR state 1, the filtered speed-deviation input. */
    public double getInputSpeedState() { return state.speedFilter; }
    /** PowerWorld HYGOVR states 2-5, the four cascaded lag coordinates. */
    public double getLeadLag12State() { return state.ll12; }
    public double getLeadLag34State() { return state.ll34; }
    public double getLeadLag56State() { return state.ll56; }
    public double getLeadLag78State() { return state.ll78; }
    /** PowerWorld HYGOVR state 6, the integral governor state. */
    public double getGovernorState() { return state.control; }
    /** PowerWorld HYGOVR state 7, the gate-servo velocity state. */
    public double getVelocityState() { return state.valve; }
    /** PowerWorld HYGOVR state 10, filtered electrical power on governor base. */
    public double getElectricalPowerState() { return state.pe; }
    public double getGovernorBaseMva() { return governorBaseMva; }
    public double getEffectiveTd() { return effective.td; }
    public double getEffectiveTp() { return effective.tp; }
    public double getEffectiveTt() { return effective.tt; }
    public double getEffectiveTw() { return effective.tw; }
    public double getEffectivePmax() { return effective.pmax; }
    public double getEffectivePmin() { return effective.pmin; }

    private Algebraic algebraic(State s, double priorGate, DeadbandMode priorMode) {
        double speedDeviation = getMachine().getSpeed() - 1.0;
        DeadbandResult db = deadband(speedDeviation, priorMode);
        double speedInput = db.output;
        double tdOutput = effective.td > SMALL ? s.speedFilter : speedInput;
        double y12 = leadLagOutput(tdOutput, s.ll12, getData().getT1(), effective.t2);
        double y34 = leadLagOutput(y12, s.ll34, getData().getT3(), effective.t4);
        double y56 = leadLagOutput(y34, s.ll56, getData().getT5(), effective.t6);
        double y78 = leadLagOutput(y56, s.ll78, getData().getT7(), effective.t8);
        double measuredPe = effective.tt > SMALL ? s.pe : machinePowerOnGovernorBase();
        double controlError = reference - y78 - getData().getR() * measuredPe;
        double gate = backlash(s.gate, priorGate, effective.db2Pu);
        double flow = s.flow;
        if (Math.abs(gate) < .005) flow = gate;
        double head = square(flow / Math.max(MIN_FLOW, Math.abs(gate)));
        double pmech = effective.at * head * (flow - getData().getQnl())
                - Math.max(0.0, getData().getDturb()) * speedDeviation * gate;
        return new Algebraic(db.mode, speedInput, tdOutput, y12, y34, y56, y78,
                measuredPe, controlError, gate, flow, head, pmech);
    }

    private Derivatives derivatives(State s, Algebraic a) {
        double speedFilterDot = effective.td > SMALL ? (a.speedInput - s.speedFilter) / effective.td : 0.0;
        double ll12Dot = lagDerivative(a.tdOutput, s.ll12, effective.t2);
        double ll34Dot = lagDerivative(a.y12, s.ll34, effective.t4);
        double ll56Dot = lagDerivative(a.y34, s.ll56, effective.t6);
        double ll78Dot = lagDerivative(a.y56, s.ll78, effective.t8);
        double controlDot = getData().getKp() * a.controlError;
        double valveDot = effective.tp > SMALL
                ? (getData().getKg() * (s.control - a.gate) - s.valve) / effective.tp : 0.0;
        double gateDot = effective.tp > SMALL ? s.valve
                : getData().getKg() * (s.control - a.gate);
        gateDot = clamp(gateDot, effective.velclose, effective.velopen);
        if ((s.gate >= effective.pmax && gateDot > 0.0)
                || (s.gate <= effective.pmin && gateDot < 0.0)) gateDot = 0.0;
        double peDot = effective.tt > SMALL
                ? (machinePowerOnGovernorBase() - s.pe) / effective.tt : 0.0;
        double flowDot = effective.tw > SMALL ? (1.0 - a.head) / effective.tw : 0.0;
        if (s.flow <= MIN_FLOW && flowDot < 0.0) flowDot = 0.0;
        return new Derivatives(speedFilterDot, ll12Dot, ll34Dot, ll56Dot, ll78Dot,
                controlDot, valveDot, gateDot, peDot, flowDot);
    }

    private State constrain(State s) {
        return new State(s.speedFilter, s.ll12, s.ll34, s.ll56, s.ll78,
                s.control, clamp(s.valve, effective.velclose, effective.velopen),
                clamp(s.gate, effective.pmin, effective.pmax), s.pe,
                Math.max(MIN_FLOW, s.flow));
    }

    private void updateCurrent(Algebraic a) {
        currentGate = a.gate;
        currentHead = a.head;
        currentOutput = a.pmech;
        currentProcessedSpeed = a.y78;
    }

    private Effective correctedParameters() {
        PsseHygovrGovernorData d = getData();
        double minimum = integrationStep * minimumTimeConstantMultiplier;
        double max = Math.max(d.getPmax(), d.getPmin());
        double min = Math.min(d.getPmax(), d.getPmin());
        double close = -Math.abs(d.getVelclose());
        double open = Math.abs(d.getVelopen());
        double base = d.getTrate() > SMALL ? d.getTrate() : 1.0;
        return new Effective(correctedOptionalTime(d.getTd(), minimum),
                correctedOptionalTime(d.getT2(), minimum), correctedOptionalTime(d.getT4(), minimum),
                correctedOptionalTime(d.getT6(), minimum), correctedOptionalTime(d.getT8(), minimum),
                correctedHalfStepTime(d.getTt(), minimum), correctedHalfStepTime(d.getTp(), minimum),
                d.getTw() > 0.0 && d.getTw() < minimum ? minimum : d.getTw(),
                open, close, max, min, Math.abs(d.getDb2()) / base,
                d.getAt() <= 0.0 ? Math.max(minimum, SMALL) : d.getAt());
    }

    private DeadbandResult deadband(double value, DeadbandMode prior) {
        // DB1 and ERR are specified in Hz; convert them to speed pu on system frequency.
        double baseHz = getMachine().getDStabBus().getNetwork().getFrequency();
        double width = baseHz > SMALL ? Math.abs(getData().getDb1()) / baseHz : 0.0;
        double hysteresis = baseHz > SMALL ? Math.abs(getData().getErr()) / baseHz : 0.0;
        DeadbandMode mode = prior;
        if (mode == DeadbandMode.NEUTRAL) {
            if (value > width) mode = DeadbandMode.HIGH;
            else if (value < -width) mode = DeadbandMode.LOW;
        } else if (mode == DeadbandMode.HIGH && value <= width - hysteresis) mode = DeadbandMode.NEUTRAL;
        else if (mode == DeadbandMode.LOW && value >= -width + hysteresis) mode = DeadbandMode.NEUTRAL;
        double output = mode == DeadbandMode.HIGH ? value - Math.max(0.0, width - hysteresis)
                : mode == DeadbandMode.LOW ? value + Math.max(0.0, width - hysteresis) : 0.0;
        return new DeadbandResult(mode, output);
    }

    private static double backlash(double input, double priorOutput, double width) {
        if (width <= SMALL) return input;
        if (input > priorOutput + width) return input - width;
        if (input < priorOutput - width) return input + width;
        return priorOutput;
    }

    private static double leadLagOutput(double input, double lagState, double lead, double lag) {
        return lag > SMALL ? lagState + lead / lag * (input - lagState) : input;
    }
    private static double lagDerivative(double input, double state, double lag) {
        return lag > SMALL ? (input - state) / lag : 0.0;
    }
    private double machinePowerOnGovernorBase() { return getMachine().getPe() / governorToMachineBase; }
    private static double correctedOptionalTime(double value, double minimum) {
        return value > 0.0 && value < minimum ? minimum : value;
    }
    private static double correctedHalfStepTime(double value, double minimum) {
        if (value <= 0.0 || minimum <= 0.0 || value >= minimum) return value;
        return value < 0.5 * minimum ? 0.0 : minimum;
    }
    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
    private static double square(double value) { return value * value; }
    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private enum DeadbandMode { LOW, NEUTRAL, HIGH }
    private record DeadbandResult(DeadbandMode mode, double output) { }
    private record Algebraic(DeadbandMode deadbandMode, double speedInput, double tdOutput,
            double y12, double y34, double y56, double y78, double measuredPe,
            double controlError, double gate, double flow, double head, double pmech) { }
    private record Derivatives(double speedFilter, double ll12, double ll34, double ll56,
            double ll78, double control, double valve, double gate, double pe, double flow) {
        static Derivatives zero() { return new Derivatives(0, 0, 0, 0, 0, 0, 0, 0, 0, 0); }
    }
    private record State(double speedFilter, double ll12, double ll34, double ll56,
            double ll78, double control, double valve, double gate, double pe, double flow) {
        static State zero() { return new State(0, 0, 0, 0, 0, 0, 0, 0, 0, 0); }
        State plus(Derivatives d, double dt) {
            return new State(speedFilter + d.speedFilter * dt, ll12 + d.ll12 * dt,
                    ll34 + d.ll34 * dt, ll56 + d.ll56 * dt, ll78 + d.ll78 * dt,
                    control + d.control * dt, valve + d.valve * dt, gate + d.gate * dt,
                    pe + d.pe * dt, flow + d.flow * dt);
        }
        State plusAverage(Derivatives a, Derivatives b, double dt) {
            return plus(new Derivatives((a.speedFilter + b.speedFilter) / 2.0,
                    (a.ll12 + b.ll12) / 2.0, (a.ll34 + b.ll34) / 2.0,
                    (a.ll56 + b.ll56) / 2.0, (a.ll78 + b.ll78) / 2.0,
                    (a.control + b.control) / 2.0, (a.valve + b.valve) / 2.0,
                    (a.gate + b.gate) / 2.0, (a.pe + b.pe) / 2.0,
                    (a.flow + b.flow) / 2.0), dt);
        }
    }
    private record Effective(double td, double t2, double t4, double t6, double t8,
            double tt, double tp, double tw, double velopen, double velclose,
            double pmax, double pmin, double db2Pu, double at) {
        Effective withGateLimits(double max, double min) {
            return new Effective(td, t2, t4, t6, t8, tt, tp, tw, velopen,
                    velclose, max, min, db2Pu, at);
        }
    }
}
