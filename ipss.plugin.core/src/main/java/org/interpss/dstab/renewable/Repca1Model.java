package org.interpss.dstab.renewable;

import org.interpss.dstab.control.util.IntegrationStateAccess;
import org.interpss.dstab.control.util.IntegrationStateMonitor;
import org.interpss.dstab.control.util.IntegrationStateMonitor.Stage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.controller.cml.ICMLStateProvider;
import org.interpss.numeric.datatype.Unit.UnitType;

/**
 * WECC REPC_A plant controller. Its outputs are incremental Pref/Qext commands
 * consumed by a REEC_A or REEC_B controller; all internal powers are on converter MVA base.
 */
public final class Repca1Model implements ICMLStateProvider, IntegrationStateAccess {
    public enum MeasurementSource {
        EXTERNAL, MONITORED_BRANCH, GENERATOR_POWER_ZERO_IDENTIFIERS, GENERATOR_POWER_MISSING_BRANCH
    }
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(Repca1Model.class);
    private static final double EPS = 1.0e-9;
    /** Suppress only solver partitioning roundoff at the initialized equilibrium. */
    private static final double EQUILIBRIUM_RESIDUAL = 1.0e-8;

    private final IntegrationStateMonitor stateMonitor = new IntegrationStateMonitor(
            "Measured Active Power", "Measured Reactive Or Voltage", "Active Control Integral", "Reactive Control Integral", "Lead Lag", "Active Power Lag");
    private final Repca1Data data;
    private final Regca1Model converter;
    private final Regcb1Model behindImpedanceConverter;
    private RenewableConverterHost additionalHost;
    private final Regfma1Model gridFormingConverter;
    private final Regfmb1Model virtualSynchronousConverter;
    private final boolean nativeRecordConvention;
    private double initialActiveReference;
    private double initialReactiveReference;
    private BaseDStabBus<?, ?> regulatedBus;
    private AclfBranch monitoredBranch;
    private boolean branchStoredInRequestedDirection;
    private boolean zeroBranchFallback;
    private boolean missingBranchFallback;
    private double systemBaseMva = 100.0;
    private double deviceBaseMva = 100.0;

    private double pext;
    private double qext;
    private double pMeasured;
    private double qOrVMeasured;
    private double pIntegral;
    private double qIntegral;
    private double qPiOutput;
    private double leadLagState;
    private double pLagState;
    private double pReference;
    private double qvReference;
    private ActivePowerResponseLimits responseLimits = ActivePowerResponseLimits.NORMAL;
    private double effectivePmax;
    private double effectivePmin;
    private double effectiveQmax;
    private double effectiveQmin;
    private PlantState predictorStart;
    private PlantDerivatives predictorDerivatives;
    private boolean initialized;
    private final TwoStepHistory twoStep = new TwoStepHistory(6);
    void setSinglePrecisionStates(boolean enabled) { twoStep.setSinglePrecision(enabled); }
    public void rebaseNextTwoStep() { twoStep.rebaseNextStep(); }
    /** Configure the absolute reactive-reference coordinate during initialization. */
    void setReactiveStateOffset(double initialReference) {
        initialReactiveReference = initialReference;
        // Native REPCA1 stores absolute Q-reference coordinates for s2/s3.
        // The controller API exposes increments; retain the physical storage
        // coordinate and translate the absolute limiter thresholds below.
        twoStep.setOffset(3,initialReference);
        twoStep.setOffset(4,initialReference);
        // QMAX/QMIN limit the absolute reference, not the increment returned
        // by this API. Translate both PI limiting and anti-windup thresholds.
        effectiveQmax=Math.max(data.qmax()-initialReference,0.0);
        effectiveQmin=Math.min(data.qmin()-initialReference,0.0);
    }

    public Repca1Model(Repca1Data data) {
        this(data, null, null, null);
    }

    public Repca1Model(Repca1Data data, Regca1Model converter) {
        this(data, converter, null, null);
    }

    public Repca1Model(Repca1Data data, Regcb1Model converter) {
        this(data, null, converter, null);
    }

    public Repca1Model(Repca1Data data, Regfma1Model converter) {
        this(data, null, null, converter);
    }

    private Repca1Model(Repca1Data data, Regca1Model converter,
            Regcb1Model behindImpedanceConverter,
            Regfma1Model gridFormingConverter) {
        this(data,converter,behindImpedanceConverter,gridFormingConverter,null);
    }

    public Repca1Model(Repca1Data data,Regfmb1Model converter) {
        this(data,null,null,null,converter);
    }

    private Repca1Model(Repca1Data data,Regca1Model converter,Regcb1Model behindImpedanceConverter,
            Regfma1Model gridFormingConverter,Regfmb1Model virtualSynchronousConverter) {
        this(data, converter, behindImpedanceConverter, gridFormingConverter, virtualSynchronousConverter, false);
    }

    private Repca1Model(Repca1Data data, Regca1Model converter, Regcb1Model behindImpedanceConverter,
            Regfma1Model gridFormingConverter, Regfmb1Model virtualSynchronousConverter, boolean nativeRecord) {
        this.data = java.util.Objects.requireNonNull(data);
        this.converter = converter;
        this.behindImpedanceConverter = behindImpedanceConverter;
        this.gridFormingConverter = gridFormingConverter;
        this.virtualSynchronousConverter=virtualSynchronousConverter;
        nativeRecordConvention = nativeRecord;
    }

    /** Native compensation and absolute-limit rules, separate from historical typed conventions. */
    public static Repca1Model fromNativeRecord(Repca1Data data) {
        return new Repca1Model(data, null, null, null, null, true);
    }
    public static Repca1Model fromNativeRecord(Repca1Data data, Regca1Model converter) {
        return new Repca1Model(data, converter, null, null, null, true);
    }
    public static Repca1Model fromNativeRecord(Repca1Data data, Regcb1Model converter) {
        return new Repca1Model(data, null, converter, null, null, true);
    }
    public static Repca1Model forConverterHost(Repca1Data data, RenewableConverterHost host) {
        var controller=fromNativeRecord(data);
        controller.additionalHost=java.util.Objects.requireNonNull(host);
        return controller;
    }
    public static Repca1Model fromNativeRecord(Repca1Data data, Regfma1Model converter) {
        return new Repca1Model(data, null, null, converter, null, true);
    }
    public static Repca1Model fromNativeRecord(Repca1Data data, Regfmb1Model converter) {
        return new Repca1Model(data, null, null, null, converter, true);
    }
    public boolean isNativeRecordConvention() { return nativeRecordConvention; }

    public ActivePowerResponseLimits getActivePowerResponseLimits() { return responseLimits; }
    public void setActivePowerResponseLimits(ActivePowerResponseLimits limits) {
        java.util.Objects.requireNonNull(limits);
        if (initialized && limits != responseLimits)
            throw new IllegalStateException("Configure response limits before initialization");
        responseLimits = limits;
    }
    public double getEffectiveActivePowerMaximum() { return effectivePmax; }
    public double getEffectiveActivePowerMinimum() { return effectivePmin; }

    public void initialize(double p, double q, double v) {
        resolveMeasurements();
        initializeMeasured(p, q, measure(p, q, v, 1.0));
    }

    /** Initialize from physical output references and an independently supplied measurement boundary. */
    public void initializeMeasured(double p, double q, Measurement measurement) {
        java.util.Objects.requireNonNull(measurement);
        if (!Double.isFinite(p) || !Double.isFinite(q))
            throw new IllegalArgumentException("Plant initial references must be finite");
        initialized = false;
        stateMonitor.reset();
        twoStep.reset();
        predictorStart = null;
        predictorDerivatives = null;
        pReference = pMeasured = measurement.p();
        qvReference = qOrVMeasured = reactiveMeasurement(measurement);

        // REPC_A outputs are increments into REEC_B, hence both PI paths must
        // initialize to zero even when measured P and Q are nonzero.
        pext = qext = pIntegral = qIntegral = qPiOutput = leadLagState = pLagState = 0.0;
        effectivePmax = Math.max(data.pmax(), 0.0);
        effectivePmin = Math.min(data.pmin(), 0.0);
        effectiveQmax = Math.max(data.qmax(), 0.0);
        effectiveQmin = Math.min(data.qmin(), 0.0);
        initialActiveReference = p;
        initialReactiveReference = q;
        if (nativeRecordConvention) {
            // Absolute native PI storage/output bounds become incremental API bounds.
            effectivePmax = Math.max(data.pmax() - p, 0.0);
            effectivePmin = Math.min(data.pmin() - p, 0.0);
            twoStep.setOffset(2, p);
            twoStep.setOffset(5, p);
            setReactiveStateOffset(q);
        }
        effectivePmax = responseLimits.upper(effectivePmax, 0.0);
        effectivePmin = responseLimits.lower(effectivePmin, 0.0);
        initialized = true;
    }

    public void step(double dt, double p, double q, double v, double frequency) {
        PlantState observationStart = state();
        Measurement measurement = measure(p, q, v, frequency);
        pMeasured = lag(pMeasured, activeMeasurement(measurement), data.tp(), dt);
        qOrVMeasured = lag(qOrVMeasured, reactiveMeasurement(measurement), data.tfltr(), dt);

        double qError = reactiveError(qOrVMeasured);
        // WECC/PowerWorld freeze the plant integrator below Vfrz; the proportional
        // path remains live, which is distinct from holding the entire output.
        boolean freezeIntegrator = measurement.vMag() < data.vfrz();
        qIntegral = integrateWithAntiWindup(qIntegral, data.ki(), qError, dt,
                data.kp(), effectiveQmin, effectiveQmax, freezeIntegrator);
        double qPi = limit(data.kp() * qError + qIntegral, effectiveQmin, effectiveQmax);
        qPiOutput = qPi;
        qext = leadLag(qPi, dt);

        if (data.fFlag() == 1) {
            double fError = deadband(1.0 - measuredFrequency(measurement),
                    data.fdbd1(), data.fdbd2());
            double droop = fError >= 0.0 ? data.dup() * fError : data.ddn() * fError;
            double pError = limit(pReference - pMeasured + droop, data.femin(), data.femax());
            pIntegral = integrateWithAntiWindup(pIntegral, data.kig(), pError, dt,
                    data.kpg(), effectivePmin, effectivePmax, false);
            double pPi = limit(data.kpg() * pError + pIntegral, effectivePmin, effectivePmax);
            pLagState = lag(pLagState, pPi, data.tg(), dt);
            pext = pLagState;
        } else {
            pext = pLagState = 0.0;
        }
        if (dt > 0.0) {
            stateMonitor.begin(Stage.APPLIED_STEP);
            stateMonitor.record(0, observationStart.pMeasured(), (data.tp() <= EPS ? 0.0 : (pMeasured-observationStart.pMeasured())/dt));
            stateMonitor.record(1, observationStart.qOrVMeasured(), (data.tfltr() <= EPS ? 0.0 : (qOrVMeasured-observationStart.qOrVMeasured())/dt));
            stateMonitor.record(2, observationStart.pIntegral(), (pIntegral-observationStart.pIntegral())/dt);
            stateMonitor.record(3, observationStart.qIntegral(), (qIntegral-observationStart.qIntegral())/dt);
            stateMonitor.record(4, observationStart.leadLagState(), (data.tfv() <= EPS ? 0.0 : (leadLagState-observationStart.leadLagState())/dt));
            stateMonitor.record(5, observationStart.pLagState(), (data.tg() <= EPS ? 0.0 : (pLagState-observationStart.pLagState())/dt));
        }
    }

    /**
     * Advances REPCA1 using the enclosing solver's modified-Euler stages.
     * Flag 0 stores the old endpoint and produces an explicit predictor; flag 1
     * evaluates the equations at the predicted network endpoint and applies the
     * trapezoidal correction. Algebraic bypasses are evaluated at the applicable
     * endpoint and are never integrated as artificial states.
     */
    public void step(double dt, double p, double q, double v, double frequency, int flag) {
        stepMeasured(dt, measure(p, q, v, frequency), flag);
    }

    /** Recorded-input or externally measured boundary; uses the same staged equations as network coupling. */
    public void stepMeasured(double dt, Measurement measurement, int flag) {
        if (flag != 0 && flag != 1) {
            throw new IllegalArgumentException("REPCA1 integration flag must be 0 or 1");
        }
        if (dt <= 0.0) {
            PlantState endpoint = applyBypasses(state(), measurement);
            apply(endpoint);
            updateStageOutputs(endpoint);
            return;
        }
        if (flag == 0) {
            predictorStart = state();
            predictorDerivatives = derivatives(predictorStart, measurement);
            retainRates(predictorStart, predictorDerivatives, Stage.PREDICTOR);
            apply(advance(predictorStart, predictorDerivatives, measurement, dt));
        } else if (flag == 1) {
            if (predictorStart == null || predictorDerivatives == null) {
                throw new IllegalStateException("REPCA1 corrector called without predictor");
            }
            PlantDerivatives correctedDerivatives = derivatives(state(), measurement);
            retainRates(state(), correctedDerivatives, Stage.CORRECTOR);
            apply(correct(predictorStart, predictorDerivatives,
                    correctedDerivatives, measurement, dt));
            predictorStart = null;
            predictorDerivatives = null;
        }
        updateStageOutputs(state());
    }

    private PlantDerivatives derivatives(PlantState state, Measurement measurement) {
        double pRate = lagRate(state.pMeasured(), activeMeasurement(measurement), data.tp());
        double qRate = lagRate(state.qOrVMeasured(), reactiveMeasurement(measurement),
                data.tfltr());

        double qError = reactiveError(state.qOrVMeasured());
        boolean freezeIntegrator = measurement.vMag() < data.vfrz();
        double qIntegralRate = integralRate(state.qIntegral(), data.ki(), qError,
                data.kp(), effectiveQmin, effectiveQmax, freezeIntegrator);
        double qPi = limit(data.kp() * qError + state.qIntegral(),
                effectiveQmin, effectiveQmax);
        double leadLagRate = lagRate(state.leadLagState(), qPi, data.tfv());

        if (data.fFlag() != 1) {
            return new PlantDerivatives(pRate, qRate, 0.0, qIntegralRate,
                    leadLagRate, 0.0);
        }
        double pError = activeError(state.pMeasured(), measurement.frequency());
        double pIntegralRate = integralRate(state.pIntegral(), data.kig(), pError,
                data.kpg(), effectivePmin, effectivePmax, false);
        double pPi = limit(data.kpg() * pError + state.pIntegral(),
                effectivePmin, effectivePmax);
        double pLagRate = lagRate(state.pLagState(), pPi, data.tg());
        return new PlantDerivatives(pRate, qRate, pIntegralRate, qIntegralRate,
                leadLagRate, pLagRate);
    }

    public void stepTwoStep(double dt, double p, double q, double v, double frequency) {
        stepTwoStepMeasured(dt, measure(p, q, v, frequency));
    }

    /** Explicit history integration with physical measurements, not injected controller states. */
    public void stepTwoStepMeasured(double dt, Measurement measurement) {
        PlantState start = applyBypasses(state(), measurement);
        if (dt > 0.0) {
            PlantDerivatives rate = derivatives(start, measurement);
            retainRates(start, rate, Stage.TWO_STEP);
            twoStep.begin(dt);
            apply(new PlantState(
                    twoStep.advance(0, start.pMeasured(), rate.pMeasured()),
                    twoStep.advance(1, start.qOrVMeasured(), rate.qOrVMeasured()),
                    twoStep.advance(2, start.pIntegral(), rate.pIntegral()),
                    measurement.vMag() < data.vfrz()
                            ? twoStep.hold(3, start.qIntegral())
                            : twoStep.advance(3, start.qIntegral(), rate.qIntegral()),
                    twoStep.advance(4, start.leadLagState(), rate.leadLagState()),
                    twoStep.advance(5, start.pLagState(), rate.pLagState())));
            twoStep.observeRates(stateMonitor);
        } else {
            apply(start);
        }
        apply(applyBypasses(state(), measurement));
        updateStageOutputs(state());
    }

    private PlantState advance(PlantState start, PlantDerivatives rate,
            Measurement measurement, double dt) {
        PlantState advanced = add(start, rate, dt);
        return applyBypasses(advanced, measurement);
    }

    private PlantState correct(PlantState start, PlantDerivatives first,
            PlantDerivatives second, Measurement measurement, double dt) {
        PlantDerivatives average = new PlantDerivatives(
                .5 * (first.pMeasured() + second.pMeasured()),
                .5 * (first.qOrVMeasured() + second.qOrVMeasured()),
                .5 * (first.pIntegral() + second.pIntegral()),
                .5 * (first.qIntegral() + second.qIntegral()),
                .5 * (first.leadLagState() + second.leadLagState()),
                .5 * (first.pLagState() + second.pLagState()));
        return applyBypasses(add(start, average, dt), measurement);
    }

    private PlantState add(PlantState state, PlantDerivatives rate, double dt) {
        return new PlantState(
                state.pMeasured() + dt * rate.pMeasured(),
                state.qOrVMeasured() + dt * rate.qOrVMeasured(),
                state.pIntegral() + dt * rate.pIntegral(),
                state.qIntegral() + dt * rate.qIntegral(),
                state.leadLagState() + dt * rate.leadLagState(),
                state.pLagState() + dt * rate.pLagState());
    }

    private PlantState applyBypasses(PlantState state, Measurement measurement) {
        double measuredP = data.tp() <= EPS ? activeMeasurement(measurement) : state.pMeasured();
        double measuredQv = data.tfltr() <= EPS
                ? reactiveMeasurement(measurement) : state.qOrVMeasured();
        double qError = reactiveError(measuredQv);
        double qPi = limit(data.kp() * qError + state.qIntegral(),
                effectiveQmin, effectiveQmax);
        double leadState = data.tfv() <= EPS ? qPi : state.leadLagState();
        double pLag = state.pLagState();
        if (data.fFlag() != 1) {
            pLag = 0.0;
        } else if (data.tg() <= EPS) {
            double pError = activeError(measuredP, measurement.frequency());
            pLag = limit(data.kpg() * pError + state.pIntegral(),
                    effectivePmin, effectivePmax);
        }
        return new PlantState(measuredP, measuredQv, state.pIntegral(),
                state.qIntegral(), leadState, pLag);
    }

    private void updateStageOutputs(PlantState state) {
        double qError = reactiveError(state.qOrVMeasured());
        qPiOutput = limit(data.kp() * qError + state.qIntegral(),
                effectiveQmin, effectiveQmax);
        if (data.tfv() <= EPS) {
            qext = qPiOutput;
        } else {
            double ratio = data.tft() / data.tfv();
            qext = ratio * qPiOutput + (1.0 - ratio) * state.leadLagState();
        }
        pext = data.fFlag() == 1 ? state.pLagState() : 0.0;
    }

    private double reactiveError(double measured) {
        double error = qvReference - measured;
        // Native records specify the lower width as a magnitude; both signs
        // produce identical cold reference trajectories. Keep typed interval
        // semantics and the original entered constants unchanged.
        double lower = nativeRecordConvention ? -Math.abs(data.dbd1()) : data.dbd1();
        return limit(deadband(error, lower, data.dbd2()),
                data.emin(), data.emax());
    }

    private double activeError(double measuredP, double frequency) {
        double effectiveFrequency = Math.abs(frequency - 1.0) <= EQUILIBRIUM_RESIDUAL
                ? 1.0 : frequency;
        double fError = deadband(1.0 - effectiveFrequency, data.fdbd1(), data.fdbd2());
        double droop = fError >= 0.0 ? data.dup() * fError : data.ddn() * fError;
        return limit(pReference - measuredP + droop, data.femin(), data.femax());
    }


    @Override public java.util.Map<String, Double> getIntegrationStates() {
        if (nativeRecordConvention) {
            var active = new LinkedHashMap<String, Double>();
            if (data.tfltr() > EPS) active.put("Measured Reactive Or Voltage", qOrVMeasured);
            active.put("Reactive Control Integral", qIntegral);
            if (data.tfv() > EPS) active.put("Lead Lag", leadLagState);
            if (data.fFlag() == 1) {
                if (data.tp() > EPS) active.put("Measured Active Power", pMeasured);
                active.put("Active Control Integral", pIntegral);
                if (data.tg() > EPS) active.put("Active Power Lag", pLagState);
            }
            return Collections.unmodifiableMap(active);
        }
        return java.util.Map.of("Measured Active Power", pMeasured,
                "Measured Reactive Or Voltage", qOrVMeasured,
                "Active Control Integral", pIntegral,
                "Reactive Control Integral", qIntegral,
                "Lead Lag", leadLagState,
                "Active Power Lag", pLagState);
    }
    @Override public IntegrationStateMonitor.Snapshot getStateDerivativeSnapshot() {
        if (nativeRecordConvention) {
            var snapshot = stateMonitor.snapshot();
            var states = new LinkedHashMap<String, Double>();
            var rates = new LinkedHashMap<String, Double>();
            for (String key : getIntegrationStates().keySet()) if (snapshot.rates().containsKey(key)) {
                states.put(key, snapshot.evaluationStates().get(key));
                rates.put(key, snapshot.rates().get(key));
            }
            return new IntegrationStateMonitor.Snapshot(snapshot.stage(), states, rates);
        }
        return stateMonitor.snapshot();
    }
    private void retainRates(PlantState s, PlantDerivatives d, Stage stage) {
        stateMonitor.begin(stage);
        stateMonitor.record(0, s.pMeasured(), d.pMeasured());
        stateMonitor.record(1, s.qOrVMeasured(), d.qOrVMeasured());
        stateMonitor.record(2, s.pIntegral(), d.pIntegral());
        stateMonitor.record(3, s.qIntegral(), d.qIntegral());
        stateMonitor.record(4, s.leadLagState(), d.leadLagState());
        stateMonitor.record(5, s.pLagState(), d.pLagState());
    }

    private PlantState state() {
        return new PlantState(pMeasured, qOrVMeasured, pIntegral, qIntegral,
                leadLagState, pLagState);
    }

    private void apply(PlantState state) {
        pMeasured = state.pMeasured();
        qOrVMeasured = state.qOrVMeasured();
        pIntegral = state.pIntegral();
        qIntegral = state.qIntegral();
        leadLagState = state.leadLagState();
        pLagState = state.pLagState();
    }

    private static double lagRate(double state, double input, double timeConstant) {
        return timeConstant <= EPS ? 0.0 : (input - state) / timeConstant;
    }

    private static double integralRate(double integral, double gain, double error,
            double proportionalGain, double lower, double upper, boolean frozen) {
        if (frozen) return 0.0;
        double output = proportionalGain * error + integral;
        if ((output >= upper && error > 0.0) || (output <= lower && error < 0.0)) return 0.0;
        return gain * error;
    }

    private void resolveMeasurements() {
        regulatedBus = null;
        monitoredBranch = null;
        branchStoredInRequestedDirection = false;
        zeroBranchFallback = missingBranchFallback = false;
        if (converter == null && behindImpedanceConverter == null
                && gridFormingConverter == null && virtualSynchronousConverter == null && additionalHost == null) return;
        BaseDStabBus<?, ?> localBus = converter != null ? converter.getDStabBus()
                : behindImpedanceConverter != null ? behindImpedanceConverter.getDStabBus()
                : gridFormingConverter!=null?gridFormingConverter.getDStabBus()
                : virtualSynchronousConverter!=null?virtualSynchronousConverter.getDStabBus():additionalHost.getDStabBus();
        systemBaseMva = localBus.getNetwork().getBaseMva();
        double configuredBase = converter != null
                ? converter.getParentGen().getMvaBase()
                : behindImpedanceConverter != null
                        ? behindImpedanceConverter.getParentGen().getMvaBase()
                        : gridFormingConverter!=null?gridFormingConverter.getParentGen().getMvaBase()
                        : virtualSynchronousConverter!=null?virtualSynchronousConverter.getParentGen().getMvaBase():additionalHost.getParentGen().getMvaBase();
        deviceBaseMva = configuredBase > EPS ? configuredBase : systemBaseMva;
        BaseDStabNetwork<?, ?> network = (BaseDStabNetwork<?, ?>) localBus.getNetwork();
        regulatedBus = data.remoteBus() == 0 ? localBus : network.getDStabBus(busId(data.remoteBus()));
        if (regulatedBus == null) {
            throw new IllegalStateException("REPCA1 remote bus not found: " + data.remoteBus());
        }

        zeroBranchFallback = nativeRecordConvention
                ? data.branchFromBus() == 0 || data.branchToBus() == 0 || data.branchId().equals("0")
                : data.branchFromBus() == 0 && data.branchToBus() == 0;
        if (zeroBranchFallback) return;
        String from = busId(nativeRecordConvention ? Math.abs(data.branchFromBus()) : data.branchFromBus());
        String to = busId(nativeRecordConvention ? Math.abs(data.branchToBus()) : data.branchToBus());
        monitoredBranch = (AclfBranch) localBus.getNetwork().getBranch(from, to, data.branchId());
        branchStoredInRequestedDirection = monitoredBranch != null;
        if (monitoredBranch == null) {
            monitoredBranch = (AclfBranch) localBus.getNetwork().getBranch(to, from, data.branchId());
        }
        if (monitoredBranch == null) {
            if (nativeRecordConvention) {
                missingBranchFallback = true;
                LOGGER.warn("REPCA1 monitored branch {}-{}-{} not found; using generator-power feedback with regulated bus {}",
                        data.branchFromBus(), data.branchToBus(), data.branchId(), regulatedBus.getId());
                return;
            }
            throw new IllegalStateException("REPCA1 monitored branch not found: "
                    + data.branchFromBus() + "-" + data.branchToBus() + "-" + data.branchId());
        }
    }

    private Measurement measure(double localP, double localQ, double localV, double localFrequency) {
        if (converter == null && behindImpedanceConverter == null
                && gridFormingConverter == null && virtualSynchronousConverter == null && additionalHost == null) {
            Complex voltage = regulatedBus == null ? new Complex(localV, 0.0) : regulatedBus.getVoltage();
            double f = regulatedBus == null ? localFrequency : regulatedBus.getFreq();
            return new Measurement(localP, localQ, voltage, Complex.ZERO, f);
        }
        if (zeroBranchFallback || missingBranchFallback) {
            // PSS/E REPCA1 defines all-zero monitored-branch identifiers as
            // generator-power feedback. Keep voltage/frequency on the selected
            // regulated bus, but use the converter's local model-base P/Q and
            // the corresponding current for Kc/line-drop calculations.
            Complex voltage = regulatedBus.getVoltage();
            Complex localPower = new Complex(localP, localQ);
            Complex current = localPower.divide(nonzero(voltage)).conjugate();
            return new Measurement(localP, localQ, voltage, current,
                    regulatedBus.getFreq());
        }
        Complex sSystem = branchStoredInRequestedDirection
                ? monitoredBranch.powerFrom2To(UnitType.PU) : monitoredBranch.powerTo2From(UnitType.PU);
        if (nativeRecordConvention && data.branchFromBus() < 0) sSystem = sSystem.negate();
        Complex branchVoltage = branchStoredInRequestedDirection
                ? ((BaseDStabBus<?, ?>) monitoredBranch.getFromBus()).getVoltage()
                : ((BaseDStabBus<?, ?>) monitoredBranch.getToBus()).getVoltage();
        // InterPSS branch flow is system-base PU. Convert it to the generator/model
        // base before Kc, Rc/Xc, droop, or PI calculations (the direct equivalent
        // of PowerWorld's model-MVA/PUflag normalization).
        double scale = systemBaseMva / deviceBaseMva;
        // The network API supplies system-base values. Materialize the input in
        // the basis declared by PUflag, then normalize it for model calculations.
        Complex declaredInput = data.puFlag() == 0 ? sSystem : sSystem.multiply(scale);
        Complex sDevice = toModelBase(declaredInput, data.puFlag(), systemBaseMva, deviceBaseMva);
        // Native compensation forms Ibranch with the regulated Vt, even when
        // the monitored power is taken at a different branch terminal.
        Complex currentVoltage = nativeRecordConvention ? regulatedBus.getVoltage() : branchVoltage;
        Complex currentDevice = sDevice.divide(nonzero(currentVoltage)).conjugate();
        return new Measurement(sDevice.getReal(), sDevice.getImaginary(), regulatedBus.getVoltage(),
                currentDevice, regulatedBus.getFreq());
    }

    private double reactiveMeasurement(Measurement measurement) {
        double measured;
        if (data.refFlag() == 0) {
            measured = measurement.q();
        } else if (data.vcFlag() == 1) {
            // A zero Rc/Xc selects the actual monitored-line impedance, matching
            // the WECC/ANDES parameter-selection rule.
            Complex lineZ = monitoredBranch == null ? Complex.ZERO : monitoredBranch.getZ();
            double rc = nativeRecordConvention || Math.abs(data.rc()) > EPS ? data.rc() : lineZ.getReal();
            double xc = nativeRecordConvention || Math.abs(data.xc()) > EPS ? data.xc() : lineZ.getImaginary();
            Complex drop = new Complex(rc, xc).multiply(measurement.current());
            measured = measurement.voltage().subtract(drop).abs();
        } else {
            measured = measurement.vMag() + data.kc() * measurement.q();
        }
        return initialized && Math.abs(measured - qvReference) <= EQUILIBRIUM_RESIDUAL
                ? qvReference : measured;
    }

    private double activeMeasurement(Measurement measurement) {
        return initialized && Math.abs(measurement.p() - pReference) <= EQUILIBRIUM_RESIDUAL
                ? pReference : measurement.p();
    }

    private double measuredFrequency(Measurement measurement) {
        return Math.abs(measurement.frequency() - 1.0) <= EQUILIBRIUM_RESIDUAL
                ? 1.0 : measurement.frequency();
    }

    private double leadLag(double input, double dt) {
        if (data.tfv() <= EPS) {
            leadLagState = input;
            return input;
        }
        leadLagState = lag(leadLagState, input, data.tfv(), dt);
        double ratio = data.tft() / data.tfv();
        return ratio * input + (1.0 - ratio) * leadLagState;
    }

    static double integrateWithAntiWindup(double integral, double gain, double error, double dt,
            double proportionalGain, double lower, double upper, boolean frozen) {
        if (frozen || dt <= 0.0) return integral;
        double output = proportionalGain * error + integral;
        if ((output >= upper && error > 0.0) || (output <= lower && error < 0.0)) return integral;
        return integral + gain * error * dt;
    }

    static Complex toModelBase(Complex value, int puFlag, double systemBase, double modelBase) {
        return puFlag == 0 ? value.multiply(systemBase / modelBase) : value;
    }

    public Repca1Data getData() { return data; }
    public double getPref() { return pext; }
    /** Absolute active-power measurement target on the device base. */
    public double getActivePowerReference() { return pReference; }
    public void setActivePowerReference(double reference) {
        if (!initialized) throw new IllegalStateException("Initialize before changing the power reference");
        if (!Double.isFinite(reference)) throw new IllegalArgumentException("Finite power reference required");
        pReference = reference;
    }
    public double getQref() { return qext; }
    public double getMeasuredActivePower() { return pMeasured; }
    public double getMeasuredReactiveOrVoltage() { return qOrVMeasured; }
    public double getReactiveReference() { return qvReference; }
    public double getReactiveControlRawError() { return qvReference - qOrVMeasured; }
    public double getReactiveControlDeadbandOutput() {
        return deadband(getReactiveControlRawError(), data.dbd1(), data.dbd2());
    }
    public double getReactiveControlLimitedError() {
        return limit(getReactiveControlDeadbandOutput(), data.emin(), data.emax());
    }
    public double getActiveControlIntegral() { return pIntegral; }
    public double getReactiveControlIntegral() { return qIntegral; }
    public double getReactiveControlPreLimitOutput() {
        return data.kp() * getReactiveControlLimitedError() + qIntegral;
    }
    public double getReactiveControlOutput() { return qPiOutput; }
    public double getLeadLagState() { return leadLagState; }
    public double getActiveLagState() { return pLagState; }
    public boolean isUsingZeroBranchFallback() { return zeroBranchFallback; }
    public boolean isUsingGeneratorPowerFallback() { return zeroBranchFallback || missingBranchFallback; }
    /** Resolved network-measurement selection; diagnostic access never changes selection or state. */
    public MeasurementSource getMeasurementSource() {
        if (missingBranchFallback) return MeasurementSource.GENERATOR_POWER_MISSING_BRANCH;
        if (zeroBranchFallback) return MeasurementSource.GENERATOR_POWER_ZERO_IDENTIFIERS;
        return monitoredBranch == null ? MeasurementSource.EXTERNAL : MeasurementSource.MONITORED_BRANCH;
    }
    public double getDeviceBaseMva() { return deviceBaseMva; }

    /** Published seven-slot native order; reserved inactive slots are not artificial states. */
    public double[] getNativeStates() {
        if (!nativeRecordConvention) throw new IllegalStateException("Native state order requires the native-record convention");
        return new double[]{data.refFlag() != 0 ? qOrVMeasured : 0,
                data.refFlag() == 0 ? qOrVMeasured : 0,
                qIntegral + initialReactiveReference, leadLagState + initialReactiveReference,
                data.fFlag() == 1 ? pMeasured : 0,
                data.fFlag() == 1 ? pIntegral + initialActiveReference : 0,
                data.fFlag() == 1 ? pLagState + initialActiveReference : 0};
    }

    @Override
    public Map<String, Double> getNamedStates() {
        Map<String, Double> named = new LinkedHashMap<>();
        named.put("Measured Active Power", pMeasured);
        named.put("Measured Reactive Or Voltage", qOrVMeasured);
        named.put("Active Control Integral", pIntegral);
        named.put("Reactive Control Integral", qIntegral);
        named.put("Reactive Control Output", qPiOutput);
        named.put("Lead Lag", leadLagState);
        named.put("Active Power Lag", pLagState);
        named.put("Active Power Command", pext);
        named.put("Reactive Command", qext);
        return Collections.unmodifiableMap(named);
    }

    static double lag(double state, double input, double timeConstant, double dt) {
        if (timeConstant <= EPS) return input;
        double initialDerivative = (input - state) / timeConstant;
        double predicted = state + dt * initialDerivative;
        double predictedDerivative = (input - predicted) / timeConstant;
        return state + 0.5 * dt * (initialDerivative + predictedDerivative);
    }

    static double deadband(double value, double lower, double upper) {
        if (value < lower) return value - lower;
        if (value > upper) return value - upper;
        return 0.0;
    }

    static double limit(double value, double lower, double upper) {
        return Math.max(lower, Math.min(upper, value));
    }

    private static Complex nonzero(Complex value) {
        return value.abs() > 0.01 ? value : new Complex(0.01, 0.0);
    }

    private static String busId(int number) { return "Bus" + number; }

    /** Powers and current are on device base; voltage is at the regulated measurement bus. */
    public record Measurement(double p, double q, Complex voltage, Complex current, double frequency) {
        public Measurement {
            java.util.Objects.requireNonNull(voltage);
            java.util.Objects.requireNonNull(current);
            if (!Double.isFinite(p) || !Double.isFinite(q) || !Double.isFinite(frequency)
                    || !Double.isFinite(voltage.getReal()) || !Double.isFinite(voltage.getImaginary())
                    || !Double.isFinite(current.getReal()) || !Double.isFinite(current.getImaginary()))
                throw new IllegalArgumentException("Plant measurements must be finite");
        }
        public double vMag() { return voltage.abs(); }
    }

    private record PlantState(double pMeasured, double qOrVMeasured,
            double pIntegral, double qIntegral, double leadLagState, double pLagState) { }

    private record PlantDerivatives(double pMeasured, double qOrVMeasured,
            double pIntegral, double qIntegral, double leadLagState, double pLagState) { }
}
