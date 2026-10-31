package org.interpss.dstab.control.gov.psse.hyg3;

import com.interpss.common.exp.InterpssRuntimeException;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.deqn.AbstractGovernor;
import com.interpss.dstab.mach.Machine;
import org.interpss.dstab.control.util.IntegrationStepAware;
import org.interpss.numeric.datatype.Unit.UnitType;

/**
 * WECC HYG3 PID/double-derivative hydro turbine-governor.
 *
 * <p>The differential and algebraic paths follow the published PowerWorld
 * HYG3 diagram. The PSS/E DYR convention is {@code 1=PID} and
 * {@code 0=double derivative}; PowerWorld maps those values to the two
 * branches shown as {@code cflag>0} and {@code cflag<0} in the diagram.</p>
 */
public class PsseHyg3Governor extends AbstractGovernor implements IntegrationStepAware, com.interpss.dstab.controller.cml.ICMLStateProvider {
    private static final double SMALL = 1.0e-9;
    private static final double[] BUILTIN_GV = {0.0, 0.07, 0.25, 0.67, 0.75, 0.83, 1.0};
    private static final double[] BUILTIN_PGV = {0.0, 0.0, 0.20, 0.80, 0.90, 0.96, 1.0};

    private State state = State.zero();
    private State oldState = State.zero();
    private Derivatives oldDerivatives = Derivatives.zero();
    private double integrationStep;
    private double minimumTimeConstantMultiplier = 1.0;
    private double effectiveTw;
    private double effectiveAt;
    private double effectivePmax;
    private double effectivePmin;
    private double governorToMachineBase = 1.0;
    private double governorBaseMva;
    private double pref;
    private double auxiliaryInput;
    private double committedGateOutput;
    private double currentGateOutput;
    private double currentCv;
    private double currentOutput;
    private DeadbandMode committedDeadbandMode = DeadbandMode.NEUTRAL;
    private boolean initialized;
    private transient PsseHyg3GovernorData preparedCurveData;
    private transient long preparedCurveRevision = Long.MIN_VALUE;
    private transient double[] preparedGatePoints, preparedPowerPoints;
    private transient long gateCurvePreparationCount;

    public PsseHyg3Governor(String id, String name, String category) {
        super(id, name, category);
        _data = new PsseHyg3GovernorData();
    }

    public PsseHyg3GovernorData getData() {
        return (PsseHyg3GovernorData) _data;
    }

    @Override
    public void configureIntegrationStep(double timeStepSec) {
        configureIntegrationStep(timeStepSec, 1.0);
    }

    public void configureIntegrationStep(double timeStepSec, double multiplier) {
        if (!Double.isFinite(timeStepSec) || timeStepSec < 0.0
                || !Double.isFinite(multiplier) || multiplier < 0.0) {
            throw new IllegalArgumentException("HYG3 integration settings must be finite and non-negative");
        }
        integrationStep = timeStepSec;
        minimumTimeConstantMultiplier = multiplier;
    }

    @Override
    public boolean initStates(BaseDStabBus<?, ?> bus, Machine mach) {
        if (!validateParameters()) return false;
        PsseHyg3GovernorData d = getData();
        double minimum = Math.max(SMALL, integrationStep * minimumTimeConstantMultiplier);
        effectiveAt = d.getAt() <= 0.0 ? minimum : d.getAt();
        effectiveTw = d.getTw() > 0.0 && d.getTw() < minimum ? minimum : d.getTw();

        double machineMva = mach.getRating(UnitType.mVA, bus.getNetwork().getBaseKva());
        governorBaseMva = d.getTrate() > SMALL ? d.getTrate() : machineMva;
        governorToMachineBase = machineMva > SMALL ? governorBaseMva / machineMva : 1.0;
        double pm0 = mach.getPm() / governorToMachineBase;
        double pe0 = mach.getPe() / governorToMachineBase;
        double q0 = pm0 / (effectiveAt * d.getH0()) + d.getQnl();
        if (!Double.isFinite(q0) || q0 <= 0.0) return false;
        double pgv0 = q0 / Math.sqrt(d.getH0());
        double gate0 = inverseGateCurve(pgv0);
        if (!Double.isFinite(gate0)) return false;

        double rawMax = Math.max(d.getPmax(), d.getPmin());
        double rawMin = Math.min(d.getPmax(), d.getPmin());
        effectivePmax = Math.max(rawMax, gate0);
        effectivePmin = Math.min(rawMin, gate0);
        pref = d.getRgate() * gate0 + d.getRelec() * pe0;
        state = new State(0.0, 0.0, gate0, gate0, q0, 0.0, pe0, 0.0, 0.0);
        oldState = state;
        committedGateOutput = gate0;
        currentGateOutput = gate0;
        currentCv = gate0;
        currentOutput = pm0;
        committedDeadbandMode = DeadbandMode.NEUTRAL;
        initialized = true;
        return true;
    }

    @Override
    public boolean nextStep(double dt, DynamicSimuMethod method, Machine mach, int flag) {
        if (method != DynamicSimuMethod.MODIFIED_EULER) {
            throw new InterpssRuntimeException("HYG3 supports MODIFIED_EULER only");
        }
        if (!initialized) return false;
        if (flag == 0) {
            oldState = state;
            Algebraic old = algebraic(oldState, committedGateOutput, committedDeadbandMode);
            oldDerivatives = derivatives(oldState, old);
            state = constrain(oldState.plus(oldDerivatives, dt));
            Algebraic predicted = algebraic(state, committedGateOutput, committedDeadbandMode);
            currentCv = predicted.cv;
            currentGateOutput = predicted.gateOutput;
            currentOutput = predicted.pmech;
        } else if (flag == 1) {
            Algebraic predicted = algebraic(state, committedGateOutput, committedDeadbandMode);
            Derivatives corrected = derivatives(state, predicted);
            state = constrain(oldState.plusAverage(oldDerivatives, corrected, dt));
            Algebraic result = algebraic(state, committedGateOutput, committedDeadbandMode);
            currentCv = result.cv;
            currentGateOutput = result.gateOutput;
            currentOutput = result.pmech;
            committedGateOutput = currentGateOutput;
            committedDeadbandMode = result.deadbandMode;
        } else {
            throw new InterpssRuntimeException("HYG3 invalid integration flag: " + flag);
        }
        return true;
    }

    @Override
    public double getOutput(Machine mach) {
        return currentOutput * governorToMachineBase;
    }

    @Override
    public void setRefPoint(double value) {
        pref = value;
    }

    public void setAuxiliaryInput(double value) { auxiliaryInput = value; }
    public double getAuxiliaryInput() { return auxiliaryInput; }
    public double getInputFilterState() { return state.tdLag; }
    public double getK1FilterState() { return state.k1Lag; }
    public double getK1Output() {
        double tdSignal = algebraic(state, currentGateOutput, committedDeadbandMode).tdSignal;
        return washoutOutput(tdSignal, state.k1Lag, getData().getK1(), getData().getTf());
    }
    public double getIntegralState() { return state.integrator; }
    public double getValveState() { return state.valve; }
    public double getControlValveCommand() { return currentCv; }
    public double getGatePosition() { return state.gate; }
    public double getGateOutput() { return currentGateOutput; }
    public double getWaterFlow() { return state.flow; }
    public double getMeasuredElectricalPower() { return measuredPower(state); }
    public double getK2FirstState() { return state.k2First; }
    public double getK2SecondState() { return state.k2Second; }
    public double getK2FirstOutput() {
        double tdSignal = algebraic(state, currentGateOutput, committedDeadbandMode).tdSignal;
        return getData().getTf() > SMALL
                ? (tdSignal - state.k2First) / getData().getTf() : 0.0;
    }
    /** Canonical first state reported by PowerWorld for the double-derivative block. */
    public double getK2FirstCanonicalState() {
        double tf = getData().getTf();
        return tf > SMALL ? -getData().getK2() * state.k2Second / (tf * tf) : 0.0;
    }
    public double getK2SecondOutput() {
        double tdSignal = algebraic(state, currentGateOutput, committedDeadbandMode).tdSignal;
        return getData().getControlFlag() == PsseHyg3GovernorData.DOUBLE_DERIVATIVE_CONTROL
                ? doubleDerivativeOutput(state, tdSignal) : 0.0;
    }
    @Override
    public java.util.Map<String,Double> getNamedStates() {
        var values=new java.util.LinkedHashMap<String,Double>();
        values.put("Input filter",state.tdLag);values.put("Integral",state.integrator);
        values.put("Derivative lag",state.k1Lag);values.put("Valve",state.valve);
        values.put("Gate",state.gate);values.put("Electrical power",state.peMeasured);
        values.put("Water flow",state.flow);values.put("Double derivative 1",state.k2First);
        values.put("Double derivative 2",state.k2Second);
        return java.util.Map.copyOf(values);
    }

    public double getEffectivePmax() { return effectivePmax; }
    public double getEffectivePmin() { return effectivePmin; }
    public double getEffectiveTw() { return effectiveTw; }
    public double getEffectiveAt() { return effectiveAt; }
    public double getGovernorBaseMva() { return governorBaseMva; }

    /** Returns the speed deadband output in per-unit speed for a value in per unit. */
    public double applyFrequencyDeadband(double speedDeviation) {
        return deadband(speedDeviation, committedDeadbandMode).output;
    }

    public boolean validateParameters() {
        PsseHyg3GovernorData d = getData();
        if (d.getControlFlag() != PsseHyg3GovernorData.PID_CONTROL
                && d.getControlFlag() != PsseHyg3GovernorData.DOUBLE_DERIVATIVE_CONTROL) return false;
        if (!finite(d.getRgate(), d.getRelec(), d.getTt(), d.getTd(), d.getK2(), d.getKi(),
                d.getK1(), d.getTf(), d.getKg(), d.getTp(), d.getVelopen(), d.getVelclose(),
                d.getPmax(), d.getPmin(), d.getDb2(), d.getH0(), d.getQnl(), d.getTw(),
                d.getAt(), d.getDturb(), d.getTrate(), d.getDbH(), d.getEps(), d.getDbL())) return false;
        if (d.getTt() < 0.0 || d.getTd() < 0.0 || d.getTf() < 0.0 || d.getTp() < 0.0
                || d.getKg() <= 0.0 || d.getVelopen() <= 0.0 || d.getVelclose() >= 0.0
                || d.getH0() <= 0.0 || d.getQnl() < 0.0 || d.getTw() < 0.0
                || d.getTrate() < 0.0 || d.getDb2() < 0.0 || d.getDbH() < 0.0
                || d.getDbL() > 0.0 || d.getEps() < 0.0) return false;
        double[] gv = d.getGv();
        double[] pgv = d.getPgv();
        if (!finite(gv) || !finite(pgv)) return false;
        if (gv[0] < 0.0) return true; // PSLF's documented built-in curve selector.
        try { withImplicitEndpoints(gv,pgv); return true; }
        catch(IllegalArgumentException invalid) { return false; }
    }

    private Algebraic algebraic(State s, double priorGateOutput, DeadbandMode priorMode) {
        PsseHyg3GovernorData d = getData();
        DeadbandResult frequency = deadband(getMachine().getSpeed() - 1.0, priorMode);
        double pe = measuredPower(s);
        double tdSignal = s.tdLag;
        double k1Output = washoutOutput(tdSignal, s.k1Lag, d.getK1(), d.getTf());
        double k2DoubleOutput = doubleDerivativeOutput(s, tdSignal);
        double cv;
        double tdInput;
        if (d.getControlFlag() == PsseHyg3GovernorData.PID_CONTROL) {
            if (d.getTd() <= SMALL) {
                double derivativeGain = d.getTf() > SMALL ? d.getK1() / d.getTf() : 0.0;
                double dynamicGain = d.getK2() + derivativeGain;
                double fixedOutput = s.integrator - derivativeGain * s.k1Lag;
                double inputWithoutGateFeedback = pref + auxiliaryInput
                        - frequency.output - d.getRelec() * pe;
                tdSignal = (inputWithoutGateFeedback - d.getRgate() * fixedOutput)
                        / (1.0 + d.getRgate() * dynamicGain);
                k1Output = washoutOutput(tdSignal, s.k1Lag, d.getK1(), d.getTf());
            }
            cv = s.integrator + d.getK2() * tdSignal + k1Output;
            tdInput = pref + auxiliaryInput - frequency.output
                    - d.getRgate() * cv - d.getRelec() * pe;
        } else {
            if (d.getTd() <= SMALL) tdSignal = frequency.output;
            k1Output = washoutOutput(tdSignal, s.k1Lag, d.getK1(), d.getTf());
            k2DoubleOutput = doubleDerivativeOutput(s, tdSignal);
            cv = s.integrator;
            tdInput = frequency.output;
        }
        cv = clamp(cv, effectivePmin, effectivePmax);
        double gateOutput = backlash(s.gate, priorGateOutput, deadband2Pu());
        double pgv = gateCurve(gateOutput);
        double flow = effectiveTw > SMALL ? s.flow : pgv * Math.sqrt(d.getH0());
        double head = square(flow / Math.max(SMALL, pgv));
        double pmech = effectiveAt * head * (flow - d.getQnl())
                - d.getDturb() * (getMachine().getSpeed() - 1.0) * gateOutput;
        double integralError = d.getControlFlag() == PsseHyg3GovernorData.PID_CONTROL
                ? tdSignal
                : pref - auxiliaryInput - k1Output - k2DoubleOutput
                        - d.getRgate() * cv - d.getRelec() * pe;
        double servoTarget = d.getKg() * (cv - gateOutput);
        double valve = d.getTp() > SMALL ? s.valve : servoTarget;
        valve = clamp(valve, d.getVelclose(), d.getVelopen());
        return new Algebraic(frequency.mode, tdInput, tdSignal, integralError, cv,
                gateOutput, valve, pmech);
    }

    private Derivatives derivatives(State s, Algebraic a) {
        PsseHyg3GovernorData d = getData();
        double tdDot = d.getTd() > SMALL ? (a.tdInput - s.tdLag) / d.getTd() : 0.0;
        double k1Dot = d.getTf() > SMALL ? (a.tdSignal - s.k1Lag) / d.getTf() : 0.0;
        double integralDot = d.getKi() * a.integralError;
        if ((s.integrator >= effectivePmax && integralDot > 0.0)
                || (s.integrator <= effectivePmin && integralDot < 0.0)) integralDot = 0.0;
        double valveDot = d.getTp() > SMALL
                ? (d.getKg() * (a.cv - a.gateOutput) - s.valve) / d.getTp() : 0.0;
        if ((s.valve >= d.getVelopen() && valveDot > 0.0)
                || (s.valve <= d.getVelclose() && valveDot < 0.0)) valveDot = 0.0;
        double gateDot = a.valve;
        if ((s.gate >= effectivePmax && gateDot > 0.0)
                || (s.gate <= effectivePmin && gateDot < 0.0)) gateDot = 0.0;
        double pgv = gateCurve(a.gateOutput);
        double head = square(s.flow / Math.max(SMALL, pgv));
        double flowDot = effectiveTw > SMALL ? (d.getH0() - head) / effectiveTw : 0.0;
        double peDot = d.getTt() > SMALL
                ? (getMachine().getPe() / governorToMachineBase - s.peMeasured) / d.getTt() : 0.0;
        double k2FirstDot = 0.0;
        double k2SecondDot = 0.0;
        if (d.getControlFlag() == PsseHyg3GovernorData.DOUBLE_DERIVATIVE_CONTROL
                && d.getTf() > SMALL) {
            double firstWashout = (a.tdSignal - s.k2First) / d.getTf();
            k2FirstDot = firstWashout;
            k2SecondDot = (firstWashout - s.k2Second) / d.getTf();
        }
        return new Derivatives(tdDot, k1Dot, integralDot, valveDot, gateDot,
                flowDot, peDot, k2FirstDot, k2SecondDot);
    }

    private State constrain(State s) {
        return new State(s.tdLag, s.k1Lag,
                clamp(s.integrator, effectivePmin, effectivePmax),
                clamp(s.gate, effectivePmin, effectivePmax), s.flow,
                clamp(s.valve, getData().getVelclose(), getData().getVelopen()),
                s.peMeasured, s.k2First, s.k2Second);
    }

    private double measuredPower(State s) {
        return getData().getTt() > SMALL ? s.peMeasured
                : getMachine().getPe() / governorToMachineBase;
    }

    private double doubleDerivativeOutput(State s, double tdSignal) {
        PsseHyg3GovernorData d = getData();
        if (d.getTf() <= SMALL) return 0.0;
        double firstWashout = (tdSignal - s.k2First) / d.getTf();
        return d.getK2() * (firstWashout - s.k2Second) / d.getTf();
    }

    private static double washoutOutput(double input, double lagState, double gain, double time) {
        return time > SMALL ? gain * (input - lagState) / time : 0.0;
    }

    private DeadbandResult deadband(double value, DeadbandMode priorMode) {
        if(getData().getFrequencyDeadbandConvention()==PsseHyg3GovernorData.FrequencyDeadbandConvention.PER_UNIT_SCALED_ERROR) {
            double high=getData().getDbH(),low=getData().getDbL(),error=getData().getEps();
            if(value>high) return new DeadbandResult(DeadbandMode.HIGH,
                    high>0&&error>0 ? value*error/high : value-high);
            if(value<low) return new DeadbandResult(DeadbandMode.LOW,
                    high>0&&error>0 ? value*error/high : value-low);
            return new DeadbandResult(DeadbandMode.NEUTRAL,0);
        }
        double frequency = Math.max(SMALL, getMachine().getDStabBus().getNetwork().getFrequency());
        double upper = getData().getDbH() / frequency;
        double lower = getData().getDbL() / frequency;
        double hysteresis = getData().getEps() / frequency;
        DeadbandMode mode = priorMode;
        if (mode == DeadbandMode.NEUTRAL) {
            if (value > upper) mode = DeadbandMode.HIGH;
            else if (value < lower) mode = DeadbandMode.LOW;
        } else if (mode == DeadbandMode.HIGH && value <= upper - hysteresis) {
            mode = value < lower ? DeadbandMode.LOW : DeadbandMode.NEUTRAL;
        } else if (mode == DeadbandMode.LOW && value >= lower + hysteresis) {
            mode = value > upper ? DeadbandMode.HIGH : DeadbandMode.NEUTRAL;
        }
        double output = switch (mode) {
            case HIGH -> value - Math.max(0.0, upper - hysteresis);
            case LOW -> value - Math.min(0.0, lower + hysteresis);
            default -> 0.0;
        };
        return new DeadbandResult(mode, output);
    }

    private double deadband2Pu() {
        return governorBaseMva > SMALL ? getData().getDb2() / governorBaseMva : 0.0;
    }

    private static double backlash(double input, double priorOutput, double width) {
        if (width <= SMALL) return input;
        if (input - priorOutput > width) return input - width;
        if (input - priorOutput < -width) return input + width;
        return priorOutput;
    }

    public double gateCurve(double gate) {
        boolean prepared = prepareGateCurve();
        double[] x = prepared ? preparedGatePoints : curveGv();
        double[] y = prepared ? preparedPowerPoints : curvePgv();
        double scale=curveScale();
        double value=interpolate(gate/scale,x,y)*scale;
        return sourceConvention()?clamp(value,0,scale):value;
    }

    public double inverseGateCurve(double power) {
        boolean prepared = prepareGateCurve();
        double[] x = prepared ? preparedPowerPoints : curvePgv();
        double[] y = prepared ? preparedGatePoints : curveGv();
        double scale=curveScale();
        return interpolate(power/scale,x,y)*scale;
    }

    /** Actual preparation count; repeated numerical evaluations reuse the same immutable point arrays. */
    public long getGateCurvePreparationCount() { return gateCurvePreparationCount; }

    private boolean prepareGateCurve() {
        // Custom owners or point providers can have observable getters without revision tracking.
        if (getClass() != PsseHyg3Governor.class || getData().getClass() != PsseHyg3GovernorData.class) return false;
        var data = getData(); long revision = data.getGateCurveRevision();
        if (preparedCurveData == data && preparedCurveRevision == revision) return true;
        double[] gate, power;
        if (data.getGv(0) < 0.) { gate = BUILTIN_GV; power = BUILTIN_PGV; }
        else {
            var points = withImplicitEndpoints(data.getGv(), data.getPgv());
            gate = points[0]; power = points[1];
        }
        preparedGatePoints = gate; preparedPowerPoints = power;
        preparedCurveData = data; preparedCurveRevision = revision;
        gateCurvePreparationCount++;
        return true;
    }

    private boolean sourceConvention() { return getData().getFrequencyDeadbandConvention()==PsseHyg3GovernorData.FrequencyDeadbandConvention.PER_UNIT_SCALED_ERROR; }
    private double curveScale() { return sourceConvention()?Math.max(1,getData().getPmax()):1; }

    private double[] curveGv() {
        if (getData().getGv(0) < 0.0) return BUILTIN_GV;
        return withImplicitEndpoints(getData().getGv(), getData().getPgv())[0];
    }

    private double[] curvePgv() {
        if (getData().getGv(0) < 0.0) return BUILTIN_PGV;
        return withImplicitEndpoints(getData().getGv(), getData().getPgv())[1];
    }

    private static double[][] withImplicitEndpoints(double[] x, double[] y) {
        double[] xx=new double[x.length+2],yy=new double[y.length+2];int count=1;
        for(int i=0;i<x.length;i++) {
            if(x[i]==0 && y[i]==0)continue;
            if(x[i]==xx[count-1] && y[i]==yy[count-1])continue;
            if(x[i]<=xx[count-1] || y[i]<yy[count-1])
                throw new IllegalArgumentException("HYG3 gate points must be ordered");
            xx[count]=x[i];yy[count++]=y[i];
        }
        if(xx[count-1]<1) {xx[count]=1;yy[count++]=1;}
        return new double[][]{java.util.Arrays.copyOf(xx,count),java.util.Arrays.copyOf(yy,count)};
    }

    private static double interpolate(double value, double[] x, double[] y) {
        if (x.length != y.length || x.length < 2) return Double.NaN;
        int upper = 1;
        while (upper < x.length - 1 && value > x[upper]) upper++;
        if (value <= x[0]) upper = 1;
        double dx = x[upper] - x[upper - 1];
        if (Math.abs(dx) <= SMALL) return y[upper];
        double fraction = (value - x[upper - 1]) / dx;
        return y[upper - 1] + fraction * (y[upper] - y[upper - 1]);
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
    private record Algebraic(DeadbandMode deadbandMode, double tdInput,
            double tdSignal, double integralError, double cv, double gateOutput, double valve,
            double pmech) { }
    private record Derivatives(double tdLag, double k1Lag, double integrator,
            double valve, double gate, double flow, double peMeasured,
            double k2First, double k2Second) {
        static Derivatives zero() { return new Derivatives(0, 0, 0, 0, 0, 0, 0, 0, 0); }
    }
    private record State(double tdLag, double k1Lag, double integrator,
            double gate, double flow, double valve, double peMeasured,
            double k2First, double k2Second) {
        static State zero() { return new State(0, 0, 0, 0, 0, 0, 0, 0, 0); }
        State plus(Derivatives d, double dt) {
            return new State(tdLag + d.tdLag * dt, k1Lag + d.k1Lag * dt,
                    integrator + d.integrator * dt, gate + d.gate * dt,
                    flow + d.flow * dt, valve + d.valve * dt,
                    peMeasured + d.peMeasured * dt, k2First + d.k2First * dt,
                    k2Second + d.k2Second * dt);
        }
        State plusAverage(Derivatives a, Derivatives b, double dt) {
            return plus(new Derivatives((a.tdLag + b.tdLag) / 2.0,
                    (a.k1Lag + b.k1Lag) / 2.0,
                    (a.integrator + b.integrator) / 2.0,
                    (a.valve + b.valve) / 2.0, (a.gate + b.gate) / 2.0,
                    (a.flow + b.flow) / 2.0,
                    (a.peMeasured + b.peMeasured) / 2.0,
                    (a.k2First + b.k2First) / 2.0,
                    (a.k2Second + b.k2Second) / 2.0), dt);
        }
    }
}
