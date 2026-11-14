package org.interpss.dstab.mach;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.controller.cml.ICMLStateProvider;

/** Published WT4E1 electrical controller for a Type-4 converter. */
public final class Wt4e1Model implements ICMLStateProvider {
    private static final Logger LOG = LoggerFactory.getLogger(Wt4e1Model.class);
    private static final double EPS = 1.0e-10;
    private static final double ZERO_VOLTAGE_REACTIVE_CURRENT_LIMIT = 1.6;
    private final Wt4e1Data data;
    private final Wt4g1Model generator;
    private BaseDStabBus<?, ?> voltageSensorBus;
    private State state;
    private State oldState;
    private Derivative predictor;
    private double voltageReference;
    private double reactiveReference;
    private double powerReference;
    private double powerFactorAngle;
    private boolean initialized;

    public Wt4e1Model(Wt4e1Data data, Wt4g1Model generator) {
        this.data = data;
        this.generator = generator;
    }

    public void initialize() {
        BaseDStabBus<?, ?> terminal = generator.getDStabBus();
        voltageSensorBus = terminal;
        if (data.remoteBus() != 0) {
            var remote = terminal.getNetwork().getBus("Bus" + data.remoteBus());
            if (remote instanceof BaseDStabBus<?, ?> bus) {
                voltageSensorBus = bus;
            } else {
                // PSS/E uses the terminal sensor when the requested bus is absent.
                LOG.warn("WT4E1 at {} cannot find voltage control bus {}; using terminal voltage",
                        terminal.getId(), data.remoteBus());
            }
        }
        double voltage = terminal.getVoltageMag();
        double sensorVoltage = voltageSensorBus.getVoltageMag();
        double p = generator.getP();
        double q = generator.getQ();
        voltageReference = sensorVoltage;
        reactiveReference = q;
        powerReference = p;
        powerFactorAngle = Math.atan2(q, p);
        double ip = p / Math.max(EPS, voltage);
        double iq = q / Math.max(EPS, voltage);
        state = new State(q, q, 0.0, 0.0, sensorVoltage, p, voltage, iq, 0.0, p);
        oldState = null;
        predictor = null;
        initialized = true;
        generator.setCommands(ip, iq);
    }

    public void step(double dt, int flag) {
        if (!initialized || dt <= 0 || (flag != 0 && flag != 1)) return;
        if (flag == 1 && oldState == null) return;
        double limit = reactiveCurrentLimit(generator.getDStabBus().getVoltageMag());
        state = state.withReactiveCurrentLimit(limit);
        if (flag == 0) {
            oldState = state;
            predictor = derivatives(state);
            state = advance(oldState, predictor, dt);
        } else {
            Derivative corrected = derivatives(state);
            state = advance(oldState, predictor.add(corrected), .5 * dt);
            oldState = null;
            predictor = null;
        }
        state = state.withReactiveCurrentLimit(limit);
        updateCommands();
    }

    private Derivative derivatives(State s) {
        double voltage = generator.getDStabBus().getVoltageMag();
        double p = generator.getP();
        double q = generator.getQ();
        // WindVAR regulates the selected bus. The inner voltage loop and
        // converter current calculations still use the physical terminal.
        double sensorVoltage = voltageSensorBus.getVoltageMag();
        double sensedVoltage = algebraicOrState(sensorVoltage, s.voltageSensor, data.voltageSensorTime());
        double vError = voltageReference - sensedVoltage;
        double vProp = algebraicOrState(data.voltageProportionalGain() * vError,
                s.windVarLag, data.windVarTime());
        double qRaw = s.voltageIntegral + vProp;
        double vIntegralRate = boundedRate(qRaw, data.voltageIntegralGain() * vError,
                data.reactiveMinimum(), data.reactiveMaximum());
        double qOrder = algebraicOrState(clamp(qRaw, data.reactiveMinimum(),
                data.reactiveMaximum()), s.voltageFilter, data.voltageFilterTime());
        double qCommand = data.varFlag() == 1 ? qOrder
                : data.powerFactorFlag() == 1
                        ? algebraicOrState(p, s.powerFactorFilter,
                                data.powerFactorFilterTime()) * Math.tan(powerFactorAngle)
                        : reactiveReference;
        double qVoltageRate = boundedRate(s.qVoltageIntegral,
                data.reactiveVoltageGain() * (qCommand - q),
                data.voltageMinimum(), data.voltageMaximum());
        double currentLimit = reactiveCurrentLimit(voltage);
        double iqRate = boundedRate(s.internalVoltage,
                data.voltageErrorGain() * (s.qVoltageIntegral - voltage), -currentLimit, currentLimit);

        double measuredP = algebraicOrState(p, s.powerFilter, data.powerFilterTime());
        double pError = measuredP - powerReference;
        double powerIntegralRate = data.powerIntegralGain() * pError;
        double piOutput = s.powerIntegral + data.powerProportionalGain() * pError;
        return new Derivative(rate(clamp(qRaw, data.reactiveMinimum(), data.reactiveMaximum()),
                        s.voltageFilter, data.voltageFilterTime()),
                vIntegralRate, powerIntegralRate, rate(piOutput, s.powerFeedback,
                        data.rateFeedbackTime()),
                rate(sensorVoltage, s.voltageSensor, data.voltageSensorTime()),
                rate(p, s.powerFilter, data.powerFilterTime()), qVoltageRate, iqRate,
                rate(data.voltageProportionalGain() * vError, s.windVarLag, data.windVarTime()),
                rate(p, s.powerFactorFilter, data.powerFactorFilterTime()));
    }

    private double reactiveCurrentLimit(double voltage) {
        // The converter's symmetric reactive-current envelope joins 1.6 pu at
        // zero terminal voltage to QMX at one pu. Iqhl caps the envelope.
        // Use the physical terminal voltage even when WindVAR senses a remote bus.
        double envelope = ZERO_VOLTAGE_REACTIVE_CURRENT_LIMIT
                + (data.reactiveMaximum() - ZERO_VOLTAGE_REACTIVE_CURRENT_LIMIT) * voltage;
        return clamp(envelope, 0.0, data.hardReactiveCurrentMaximum());
    }

    private void updateCommands() {
        double voltage = Math.max(EPS, generator.getDStabBus().getVoltageMag());
        double measuredP = algebraicOrState(generator.getP(), state.powerFilter,
                data.powerFilterTime());
        double pError = measuredP - powerReference;
        double piOutput = state.powerIntegral + data.powerProportionalGain() * pError;
        double rateFeedback = data.rateFeedbackTime() <= EPS ? 0.0
                : data.rateFeedbackGain() * (piOutput - state.powerFeedback)
                        / data.rateFeedbackTime();
        double deltaPower = clamp(piOutput - rateFeedback,
                data.powerRateMinimum(), data.powerRateMaximum());
        double ip = clamp((powerReference + deltaPower) / voltage, 0.0,
                Math.min(data.activeCurrentMaximum(), data.hardActiveCurrentMaximum()));
        double iq = state.internalVoltage;
        double total = Math.hypot(ip, iq);
        if (total > data.converterCurrentMaximum()) {
            if (data.pqPriorityFlag() == 0) ip = Math.sqrt(Math.max(0.0,
                    data.converterCurrentMaximum() * data.converterCurrentMaximum() - iq * iq));
            else iq = Math.copySign(Math.sqrt(Math.max(0.0,
                    data.converterCurrentMaximum() * data.converterCurrentMaximum() - ip * ip)), iq);
        }
        iq = clamp(iq, -data.hardReactiveCurrentMaximum(), data.hardReactiveCurrentMaximum());
        generator.setCommands(ip, iq);
    }

    @Override public Map<String, Double> getNamedStates() {
        if (!initialized) return Map.of();
        Map<String, Double> values = new LinkedHashMap<>();
        // A bypassed filter exposes the algebraic order on the same
        // boundary as the live voltage sensor, with no integration memory.
        double voltageError = voltageReference - algebraicOrState(voltageSensorBus.getVoltageMag(),
                state.voltageSensor, data.voltageSensorTime());
        double windVar = algebraicOrState(data.voltageProportionalGain() * voltageError,
                state.windVarLag, data.windVarTime());
        values.put("Voltage regulator filter", algebraicOrState(
                clamp(state.voltageIntegral + windVar, data.reactiveMinimum(), data.reactiveMaximum()),
                state.voltageFilter, data.voltageFilterTime()));
        values.put("Voltage regulator integrator", state.voltageIntegral);
        values.put("Active-power regulator integrator", state.powerIntegral);
        values.put("Active-power regulator feedback", state.powerFeedback);
        // With TRV bypassed, the native sensor slot follows the live input.
        // Keep the dormant integration memory separate from that observation.
        values.put("Voltage sensor", algebraicOrState(voltageSensorBus.getVoltageMag(),
                state.voltageSensor, data.voltageSensorTime()));
        values.put("Power filter", state.powerFilter);
        values.put("MVAR/Vref integrator", state.qVoltageIntegral);
        values.put("Voltage-error/internal-voltage integrator", state.internalVoltage);
        values.put("WindVar lag", state.windVarLag);
        values.put("Fast-PF electrical-power filter", state.powerFactorFilter);
        return Map.copyOf(values);
    }

    private static double algebraicOrState(double input, double state, double time) { return time <= EPS ? input : state; }
    private static double rate(double input, double state, double time) { return time <= EPS ? 0.0 : (input - state) / time; }
    private static double boundedRate(double value, double derivative, double min, double max) { return (value >= max && derivative > 0) || (value <= min && derivative < 0) ? 0.0 : derivative; }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    private static State advance(State s, Derivative d, double h) { return new State(
            s.voltageFilter+h*d.voltageFilter, s.voltageIntegral+h*d.voltageIntegral,
            s.powerIntegral+h*d.powerIntegral, s.powerFeedback+h*d.powerFeedback,
            s.voltageSensor+h*d.voltageSensor, s.powerFilter+h*d.powerFilter,
            s.qVoltageIntegral+h*d.qVoltageIntegral, s.internalVoltage+h*d.internalVoltage,
            s.windVarLag+h*d.windVarLag, s.powerFactorFilter+h*d.powerFactorFilter); }
    public Wt4e1Data getData() { return data; }
    private record State(double voltageFilter, double voltageIntegral, double powerIntegral,
            double powerFeedback, double voltageSensor, double powerFilter,
            double qVoltageIntegral, double internalVoltage, double windVarLag,
            double powerFactorFilter) {
        State withReactiveCurrentLimit(double limit) {
            double current = clamp(internalVoltage, -limit, limit);
            if (current == internalVoltage) return this;
            // Project the integrator itself so voltage recovery releases the
            // limit immediately. Clipping only the command leaves it wound up.
            return new State(voltageFilter, voltageIntegral, powerIntegral, powerFeedback,
                    voltageSensor, powerFilter, qVoltageIntegral, current, windVarLag, powerFactorFilter);
        }
    }
    private record Derivative(double voltageFilter, double voltageIntegral, double powerIntegral,
            double powerFeedback, double voltageSensor, double powerFilter,
            double qVoltageIntegral, double internalVoltage, double windVarLag,
            double powerFactorFilter) {
        Derivative add(Derivative o) { return new Derivative(voltageFilter+o.voltageFilter,
                voltageIntegral+o.voltageIntegral, powerIntegral+o.powerIntegral,
                powerFeedback+o.powerFeedback, voltageSensor+o.voltageSensor,
                powerFilter+o.powerFilter, qVoltageIntegral+o.qVoltageIntegral,
                internalVoltage+o.internalVoltage, windVarLag+o.windVarLag,
                powerFactorFilter+o.powerFactorFilter); }
    }
}
