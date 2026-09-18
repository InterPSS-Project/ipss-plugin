package org.interpss.dstab.renewable;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;

/**
 * WECC REEC_B electrical controller producing REGC_A current commands.
 *
 * <p>The state and limiter ordering follows the published PowerWorld/WECC
 * diagram. The algebraic current-priority logic and non-positive-Imax
 * convention are cross-checked against ANDES 2.0.</p>
 */
public final class Reecb1Model implements RenewableElectricalController {
    private static final double EPS = 1.0e-9;
    private static final double MAX_CONTROL_STEP = 1.0 / 960.0;

    private final Reecb1Data data;
    private final Regca1Model converter;
    private Repca1Model plantController;
    private BaseDStabBus<?, ?> sensedBus;
    private double p0;
    private double q0;
    private double powerFactorRatio;
    private double dipReference;
    private double vMeasured;
    private double pMeasured;
    private double pOrder;
    private double qCurrent;
    private double qOuterIntegral;
    private double vIntegral;
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
    private double integrationStep;
    private double minimumTimeConstantMultiplier = 1.0;
    private boolean voltageDip;
    private double ipcmd;
    private double iqcmd;

    public Reecb1Model(Reecb1Data data) {
        this(data, null);
    }

    public Reecb1Model(Reecb1Data data, Regca1Model converter) {
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
        pOrder = p;
        qCurrent = q / nonzero(sensedV);
        qOuterIntegral = 0.0;
        vIntegral = qCurrent;

        effectivePmax = Math.max(effectivePmax, pOrder);
        effectivePmin = Math.min(effectivePmin, pOrder);
        effectiveQmax = Math.max(effectiveQmax, q);
        effectiveQmin = Math.min(effectiveQmin, q);
        effectiveVmax = Math.max(effectiveVmax, 0.0);
        effectiveVmin = Math.min(effectiveVmin, 0.0);

        voltageDip = sensedV < data.vdip() || sensedV > data.vup();
        ipcmd = p / nonzero(sensedV);
        iqcmd = -qCurrent;
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
        for (int i = 0; i < substeps; i++) {
            stepControls(controlStep, p, q, sensedV);
        }
    }

    private void stepControls(double dt, double p, double q, double sensedV) {
        // The dip comparator uses terminal voltage before the measurement lag.
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
        double rawIp = pOrder / voltage;

        double preliminaryIqMax = data.pqFlag() == 0
                ? currentLimit() : remaining(currentLimit(), limit(rawIp, 0.0, currentLimit()));
        if (data.qFlag() == 0) {
            if (!voltageDip) {
                qCurrent = lag(qCurrent, qTarget / voltage, effectiveTiq, dt);
            }
        } else {
            double qError = data.vFlag() == 1 ? qTarget - q : 0.0;
            qOuterIntegral = integrateWithAntiWindup(qOuterIntegral, data.kqi(), qError,
                    dt, data.kqp(), effectiveVmin, effectiveVmax, voltageDip);
            double voltageCommand = data.vFlag() == 1
                    ? limit(data.kqp() * qError + qOuterIntegral,
                            effectiveVmin, effectiveVmax)
                    : 0.0;
            vIntegral = integrateWithAntiWindup(vIntegral, data.kvi(), voltageCommand,
                    dt, data.kvp(), -preliminaryIqMax, preliminaryIqMax, voltageDip);
            qCurrent = limit(data.kvp() * voltageCommand + vIntegral,
                    -preliminaryIqMax, preliminaryIqMax);
        }

        // Voltage_dip freezes states 3-6; it does not gate algebraic Iqinj.
        double voltageError = deadband(dipReference - vMeasured, data.dbd1(), data.dbd2());
        double iqInjection = limit(data.kqv() * voltageError,
                effectiveIql1, effectiveIqh1);
        applyCurrentLimits(rawIp, -(qCurrent + iqInjection));
    }

    private void updatePowerOrder(double pref, double dt) {
        double target = limit(pref, effectivePmin, effectivePmax);
        double unconstrainedRate = effectiveTpord <= EPS
                ? (target - pOrder) / dt : (target - pOrder) / effectiveTpord;
        double rate = limit(unconstrainedRate, effectiveDpmin, effectiveDpmax);
        pOrder = limit(pOrder + rate * dt, effectivePmin, effectivePmax);
    }

    private void applyCurrentLimits(double rawIp, double rawIq) {
        double imax = currentLimit();
        if (data.pqFlag() == 0) {
            rawIq = limit(rawIq, -imax, imax);
            rawIp = limit(rawIp, 0.0, remaining(imax, rawIq));
        } else {
            rawIp = limit(rawIp, 0.0, imax);
            double iqMax = remaining(imax, rawIp);
            rawIq = limit(rawIq, -iqMax, iqMax);
        }
        ipcmd = rawIp;
        iqcmd = rawIq;
    }

    private void resolveSensedBus() {
        if (converter == null || data.remoteBus() == 0) return;
        BaseDStabNetwork<?, ?> network =
                (BaseDStabNetwork<?, ?>) converter.getDStabBus().getNetwork();
        sensedBus = network.getDStabBus("Bus" + data.remoteBus());
        if (sensedBus == null) {
            throw new IllegalStateException("REECB1 remote bus not found: " + data.remoteBus());
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

    public Reecb1Data getData() { return data; }
    @Override public Repca1Model getPlantController() { return plantController; }
    @Override public void setPlantController(Repca1Model controller) { plantController = controller; }
    public double getMeasuredVoltage() { return vMeasured; }
    public double getMeasuredActivePower() { return pMeasured; }
    public double getActivePowerOrder() { return pOrder; }
    public double getReactiveCurrentState() { return qCurrent; }
    public double getQControlIntegral() { return qOuterIntegral; }
    public double getVoltageControlIntegral() { return vIntegral; }
    public double getEffectiveTrv() { return effectiveTrv; }
    public double getEffectiveTp() { return effectiveTp; }
    public double getEffectiveTiq() { return effectiveTiq; }
    public double getEffectiveTpord() { return effectiveTpord; }
    public double getEffectivePmax() { return effectivePmax; }
    public double getEffectivePmin() { return effectivePmin; }
    public boolean isVoltageDip() { return voltageDip; }
    @Override public double getIpcmd() { return ipcmd; }
    @Override public double getIqcmd() { return iqcmd; }
}
