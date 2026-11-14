package org.interpss.dstab.svc;

import java.util.Arrays;
import java.util.Collections;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import com.interpss.dstab.controller.runtime.IntegrationStateAccess;
import com.interpss.dstab.controller.runtime.IntegrationStateMonitor;
import com.interpss.dstab.controller.runtime.IntegrationStateMonitor.Stage;
import com.interpss.dstab.controller.runtime.StaggeredEulerParticipant;

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
        implements ICMLStateProvider, IntegrationStateAccess, StaggeredEulerParticipant,
        com.interpss.dstab.controller.runtime.PhysicalStepTrial {
    private static final String[] NAMES = {"Voltage measurement filter", "Main PI integrator",
            "STATCOM output lag", "Slow-reset PI integrator", "Short-term rating integral",
            "Transient-gain lead-lag filter"};
    private final Svsmo3t2Data data;
    private final StaticVarCompensator svc;
    private final BaseDStabBus<?, ?> remoteBus;
    private final Svsmo3t2MssBoundary switching;
    private final double[] x = new double[6], start = new double[6], trialBackup = new double[6];
    private final double[] rate = new double[6], firstRate = new double[6];
    private final IntegrationStateMonitor monitor = new IntegrationStateMonitor(NAMES);
    private final Hashtable<String, Object> outputStates = new Hashtable<>();
    private final double sensorRatio, gainRatio;
    private final boolean nonlinearDroopActive;
    private final boolean thermalRatingActive;
    private double initialB, baseRatio, scheduledVoltage, auxiliarySignal;
    private double integrationTime, predictorStep;
    private double overvoltageRecoveryStart = -1;
    /** Last accepted physical output; trial states cannot start the rating timer early. */
    private double acceptedCurrent;
    private double publishedCurrent, trialPhysicalCurrent;
    private boolean physicalTrialOpen, physicalTrialPublished, automaticPhysicalTrial;
    private final double[] physicalTrialCoordinates, physicalTrialHistory;
    private int physicalTrialFlags;
    private final IntegrationStateMonitor physicalTrialMonitor;

    private double preparedVrefMin, preparedVrefMax;
    private double shortTimer, unblockTimer, overvoltageTimer, deadbandTimer;
    private boolean predictorPending, blocked, tripped, thermalLimited, deadbandLocked, shortRatingExhausted;
    private boolean initialized, networkDetached, overvoltageRecovery;

    public Svsmo3t2Model(BaseDStabBus<?, ?> bus, BaseDStabBus<?, ?> remoteBus,
            StaticVarCompensator svc, Svsmo3t2Data data) {
        switching = new Svsmo3t2MssBoundary((com.interpss.dstab.BaseDStabNetwork<?, ?>) bus.getNetwork(), data);
        this.data = data;
        physicalTrialCoordinates=data.firingTime()==0?new double[30]:null;
        physicalTrialHistory=data.firingTime()==0?new double[9]:null;
        physicalTrialMonitor=data.firingTime()==0?new IntegrationStateMonitor(NAMES):null;
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
        acceptedCurrent = x[2];
        publishedCurrent=x[2];physicalTrialOpen=physicalTrialPublished=automaticPhysicalTrial=false;
        scheduledVoltage = x[0] + (data.deadband() > 0 ? 0 : droop(x[0]) * x[2]);
        preparedVrefMin = Math.min(data.vrefMin(), scheduledVoltage);
        preparedVrefMax = Math.max(data.vrefMax(), scheduledVoltage);
        shortTimer = unblockTimer = overvoltageTimer = deadbandTimer = 0;
        predictorPending = blocked = tripped = thermalLimited = shortRatingExhausted = false;
        networkDetached = overvoltageRecovery = false;
        deadbandLocked = data.deadband() > 0;
        integrationTime = 0;
        overvoltageRecoveryStart = -1;
        predictorStep = 0;
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
        if (networkDetached) Arrays.fill(rate, 0); else evaluateRates();
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
        if (networkDetached) return;
        System.arraycopy(x, 0, trialBackup, 0, x.length);
        System.arraycopy(values, offset, x, 0, x.length);
        projectAlgebraicStates();
        if (!finiteState()) {
            System.arraycopy(trialBackup, 0, x, 0, x.length);
            throw new IllegalStateException("Nonfinite SVSMO3T2 projected state");
        }
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
        if (networkDetached || stepSec == 0) return;
        updateHistory(stepSec, endpointTimeSec);
        integrationTime = endpointTimeSec;
    }

    @Override public boolean requiresPhysicalStepTrial(){return data.firingTime()==0;}
    @Override public void beginPhysicalStepTrial(double time,double dt) {
        if(!requiresPhysicalStepTrial())return;
        if(!initialized||predictorPending||physicalTrialOpen||!Double.isFinite(time)||!Double.isFinite(dt)||dt<=0)
            throw new IllegalStateException("Initialized, idle positive physical step required");
        System.arraycopy(x,0,physicalTrialCoordinates,0,6);
        System.arraycopy(start,0,physicalTrialCoordinates,6,6);
        System.arraycopy(rate,0,physicalTrialCoordinates,12,6);
        System.arraycopy(firstRate,0,physicalTrialCoordinates,18,6);
        System.arraycopy(trialBackup,0,physicalTrialCoordinates,24,6);
        physicalTrialHistory[0]=integrationTime;physicalTrialHistory[1]=predictorStep;
        physicalTrialHistory[2]=overvoltageRecoveryStart;physicalTrialHistory[3]=acceptedCurrent;
        physicalTrialHistory[4]=shortTimer;physicalTrialHistory[5]=unblockTimer;
        physicalTrialHistory[6]=overvoltageTimer;physicalTrialHistory[7]=deadbandTimer;
        physicalTrialHistory[8]=publishedCurrent;
        physicalTrialFlags=(predictorPending?1:0)|(blocked?2:0)|(tripped?4:0)|(thermalLimited?8:0)
                |(deadbandLocked?16:0)|(shortRatingExhausted?32:0)
                |(networkDetached?128:0)|(overvoltageRecovery?256:0);
        monitor.copyObservationTo(physicalTrialMonitor);
        trialPhysicalCurrent=x[2];physicalTrialOpen=true;physicalTrialPublished=false;
    }
    @Override public void publishPhysicalStepTrial(){
        if(!requiresPhysicalStepTrial())return;
        if(!physicalTrialOpen)throw new IllegalStateException("Begin physical-output trial before publication");
        physicalTrialPublished=true;
    }
    @Override public void validatePhysicalStepAcceptance(){
        if(physicalTrialOpen&&(!physicalTrialPublished||predictorPending))throw new IllegalStateException("Complete and publish the physical trial before acceptance");
    }
    @Override public void acceptPhysicalStepTrial(){
        validatePhysicalStepAcceptance();
        if(!physicalTrialOpen)return;
        publishedCurrent=trialPhysicalCurrent;acceptedCurrent=getCurrent();
        physicalTrialOpen=physicalTrialPublished=automaticPhysicalTrial=false;
    }
    @Override public void discardPhysicalStepTrial(){
        if(!physicalTrialOpen)return;
        System.arraycopy(physicalTrialCoordinates,0,x,0,6);
        System.arraycopy(physicalTrialCoordinates,6,start,0,6);
        System.arraycopy(physicalTrialCoordinates,12,rate,0,6);
        System.arraycopy(physicalTrialCoordinates,18,firstRate,0,6);
        System.arraycopy(physicalTrialCoordinates,24,trialBackup,0,6);
        integrationTime=physicalTrialHistory[0];predictorStep=physicalTrialHistory[1];
        overvoltageRecoveryStart=physicalTrialHistory[2];acceptedCurrent=physicalTrialHistory[3];
        shortTimer=physicalTrialHistory[4];unblockTimer=physicalTrialHistory[5];
        overvoltageTimer=physicalTrialHistory[6];deadbandTimer=physicalTrialHistory[7];publishedCurrent=physicalTrialHistory[8];
        predictorPending=(physicalTrialFlags&1)!=0;blocked=(physicalTrialFlags&2)!=0;tripped=(physicalTrialFlags&4)!=0;
        thermalLimited=(physicalTrialFlags&8)!=0;deadbandLocked=(physicalTrialFlags&16)!=0;shortRatingExhausted=(physicalTrialFlags&32)!=0;
        networkDetached=(physicalTrialFlags&128)!=0;overvoltageRecovery=(physicalTrialFlags&256)!=0;
        physicalTrialMonitor.copyObservationTo(monitor);physicalTrialOpen=physicalTrialPublished=automaticPhysicalTrial=false;
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
        if (!initialized || method != DynamicSimuMethod.MODIFIED_EULER || !Double.isFinite(dt) || dt < 0
                || (flag != 0 && flag != 1)) return false;
        if (predictorPending && (dt == 0 || flag == 0 || dt != predictorStep)) return false;
        if (networkDetached) return finiteState();
        if(dt>0 && flag==0 && requiresPhysicalStepTrial() && !physicalTrialOpen){
            beginPhysicalStepTrial(integrationTime,dt);automaticPhysicalTrial=true;
        }
        System.arraycopy(x, 0, trialBackup, 0, x.length);
        // Event-side limiter projection is algebraic. No derivative integration,
        // timer advance or accepted-history commit occurs at zero duration.
        if (dt == 0) { projectAlgebraicStates(); return acceptFiniteTrial(); }
        if (flag == 0) {
            if (predictorPending) return false;
            evaluateRates();
            observe(Stage.PREDICTOR);
            System.arraycopy(x, 0, start, 0, x.length);
            System.arraycopy(rate, 0, firstRate, 0, x.length);
            for (int i = 0; i < x.length; i++) x[i] = start[i] + dt * firstRate[i];
            projectAlgebraicStates();
            if (!acceptFiniteTrial()) {if(automaticPhysicalTrial)discardPhysicalStepTrial();return false;}
            predictorStep = dt;
            predictorPending = true;
            if(automaticPhysicalTrial)publishPhysicalStepTrial();
        } else {
            if (!predictorPending) return false;
            evaluateRates();
            observe(Stage.CORRECTOR);
            for (int i = 0; i < x.length; i++) x[i] = start[i] + .5 * dt * (firstRate[i] + rate[i]);
            projectAlgebraicStates();
            if (!acceptFiniteTrial()) {if(automaticPhysicalTrial)discardPhysicalStepTrial();return false;}
            updateHistory(dt, integrationTime + dt);
            predictorPending = false;
            if (finiteState()) integrationTime += dt;
            if(automaticPhysicalTrial)acceptPhysicalStepTrial();
        }
        return finiteState();
    }

    /** Reject a numerical trial without publishing invalid coordinates or advancing history. */
    private boolean acceptFiniteTrial() {
        if (finiteState()) return true;
        System.arraycopy(trialBackup, 0, x, 0, x.length);
        return false;
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
    }

    private void projectAlgebraicStates() {
        double sensed = remoteBus.getVoltageMag();
        double measured = measurement(sensed), error = voltageError(measured);
        if (data.tb2() == 0) x[5] = error;
        double terminal = getDStabBus().getVoltageMag();
        double low = minimumCurrent(terminal), high = maximumCurrent(terminal);
        double proportional = clamp(data.kp() * gainOutput(error), low, high);
        x[1] = clamp(x[1], low - proportional, high - proportional);
        if (data.firingTime() == 0 && !insideVoltageDeadband(measured)) x[2] = clamp(x[1] + proportional, low, high);
        double resetP = clamp(data.kpr() * resetError(), data.vrMin(), data.vrMax());
        if (data.slowResetFlag() != 0) x[3] = clamp(x[3], data.vrMin() - resetP, data.vrMax() - resetP);
        x[4] = Math.max(0, x[4]);
    }

    private void updateHistory(double dt, double endpointTime) {
        boolean previouslyTripped = tripped;
        double voltage = getDStabBus().getVoltageMag();
        // Undervoltage suppresses output only while outside its envelope.
        // Overvoltage arms a recovery hold; Sdelay starts on voltage recovery,
        // not when blocking first begins. Keep the raw-voltage trip independent.
        boolean recoveryHeldThisInterval = overvoltageRecovery;
        if (voltage > data.ov2()) {
            overvoltageRecovery = data.unblockTime() > 0;
            overvoltageRecoveryStart = -1;
            unblockTimer = 0;
        } else if (overvoltageRecovery) {
            // Recovery begins at the preceding accepted boundary. Use the
            // solver's endpoint clock, including its explicitly selected precision.
            if (overvoltageRecoveryStart < 0) overvoltageRecoveryStart = integrationTime;
            unblockTimer = endpointTime - overvoltageRecoveryStart;
            if (unblockTimer >= data.unblockTime()) {
                overvoltageRecovery = false;
                overvoltageRecoveryStart = -1;
                unblockTimer = 0;
            }
        }
        // Expiration releases the next interval; retain the physical output
        // accepted for the interval whose derivatives still used the hold.
        blocked = voltage < data.uv2() || voltage > data.ov2() || recoveryHeldThisInterval;
        overvoltageTimer = voltage > data.tripVoltage() ? overvoltageTimer + dt : 0;
        if (voltage > data.tripVoltage() && overvoltageTimer >= data.tripTime()) tripped = true;
        if (!thermalRatingActive && !shortRatingExhausted) {
            shortTimer = Math.abs(acceptedCurrent) > data.continuousCurrent() ? shortTimer + dt : 0;
            if (Math.abs(acceptedCurrent) > data.continuousCurrent() && shortTimer >= data.shortRatingTime())
                shortRatingExhausted = true;
        }
        if (thermalRatingActive) {
            if (x[4] >= data.thermalLimit()) thermalLimited = true;
            else if (x[4] <= data.thermalLimit() - data.thermalHysteresis()) thermalLimited = false;
        }
        if (data.deadband() > 0) {
            boolean held = insideVoltageDeadband(measurement(remoteBus.getVoltageMag()));
            if (held) {
                // Held accepted endpoints retain the lag in the PI integral.
                // Lead-lag storage remains live and may contribute proportional output.
                x[1] = x[2];
            }
            deadbandLocked = held;
            deadbandTimer = 0;
        }
        if (tripped && !previouslyTripped) projectAlgebraicStates();
        acceptedCurrent = getCurrent();
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
        if (voltage > data.ov1() && data.ov2() > data.ov1()) {
            double factor = 1 - 2 * (voltage - data.ov1()) / (data.ov2() - data.ov1());
            // Select the voltage-reduction branch using full short-term
            // capability, then reduce the currently available rating. This is
            // distinct from clipping the capability envelope to that rating.
            double capability = data.continuousCurrent() * data.shortCurrentMultiplier();
            return capability * factor < max ? max * factor : max;
        }
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
                || overvoltageRecovery;
    }
    private double measurement(double sensed) {
        return data.tb1() == 0 ? sensed : sensorRatio * sensed + (1 - sensorRatio) * x[0];
    }
    private double gainOutput(double error) {
        return data.tb2() == 0 ? error : gainRatio * error + (1 - gainRatio) * x[5];
    }
    private double resetError() {
        double current = x[2];
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
    private boolean insideVoltageDeadband(double measured) {
        return data.deadband() > 0 && Math.abs(voltageReference() - measured) < data.deadband();
    }
    private double voltageError(double measured) {
        // The deadband acts on the regulator input, not on dynamic storage.
        // Input changes can reopen regulation without committing any history.
        if (insideVoltageDeadband(measured)) return 0;

        // The diagram feeds back the output-lag coordinate. External current
        // blocking must not erase that internal regulator feedback.
        return clamp(voltageReference() + auxiliarySignal - measured - droop(measured) * x[2],
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
    /** Blocking is accepted history, not a new discrete action inside a network iteration. */
    public double getCurrent() {
        return networkDetached || blocked ? 0 : data.firingTime()==0
                ? (physicalTrialPublished?trialPhysicalCurrent:publishedCurrent) : x[2];
    }
    public double getMvarOutput() { return getCurrent() * getDStabBus().getVoltageMag() * data.statcomBaseMva(); }
    public double getVoltageReference() { return voltageReference(); }
    public double getScheduledVoltage() { return scheduledVoltage; }
    public void setScheduledVoltage(double value) {
        if(physicalTrialOpen&&!automaticPhysicalTrial)throw new IllegalStateException("Finish the physical trial before changing its input");
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite scheduled voltage");
        scheduledVoltage = value;
    }
    public void setAuxiliarySignal(double value) {
        if(physicalTrialOpen&&!automaticPhysicalTrial)throw new IllegalStateException("Finish the physical trial before changing its input");
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite auxiliary signal");
        auxiliarySignal = value;
    }
    public boolean isBlocked() { return blocked; }
    public boolean isTripped() { return tripped; }
    public boolean isNetworkDetached() { return networkDetached; }
    /** Accept a scheduled electrical-network event, independently of state integration. */
    public void acceptNetworkEvent() {
        checkNetworkEventReady();
        acceptPreparedNetworkEvent();
    }
    final void acceptPreparedNetworkEvent() {
        if (tripped) { networkDetached = true; acceptedCurrent = 0; }
    }
    void checkNetworkEventReady() {
        if (physicalTrialOpen) throw new IllegalStateException("Accept or discard physical trial before a network event");
        if (predictorPending) throw new IllegalStateException("Finish the predictor before accepting a network event");
    }
    /** Available rating before voltage reduction/blocking; a tripped device has none. */
    public double getAvailableCurrentRating() { return tripped ? 0 : rating(); }
    @Override public Map<String, Double> getIntegrationHistories() {
        var result = new LinkedHashMap<String, Double>();
        result.put("Accepted physical current", acceptedCurrent);
        result.put("Available current rating", getAvailableCurrentRating());
        result.put("Short-rating elapsed seconds", shortTimer);
        result.put("Unblocking elapsed seconds", unblockTimer);
        result.put("Overvoltage elapsed seconds", overvoltageTimer);
        result.put("Deadband elapsed seconds", deadbandTimer);
        result.put("Blocked", blocked ? 1. : 0.);
        result.put("Overvoltage recovery hold", overvoltageRecovery ? 1. : 0.);
        result.put("Overvoltage recovery start seconds", overvoltageRecoveryStart);
        result.put("Tripped", tripped ? 1. : 0.);
        result.put("Network detached", networkDetached ? 1. : 0.);
        result.put("Thermal rating limited", thermalLimited ? 1. : 0.);
        result.put("Definite rating exhausted", shortRatingExhausted ? 1. : 0.);
        result.put("Deadband locked", deadbandLocked ? 1. : 0.);
        return Collections.unmodifiableMap(result);
    }
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
        if (predictorPending || physicalTrialOpen) return false;
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
