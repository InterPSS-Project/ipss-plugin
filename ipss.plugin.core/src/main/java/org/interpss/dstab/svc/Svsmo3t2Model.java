package org.interpss.dstab.svc;

import java.util.Arrays;
import java.util.Collections;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.control.util.IntegrationStateAccess;
import org.interpss.dstab.control.util.IntegrationStateMonitor;
import org.interpss.dstab.control.util.IntegrationStateMonitor.Stage;
import org.interpss.dstab.control.util.StaggeredEulerParticipant;

import com.interpss.core.aclf.facts.StaticVarCompensator;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.dstab.controller.cml.ICMLStateProvider;
import com.interpss.dstab.device.DynamicBusDeviceType;
import com.interpss.dstab.device.impl.DynamicBusDeviceImpl;

/**
 * Direct numerical SVSMO3T2 current controller for a named shunt FACTS device.
 * The solved static admittance stays in Y; the injection replaces its current
 * with the controlled current on the explicit STATCOM base.
 */
public final class Svsmo3t2Model extends DynamicBusDeviceImpl
        implements ICMLStateProvider, IntegrationStateAccess, StaggeredEulerParticipant {
    private static final String[] NAMES = {"Voltage measurement filter", "Main PI integrator",
            "STATCOM output lag", "Slow-reset PI integrator", "Short-term rating integral",
            "Transient-gain lead-lag filter"};
    private final Svsmo3t2Data data;
    private final StaticVarCompensator svc;
    private final BaseDStabBus<?, ?> remoteBus;
    private final Svsmo3t2MssBoundary switching;
    private final double[] x = new double[6], start = new double[6];
    private final double[] rate = new double[6], firstRate = new double[6];
    private final IntegrationStateMonitor monitor = new IntegrationStateMonitor(NAMES);
    private final Hashtable<String, Object> outputStates = new Hashtable<>();
    private final double sensorRatio, gainRatio;
    private final boolean nonlinearDroopActive;
    private final boolean thermalRatingActive;
    private double initialB, baseRatio, scheduledVoltage, auxiliarySignal;
    private double integrationTime;
    private double preparedVrefMin, preparedVrefMax;
    private double shortTimer, unblockTimer, overvoltageTimer, deadbandTimer;
    private boolean predictorPending, blocked, tripped, thermalLimited, deadbandLocked, shortRatingExhausted;
    private boolean initialized;

    public Svsmo3t2Model(BaseDStabBus<?, ?> bus, BaseDStabBus<?, ?> remoteBus,
            StaticVarCompensator svc, Svsmo3t2Data data) {
        switching = new Svsmo3t2MssBoundary((com.interpss.dstab.BaseDStabNetwork<?, ?>) bus.getNetwork(), data);
        this.data = data;
        this.svc = svc;
        this.remoteBus = remoteBus;
        sensorRatio = data.tb1() > 0 ? data.tc1() / data.tb1() : 0;
        gainRatio = data.tb2() > 0 ? data.tc2() / data.tb2() : 0;
        // Source initialization suppresses this loop when either slow-reset
        // gain is positive, even with its separate enable flag off. Keep the
        // entered ICON/CON data immutable and expose the prepared selection.
        nonlinearDroopActive = data.nonlinearDroopFlag() != 0 && data.kpr() <= 0 && data.kir() <= 0;
        thermalRatingActive = data.thermalLimit() > 0 && data.shortRatingTime() == 0;
        setDeviceType(DynamicBusDeviceType.DYNAMIC_LOAD);
        setName("SVSMO3T2");
        setId(svc.getId());
        setExtendedDeviceId("SVSMO3T2_" + svc.getId() + "@" + bus.getId());
        setDStabBus(bus);
        outputStates.put(DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID, getExtendedDeviceId());
    }

    @Override public boolean initStates(BaseDStabBus<?, ?> bus) {
        initialized = false;
        if (bus != getDStabBus() || remoteBus == null || !svc.isActive()
                || !(bus.getVoltageMag() > 0) || !(bus.getNetwork().getBaseMva() > 0)) return false;
        Arrays.fill(x, 0);
        initialB = svc.getBActual();
        baseRatio = data.statcomBaseMva() / bus.getNetwork().getBaseMva();
        x[0] = remoteBus.getVoltageMag();
        x[1] = x[2] = initialB * bus.getVoltageMag() / baseRatio;
        scheduledVoltage = x[0] + (data.deadband() > 0 ? 0 : droop(x[0]) * x[2]);
        preparedVrefMin = Math.min(data.vrefMin(), scheduledVoltage);
        preparedVrefMax = Math.max(data.vrefMax(), scheduledVoltage);
        shortTimer = unblockTimer = overvoltageTimer = deadbandTimer = 0;
        predictorPending = blocked = tripped = thermalLimited = shortRatingExhausted = false;
        deadbandLocked = data.deadband() > 0;
        integrationTime = 0;
        switching.initialize();
        monitor.reset();
        outputStates.put(DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID, getExtendedDeviceId());
        evaluateRates();
        observe(Stage.INITIALIZATION);
        initialized = finiteState();
        return initialized;
    }

    @Override public int staggeredStateCount() { return x.length; }

    @Override public void readStaggeredState(double[] values, int offset) {
        requireStaggeredBoundary();
        System.arraycopy(x, 0, values, offset, x.length);
    }

    /** Pure derivative evaluation: the enclosing solver owns state/history buffers. */
    @Override public void evaluateStaggered(double stepSec, double[] derivatives, int offset) {
        requireStaggeredBoundary();
        requireStaggeredStep(stepSec);
        if (tripped) Arrays.fill(rate, 0); else evaluateRates();
        observe(Stage.STAGGERED);
        System.arraycopy(rate, 0, derivatives, offset, rate.length);
    }

    /** Project one numerical trial without advancing timers or switching banks. */
    @Override public void applyStaggeredState(double[] values, int offset, double stepSec) {
        requireStaggeredBoundary();
        requireStaggeredStep(stepSec);
        java.util.Objects.checkFromIndexSize(offset, x.length, values.length);
        for (int i = 0; i < x.length; i++) if (!Double.isFinite(values[offset + i]))
            throw new IllegalArgumentException("Finite SVSMO3T2 integration coordinates required");
        if (tripped) return;
        System.arraycopy(values, offset, x, 0, x.length);
        projectAlgebraicStates();
        if (!finiteState()) throw new IllegalStateException("Nonfinite SVSMO3T2 projected state");
    }

    /** Commit continuous-step histories once; physical switching remains an endpoint callback. */
    @Override public void completeStaggeredStep(double stepSec) {
        completeStaggeredStep(stepSec, integrationTime + stepSec);
    }

    @Override public void completeStaggeredStep(double stepSec, double endpointTimeSec) {
        requireStaggeredBoundary();
        requireStaggeredStep(stepSec);
        if (!Double.isFinite(endpointTimeSec) || endpointTimeSec < integrationTime
                || stepSec > 0 && !(endpointTimeSec > integrationTime))
            throw new IllegalArgumentException("SVSMO3T2 requires a finite advancing endpoint time");
        if (tripped || stepSec == 0) return;
        updateHistory(stepSec);
        integrationTime = endpointTimeSec;
    }

    private void requireStaggeredBoundary() {
        if (!initialized || predictorPending)
            throw new IllegalStateException("Initialize SVSMO3T2 and finish any predictor before using shared state access");
    }

    private static void requireStaggeredStep(double stepSec) {
        if (!Double.isFinite(stepSec) || stepSec < 0)
            throw new IllegalArgumentException("Finite nonnegative SVSMO3T2 interval required");
    }

    @Override public boolean nextStep(double dt, DynamicSimuMethod method, int flag) {
        if (method != DynamicSimuMethod.MODIFIED_EULER || !Double.isFinite(dt) || dt < 0
                || (flag != 0 && flag != 1)) return false;
        if (tripped) return finiteState();
        // Event-side limiter projection is algebraic. No derivative integration,
        // timer advance or accepted-history commit occurs at zero duration.
        if (dt == 0) { projectAlgebraicStates(); return finiteState(); }
        if (flag == 0) {
            if (predictorPending) return false;
            evaluateRates();
            observe(Stage.PREDICTOR);
            System.arraycopy(x, 0, start, 0, x.length);
            System.arraycopy(rate, 0, firstRate, 0, x.length);
            for (int i = 0; i < x.length; i++) x[i] = start[i] + dt * firstRate[i];
            projectAlgebraicStates();
            predictorPending = true;
        } else {
            if (!predictorPending) return false;
            evaluateRates();
            observe(Stage.CORRECTOR);
            for (int i = 0; i < x.length; i++) x[i] = start[i] + .5 * dt * (firstRate[i] + rate[i]);
            projectAlgebraicStates();
            updateHistory(dt);
            predictorPending = false;
            if (finiteState()) integrationTime += dt;
        }
        return finiteState();
    }

    private void evaluateRates() {
        double sensed = remoteBus.getVoltageMag();
        double measured = measurement(sensed);
        double error = voltageError(measured);
        double gainOutput = gainOutput(error);
        double terminal = getDStabBus().getVoltageMag();
        double low = minimumCurrent(terminal), high = maximumCurrent(terminal);
        double proportional = clamp(data.kp() * gainOutput, low, high);
        double integralRate = data.ki() * gainOutput;
        rate[0] = data.tb1() > 0 ? (sensed - x[0]) / data.tb1() : 0;
        rate[1] = nonWindupRate(x[1], integralRate, low - proportional, high - proportional);
        double command = clamp(proportional + x[1], low, high);
        rate[2] = data.firingTime() > 0 ? (command - x[2]) / data.firingTime() : 0;
        double resetError = resetError();
        double resetProportional = clamp(data.kpr() * resetError, data.vrMin(), data.vrMax());
        rate[3] = data.slowResetFlag() == 0 ? 0 : nonWindupRate(x[3], data.kir() * resetError,
                data.vrMin() - resetProportional, data.vrMax() - resetProportional);
        double excess = Math.max(0, Math.abs(x[2]) - data.continuousCurrent());
        rate[4] = !thermalRatingActive ? 0
                : nonWindupRate(x[4], excess * excess - data.thermalResetRate(), 0, Double.POSITIVE_INFINITY);
        rate[5] = data.tb2() > 0 ? (error - x[5]) / data.tb2() : 0;
        if (deadbandLocked) rate[1] = rate[2] = rate[5] = 0;
    }

    private void projectAlgebraicStates() {
        double sensed = remoteBus.getVoltageMag();
        double measured = measurement(sensed), error = voltageError(measured);
        if (data.tb2() == 0) x[5] = error;
        double terminal = getDStabBus().getVoltageMag();
        double low = minimumCurrent(terminal), high = maximumCurrent(terminal);
        double proportional = clamp(data.kp() * gainOutput(error), low, high);
        x[1] = clamp(x[1], low - proportional, high - proportional);
        if (data.firingTime() == 0 && !deadbandLocked) x[2] = clamp(x[1] + proportional, low, high);
        double resetP = clamp(data.kpr() * resetError(), data.vrMin(), data.vrMax());
        if (data.slowResetFlag() != 0) x[3] = clamp(x[3], data.vrMin() - resetP, data.vrMax() - resetP);
        x[4] = Math.max(0, x[4]);
    }

    private void updateHistory(double dt) {
        double voltage = getDStabBus().getVoltageMag();
        boolean outside = voltage < data.uv2() || voltage > data.ov2();
        if (outside && !blocked) { blocked = true; unblockTimer = 0; }
        if (blocked) {
            unblockTimer += dt;
            if (!outside && unblockTimer >= data.unblockTime()) { blocked = false; unblockTimer = 0; }
        }
        overvoltageTimer = voltage > data.tripVoltage() ? overvoltageTimer + dt : 0;
        if (voltage > data.tripVoltage() && overvoltageTimer >= data.tripTime()) tripped = true;
        if (!thermalRatingActive && !shortRatingExhausted) {
            shortTimer = Math.abs(x[2]) > data.continuousCurrent() ? shortTimer + dt : 0;
            if (Math.abs(x[2]) > data.continuousCurrent() && shortTimer >= data.shortRatingTime())
                shortRatingExhausted = true;
        }
        if (thermalRatingActive) {
            if (x[4] >= data.thermalLimit()) thermalLimited = true;
            else if (x[4] <= data.thermalLimit() - data.thermalHysteresis()) thermalLimited = false;
        }
        if (data.deadband() > 0) {
            double deviation = Math.abs(voltageReference() - measurement(remoteBus.getVoltageMag()));
            if (deadbandLocked && deviation >= data.deadband()) {
                deadbandLocked = false;
                deadbandTimer = 0;
            } else if (!deadbandLocked && deviation < data.deadband() / data.deadbandRatio()) {
                deadbandTimer += dt;
                if (deadbandTimer >= data.deadbandTime()) deadbandLocked = true;
            } else deadbandTimer = 0;
        }
    }

    private double rating() {
        boolean continuous = thermalRatingActive ? thermalLimited : shortRatingExhausted;
        return data.continuousCurrent() * (continuous ? 1 : data.shortCurrentMultiplier());
    }

    private double maximumCurrent(double voltage) {
        if (tripped || effectiveBlocked(voltage)) return 0;
        double max = rating();
        if (voltage < data.uv1() && data.uv1() > data.uv2())
            return max * (voltage - data.uv2()) / (data.uv1() - data.uv2());
        if (voltage > data.ov1() && data.ov2() > data.ov1())
            return max * (1 - 2 * (voltage - data.ov1()) / (data.ov2() - data.ov1()));
        return max;
    }

    private double minimumCurrent(double voltage) {
        if (tripped || effectiveBlocked(voltage)) return 0;
        double max = rating();
        return voltage < data.uv1() && data.uv1() > data.uv2()
                ? -max * (voltage - data.uv2()) / (data.uv1() - data.uv2()) : -max;
    }

    private double droop(double measured) {
        if (!nonlinearDroopActive) return data.xc0();
        return measured >= data.vUpper() ? data.xc1() : measured <= data.vLower() ? data.xc3() : data.xc2();
    }
    private boolean effectiveBlocked(double terminalVoltage) {
        return terminalVoltage < data.uv2() || terminalVoltage > data.ov2()
                || blocked && unblockTimer < data.unblockTime();
    }
    private double measurement(double sensed) {
        return data.tb1() == 0 ? sensed : sensorRatio * sensed + (1 - sensorRatio) * x[0];
    }
    private double gainOutput(double error) {
        return data.tb2() == 0 ? error : gainRatio * error + (1 - gainRatio) * x[5];
    }
    private double resetError() {
        double current = getCurrent();
        return -Math.copySign(Math.max(0, Math.abs(current) - data.currentDeadband()), current);
    }
    private double voltageReference() {
        // Initialization may require a reference outside the entered range.
        return clamp(scheduledVoltage + slowResetOutput(), preparedVrefMin, preparedVrefMax);
    }
    private double slowResetOutput() {
        return data.slowResetFlag() == 0 ? 0 : clamp(x[3]
                + clamp(data.kpr() * resetError(), data.vrMin(), data.vrMax()), data.vrMin(), data.vrMax());
    }
    private double voltageError(double measured) {
        return clamp(voltageReference() + auxiliarySignal - measured - droop(measured) * getCurrent(),
                data.veMin(), data.veMax());
    }
    private static double nonWindupRate(double state, double rate, double low, double high) {
        return state <= low && rate < 0 || state >= high && rate > 0 ? 0 : rate;
    }
    private static double clamp(double value, double low, double high) { return Math.max(low, Math.min(high, value)); }
    private boolean finiteState() {
        for (double value : x) if (!Double.isFinite(value)) return false;
        return true;
    }
    private void observe(Stage stage) {
        monitor.begin(stage);
        for (int i = 0; i < x.length; i++) monitor.record(i, x[i], rate[i]);
    }
    public Svsmo3t2Data getData() { return data; }
    public double getIntegrationTime() { return integrationTime; }
    public boolean isNonlinearDroopActive() { return nonlinearDroopActive; }
    public boolean isThermalRatingActive() { return thermalRatingActive; }
    public StaticVarCompensator getStaticDevice() { return svc; }
    public BaseDStabBus<?, ?> getRemoteBus() { return remoteBus; }
    public double getCurrent() { return tripped || effectiveBlocked(getDStabBus().getVoltageMag()) ? 0 : x[2]; }
    public double getMvarOutput() { return getCurrent() * getDStabBus().getVoltageMag() * data.statcomBaseMva(); }
    public double getVoltageReference() { return voltageReference(); }
    public double getScheduledVoltage() { return scheduledVoltage; }
    public void setScheduledVoltage(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite scheduled voltage");
        scheduledVoltage = value;
    }
    public void setAuxiliarySignal(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite auxiliary signal");
        auxiliarySignal = value;
    }
    public boolean isBlocked() { return blocked; }
    public boolean isTripped() { return tripped; }
    @Override public Object getOutputObject() {
        Complex voltage = getDStabBus().getVoltage();
        double magnitude = voltage.abs();
        // j*B*V is the current already represented by the solved static device.
        double correction = initialB - (magnitude > 0 ? getCurrent() * baseRatio / magnitude : 0);
        return new Complex(-voltage.getImaginary() * correction, voltage.getReal() * correction);
    }
    @Override public boolean updateAttributes(boolean netChange) { return finiteState(); }
    /** Physical actions run only after the solver has accepted the full endpoint. */
    @Override public boolean afterStep(double dt) {
        if (!Double.isFinite(dt) || dt < 0 || predictorPending) return false;
        if (dt == 0) return finiteState();
        return acceptSwitchingEndpoint();
    }
    /** Call after initial network compensation is established, never during its construction. */
    public boolean acceptSwitchingEndpoint() {
        if (predictorPending) return false;
        if (!tripped) switching.advance(integrationTime, getCurrent());
        return finiteState();
    }
    public Svsmo3t2MssController getMssController() { return switching.controller(); }
    public Map<String, Double> getSwitchingNamedStates() { return switching.getNamedStates(); }
    @Override public Map<String, Double> getNamedStates() {
        var result = new LinkedHashMap<String, Double>();
        // The sensor exports its residual lead-lag coordinate. The gain block
        // exports its low-pass storage; these are distinct published realizations.
        for (int i = 0; i < x.length; i++) result.put(NAMES[i],
                i == 0 && data.tb1() > 0 ? (1 - sensorRatio) * x[i] : x[i]);
        return Collections.unmodifiableMap(result);
    }
    @Override public Map<String, Double> getIntegrationStates() {
        var result = new LinkedHashMap<String, Double>();
        for (int i = 0; i < x.length; i++) result.put(NAMES[i], x[i]);
        return Collections.unmodifiableMap(result);
    }
    @Override public IntegrationStateMonitor.Snapshot getStateDerivativeSnapshot() { return monitor.snapshot(); }
    @Override public Hashtable<String, Object> getStates(Object ref) {
        for (int i = 0; i < x.length; i++) outputStates.put(NAMES[i],
                i == 0 && data.tb1() > 0 ? (1 - sensorRatio) * x[i] : x[i]);
        return outputStates;
    }
}
