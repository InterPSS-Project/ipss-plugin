package org.interpss.dstab.renewable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.controller.cml.ICMLStateProvider;

/** WECC REEC_A electrical controller producing REGC_A current commands. */
public final class Reeca1Model implements RenewableElectricalController, ICMLStateProvider {
    private static final double EPS = 1.0e-9;
    /** Suppress only solver partitioning roundoff at the initialized equilibrium. */
    private static final double EQUILIBRIUM_RESIDUAL = 1.0e-8;
    /** Maximum internal step for the stiff cascaded Q/voltage PI controls. */
    private static final double MAX_CONTROL_STEP = 1.0 / 960.0;

    private final Reeca1Data data;
    private final Regca1Model converter;
    private Repca1Model plantController;
    private WindControlStack windControlStack;
    private BaseDStabBus<?, ?> sensedBus;
    private double p0;
    private double q0;
    private double localVoltage0;
    private double sensedVoltage0;
    private double qReference;
    private double powerFactorRatio;
    private double dipReference;
    private double vMeasured;
    private double pMeasured;
    private double pFilter;
    private double pOrder;
    private double qCurrent;
    private double qIntegral;
    private double vIntegral;
    private double reactivePowerTarget;
    private double reactiveControlError;
    private double reactiveControlPreLimitOutput;
    private double qControlOutput;
    private double voltageControlError;
    private double voltageControlPreLimitOutput;
    private double voltageControlOutput;
    private double preliminaryIqLimit;
    private double reactiveCurrentInjection;
    private double effectivePmax;
    private double effectivePmin;
    private double effectiveQmax;
    private double effectiveQmin;
    private double effectiveVmax;
    private double effectiveVmin;
    private double postDipTimer;
    private double iqHoldTimer;
    private double heldIpMax;
    private double ipLimit;
    private double iqLimit;
    private boolean previousDip;
    private double ipcmd;
    private double iqcmd;

    public Reeca1Model(Reeca1Data data, Regca1Model converter) {
        this.data = data;
        this.converter = converter;
    }

    @Override
    public void initialize(double p, double q, double v) {
        resolveSensedBus();
        double sensedV = sensedVoltage(v);
        p0 = p;
        q0 = q;
        localVoltage0 = v;
        sensedVoltage0 = sensedV;
        // Qext is a Q reference except in QFLAG=1/VFLAG=0 local-voltage
        // control, where the PowerWorld/WECC diagram routes it as a voltage
        // bias before the Q limits.  Initialize either path at equilibrium.
        qReference = data.qFlag() == 1 && data.vFlag() == 0
                ? sensedV - data.vref1() : q;
        powerFactorRatio = Math.abs(p) > EPS ? q / p : 0.0;
        dipReference = data.vref0() == 0.0 ? sensedV : data.vref0();
        vMeasured = sensedV;
        pMeasured = pFilter = pOrder = p;
        qCurrent = q / nonzero(v);
        // In the PowerWorld/WECC REEC_A diagram PIQ produces an absolute
        // voltage reference.  The following summing junction subtracts the
        // filtered terminal voltage before PIV, so coordinated Q/V control
        // initializes PIQ at the sensed voltage, not at a zero-based bias.
        qIntegral = data.qFlag() == 1 && data.vFlag() == 1 ? sensedV : 0.0;
        vIntegral = qCurrent;
        reactivePowerTarget = q;
        reactiveControlError = 0.0;
        reactiveControlPreLimitOutput = qIntegral;
        qControlOutput = qIntegral;
        voltageControlError = 0.0;
        voltageControlPreLimitOutput = qCurrent;
        voltageControlOutput = qCurrent;
        effectivePmax = Math.max(data.pmax(), pOrder);
        effectivePmin = Math.min(data.pmin(), pOrder);
        effectiveQmax = Math.max(data.qmax(), q);
        effectiveQmin = Math.min(data.qmin(), q);
        // Both VFLAG branches carry an absolute voltage reference at VMAX/VMIN.
        // Expand around the initialized reference so initialization cannot
        // introduce an artificial step when case data has inconsistent limits.
        double initialVoltagePath = sensedV;
        effectiveVmax = Math.max(data.vmax(), initialVoltagePath);
        effectiveVmin = Math.min(data.vmin(), initialVoltagePath);
        postDipTimer = iqHoldTimer = 0.0;
        heldIpMax = currentLimit();
        ipLimit = iqLimit = currentLimit();
        preliminaryIqLimit = currentLimit();
        reactiveCurrentInjection = 0.0;
        previousDip = false;
        ipcmd = p / nonzero(v);
        iqcmd = -qCurrent;
        if (plantController != null) plantController.initialize(p, q, sensedV);
        if (windControlStack != null) windControlStack.initialize(p);
    }

    @Override
    public void step(double dt, double p, double q, double v, double frequency) {
        // The network/controller partition can return residuals at machine
        // precision even when no event or reference change has occurred. PSS/E
        // preserves that initialized equilibrium. Apply the same numerical-zero
        // convention before forming constant-power current commands; otherwise a
        // weak fleet can amplify a 1e-12 input residual into a false trajectory.
        p = snapToInitial(p, p0);
        q = snapToInitial(q, q0);
        v = snapToInitial(v, localVoltage0);
        frequency = snapToInitial(frequency, 1.0);
        double sensedV = snapToInitial(sensedVoltage(v), sensedVoltage0);
        if (plantController != null) plantController.step(dt, p, q, sensedV, frequency);
        boolean voltageDip = sensedV < data.vdip() || sensedV > data.vup();
        double plantPref = plantController == null ? 0.0 : plantController.getPref();
        if (windControlStack != null) {
            windControlStack.step(dt, p, pOrder, p0 + plantPref, voltageDip);
        }

        int substeps = Math.max(1, (int) Math.ceil(dt / MAX_CONTROL_STEP));
        double controlStep = dt / substeps;
        for (int i = 0; i < substeps; i++) {
            stepElectricalControls(controlStep, p, q, v, sensedV);
        }
    }

    private void stepElectricalControls(double dt, double p, double q, double v,
            double sensedV) {
        boolean voltageDip = sensedV < data.vdip() || sensedV > data.vup();
        updateDipTimers(dt, voltageDip);
        vMeasured = Repca1Model.lag(vMeasured, sensedV, data.trv(), dt);
        pMeasured = Repca1Model.lag(pMeasured, p, data.tp(), dt);

        double plantPref = plantController == null ? 0.0 : plantController.getPref();
        double pref = windControlStack != null && windControlStack.getTorqueController() != null
                ? windControlStack.getPref() : p0 + plantPref;
        double rateTarget = Repca1Model.limit(pref,
                pFilter + data.dpmin() * dt, pFilter + data.dpmax() * dt);
        pFilter = rateTarget;
        // PFLAG selects drive-train generator speed wg, not network frequency.
        // WTTQ_A already multiplies torque by wg when producing Pref and, as in
        // ANDES, resets the REEC_A multiplier to 1.0 to avoid applying speed
        // twice.
        double generatorSpeed = windControlStack == null ? 1.0
                : windControlStack.getElectricalControllerSpeed();
        double selectedP = data.pFlag() == 1 ? generatorSpeed * pFilter : pFilter;
        if (!voltageDip) {
            pOrder = Repca1Model.lag(pOrder,
                    Repca1Model.limit(selectedP, effectivePmin, effectivePmax),
                    data.tpord(), dt);
            pOrder = Repca1Model.limit(pOrder, effectivePmin, effectivePmax);
        }

        double plantQref = plantController == null ? 0.0 : plantController.getQref();
        double selectedQ = data.pfFlag() == 1
                ? pMeasured * powerFactorRatio : qReference + plantQref;
        double qTarget = Repca1Model.limit(selectedQ, effectiveQmin, effectiveQmax);
        reactivePowerTarget = qTarget;

        // The published PowerWorld/WECC REEC_A diagram feeds State 1
        // (Vt_filt), with the 0.01 pu floor, to both current-command dividers.
        double currentConversionVoltage = nonzero(vMeasured);
        double rawIp = pOrder / currentConversionVoltage;
        double rawQCurrent;
        if (data.qFlag() == 0) {
            reactiveControlError = 0.0;
            reactiveControlPreLimitOutput = 0.0;
            qControlOutput = 0.0;
            if (!voltageDip) {
                qCurrent = Repca1Model.lag(qCurrent, qTarget / currentConversionVoltage,
                        data.tiq(), dt);
            }
            rawQCurrent = qCurrent;
            voltageControlError = 0.0;
            voltageControlPreLimitOutput = rawQCurrent;
            voltageControlOutput = rawQCurrent;
            preliminaryIqLimit = preliminaryReactiveCurrentLimit(rawIp);
        } else {
            double preliminaryIqMax = preliminaryReactiveCurrentLimit(rawIp);
            double voltageBias;
            if (data.vFlag() == 1) {
                double qError = qTarget - q;
                reactiveControlError = qError;
                double oldQIntegral = qIntegral;
                double candidateQIntegral = Repca1Model.integrateWithAntiWindup(
                        oldQIntegral, data.kqi(), qError, dt, data.kqp(),
                        effectiveVmin, effectiveVmax, voltageDip);
                qIntegral = candidateQIntegral;
                reactiveControlPreLimitOutput = data.kqp() * qError + qIntegral;
                voltageBias = Repca1Model.limit(reactiveControlPreLimitOutput,
                        effectiveVmin, effectiveVmax);

                // WECC Figure 3-2 links the cascaded s2/s3 non-windup
                // integrators. If the downstream voltage PI is saturated and
                // both derivatives drive farther into the same limit, freeze
                // the upstream Q integrator as well. Opposite motion remains
                // enabled so either state can recover from a limit.
                double voltageError = voltageBias - vMeasured;
                double downstreamPreLimit = data.kvp() * voltageError + vIntegral;
                double upstreamDelta = candidateQIntegral - oldQIntegral;
                double downstreamRate = data.kvi() * voltageError;
                boolean linkedUpperFreeze = downstreamPreLimit >= preliminaryIqMax - EPS
                        && downstreamRate > 0.0 && upstreamDelta > 0.0;
                boolean linkedLowerFreeze = downstreamPreLimit <= -preliminaryIqMax + EPS
                        && downstreamRate < 0.0 && upstreamDelta < 0.0;
                if (linkedUpperFreeze || linkedLowerFreeze) {
                    qIntegral = oldQIntegral;
                    reactiveControlPreLimitOutput = data.kqp() * qError + qIntegral;
                    voltageBias = Repca1Model.limit(reactiveControlPreLimitOutput,
                            effectiveVmin, effectiveVmax);
                }
            } else {
                // VMAX/VMIN are downstream of the VFLAG selector in REEC_A,
                // so they constrain the direct Vref1+Qext branch as well as
                // the coordinated Q-PI branch.
                voltageBias = Repca1Model.limit(data.vref1() + selectedQ,
                        effectiveVmin, effectiveVmax);
                reactiveControlError = 0.0;
                reactiveControlPreLimitOutput = data.vref1() + selectedQ;
            }
            qControlOutput = voltageBias;
            // The PowerWorld/WECC summing junction following the VFLAG selector
            // subtracts State 1 (Vt_filt) from either absolute-reference path.
            double voltageError = voltageBias - vMeasured;
            voltageControlError = voltageError;
            preliminaryIqLimit = preliminaryIqMax;
            vIntegral = Repca1Model.integrateWithAntiWindup(vIntegral, data.kvi(), voltageError,
                    dt, data.kvp(), -preliminaryIqMax, preliminaryIqMax, voltageDip);
            voltageControlPreLimitOutput = data.kvp() * voltageError + vIntegral;
            rawQCurrent = Repca1Model.limit(voltageControlPreLimitOutput,
                    -preliminaryIqMax, preliminaryIqMax);
            voltageControlOutput = rawQCurrent;
        }

        double iqInjection = reactiveCurrentInjection(voltageDip);
        reactiveCurrentInjection = iqInjection;
        double rawIq = -(rawQCurrent + iqInjection);
        applyCurrentLimits(rawIp, rawIq, voltageDip);
        previousDip = voltageDip;
    }

    private void applyCurrentLimits(double rawIp, double rawIq, boolean voltageDip) {
        double iqTable = currentTableLimit(vMeasured, true, Double.POSITIVE_INFINITY);
        double ipTable = currentTableLimit(vMeasured, false, Double.POSITIVE_INFINITY);
        if (data.pqFlag() == 0) {
            double iqMax = Math.min(currentLimit(), iqTable);
            rawIq = Repca1Model.limit(rawIq, -iqMax, iqMax);
            double calculatedIpMax = Math.min(ipTable, remaining(currentLimit(), rawIq));
            if (voltageDip) heldIpMax = calculatedIpMax;
            double ipMax = postDipTimer > 0.0 ? heldIpMax : calculatedIpMax;
            rawIp = Repca1Model.limit(rawIp, 0.0, ipMax);
            iqLimit = iqMax;
            ipLimit = ipMax;
        } else {
            double calculatedIpMax = Math.min(currentLimit(), ipTable);
            if (voltageDip) heldIpMax = calculatedIpMax;
            double ipMax = postDipTimer > 0.0 ? heldIpMax : calculatedIpMax;
            rawIp = Repca1Model.limit(rawIp, 0.0, ipMax);
            double iqMax = Math.min(iqTable, remaining(currentLimit(), rawIp));
            rawIq = Repca1Model.limit(rawIq, -iqMax, iqMax);
            ipLimit = ipMax;
            iqLimit = iqMax;
        }
        ipcmd = rawIp;
        iqcmd = rawIq;
    }

    private double preliminaryReactiveCurrentLimit(double rawIp) {
        double iqTable = currentTableLimit(vMeasured, true, Double.POSITIVE_INFINITY);
        if (data.pqFlag() == 0) return Math.min(currentLimit(), iqTable);
        double calculatedIpMax = Math.min(currentLimit(),
                currentTableLimit(vMeasured, false, Double.POSITIVE_INFINITY));
        double ipMax = postDipTimer > 0.0 ? heldIpMax : calculatedIpMax;
        double limitedIp = Repca1Model.limit(rawIp, 0.0, ipMax);
        return Math.min(iqTable, remaining(currentLimit(), limitedIp));
    }

    private double reactiveCurrentInjection(boolean voltageDip) {
        double error = Repca1Model.deadband(dipReference - vMeasured, data.dbd1(), data.dbd2());
        double injection = Repca1Model.limit(data.kqv() * error, data.iql1(), data.iqh1());
        if (voltageDip) {
            return injection;
        }
        if (iqHoldTimer <= 0.0) return 0.0;
        return data.thld() > 0.0 ? data.iqfrz() : injection;
    }

    private void updateDipTimers(double dt, boolean voltageDip) {
        if (previousDip && !voltageDip) {
            postDipTimer = data.thld2();
            iqHoldTimer = Math.abs(data.thld());
        } else if (!voltageDip) {
            postDipTimer = Math.max(0.0, postDipTimer - dt);
            iqHoldTimer = Math.max(0.0, iqHoldTimer - dt);
        }
    }

    private double currentTableLimit(double voltage, boolean reactive, double disabledValue) {
        double[] x = reactive
                ? new double[]{data.vq1(), data.vq2(), data.vq3(), data.vq4()}
                : new double[]{data.vp1(), data.vp2(), data.vp3(), data.vp4()};
        double[] y = reactive
                ? new double[]{data.iq1(), data.iq2(), data.iq3(), data.iq4()}
                : new double[]{data.ip1(), data.ip2(), data.ip3(), data.ip4()};
        if (!validTable(x, y)) return disabledValue;
        if (voltage <= x[0]) return y[0];
        for (int i = 1; i < x.length; i++) {
            if (voltage <= x[i]) {
                return y[i - 1] + (voltage - x[i - 1]) * (y[i] - y[i - 1])
                        / (x[i] - x[i - 1]);
            }
        }
        return y[y.length - 1];
    }

    private static boolean validTable(double[] x, double[] y) {
        boolean nonzero = false;
        for (int i = 0; i < x.length; i++) {
            nonzero |= Math.abs(x[i]) > EPS || Math.abs(y[i]) > EPS;
            if (i > 0 && (x[i] <= x[i - 1] || y[i] < y[i - 1])) return false;
        }
        return nonzero;
    }

    private void resolveSensedBus() {
        if (converter == null || data.remoteBus() == 0) return;
        BaseDStabNetwork<?, ?> network = (BaseDStabNetwork<?, ?>) converter.getDStabBus().getNetwork();
        sensedBus = network.getDStabBus("Bus" + data.remoteBus());
        if (sensedBus == null) {
            throw new IllegalStateException("REECA1 remote bus not found: " + data.remoteBus());
        }
    }

    private double sensedVoltage(double localVoltage) {
        return sensedBus == null ? localVoltage : sensedBus.getVoltageMag();
    }

    private static double remaining(double total, double priority) {
        return Math.sqrt(Math.max(0.0, total * total - priority * priority));
    }

    /** PSS/E/ANDES convention: non-positive Imax disables the circular limit. */
    private double currentLimit() {
        return data.imax() > EPS ? data.imax() : 1.0e8;
    }

    private static double nonzero(double value) {
        return Math.max(0.01, Math.abs(value));
    }

    private static double snapToInitial(double value, double initial) {
        return Math.abs(value - initial) <= EQUILIBRIUM_RESIDUAL ? initial : value;
    }

    public Reeca1Data getData() { return data; }
    @Override public Repca1Model getPlantController() { return plantController; }
    @Override public void setPlantController(Repca1Model controller) { plantController = controller; }
    public WindControlStack getWindControlStack() { return windControlStack; }
    public void setWindControlStack(WindControlStack stack) { windControlStack = stack; }
    public double getMeasuredVoltage() { return vMeasured; }
    public double getMeasuredActivePower() { return pMeasured; }
    public double getActivePowerFilter() { return pFilter; }
    public double getActivePowerOrder() { return pOrder; }
    public double getReactiveCurrentState() { return qCurrent; }
    public double getReactivePowerTarget() { return reactivePowerTarget; }
    public double getReactiveControlError() { return reactiveControlError; }
    public double getReactiveControlIntegral() { return qIntegral; }
    public double getReactiveControlPreLimitOutput() { return reactiveControlPreLimitOutput; }
    public double getReactiveControlOutput() { return qControlOutput; }
    public double getVoltageControlError() { return voltageControlError; }
    public double getVoltageControlIntegral() { return vIntegral; }
    public double getVoltageControlPreLimitOutput() { return voltageControlPreLimitOutput; }
    public double getVoltageControlOutput() { return voltageControlOutput; }
    public double getPreliminaryReactiveCurrentLimit() { return preliminaryIqLimit; }
    public double getReactiveCurrentInjection() { return reactiveCurrentInjection; }
    public double getActiveCurrentLimit() { return ipLimit; }
    public double getReactiveCurrentLimit() { return iqLimit; }
    public boolean isVoltageDip() { return previousDip; }
    @Override public double getIpcmd() { return ipcmd; }
    @Override public double getIqcmd() { return iqcmd; }

    @Override
    public Map<String, Double> getNamedStates() {
        Map<String, Double> named = new LinkedHashMap<>();
        named.put("Measured Voltage", vMeasured);
        named.put("Measured Active Power", pMeasured);
        named.put("Active Power Filter", pFilter);
        named.put("Active Power Order", pOrder);
        named.put("Reactive Current", qCurrent);
        named.put("Reactive Control Integral", qIntegral);
        named.put("Voltage Control Integral", vIntegral);
        named.put("Reactive Control Output", qControlOutput);
        named.put("Voltage Control Output", voltageControlOutput);
        named.put("Active Current Command", ipcmd);
        named.put("Reactive Current Command", iqcmd);
        return Collections.unmodifiableMap(named);
    }
}
