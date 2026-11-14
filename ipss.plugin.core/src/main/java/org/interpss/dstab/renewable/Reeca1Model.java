package org.interpss.dstab.renewable;

import com.interpss.dstab.controller.runtime.IntegrationStateAccess;
import com.interpss.dstab.controller.runtime.IntegrationStateMonitor;
import com.interpss.dstab.controller.runtime.IntegrationStateMonitor.Stage;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.controller.cml.ICMLStateProvider;

/** WECC REEC_A electrical controller producing REGC_A current commands. */
public final class Reeca1Model implements TwoStepElectricalController, ICMLStateProvider, IntegrationStateAccess {
    /** Native PSS/E retains PFLAG's speed input even when WTTQA1 is connected. */
    public enum WindSpeedConvention { PSSE_GENERATOR_SPEED, WTTQA_POWER_REFERENCE }
    private static final double EPS = 1.0e-9;
    /** Suppress only solver partitioning roundoff at the initialized equilibrium. */
    private static final double EQUILIBRIUM_RESIDUAL = 1.0e-8;
    /** Maximum internal step for the stiff cascaded Q/voltage PI controls. */
    private static final double MAX_CONTROL_STEP = 1.0 / 960.0;

    private final IntegrationStateMonitor stateMonitor = new IntegrationStateMonitor(
            "Measured Voltage", "Measured Active Power", "Active Power Order", "Reactive Current", "Reactive Control Integral", "Voltage Control Integral");
    private final Reeca1Data data;
    private final RenewableConverterHost converter;
    private final boolean nativeRecordConvention;
    private double initializedVoltageBias;
    private Repca1Model plantController;
    private org.interpss.dstab.mach.Reaxbu1Model auxiliaryController;
    private boolean twoStepAuxiliaryManaged;
    /** The enclosing solver must evaluate and integrate the shared upstream group once. */
    public void setTwoStepAuxiliaryManaged(boolean managed){
        if(managed&&auxiliaryController==null)throw new IllegalStateException("No auxiliary control to manage");
        twoStepAuxiliaryManaged=managed;
    }
    private RepccModel repccController;
    private HplntdCoordinator.Port hybridController;
    private WtgwgoaModel weakGridController;
    private WindControlStack windControlStack;
    private WindSpeedConvention windSpeedConvention = WindSpeedConvention.WTTQA_POWER_REFERENCE;
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
    private ActivePowerResponseLimits responseLimits = ActivePowerResponseLimits.NORMAL;
    private boolean responseLimitsInitialized;
    private double effectivePmax;
    private double effectivePmin;
    private double effectiveQmax;
    private double effectiveQmin;
    private double effectiveVmax;
    private double effectiveVmin;
    private double postDipTimer;
    private boolean singlePrecisionTimers;
    private double iqHoldTimer;
    private double heldIpMax;
    private double ipLimit;
    private double iqLimit;
    private boolean previousDip;
    private double ipcmd;
    private double iqcmd;
    private double twoStepIpcmd;
    private double twoStepIqcmd;
    private final TwoStepHistory twoStep = new TwoStepHistory(6);

    public Reeca1Model(Reeca1Data data, Regca1Model converter) {
        this(data, converter, false);
    }

    private Reeca1Model(Reeca1Data data, RenewableConverterHost converter, boolean nativeRecord) {
        this.data = java.util.Objects.requireNonNull(data);
        this.converter = converter;
        nativeRecordConvention = nativeRecord;
        if (nativeRecord) windSpeedConvention = WindSpeedConvention.PSSE_GENERATOR_SPEED;
    }

    /** Native calculated-bias rules remain separate from the historical typed input convention. */
    public static Reeca1Model fromNativeRecord(Reeca1Data data, Regca1Model converter) {
        return new Reeca1Model(data, converter, true);
    }

    public static Reeca1Model forConverterHost(Reeca1Data data, RenewableConverterHost converter) {
        return new Reeca1Model(data, converter, true);
    }

    public boolean isNativeRecordConvention() { return nativeRecordConvention; }

    @Override public ActivePowerResponseLimits getActivePowerResponseLimits() { return responseLimits; }
    @Override public void setActivePowerResponseLimits(ActivePowerResponseLimits limits) {
        java.util.Objects.requireNonNull(limits);
        if (responseLimitsInitialized && limits != responseLimits)
            throw new IllegalStateException("Configure response limits before initialization");
        responseLimits = limits;
    }
    public double getEffectiveActivePowerMaximum() { return effectivePmax; }
    public double getEffectiveActivePowerMinimum() { return effectivePmin; }

    @Override
    public void initialize(double p, double q, double v) {
        twoStepAuxiliaryManaged=false;
        stateMonitor.reset();
        twoStep.reset();
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
        initializedVoltageBias = nativeRecordConvention && data.qFlag() == 1
                && data.vFlag() == 0 && data.pfFlag() == 1 ? sensedV - q : data.vref1();
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
        effectivePmax = responseLimits.upper(effectivePmax, pOrder);
        effectivePmin = responseLimits.lower(effectivePmin, pOrder);
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
        if (plantController != null) plantController.setActivePowerResponseLimits(responseLimits);
        responseLimitsInitialized = true;
        if (plantController != null) {
            plantController.initialize(p, q, sensedV);
            plantController.setReactiveStateOffset(qReference);
        }
        if(auxiliaryController!=null) {
            double systemBase=converter==null?100:converter.getDStabBus().getNetwork().getBaseMva();
            double machineBase=converter==null?systemBase:converter.getParentGen().getMvaBase();
            auxiliaryController.initializeOutputs(qReference,p,systemBase/(machineBase>0?machineBase:systemBase));
        }
        if (repccController != null) repccController.initialize(p,qReference,!(data.qFlag()==1&&data.vFlag()==0));
        if(hybridController!=null)hybridController.initialize(p,qReference);
        if (weakGridController != null) weakGridController.initialize(v, p);
        if (windControlStack != null) windControlStack.initialize(p);
        twoStepIpcmd = ipcmd;
        twoStepIqcmd = iqcmd;
    }

    @Override
    public void step(double dt, double p, double q, double v, double frequency) {
        if(auxiliaryController!=null)throw new UnsupportedOperationException("Shared auxiliary references require staged modified Euler");
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
        if (repccController != null) { repccController.step(dt,0); repccController.step(dt,1); }
        if(hybridController!=null)throw new IllegalStateException("Shared hybrid composition requires staged controller callbacks");
        boolean voltageDip = sensedV < data.vdip() || sensedV > data.vup();
        if (weakGridController != null) weakGridController.step(dt, v, getPlantPowerReference(p0));
        if (windControlStack != null) {
            windControlStack.step(dt, p, pOrder, getSelectedPowerReference(p0), voltageDip);
        }

        int substeps = Math.max(1, (int) Math.ceil(dt / MAX_CONTROL_STEP));
        double controlStep = dt / substeps;
        for (int i = 0; i < substeps; i++) {
            stepElectricalControls(controlStep, p, q, v, sensedV);
        }
    }

    /**
     * Samples both modified-Euler network endpoints. Dynamic REEC_A states are
     * advanced once at flag 0 by their bounded trapezoidal blocks; flag 1 then
     * refreshes algebraic current commands from the corrected network endpoint.
     * The composed plant and wind controls receive both stages through their
     * native predictor/corrector contracts.
     */
    @Override
    public void step(double dt, double p, double q, double v, double frequency, int flag) {
        if (flag != 0 && flag != 1) {
            throw new IllegalArgumentException("REECA1 integration flag must be 0 or 1");
        }
        p = snapToInitial(p, p0);
        q = snapToInitial(q, q0);
        v = snapToInitial(v, localVoltage0);
        frequency = snapToInitial(frequency, 1.0);
        double sensedV = snapToInitial(sensedVoltage(v), sensedVoltage0);
        boolean voltageDip = sensedV < data.vdip() || sensedV > data.vup();
        if (plantController != null) {
            plantController.step(dt, p, q, sensedV, frequency, flag);
        }
        if(auxiliaryController!=null&&dt>0)auxiliaryController.step(dt,flag);
        if (repccController != null) repccController.step(dt,flag);
        if(hybridController!=null)hybridController.step(dt,flag);
        if (weakGridController != null) weakGridController.step(dt, v, getPlantPowerReference(p0), flag);
        if (windControlStack != null) {
            windControlStack.step(dt, p, pOrder, getSelectedPowerReference(p0), voltageDip, flag);
        }
        if (flag == 0) {
            int substeps = Math.max(1, (int) Math.ceil(dt / MAX_CONTROL_STEP));
            double controlStep = dt / substeps;
            for (int i = 0; i < substeps; i++) {
                stepElectricalControls(controlStep, p, q, v, sensedV);
            }
        } else {
            // Re-evaluate bypasses, limits, and commands without integrating a
            // second time. REGCA1's flag-1 derivative now sees this endpoint.
            stepElectricalControls(0.0, p, q, v, sensedV);
        }
    }

    private void stepElectricalControls(double dt, double p, double q, double v,
            double sensedV) {
        stepElectricalControls(dt, p, q, v, sensedV, true);
    }

    private void stepElectricalControls(double dt, double p, double q, double v,
            double sensedV, boolean updateDipHistory) {
        double observed0 = vMeasured;
        double observed1 = pMeasured;
        double observed2 = pOrder;
        double observed3 = qCurrent;
        double observed4 = qIntegral;
        double observed5 = vIntegral;
        boolean voltageDip = sensedV < data.vdip() || sensedV > data.vup();
        if (updateDipHistory) updateDipTimers(dt, voltageDip);
        vMeasured = Repca1Model.lag(vMeasured, sensedV, data.trv(), dt);
        pMeasured = Repca1Model.lag(pMeasured, p, data.tp(), dt);

        double pref = windControlStack != null && windControlStack.isTorqueControllerActive()
                ? windControlStack.getPref() : getSelectedPowerReference(p0);
        double rateTarget = Repca1Model.limit(pref,
                pFilter + data.dpmin() * dt, pFilter + data.dpmax() * dt);
        pFilter = rateTarget;
        // PSS/E applies PFLAG to wg independently of WTTQA1's own Pref output.
        // The explicit power-reference convention retains the older ANDES
        // coupling for typed clients and cross-vendor comparison fixtures.
        double generatorSpeed = getActivePowerSpeedMultiplier();
        double selectedP = data.pFlag() == 1 ? generatorSpeed * pFilter : pFilter;
        if (!voltageDip) {
            pOrder = Repca1Model.lag(pOrder,
                    Repca1Model.limit(selectedP, effectivePmin, effectivePmax),
                    data.tpord(), dt);
            pOrder = Repca1Model.limit(pOrder, effectivePmin, effectivePmax);
        }

        double plantQref = hybridController!=null?hybridController.reactiveIncrement():repccController!=null?repccController.reactiveIncrement():plantController == null ? 0.0 : plantController.getQref();
        double selectedQ = data.pfFlag() == 1
                ? pMeasured * powerFactorRatio : auxiliaryController!=null?auxiliaryController.getReactiveOutput():qReference + plantQref;
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
                voltageBias = Repca1Model.limit(initializedVoltageBias + selectedQ,
                        effectiveVmin, effectiveVmax);
                reactiveControlError = 0.0;
                reactiveControlPreLimitOutput = initializedVoltageBias + selectedQ;
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
        applyCurrentLimits(rawIp, rawIq, voltageDip, updateDipHistory);
        if (dt > 0.0) {
            stateMonitor.begin(Stage.APPLIED_STEP);
            stateMonitor.record(0, observed0, (data.trv() <= EPS ? 0.0 : (vMeasured-observed0)/dt));
            stateMonitor.record(1, observed1, (data.tp() <= EPS ? 0.0 : (pMeasured-observed1)/dt));
            stateMonitor.record(2, observed2, (data.tpord() <= EPS ? 0.0 : (pOrder-observed2)/dt));
            stateMonitor.record(3, observed3, (data.tiq() <= EPS ? 0.0 : (qCurrent-observed3)/dt));
            stateMonitor.record(4, observed4, (qIntegral-observed4)/dt);
            stateMonitor.record(5, observed5, (vIntegral-observed5)/dt);
        }
        if (updateDipHistory) previousDip = voltageDip;
    }

    /**
     * Preserve an excursion at the solved left side of a network event.
     * An event may remove it before the next integration call samples voltage.
     * The next positive step owns the recovery transition and timer update;
     * ordinary algebraic refreshes must not consume that history.
     */
    void captureNetworkEventLeftBoundary(double localVoltage) {
        double sensedV = sensedVoltage(localVoltage);
        if (sensedV < data.vdip() || sensedV > data.vup()) previousDip = true;
    }

    public void rebaseNextTwoStep() {
        twoStep.rebaseNextStep();
        if (plantController != null) plantController.rebaseNextTwoStep();
        if (windControlStack != null) windControlStack.rebaseNextTwoStep();
        if (weakGridController != null) weakGridController.rebaseNextTwoStep();
    }

    /** Refresh upstream algebraic signals without consuming derivative history. */
    public void refreshTwoStep(double p, double q, double v, double frequency) {
        if(auxiliaryController!=null&&!twoStepAuxiliaryManaged)throw new UnsupportedOperationException("Shared auxiliary references require an owning integration group");
        p = snapToInitial(p, p0);
        q = snapToInitial(q, q0);
        v = snapToInitial(v, localVoltage0);
        frequency = snapToInitial(frequency, 1.0);
        double sensedV = snapToInitial(sensedVoltage(v), sensedVoltage0);
        boolean dip = sensedV < data.vdip() || sensedV > data.vup();
        if (plantController != null) plantController.stepTwoStep(0.0, p, q, sensedV, frequency);
        if (weakGridController != null) weakGridController.stepTwoStep(0.0, v, getPlantPowerReference(p0));
        if (windControlStack != null)
            windControlStack.stepTwoStep(0.0, p, pOrder, getSelectedPowerReference(p0), dip);
        stepElectricalControls(0.0, p, q, v, sensedV, false);
    }

    /**
     * Opt-in explicit two-step path. Electrical derivatives use the old wind
     * and plant outputs; wind derivatives use the old plant output and Pord.
     * Endpoint output refreshes occur only after all states have advanced.
     */
    public void stepTwoStep(double dt, double p, double q, double v, double frequency) {
        if (!Double.isFinite(dt) || dt < 0.)
            throw new IllegalArgumentException("Two-step duration must be finite and nonnegative");
        if(auxiliaryController!=null&&!twoStepAuxiliaryManaged)throw new UnsupportedOperationException("Shared auxiliary references require an owning integration group");
        if (dt <= 0.0) {
            refreshTwoStep(p, q, v, frequency);
            return;
        }
        p = snapToInitial(p, p0);
        q = snapToInitial(q, q0);
        v = snapToInitial(v, localVoltage0);
        frequency = snapToInitial(frequency, 1.0);
        double sensedV = snapToInitial(sensedVoltage(v), sensedVoltage0);
        boolean dip = sensedV < data.vdip() || sensedV > data.vup();
        double oldPorder = pOrder;
        double plantPref = plantController == null ? 0.0 : plantController.getPref();
        double selectedReference = getSelectedPowerReference(p0);
        double pref = windControlStack != null && windControlStack.isTorqueControllerActive()
                ? windControlStack.getPref() : selectedReference;
        updateDipTimers(dt, dip);
        pFilter = Repca1Model.limit(pref, pFilter + data.dpmin() * dt,
                pFilter + data.dpmax() * dt);
        stepElectricalControls(0.0, p, q, v, sensedV, false);
        double selectedP = data.pFlag() == 1
                ? getActivePowerSpeedMultiplier() * pFilter : pFilter;
        double vRate = lagRate(vMeasured, sensedV, data.trv());
        double pRate = lagRate(pMeasured, p, data.tp());
        double orderRate = lagRate(pOrder,
                Repca1Model.limit(selectedP, effectivePmin, effectivePmax), data.tpord());
        double iqRate = lagRate(qCurrent, reactivePowerTarget / nonzero(vMeasured), data.tiq());
        double qIntegralRate = data.qFlag() == 1 && data.vFlag() == 1
                ? integralRate(qIntegral, data.kqi(), reactiveControlError, data.kqp(),
                        effectiveVmin, effectiveVmax) : 0.0;
        double vIntegralRate = data.qFlag() == 1
                ? integralRate(vIntegral, data.kvi(), voltageControlError, data.kvp(),
                        -preliminaryIqLimit, preliminaryIqLimit) : 0.0;
        if ((voltageControlPreLimitOutput >= preliminaryIqLimit - EPS
                    && data.kvi() * voltageControlError > 0.0 && qIntegralRate > 0.0)
                || (voltageControlPreLimitOutput <= -preliminaryIqLimit + EPS
                    && data.kvi() * voltageControlError < 0.0 && qIntegralRate < 0.0)) qIntegralRate = 0.0;
        // Current-limit logic precedes the state advance. The next converter
        // command combines this limit with the advanced Pord/Vt_filt path.
        // Q-priority headroom reads the last published converter command.
        // An algebraic refresh at a new voltage must not move this feedback
        // across the integration boundary before that command is published.
        double stagedIpLimit = data.pqFlag() == 0 && postDipTimer <= 0.0
                ? Math.min(currentTableLimit(vMeasured, false, Double.POSITIVE_INFINITY),
                        remaining(currentLimit(), twoStepIqcmd)) : ipLimit;
        stateMonitor.begin(Stage.TWO_STEP);
        stateMonitor.record(0, vMeasured, vRate);
        stateMonitor.record(1, pMeasured, pRate);
        stateMonitor.record(2, pOrder, dip ? 0.0 : orderRate);
        stateMonitor.record(3, qCurrent, dip || data.qFlag() != 0 ? 0.0 : iqRate);
        stateMonitor.record(4, qIntegral, dip ? 0.0 : qIntegralRate);
        stateMonitor.record(5, vIntegral, dip ? 0.0 : vIntegralRate);
        twoStep.begin(dt);
        vMeasured = twoStep.advance(0, vMeasured, vRate);
        pMeasured = twoStep.advance(1, pMeasured, pRate);
        pOrder = dip ? twoStep.hold(2, pOrder) : Repca1Model.limit(
                twoStep.advance(2, pOrder, orderRate), effectivePmin, effectivePmax);
        qCurrent = dip || data.qFlag() != 0 ? twoStep.hold(3, qCurrent)
                : twoStep.advance(3, qCurrent, iqRate);
        qIntegral = dip ? twoStep.hold(4, qIntegral)
                : twoStep.advance(4, qIntegral, qIntegralRate);
        vIntegral = dip ? twoStep.hold(5, vIntegral)
                : twoStep.advance(5, vIntegral, vIntegralRate);
        twoStep.observeRates(stateMonitor);
        // REGCA consumes the last published electrical-control command. Its
        // proportional Q inputs belong to this completed step, while these
        // controller states have advanced. Retain that boundary before the
        // plant advances or an endpoint/network refresh changes those inputs.
        stepElectricalControls(0.0, p, q, v, sensedV, false);
        twoStepIpcmd = Repca1Model.limit(pOrder / nonzero(vMeasured), 0.0, stagedIpLimit);
        twoStepIqcmd = iqcmd;
        // Capture recovery headroom with the command publication, once per step.
        // Endpoint algebraic refreshes must not overwrite the retained value.
        if (dip && postDipTimer <= 0.0) {
            heldIpMax = Math.min(currentTableLimit(vMeasured, false, Double.POSITIVE_INFINITY),
                    data.pqFlag() == 0 ? remaining(currentLimit(), twoStepIqcmd) : currentLimit());
        }
        if (windControlStack != null)
            windControlStack.stepTwoStep(dt, p, oldPorder, selectedReference, dip);
        if (weakGridController != null) weakGridController.stepTwoStep(dt, v, p0 + plantPref);
        if (plantController != null) plantController.stepTwoStep(dt, p, q, sensedV, frequency);
        refreshTwoStep(p, q, v, frequency);
        // Network/output refreshes must not consume a dip transition or start
        // its hold timer before the next integration step evaluates it.
        previousDip = dip;
    }

    private static double lagRate(double state, double input, double timeConstant) {
        return timeConstant <= EPS ? 0.0 : (input - state) / timeConstant;
    }

    private static double integralRate(double integral, double gain, double error,
            double proportionalGain, double lower, double upper) {
        double output = proportionalGain * error + integral;
        if ((output >= upper && error > 0.0) || (output <= lower && error < 0.0)) return 0.0;
        return gain * error;
    }

    private void applyCurrentLimits(double rawIp, double rawIq, boolean voltageDip, boolean captureHold) {
        double iqTable = currentTableLimit(vMeasured, true, Double.POSITIVE_INFINITY);
        double ipTable = currentTableLimit(vMeasured, false, Double.POSITIVE_INFINITY);
        if (data.pqFlag() == 0) {
            double iqMax = Math.min(currentLimit(), iqTable);
            rawIq = Repca1Model.limit(rawIq, -iqMax, iqMax);
            double calculatedIpMax = Math.min(ipTable, remaining(currentLimit(), rawIq));
            if (captureHold && voltageDip && postDipTimer <= 0.0) heldIpMax = calculatedIpMax;
            double ipMax = postDipTimer > 0.0 ? heldIpMax : calculatedIpMax;
            rawIp = Repca1Model.limit(rawIp, 0.0, ipMax);
            iqLimit = iqMax;
            ipLimit = ipMax;
        } else {
            double calculatedIpMax = Math.min(currentLimit(), ipTable);
            if (captureHold && voltageDip && postDipTimer <= 0.0) heldIpMax = calculatedIpMax;
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
        // A recovery interval already in progress keeps its original deadline,
        // including time spent in a subsequent voltage excursion.
        if (postDipTimer > 0.0) {
            double remaining = singlePrecisionTimers
                    ? (float) ((float) postDipTimer - (float) dt)
                    : postDipTimer - dt;
            postDipTimer = Math.max(0.0, remaining);
        } else if (previousDip && !voltageDip) {
            postDipTimer = singlePrecisionTimers ? (float) data.thld2() : data.thld2();
        }
        if (previousDip && !voltageDip) {
            iqHoldTimer = Math.abs(data.thld());
        } else if (!voltageDip) {
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
        int length = 2;
        // A zero voltage terminates the optional third/fourth pairs. Current
        // ordinates need not increase; only the voltage coordinates are sorted.
        while (length < x.length && x[length] != 0.0) length++;
        if (!validTable(x, y, length)) return disabledValue;
        if (voltage <= x[0]) return y[0];
        for (int i = 1; i < length; i++) {
            if (voltage <= x[i]) {
                return y[i - 1] + (voltage - x[i - 1]) * (y[i] - y[i - 1])
                        / (x[i] - x[i - 1]);
            }
        }
        return y[length - 1];
    }

    private static boolean validTable(double[] x, double[] y, int length) {
        boolean nonzero = false;
        for (int i = 0; i < length; i++) {
            if (!Double.isFinite(x[i]) || !Double.isFinite(y[i])) return false;
            nonzero |= Math.abs(x[i]) > EPS || Math.abs(y[i]) > EPS;
            if (i > 0 && x[i] <= x[i - 1]) return false;
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
    void setSinglePrecisionStates(boolean enabled) {
        singlePrecisionTimers = enabled;
        twoStep.setSinglePrecision(enabled);
        if (plantController!=null) plantController.setSinglePrecisionStates(enabled);
        if (windControlStack!=null) windControlStack.setSinglePrecisionStates(enabled);
    }
    @Override public Repca1Model getPlantController() { return plantController; }
    @Override public org.interpss.dstab.mach.Reaxbu1Model getAuxiliaryController(){return auxiliaryController;}
    @Override public void setAuxiliaryController(org.interpss.dstab.mach.Reaxbu1Model controller){
        if(controller!=null&&(plantController!=null||repccController!=null||hybridController!=null||auxiliaryController!=null))
            throw new IllegalStateException("Plant reference port is occupied");
        auxiliaryController=controller;
    }
    @Override public void setPlantController(Repca1Model controller) {
        if(controller!=null&&(repccController!=null||hybridController!=null||auxiliaryController!=null))throw new IllegalStateException("The plant reference port is already occupied");
        plantController = controller;
    }
    @Override public RepccModel getRepccController(){return repccController;}
    @Override public void setRepccController(RepccModel controller){
        if(controller!=null&&(plantController!=null||repccController!=null||hybridController!=null||auxiliaryController!=null))throw new IllegalStateException("Plant reference port is occupied");
        if(controller!=null&&controller.getConverter()!=converter)throw new IllegalArgumentException("REPCC converter ownership mismatch");
        repccController=controller;
    }
    @Override public HplntdCoordinator.Port getHybridController(){return hybridController;}
    boolean isOwnedBy(Regca1Model candidate){return converter==candidate;}
    public void setHybridController(HplntdCoordinator.Port controller){
        if(controller==null&&hybridController!=null)throw new IllegalStateException("Shared hybrid ownership cannot be detached one unit at a time");
        if(controller!=null&&(plantController!=null||repccController!=null||hybridController!=null||weakGridController!=null||windControlStack!=null||auxiliaryController!=null))throw new IllegalStateException("Plant reference port is occupied");
        if(controller!=null&&controller.converter()!=converter)throw new IllegalArgumentException("Hybrid converter ownership mismatch");
        hybridController=controller;
    }
    @Override public WtgwgoaModel getWeakGridController() { return weakGridController; }
    @Override public void setWeakGridController(WtgwgoaModel controller) { if(controller!=null&&hybridController!=null)throw new IllegalStateException("Hybrid reference port is occupied");weakGridController = controller; }
    public WindControlStack getWindControlStack() { return windControlStack; }
    public void setWindControlStack(WindControlStack stack) {
        if(stack!=null&&hybridController!=null)throw new IllegalStateException("Hybrid reference port is occupied");
        windControlStack = stack;
        applyWindSpeedConvention();
    }
    public WindSpeedConvention getWindSpeedConvention() { return windSpeedConvention; }
    public void setWindSpeedConvention(WindSpeedConvention convention) {
        windSpeedConvention = java.util.Objects.requireNonNull(convention);
        applyWindSpeedConvention();
    }
    private void applyWindSpeedConvention() {
        if (windControlStack != null) windControlStack.setHoldInitialSpeedWithoutDriveTrain(
                windSpeedConvention == WindSpeedConvention.PSSE_GENERATOR_SPEED);
    }
    public double getActivePowerSpeedMultiplier() {
        if (windControlStack == null) return 1.0;
        return windSpeedConvention == WindSpeedConvention.PSSE_GENERATOR_SPEED
                ? windControlStack.getGeneratorSpeed()
                : windControlStack.getElectricalControllerSpeed();
    }
    public double getMeasuredVoltage() { return vMeasured; }
    /** Native VAR(L+3) semantics where the direct-voltage branch is active. */
    public double getInitializedVoltageBias() { return initializedVoltageBias; }
    /** Reserved inactive native storage slots are zero; legacy named aliases are unchanged. */
    public double[] getNativeStates() {
        if (!nativeRecordConvention) throw new IllegalStateException("Native state order requires the native-record convention");
        return new double[]{vMeasured, data.pfFlag() == 1 ? pMeasured : 0,
                data.qFlag() == 1 && data.vFlag() == 1 ? qIntegral : 0,
                data.qFlag() == 1 ? vIntegral : 0, data.qFlag() == 0 ? qCurrent : 0, pOrder};
    }
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
    public double getTwoStepIpcmd() { return twoStepIpcmd; }
    public double getTwoStepIqcmd() { return twoStepIqcmd; }


    @Override public java.util.Map<String, Double> getIntegrationStates() {
        if (nativeRecordConvention) {
            var result = new LinkedHashMap<String, Double>();
            if (data.trv() > EPS) result.put("Measured Voltage", vMeasured);
            if (data.pfFlag() == 1 && data.tp() > EPS) result.put("Measured Active Power", pMeasured);
            if (data.qFlag() == 1 && data.vFlag() == 1) result.put("Reactive Control Integral", qIntegral);
            if (data.qFlag() == 1) result.put("Voltage Control Integral", vIntegral);
            if (data.qFlag() == 0 && data.tiq() > EPS) result.put("Reactive Current", qCurrent);
            if (data.tpord() > EPS) result.put("Active Power Order", pOrder);
            return java.util.Collections.unmodifiableMap(result);
        }
        return java.util.Map.of("Measured Voltage", vMeasured,
                "Measured Active Power", pMeasured,
                "Active Power Order", pOrder,
                "Reactive Current", qCurrent,
                "Reactive Control Integral", qIntegral,
                "Voltage Control Integral", vIntegral);
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

    /** Discrete and rate-limiter memory is separate from differential coordinates. */
    public Map<String, Double> getControlMemory() {
        return Map.of("Active Power Filter", pFilter, "Post Dip Timer", postDipTimer,
                "Reactive Hold Timer", iqHoldTimer, "Held Active Current Limit", heldIpMax,
                "Previous Dip", previousDip ? 1.0 : 0.0,
                "Published Active Command", twoStepIpcmd, "Published Reactive Command", twoStepIqcmd);
    }

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
        if(auxiliaryController!=null)auxiliaryController.getNamedStates().forEach((name,value)->named.put("Auxiliary "+name,value));
        return Collections.unmodifiableMap(named);
    }
}
