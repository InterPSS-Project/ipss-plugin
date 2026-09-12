package org.interpss.dstab.renewable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.controller.cml.ICMLStateProvider;

/** Published seven-state utility-scale battery electrical controller REECC1. */
public final class Reecc1Model
        implements RenewableElectricalController, ICMLStateProvider {
    private static final double EPS = 1.0e-9;
    private static final double MAX_CONTROL_STEP = 1.0 / 960.0;

    private final Reecc1Data data;
    private final Regca1Model converter;
    private Repca1Model plantController;
    private BaseDStabBus<?, ?> sensedBus;
    private double p0;
    private double q0;
    private double powerFactorRatio;
    private double dipReference;
    private double vMeasured;
    private double pMeasured;
    private double qIntegral;
    private double vIntegral;
    private double qCurrent;
    private double reactiveOutput;
    private double pOrder;
    private double energyOutput;
    private double auxiliaryPower;
    private double ipcmd;
    private double iqcmd;
    private double integrationStep;
    private double minimumTimeConstantMultiplier = 1.0;
    private double effectiveTrv;
    private double effectiveTp;
    private double effectiveTiq;
    private double effectiveTpord;
    private double effectivePmax;
    private double effectivePmin;
    private double effectiveQmax;
    private double effectiveQmin;
    private double effectiveVmax;
    private double effectiveVmin;
    private double effectiveDpmax;
    private double effectiveDpmin;
    private double effectiveIqh1;
    private double effectiveIql1;
    private boolean voltageDip;

    public Reecc1Model(Reecc1Data data) {
        this(data, null);
    }

    public Reecc1Model(Reecc1Data data, Regca1Model converter) {
        this.data = data;
        this.converter = converter;
    }

    @Override
    public void configureIntegrationStep(double timeStepSec) {
        configureIntegrationStep(timeStepSec, 1.0);
    }

    public void configureIntegrationStep(double timeStepSec, double multiplier) {
        integrationStep = timeStepSec;
        minimumTimeConstantMultiplier = multiplier;
    }

    @Override
    public void initialize(double p, double q, double v) {
        resolveSensedBus();
        loadEffectiveParameters();
        double sensedV = sensedVoltage(v);
        p0 = p;
        q0 = q;
        powerFactorRatio = Math.abs(p) > EPS ? q / p : 0.0;
        dipReference = data.vref0() == 0.0 ? sensedV : data.vref0();
        vMeasured = sensedV;
        pMeasured = p;
        qIntegral = data.qFlag() == 1 && data.vFlag() == 1 ? sensedV : 0.0;
        vIntegral = q / nonzero(sensedV);
        qCurrent = data.qFlag() == 0 ? q / nonzero(sensedV) : 0.0;
        reactiveOutput = q / nonzero(sensedV);
        pOrder = p;
        energyOutput = 0.0;

        effectivePmax = Math.max(effectivePmax, pOrder);
        effectivePmin = Math.min(effectivePmin, pOrder);
        effectiveQmax = Math.max(effectiveQmax, q);
        effectiveQmin = Math.min(effectiveQmin, q);
        effectiveVmax = Math.max(effectiveVmax, sensedV);
        effectiveVmin = Math.min(effectiveVmin, sensedV);

        voltageDip = sensedV < data.vdip() || sensedV > data.vup();
        updateCommands();
        if (plantController != null) plantController.initialize(p, q, sensedV);
    }

    private void loadEffectiveParameters() {
        effectiveTrv = correctedBypass(data.trv());
        effectiveTp = correctedBypass(data.tp());
        effectiveTiq = correctedBypass(data.tiq());
        effectiveTpord = correctedBypass(data.tpord());
        effectivePmax = Math.max(data.pmax(), data.pmin());
        effectivePmin = Math.min(data.pmax(), data.pmin());
        effectiveQmax = Math.max(data.qmax(), data.qmin());
        effectiveQmin = Math.min(data.qmax(), data.qmin());
        effectiveVmax = Math.max(data.vmax(), data.vmin());
        effectiveVmin = Math.min(data.vmax(), data.vmin());
        effectiveDpmax = Math.max(data.dpmax(), data.dpmin());
        effectiveDpmin = Math.min(data.dpmax(), data.dpmin());
        effectiveIqh1 = Math.max(data.iqh1(), data.iql1());
        effectiveIql1 = Math.min(data.iqh1(), data.iql1());
    }

    private double correctedBypass(double value) {
        double minimum = minimumTimeConstantMultiplier * integrationStep;
        if (value > 0.0 && value < 0.5 * minimum) return 0.0;
        if (value > 0.5 * minimum && value < minimum) return minimum;
        return value;
    }

    @Override
    public void step(double dt, double p, double q, double v, double frequency) {
        if (dt <= 0.0) return;
        double sensedV = sensedVoltage(v);
        if (plantController != null) plantController.step(dt, p, q, sensedV, frequency);
        int substeps = Math.max(1, (int) Math.ceil(dt / MAX_CONTROL_STEP));
        double controlStep = dt / substeps;
        for (int index = 0; index < substeps; index++) {
            stepControls(controlStep, p, q, sensedV);
        }
    }

    private void stepControls(double dt, double p, double q, double sensedV) {
        voltageDip = sensedV < data.vdip() || sensedV > data.vup();
        vMeasured = lag(vMeasured, sensedV, effectiveTrv, dt);
        pMeasured = lag(pMeasured, p, effectiveTp, dt);

        double plantPref = plantController == null ? 0.0 : plantController.getPref();
        if (!voltageDip) updatePowerOrder(p0 + plantPref, dt);

        double plantQref = plantController == null ? 0.0 : plantController.getQref();
        double selectedQ = data.pfFlag() == 1
                ? pMeasured * powerFactorRatio : q0 + plantQref;
        double qTarget = limit(selectedQ, effectiveQmin, effectiveQmax);
        double voltage = nonzero(vMeasured);
        double rawIp = pOrder / voltage + auxiliaryPower;
        double preliminaryIqLimit = preliminaryReactiveLimit(rawIp);

        if (data.qFlag() == 0) {
            if (!voltageDip) qCurrent = lag(qCurrent, qTarget / voltage, effectiveTiq, dt);
            reactiveOutput = qCurrent;
        } else {
            double qError = data.vFlag() == 1 ? qTarget - q : 0.0;
            qIntegral = integrateWithAntiWindup(qIntegral, data.kqi(), qError,
                    dt, data.kqp(), effectiveVmin, effectiveVmax, voltageDip);
            double voltageCommand = data.vFlag() == 1
                    ? limit(data.kqp() * qError + qIntegral, effectiveVmin, effectiveVmax)
                    : 0.0;
            double voltageError = voltageCommand - vMeasured;
            vIntegral = integrateWithAntiWindup(vIntegral, data.kvi(), voltageError,
                    dt, data.kvp(), -preliminaryIqLimit, preliminaryIqLimit, voltageDip);
            reactiveOutput = limit(data.kvp() * voltageError + vIntegral,
                    -preliminaryIqLimit, preliminaryIqLimit);
        }

        integrateEnergy(dt, p);
        updateCommands();
    }

    private void updatePowerOrder(double pref, double dt) {
        double target = limit(pref, effectivePmin, effectivePmax);
        double rate = effectiveTpord <= EPS ? (target - pOrder) / dt
                : (target - pOrder) / effectiveTpord;
        pOrder = limit(pOrder + limit(rate, effectiveDpmin, effectiveDpmax) * dt,
                effectivePmin, effectivePmax);
    }

    private void integrateEnergy(double dt, double p) {
        double soc = getStateOfCharge();
        if ((soc <= data.minimumSoc() + EPS && p > 0.0)
                || (soc >= data.maximumSoc() - EPS && p < 0.0)) return;
        energyOutput += dt * p / data.dischargeTime();
        double nextSoc = data.initialSoc() - energyOutput;
        if (nextSoc < data.minimumSoc()) energyOutput = data.initialSoc() - data.minimumSoc();
        if (nextSoc > data.maximumSoc()) energyOutput = data.initialSoc() - data.maximumSoc();
    }

    private void updateCommands() {
        double voltageError = deadband(dipReference - vMeasured, data.dbd1(), data.dbd2());
        double iqInjection = limit(data.kqv() * voltageError, effectiveIql1, effectiveIqh1);
        applyCurrentLimits(pOrder / nonzero(vMeasured) + auxiliaryPower,
                -(reactiveOutput + iqInjection));
    }

    private void applyCurrentLimits(double rawIp, double rawIq) {
        double imax = currentLimit();
        double iqTable = tableLimit(vMeasured, true);
        double ipTable = tableLimit(vMeasured, false);
        double ipLower;
        double ipUpper;
        if (data.pqFlag() == 0) {
            double iqMax = Math.min(imax, iqTable);
            rawIq = limit(rawIq, -iqMax, iqMax);
            double magnitude = Math.min(ipTable, remaining(imax, rawIq));
            ipLower = -magnitude;
            ipUpper = magnitude;
        } else {
            double magnitude = Math.min(imax, ipTable);
            ipLower = -magnitude;
            ipUpper = magnitude;
            rawIp = limit(rawIp, socLower(ipLower), socUpper(ipUpper));
            double iqMax = Math.min(iqTable, remaining(imax, rawIp));
            rawIq = limit(rawIq, -iqMax, iqMax);
            ipcmd = rawIp;
            iqcmd = rawIq;
            return;
        }
        rawIp = limit(rawIp, socLower(ipLower), socUpper(ipUpper));
        ipcmd = rawIp;
        iqcmd = rawIq;
    }

    private double socLower(double lower) {
        return getStateOfCharge() >= data.maximumSoc() - EPS ? 0.0 : lower;
    }

    private double socUpper(double upper) {
        return getStateOfCharge() <= data.minimumSoc() + EPS ? 0.0 : upper;
    }

    private double preliminaryReactiveLimit(double rawIp) {
        double iqTable = tableLimit(vMeasured, true);
        if (data.pqFlag() == 0) return Math.min(currentLimit(), iqTable);
        double ipMagnitude = Math.min(currentLimit(), tableLimit(vMeasured, false));
        double limitedIp = limit(rawIp, socLower(-ipMagnitude), socUpper(ipMagnitude));
        return Math.min(iqTable, remaining(currentLimit(), limitedIp));
    }

    private double tableLimit(double voltage, boolean reactive) {
        double[] x = reactive
                ? new double[]{data.vq1(), data.vq2(), data.vq3(), data.vq4()}
                : new double[]{data.vp1(), data.vp2(), data.vp3(), data.vp4()};
        double[] y = reactive
                ? new double[]{data.iq1(), data.iq2(), data.iq3(), data.iq4()}
                : new double[]{data.ip1(), data.ip2(), data.ip3(), data.ip4()};
        int count = validPointCount(x, y);
        if (count == 0) return Double.POSITIVE_INFINITY;
        if (voltage <= x[0]) return Math.max(0.0, y[0]);
        for (int index = 1; index < count; index++) {
            if (voltage <= x[index]) {
                double value = y[index - 1] + (voltage - x[index - 1])
                        * (y[index] - y[index - 1]) / (x[index] - x[index - 1]);
                return Math.max(0.0, value);
            }
        }
        return Math.max(0.0, y[count - 1]);
    }

    private static int validPointCount(double[] x, double[] y) {
        boolean enabled = false;
        for (int index = 0; index < x.length; index++) {
            enabled |= Math.abs(x[index]) > EPS || Math.abs(y[index]) > EPS;
        }
        if (!enabled) return 0;
        int count = 0;
        for (int index = 0; index < x.length; index++) {
            if (index > 0 && x[index] <= x[index - 1]) break;
            count++;
        }
        return count;
    }

    private void resolveSensedBus() {
        if (converter == null || data.remoteBus() == 0) return;
        BaseDStabNetwork<?, ?> network =
                (BaseDStabNetwork<?, ?>) converter.getDStabBus().getNetwork();
        sensedBus = network.getDStabBus("Bus" + data.remoteBus());
        if (sensedBus == null) {
            throw new IllegalStateException("REECC1 remote bus not found: " + data.remoteBus());
        }
    }

    private double sensedVoltage(double localVoltage) {
        return sensedBus == null ? localVoltage : sensedBus.getVoltageMag();
    }

    private double currentLimit() {
        return data.imax() > EPS ? data.imax() : 1.0e8;
    }

    private static double lag(double state, double input, double timeConstant, double dt) {
        return timeConstant <= EPS ? input : state + dt * (input - state) / timeConstant;
    }

    private static double integrateWithAntiWindup(double integral, double gain, double error,
            double dt, double proportionalGain, double lower, double upper, boolean frozen) {
        return Repca1Model.integrateWithAntiWindup(integral, gain, error, dt,
                proportionalGain, lower, upper, frozen);
    }

    private static double deadband(double value, double lower, double upper) {
        return Repca1Model.deadband(value, lower, upper);
    }

    private static double limit(double value, double lower, double upper) {
        return Repca1Model.limit(value, lower, upper);
    }

    private static double remaining(double total, double priority) {
        return Math.sqrt(Math.max(0.0, total * total - priority * priority));
    }

    private static double nonzero(double value) {
        return Math.max(0.01, Math.abs(value));
    }

    public Reecc1Data getData() { return data; }
    @Override public Repca1Model getPlantController() { return plantController; }
    @Override public void setPlantController(Repca1Model controller) { plantController = controller; }
    public double getMeasuredVoltage() { return vMeasured; }
    public double getMeasuredActivePower() { return pMeasured; }
    public double getReactivePowerIntegral() { return qIntegral; }
    public double getVoltageIntegral() { return vIntegral; }
    public double getReactiveCurrentState() { return qCurrent; }
    public double getReactiveOutput() { return reactiveOutput; }
    public double getActivePowerOrder() { return pOrder; }
    public double getEnergyOutput() { return energyOutput; }
    public double getStateOfCharge() { return data.initialSoc() - energyOutput; }
    public void setAuxiliaryPower(double value) { auxiliaryPower = value; }
    public double getAuxiliaryPower() { return auxiliaryPower; }
    @Override public double getIpcmd() { return ipcmd; }
    @Override public double getIqcmd() { return iqcmd; }
    public boolean isVoltageDip() { return voltageDip; }

    @Override
    public Map<String, Double> getNamedStates() {
        Map<String, Double> states = new LinkedHashMap<>();
        states.put("Voltage Measurement Filter", vMeasured);
        states.put("Real Power Filter", pMeasured);
        states.put("Reactive Power PI", qIntegral);
        states.put("Voltage Error PI", vIntegral);
        states.put("Reactive Current Lag", qCurrent);
        states.put("Power Order Lag", pOrder);
        states.put("Battery Energy Output", energyOutput);
        return Collections.unmodifiableMap(states);
    }
}
