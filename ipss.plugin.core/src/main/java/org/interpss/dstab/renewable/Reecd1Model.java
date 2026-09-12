package org.interpss.dstab.renewable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.interpss.dstab.controller.cml.ICMLStateProvider;

/** Public WECC REEC_D seven-state renewable electrical controller. */
public final class Reecd1Model
        implements RenewableElectricalController, ICMLStateProvider {
    private static final double EPS = 1.0e-9;
    private static final double MAX_CONTROL_STEP = 1.0 / 960.0;

    private final Reecd1Data data;
    private Repca1Model plantController;
    private double integrationStep;
    private double minimumTimeConstantMultiplier = 1.0;
    private double effectiveTrv;
    private double effectiveTp;
    private double effectiveTiq;
    private double effectiveTpord;
    private double effectiveTr1;
    private double effectivePmax;
    private double effectivePmin;
    private double effectiveQmax;
    private double effectiveQmin;
    private double effectiveVmax;
    private double effectiveVmin;

    private double initialP;
    private double reactiveReference;
    private double powerFactorRatio;
    private double dipReference;
    private double generatorSpeed = 1.0;
    private double auxiliaryPower;

    // Published state order: Vmeas, Pmeas, PIQ, PIV, Q_V, Pord, Vcomp.
    private double measuredVoltage;
    private double measuredPower;
    private double reactiveIntegral;
    private double voltageIntegral;
    private double reactiveLag;
    private double powerOrder;
    private double compensatedVoltage;

    private boolean previousDip;
    private boolean blocked;
    private double unblockTimer;
    private double activeLimitHoldTimer;
    private double reactiveHoldTimer;
    private double heldActiveCommand;
    private double heldActiveMaximum;
    private double heldReactiveDesired;
    private double activeCurrentMaximum;
    private double activeCurrentMinimum;
    private double reactiveCurrentMaximum;
    private double reactiveCurrentMinimum;
    private double reactiveCurrentInjection;
    private double ipcmd;
    private double iqcmd;

    public Reecd1Model(Reecd1Data data) {
        this.data = data;
    }

    @Override
    public void configureIntegrationStep(double timeStepSec) {
        configureIntegrationStep(timeStepSec, 1.0);
    }

    public void configureIntegrationStep(double timeStepSec, double multiplier) {
        if (timeStepSec < 0.0 || multiplier <= 0.0
                || !Double.isFinite(timeStepSec) || !Double.isFinite(multiplier)) {
            throw new IllegalArgumentException("REEC_D integration settings are invalid");
        }
        integrationStep = timeStepSec;
        minimumTimeConstantMultiplier = multiplier;
        loadEffectiveTimes();
    }

    @Override
    public void initialize(double p, double q, double v) {
        loadEffectiveTimes();
        initialP = p;
        reactiveReference = q;
        powerFactorRatio = Math.abs(p) > EPS ? q / p : 0.0;
        measuredVoltage = nonzero(v);
        measuredPower = data.powerFactorFlag() == 1 ? p : 0.0;
        compensatedVoltage = compensationTarget(p, q, measuredVoltage);
        dipReference = initialDipReference(measuredVoltage);
        effectivePmax = Math.max(data.powerMaximum(), p);
        effectivePmin = Math.min(data.powerMinimum(), p);
        effectiveQmax = Math.max(data.externalReactiveMaximum(), q);
        effectiveQmin = Math.min(data.externalReactiveMinimum(), q);
        effectiveVmax = Math.max(data.voltageControlMaximum(), compensatedVoltage);
        effectiveVmin = Math.min(data.voltageControlMinimum(), compensatedVoltage);
        reactiveIntegral = data.reactiveControlFlag() == 1
                && data.voltageFlag() == 1 ? compensatedVoltage : 0.0;
        voltageIntegral = q / measuredVoltage;
        reactiveLag = data.reactiveControlFlag() == 0 ? q / measuredVoltage : 0.0;
        powerOrder = p;
        previousDip = voltageDipActive();
        blocked = outsideBlockingBand();
        unblockTimer = activeLimitHoldTimer = reactiveHoldTimer = 0.0;
        heldReactiveDesired = reactiveLag;
        updateCommands(q);
        heldActiveCommand = ipcmd;
        heldActiveMaximum = activeCurrentMaximum;
        if (plantController != null) {
            plantController.initialize(p, q, measuredVoltage);
        }
    }

    @Override
    public void step(double dt, double p, double q, double v, double frequency) {
        if (dt <= 0.0 || !Double.isFinite(dt)) return;
        if (plantController != null) {
            plantController.step(dt, p, q, nonzero(v), frequency);
        }
        int substeps = Math.max(1, (int) Math.ceil(dt / MAX_CONTROL_STEP));
        double h = dt / substeps;
        for (int index = 0; index < substeps; index++) {
            stepControls(h, p, q, v);
        }
    }

    private void stepControls(double dt, double p, double q, double v) {
        measuredVoltage = lag(measuredVoltage, nonzero(v), effectiveTrv, dt);
        if (data.powerFactorFlag() == 1) {
            measuredPower = lag(measuredPower, p, effectiveTp, dt);
        }
        compensatedVoltage = lag(compensatedVoltage,
                compensationTarget(p, q, nonzero(v)), effectiveTr1, dt);

        boolean dip = isVoltageDip();
        updateTimers(dt, dip);
        updateBlocking(dt);

        if (!dip) {
            double plantPref = plantController == null ? 0.0 : plantController.getPref();
            double selected = initialP + plantPref;
            if (data.powerFlag() == 1) selected *= generatorSpeed;
            double target = clamp(selected, effectivePmin, effectivePmax);
            double requestedRate = effectiveTpord <= EPS
                    ? (target - powerOrder) / dt
                    : (target - powerOrder) / effectiveTpord;
            powerOrder = clamp(powerOrder + clamp(requestedRate,
                    data.powerRampMinimum(), data.powerRampMaximum()) * dt,
                    effectivePmin, effectivePmax);
        }

        double plantQ = plantController == null ? 0.0 : plantController.getQref();
        double selectedQ = data.powerFactorFlag() == 1
                ? measuredPower * powerFactorRatio : reactiveReference + plantQ;
        double qTarget = clamp(selectedQ, effectiveQmin, effectiveQmax);
        double voltage = nonzero(measuredVoltage);
        double rawIp = powerOrder / voltage + auxiliaryPower;
        double preliminaryIqMaximum = preliminaryReactiveMaximum(rawIp);

        double baseReactive;
        if (data.reactiveControlFlag() == 0) {
            if (!dip) {
                reactiveLag = lag(reactiveLag, qTarget / voltage,
                        effectiveTiq, dt);
            }
            baseReactive = reactiveLag;
        } else {
            double voltageReference;
            if (data.voltageFlag() == 1) {
                double qError = qTarget - q;
                reactiveIntegral = Repca1Model.integrateWithAntiWindup(
                        reactiveIntegral, data.reactiveIntegralGain(), qError,
                        dt, data.reactiveProportionalGain(),
                        effectiveVmin, effectiveVmax, dip);
                voltageReference = clamp(data.reactiveProportionalGain() * qError
                                + reactiveIntegral,
                        effectiveVmin, effectiveVmax);
            } else {
                voltageReference = clamp(data.innerVoltageReference() + qTarget,
                        effectiveVmin, effectiveVmax);
            }
            double voltageError = voltageReference - compensatedVoltage;
            double oldReactiveIntegral = reactiveIntegral;
            double candidateVoltageIntegral = Repca1Model.integrateWithAntiWindup(voltageIntegral,
                    data.voltageIntegralGain(), voltageError, dt,
                    data.voltageProportionalGain(), -preliminaryIqMaximum,
                    preliminaryIqMaximum, dip);
            double voltagePreLimit = data.voltageProportionalGain() * voltageError
                    + candidateVoltageIntegral;
            double upstreamRate = data.reactiveIntegralGain() * (qTarget - q);
            double downstreamRate = data.voltageIntegralGain() * voltageError;
            boolean linkedUpperFreeze = voltagePreLimit >= preliminaryIqMaximum - EPS
                    && downstreamRate > 0.0 && upstreamRate > 0.0;
            boolean linkedLowerFreeze = voltagePreLimit <= -preliminaryIqMaximum + EPS
                    && downstreamRate < 0.0 && upstreamRate < 0.0;
            if ((linkedUpperFreeze || linkedLowerFreeze)
                    && data.reactiveControlFlag() == 1 && data.voltageFlag() == 1) {
                reactiveIntegral = oldReactiveIntegral;
            }
            voltageIntegral = candidateVoltageIntegral;
            double desired = clamp(data.voltageProportionalGain() * voltageError
                            + voltageIntegral,
                    -preliminaryIqMaximum, preliminaryIqMaximum);
            baseReactive = desired;
        }

        double injection = reactiveInjection();
        reactiveCurrentInjection = injection;
        double reactiveDesired = baseReactive + injection;
        if (dip) heldReactiveDesired = reactiveDesired;
        if (!dip && reactiveHoldTimer > 0.0) {
            reactiveDesired = data.reactiveHoldTime() > 0.0
                    ? heldReactiveDesired : data.frozenReactiveCurrent();
        }
        applyCurrentLimits(rawIp, reactiveDesired, dip);
        if (dip) {
            heldActiveCommand = ipcmd;
            heldActiveMaximum = activeCurrentMaximum;
        }
        previousDip = dip;
    }

    private void updateCommands(double q) {
        double rawIp = powerOrder / nonzero(measuredVoltage) + auxiliaryPower;
        double reactive = data.reactiveControlFlag() == 0
                ? reactiveLag : q / nonzero(measuredVoltage);
        applyCurrentLimits(rawIp, reactive + reactiveInjection(),
                voltageDipActive());
    }

    private void applyCurrentLimits(double rawIp, double rawIq, boolean dip) {
        Limits limits = currentLimits(rawIp, rawIq);
        if (blocked || unblockTimer > 0.0) limits = new Limits(0.0, 0.0, 0.0, 0.0);
        if (!dip && activeLimitHoldTimer > 0.0 && !isBlocked()) {
            limits = new Limits(-data.chargingCurrentFactor() * heldActiveMaximum,
                    heldActiveMaximum, limits.iqMinimum, limits.iqMaximum);
        }
        double requestedIp = activeLimitHoldTimer > 0.0
                ? heldActiveCommand : rawIp;
        activeCurrentMinimum = limits.ipMinimum;
        activeCurrentMaximum = limits.ipMaximum;
        reactiveCurrentMinimum = limits.iqMinimum;
        reactiveCurrentMaximum = limits.iqMaximum;
        ipcmd = clamp(requestedIp, limits.ipMinimum, limits.ipMaximum);
        double iqLimited = clamp(rawIq, limits.iqMinimum, limits.iqMaximum);
        iqcmd = -iqLimited;
    }

    private Limits currentLimits(double rawIp, double rawIq) {
        double iqMaximum = tableLimit(measuredVoltage,
                data.reactiveVoltagePoints(), data.reactiveCurrentPoints());
        double ipMaximum = tableLimit(measuredVoltage,
                data.activeVoltagePoints(), data.activeCurrentPoints());
        if (!Double.isFinite(iqMaximum)) iqMaximum = 1.0e10;
        if (!Double.isFinite(ipMaximum)) ipMaximum = 1.0e10;

        double imax = currentLimit();
        double iqMinimum;
        double ipMinimum;
        if (data.pqPriorityFlag() == 0) {
            iqMaximum = Math.min(imax, iqMaximum);
            iqMinimum = iqMaximum < 0.0 ? iqMaximum : -iqMaximum;
            double limitedIq = clamp(rawIq, iqMinimum, iqMaximum);
            double circle = remaining(imax, limitedIq);
            ipMaximum = Math.min(ipMaximum, circle);
            ipMinimum = -data.chargingCurrentFactor() * ipMaximum;
        } else {
            ipMaximum = Math.min(imax, ipMaximum);
            ipMinimum = -data.chargingCurrentFactor() * ipMaximum;
            double requestedIp = activeLimitHoldTimer > 0.0
                    ? heldActiveCommand : rawIp;
            double limitedIp = clamp(requestedIp, ipMinimum, ipMaximum);
            iqMaximum = Math.min(iqMaximum, remaining(imax, limitedIp));
            iqMinimum = iqMaximum < 0.0 ? iqMaximum : -iqMaximum;
        }
        return new Limits(ipMinimum, ipMaximum, iqMinimum, iqMaximum);
    }

    private double preliminaryReactiveMaximum(double rawIp) {
        Limits limits = currentLimits(rawIp, 0.0);
        if (blocked || unblockTimer > 0.0) return 0.0;
        return Math.max(Math.abs(limits.iqMinimum), Math.abs(limits.iqMaximum));
    }

    private double reactiveInjection() {
        double error = Repca1Model.deadband(dipReference - measuredVoltage,
                data.deadbandLow(), data.deadbandHigh());
        return clamp(data.reactiveInjectionGain() * error,
                data.reactiveInjectionMinimum(), data.reactiveInjectionMaximum());
    }

    private void updateTimers(double dt, boolean dip) {
        if (previousDip && !dip) {
            activeLimitHoldTimer = data.activeLimitHoldTime();
            reactiveHoldTimer = Math.abs(data.reactiveHoldTime());
        } else if (!dip) {
            activeLimitHoldTimer = Math.max(0.0, activeLimitHoldTimer - dt);
            reactiveHoldTimer = Math.max(0.0, reactiveHoldTimer - dt);
        }
    }

    private void updateBlocking(double dt) {
        boolean outside = outsideBlockingBand();
        if (outside) {
            blocked = true;
            unblockTimer = data.unblockDelay();
        } else if (blocked) {
            blocked = false;
            unblockTimer = data.unblockDelay();
        } else if (unblockTimer > 0.0) {
            unblockTimer = Math.max(0.0, unblockTimer - dt);
        }
    }

    private boolean outsideBlockingBand() {
        return measuredVoltage <= data.blockingVoltageLow()
                || measuredVoltage >= data.blockingVoltageHigh();
    }

    private boolean voltageDipActive() {
        return measuredVoltage < data.voltageDip()
                || measuredVoltage > data.voltageUp();
    }

    private double compensationTarget(double p, double q, double v) {
        if (data.voltageCompensationFlag() == 0) {
            return v + data.reactiveDroopGain() * q;
        }
        double realDrop = (data.compensationResistance() * p
                + data.compensationReactance() * q) / nonzero(v);
        double imaginaryDrop = (data.compensationReactance() * p
                - data.compensationResistance() * q) / nonzero(v);
        return Math.hypot(v - realDrop, imaginaryDrop);
    }

    static double tableLimit(double voltage, double[] x, double[] y) {
        int count = Reecd1Data.tablePointCount(x, y);
        if (count == 0) return Double.POSITIVE_INFINITY;
        if (voltage <= x[0]) return y[0];
        for (int index = 1; index < count; index++) {
            if (voltage <= x[index]) {
                return y[index - 1] + (voltage - x[index - 1])
                        * (y[index] - y[index - 1]) / (x[index] - x[index - 1]);
            }
        }
        return y[count - 1];
    }

    private double initialDipReference(double voltage) {
        double configured = data.voltageReference();
        if (configured <= 0.0) return voltage;
        double error = configured - voltage;
        return data.reactiveInjectionGain() > 0.0
                && !(error > data.deadbandLow() && error < data.deadbandHigh())
                ? voltage : configured;
    }

    private void loadEffectiveTimes() {
        effectiveTrv = correctedBypass(data.voltageMeasurementTime());
        effectiveTp = correctedBypass(data.powerMeasurementTime());
        effectiveTiq = correctedBypass(data.reactiveLagTime());
        effectiveTpord = correctedBypass(data.powerOrderTime());
        effectiveTr1 = correctedBypass(data.compensationFilterTime());
    }

    private double correctedBypass(double value) {
        double minimum = minimumTimeConstantMultiplier * integrationStep;
        if (minimum <= 0.0) return value;
        if (value > 0.0 && value < 0.5 * minimum) return 0.0;
        if (value > 0.5 * minimum && value < minimum) return minimum;
        return value;
    }

    private double currentLimit() {
        return data.currentMaximum() > EPS ? data.currentMaximum() : 1.0e8;
    }

    private static double lag(double state, double input, double time, double dt) {
        return time <= EPS ? input : state + dt * (input - state) / time;
    }

    private static double remaining(double total, double priority) {
        return Math.sqrt(Math.max(0.0, total * total - priority * priority));
    }

    private static double nonzero(double value) {
        return Math.max(0.01, Math.abs(value));
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public Reecd1Data getData() { return data; }
    @Override public Repca1Model getPlantController() { return plantController; }
    @Override public void setPlantController(Repca1Model value) { plantController = value; }
    public void setGeneratorSpeed(double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException("REEC_D generator speed must be positive");
        }
        generatorSpeed = value;
    }
    public void setAuxiliaryPower(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("REEC_D auxiliary power must be finite");
        }
        auxiliaryPower = value;
    }
    public boolean isBlocked() { return blocked || unblockTimer > 0.0; }
    public boolean isVoltageDip() { return voltageDipActive(); }
    public double getUnblockTimer() { return unblockTimer; }
    public double getActiveLimitHoldTimer() { return activeLimitHoldTimer; }
    public double getReactiveHoldTimer() { return reactiveHoldTimer; }
    public double getActiveCurrentMaximum() { return activeCurrentMaximum; }
    public double getActiveCurrentMinimum() { return activeCurrentMinimum; }
    public double getReactiveCurrentMaximum() { return reactiveCurrentMaximum; }
    public double getReactiveCurrentMinimum() { return reactiveCurrentMinimum; }
    public double getReactiveCurrentInjection() { return reactiveCurrentInjection; }
    @Override public double getIpcmd() { return ipcmd; }
    @Override public double getIqcmd() { return iqcmd; }

    @Override
    public Map<String, Double> getNamedStates() {
        Map<String, Double> states = new LinkedHashMap<>();
        states.put("Vmeas", measuredVoltage);
        states.put("Pmeas", measuredPower);
        states.put("PIQ", reactiveIntegral);
        states.put("PIV", voltageIntegral);
        states.put("Q_V", reactiveLag);
        states.put("Pord", powerOrder);
        states.put("Vcomp", compensatedVoltage);
        return Collections.unmodifiableMap(states);
    }

    private record Limits(double ipMinimum, double ipMaximum,
            double iqMinimum, double iqMaximum) { }
}
