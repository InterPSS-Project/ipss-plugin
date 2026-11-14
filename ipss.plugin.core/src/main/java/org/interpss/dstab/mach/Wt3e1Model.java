package org.interpss.dstab.mach;

import java.util.LinkedHashMap;
import java.util.Map;

import com.interpss.dstab.controller.cml.ICMLStateProvider;
import com.interpss.dstab.controller.runtime.StaggeredEulerParticipant;
import com.interpss.dstab.controller.runtime.IntegrationStateAccess;
import com.interpss.dstab.controller.runtime.IntegrationStateMonitor;

/** Published WT3E1 Type-3 electrical controller. */
public final class Wt3e1Model implements ICMLStateProvider, StaggeredEulerParticipant, IntegrationStateAccess {
    private static final double EPS = 1.0e-10;
    private static final String[] STATE_NAMES = {"Voltage regulator filter", "Voltage regulator integrator",
            "Torque regulator filter", "Torque regulator integrator", "Voltage sensor", "Power filter",
            "MVAR/Vref integrator", "Voltage-error/internal-voltage integrator", "WindVar lag",
            "Fast-PF electrical-power filter"};
    private final double[] stateBuffer = new double[10], rateBuffer = new double[10];
    private final IntegrationStateMonitor monitor = new IntegrationStateMonitor(STATE_NAMES);
    private double pendingStep;
    private final double[] speedPower, speedValues;
    private final Wt3e1Data data;
    private final Wt3GeneratorModel generator;
    private State state;
    private State oldState;
    private Derivative predictor;
    private double voltageReference;
    private double qReference;
    private double powerFactorAngle;
    private double speedDeviation;
    private double controlBaseMva;
    private double systemBaseMva;
    private boolean initialized;

    public Wt3e1Model(Wt3e1Data data, Wt3GeneratorModel generator) {
        this.data = data;
        this.generator = generator;
        speedPower = new double[] {0.0, .2, .4, .6, data.minimumPowerAtFullSpeed()};
        speedValues = new double[] {data.omegaPmin(), data.omegaP20(), data.omegaP40(),
                data.omegaP60(), data.omegaP100()};
    }

    public void initialize() {
        double voltage = terminalVoltage();
        systemBaseMva = generator.getDStabBus().getNetwork().getBaseMva();
        controlBaseMva = generator.getAggregateRatedMw() * data.onlineFraction();
        double pControl = generator.getP() * systemBaseMva / controlBaseMva;
        double qControl = generator.getQ() * systemBaseMva / controlBaseMva;
        double compensated = compensatedVoltage(voltage, pControl, qControl);
        voltageReference = compensated;
        qReference = qControl;
        powerFactorAngle = Math.atan2(qControl, pControl);
        speedDeviation = optimalSpeed(pControl);
        double pOrder = clamp(pControl, data.pmin(), data.pmax());
        double torqueIntegral = pOrder / Math.max(EPS, 1.0 + speedDeviation);
        double qOrder = qControl;
        // When terminal-voltage control is bypassed, this Vref integrator
        // directly supplies Eqcmd and must start at the solved converter Eq.
        double initialVref = data.voltageLimitFlag() == 0
                ? generator.getInternalVoltageState() : voltage;
        state = new State(qOrder, qOrder, pOrder, torqueIntegral, compensated,
                speedDeviation, initialVref, generator.getInternalVoltageState(),
                0.0, generator.getP());
        oldState = null; predictor = null; monitor.reset();
        initialized = true;
        updateCommands();
    }

    public void step(double dt, int flag) {
        ready(); interval(dt);
        if (flag != 0 && flag != 1) throw new IllegalArgumentException("Predictor or corrector required");
        if (dt == 0) { exclusive(); return; }
        if (flag == 0 && oldState != null || flag == 1 && (oldState == null || dt != pendingStep))
            throw new IllegalStateException("Matching WT3E1 predictor/corrector required");
        Derivative rate = derivatives(state);
        State candidate = flag == 0 ? advance(state, rate, dt)
                : advance(oldState, predictor.add(rate), .5 * dt);
        validate(candidate);
        publish(candidate);
        record(rate, flag == 0 ? IntegrationStateMonitor.Stage.PREDICTOR : IntegrationStateMonitor.Stage.CORRECTOR);
        if (flag == 0) { oldState = state; predictor = rate; pendingStep = dt; }
        else { oldState = null; predictor = null; }
        state = candidate;
    }

    /** Restore an abandoned ordinary predictor without retaining a trial history. */
    public void cancelPrediction() {
        ready();
        if (oldState != null) { publish(oldState); state = oldState; oldState = null; predictor = null; monitor.reset(); }
    }

    @Override public int staggeredStateCount() { return 10; }
    @Override public void readStaggeredState(double[] values, int offset) {
        ready(); buffer(values, offset); copy(state, values, offset);
    }
    @Override public void evaluateStaggered(double stepSec, double[] derivatives, int offset) {
        ready(); interval(stepSec); exclusive(); buffer(derivatives, offset);
        Derivative rate = derivatives(state);
        copy(rate, rateBuffer, 0);
        for (double value : rateBuffer) finite(value);
        updateCommands(); // Algebraic publication only; no state or accepted history advances.
        System.arraycopy(rateBuffer, 0, derivatives, offset, 10);
        record(rate, IntegrationStateMonitor.Stage.STAGGERED);
    }
    @Override public void applyStaggeredState(double[] values, int offset, double stepSec) {
        ready(); interval(stepSec); exclusive(); buffer(values, offset);
        for (int i = 0; i < 10; i++) finite(values[offset+i]);
        State candidate = new State(values[offset], values[offset+1], values[offset+2], values[offset+3],
                values[offset+4], values[offset+5], values[offset+6], values[offset+7], values[offset+8], values[offset+9]);
        publish(candidate); state = candidate;
    }
    @Override public Map<String, Double> getIntegrationStates() { return getNamedStates(); }
    @Override public IntegrationStateMonitor.Snapshot getStateDerivativeSnapshot() { return monitor.snapshot(); }
    private void record(Derivative rate, IntegrationStateMonitor.Stage stage) {
        copy(state, stateBuffer, 0); copy(rate, rateBuffer, 0); monitor.begin(stage);
        for (int i = 0; i < 10; i++) monitor.record(i, stateBuffer[i], rateBuffer[i]);
    }
    private void validate(State candidate) { copy(candidate, stateBuffer, 0); for (double value : stateBuffer) finite(value); }
    private void ready() { if (!initialized) throw new IllegalStateException("Initialize WT3E1 first"); }
    private void exclusive() { if (oldState != null) throw new IllegalStateException("Finish or cancel ordinary WT3E1 prediction first"); }
    private static void finite(double value) { if (!Double.isFinite(value)) throw new IllegalArgumentException("Finite WT3E1 value required"); }
    private static void interval(double value) { finite(value); if (value < 0) throw new IllegalArgumentException("Nonnegative interval required"); }
    private static void buffer(double[] values, int offset) { java.util.Objects.checkFromIndexSize(offset, 10, values.length); }
    private static void copy(State s, double[] v, int o) {
        v[o]=s.vregFilter; v[o+1]=s.vregIntegral; v[o+2]=s.torqueFilter; v[o+3]=s.torqueIntegral;
        v[o+4]=s.voltageSensor; v[o+5]=s.powerFilter; v[o+6]=s.qVrefIntegral;
        v[o+7]=s.verrorEqIntegral; v[o+8]=s.windVarLag; v[o+9]=s.pelecFilter;
    }
    private static void copy(Derivative s, double[] v, int o) {
        v[o]=s.vregFilter; v[o+1]=s.vregIntegral; v[o+2]=s.torqueFilter; v[o+3]=s.torqueIntegral;
        v[o+4]=s.voltageSensor; v[o+5]=s.powerFilter; v[o+6]=s.qVrefIntegral;
        v[o+7]=s.verrorEqIntegral; v[o+8]=s.windVarLag; v[o+9]=s.pelecFilter;
    }

    private Derivative derivatives(State s) {
        double voltage = terminalVoltage();
        double scale = systemBaseMva / controlBaseMva;
        double pControl = generator.getP() * scale;
        double qControl = generator.getQ() * scale;
        double compensated = compensatedVoltage(voltage, pControl, qControl);
        double vSensor = algebraicOrState(compensated, s.voltageSensor, data.trv());
        double vError = (voltageReference - vSensor) / data.onlineFraction();
        double vProp = algebraicOrState(data.kpv() * vError, s.windVarLag, data.tvr());
        double qRaw = s.vregIntegral + vProp;
        double vIntegralRate = data.kiv() * vError;
        if ((qRaw >= data.qmax() && vIntegralRate > 0.0)
                || (qRaw <= data.qmin() && vIntegralRate < 0.0)) vIntegralRate = 0.0;
        double qLimited = clamp(qRaw, data.qmin(), data.qmax());
        double qOrder = algebraicOrState(qLimited, s.vregFilter, data.tfv());
        double qCommand = switch (data.varFlag()) {
            case 1 -> qOrder;
            case -1 -> algebraicOrState(generator.getP(), s.pelecFilter, data.tpp())
                    * Math.tan(powerFactorAngle) * scale;
            default -> qReference;
        };
        qCommand = clamp(qCommand, data.qmin(), data.qmax());
        double voltageOrderRate = boundedRate(s.qVrefIntegral,
                data.kqi() * (qCommand - qControl), data.vmincl(), data.vmaxcl());
        double eqMin = data.voltageLimitFlag() == 1 ? voltage + data.xiqmin() : data.xiqmin();
        double eqMax = data.voltageLimitFlag() == 1 ? voltage + data.xiqmax() : data.xiqmax();
        double eqRate = data.voltageLimitFlag() == 0 ? 0.0
                : boundedRate(s.verrorEqIntegral,
                        data.kqv() * (s.qVrefIntegral - voltage), eqMin, eqMax);

        double speedOrder = optimalSpeed(pControl);
        double filteredSpeed = algebraicOrState(speedOrder, s.powerFilter,
                data.powerFilterTime());
        double speedError = speedDeviation - filteredSpeed;
        double torqueTarget = (s.torqueIntegral + data.kpp() * speedError)
                * (1.0 + speedDeviation);
        double torqueIntegralRate = data.kip() * speedError;
        double pOrderRate = data.tfp() <= EPS ? 0.0
                : clamp((torqueTarget - s.torqueFilter) / data.tfp(),
                        data.rpmin(), data.rpmax());
        pOrderRate = boundedRate(s.torqueFilter, pOrderRate, data.pmin(), data.pmax());
        return new Derivative(rate(qLimited, s.vregFilter, data.tfv()), vIntegralRate,
                pOrderRate, torqueIntegralRate,
                rate(compensated, s.voltageSensor, data.trv()),
                rate(speedOrder, s.powerFilter, data.powerFilterTime()),
                voltageOrderRate, eqRate, rate(data.kpv() * vError, s.windVarLag, data.tvr()),
                rate(generator.getP(), s.pelecFilter, data.tpp()));
    }

    private void updateCommands() { publish(state); }

    private void publish(State value) {
        double voltage = terminalVoltage();
        double pOrder = clamp(value.torqueFilter, data.pmin(), data.pmax());
        double ipControl = clamp(pOrder / Math.max(EPS, voltage), 0.0, data.ipmax());
        double eq = data.voltageLimitFlag() == 0 ? value.qVrefIntegral : value.verrorEqIntegral;
        generator.setCommands(ipControl * controlBaseMva / systemBaseMva, eq);
    }

    private double compensatedVoltage(double voltage, double p, double q) {
        double current = Math.hypot(p, q) / Math.max(EPS, voltage);
        return voltage - data.xcc() * current;
    }

    private double optimalSpeed(double p) {
        double[] px = speedPower;
        double[] wy = speedValues;
        if (p <= px[0]) return wy[0] - 1.0;
        for (int i = 1; i < px.length; i++) {
            if (p <= px[i]) {
                double f = (p - px[i - 1]) / (px[i] - px[i - 1]);
                return wy[i - 1] + f * (wy[i] - wy[i - 1]) - 1.0;
            }
        }
        return wy[wy.length - 1] - 1.0;
    }

    private double terminalVoltage() { return generator.getDStabBus().getVoltageMag(); }
    private static double algebraicOrState(double input, double value, double t) { return t <= EPS ? input : value; }
    private static double rate(double input, double value, double t) { return t <= EPS ? 0.0 : (input - value) / t; }
    private static double boundedRate(double value, double derivative, double min, double max) {
        if ((value >= max && derivative > 0) || (value <= min && derivative < 0)) return 0.0;
        return derivative;
    }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }

    private static State advance(State s, Derivative d, double h) {
        return new State(s.vregFilter + h*d.vregFilter, s.vregIntegral + h*d.vregIntegral,
                s.torqueFilter + h*d.torqueFilter, s.torqueIntegral + h*d.torqueIntegral,
                s.voltageSensor + h*d.voltageSensor, s.powerFilter + h*d.powerFilter,
                s.qVrefIntegral + h*d.qVrefIntegral, s.verrorEqIntegral + h*d.verrorEqIntegral,
                s.windVarLag + h*d.windVarLag, s.pelecFilter + h*d.pelecFilter);
    }

    @Override public Map<String, Double> getNamedStates() {
        if (!initialized) return Map.of();
        Map<String, Double> values = new LinkedHashMap<>();
        values.put("Voltage regulator filter", state.vregFilter);
        values.put("Voltage regulator integrator", state.vregIntegral);
        values.put("Torque regulator filter", state.torqueFilter);
        values.put("Torque regulator integrator", state.torqueIntegral);
        values.put("Voltage sensor", state.voltageSensor);
        values.put("Power filter", state.powerFilter);
        values.put("MVAR/Vref integrator", state.qVrefIntegral);
        values.put("Voltage-error/internal-voltage integrator", state.verrorEqIntegral);
        values.put("WindVar lag", state.windVarLag);
        values.put("Fast-PF electrical-power filter", state.pelecFilter);
        return Map.copyOf(values);
    }

    public Wt3e1Data getData() { return data; }
    public Wt3GeneratorModel getGenerator() { return generator; }
    public double getSpeedDeviation() { return speedDeviation; }
    public double getPowerOrder() { return state == null ? 0.0 : state.torqueFilter; }
    public double getSpeedReferenceState() {
        return state == null ? speedDeviation : state.powerFilter;
    }
    public void setSpeedDeviation(double value) { if (!Double.isFinite(value)) throw new IllegalArgumentException("WT3E1 speed must be finite"); speedDeviation = value; }

    private record State(double vregFilter, double vregIntegral, double torqueFilter,
            double torqueIntegral, double voltageSensor, double powerFilter,
            double qVrefIntegral, double verrorEqIntegral, double windVarLag,
            double pelecFilter) { }
    private record Derivative(double vregFilter, double vregIntegral, double torqueFilter,
            double torqueIntegral, double voltageSensor, double powerFilter,
            double qVrefIntegral, double verrorEqIntegral, double windVarLag,
            double pelecFilter) {
        Derivative add(Derivative o) { return new Derivative(vregFilter+o.vregFilter,
                vregIntegral+o.vregIntegral, torqueFilter+o.torqueFilter,
                torqueIntegral+o.torqueIntegral, voltageSensor+o.voltageSensor,
                powerFilter+o.powerFilter, qVrefIntegral+o.qVrefIntegral,
                verrorEqIntegral+o.verrorEqIntegral, windVarLag+o.windVarLag,
                pelecFilter+o.pelecFilter); }
    }
}
