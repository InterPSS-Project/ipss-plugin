package org.interpss.dstab.control.gov.psse.tgov3;

import org.interpss.dstab.control.util.AsymmetricDeadbandBlock;
import org.interpss.dstab.control.util.IntegrationStepAware;
import org.interpss.numeric.datatype.Unit.UnitType;

import com.interpss.common.exp.InterpssRuntimeException;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.deqn.AbstractGovernor;
import com.interpss.dstab.mach.Machine;

/**
 * PSS/E TGOV3D modified IEEE Type-1 steam governor with fast valving.
 *
 * <p>The state realization follows the published PowerWorld/PSS/E diagram and
 * uses the InterPSS modified-Euler predictor/corrector. Fast valving is an
 * externally initiated model action because its trigger is a simulation option,
 * not a TGOV3D record field.</p>
 */
public class PsseTgov3dGovernor extends AbstractGovernor implements IntegrationStepAware {
    private static final double EPS = 1.0e-9;
    // PSS/E's fixed TGOV3D intercept-valve characteristic is the exponential
    // curve through (0, 0), (0.3, 0.8), and (1, 1).
    private static final double INTERCEPT_VALVE_EXPONENT = 5.29881692679524;
    private static final double INTERCEPT_VALVE_SCALE = 1.00502260329671;

    private State state = State.zero();
    private State oldState = State.zero();
    private Derivatives oldDerivatives = Derivatives.zero();
    private double reference;
    private double auxiliaryInput;
    private double governorToMachineBase = 1.0;
    private double integrationStep;
    private double minimumTimeConstantMultiplier = 1.0;
    private double effectiveT3, effectiveT5;
    private double effectiveUo, effectiveUc, effectivePmax, effectivePmin;
    private double effectivePrmax;
    private double currentOutput;
    private double committedSpeedInput;
    private double fastValvingElapsed = Double.NaN;
    private boolean initialized;

    public PsseTgov3dGovernor(String id, String name, String category) {
        super(id, name, category);
        _data = new PsseTgov3dGovernorData();
    }

    public PsseTgov3dGovernorData getData() {
        return (PsseTgov3dGovernorData) _data;
    }

    @Override public void configureIntegrationStep(double timeStepSec) {
        configureIntegrationStep(timeStepSec, 1.0);
    }

    public void configureIntegrationStep(double timeStepSec, double multiplier) {
        if (!Double.isFinite(timeStepSec) || timeStepSec < 0.0
                || !Double.isFinite(multiplier) || multiplier < 0.0) {
            throw new IllegalArgumentException(
                    "TGOV3D integration settings must be finite and non-negative");
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
        double fractionSum = shaftFractionSum();
        double valve0 = pm0 / fractionSum;
        if (!finite(pm0, valve0)) return false;

        effectivePmax = Math.max(effectivePmax, valve0);
        effectivePmin = Math.min(effectivePmin, valve0);
        effectivePrmax = Math.max(getData().getPrmax(), valve0);
        state = new State(0.0, valve0, valve0, valve0, valve0);
        oldState = state;
        reference = valve0 - auxiliaryInput;
        committedSpeedInput = applySpeedDeadband(mach.getSpeed() - 1.0);
        currentOutput = pm0;
        fastValvingElapsed = Double.NaN;
        initialized = true;
        return true;
    }

    @Override public boolean nextStep(double dt, DynamicSimuMethod method,
            Machine mach, int flag) {
        if (method != DynamicSimuMethod.MODIFIED_EULER) {
            throw new InterpssRuntimeException("TGOV3D supports MODIFIED_EULER only");
        }
        if (!initialized) return false;
        if (flag == 0) {
            oldState = state;
            Algebraic old = algebraic(oldState, dt, 0.0);
            oldDerivatives = derivatives(oldState, old);
            state = normalize(oldState.plus(oldDerivatives, dt), dt);
            currentOutput = output(state, dt);
        } else if (flag == 1) {
            Algebraic predicted = algebraic(state, dt, dt);
            Derivatives corrected = derivatives(state, predicted);
            state = normalize(oldState.plusAverage(oldDerivatives, corrected, dt), dt);
            if (Double.isFinite(fastValvingElapsed)) fastValvingElapsed += dt;
            committedSpeedInput = applySpeedDeadband(getMachine().getSpeed() - 1.0);
            currentOutput = output(state, 0.0);
        } else {
            throw new InterpssRuntimeException("TGOV3D invalid integration flag: " + flag);
        }
        return true;
    }

    @Override public double getOutput(Machine mach) {
        return currentOutput * governorToMachineBase;
    }

    @Override public void setRefPoint(double value) {
        reference = value / governorToMachineBase / shaftFractionSum();
    }

    /** Starts the intercept-valve closing/hold/reopening sequence at this instant. */
    public void initiateFastValving() { fastValvingElapsed = 0.0; }
    public void cancelFastValving() { fastValvingElapsed = Double.NaN; }
    public boolean isFastValvingActive() {
        return Double.isFinite(fastValvingElapsed)
                && fastValvingElapsed < getData().getTc();
    }
    public void setAuxiliaryInput(double value) { auxiliaryInput = value; }
    public double getReference() { return reference; }
    public double getValvePosition() { return state.valve; }
    public double getSteamBowlOutput() { return steamBowl(state); }
    public double getReheaterPressure() { return reheaterPressure(state); }
    public double getCrossoverOutput() { return crossover(state, 0.0); }
    public double getInterceptValvePosition() { return interceptValvePosition(0.0); }
    public double getInterceptValveFlowGain() {
        return interceptValveFlowGain(interceptValvePosition(0.0));
    }
    public double getInterceptFlow() {
        return reheaterPressure(state) * getInterceptValveFlowGain();
    }
    public double getLeadLagOutput() {
        double input = applySpeedDeadband(getMachine().getSpeed() - 1.0);
        return leadLagOutput(state, input, Math.max(integrationStep, EPS));
    }
    public double getEffectiveT3() { return effectiveT3; }
    public double getEffectiveT5() { return effectiveT5; }
    public double getEffectiveUo() { return effectiveUo; }
    public double getEffectiveUc() { return effectiveUc; }
    public double getEffectivePmax() { return effectivePmax; }
    public double getEffectivePmin() { return effectivePmin; }
    public double getEffectivePrmax() { return effectivePrmax; }
    public double getGovernorBaseMva(Machine mach) {
        return governorToMachineBase * mach.getRating(UnitType.mVA,
                mach.getDStabBus().getNetwork().getBaseKva());
    }
    public double applySpeedDeadband(double speedDeviation) {
        return AsymmetricDeadbandBlock.apply(speedDeviation,
                getData().getDbH(), getData().getDbL());
    }

    public boolean validateParameters() {
        PsseTgov3dGovernorData d = getData();
        return finite(d.getK(), d.getT1(), d.getT2(), d.getT3(), d.getUo(),
                        d.getUc(), d.getPmax(), d.getPmin(), d.getT4(), d.getK1(),
                        d.getT5(), d.getK2(), d.getT6(), d.getK3(), d.getTa(),
                        d.getTb(), d.getTc(), d.getPrmax(), d.getDbH(), d.getDbL(),
                        d.getTrate())
                && d.getK() > 0.0 && d.getT1() >= 0.0 && d.getT2() >= 0.0
                && d.getT3() >= 0.0 && d.getT4() >= 0.0 && d.getT5() >= 0.0
                && d.getT6() >= 0.0 && d.getK1() >= 0.0 && d.getK2() >= 0.0
                && d.getK3() >= 0.0 && shaftFractionSum() > EPS
                && d.getTa() >= 0.0 && d.getTb() >= d.getTa()
                && d.getTc() >= d.getTb() && d.getPrmax() > 0.0
                && d.getDbH() >= 0.0 && d.getDbL() <= 0.0
                && d.getDbL() <= d.getDbH() && d.getTrate() >= 0.0;
    }

    private Algebraic algebraic(State s, double dt, double evaluationOffset) {
        double speedInput = applySpeedDeadband(getMachine().getSpeed() - 1.0);
        double leadLag = leadLagOutput(s, speedInput, dt);
        double valveError = reference + auxiliaryInput - leadLag - s.valve;
        double bowl = steamBowl(s);
        double pressure = reheaterPressure(s);
        double intercept = interceptValvePosition(evaluationOffset);
        double flow = pressure * interceptValveFlowGain(intercept);
        double cross = getData().getT6() > EPS ? s.crossover : flow;
        return new Algebraic(speedInput, leadLag, valveError, bowl, pressure,
                intercept, flow, cross);
    }

    private Derivatives derivatives(State s, Algebraic a) {
        PsseTgov3dGovernorData d = getData();
        double leadLagDot = d.getT1() > EPS
                ? (a.speedInput - s.leadLagState) / d.getT1() : 0.0;
        double rawValveRate = effectiveT3 > EPS
                ? a.valveError / effectiveT3
                : a.valveError > EPS ? effectiveUo : a.valveError < -EPS ? effectiveUc : 0.0;
        double valveDot = clamp(rawValveRate, effectiveUc, effectiveUo);
        if ((s.valve >= effectivePmax && valveDot > 0.0)
                || (s.valve <= effectivePmin && valveDot < 0.0)) valveDot = 0.0;
        double bowlDot = d.getT4() > EPS ? (s.valve - s.bowl) / d.getT4() : 0.0;
        // The diagram feeds the nonlinear intercept-valve flow, before T6,
        // back to the T5 reheater-pressure integrator.
        double pressureDot = effectiveT5 > EPS
                ? (a.bowl - a.flow) / effectiveT5 : 0.0;
        if (s.reheaterPressure >= effectivePrmax && pressureDot > 0.0) {
            pressureDot = 0.0;
        }
        double crossoverDot = d.getT6() > EPS
                ? (a.flow - s.crossover) / d.getT6() : 0.0;
        return new Derivatives(leadLagDot, valveDot, bowlDot, pressureDot,
                crossoverDot);
    }

    private State normalize(State s, double dt) {
        double speedInput = applySpeedDeadband(getMachine().getSpeed() - 1.0);
        double leadLag = getData().getT1() > EPS ? s.leadLagState : speedInput;
        double valve = clamp(s.valve, effectivePmin, effectivePmax);
        double bowl = getData().getT4() > EPS ? s.bowl : valve;
        double pressure = effectiveT5 > EPS ? clamp(s.reheaterPressure, 0.0,
                effectivePrmax) : bowl;
        double intercept = interceptValvePosition(dt);
        double flow = pressure * interceptValveFlowGain(intercept);
        double cross = getData().getT6() > EPS ? s.crossover : flow;
        return new State(leadLag, valve, bowl, pressure, cross);
    }

    private double output(State s, double evaluationOffset) {
        Algebraic a = algebraic(s, Math.max(integrationStep, EPS), evaluationOffset);
        PsseTgov3dGovernorData d = getData();
        return d.getK1() * a.bowl + d.getK2() * a.flow + d.getK3() * a.crossover;
    }

    private double leadLagOutput(State s, double input, double dt) {
        PsseTgov3dGovernorData d = getData();
        if (d.getT1() > EPS) {
            double ratio = d.getT2() / d.getT1();
            return d.getK() * (ratio * input + (1.0 - ratio) * s.leadLagState);
        }
        double derivative = dt > EPS ? (input - committedSpeedInput) / dt : 0.0;
        return d.getK() * (input + d.getT2() * derivative);
    }

    private double steamBowl(State s) {
        return getData().getT4() > EPS ? s.bowl : s.valve;
    }

    private double reheaterPressure(State s) {
        return effectiveT5 > EPS ? clamp(s.reheaterPressure, 0.0, effectivePrmax)
                : steamBowl(s);
    }

    private double crossover(State s, double offset) {
        if (getData().getT6() > EPS) return s.crossover;
        return reheaterPressure(s) * interceptValveFlowGain(
                interceptValvePosition(offset));
    }

    private double interceptValvePosition(double evaluationOffset) {
        if (!Double.isFinite(fastValvingElapsed)) return 1.0;
        PsseTgov3dGovernorData d = getData();
        double time = fastValvingElapsed + evaluationOffset;
        if (time <= 0.0) return 1.0;
        if (time < d.getTa()) return d.getTa() > EPS ? 1.0 - time / d.getTa() : 0.0;
        if (time < d.getTb()) return 0.0;
        if (time < d.getTc()) return d.getTc() > d.getTb() + EPS
                ? (time - d.getTb()) / (d.getTc() - d.getTb()) : 1.0;
        return 1.0;
    }

    /** Fixed TGOV3D exponential flow curve shown in the PSS/E/PowerWorld diagram. */
    private static double interceptValveFlowGain(double valvePosition) {
        double position = clamp(valvePosition, 0.0, 1.0);
        return INTERCEPT_VALVE_SCALE
                * (1.0 - Math.exp(-INTERCEPT_VALVE_EXPONENT * position));
    }

    private void prepareEffectiveParameters() {
        PsseTgov3dGovernorData d = getData();
        double minimum = minimumTimeConstantMultiplier * integrationStep;
        effectiveT3 = correctedQuarterTime(d.getT3(), minimum);
        effectiveT5 = correctedQuarterTime(d.getT5(), minimum);
        effectiveUo = Math.abs(Math.max(d.getUo(), d.getUc()));
        effectiveUc = -Math.abs(Math.min(d.getUo(), d.getUc()));
        effectivePmax = Math.max(d.getPmax(), d.getPmin());
        effectivePmin = Math.min(d.getPmax(), d.getPmin());
    }

    private double shaftFractionSum() {
        return getData().getK1() + getData().getK2() + getData().getK3();
    }

    private static double correctedQuarterTime(double value, double minimum) {
        if (value <= 0.0 || minimum <= 0.0) return value;
        if (value < 0.25 * minimum) return 0.0;
        return value < 0.5 * minimum ? 0.5 * minimum : value;
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Algebraic(double speedInput, double leadLag, double valveError,
            double bowl, double pressure, double interceptValve, double flow,
            double crossover) { }
    private record Derivatives(double leadLagState, double valve, double bowl,
            double reheaterPressure, double crossover) {
        static Derivatives zero() { return new Derivatives(0, 0, 0, 0, 0); }
    }
    private record State(double leadLagState, double valve, double bowl,
            double reheaterPressure, double crossover) {
        static State zero() { return new State(0, 0, 0, 0, 0); }
        State plus(Derivatives d, double dt) {
            return new State(leadLagState + d.leadLagState * dt,
                    valve + d.valve * dt, bowl + d.bowl * dt,
                    reheaterPressure + d.reheaterPressure * dt,
                    crossover + d.crossover * dt);
        }
        State plusAverage(Derivatives a, Derivatives b, double dt) {
            return plus(new Derivatives((a.leadLagState + b.leadLagState) / 2.0,
                    (a.valve + b.valve) / 2.0, (a.bowl + b.bowl) / 2.0,
                    (a.reheaterPressure + b.reheaterPressure) / 2.0,
                    (a.crossover + b.crossover) / 2.0), dt);
        }
    }
}
