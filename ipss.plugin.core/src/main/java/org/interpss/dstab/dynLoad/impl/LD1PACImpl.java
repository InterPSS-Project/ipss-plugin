/**
 * Copyright www.interpss.com 2005-2014
 */
package org.interpss.dstab.dynLoad.impl;

import java.util.Hashtable;

import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.dynLoad.LD1PAC;
import org.interpss.dstab.control.util.IntegrationStateAccess;
import org.interpss.dstab.control.util.IntegrationStateMonitor;
import org.interpss.dstab.control.util.StaggeredEulerParticipant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.core.aclf.AclfLoad;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.dstab.dynLoad.impl.DynLoadModelImpl;
import com.interpss.dstab.controller.cml.ICMLStateProvider;

/**
 * An implementation of the model object '<em><b>LD1PAC</b></em>'.
 */
public class LD1PACImpl extends DynLoadModelImpl implements LD1PAC, ICMLStateProvider,
        StaggeredEulerParticipant, IntegrationStateAccess, org.interpss.dstab.control.util.NetworkEventInput,
        org.interpss.dstab.control.util.InitialEndpointAction,
        org.interpss.dstab.control.util.IntegrationStepContext {
    private final double[] integrationRates = new double[9];
    private final double[] timerStart = new double[5], timerFirstRates = new double[5], timerTrial = new double[5];
    private final double[] evaluatedCurrent = new double[2];
    private IntegrationStateMonitor integrationMonitor = new IntegrationStateMonitor(
            "Vmeas", "Fmeas", "TemperatureA", "TemperatureB");
    private long staggeredEvaluationCount;
    public enum CharacteristicPolicy { STANDALONE, LEGACY_COMPOSITE, ZONE_COMPOSITE }
    private CharacteristicPolicy characteristicPolicy = CharacteristicPolicy.STANDALONE;
    private boolean legacyCompositeCharacteristic;
    private Complex electricalNortonY;
    private AclfLoad parentLoad;
    public enum AcceptedTimerPrecision { DOUBLE, BINARY32 }
    private AcceptedTimerPrecision acceptedTimerPrecision = AcceptedTimerPrecision.DOUBLE;
    public enum FrequencyInputMode { BUS_FREQUENCY, TERMINAL_PHASE_DOUBLE, TERMINAL_PHASE_BINARY32 }
    private FrequencyInputMode frequencyInputMode = FrequencyInputMode.BUS_FREQUENCY;
    private double acceptedPhase, phaseSampleTime, phaseFrequencyDeviation, eventFrequencyDeviation;
    private double frequencyOutput = 1, lastFrequencyEventTime = Double.NaN;
    private boolean frequencyEventPending;
    private double reactiveFrequencyScale = 1;
    public enum ElectricalInputMode { LIVE_NETWORK, ACCEPTED_ENDPOINT }
    private ElectricalInputMode electricalInputMode = ElectricalInputMode.LIVE_NETWORK;
    private double acceptedVoltageMagnitude, acceptedHeatingSquaredA, acceptedHeatingSquaredB;
    private final double[] networkEventLeftRates = new double[9];
    private boolean networkEventRatesPrepared;
    private boolean legacyUvTripped;
    private boolean initialEndpointAccepted;
    private double pendingSamplingEndpoint = Double.NaN;

    @Override public void prepareIntegrationStep(double startTimeSec, double stepSec) {
        if (!Double.isFinite(startTimeSec) || !Double.isFinite(stepSec) || stepSec < 0
                || !Double.isFinite(startTimeSec + stepSec))
            throw new IllegalArgumentException("A finite compressor interval and physical clock are required");
        pendingSamplingEndpoint = startTimeSec + stepSec;
    }

    private void updateLegacyContactor(double voltage) {
        if (voltage < vc1off) {
            double remaining = voltage < vc2off ? 0 : (voltage - vc2off) / (vc1off - vc2off);
            kcon = Math.min(kcon, remaining);
        }
        if (voltage >= vc1on) kcon = 1;
        else if (voltage >= vc2on)
            kcon = Math.max(kcon, (voltage - vc2on) / (vc1on - vc2on));
        fcon_trip = Math.max(fcon_trip, 1 - kcon);
    }

    private double thermalRemaining(double temperature) {
        if (temperature < th1t) return 1;
        if (th2t == 0 || th1t >= th2t) return 0;
        return Math.max(0, Math.min(1, (th2t - temperature) / (th2t - th1t)));
    }

    private void updateLegacyThermalProtection() {
        // A bypass exposes the current algebraic temperature immediately, but
        // accepted trip decisions consume the separately sampled heating input.
        fthA = Math.min(fthA, thermalRemaining(thermalBypassed() ? rstall * acceptedHeatingSquaredA : tempA));
        // Group B holds its thermal fraction while waiting to restart. The
        // accepted restart action clears its timer before this evaluation.
        if (statusB != 0 || acRestartTimer == 0)
            fthB = Math.min(fthB, thermalRemaining(thermalBypassed() ? rstall * acceptedHeatingSquaredB : tempB));
        if (fthA == 0) tempA = tempA_old = 0;
        if (fthB == 0) tempB = tempB_old = 0;
    }

    @Override public boolean acceptInitialEndpoint() {
        if (!legacyCompositeCharacteristic) return false;
        if (PQmotor == null) throw new IllegalStateException("Initialize the compressor before accepting its endpoint");
        if (initialEndpointAccepted) return false;
        initialEndpointAccepted = true;
        double beforeA = fthA, beforeB = fthB;
        updateLegacyThermalProtection();
        calculateMotorPower();
        sampleElectricalInputs();
        return beforeA != fthA || beforeB != fthB;
    }

    @Override public void acceptInitialNetworkEndpoint() {
        if (!legacyCompositeCharacteristic) return;
        if (!initialEndpointAccepted) throw new IllegalStateException("Accept the initial physical action before sampling its network");
        calculateMotorPower();
        sampleElectricalInputs();
    }

    /** Select the model's electrical measurement boundary before initialization. */
    public void setElectricalInputMode(ElectricalInputMode mode) {
        java.util.Objects.requireNonNull(mode);
        if (PQmotor != null && mode != electricalInputMode)
            throw new IllegalStateException("Electrical input sampling must be selected before initialization");
        electricalInputMode = mode;
    }
    public ElectricalInputMode getElectricalInputMode() { return electricalInputMode; }
    @Override public boolean providesNetworkEventLeftRates() {
        return electricalInputMode == ElectricalInputMode.ACCEPTED_ENDPOINT;
    }
    @Override public boolean readNetworkEventLeftRates(double[] rates, int offset) {
        java.util.Objects.checkFromIndexSize(offset, staggeredStateCount(), rates.length);
        if (!networkEventRatesPrepared) return false;
        System.arraycopy(networkEventLeftRates, 0, rates, offset, staggeredStateCount());
        return true;
    }
    private double voltageInput() {
        return electricalInputMode == ElectricalInputMode.ACCEPTED_ENDPOINT
                ? acceptedVoltageMagnitude : getDStabBus().getVoltageMag();
    }
    private void sampleElectricalInputs() {
        boolean held = electricalInputMode == ElectricalInputMode.ACCEPTED_ENDPOINT;
        if (!held && !thermalBypassed()) return;
        Complex voltage = getDStabBus().getVoltage();
        double magnitude = getDStabBus().getVoltageMag();
        if (held) acceptedVoltageMagnitude = magnitude;
        acceptedHeatingSquaredA = heatingCurrentSquared(voltage, magnitude, statusA, pac_a, qac_a, fthA);
        acceptedHeatingSquaredB = heatingCurrentSquared(voltage, magnitude, statusB, pac_b, qac_b, fthB);
        if (held && tv <= 1e-12) vt_measured = vt_measured_old = acceptedVoltageMagnitude;
    }

    /** Choose a prepared phase sampler or the existing bus-frequency input before initialization. */
    public void setFrequencyInputMode(FrequencyInputMode mode) {
        java.util.Objects.requireNonNull(mode);
        if (PQmotor != null && mode != frequencyInputMode)
            throw new IllegalStateException("Frequency input must be selected before initialization");
        frequencyInputMode = mode;
    }
    public FrequencyInputMode getFrequencyInputMode() { return frequencyInputMode; }
    public double getFrequencyOutput() { return frequencyOutput; }
    private void prepareReactiveFrequencyScale() {
        reactiveFrequencyScale = !legacyCompositeCharacteristic ? 1
                : Math.abs(powerFactor) < 1 ? 1 / Math.sqrt(1 - powerFactor * powerFactor) : 0;
    }
    private double frequencySignal() {
        return legacyCompositeCharacteristic ? Math.max(.95, Math.min(1.05, freq_measured)) : freq_measured;
    }
    @Override public boolean averageStaggeredRateAtDiscontinuity(int index) {
        // The sampled phase input has its own explicit event preparation.
        return index != 1 || frequencyInputMode == FrequencyInputMode.BUS_FREQUENCY;
    }
    @Override public void acceptNetworkEventInput(double physicalTimeSec) {
        if (!Double.isFinite(physicalTimeSec)) throw new IllegalArgumentException("Invalid event time");
        if ((frequencyInputMode == FrequencyInputMode.BUS_FREQUENCY
                && electricalInputMode == ElectricalInputMode.LIVE_NETWORK && !legacyCompositeCharacteristic)
                || physicalTimeSec == lastFrequencyEventTime) return;
        if (physicalTimeSec < lastFrequencyEventTime)
            throw new IllegalArgumentException("Frequency events must be ordered");
        if (electricalInputMode == ElectricalInputMode.ACCEPTED_ENDPOINT) {
            networkEventLeftRates[0] = tv > 1e-12 ? (acceptedVoltageMagnitude - vt_measured) / tv : 0;
            networkEventLeftRates[1] = tf > 1e-12 ? (frequencyInput() - freq_measured) / tf : 0;
            networkEventLeftRates[2] = temperatureRate(tempA, acceptedHeatingSquaredA, fthA);
            networkEventLeftRates[3] = temperatureRate(tempB, acceptedHeatingSquaredB, fthB);
            if (legacyCompositeCharacteristic) evaluateTimerRates(acceptedVoltageMagnitude, networkEventLeftRates, 4);
            networkEventRatesPrepared = true;
        }
        if (legacyCompositeCharacteristic) {
            projectTimerPickups(getDStabBus().getVoltageMag());
            updateLegacyContactor(getDStabBus().getVoltageMag());
        }
        if (electricalInputMode == ElectricalInputMode.ACCEPTED_ENDPOINT || thermalBypassed()) {
            calculateMotorPower();
            sampleElectricalInputs();
        }
        if (frequencyInputMode != FrequencyInputMode.BUS_FREQUENCY) {
            eventFrequencyDeviation = phaseFrequencyDeviation;
            phaseFrequencyDeviation = 0;
            frequencyEventPending = true;
            if (tf <= 1e-12) freq_measured = freq_measured_old = 1;
        }
        lastFrequencyEventTime = physicalTimeSec;
    }
    private BaseDStabBus<?, ?> frequencyBus() {
        return frequencySourceBus == null ? getDStabBus() : frequencySourceBus;
    }
    private void acceptPhaseSample(double interval) {
        double endpoint = pendingSamplingEndpoint;
        pendingSamplingEndpoint = Double.NaN;
        if (frequencyInputMode == FrequencyInputMode.BUS_FREQUENCY || interval == 0) return;
        // Continuous states still integrate during startup. Only the sampled
        // phase memory and its clock wait for the first positive endpoint.
        if (endpoint <= 0) return;
        double nextTime = frequencyInputMode == FrequencyInputMode.TERMINAL_PHASE_BINARY32
                ? (double) ((float) phaseSampleTime + (float) interval) : phaseSampleTime + interval;
        if (!Double.isFinite(nextTime) || !(nextTime > phaseSampleTime))
            throw new IllegalStateException("Frequency input clock cannot advance");
        double phase = frequencyBus().getVoltageAng();
        phaseFrequencyDeviation = Math.IEEEremainder(phase - acceptedPhase, 2 * Math.PI)
                / (2 * Math.PI * getDStabBus().getNetwork().getFrequency() * (nextTime - phaseSampleTime));
        acceptedPhase = phase; phaseSampleTime = nextTime; frequencyEventPending = false;
        if (tf <= 1e-12) freq_measured = 1 + phaseFrequencyDeviation;
    }

    /** Select the arithmetic of committed protection timers before initialization. */
    public void setAcceptedTimerPrecision(AcceptedTimerPrecision precision) {
        java.util.Objects.requireNonNull(precision);
        if (PQmotor != null && precision != acceptedTimerPrecision)
            throw new IllegalStateException("Timer precision must be selected before initialization");
        acceptedTimerPrecision = precision;
    }
    public AcceptedTimerPrecision getAcceptedTimerPrecision() { return acceptedTimerPrecision; }
    private double advanceAcceptedTimer(double timer, double interval) {
        return acceptedTimerPrecision == AcceptedTimerPrecision.BINARY32
                ? (double) ((float) timer + (float) interval) : timer + interval;
    }

    private void readTimers(double[] values, int offset) {
        values[offset] = uVRelayTimer1; values[offset + 1] = uVRelayTimer2;
        values[offset + 2] = acStallTimer; values[offset + 3] = acStallTimerB;
        values[offset + 4] = acRestartTimer;
    }
    private void applyTimers(double[] values, int offset) {
        uVRelayTimer1 = Math.max(0, values[offset]); uVRelayTimer2 = Math.max(0, values[offset + 1]);
        acStallTimer = Math.max(0, values[offset + 2]); acStallTimerB = Math.max(0, values[offset + 3]);
        acRestartTimer = Math.max(0, values[offset + 4]);
    }
    private void evaluateTimerRates(double voltage, double[] rates, int offset) {
        boolean uv = fuvr > 0 && !legacyUvTripped;
        rates[offset] = uv && voltage < uvtr1 ? 1 : 0;
        rates[offset + 1] = uv && voltage < uvtr2 ? 1 : 0;
        rates[offset + 2] = statusA == 1 && voltage < vstall ? 1 : 0;
        rates[offset + 3] = frst > 0 && statusB == 1 && voltage < vstall ? 1 : 0;
        rates[offset + 4] = frst > 0 && statusB == 0 && voltage > vrst ? 1 : 0;
    }
    private void projectTimerPickups(double voltage) {
        if (!(fuvr > 0 && !legacyUvTripped && voltage < uvtr1)) uVRelayTimer1 = 0;
        if (!(fuvr > 0 && !legacyUvTripped && voltage < uvtr2)) uVRelayTimer2 = 0;
        if (!(statusA == 1 && voltage < vstall)) acStallTimer = 0;
        if (!(frst > 0 && statusB == 1 && voltage < vstall)) acStallTimerB = 0;
        if (!(frst > 0 && statusB == 0 && voltage > vrst)) acRestartTimer = 0;
    }
    private void roundAcceptedTimers() {
        if (acceptedTimerPrecision != AcceptedTimerPrecision.BINARY32) return;
        uVRelayTimer1 = (float) uVRelayTimer1; uVRelayTimer2 = (float) uVRelayTimer2;
        acStallTimer = (float) acStallTimer; acStallTimerB = (float) acStallTimerB;
        acRestartTimer = (float) acRestartTimer;
    }

    /** Bind a complete source load record before initialization; loading factor is not coverage. */
    public void setParentLoad(AclfLoad load) {
        java.util.Objects.requireNonNull(load);
        if (PQmotor != null) throw new IllegalStateException("Source load must be bound before initialization");
        if (getDStabBus() == null || getDStabBus().getContributeLoad(load.getId()) != load)
            throw new IllegalArgumentException("Source load must belong to the compressor bus");
        parentLoad = load;
    }

    public AclfLoad getParentLoad() { return parentLoad; }

    /** Select the compressor family's characteristic before initialization. */
    public void setCharacteristicPolicy(CharacteristicPolicy policy) {
        java.util.Objects.requireNonNull(policy);
        if (PQmotor != null && policy != characteristicPolicy)
            throw new IllegalStateException("Compressor characteristic must be selected before initialization");
        boolean wasLegacy = legacyCompositeCharacteristic;
        characteristicPolicy = policy;
        legacyCompositeCharacteristic = policy == CharacteristicPolicy.LEGACY_COMPOSITE;
        if (wasLegacy != legacyCompositeCharacteristic)
            integrationMonitor = legacyCompositeCharacteristic
                    ? new IntegrationStateMonitor("Vmeas", "Fmeas", "TemperatureA", "TemperatureB",
                            "UvTimer1", "UvTimer2", "StallTimer", "StallTimerB", "RestartTimer")
                    : new IntegrationStateMonitor("Vmeas", "Fmeas", "TemperatureA", "TemperatureB");
        prepareReactiveFrequencyScale();
    }

    @Override public int staggeredStateCount() { return legacyCompositeCharacteristic ? 9 : 4; }
    @Override public void readStaggeredState(double[] values, int offset) {
        java.util.Objects.checkFromIndexSize(offset, staggeredStateCount(), values.length);
        values[offset] = vt_measured; values[offset + 1] = freq_measured;
        values[offset + 2] = tempA; values[offset + 3] = tempB;
        if (legacyCompositeCharacteristic) readTimers(values, offset + 4);
    }
    @Override public void evaluateStaggered(double stepSec, double[] rates, int offset) {
        java.util.Objects.checkFromIndexSize(offset, staggeredStateCount(), rates.length);
        if (!Double.isFinite(stepSec) || stepSec <= 0)
            throw new IllegalArgumentException("A motor integration interval must be finite and positive");
        evaluateRates(vt_measured, freq_measured, rates, offset, IntegrationStateMonitor.Stage.STAGGERED);
        staggeredEvaluationCount++;
    }
    @Override public void applyStaggeredState(double[] values, int offset, double stepSec) {
        java.util.Objects.checkFromIndexSize(offset, staggeredStateCount(), values.length);
        if (!Double.isFinite(stepSec) || stepSec < 0) throw new IllegalArgumentException("Invalid motor interval");
        for (int i = 0; i < staggeredStateCount(); i++)
            if (!Double.isFinite(values[offset + i])) throw new IllegalArgumentException("Motor states must be finite");
        vt_measured = tv > 1e-12 ? values[offset] : voltageInput();
        freq_measured = tf > 1e-12 ? values[offset + 1] : frequencyInput();
        if (thermalBypassed()) refreshAlgebraicTemperatures(getDStabBus().getVoltage(), getDStabBus().getVoltageMag());
        else {
            tempA = legacyCompositeCharacteristic && fthA == 0 ? 0 : values[offset + 2];
            tempB = legacyCompositeCharacteristic && fthB == 0 ? 0 : values[offset + 3];
        }
        if (legacyCompositeCharacteristic) applyTimers(values, offset + 4);
    }
    @Override public void completeStaggeredStep(double stepSec) {
        if (!Double.isFinite(stepSec) || stepSec < 0)
            throw new IllegalArgumentException("An accepted motor interval must be finite and nonnegative");
        if (stepSec == 0) return;
        vt_measured_old = vt_measured; freq_measured_old = freq_measured;
        tempA_old = tempA; tempB_old = tempB;
        timeCounter += stepSec;
    }
    private void evaluateRates(double measuredVoltage, double measuredFrequency, double[] rates,
            int offset, IntegrationStateMonitor.Stage stage) {
        Complex voltage = getDStabBus().getVoltage();
        double magnitude = getDStabBus().getVoltageMag();
        rates[offset] = tv > 1e-12 ? (voltageInput() - measuredVoltage) / tv : 0;
        rates[offset + 1] = tf > 1e-12 ? (frequencyInput() - measuredFrequency) / tf : 0;
        if (thermalBypassed()) {
            refreshAlgebraicTemperatures(voltage, magnitude);
            rates[offset + 2] = rates[offset + 3] = 0;
        } else {
            double heatA = electricalInputMode == ElectricalInputMode.ACCEPTED_ENDPOINT ? acceptedHeatingSquaredA
                    : heatingCurrentSquared(voltage, magnitude, statusA, pac_a, qac_a, fthA);
            double heatB = electricalInputMode == ElectricalInputMode.ACCEPTED_ENDPOINT ? acceptedHeatingSquaredB
                    : heatingCurrentSquared(voltage, magnitude, statusB, pac_b, qac_b, fthB);
            rates[offset + 2] = temperatureRate(tempA, heatA, fthA);
            rates[offset + 3] = temperatureRate(tempB, heatB, fthB);
        }
        if (legacyCompositeCharacteristic) evaluateTimerRates(voltageInput(), rates, offset + 4);
        integrationMonitor.begin(stage);
        integrationMonitor.record(0, measuredVoltage, rates[offset]);
        integrationMonitor.record(1, measuredFrequency, rates[offset + 1]);
        integrationMonitor.record(2, tempA, rates[offset + 2]);
        integrationMonitor.record(3, tempB, rates[offset + 3]);
        if (legacyCompositeCharacteristic) {
            integrationMonitor.record(4, uVRelayTimer1, rates[offset + 4]);
            integrationMonitor.record(5, uVRelayTimer2, rates[offset + 5]);
            integrationMonitor.record(6, acStallTimer, rates[offset + 6]);
            integrationMonitor.record(7, acStallTimerB, rates[offset + 7]);
            integrationMonitor.record(8, acRestartTimer, rates[offset + 8]);
        }
    }
    private double temperatureRate(double temperature, double heatingSquared, double remaining) {
        return thermalBypassed() || legacyCompositeCharacteristic && remaining == 0 ? 0 : (heatingSquared * rstall - temperature) / tth;
    }
    private double heatingCurrentSquared(Complex voltage, double magnitude, int status, double p, double q, double remaining) {
        if (legacyCompositeCharacteristic && (kcon == 0 || remaining == 0)) return 0;
        if (status == 0 || magnitude <= vstallbrk) {
            double deviation = frequencySignal() - 1;
            double conductance = YStall.getReal() * (legacyCompositeCharacteristic && status == 1
                    ? loadFactor * (1 + cmpKpf * deviation) : 1);
            double susceptance = YStall.getImaginary() * (legacyCompositeCharacteristic && status == 1
                    ? 1 + cmpKqf * reactiveFrequencyScale * deviation : 1);
            evaluatedCurrent[0] = voltage.getReal() * conductance - voltage.getImaginary() * susceptance;
            evaluatedCurrent[1] = voltage.getReal() * susceptance + voltage.getImaginary() * conductance;
        } else MotorElectricalKernel.divide(p, q, voltage.getReal(), voltage.getImaginary(), evaluatedCurrent);
        return Math.pow(org.apache.commons.math3.util.FastMath.hypot(evaluatedCurrent[0], evaluatedCurrent[1]), 2);
    }
    @Override public Map<String, Double> getIntegrationStates() {
        if (legacyCompositeCharacteristic)
            return Map.of("Vmeas", vt_measured, "Fmeas", freq_measured,
                    "TemperatureA", tempA, "TemperatureB", tempB, "UvTimer1", uVRelayTimer1,
                    "UvTimer2", uVRelayTimer2, "StallTimer", acStallTimer,
                    "StallTimerB", acStallTimerB, "RestartTimer", acRestartTimer);
        return Map.of("Vmeas", vt_measured, "Fmeas", freq_measured,
                "TemperatureA", tempA, "TemperatureB", tempB);
    }
    @Override public IntegrationStateMonitor.Snapshot getStateDerivativeSnapshot() { return integrationMonitor.snapshot(); }
    @Override public Map<String, Double> getIntegrationHistories() {
        Map<String, Double> result = Map.ofEntries(Map.entry("UvTimer1", uVRelayTimer1), Map.entry("UvTimer2", uVRelayTimer2),
                Map.entry("StallTimer", acStallTimer), Map.entry("StallTimerB", acStallTimerB),
                Map.entry("RestartTimer", acRestartTimer), Map.entry("ContactorFraction", kcon),
                Map.entry("ContactorTripFraction", fcon_trip), Map.entry("UvFraction", kuv),
                Map.entry("UvTripped", legacyUvTripped ? 1. : 0.),
                Map.entry("ContactorClosed", kcon > 0 ? 1. : 0.),
                Map.entry("ThermalFractionA", fthA), Map.entry("ThermalFractionB", fthB),
                Map.entry("RunA", (double) statusA), Map.entry("RunB", (double) statusB),
                Map.entry("InitialEndpointAccepted", initialEndpointAccepted ? 1. : 0.),
                Map.entry("AcceptedTime", timeCounter));
        if (frequencyInputMode == FrequencyInputMode.BUS_FREQUENCY
                && electricalInputMode == ElectricalInputMode.LIVE_NETWORK && !thermalBypassed()) return result;
        var history = new LinkedHashMap<>(result);
        if (thermalBypassed()) {
            history.put("ThermalBypass", 1.);
            history.put("ThermalActionTemperatureA", rstall * acceptedHeatingSquaredA);
            history.put("ThermalActionTemperatureB", rstall * acceptedHeatingSquaredB);
        }
        history.put("FrequencySampleTime", phaseSampleTime);
        history.put("FrequencyPhase", acceptedPhase);
        history.put("FrequencySampleDeviation", phaseFrequencyDeviation);
        history.put("FrequencyEventDeviation", eventFrequencyDeviation);
        history.put("FrequencyEventPending", frequencyEventPending ? 1. : 0.);
        history.put("FrequencyInput", frequencyInput());
        history.put("FrequencyEventSeen", Double.isFinite(lastFrequencyEventTime) ? 1. : 0.);
        history.put("FrequencyEventTime", Double.isFinite(lastFrequencyEventTime) ? lastFrequencyEventTime : 0.);
        if (electricalInputMode == ElectricalInputMode.ACCEPTED_ENDPOINT) {
            history.put("ElectricalVoltage", acceptedVoltageMagnitude);
            history.put("ElectricalHeatingSquaredA", acceptedHeatingSquaredA);
            history.put("ElectricalHeatingSquaredB", acceptedHeatingSquaredB);
            history.put("ElectricalEventRatesPrepared", networkEventRatesPrepared ? 1. : 0.);
        }
        return Map.copyOf(history);
    }
    public long getStaggeredEvaluationCount() { return staggeredEvaluationCount; }
    private static final Logger logger = LoggerFactory.getLogger(LD1PACImpl.class);
	private BaseDStabBus<?, ?> frequencySourceBus;
	/**
	 * The default value of the '{@link #getStage() <em>Stage</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getStage()
	 * @generated
	 * @ordered
	 */
	protected static final int STAGE_EDEFAULT = 0;

	/**
	 * The cached value of the '{@link #getStage() <em>Stage</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getStage()
	 * @generated
	 * @ordered
	 */
	protected int stage = STAGE_EDEFAULT;

	/**
	 * The default value of the '{@link #getPac() <em>Pac</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getPac()
	 * @generated
	 * @ordered
	 */
	protected static final double PAC_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getPac() <em>Pac</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getPac()
	 * @generated
	 * @ordered
	 */
	protected double pac = PAC_EDEFAULT;

	/**
	 * The default value of the '{@link #getQac() <em>Qac</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getQac()
	 * @generated
	 * @ordered
	 */
	protected static final double QAC_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getQac() <em>Qac</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getQac()
	 * @generated
	 * @ordered
	 */
	protected double qac = QAC_EDEFAULT;

	/**
	 * The default value of the '{@link #getPowerFactor() <em>Power Factor</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getPowerFactor()
	 * @generated
	 * @ordered
	 */
	protected static final double POWER_FACTOR_EDEFAULT = 0.97;

	/**
	 * The cached value of the '{@link #getPowerFactor() <em>Power Factor</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getPowerFactor()
	 * @generated
	 * @ordered
	 */
	protected double powerFactor = POWER_FACTOR_EDEFAULT;
	
	
	/**
	 * The default value of the '{@link #getLoadFactor() <em>Load Factor</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getLoadFactor()
	 * @generated
	 * @ordered
	 */
	protected static final double LOAD_FACTOR_EDEFAULT = 0.85;

	/**
	 * The default value of the '{@link #getVstall() <em>Vstall</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getVstall()
	 * @generated
	 * @ordered
	 */
	protected static final double VSTALL_EDEFAULT = 0.6;

	/**
	 * The cached value of the '{@link #getVstall() <em>Vstall</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getVstall()
	 * @generated
	 * @ordered
	 */
	protected double vstall = VSTALL_EDEFAULT;
    private double configuredVstall = VSTALL_EDEFAULT;

	/**
	 * The default value of the '{@link #getRstall() <em>Rstall</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getRstall()
	 * @generated
	 * @ordered
	 */
	protected static final double RSTALL_EDEFAULT = 0.124;

	/**
	 * The cached value of the '{@link #getRstall() <em>Rstall</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getRstall()
	 * @generated
	 * @ordered
	 */
	protected double rstall = RSTALL_EDEFAULT;

	/**
	 * The default value of the '{@link #getXstall() <em>Xstall</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getXstall()
	 * @generated
	 * @ordered
	 */
	protected static final double XSTALL_EDEFAULT = 0.114;

	/**
	 * The cached value of the '{@link #getXstall() <em>Xstall</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getXstall()
	 * @generated
	 * @ordered
	 */
	protected double xstall = XSTALL_EDEFAULT;

	/**
	 * The default value of the '{@link #getTstall() <em>Tstall</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getTstall()
	 * @generated
	 * @ordered
	 */
	protected static final double TSTALL_EDEFAULT = 0.033;

	/**
	 * The cached value of the '{@link #getTstall() <em>Tstall</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getTstall()
	 * @generated
	 * @ordered
	 */
	protected double tstall = TSTALL_EDEFAULT;

	/**
	 * The default value of the '{@link #getLFadj() <em>LFadj</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getLFadj()
	 * @generated
	 * @ordered
	 */
	protected static final double LFADJ_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getLFadj() <em>LFadj</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getLFadj()
	 * @generated
	 * @ordered
	 */
	protected double lFadj = LFADJ_EDEFAULT;

	/**
	 * The default value of the '{@link #getKp1() <em>Kp1</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getKp1()
	 * @generated
	 * @ordered
	 */
	protected static final double KP1_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getKp1() <em>Kp1</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getKp1()
	 * @generated
	 * @ordered
	 */
	protected double kp1 = KP1_EDEFAULT;

	/**
	 * The default value of the '{@link #getNp1() <em>Np1</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getNp1()
	 * @generated
	 * @ordered
	 */
	protected static final double NP1_EDEFAULT = 1.0;

	/**
	 * The cached value of the '{@link #getNp1() <em>Np1</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getNp1()
	 * @generated
	 * @ordered
	 */
	protected double np1 = NP1_EDEFAULT;

	/**
	 * The default value of the '{@link #getKq1() <em>Kq1</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getKq1()
	 * @generated
	 * @ordered
	 */
	protected static final double KQ1_EDEFAULT = 6.0;

	/**
	 * The cached value of the '{@link #getKq1() <em>Kq1</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getKq1()
	 * @generated
	 * @ordered
	 */
	protected double kq1 = KQ1_EDEFAULT;

	/**
	 * The default value of the '{@link #getNq1() <em>Nq1</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getNq1()
	 * @generated
	 * @ordered
	 */
	protected static final double NQ1_EDEFAULT = 2.0;

	/**
	 * The cached value of the '{@link #getNq1() <em>Nq1</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getNq1()
	 * @generated
	 * @ordered
	 */
	protected double nq1 = NQ1_EDEFAULT;

	/**
	 * The default value of the '{@link #getKp2() <em>Kp2</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getKp2()
	 * @generated
	 * @ordered
	 */
	protected static final double KP2_EDEFAULT = 12.0;

	/**
	 * The cached value of the '{@link #getKp2() <em>Kp2</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getKp2()
	 * @generated
	 * @ordered
	 */
	protected double kp2 = KP2_EDEFAULT;

	/**
	 * The default value of the '{@link #getNp2() <em>Np2</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getNp2()
	 * @generated
	 * @ordered
	 */
	protected static final double NP2_EDEFAULT = 3.2;

	/**
	 * The cached value of the '{@link #getNp2() <em>Np2</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getNp2()
	 * @generated
	 * @ordered
	 */
	protected double np2 = NP2_EDEFAULT;

	/**
	 * The default value of the '{@link #getKq2() <em>Kq2</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getKq2()
	 * @generated
	 * @ordered
	 */
	protected static final double KQ2_EDEFAULT = 11.0;

	/**
	 * The cached value of the '{@link #getKq2() <em>Kq2</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getKq2()
	 * @generated
	 * @ordered
	 */
	protected double kq2 = KQ2_EDEFAULT;

	/**
	 * The default value of the '{@link #getNq2() <em>Nq2</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getNq2()
	 * @generated
	 * @ordered
	 */
	protected static final double NQ2_EDEFAULT = 2.5;

	/**
	 * The cached value of the '{@link #getNq2() <em>Nq2</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getNq2()
	 * @generated
	 * @ordered
	 */
	protected double nq2 = NQ2_EDEFAULT;

	/**
	 * The default value of the '{@link #getVbrk() <em>Vbrk</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getVbrk()
	 * @generated
	 * @ordered
	 */
	protected static final double VBRK_EDEFAULT = 0.86;

	/**
	 * The cached value of the '{@link #getVbrk() <em>Vbrk</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getVbrk()
	 * @generated
	 * @ordered
	 */
	protected double vbrk = VBRK_EDEFAULT;
    private double configuredVbrk = VBRK_EDEFAULT;

	/**
	 * The default value of the '{@link #getFrst() <em>Frst</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getFrst()
	 * @generated
	 * @ordered
	 */
	protected static final double FRST_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getFrst() <em>Frst</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getFrst()
	 * @generated
	 * @ordered
	 */
	protected double frst = FRST_EDEFAULT;

	/**
	 * The default value of the '{@link #getVrst() <em>Vrst</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getVrst()
	 * @generated
	 * @ordered
	 */
	protected static final double VRST_EDEFAULT = 0.9;

	/**
	 * The cached value of the '{@link #getVrst() <em>Vrst</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getVrst()
	 * @generated
	 * @ordered
	 */
	protected double vrst = VRST_EDEFAULT;

	/**
	 * The default value of the '{@link #getTrst() <em>Trst</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getTrst()
	 * @generated
	 * @ordered
	 */
	protected static final double TRST_EDEFAULT = 0.4;

	/**
	 * The cached value of the '{@link #getTrst() <em>Trst</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getTrst()
	 * @generated
	 * @ordered
	 */
	protected double trst = TRST_EDEFAULT;

	/**
	 * The default value of the '{@link #getCmpKpf() <em>Cmp Kpf</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getCmpKpf()
	 * @generated
	 * @ordered
	 */
	protected static final double CMP_KPF_EDEFAULT = 1.0;

	/**
	 * The cached value of the '{@link #getCmpKpf() <em>Cmp Kpf</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getCmpKpf()
	 * @generated
	 * @ordered
	 */
	protected double cmpKpf = CMP_KPF_EDEFAULT;

	/**
	 * The default value of the '{@link #getCmpKqf() <em>Cmp Kqf</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getCmpKqf()
	 * @generated
	 * @ordered
	 */
	protected static final double CMP_KQF_EDEFAULT = -3.3;

	/**
	 * The cached value of the '{@link #getCmpKqf() <em>Cmp Kqf</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getCmpKqf()
	 * @generated
	 * @ordered
	 */
	protected double cmpKqf = CMP_KQF_EDEFAULT;

	/**
	 * The default value of the '{@link #getFuvr() <em>Fuvr</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getFuvr()
	 * @generated
	 * @ordered
	 */
	protected static final double FUVR_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getFuvr() <em>Fuvr</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getFuvr()
	 * @generated
	 * @ordered
	 */
	protected double fuvr = FUVR_EDEFAULT;

	/**
	 * The default value of the '{@link #getVtr1() <em>Vtr1</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getVtr1()
	 * @generated
	 * @ordered
	 */
	protected static final double UVTR1_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getVtr1() <em>Vtr1</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getVtr1()
	 * @generated
	 * @ordered
	 */
	protected double uvtr1 = UVTR1_EDEFAULT;

	/**
	 * The default value of the '{@link #getTtr1() <em>Ttr1</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getTtr1()
	 * @generated
	 * @ordered
	 */
	protected static final double TTR1_EDEFAULT = 999.0;

	/**
	 * The cached value of the '{@link #getTtr1() <em>Ttr1</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getTtr1()
	 * @generated
	 * @ordered
	 */
	protected double ttr1 = TTR1_EDEFAULT;

	/**
	 * The default value of the '{@link #getVtr2() <em>Vtr2</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getVtr2()
	 * @generated
	 * @ordered
	 */
	protected static final double UVTR2_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getVtr2() <em>Vtr2</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getVtr2()
	 * @generated
	 * @ordered
	 */
	protected double uvtr2 = UVTR2_EDEFAULT;

	/**
	 * The default value of the '{@link #getTtr2() <em>Ttr2</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getTtr2()
	 * @generated
	 * @ordered
	 */
	protected static final double TTR2_EDEFAULT = 999.0;

	/**
	 * The cached value of the '{@link #getTtr2() <em>Ttr2</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getTtr2()
	 * @generated
	 * @ordered
	 */
	protected double ttr2 = TTR2_EDEFAULT;

	/**
	 * The default value of the '{@link #getVc1off() <em>Vc1off</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getVc1off()
	 * @generated
	 * @ordered
	 */
	protected static final double VC1OFF_EDEFAULT = 0.5;

	/**
	 * The cached value of the '{@link #getVc1off() <em>Vc1off</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getVc1off()
	 * @generated
	 * @ordered
	 */
	protected double vc1off = VC1OFF_EDEFAULT;

	/**
	 * The default value of the '{@link #getVc2off() <em>Vc2off</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getVc2off()
	 * @generated
	 * @ordered
	 */
	protected static final double VC2OFF_EDEFAULT = 0.4;

	/**
	 * The cached value of the '{@link #getVc2off() <em>Vc2off</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getVc2off()
	 * @generated
	 * @ordered
	 */
	protected double vc2off = VC2OFF_EDEFAULT;

	/**
	 * The default value of the '{@link #getVc1on() <em>Vc1on</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getVc1on()
	 * @generated
	 * @ordered
	 */
	protected static final double VC1ON_EDEFAULT = 0.6;

	/**
	 * The cached value of the '{@link #getVc1on() <em>Vc1on</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getVc1on()
	 * @generated
	 * @ordered
	 */
	protected double vc1on = VC1ON_EDEFAULT;

	/**
	 * The default value of the '{@link #getVc2on() <em>Vc2on</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getVc2on()
	 * @generated
	 * @ordered
	 */
	protected static final double VC2ON_EDEFAULT = 0.5;

	/**
	 * The cached value of the '{@link #getVc2on() <em>Vc2on</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getVc2on()
	 * @generated
	 * @ordered
	 */
	protected double vc2on = VC2ON_EDEFAULT;

	/**
	 * The default value of the '{@link #getTth() <em>Tth</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getTth()
	 * @generated
	 * @ordered
	 */
	protected static final double TTH_EDEFAULT = 999.0;

	/**
	 * The cached value of the '{@link #getTth() <em>Tth</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getTth()
	 * @generated
	 * @ordered
	 */
	protected double tth = TTH_EDEFAULT;

	/**
	 * The default value of the '{@link #getTh1t() <em>Th1t</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getTh1t()
	 * @generated
	 * @ordered
	 */
	protected static final double TH1T_EDEFAULT = 999.0;

	/**
	 * The cached value of the '{@link #getTh1t() <em>Th1t</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getTh1t()
	 * @generated
	 * @ordered
	 */
	protected double th1t = TH1T_EDEFAULT;

	/**
	 * The default value of the '{@link #getTh2t() <em>Th2t</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getTh2t()
	 * @generated
	 * @ordered
	 */
	protected static final double TH2T_EDEFAULT = 999.0;

	/**
	 * The cached value of the '{@link #getTh2t() <em>Th2t</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getTh2t()
	 * @generated
	 * @ordered
	 */
	protected double th2t = TH2T_EDEFAULT;

	/**
	 * The default value of the '{@link #getUVRelayTimer1() <em>UV Relay Timer1</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getUVRelayTimer1()
	 * @generated
	 * @ordered
	 */
	protected static final double UV_RELAY_TIMER1_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getUVRelayTimer1() <em>UV Relay Timer1</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getUVRelayTimer1()
	 * @generated
	 * @ordered
	 */
	protected double uVRelayTimer1 = UV_RELAY_TIMER1_EDEFAULT;

	/**
	 * The default value of the '{@link #getUVRelayTimer2() <em>UV Relay Timer2</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getUVRelayTimer2()
	 * @generated
	 * @ordered
	 */
	protected static final double UV_RELAY_TIMER2_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getUVRelayTimer2() <em>UV Relay Timer2</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getUVRelayTimer2()
	 * @generated
	 * @ordered
	 */
	protected double uVRelayTimer2 = UV_RELAY_TIMER2_EDEFAULT;

	/**
	 * The default value of the '{@link #getAcStallTimer() <em>Ac Stall Timer</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getAcStallTimer()
	 * @generated
	 * @ordered
	 */
	protected static final double AC_STALL_TIMER_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getAcStallTimer() <em>Ac Stall Timer</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getAcStallTimer()
	 * @generated
	 * @ordered
	 */
	protected double acStallTimer = AC_STALL_TIMER_EDEFAULT;
    // Restartable motors can stall again after the non-restartable group has stopped.
    protected double acStallTimerB;

	/**
	 * The default value of the '{@link #getAcRestartTimer() <em>Ac Restart Timer</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getAcRestartTimer()
	 * @generated
	 * @ordered
	 */
	protected static final double AC_RESTART_TIMER_EDEFAULT = 0.0;

	/**
	 * The cached value of the '{@link #getAcRestartTimer() <em>Ac Restart Timer</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getAcRestartTimer()
	 * @generated
	 * @ordered
	 */
	protected double acRestartTimer = AC_RESTART_TIMER_EDEFAULT;
	
	/**
	 * The default value of the '{@link #getTv() <em>Tv</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getTv()
	 * @generated
	 * @ordered
	 */
	protected static final double TV_EDEFAULT = 0.02;

	/**
	 * The cached value of the '{@link #getTv() <em>Tv</em>}' attribute.
	 * <!-- begin-user-doc -->
	 * <!-- end-user-doc -->
	 * @see #getTv()
	 * @generated
	 * @ordered
	 */
	protected double tv = TV_EDEFAULT;
	protected double tf = 0.05;

	protected double fcon  = 1.0;  // fraction not tripped by the contractor
	
	// the aggregate motor model is represnted as two types of motors, A - non-restartable, B - restartable after voltage recovery
	
	protected double fthA = 1.0;  // fraction not tripped by the motor A thermal protection
	
	protected double fthB = 1.0;  // fraction not tripped by the motor B thermal protection
	
	protected double tempA = 0.0; // Temperature of the motor A
	
	protected double tempB = 0.0; // Temperature of the motor B
	
	
	protected double complf = 1.0;
	
	private double Gstall = 0.0, Bstall = 0.0;
	private Complex YStall = null;
	
	
	private Complex PQmotor = null;
	
	private double vstallbrk = 0.0;
	private double 	pac_a = 0.0, qac_a = 0.0, pac_b = 0.0, qac_b = 0.0;
	private double p0=0, q0=0;
	private double kuv =1.0, kcon = 1.0, fcon_trip = 0.0;
	private boolean isContractorActioned = false;
	private double I_CONV_FACTOR_M2S = 0.0;
	
	// internal variables for integration
	private double vt_measured = 0.0;
	private double vt_measured_old = 0.0;
	private double freq_measured = 1.0;
	private double freq_measured_old = 1.0;
	
	private double tempA_old = 0.0;
	private double tempB_old = 0.0;
	
	private double dv_dt0 = 0.0;
	private double dv_dt1 = 0.0;
	private double df_dt0 = 0.0;
	private double df_dt1 = 0.0;

	private double dThA_dt0 = 0.0;
	private double dThB_dt0 = 0.0;
	private double dThA_dt1 = 0.0;
	private double dThB_dt1 = 0.0;

	// thermal protection
	// the tripping characterisic is modeled as y = thermalEqnCoeff1*x +thermalEqnCoeff2 for x within {Th1t, Th2t}
	// here x is the internal temperature, y is the output for determining thermal tripping
	// when x>Th1t, starting trippinng, when x>Th2t, all will be tripped
	
	private double thermalEqnCoeff1 = -1.0/3;
	private double thermalEqnCoeff2 = 1.433;  // default value
	
	private int statusA =1, statusB = 1; // the operating status of the part A and B of the motor; 0 - stall, 1- running
	private double timestep = 0.0;
	
	private Hashtable<String, Object> states = null;
	private static final String OUT_SYMBOL_P ="ACMotorP";
	private static final String OUT_SYMBOL_Q ="ACMotorQ";
	private static final String OUT_SYMBOL_VT ="ACMotorVt";
	private static final String OUT_SYMBOL_STATE ="ACMotorState";
	private String extended_device_Id = "";
	
	double timeCounter= 0;

	public LD1PACImpl() {
		super();
        // The inherited accessor and all numerical equations share one field.
        this.loadFactor = LOAD_FACTOR_EDEFAULT;
		this.states = new Hashtable<>();
	}
	
	public LD1PACImpl(BaseDStabBus<?,?> dstabBus, String id) {
		this();
		this.id = id;
		
		this.setDStabBus(dstabBus);
		dstabBus.getDynLoadModelList().add(this);
	}

	

	@Override
	public boolean initStates() {
        prepareReactiveFrequencyScale();
        if (legacyCompositeCharacteristic && cmpKqf != 0 && reactiveFrequencyScale == 0)
            throw new IllegalArgumentException("Reactive frequency sensitivity requires a nonunity power factor");
        integrationMonitor.reset(); staggeredEvaluationCount = 0; timeCounter = 0;
        acceptedPhase = frequencyBus().getVoltageAng(); phaseSampleTime = 0;
        phaseFrequencyDeviation = 0; eventFrequencyDeviation = 0; frequencyEventPending = false;
        frequencyOutput = 1; lastFrequencyEventTime = Double.NaN;
        networkEventRatesPrepared = false;

        acStallTimer = 0; acStallTimerB = 0; acRestartTimer = 0;
        uVRelayTimer1 = 0; uVRelayTimer2 = 0;
        statusA = 1; statusB = 1; fthA = 1; fthB = 1;
        kuv = 1; kcon = 1; fcon_trip = 0;
        legacyUvTripped = false;
        initialEndpointAccepted = false;
        pendingSamplingEndpoint = Double.NaN;
        equivY = null; electricalNortonY = null; vstallbrk = 0;

        vstall = configuredVstall; vbrk = configuredVbrk;
        if (legacyCompositeCharacteristic) {
            p0 = 1 - kp1 * Math.pow(1 - vbrk, np1);
            q0 = Math.tan(Math.acos(powerFactor)) - kq1 * Math.pow(1 - vbrk, nq1);
            vstall += lFadj * (loadFactor - 1);
            vbrk += lFadj * (loadFactor - 1);
        }
		boolean flag = true;
        this.stage = 1; // initialized to running state
        
		this.Gstall = this.rstall/(this.rstall*this.rstall + this.xstall*this.xstall);
		this.Bstall = -this.xstall/(this.rstall*this.rstall + this.xstall*this.xstall);
		
		this.YStall = new Complex(this.Gstall, this.Bstall);
		
		//TODO the initLoad is the total load at the bus ,include constant Z and I load
		//In the future, this may need to be update to consider the constant P load only
        Complex busTotalLoad = this.getDStabBus().getInitLoad();
        if (parentLoad != null && !parentLoad.isActive()) return false;
        Complex allocatedPower = parentLoad == null
                ? new Complex(busTotalLoad.getReal()*this.loadPercent/100.0d,
                        busTotalLoad.getReal()*this.loadPercent/100.0d*Math.tan(Math.acos(this.powerFactor)))
                : parentLoad.getLoad(getDStabBus().getVoltageMag());
        if (allocatedPower == null || !Double.isFinite(allocatedPower.getReal())
                || !Double.isFinite(allocatedPower.getImaginary()) || allocatedPower.getReal() <= 0) return false;
        this.pac = allocatedPower.getReal();
        this.qac = allocatedPower.getImaginary();
        if (parentLoad != null) setLoadPercent(100 * pac / busTotalLoad.getReal());
		
		
		// pac and qac is the initial power
		this.setInitLoadPQ(new Complex(pac,qac));
		this.setLoadPQ(new Complex(pac,qac));
		
		// if mva is not defined and loading factor is available
        if(legacyCompositeCharacteristic || this.getMvaBase()==0.0){
			if(this.loadFactor >0 && this.loadFactor<=1.0)
				logger.debug("AC motor MVABase will be calculated based on load factor");
			else 
				this.loadFactor = 1.0;
			
            double initialPower = legacyCompositeCharacteristic
                    ? loadFactor * runningActiveCharacteristic(getDStabBus().getVoltageMag()) : loadFactor;
            this.setMvaBase(this.pac*this.getDStabBus().getNetwork().getBaseMva()/initialPower);
		}
		
		this.I_CONV_FACTOR_M2S = this.getMvaBase()/this.getDStabBus().getNetwork().getBaseMva();
				
		//Check whether a compensation is needed. If yes, calculate the compensation shuntY

		// if bus.loadQ < ld1pac.q, then compShuntB = ld1pac.q-bus.loadQ
		double vt = this.getDStabBus().getVoltageMag();
		
		this.vt_measured = vt;
		
		this.vt_measured_old = vt;
		this.freq_measured = frequencyInput();
		this.freq_measured_old = this.freq_measured;
		
		if(this.qac >busTotalLoad.getImaginary()){
			
			 double b = (this.qac - busTotalLoad.getImaginary())/vt/vt;
			 this.compensateShuntY = new Complex(0,b);
		}
		
		
		// Thermal and polynomial states are on the motor MVA base. Using the
		// system-base load current here understates I^2*R by the square of the
		// motor-to-system base ratio.
		double pac_mbase = this.pac*this.getDStabBus().getNetwork().getBaseMva()
				/this.getMvaBase();
		double qac_mbase = this.qac*this.getDStabBus().getNetwork().getBaseMva()
				/this.getMvaBase();
        if (legacyCompositeCharacteristic) {
            qac_mbase = runningReactiveCharacteristic(vt);
            compensateShuntY = new Complex(0, (qac_mbase * I_CONV_FACTOR_M2S - qac) / (vt * vt));
        }
		Complex Sac = new Complex(pac_mbase, qac_mbase);
		double i_motor = Sac.abs()/vt;
		
		this.tempA = i_motor*i_motor*this.rstall;
		this.tempB = this.tempA;
		
		this.tempA_old = this.tempA;
		this.tempB_old = this.tempB;
		
		// update the Vstall and Vbrk if necessary
        if (characteristicPolicy == CharacteristicPolicy.STANDALONE) {
            this.vstall= this.vstall*(1+this.lFadj*(this.loadFactor-1));
            this.vbrk = this.vbrk*(1+this.lFadj*(this.loadFactor-1));
        }
		
		// initialize the parts A and B of the motor
		this.pac_a = pac_mbase; 
		this.pac_b = pac_mbase;
		
		this.qac_a = qac_mbase; 
		this.qac_b = qac_mbase;
		
		
	   // Calculate the P0 and Q0 at stage 0
        if (!legacyCompositeCharacteristic) {
            this.p0 = pac_mbase - kp1*Math.pow((vt-vbrk),np1);
            this.q0 = qac_mbase - this.kq1*Math.pow((vt-vbrk),nq1);
        }
		
		
		
		double pst = 0.0, pac_calc = 0.0;
		for (double v = 0.4; v<vbrk; v+=0.0001){
			pst = this.Gstall*v*v;
			// Below Vbrk the running-motor characteristic is the Kp2/Np2
			// branch. Using Kp1 with a negative base makes non-integer Np1
			// produce NaN and leaves the impedance-transition voltage at zero.
            // The running low-voltage branch carries the same loading factor,
            // so it cancels when finding its intersection with the polynomial.
            pac_calc = runningActiveCharacteristic(v);
			
			if(pac_calc<pst){
				this.vstallbrk = v;
				break;
			}
			
		}
		
		if(this.vstallbrk>this.vstall){
			this.vstallbrk = this.vstall;
		}
		
		
		if(this.th1t >0 && this.th2t>this.th1t){
			this.thermalEqnCoeff1 = -1/(this.th2t - this.th1t);
			this.thermalEqnCoeff2 = this.th2t /(this.th2t - this.th1t);
		}
		
//		Complex loadPQFactor = calcLoadCharacterFactor();
//		
//		this.p = this.pac/loadPQFactor.getReal();
//		this.q = this.qac/loadPQFactor.getImaginary();
		
		this.PQmotor = new Complex(pac_mbase,qac_mbase);
		
		
		
		extended_device_Id = "ACMotor_"+this.getId()+"@"+this.getDStabBus().getId();
		this.states.put(DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID, extended_device_Id);
		this.setExtendedDeviceId(extended_device_Id);

        sampleElectricalInputs();
		
		return flag;
	}
	
	/**
	 * The thermal protection heating increase is modeled as 
	 * differential equation, thus it must be represented with the nextStep();
	 * 
     * Legacy timers share this integration owner; afterStep applies their discrete actions.
	 */
	@Override
    public boolean nextStep(double dt, DynamicSimuMethod method, int flag) {
        if (method != DynamicSimuMethod.MODIFIED_EULER)
            throw new IllegalArgumentException("Only modified Euler is supported by ordinary motor dispatch");
        if (!Double.isFinite(dt) || dt < 0 || flag < 0 || flag > 1)
            throw new IllegalArgumentException("Invalid motor interval or integration stage");
        evaluateRates(flag == 0 && dt > 0 ? vt_measured_old : vt_measured,
                flag == 0 && dt > 0 ? freq_measured_old : freq_measured, integrationRates, 0,
                flag == 0 ? IntegrationStateMonitor.Stage.PREDICTOR : IntegrationStateMonitor.Stage.CORRECTOR);
        if (dt == 0) return true;
        timestep = dt;
        if (flag == 0) {
            if (legacyCompositeCharacteristic) {
                readTimers(timerStart, 0);
                for (int i = 0; i < 5; i++) {
                    timerFirstRates[i] = integrationRates[i + 4];
                    timerTrial[i] = timerStart[i] + dt * timerFirstRates[i];
                }
                applyTimers(timerTrial, 0);
            }
            dv_dt0 = integrationRates[0]; df_dt0 = integrationRates[1];
            dThA_dt0 = integrationRates[2]; dThB_dt0 = integrationRates[3];
            vt_measured = tv > 1e-12 ? vt_measured_old + dv_dt0 * dt : voltageInput();
            freq_measured = tf > 1e-12 ? freq_measured_old + df_dt0 * dt : frequencyInput();
            tempA = tempA_old + dThA_dt0 * dt;
            tempB = tempB_old + dThB_dt0 * dt;
        } else {
            if (legacyCompositeCharacteristic) {
                for (int i = 0; i < 5; i++)
                    timerTrial[i] = timerStart[i] + .5 * dt * (timerFirstRates[i] + integrationRates[i + 4]);
                applyTimers(timerTrial, 0);
            }
            dv_dt1 = integrationRates[0]; df_dt1 = integrationRates[1];
            dThA_dt1 = integrationRates[2]; dThB_dt1 = integrationRates[3];
            vt_measured = tv > 1e-12 ? vt_measured_old + (dv_dt0 + dv_dt1) * .5 * dt : voltageInput();
            freq_measured = tf > 1e-12 ? freq_measured_old + (df_dt0 + df_dt1) * .5 * dt : frequencyInput();
            tempA = tempA_old + (dThA_dt0 + dThA_dt1) * .5 * dt;
            tempB = tempB_old + (dThB_dt0 + dThB_dt1) * .5 * dt;
            completeStaggeredStep(dt);
        }
        return true;
    }

	/**
	 *  to check whether AC motor stalls, restarts and the actions of the protections
      %  this should be perform after the network solution step
	 */
//	private boolean postProcessStep(double dt){
//		boolean flag = true;
//		/*
//		 % UV Relay
//         % Two levels of undervoltage load shedding can be represented: If the voltage drops
//         % below uvtr1 for ttr1 seconds, the fraction ?fuvtr? of the load is tripped; If the voltage drops below uvtr2
//         % for ttr2 seconds, the fraction ?fuvr? of the load is tripped
//		*/
//		
//		if(this.vt_measured <this.getUVtr1() && this.fuvr >0.0){
//			this.uVRelayTimer1 = this.uVRelayTimer1 +dt;
//		}
//		else
//			this.uVRelayTimer1 = 0.0;
//		
//		if(this.vt_measured <this.getUVtr2() && this.fuvr >0.0){
//			this.uVRelayTimer2 = this.uVRelayTimer2 +dt;
//		}
//		else
//			this.uVRelayTimer2 = 0.0;
//		
//		// trip the portion with under voltage relays
//		if(this.uVRelayTimer1> this.ttr1){
//			this.kuv = 1.0 - this.fuvr;
//		}
//		
//		if(this.uVRelayTimer2> this.ttr2){
//			this.kuv = 1.0 - this.fuvr;
//		}
//		
//		/*
//        % contractor
//        %  Contactor -- If the voltage drops to below Vc2off, all of the load is tripped; if the voltage is between
//        % Vc1off and Vc2off, a linear fraction of the load is tripped. If the voltage later recovers to above Vc2on, all
//        % of the motor is reconnected; if the voltage recovers to between Vc2on and Vc1on, a linear fraction of the
//        % load is reconnected.
//        */
//		
//		if(this.vt_measured < this.vc2off){
//			this.kcon  = 0.0;
//			this.fcon_trip = 1.0;
//		}
//		else if(this.vt_measured>=this.vc2off && this.vt_measured <this.vc1off){
//			this.kcon = (this.vt_measured - this.vc2off)/(this.vc1off- this.vc2off);
//			this.fcon_trip = 1.0 - this.kcon;
//			
//		}
//		
//		
//		if(this.vt_measured >=this.vc1on){
//			this.kcon = 1.0;
//		}
//		else if(this.vt_measured < this.vc1on && this.vt_measured >= this.vc2on){
//			double Frecv = (this.vt_measured - this.vc2on)/(this.vc1on- this.vc2on);
//			this.kcon  = 1 - this.fcon_trip*(1-Frecv);
//		}
//		
//		
//		/*
//		 * Thermal protection 
//		 */
//		
//		// update the timer for thermal protection
//		
//		double vmag = this.getDStabBus().getVoltage().abs();
//		
//		if (this.statusA ==1){
//			if(vmag < this.vstall){
//				this.acStallTimer = this.acStallTimer+ dt;
//			}
//			else
//				this.acStallTimer = 0.0;
//		}
//		
//		
//		if (this.statusB ==0){
//			if(vmag > this.vrst){
//				this.acRestartTimer = this.acRestartTimer+ dt;
//			}
//			else
//				this.acRestartTimer = 0.0;
//		}
//		
//		/*
//		 *  % update the status of the motor. transition from running to
//            % stalling is the same for the equivalent Motor A and B
//		 */
//		
//		if(this.acStallTimer > this.tstall && this.statusA ==1){
//			this.statusA = 0;
//			this.statusB = 0;
//			this.stage = 0;
//		}
//		
//		// considering AC restarting 
//		if(this.frst >0.0 && this.statusB ==0 && this.acRestartTimer > this.trst){
//			this.statusB = 1;
//			this.stage = 2;
//		}
//		
//		
//		/*
//		  % check whether AC motor will be trip next step, and what will be
//          % the remaining fraction;
//		 */
//		
//		if(this.thermalEqnCoeff1< 0.0){
//			if(this.tempA > this.th1t){
//				if(this.statusA == 0){
//					this.fthA = this.tempA*this.thermalEqnCoeff1 + this.thermalEqnCoeff2;
//					
//					if(this.fthA<0.0)
//						this.fthA = 0.0;
//				}
//			}
//			
//			if(this.tempB > this.th1t){
//				if(this.statusB == 0){
//					this.fthB = this.tempB*this.thermalEqnCoeff1 + this.thermalEqnCoeff2;
//					
//					if(this.fthB<0.0)
//						this.fthB = 0.0;
//				}
//			}
//		}
//		
//		
//		// Calculate the AC motor power
//
//		// call this method to update "this.PQmotor"
//		calculateMotorPower();
//		
//		if(vmag>1.0E-8)
//		    this.equivYpq = this.PQmotor.conjugate().divide(vmag*vmag); // on motor MVABase
//		else {
//			this.equivYpq = new Complex(1.0E4,1.0E4); // a large value
//			
//		}
//		return flag;
//	}
	
	/**
	 * calculate the motor power, return value is pu on motor mvabase;
	 * @return
	 */
	private Complex calculateMotorPower(){
		
		// Calculate the AC motor power
        double freq = frequencySignal();
        frequencyOutput = freq;
        double reactiveFrequencyGain = cmpKqf * reactiveFrequencyScale;
        double vmag = this.getDStabBus().getVoltageMag();
        evaluateIndividualPowers(vmag, freq, reactiveFrequencyGain);
        if (thermalBypassed()) projectAlgebraicTemperatures(getDStabBus().getVoltage(), vmag);

        // ac motor total power on motor base

        double Pmotor = this.pac_a*(1-this.frst)*this.fthA+this.pac_b*this.frst*this.fthB;
        double Qmotor = this.qac_a*(1-this.frst)*this.fthA+this.qac_b*this.frst*this.fthB;

        // consider the UV Relay,contractor as well as load change

        Pmotor = this.kuv*this.kcon*( 1.0 + this.accumulatedLoadChangeFactor)*Pmotor;
        Qmotor = this.kuv*this.kcon*( 1.0 + this.accumulatedLoadChangeFactor)*Qmotor;

        this.PQmotor = new Complex(Pmotor, Qmotor);

        double powerFactorM2S = getPowerConvFactorM2S();
        Complex terminalPower = legacyCompositeCharacteristic
                ? new Complex(Pmotor * powerFactorM2S, Qmotor * powerFactorM2S - compensateShuntY.getImaginary() * vmag * vmag)
                : this.PQmotor.multiply(powerFactorM2S);
        this.setLoadPQ(terminalPower);

        this.pac = Pmotor*getPowerConvFactorM2S();       // both are on system base
        this.qac = terminalPower.getImaginary();

        return this.PQmotor;
    }

    private boolean thermalBypassed() { return legacyCompositeCharacteristic && tth == 0; }

    private void refreshAlgebraicTemperatures(Complex voltage, double magnitude) {
        evaluateIndividualPowers(magnitude, frequencySignal(), cmpKqf * reactiveFrequencyScale);
        projectAlgebraicTemperatures(voltage, magnitude);
    }

    private void projectAlgebraicTemperatures(Complex voltage, double magnitude) {
        tempA = tempA_old = rstall * heatingCurrentSquared(voltage, magnitude, statusA, pac_a, qac_a, fthA);
        tempB = tempB_old = rstall * heatingCurrentSquared(voltage, magnitude, statusB, pac_b, qac_b, fthB);
    }

    private void evaluateIndividualPowers(double vmag, double freq, double reactiveFrequencyGain) {
		if(this.statusA == 1){
			if(vmag >= this.vbrk){
                this.pac_a  = (p0 +kp1*Math.pow((vmag-vbrk),np1))*(1+this.cmpKpf*(freq-1.0))*(legacyCompositeCharacteristic ? loadFactor : 1);
                this.qac_a = (q0 +kq1*Math.pow((vmag-vbrk),nq1))*(1+reactiveFrequencyGain*(freq-1.0));
				
			}
			else if(vmag <this.vbrk && vmag > this.vstallbrk){
                this.pac_a = (p0 +kp2*Math.pow((vbrk-vmag),np2))*(1+this.cmpKpf*(freq-1.0))*(legacyCompositeCharacteristic ? loadFactor : 1);
                this.qac_a = (q0 +kq2*Math.pow((vbrk-vmag),nq2))*(1+reactiveFrequencyGain*(freq-1.0));
			}
			else{
                this.pac_a = this.Gstall*vmag*vmag*(legacyCompositeCharacteristic ? loadFactor*(1+cmpKpf*(freq-1)) : 1);
                this.qac_a = -this.Bstall*vmag*vmag*(legacyCompositeCharacteristic ? 1+reactiveFrequencyGain*(freq-1) : 1);
			}
			
		}
		// stall
		else {
			this.pac_a =this.Gstall*vmag*vmag;
			this.qac_a =-this.Bstall*vmag*vmag;
		}
		
		if(this.frst> 0.0){
			
			if(this.statusB == 1){
				if(vmag >= this.vbrk){
                    this.pac_b  = (p0 +kp1*Math.pow((vmag-vbrk),np1))*(1+this.cmpKpf*(freq-1.0))*(legacyCompositeCharacteristic ? loadFactor : 1);
                    this.qac_b = (q0 +kq1*Math.pow((vmag-vbrk),nq1))*(1+reactiveFrequencyGain*(freq-1.0));
					
				}
				else if(vmag <this.vbrk && vmag > this.vstallbrk){
                    this.pac_b = (p0 +kp2*Math.pow((vbrk-vmag),np2))*(1+this.cmpKpf*(freq-1.0))*(legacyCompositeCharacteristic ? loadFactor : 1);
                    this.qac_b = (q0 +kq2*Math.pow((vbrk-vmag),nq2))*(1+reactiveFrequencyGain*(freq-1.0));
				}
				else{
                    this.pac_b = this.Gstall*vmag*vmag*(legacyCompositeCharacteristic ? loadFactor*(1+cmpKpf*(freq-1)) : 1);
                    this.qac_b = -this.Bstall*vmag*vmag*(legacyCompositeCharacteristic ? 1+reactiveFrequencyGain*(freq-1) : 1);
				}
				
			}
			// stall
			else {
				this.pac_b =this.Gstall*vmag*vmag;
				this.qac_b =-this.Bstall*vmag*vmag;
			}
		}
		
    }

    private double runningActiveCharacteristic(double voltage) {
        return voltage >= vbrk ? p0 + kp1 * Math.pow(voltage - vbrk, np1)
                : p0 + kp2 * Math.pow(vbrk - voltage, np2);
    }
    private double runningReactiveCharacteristic(double voltage) {
        return voltage >= vbrk ? q0 + kq1 * Math.pow(voltage - vbrk, nq1)
                : q0 + kq2 * Math.pow(vbrk - voltage, nq2);
    }
	
	/**
	 * calculation the factor for converting current from motor mvabase to system vabase
	 * @return
	 */
	private double getCurrConvFactorM2S(){
		return this.I_CONV_FACTOR_M2S = this.getMvaBase()/this.getDStabBus().getNetwork().getBaseMva();
	}
	
	/**
	 * calculation the factor for converting current from motor mvabase to system vabase
	 * @return
	 */
	private double getPowerConvFactorM2S(){
		return this.getMvaBase()/this.getDStabBus().getNetwork().getBaseMva();
	}
	
	
	@Override
	public Complex getPosSeqEquivY() {
		Complex zstall = new Complex(this.rstall,this.xstall);
		Complex y = new Complex(1.0,0).divide(zstall);
        electricalNortonY = y.multiply(getMvaBase()/this.getDStabBus().getNetwork().getBaseMva());
        this.equivY = legacyCompositeCharacteristic ? electricalNortonY.add(compensateShuntY) : electricalNortonY;
		
		return this.equivY;
	}
	
	@Override
	public Complex getNortonCurInj() {
		Complex vt = this.getDStabBus().getVoltage();
		
        // Every trial voltage needs a fresh constitutive current. A cached
        // low-voltage admittance can otherwise survive a branch or stall change.
        calculateMotorPower();
        if (vt.getReal() == 0 && vt.getImaginary() == 0) {
            evaluatedCurrent[0] = 0;
            evaluatedCurrent[1] = 0;
        } else {
            MotorElectricalKernel.divide(PQmotor.getReal(), PQmotor.getImaginary(),
                    vt.getReal(), vt.getImaginary(), evaluatedCurrent);
        }

		if(this.equivY==null) getPosSeqEquivY();
		
        Complex y = legacyCompositeCharacteristic ? electricalNortonY : equivY;
        this.nortonCurInj = new Complex(
                y.getReal()*vt.getReal() - y.getImaginary()*vt.getImaginary() - evaluatedCurrent[0]*I_CONV_FACTOR_M2S,
                y.getReal()*vt.getImaginary() + y.getImaginary()*vt.getReal() + evaluatedCurrent[1]*I_CONV_FACTOR_M2S);
		
		 
	 	  if(this.nortonCurInj.isNaN()) {
	 		  System.out.println("Vt, Power, load_change_fraction = "+vt.toString()+", "+this.PQmotor.toString()+", "+this.accumulatedLoadChangeFactor);
	 		  throw new Error("Current injection is NaN @"+extendedDeviceId);
	 	  }
				
		return this.nortonCurInj;
	}
	
	@Override
	public Object getOutputObject() {
	     return this.getNortonCurInj();
	}
	

	@Override
	public boolean updateAttributes(boolean netChange) {
		//System.out.println("Time counter = "+timeCounter+","+this.extended_device_Id);
		return true;
	}
	
	@Override
	public boolean afterStep(double dt) {
        if (!Double.isFinite(dt) || dt < 0)
            throw new IllegalArgumentException("An accepted compressor interval must be finite and nonnegative");
        if (dt == 0) return true;
        networkEventRatesPrepared = false;
        double protectionVoltage = legacyCompositeCharacteristic ? getDStabBus().getVoltageMag() : vt_measured;
        boolean uvEnabled = fuvr > 0 && !(legacyCompositeCharacteristic && legacyUvTripped);
        if (legacyCompositeCharacteristic) {
            roundAcceptedTimers();
            projectTimerPickups(protectionVoltage);
        }
		/**
		 *  to check whether AC motor stalls, restarts and the actions of the protections
	      %  this should be perform after the network solution step
		 */
			
		
			boolean flag = true;
			/*
			 % UV Relay
	         % Two levels of undervoltage load shedding can be represented: If the voltage drops
	         % below uvtr1 for ttr1 seconds, the fraction ?fuvtr? of the load is tripped; If the voltage drops below uvtr2
	         % for ttr2 seconds, the fraction ?fuvr? of the load is tripped
			*/
			
            if (protectionVoltage < getUVtr1() && uvEnabled) {
                if (!legacyCompositeCharacteristic) this.uVRelayTimer1 = advanceAcceptedTimer(uVRelayTimer1, dt);
			}
			else
				this.uVRelayTimer1 = 0.0;
			
            if (protectionVoltage < getUVtr2() && uvEnabled) {
                if (!legacyCompositeCharacteristic) this.uVRelayTimer2 = advanceAcceptedTimer(uVRelayTimer2, dt);
			}
			else
				this.uVRelayTimer2 = 0.0;
			
			// trip the portion with under voltage relays
			if(this.uVRelayTimer1> this.ttr1){
				this.kuv = 1.0 - this.fuvr;
			}
			
			if(this.uVRelayTimer2> this.ttr2){
				this.kuv = 1.0 - this.fuvr;
			}

            if (legacyCompositeCharacteristic && uvEnabled
                    && (uVRelayTimer1 > ttr1 || uVRelayTimer2 > ttr2)) {
                legacyUvTripped = true;
                uVRelayTimer1 = uVRelayTimer2 = 0;
            }
			
			/*
	        % contractor
	        %  Contactor -- If the voltage drops to below Vc2off, all of the load is tripped; if the voltage is between
	        % Vc1off and Vc2off, a linear fraction of the load is tripped. If the voltage later recovers to above Vc2on, all
	        % of the motor is reconnected; if the voltage recovers to between Vc2on and Vc1on, a linear fraction of the
	        % load is reconnected.
	        */
			
            if (legacyCompositeCharacteristic) updateLegacyContactor(protectionVoltage);
            else {
			if(this.vt_measured < this.vc2off){
				this.kcon  = 0.0;
				this.fcon_trip = 1.0;
			}
			else if(this.vt_measured>=this.vc2off && this.vt_measured <this.vc1off){
				this.kcon = (this.vt_measured - this.vc2off)/(this.vc1off- this.vc2off);
				this.fcon_trip = 1.0 - this.kcon;
				
			}
			
			
			if(this.vt_measured >=this.vc1on){
				this.kcon = 1.0;
			}
			else if(this.vt_measured < this.vc1on && this.vt_measured >= this.vc2on){
				double Frecv = (this.vt_measured - this.vc2on)/(this.vc1on- this.vc2on);
				this.kcon  = 1 - this.fcon_trip*(1-Frecv);
			}
            }
			
			
			/*
			 * Thermal protection 
			 */
			
			// update the timer for thermal protection
			
			double vmag = this.getDStabBus().getVoltage().abs();
			
            if (!legacyCompositeCharacteristic) {
                acStallTimer = statusA == 1 && vmag < vstall ? advanceAcceptedTimer(acStallTimer, dt) : 0;
                acStallTimerB = frst > 0 && statusB == 1 && vmag < vstall ? advanceAcceptedTimer(acStallTimerB, dt) : 0;
                acRestartTimer = frst > 0 && statusB == 0 && vmag > vrst ? advanceAcceptedTimer(acRestartTimer, dt) : 0;
            }
			
			/*
			 *  % update the status of the motor. transition from running to
	            % stalling is the same for the equivalent Motor A and B
			 */
			
			if(this.acStallTimer > this.tstall && this.statusA ==1){
				this.statusA = 0;

                acStallTimer = 0;
                if (frst <= 0) statusB = 0;
				this.stage = 0;
			}

            if (statusB == 1 && frst > 0 && acStallTimerB > tstall) {
                statusB = 0; acStallTimerB = 0;
                if (statusA == 0) stage = 0;
            }
			
			// considering AC restarting 
            if (frst > 0 && statusB == 0 && acRestartTimer > trst
                    && (!legacyCompositeCharacteristic || fthB > 0)) {
				this.statusB = 1;
                acRestartTimer = 0; acStallTimerB = 0;
				this.stage = 2;
			}
			
			
			/*
			  % check whether AC motor will be trip next step, and what will be
	          % the remaining fraction;
			 */
			
            if (legacyCompositeCharacteristic) updateLegacyThermalProtection();
            else if(this.thermalEqnCoeff1< 0.0){
				if(this.tempA > this.th1t){
					if(this.statusA == 0){
						this.fthA = this.tempA*this.thermalEqnCoeff1 + this.thermalEqnCoeff2;
						
						if(this.fthA<0.0)
							this.fthA = 0.0;
					}
				}
				
				if(this.tempB > this.th1t){
					if(this.statusB == 0){
						this.fthB = this.tempB*this.thermalEqnCoeff1 + this.thermalEqnCoeff2;
						
						if(this.fthB<0.0)
							this.fthB = 0.0;
					}
				}
			}
			
			
			// Calculate the AC motor power

			// call this method to update "this.PQmotor"
			calculateMotorPower();
			
            sampleElectricalInputs();
            acceptPhaseSample(dt);
			return flag;
	}
	
	//The following calculation is  based on the AC motor model specification in PSS/E doc
	private Complex calcLoadCharacterFactor(){
		
		double v = this.getDStabBus().getVoltage().abs();
		// exponential factor

		double pfactor = 0.0, qfactor = 0.0, pfactorStall = 0.0, qfactorStall = 0.0;
		
		if(v>this.vbrk  & stage == 0){
			pfactor  = p0 +kp1*Math.pow((v-vbrk),np1);
			qfactor  = q0 +kq1*Math.pow((v-vbrk),nq1);
	    }
		//TODO change from "v<Vbrk & v> Vstall & stage == 0 "to  "v<Vbrk & v> Vc1off & stage ==0"
		else if( v<vbrk & v> vstall & stage == 0){ //
			pfactor = p0 +kp2*Math.pow((vbrk-v),np2);
			qfactor = q0 +kq2*Math.pow((vbrk-v),nq2);
			pfactorStall = pfactor;
			qfactorStall = qfactor;
		}
		// when v<= Vstall and stage = 0, the same pq factor, which is calculated in the last step
		// before v<Vstall, will be used
		
		if(v<=vstall & stage == 0){
			pfactor = pfactorStall*(v/vstall)*(v/vstall);
			qfactor = qfactorStall*(v/vstall)*(v/vstall);
		}
		
		// consider the frequency dependence
		if(pfactor !=0.0 ||qfactor!=0){
			double dFreq = freq_measured-1.0;
			pfactor = pfactor*(1+cmpKpf*dFreq);
			qfactor = qfactor*(1+cmpKqf*dFreq/Math.sqrt(1-powerFactor*powerFactor));
		}
		
       //System.out.println("Voltage, PQFactor :"+v+","+ pfactor+","+qfactor);
		
		return new Complex(pfactor,qfactor);
	}
   
	
	@Override
	public Hashtable<String, Object> getStates(Object ref) {
		states.putAll(getNamedStates());
		states.put(OUT_SYMBOL_P, this.getPac());
		states.put(OUT_SYMBOL_Q, this.getQac());
		states.put(OUT_SYMBOL_VT, this.getDStabBus().getVoltage().abs());
		states.put(OUT_SYMBOL_STATE, stage==1?0:1);
		return this.states;
	}

	@Override
	public String toString() {
		if (eIsProxy()) return super.toString();

		StringBuilder result = new StringBuilder(super.toString());
		result.append(" (stage: ");
		result.append(stage);
		result.append(", p0: ");
		result.append(p0);
		result.append(", q0: ");
		result.append(q0);
		result.append(", pac: ");
		result.append(pac);
		result.append(", qac: ");
		result.append(qac);
		result.append(", powerFactor: ");
		result.append(powerFactor);
		result.append(", vstall: ");
		result.append(vstall);
		result.append(", rstall: ");
		result.append(rstall);
		result.append(", xstall: ");
		result.append(xstall);
		result.append(", tstall: ");
		result.append(tstall);
		result.append(", lFadj: ");
		result.append(lFadj);
		result.append(", kp1: ");
		result.append(kp1);
		result.append(", np1: ");
		result.append(np1);
		result.append(", kq1: ");
		result.append(kq1);
		result.append(", nq1: ");
		result.append(nq1);
		result.append(", kp2: ");
		result.append(kp2);
		result.append(", np2: ");
		result.append(np2);
		result.append(", kq2: ");
		result.append(kq2);
		result.append(", nq2: ");
		result.append(nq2);
		result.append(", vbrk: ");
		result.append(vbrk);
		result.append(", frst: ");
		result.append(frst);
		result.append(", vrst: ");
		result.append(vrst);
		result.append(", trst: ");
		result.append(trst);
		result.append(", cmpKpf: ");
		result.append(cmpKpf);
		result.append(", cmpKqf: ");
		result.append(cmpKqf);
		result.append(", fuvr: ");
		result.append(fuvr);
		result.append(", vtr1: ");
		result.append(uvtr1);
		result.append(", ttr1: ");
		result.append(ttr1);
		result.append(", vtr2: ");
		result.append(uvtr2);
		result.append(", ttr2: ");
		result.append(ttr2);
		result.append(", vc1off: ");
		result.append(vc1off);
		result.append(", vc2off: ");
		result.append(vc2off);
		result.append(", vc1on: ");
		result.append(vc1on);
		result.append(", vc2on: ");
		result.append(vc2on);
		result.append(", tth: ");
		result.append(tth);
		result.append(", th1t: ");
		result.append(th1t);
		result.append(", th2t: ");
		result.append(th2t);
		result.append(", uVRelayTimer1: ");
		result.append(uVRelayTimer1);
		result.append(", uVRelayTimer2: ");
		result.append(uVRelayTimer2);
		result.append(", acStallTimer: ");
		result.append(acStallTimer);
		result.append(", acRestartTimer: ");
		result.append(acRestartTimer);
		result.append(", tv: ");
		result.append(tv);
		result.append(')');
		return result.toString();
	}
	
	@Override
	public boolean changeLoad(double factor) {
		if (factor < -1.0){
			logger.error("percentageFactor < -1.0, this change will not be applied");
			return false;
		}
		if (this.accumulatedLoadChangeFactor <= -1.0 &&  factor < 0.0)
			logger.error("this.accumulatedLoadChangeFactor<=-1.0 and percentageFactor < 0.0, this change will not be applied");
		
		this.accumulatedLoadChangeFactor = this.accumulatedLoadChangeFactor + factor;
		
		if (this.accumulatedLoadChangeFactor < -1.0){
			logger.error("the accumulatedLoadChangeFactor is less than -1.0 after this change, so the accumulatedLoadChangeFactor is reset to -1.0");
			this.accumulatedLoadChangeFactor = -1.0;
		}
		logger.info("accumulated Load Change Factor = "+ this.accumulatedLoadChangeFactor);
		
		return true;
	}
	


	public int getStage() {
		return stage;
	}

	public void setStage(int newStage) {
		stage = newStage;
	}
	

	public double getPac() {
		return pac;
	}

	public void setPac(double newPac) {
		pac = newPac;
	}

	public double getQac() {
		return qac;
	}

	public void setQac(double newQac) {
		qac = newQac;
	}

	public double getPowerFactor() {
		return powerFactor;
	}

	public void setPowerFactor(double newPowerFactor) {
        if (PQmotor != null && legacyCompositeCharacteristic
                && cmpKqf != 0 && Math.abs(newPowerFactor) >= 1)
            throw new IllegalArgumentException("Reactive frequency sensitivity requires a nonunity power factor");
		powerFactor = newPowerFactor;
        prepareReactiveFrequencyScale();
	}

	public double getVstall() {
		return vstall;
	}

	public void setVstall(double newVstall) {
		vstall = newVstall;
        configuredVstall = newVstall;
	}

	public double getRstall() {
		return rstall;
	}

	public void setRstall(double newRstall) {
		rstall = newRstall;
	}

	public double getXstall() {
		return xstall;
	}

	public void setXstall(double newXstall) {
		xstall = newXstall;
	}

	public double getTstall() {
		return tstall;
	}

	public void setTstall(double newTstall) {
		tstall = newTstall;
	}

	public double getLFadj() {
		return lFadj;
	}

	public void setLFadj(double newLFadj) {
		lFadj = newLFadj;
	}

	public double getKp1() {
		return kp1;
	}

	public void setKp1(double newKp1) {
		kp1 = newKp1;
	}

	public double getNp1() {
		return np1;
	}

	public void setNp1(double newNp1) {
		np1 = newNp1;
	}

	public double getKq1() {
		return kq1;
	}

	public void setKq1(double newKq1) {
		kq1 = newKq1;
	}

	public double getNq1() {
		return nq1;
	}

	public void setNq1(double newNq1) {
		nq1 = newNq1;
	}

	public double getKp2() {
		return kp2;
	}

	public void setKp2(double newKp2) {
		kp2 = newKp2;
	}

	public double getNp2() {
		return np2;
	}

	public void setNp2(double newNp2) {
		np2 = newNp2;
	}

	public double getKq2() {
		return kq2;
	}

	public void setKq2(double newKq2) {
		kq2 = newKq2;
	}

	public double getNq2() {
		return nq2;
	}

	public void setNq2(double newNq2) {
		nq2 = newNq2;
	}

	public double getVbrk() {
		return vbrk;
	}

	public void setVbrk(double newVbrk) {
		vbrk = newVbrk;
        configuredVbrk = newVbrk;
	}

	public double getFrst() {
		return frst;
	}

	public void setFrst(double newFrst) {
		frst = newFrst;
	}

	public double getVrst() {
		return vrst;
	}

	public void setVrst(double newVrst) {
		vrst = newVrst;
	}

	public double getTrst() {
		return trst;
	}

	public void setTrst(double newTrst) {
		trst = newTrst;
	}

	public double getCmpKpf() {
		return cmpKpf;
	}

	public void setCmpKpf(double newCmpKpf) {
		cmpKpf = newCmpKpf;
	}

	public double getCmpKqf() {
		return cmpKqf;
	}

	public void setCmpKqf(double newCmpKqf) {
        if (PQmotor != null && legacyCompositeCharacteristic
                && newCmpKqf != 0 && reactiveFrequencyScale == 0)
            throw new IllegalArgumentException("Reactive frequency sensitivity requires a nonunity power factor");
		cmpKqf = newCmpKqf;
	}

	public double getFuvr() {
		return fuvr;
	}

	public void setFuvr(double newFuvr) {
		fuvr = newFuvr;
	}

	public double getUVtr1() {
		return uvtr1;
	}

	public void setUVtr1(double newVtr1) {
		uvtr1 = newVtr1;
	}

	public double getTtr1() {
		return ttr1;
	}

	public void setTtr1(double newTtr1) {
		ttr1 = newTtr1;
	}

	public double getUVtr2() {
		return uvtr2;
	}

	public void setUVtr2(double newUVtr2) {
		uvtr2 = newUVtr2;
	}

	public double getTtr2() {
		return ttr2;
	}

	public void setTtr2(double newTtr2) {
		ttr2 = newTtr2;
	}

	public double getVc1off() {
		return vc1off;
	}

	public void setVc1off(double newVc1off) {
		vc1off = newVc1off;
	}

	public double getVc2off() {
		return vc2off;
	}

	public void setVc2off(double newVc2off) {
		vc2off = newVc2off;
	}

	public double getVc1on() {
		return vc1on;
	}

	public void setVc1on(double newVc1on) {
		vc1on = newVc1on;
	}

	public double getVc2on() {
		return vc2on;
	}

	public void setVc2on(double newVc2on) {
		vc2on = newVc2on;
	}

	public double getTth() {
		return tth;
	}

	public void setTth(double newTth) {
		tth = newTth;
	}

	public double getTh1t() {
		return th1t;
	}

	public void setTh1t(double newTh1t) {
		th1t = newTh1t;
	}

	public double getTh2t() {
		return th2t;
	}

	public void setTh2t(double newTh2t) {
		th2t = newTh2t;
	}

	public double getUVRelayTimer1() {
		return uVRelayTimer1;
	}

	public void setUVRelayTimer1(double newUVRelayTimer1) {
		uVRelayTimer1 = newUVRelayTimer1;
	}

	public double getUVRelayTimer2() {
		return uVRelayTimer2;
	}

	public void setUVRelayTimer2(double newUVRelayTimer2) {
		uVRelayTimer2 = newUVRelayTimer2;
	}

	public double getAcStallTimer() {
		return acStallTimer;
	}

	public void setAcStallTimer(double newAcStallTimer) {
		acStallTimer = newAcStallTimer;
	}

	public double getAcRestartTimer() {
		return acRestartTimer;
	}

	public void setAcRestartTimer(double newAcRestartTimer) {
		acRestartTimer = newAcRestartTimer;
	}

	public double getTv() {
		return tv;
	}

	public void setTv(double newTv) {
		tv = newTv;
	}

	@Override public double getTf() { return tf; }
	@Override public void setTf(double value) {
		if (!Double.isFinite(value) || value < 0.0) throw new IllegalArgumentException("Tf must be non-negative");
		tf = value;
	}
	@Override public double getMeasuredVoltage() { return vt_measured; }
	@Override public double getMeasuredFrequency() { return freq_measured; }

	/**
	 * Uses a transmission-side frequency for a motor embedded behind algebraic
	 * distribution branches. Standalone motors continue to use their own bus.
	 */
	public void setFrequencySourceBus(BaseDStabBus<?, ?> bus) {
		this.frequencySourceBus = bus;
	}

	private double frequencyInput() {
        if (frequencyInputMode != FrequencyInputMode.BUS_FREQUENCY)
            return 1 + (frequencyEventPending && tf > 1e-12 ? .5 * eventFrequencyDeviation : phaseFrequencyDeviation);
        return frequencyBus().getFreq();
	}

	@Override
	public Map<String, Double> getNamedStates() {
		Map<String, Double> named = new LinkedHashMap<>();
		named.put("Vmeas", vt_measured);
		named.put("Fmeas", freq_measured);
        named.put("FrequencyOutput", frequencyOutput);
		named.put("TemperatureA", tempA);
		named.put("TemperatureB", tempB);
		named.put("UvTimer1", uVRelayTimer1);
		named.put("UvTimer2", uVRelayTimer2);
		named.put("StallTimer", acStallTimer);
        named.put("StallTimerB", acStallTimerB);
		named.put("RestartTimer", acRestartTimer);
        named.put("RunningP0", p0); named.put("RunningQ0", q0);
		return Map.copyOf(named);
	}

} //LD1PACImpl
