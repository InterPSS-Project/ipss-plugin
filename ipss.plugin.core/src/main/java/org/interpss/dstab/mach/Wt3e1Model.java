package org.interpss.dstab.mach;

import java.util.LinkedHashMap;
import java.util.Map;

import com.interpss.dstab.controller.cml.ICMLStateProvider;

/** Published WT3E1 Type-3 electrical controller. */
public final class Wt3e1Model implements ICMLStateProvider {
    private static final double EPS = 1.0e-10;
    private final Wt3e1Data data;
    private final Wt3g1Model generator;
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

    public Wt3e1Model(Wt3e1Data data, Wt3g1Model generator) {
        this.data = data;
        this.generator = generator;
    }

    public void initialize() {
        double voltage = terminalVoltage();
        systemBaseMva = generator.getDStabBus().getNetwork().getBaseMva();
        controlBaseMva = generator.getData().aggregateRatedMw() * data.onlineFraction();
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
        state = new State(qOrder, qOrder, pOrder, torqueIntegral, compensated,
                speedDeviation, voltage, generator.getInternalVoltageState(),
                0.0, generator.getP());
        initialized = true;
        updateCommands();
    }

    public void step(double dt, int flag) {
        if (!initialized || dt <= 0 || (flag != 0 && flag != 1)) return;
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
        updateCommands();
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

    private void updateCommands() {
        double voltage = terminalVoltage();
        double pOrder = clamp(state.torqueFilter, data.pmin(), data.pmax());
        double ipControl = clamp(pOrder / Math.max(EPS, voltage), 0.0, data.ipmax());
        double eq = data.voltageLimitFlag() == 0 ? state.qVrefIntegral : state.verrorEqIntegral;
        generator.setCommands(ipControl * controlBaseMva / systemBaseMva, eq);
    }

    private double compensatedVoltage(double voltage, double p, double q) {
        double current = Math.hypot(p, q) / Math.max(EPS, voltage);
        return voltage - data.xcc() * current;
    }

    private double optimalSpeed(double p) {
        double[] px = {0.0, .2, .4, .6, data.minimumPowerAtFullSpeed()};
        double[] wy = {data.omegaPmin(), data.omegaP20(), data.omegaP40(),
                data.omegaP60(), data.omegaP100()};
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
