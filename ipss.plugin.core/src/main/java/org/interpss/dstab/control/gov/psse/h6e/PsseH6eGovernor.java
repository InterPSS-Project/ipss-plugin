package org.interpss.dstab.control.gov.psse.h6e;

import java.util.ArrayList;
import java.util.List;

import com.interpss.common.exp.InterpssRuntimeException;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.deqn.AbstractGovernor;
import com.interpss.dstab.mach.Machine;
import org.interpss.dstab.control.util.IntegrationStepAware;
import org.interpss.numeric.datatype.Unit.UnitType;

/** WECC H6E / PSS/E H6EU1 Kaplan hydro turbine-governor. */
public class PsseH6eGovernor extends AbstractGovernor implements IntegrationStepAware {
    private static final double SMALL = 1.0e-9;
    private static final double MIN_FLOW = 1.0e-4;

    private State state = State.zero();
    private State oldState = State.zero();
    private Derivatives oldDerivatives = Derivatives.zero();
    private double integrationStep;
    private double minimumTimeConstantMultiplier = 1.0;
    private Effective effective;
    private double governorBaseMva;
    private double governorToMachineBase = 1.0;
    private double targetPref;
    private double movingPref;
    private double auxiliaryInput;
    private double committedGateCommand;
    private double committedGate;
    private double committedBlade;
    private double committedBladeDemand;
    private double currentGateCommand;
    private double currentGate;
    private double currentBlade;
    private double currentHead;
    private double currentOutput;
    private DeadbandMode deadbandMode = DeadbandMode.NEUTRAL;
    private boolean initialized;

    public PsseH6eGovernor(String id, String name, String category) {
        super(id, name, category);
        _data = new PsseH6eGovernorData();
    }

    public PsseH6eGovernorData getData() { return (PsseH6eGovernorData) _data; }

    @Override public void configureIntegrationStep(double timeStepSec) {
        configureIntegrationStep(timeStepSec, 1.0);
    }

    public void configureIntegrationStep(double timeStepSec, double multiplier) {
        if (!Double.isFinite(timeStepSec) || timeStepSec < 0.0
                || !Double.isFinite(multiplier) || multiplier < 0.0) {
            throw new IllegalArgumentException("H6E integration settings must be finite and non-negative");
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
        if (!Double.isFinite(pm0) || pm0 < 0.0) return false;

        double head0 = effective.hdam;
        double powerAtUnitHead = pm0 / head0;
        double q0 = inversePowerCurve(powerAtUnitHead);
        if (!Double.isFinite(q0) || q0 < MIN_FLOW) return false;
        double flowArea0 = q0 / Math.sqrt(head0);
        double gate0 = inverseFlowArea(flowArea0);
        if (!Double.isFinite(gate0)) return false;
        double gmax = Math.max(effective.gmax, gate0);
        double gmin = Math.min(effective.gmin, gate0);
        effective = effective.withGateLimits(gmax, gmin);
        double blade0 = bladeCurve(gate0);

        if (effective.fd == 0) {
            targetPref = 1.0 + gate0 * getData().getRg();
            state = new State(pe0, 1.0, gate0, 0.0, 0.0, gate0, blade0, blade0, q0);
        } else {
            targetPref = 1.0 + pe0 * getData().getRe();
            double integrator = gate0 - getData().getKp() * (targetPref - 1.0);
            state = new State(pe0, 1.0, integrator, 0.0, 0.0, gate0, blade0, blade0, q0);
        }
        oldState = state;
        movingPref = targetPref;
        committedGateCommand = gate0;
        currentGateCommand = gate0;
        committedGate = gate0;
        currentGate = gate0;
        committedBladeDemand = blade0;
        committedBlade = blade0;
        currentBlade = blade0;
        currentHead = head0;
        currentOutput = pm0;
        deadbandMode = DeadbandMode.NEUTRAL;
        initialized = true;
        return true;
    }

    @Override public boolean nextStep(double dt, DynamicSimuMethod method, Machine mach, int flag) {
        if (method != DynamicSimuMethod.MODIFIED_EULER) {
            throw new InterpssRuntimeException("H6E supports MODIFIED_EULER only");
        }
        if (!initialized) return false;
        if (flag == 0) {
            updateMovingReference(dt);
            oldState = state;
            Algebraic a = algebraic(oldState, committedGateCommand, committedGate,
                    committedBladeDemand, committedBlade, deadbandMode);
            oldDerivatives = derivatives(oldState, a);
            state = constrain(oldState.plus(oldDerivatives, dt));
            updateCurrent(algebraic(state, committedGateCommand, committedGate,
                    committedBladeDemand, committedBlade, deadbandMode));
        } else if (flag == 1) {
            Algebraic predicted = algebraic(state, committedGateCommand, committedGate,
                    committedBladeDemand, committedBlade, deadbandMode);
            Derivatives corrected = derivatives(state, predicted);
            state = constrain(oldState.plusAverage(oldDerivatives, corrected, dt));
            Algebraic result = algebraic(state, committedGateCommand, committedGate,
                    committedBladeDemand, committedBlade, deadbandMode);
            updateCurrent(result);
            committedGateCommand = currentGateCommand;
            committedGate = currentGate;
            committedBladeDemand = result.bladeDemand;
            committedBlade = currentBlade;
            deadbandMode = result.deadbandMode;
        } else {
            throw new InterpssRuntimeException("H6E invalid integration flag: " + flag);
        }
        return true;
    }

    @Override public double getOutput(Machine mach) { return currentOutput * governorToMachineBase; }
    @Override public void setRefPoint(double value) { targetPref = value; }

    public void setAuxiliaryInput(double value) { auxiliaryInput = value; }
    public double getAuxiliaryInput() { return auxiliaryInput; }
    public double getGovernorBaseMva() { return governorBaseMva; }
    public double getGateCommand() { return currentGateCommand; }
    public double getGatePosition() { return state.gate; }
    public double getGateOutput() { return currentGate; }
    public double getBladePosition() { return currentBlade; }
    public double getWaterFlow() { return state.flow; }
    public double getHead() { return currentHead; }
    public double getEffectiveGmax() { return effective.gmax; }
    public double getEffectiveGmin() { return effective.gmin; }
    public double getEffectiveKi() { return effective.ki; }
    public double getEffectiveTw() { return effective.tw; }
    public double getEffectiveTsp() { return effective.tsp; }
    public double getEffectiveTpe() { return effective.tpe; }
    public double getEffectiveTd() { return effective.td; }
    public double getEffectiveTg() { return effective.tg; }
    public double getEffectiveBgvmin() { return effective.bgvmin; }
    public double getMovingReference() { return movingPref; }

    public boolean validateParameters() {
        PsseH6eGovernorData d = getData();
        if (!finite(d.getRe(), d.getRg(), d.getTpe(), d.getTsp(), d.getKp(), d.getKi(),
                d.getKd(), d.getTd(), d.getVelm(), d.getGmax(), d.getGmin(), d.getBuf(),
                d.getBuv(), d.getKg(), d.getTg(), d.getBlg(), d.getDbbd(), d.getTbd(),
                d.getBlb(), d.getDbbs(), d.getTbs(), d.getBgvmin(), d.getBlv(),
                d.getDturb(), d.getPgc(), d.getDeff(), d.getHdam(), d.getTw(),
                d.getSprate(), d.getDb1(), d.getEps(), d.getTrate())) return false;
        if (d.getTpe() < 0.0 || d.getTd() < 0.0 || d.getTg() < 0.0
                || d.getTbd() < 0.0 || d.getTbs() < 0.0 || d.getTw() < 0.0
                || d.getHdam() <= 0.0 || d.getTrate() < 0.0
                || d.getBgvmin() < 0.0 || d.getBgvmin() >= 0.99999) return false;
        return validCurves();
    }

    private Effective correctedParameters() {
        PsseH6eGovernorData d = getData();
        double minimum = integrationStep * minimumTimeConstantMultiplier;
        return new Effective(d.getFd() == 0 ? 0 : 1,
                correctedOptionalTime(d.getTpe(), minimum), correctedSignedTsp(d.getTsp(), minimum),
                Math.max(1.0e-6, d.getKi()), correctedOptionalTime(d.getTd(), minimum),
                Math.abs(d.getVelm()), Math.max(d.getGmax(), d.getGmin()),
                Math.min(d.getGmax(), d.getGmin()), Math.abs(d.getBuv()),
                correctedOptionalTime(d.getTg(), minimum), Math.abs(d.getBlg()),
                Math.abs(d.getDbbd()), correctedOptionalTime(d.getTbd(), minimum),
                Math.abs(d.getBlb()), Math.abs(d.getDbbs()),
                correctedOptionalTime(d.getTbs(), minimum), Math.abs(d.getBlv()),
                Math.max(0.0, d.getDturb()), Math.max(0.0, d.getDeff()),
                clamp(d.getBgvmin(), 1.0e-5, 0.99999), d.getHdam(),
                d.getTw() > 0.0 && d.getTw() < minimum ? minimum : d.getTw());
    }

    private Algebraic algebraic(State s, double priorGateCommand, double priorGate,
            double priorBladeDemand, double priorBlade, DeadbandMode priorDeadbandMode) {
        PsseH6eGovernorData d = getData();
        double rawSpeed = effective.tsp < 0.0 ? getMachine().getDStabBus().getFreq()
                : getMachine().getSpeed();
        double measuredSpeed = Math.abs(effective.tsp) > SMALL ? s.speed : rawSpeed;
        DeadbandResult db = deadband(measuredSpeed - 1.0, priorDeadbandMode);
        double speedForControl = 1.0 + db.output;
        double speedError = movingPref - speedForControl + auxiliaryInput;
        double measuredPe = effective.tpe > SMALL ? s.pe : machinePowerOnGovernorBase();
        double propInput = effective.fd == 0
                ? speedError - d.getRg() * priorGateCommand : speedError;
        double integralInput = effective.fd == 0
                ? propInput : speedError - d.getRe() * measuredPe;
        double derivative = effective.td > SMALL
                ? (measuredSpeed - 1.0 - s.derivativeLag) / effective.td : 0.0;
        double command = clamp(d.getKp() * propInput + s.integrator - d.getKd() * derivative,
                effective.gmin, effective.gmax);
        double gate = backlash(s.gate, priorGate, effective.blg);
        double bladeCommand = bladeCurve(command);
        double bladeDemand;
        if (effective.tbd > SMALL) {
            bladeDemand = s.bladeDemand;
        } else {
            bladeDemand = slidingDeadband(bladeCommand, priorBladeDemand, effective.dbbd);
        }
        double bladeServo = effective.tbs > SMALL ? s.bladeServo : bladeDemand;
        double blade = backlash(bladeServo, priorBlade, effective.blb);
        double bladeArea = effective.bgvmin + (1.0 - effective.bgvmin) * blade;
        double flowArea = bladeArea * gate;
        double flow = s.flow;
        if (flowArea < 0.005) flow = Math.sqrt(effective.hdam) * flowArea;
        double head = square(flow / Math.max(SMALL, flowArea));
        double hydraulic = turbinePower(flow, head, bladeDemand, blade);
        double pmech = hydraulic - effective.dturb * (getMachine().getSpeed() - 1.0) * gate;
        return new Algebraic(db.mode, rawSpeed, measuredSpeed, measuredPe, propInput,
                integralInput, command, gate, bladeCommand, bladeDemand, blade, flowArea,
                flow, head, pmech);
    }

    private Derivatives derivatives(State s, Algebraic a) {
        PsseH6eGovernorData d = getData();
        double peDot = effective.tpe > SMALL ? (machinePowerOnGovernorBase() - s.pe) / effective.tpe : 0.0;
        double speedDot = Math.abs(effective.tsp) > SMALL
                ? (a.rawSpeed - s.speed) / Math.abs(effective.tsp) : 0.0;
        double intDot = effective.ki * a.integralInput;
        double intMax = effective.gmax - d.getKp() * a.propInput;
        double intMin = effective.gmin - d.getKp() * a.propInput;
        if ((s.integrator >= intMax && intDot > 0.0) || (s.integrator <= intMin && intDot < 0.0)) intDot = 0.0;
        double derivativeDot = effective.td > SMALL
                ? (a.measuredSpeed - 1.0 - s.derivativeLag) / effective.td : 0.0;
        double valveDot = effective.tg > SMALL
                ? (d.getKg() * (a.gateCommand - s.gate) - s.valve) / effective.tg : 0.0;
        if ((s.valve >= effective.velm && valveDot > 0.0)
                || (s.valve <= -effective.velm && valveDot < 0.0)) valveDot = 0.0;
        double gateDot = effective.tg > SMALL ? s.valve : clamp(d.getKg() * (a.gateCommand - s.gate),
                -effective.velm, effective.velm);
        if (s.gate < d.getBuf() && gateDot < -effective.buv) gateDot = -effective.buv;
        if ((s.gate >= effective.gmax && gateDot > 0.0) || (s.gate <= effective.gmin && gateDot < 0.0)) gateDot = 0.0;
        double bladeDemandDot = 0.0;
        if (effective.tbd > SMALL) {
            bladeDemandDot = slidingDeadbandDifference(a.bladeCommand - s.bladeDemand, effective.dbbd)
                    / effective.tbd;
        }
        double bladeServoDot = 0.0;
        if (effective.tbs > SMALL) {
            bladeServoDot = slidingDeadbandDifference(a.bladeDemand - s.bladeServo, effective.dbbs)
                    / effective.tbs;
            bladeServoDot = clamp(bladeServoDot, -effective.blv, effective.blv);
        }
        double flowDot = effective.tw > SMALL ? (effective.hdam - a.head) / effective.tw : 0.0;
        if (s.flow <= MIN_FLOW && flowDot < 0.0) flowDot = 0.0;
        return new Derivatives(peDot, speedDot, intDot, derivativeDot, valveDot, gateDot,
                bladeDemandDot, bladeServoDot, flowDot);
    }

    private State constrain(State s) {
        double prop = currentPropInput(s);
        return new State(s.pe, s.speed,
                clamp(s.integrator, effective.gmin - getData().getKp() * prop,
                        effective.gmax - getData().getKp() * prop),
                s.derivativeLag, clamp(s.valve, -effective.velm, effective.velm),
                clamp(s.gate, effective.gmin, effective.gmax), s.bladeDemand,
                s.bladeServo, Math.max(MIN_FLOW, s.flow));
    }

    private double currentPropInput(State s) {
        double speed = Math.abs(effective.tsp) > SMALL ? s.speed
                : (effective.tsp < 0.0 ? getMachine().getDStabBus().getFreq() : getMachine().getSpeed());
        double conditionedDeviation = deadband(speed - 1.0, deadbandMode).output;
        double speedError = movingPref - (1.0 + conditionedDeviation) + auxiliaryInput;
        return effective.fd == 0 ? speedError - getData().getRg() * committedGateCommand : speedError;
    }

    private void updateCurrent(Algebraic a) {
        currentGateCommand = a.gateCommand;
        currentGate = a.gate;
        currentBlade = a.blade;
        currentHead = a.head;
        currentOutput = a.pmech;
    }

    private void updateMovingReference(double dt) {
        double rate = getData().getSprate();
        if (rate <= 0.0 || dt <= 0.0) { movingPref = targetPref; return; }
        movingPref += clamp(targetPref - movingPref, -rate * dt, rate * dt);
    }

    private double machinePowerOnGovernorBase() { return getMachine().getPe() / governorToMachineBase; }

    private double turbinePower(double flow, double head, double bladeDemand, double blade) {
        Curve pq = powerCurve();
        double power;
        if (flow < pq.x[0] && pq.x[0] > SMALL) {
            power = getData().getPgc() * (flow - pq.x[0]) / pq.x[0];
        } else {
            power = interpolateClamped(flow, pq.x, pq.y)
                    - effective.deff * square(bladeDemand - blade);
        }
        return head * power;
    }

    public double bladeCurve(double gate) {
        Curve curve = curve(getData().getGv(), getData().getBgv());
        return interpolateClamped(gate, curve.x, curve.y);
    }

    public double flowArea(double gate) {
        double b = bladeCurve(gate);
        double minimum = effective == null
                ? clamp(getData().getBgvmin(), 1.0e-5, 0.99999) : effective.bgvmin;
        return gate * (minimum + (1.0 - minimum) * b);
    }

    public double powerCurve(double flow) {
        Curve curve = powerCurve();
        return interpolateClamped(flow, curve.x, curve.y);
    }

    private Curve powerCurve() {
        double[] gv = getData().getGv();
        double[] bgv = getData().getBgv();
        double[] pgv = getData().getPgv();
        List<Double> q = new ArrayList<>();
        List<Double> p = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            if (i == 0 || gv[i] > 0.0) {
                double minimum = effective == null
                        ? clamp(getData().getBgvmin(), 1.0e-5, 0.99999) : effective.bgvmin;
                q.add(gv[i] * (minimum + (1.0 - minimum) * bgv[i]));
                p.add(pgv[i]);
            }
        }
        return new Curve(toArray(q), toArray(p));
    }

    private double inversePowerCurve(double power) {
        Curve curve = powerCurve();
        return interpolateClamped(power, curve.y, curve.x);
    }

    private double inverseFlowArea(double area) {
        double lo = 0.0;
        double hi = Math.max(1.0, Math.max(getData().getGmax(), getData().getGmin()));
        for (int i = 0; i < 100; i++) {
            double mid = (lo + hi) * 0.5;
            if (flowArea(mid) < area) lo = mid; else hi = mid;
        }
        return (lo + hi) * 0.5;
    }

    private boolean validCurves() {
        Curve blade = curve(getData().getGv(), getData().getBgv());
        Curve power = powerCurve();
        return strictlyIncreasing(blade.x) && nondecreasing(blade.y)
                && strictlyIncreasing(power.x) && nondecreasing(power.y);
    }

    private static Curve curve(double[] xValues, double[] yValues) {
        List<Double> x = new ArrayList<>();
        List<Double> y = new ArrayList<>();
        for (int i = 0; i < xValues.length; i++) {
            if (i == 0 || xValues[i] > 0.0) { x.add(xValues[i]); y.add(yValues[i]); }
        }
        return new Curve(toArray(x), toArray(y));
    }

    private DeadbandResult deadband(double value, DeadbandMode prior) {
        double width = Math.abs(getData().getDb1());
        double error = Math.abs(getData().getEps());
        DeadbandMode mode = prior;
        if (mode == DeadbandMode.NEUTRAL) {
            if (value > width) mode = DeadbandMode.HIGH;
            else if (value < -width) mode = DeadbandMode.LOW;
        } else if (mode == DeadbandMode.HIGH && value <= width - error) mode = DeadbandMode.NEUTRAL;
        else if (mode == DeadbandMode.LOW && value >= -width + error) mode = DeadbandMode.NEUTRAL;
        double output = mode == DeadbandMode.HIGH ? value - Math.max(0.0, width - error)
                : mode == DeadbandMode.LOW ? value + Math.max(0.0, width - error) : 0.0;
        return new DeadbandResult(mode, output);
    }

    private static double correctedOptionalTime(double value, double minimum) {
        if (value <= 0.0 || minimum <= 0.0 || value >= minimum) return value;
        return value < 0.5 * minimum ? 0.0 : minimum;
    }

    private static double correctedSignedTsp(double value, double minimum) {
        if (minimum <= 0.0 || Math.abs(value) >= minimum) return value;
        return Math.copySign(minimum, value == 0.0 ? 1.0 : value);
    }

    private static double backlash(double input, double prior, double width) {
        if (width <= SMALL) return input;
        if (input > prior + width) return input - width;
        if (input < prior - width) return input + width;
        return prior;
    }

    private static double slidingDeadband(double input, double prior, double width) {
        return prior + slidingDeadbandDifference(input - prior, width);
    }

    private static double slidingDeadbandDifference(double difference, double width) {
        if (difference > width) return difference - width;
        if (difference < -width) return difference + width;
        return 0.0;
    }

    private static double interpolateClamped(double value, double[] x, double[] y) {
        if (x.length == 0 || x.length != y.length) return Double.NaN;
        if (x.length == 1 || value <= x[0]) return y[0];
        if (value >= x[x.length - 1]) return y[y.length - 1];
        int upper = 1;
        while (upper < x.length && value > x[upper]) upper++;
        double fraction = (value - x[upper - 1]) / (x[upper] - x[upper - 1]);
        return y[upper - 1] + fraction * (y[upper] - y[upper - 1]);
    }

    private static boolean strictlyIncreasing(double[] values) {
        if (values.length < 1) return false;
        for (int i = 1; i < values.length; i++) if (values[i] <= values[i - 1]) return false;
        return true;
    }
    private static boolean nondecreasing(double[] values) {
        for (int i = 1; i < values.length; i++) if (values[i] < values[i - 1]) return false;
        return true;
    }
    private static double[] toArray(List<Double> values) {
        double[] result = new double[values.size()];
        for (int i = 0; i < result.length; i++) result[i] = values.get(i);
        return result;
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
    private record Curve(double[] x, double[] y) { }
    private record Algebraic(DeadbandMode deadbandMode, double rawSpeed, double measuredSpeed,
            double measuredPe, double propInput, double integralInput, double gateCommand,
            double gate, double bladeCommand, double bladeDemand, double blade, double flowArea,
            double flow, double head, double pmech) { }
    private record Derivatives(double pe, double speed, double integrator, double derivativeLag,
            double valve, double gate, double bladeDemand, double bladeServo, double flow) {
        static Derivatives zero() { return new Derivatives(0, 0, 0, 0, 0, 0, 0, 0, 0); }
    }
    private record State(double pe, double speed, double integrator, double derivativeLag,
            double valve, double gate, double bladeDemand, double bladeServo, double flow) {
        static State zero() { return new State(0, 0, 0, 0, 0, 0, 0, 0, 0); }
        State plus(Derivatives d, double dt) {
            return new State(pe + d.pe * dt, speed + d.speed * dt,
                    integrator + d.integrator * dt, derivativeLag + d.derivativeLag * dt,
                    valve + d.valve * dt, gate + d.gate * dt,
                    bladeDemand + d.bladeDemand * dt, bladeServo + d.bladeServo * dt,
                    flow + d.flow * dt);
        }
        State plusAverage(Derivatives a, Derivatives b, double dt) {
            return plus(new Derivatives((a.pe + b.pe) / 2.0, (a.speed + b.speed) / 2.0,
                    (a.integrator + b.integrator) / 2.0,
                    (a.derivativeLag + b.derivativeLag) / 2.0,
                    (a.valve + b.valve) / 2.0, (a.gate + b.gate) / 2.0,
                    (a.bladeDemand + b.bladeDemand) / 2.0,
                    (a.bladeServo + b.bladeServo) / 2.0,
                    (a.flow + b.flow) / 2.0), dt);
        }
    }

    private record Effective(int fd, double tpe, double tsp, double ki, double td,
            double velm, double gmax, double gmin, double buv, double tg, double blg,
            double dbbd, double tbd, double blb, double dbbs, double tbs, double blv,
            double dturb, double deff, double bgvmin, double hdam, double tw) {
        Effective withGateLimits(double max, double min) {
            return new Effective(fd, tpe, tsp, ki, td, velm, max, min, buv, tg, blg,
                    dbbd, tbd, blb, dbbs, tbs, blv, dturb, deff, bgvmin, hdam, tw);
        }
    }
}
