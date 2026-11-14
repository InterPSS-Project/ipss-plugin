package org.interpss.dstab.renewable;

import com.interpss.dstab.controller.runtime.IntegrationStateAccess;
import com.interpss.dstab.controller.runtime.IntegrationStateMonitor;
import com.interpss.dstab.controller.runtime.IntegrationStateMonitor.Stage;

import java.util.Collections;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import com.interpss.dstab.controller.runtime.IntegrationStepAware;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.dstab.controller.cml.ICMLStateProvider;
import com.interpss.dstab.device.DynamicGenDevice;
import com.interpss.dstab.device.NetworkIterationAware;
import com.interpss.dstab.device.impl.DynamicBusDeviceImpl;

/**
 * Positive-sequence WECC REGC_A converter kernels. REGCA1 uses a current
 * coordinate; REGCAU2 retains a distinct optional effective-reactance interface.
 * Electrical and plant controls are composed while this device owns the source.
 */
public final class Regca1Model extends DynamicBusDeviceImpl
        implements DynamicGenDevice, RenewableConverterHost, IntegrationStepAware, ICMLStateProvider, IntegrationStateAccess,
        NetworkIterationAware, com.interpss.dstab.device.ConverterCompensationPort,
        com.interpss.dstab.controller.runtime.NetworkEventInput,
        com.interpss.dstab.controller.runtime.IntegrationStepContext {
    public enum IntegrationScheme { MODIFIED_EULER, TWO_STEP }
    private static final double EPS = 1.0e-9;

    private final IntegrationStateMonitor stateMonitor;
    private final Regca1Data data;
    private final String modelName;
    private final double effectiveReactance;
    private final boolean removableCompensation;
    private final String voltageKey, activeCurrentKey, reactiveCurrentKey, activePowerKey, reactivePowerKey;
    private final Hashtable<String, Object> states = new Hashtable<>();
    private DStabGen parentGen;
    private RenewableElectricalController electricalController;
    private Reeca1Model reeca1Controller;
    private double deviceBaseMva;
    private double systemBaseMva;
    private double vFiltered;
    /** Output of the REGC_A active-current regulator, before algebraic LVG. */
    private double ipState;
    /** Reactive regulator coordinate: current, or voltage for nonzero REGCAU2 Xe. */
    private double iqState;
    private double p;
    private double q;
    private double initialQ;
    private double oldVFiltered;
    private double oldIpState;
    private double oldIqState;
    private double dv0;
    private double dip0;
    private double diq0;
    private double integrationStep;
    private IntegrationScheme integrationScheme = IntegrationScheme.MODIFIED_EULER;
    private final TwoStepHistory twoStep = new TwoStepHistory(3);
    private boolean networkHighVoltageEnabled;
    private boolean networkSolutionInProgress;
    private boolean networkOutputAvailable;
    private double savedNetworkVoltage;
    private double networkCorrection;
    private double deceleratedNetworkVoltage;
    private boolean oldNetworkOutputAvailable;
    private double oldSavedNetworkVoltage, oldNetworkCorrection, oldDeceleratedNetworkVoltage;
    private double pendingEventVoltage = Double.NaN;
    private double oldPendingEventVoltage;
    public enum NetworkVoltageMemoryPolicy { PER_NETWORK_SOLVE, ACCEPTED_BOUNDARY }
    private NetworkVoltageMemoryPolicy voltageMemoryPolicy = NetworkVoltageMemoryPolicy.PER_NETWORK_SOLVE;
    private double committedSavedVoltage, acceptedTerminalVoltage;
    private double preparedMemoryTime = Double.NaN, preparedMemoryStep = Double.NaN;
    private double acceptedMemoryStepTime = Double.NaN, acceptedMemoryEventTime = Double.NaN;
    private double trialMemoryEventTime = Double.NaN;
    private boolean positiveMemoryStep, eventMemoryTrial, memoryNetworkAccepted;

    /** Opt-in history policy; configure between accepted steps, preferably before initialization. */
    public void setNetworkVoltageMemoryPolicy(NetworkVoltageMemoryPolicy policy) {
        java.util.Objects.requireNonNull(policy);
        if (networkSolutionInProgress || positiveMemoryStep || eventMemoryTrial && memoryNetworkAccepted)
            throw new IllegalStateException("Change voltage-memory policy between accepted steps");
        if (policy == voltageMemoryPolicy) return;
        voltageMemoryPolicy = policy;
        resetAcceptedVoltageMemory();
    }
    public NetworkVoltageMemoryPolicy getNetworkVoltageMemoryPolicy() { return voltageMemoryPolicy; }
    public record AcceptedVoltageMemoryState(double savedVoltage, double terminalVoltage,
            double stepStartTime, double eventTime, boolean positiveStepPending) { }
    public AcceptedVoltageMemoryState getAcceptedVoltageMemoryState() {
        return new AcceptedVoltageMemoryState(committedSavedVoltage, acceptedTerminalVoltage,
                acceptedMemoryStepTime, acceptedMemoryEventTime, positiveMemoryStep);
    }
    private boolean acceptedVoltageMemory() {
        return networkHighVoltageEnabled && voltageMemoryPolicy == NetworkVoltageMemoryPolicy.ACCEPTED_BOUNDARY;
    }
    private void resetAcceptedVoltageMemory() {
        committedSavedVoltage = savedNetworkVoltage;
        acceptedTerminalVoltage = getDStabBus() == null ? 1. : Math.max(.01,getDStabBus().getVoltageMag());
        preparedMemoryTime = preparedMemoryStep = acceptedMemoryStepTime = acceptedMemoryEventTime = trialMemoryEventTime = Double.NaN;
        positiveMemoryStep = eventMemoryTrial = memoryNetworkAccepted = false;
    }
    @Override public void prepareIntegrationStep(double startTimeSec,double stepSec) {
        if (!acceptedVoltageMemory()) return;
        if (!Double.isFinite(startTimeSec) || !Double.isFinite(stepSec) || stepSec <= 0. || !Double.isFinite(startTimeSec+stepSec))
            throw new IllegalArgumentException("Finite physical integration interval required");
        if (positiveMemoryStep && (startTimeSec != preparedMemoryTime || stepSec != preparedMemoryStep))
            throw new IllegalStateException("Previous voltage-memory step has not been accepted");
        if (!Double.isNaN(preparedMemoryTime) && startTimeSec < preparedMemoryTime)
            throw new IllegalArgumentException("Reinitialize before reversing the physical clock");
        // Clock only: no terminal sampling, trial state or committed history update.
        preparedMemoryTime = startTimeSec;preparedMemoryStep = stepSec;
    }
    private void startVoltageMemoryStep(double dt,DynamicSimuMethod method,int flag) {
        if (!acceptedVoltageMemory()) return;
        if (method != DynamicSimuMethod.MODIFIED_EULER || (flag != 0 && flag != 1)
                || !Double.isFinite(dt) || dt < 0.)
            throw new IllegalArgumentException("Accepted voltage memory requires valid integration stages");
        if (integrationScheme == IntegrationScheme.TWO_STEP && !(activeController() instanceof TwoStepElectricalController))
            throw new IllegalStateException("Electrical controller does not provide a two-step integration boundary");
        if (flag != 0 || dt == 0.) return;
        if (!Double.isFinite(preparedMemoryTime) || dt != preparedMemoryStep)
            throw new IllegalStateException("Prepare the physical step before converter integration");
        if (preparedMemoryTime == acceptedMemoryStepTime)
            throw new IllegalStateException("Physical voltage-memory step was already accepted");
        positiveMemoryStep = true;
        memoryNetworkAccepted = false;
    }


    /** Stored algebraic memory, distinct from the converter's differential states. */
    public record NetworkHighVoltageState(boolean enabled, boolean solutionInProgress,
            boolean outputAvailable, double savedVoltage, double correction, double deceleratedVoltage) {}

    public NetworkHighVoltageState getNetworkHighVoltageState() {
        return new NetworkHighVoltageState(networkHighVoltageEnabled, networkSolutionInProgress,
                networkOutputAvailable, savedNetworkVoltage, networkCorrection, deceleratedNetworkVoltage);
    }

    /**
     * Opt in to network-iteration voltage memory for high-voltage correction and
     * the low-voltage active-current gain. Requires a solver delivering
     * the complete NetworkIterationAware lifecycle, including final evaluation.
     * Configure between solves. Enabling retains the voltage memory seeded by
     * initialization; disabling resets it at the current terminal voltage.
     * The default instantaneous behavior is preserved.
     */
    public void setNetworkIterationHighVoltageEnabled(boolean enabled) {
        if (networkSolutionInProgress || positiveMemoryStep || eventMemoryTrial && memoryNetworkAccepted) throw new IllegalStateException("Configure voltage memory between accepted steps");
        if (networkHighVoltageEnabled == enabled) return;
        networkHighVoltageEnabled = enabled;
        if (!enabled) resetNetworkHighVoltageMemory();
    }

    private void resetNetworkHighVoltageMemory() {
        networkSolutionInProgress = false;
        networkOutputAvailable = false;
        savedNetworkVoltage = getDStabBus() == null ? 1.0 : Math.max(.01, getDStabBus().getVoltageMag());
        deceleratedNetworkVoltage = savedNetworkVoltage;
        networkCorrection = 0.0;
        pendingEventVoltage = Double.NaN;
        resetAcceptedVoltageMemory();
    }

    @Override public void beginNetworkSolution(NetworkIterationAware.Context context) {
        if (!networkHighVoltageEnabled) return;
        if (networkSolutionInProgress) throw new IllegalStateException("Network solution is already in progress");
        oldNetworkOutputAvailable = networkOutputAvailable;
        oldSavedNetworkVoltage = savedNetworkVoltage;
        oldNetworkCorrection = networkCorrection;
        oldDeceleratedNetworkVoltage = deceleratedNetworkVoltage;
        oldPendingEventVoltage = pendingEventVoltage;
        networkSolutionInProgress = true;
        networkOutputAvailable = true;
        // Event processing can perform a preliminary linear solve before this
        // lifecycle starts. Advance memory from the captured left endpoint.
        double previousVoltage = Double.isNaN(pendingEventVoltage)
                ? Math.max(.01, getDStabBus().getVoltageMag()) : pendingEventVoltage;
        if (acceptedVoltageMemory()) {
            eventMemoryTrial = context.externalEventBoundary() && !positiveMemoryStep;
            trialMemoryEventTime = eventMemoryTrial ? context.stepStartTime() : Double.NaN;
            boolean newEvent = eventMemoryTrial && trialMemoryEventTime != acceptedMemoryEventTime;
            savedNetworkVoltage = positiveMemoryStep || newEvent
                    ? (1. - data.accel()) * committedSavedVoltage + data.accel() * acceptedTerminalVoltage
                    : committedSavedVoltage;
            memoryNetworkAccepted = false;
        } else {
            savedNetworkVoltage = (1.0 - data.accel()) * savedNetworkVoltage
                    + data.accel() * previousVoltage;
        }
        pendingEventVoltage = Double.NaN;
        networkCorrection = 0.0;
    }

    @Override public void beforeNetworkIteration(int iteration) {
        if (networkHighVoltageEnabled) evaluateNetworkHighVoltage();
    }

    @Override public void afterNetworkConvergence() {
        if (networkHighVoltageEnabled) evaluateNetworkHighVoltage();
    }

    private void evaluateNetworkHighVoltage() {
        if (!networkSolutionInProgress) throw new IllegalStateException("Begin network solution before evaluation");
        deceleratedNetworkVoltage = (1.0 - data.accel()) * savedNetworkVoltage
                + data.accel() * Math.max(.01, getDStabBus().getVoltageMag());
        networkCorrection += data.khv() * Math.max(deceleratedNetworkVoltage - data.volim(), 0.0);
    }

    @Override public void endNetworkSolution(boolean converged) {
        if (!networkSolutionInProgress) return;
        if (!converged) {
            networkOutputAvailable = oldNetworkOutputAvailable;
            savedNetworkVoltage = oldSavedNetworkVoltage;
            networkCorrection = oldNetworkCorrection;
            deceleratedNetworkVoltage = oldDeceleratedNetworkVoltage;
            pendingEventVoltage = oldPendingEventVoltage;
        }
        networkSolutionInProgress = false;
        if (acceptedVoltageMemory()) memoryNetworkAccepted = converged;
    }

    private double activeNetworkOutput(double voltage) {
        double gainVoltage = networkOutputAvailable ? deceleratedNetworkVoltage : voltage;
        return activeCurrentOutput() * lowVoltageActiveGain(gainVoltage, data.lvpnt0(), data.lvpnt1());
    }

    private double reactiveNetworkOutput(double voltage) {
        if (networkOutputAvailable)
            return Math.min(-data.iolim(), reactiveCurrentOutput() - networkCorrection / deceleratedNetworkVoltage);
        return highVoltageReactiveOutput(reactiveCurrentOutput(), voltage, data.volim(), data.khv(), data.iolim());
    }

    @Override
    public void configureIntegrationStep(double timeStepSec) {
        integrationStep = timeStepSec;
        RenewableElectricalController controller = activeController();
        if (controller != null) controller.configureIntegrationStep(timeStepSec);
    }

    public Regca1Model(DStabGen parentGen, BaseDStabBus<?, ?> bus, String id, Regca1Data data) {
        this(parentGen, bus, id, data, "REGCA1", 0.);
    }

    /** REGCAU2 shares the control kernels but retains its explicit network reactance. */
    public Regca1Model(DStabGen parentGen, BaseDStabBus<?, ?> bus, String id, Regcau2Data data) {
        this(parentGen, bus, id, data.controls(), "REGCAU2", data.xe());
    }

    private Regca1Model(DStabGen parentGen, BaseDStabBus<?, ?> bus, String id,
            Regca1Data data, String modelName, double effectiveReactance) {
        this.data = data;
        this.modelName = modelName;
        this.effectiveReactance = effectiveReactance;
        removableCompensation = modelName.equals("REGCA1");
        voltageKey=modelName+"_V";activeCurrentKey=modelName+"_IP";reactiveCurrentKey=modelName+"_IQ";
        activePowerKey=modelName+"_P";reactivePowerKey=modelName+"_Q";
        stateMonitor = new IntegrationStateMonitor("Filtered Voltage", "Active Current Regulator",
                reactiveStateName());
        setParentGen(parentGen);
        setDStabBus(bus);
        setId(id);
        setExtendedDeviceId(modelName + "_" + id + "@" + bus.getId());
    }

    @Override
    public boolean initStates(BaseDStabBus<?, ?> bus) {
        stateMonitor.reset();
        twoStep.reset();
        resetNetworkHighVoltageMemory();
        RenewableElectricalController controller = activeController();
        if (parentGen == null || controller == null || bus == null) return false;
        Complex initial = parentGen.getGen();
        if (initial == null) return false;
        systemBaseMva = bus.getNetwork().getBaseMva();
        deviceBaseMva = parentGen.getMvaBase() > EPS ? parentGen.getMvaBase() : systemBaseMva;
        double scale = systemBaseMva / deviceBaseMva;
        double v = Math.max(0.01, bus.getVoltageMag());
        p = initial.getReal() * scale;
        q = initial.getImaginary() * scale;
        initialQ = q;
        vFiltered = v;
        double lvg = lowVoltageActiveGain(v, data.lvpnt0(), data.lvpnt1());
        ipState = lvg > EPS ? p / v / lvg : 1.0;
        // The WECC REGC_A block uses positive Iq for positive reactive-power output.
        // REEC_B supplies the opposite-signed Iqcmd to the -1/(1+sTg) regulator.
        iqState = effectiveReactance > 0. ? v + effectiveReactance * q / v : q / v;
        controller.initialize(p, q, v);
        states.put(DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID, getExtendedDeviceId());
        return finite(ipState) && finite(iqState);
    }

    /**
     * Capture a settled voltage excursion before the network applies an event.
     * This does not advance continuous states, derivative history, or timers.
     * Call after the left endpoint's algebraic refresh and before changing voltage.
     */
    public void captureNetworkEventLeftBoundary() {
        if (networkHighVoltageEnabled) {
            if (networkSolutionInProgress)
                throw new IllegalStateException("Capture the event boundary between network solutions");
            pendingEventVoltage = Math.max(.01, getDStabBus().getVoltageMag());
        }
        if (integrationScheme == IntegrationScheme.TWO_STEP && reeca1Controller != null)
            reeca1Controller.captureNetworkEventLeftBoundary(
                    Math.max(0.01, getDStabBus().getVoltageMag()));
    }

    /** Rebase bundle histories on the next positive two-step integration call. */
    public void rebaseNextTwoStep() {
        if (integrationScheme == IntegrationScheme.TWO_STEP) {
            twoStep.rebaseNextStep();
            if(activeController() instanceof TwoStepElectricalController controller)
                controller.rebaseNextTwoStep();
        }
    }

    @Override
    public boolean nextStep(double dt, DynamicSimuMethod method, int flag) {
        startVoltageMemoryStep(dt,method,flag);
        if (integrationScheme == IntegrationScheme.TWO_STEP)
            return nextTwoStep(dt, method, flag);
        double v = Math.max(0.01, getDStabBus().getVoltageMag());
        double frequency = getDStabBus().getFreq();
        // The enclosing DStab solver invokes flag 0 and flag 1 for one modified-Euler
        // step. Deliver both stages to the composed control so stage-aware REEC/REPC
        // implementations can sample the corrected network endpoint. The interface
        // default preserves the former predictor-only behavior for legacy controls.
        RenewableElectricalController controller = activeController();
        // WGO's voltage trigger measures the terminal, not the safe divisor
        // used by REGCA's current equations. Preserve the legacy input when absent.
        double controlVoltage = controller.getWeakGridController() == null
                ? v : getDStabBus().getVoltageMag();
        controller.step(dt, p, q, controlVoltage, frequency, flag);
        double ipcmd = controller.getIpcmd();
        double iqcmd = controller.getIqcmd();

        if (flag == 0) {
            oldVFiltered = vFiltered;
            oldIpState = ipState;
            oldIqState = iqState;
            dv0 = derivative(v, vFiltered, data.tfltr());
            dip0 = activeCurrentDerivative(ipcmd, ipState);
            diq0 = reactiveCurrentDerivative(iqcmd, iqState);
            stateMonitor.begin(Stage.PREDICTOR);
            stateMonitor.record(0, vFiltered, dv0);
            stateMonitor.record(1, ipState, dip0);
            stateMonitor.record(2, iqState, diq0);
            vFiltered = advance(vFiltered, v, dv0, data.tfltr(), dt);
            ipState = advance(ipState, ipcmd, dip0, data.tg(), dt);
            iqState = advance(iqState, -iqcmd, diq0, data.tg(), dt);
        } else {
            double dv1 = derivative(v, vFiltered, data.tfltr());
            double dip1 = activeCurrentDerivative(ipcmd, ipState);
            double diq1 = reactiveCurrentDerivative(iqcmd, iqState);
            stateMonitor.begin(Stage.CORRECTOR);
            stateMonitor.record(0, vFiltered, dv1);
            stateMonitor.record(1, ipState, dip1);
            stateMonitor.record(2, iqState, diq1);
            vFiltered = data.tfltr() <= EPS ? oldVFiltered : oldVFiltered + 0.5 * dt * (dv0 + dv1);
            ipState = data.tg() <= EPS ? oldIpState :
                    oldIpState + 0.5 * dt * (dip0 + dip1);
            iqState = data.tg() <= EPS ? oldIqState :
                    oldIqState + 0.5 * dt * (diq0 + diq1);
        }
        if (data.tg() > EPS) ipState = Math.min(ipState, lowVoltagePowerLimit());
        return finite(ipState) && finite(iqState);
    }

    private boolean nextTwoStep(double dt, DynamicSimuMethod method, int flag) {
        if (!(activeController() instanceof TwoStepElectricalController controller))
            throw new IllegalStateException("Electrical controller does not provide a two-step integration boundary");
        if (method != DynamicSimuMethod.MODIFIED_EULER || (flag != 0 && flag != 1)
                || !Double.isFinite(dt) || dt < 0.0)
            throw new IllegalArgumentException("Two-step REGCA1 requires finite dt and stages 0/1");
        // Read power at the solved boundary, not the preceding bus-attribute update.
        // During a two-step update flag 1 refreshes algebraic outputs only.
        if (!updateAttributes(false)) return false;
        double v = Math.max(0.01, getDStabBus().getVoltageMag());
        double frequency = getDStabBus().getFreq();
        double controlVoltage = activeController().getWeakGridController() == null
                ? v : getDStabBus().getVoltageMag();
        controller.refreshTwoStep(p,q,controlVoltage,frequency);
        if (flag == 0 && dt > 0.0) {
            double dv = derivative(v, vFiltered, data.tfltr());
            double dip = activeCurrentDerivative(controller.getTwoStepIpcmd(), ipState);
            double diq = reactiveCurrentDerivative(controller.getTwoStepIqcmd(), iqState);
            stateMonitor.begin(Stage.TWO_STEP);
            stateMonitor.record(0, vFiltered, dv);
            stateMonitor.record(1, ipState, dip);
            stateMonitor.record(2, iqState, diq);
            controller.stepTwoStep(dt,p,q,controlVoltage,frequency);
            twoStep.begin(dt);
            vFiltered = twoStep.advance(0, vFiltered, dv);
            ipState = twoStep.advance(1, ipState, dip);
            iqState = twoStep.advance(2, iqState, diq);
            twoStep.observeRates(stateMonitor);
            if (data.tg() > EPS) ipState = Math.min(ipState, lowVoltagePowerLimit());
        }
        return finite(vFiltered) && finite(ipState) && finite(iqState);
    }

    private double activeCurrentDerivative(double command, double state) {
        double derivative = derivative(command, state, data.tg());
        double lvpl = lowVoltagePowerLimit();
        if (state >= lvpl && derivative > 0.0) derivative = 0.0;
        return data.rrpwr() > 0.0 ? Math.min(data.rrpwr(), derivative) : derivative;
    }

    private double reactiveCurrentDerivative(double command, double state) {
        double derivative = derivative(reactiveRegulatorTarget(command), state, data.tg());
        // REGCA1 selects one recovery-rate boundary from the initialized
        // machine Q sign. Exact zero selects neither directional boundary;
        // for nonzero Q, zero-valued limits remain active bounds, not disable flags.
        if (initialQ > 0.0) derivative = Math.min(data.iqrmax(), derivative);
        else if (initialQ < 0.0) derivative = Math.max(data.iqrmin(), derivative);
        return derivative;
    }

    private double limitedActiveCommand(double command) {
        return Math.min(command, lowVoltagePowerLimit());
    }

    private double lowVoltagePowerLimit() {
        double voltage = getFilteredVoltage();
        if (data.lvplsw() == 0 || data.lvpl1() < 0.0 || voltage >= data.brkpt()) {
            return Double.POSITIVE_INFINITY;
        }
        if (voltage <= data.zerox()) return 0.0;
        return (voltage - data.zerox()) * data.lvpl1() / (data.brkpt() - data.zerox());
    }

    static double lowVoltageActiveGain(double value, double low, double high) {
        if (value <= low) return 0.0;
        if (value >= high) return 1.0;
        return (value - low) / (high - low);
    }

    static double highVoltageReactiveOutput(double iqState, double voltage,
            double volim, double khv, double iolim) {
        // Native PSS/E Iolim is a negative limit in the opposite current
        // convention. Here Iq is positive for generated Q, so it is an upper
        // boundary at -Iolim, not a lower boundary at Iolim. It applies also
        // below Volim; it limits the network output, never the regulator state.
        return Math.min(-iolim, iqState - khv * Math.max(0.0, voltage - volim));
    }

    private static double derivative(double input, double state, double timeConstant) {
        return timeConstant <= EPS ? 0.0 : (input - state) / timeConstant;
    }

    private static double advance(double state, double algebraicInput, double rate,
            double timeConstant, double dt) {
        return timeConstant <= EPS ? state : state + dt * rate;
    }

    @Override public boolean supportsUncompensatedNetworkSolve() {
        return removableCompensation && !networkHighVoltageEnabled && supportsDeferredCurrentPublication();
    }
    @Override public Complex networkCompensationAdmittance() {
        var z=parentGen.getPosGenZ();
        if(z==null)return Complex.ZERO;
        z=z.multiply(parentGen.getZMultiFactor());
        return z.abs()>EPS?Complex.ONE.divide(z):Complex.ZERO;
    }
    @Override public Complex uncompensatedNetworkCurrent(){return injectedCurrent(getDStabBus().getVoltage());}
    @Override public boolean supportsDeferredCurrentPublication() {
        // A bypass may read replaceable commands. These two final implementations
        // expose stored command values; custom callbacks retain ordinary dispatch.
        var controller = activeController();
        return removableCompensation && getClass() == Regca1Model.class && (data.tg() > EPS
                || controller instanceof Reeca1Model || controller instanceof ConverterCurrentCommands);
    }

    @Override public boolean supportsNetworkIterationAcceleration() {
        return removableCompensation && getClass() == Regca1Model.class && data.tg() > EPS && !networkHighVoltageEnabled;
    }

    @Override
    public Object getOutputObject() {
        Complex voltage = getDStabBus().getVoltage();
        Complex injected = injectedCurrent(voltage);
        Complex z = parentGen.getPosGenZ();
        Complex norton = injected;
        if (z != null) {
            z = z.multiply(parentGen.getZMultiFactor());
            if (z.abs() > EPS) norton = norton.add(voltage.divide(z));
        }
        return norton;
    }

    private Complex injectedCurrent(Complex voltage) {
        double ip = activeNetworkOutput(voltage.abs());
        double iq = reactiveNetworkOutput(voltage.abs());
        double theta = voltage.getArgument();
        double scale = deviceBaseMva / systemBaseMva;
        return new Complex(
                scale * (ip * Math.cos(theta) + iq * Math.sin(theta)),
                scale * (ip * Math.sin(theta) - iq * Math.cos(theta)));
    }

    @Override
    public Hashtable<String, Object> getStates(Object ref) {
        // Inactive devices can be observed before dynamic initialization.
        states.put(DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID, getExtendedDeviceId());
        states.put(voltageKey, getFilteredVoltage());
        states.put(activeCurrentKey, getIp());
        states.put(reactiveCurrentKey, getIq());
        states.put(activePowerKey, p);
        states.put(reactivePowerKey, q);
        return states;
    }

    @Override
    public boolean updateAttributes(boolean netChange) {
        if (getDStabBus() == null || deviceBaseMva <= EPS || systemBaseMva <= EPS) return false;
        Complex voltage = getDStabBus().getVoltage();
        Complex power = voltage.multiply(injectedCurrent(voltage).conjugate())
                .multiply(systemBaseMva / deviceBaseMva);
        double newP = power.getReal();
        double newQ = power.getImaginary();
        if (netChange) {
            p = 0.5 * (p + newP);
            q = 0.5 * (q + newQ);
        } else {
            p = newP;
            q = newQ;
        }
        return finite(p) && finite(q);
    }
    /** Publish solved event inputs without advancing any converter/controller history. */
    @Override public void acceptNetworkEventInput(double physicalTimeSec) {
        if (!Double.isFinite(physicalTimeSec)) throw new IllegalArgumentException("Invalid event time");
        if (!isActive() || getDStabBus() == null || !getDStabBus().isActive()
                || parentGen == null || !parentGen.isActive()
                || deviceBaseMva <= EPS || systemBaseMva <= EPS) return;
        if (networkSolutionInProgress)
            throw new IllegalStateException("Event inputs require a completed network solution");
        if (acceptedVoltageMemory()) {
            if (positiveMemoryStep) throw new IllegalStateException("Event acceptance precedes positive integration");
            if (!eventMemoryTrial && physicalTimeSec == acceptedMemoryEventTime) return;
            if (!eventMemoryTrial || !memoryNetworkAccepted || physicalTimeSec != trialMemoryEventTime)
                throw new IllegalStateException("Accept an event only after its converged network boundary");
        }
        // This is the same pure current function used by the network source.
        // Optional iteration memory has already been finalized by convergence.
        Complex voltage = getDStabBus().getVoltage();
        Complex power = voltage.multiply(injectedCurrent(voltage).conjugate())
                .multiply(systemBaseMva / deviceBaseMva);
        double acceptedP = power.getReal(), acceptedQ = power.getImaginary();
        if (!finite(acceptedP) || !finite(acceptedQ))
            throw new IllegalStateException("Nonfinite accepted converter power");
        if (acceptedVoltageMemory()) {
            if (!Double.isFinite(savedNetworkVoltage) || !Double.isFinite(voltage.abs()))
                throw new IllegalStateException("Nonfinite accepted event voltage memory");
            committedSavedVoltage = savedNetworkVoltage;
            acceptedTerminalVoltage = Math.max(.01,voltage.abs());
            acceptedMemoryEventTime = physicalTimeSec;
            eventMemoryTrial = false;
        }
        p = acceptedP;
        q = acceptedQ;
    }
    @Override public boolean afterStep(double dt) {
        if (acceptedVoltageMemory() && positiveMemoryStep) {
            if (!memoryNetworkAccepted || eventMemoryTrial || dt != preparedMemoryStep)
                throw new IllegalStateException("Accept voltage memory only after the solved physical step");
            double terminal = Math.max(.01,getDStabBus().getVoltageMag());
            if (!Double.isFinite(terminal) || !Double.isFinite(savedNetworkVoltage))
                throw new IllegalStateException("Nonfinite accepted voltage memory");
            committedSavedVoltage = savedNetworkVoltage;
            acceptedTerminalVoltage = terminal;
            acceptedMemoryStepTime = preparedMemoryTime;
            positiveMemoryStep = false;
        }
        var controller=activeController();
        if(controller!=null&&controller.getRepccController()!=null)controller.getRepccController().acceptedEndpoint();
        if(controller!=null&&controller.getHybridController()!=null)controller.getHybridController().owner().acceptedEndpoint();
        return true;
    }
    @Override public DStabGen getParentGen() { return parentGen; }

    @Override
    public void setParentGen(DStabGen parentGen) {
        this.parentGen = parentGen;
        if (parentGen != null) parentGen.setDynamicGenDevice(this);
    }

    public Regca1Data getData() { return data; }
    public String getModelName() { return modelName; }
    public double getEffectiveReactance() { return effectiveReactance; }
    public IntegrationScheme getIntegrationScheme() { return integrationScheme; }
    /**
     * Select before initialization for fixed-step TWO_STEP diagnostics. This
     * opt-in storage-precision discriminator leaves equations and case data
     * unchanged; full fleet/event accuracy has not been established.
     */
    public void setSinglePrecisionStates(boolean enabled) {
        twoStep.setSinglePrecision(enabled);
        if (reeca1Controller!=null) reeca1Controller.setSinglePrecisionStates(enabled);
    }
    /**
     * Select before initialization. TWO_STEP is an explicit verification path;
     * its commercial lag identities are verified, full fleet/event parity is open.
     */
    public void setIntegrationScheme(IntegrationScheme scheme) {
        if (effectiveReactance > 0. && scheme == IntegrationScheme.TWO_STEP)
            throw new IllegalArgumentException("Nonzero REGCAU2 Xe currently requires modified Euler");
        if(scheme==IntegrationScheme.TWO_STEP&&activeController()!=null&&activeController().getHybridController()!=null)throw new IllegalArgumentException("Shared hybrid composition currently requires staged modified Euler");
        if(scheme==IntegrationScheme.TWO_STEP&&activeController()!=null&&activeController().getRepccController()!=null)
            throw new IllegalArgumentException("REPCC uses staged modified Euler, not the converter two-step path");
        integrationScheme = java.util.Objects.requireNonNull(scheme);
        stateMonitor.reset();
        twoStep.reset();
    }
    public Reecb1Model getElectricalController() {
        return electricalController instanceof Reecb1Model controller ? controller : null;
    }
    public void setElectricalController(Reecb1Model electricalController) {
        setActiveElectricalController(electricalController);
    }
    /**
     * Connects a controller through the REGC_A signal contract. This also enables
     * equation-level verification without coupling the converter to one REEC variant.
     */
    public void setActiveElectricalController(RenewableElectricalController electricalController) {
        if(activeController()!=null&&activeController().getHybridController()!=null&&activeController()!=electricalController)throw new IllegalStateException("Rebuild the shared hybrid group before replacing a controller");
        this.electricalController = electricalController;
        this.reeca1Controller = null;
        if (electricalController != null && integrationStep > 0.0) {
            electricalController.configureIntegrationStep(integrationStep);
        }
    }
    public Reeca1Model getReeca1Controller() { return reeca1Controller; }
    public void setReeca1Controller(Reeca1Model controller) {
        if(activeController()!=null&&activeController().getHybridController()!=null&&activeController()!=controller)throw new IllegalStateException("Rebuild the shared hybrid group before replacing a controller");
        this.reeca1Controller = controller;
        this.electricalController = null;
        if (controller != null && integrationStep > 0.0) {
            controller.configureIntegrationStep(integrationStep);
        }
    }
    public RenewableElectricalController getActiveElectricalController() { return activeController(); }
    public double getFilteredVoltage() {
        return data.tfltr() <= EPS && getDStabBus() != null
                ? Math.max(.01, getDStabBus().getVoltageMag()) : vFiltered;
    }
    private double activeCurrentOutput() {
        return data.tg() <= EPS ? limitedActiveCommand(activeController().getIpcmd()) : ipState;
    }
    private double reactiveCurrentOutput() {
        double value = data.tg() <= EPS ? reactiveRegulatorTarget(activeController().getIqcmd()) : iqState;
        return effectiveReactance > 0.
                ? (value - getDStabBus().getVoltageMag()) / effectiveReactance : value;
    }

    private double reactiveRegulatorTarget(double command) {
        return effectiveReactance > 0.
                ? activeController().getReactiveVoltageCommand(getDStabBus().getVoltageMag(), effectiveReactance, command) : -command;
    }

    private String reactiveStateName() {
        return effectiveReactance > 0. ? "Reactive Voltage Regulator" : "Reactive Current Regulator";
    }
    public double getIpRegulatorState() { return ipState; }
    /** Stored current coordinate, or internal voltage when REGCAU2 Xe is nonzero. */
    public double getIqRegulatorState() { return iqState; }
    public double getIp() {
        double v = getDStabBus() == null ? vFiltered : getDStabBus().getVoltageMag();
        return activeNetworkOutput(v);
    }
    public double getIq() {
        double v = getDStabBus() == null ? vFiltered : getDStabBus().getVoltageMag();
        return reactiveNetworkOutput(v);
    }

    @Override
    public Map<String, Double> getNamedStates() {
        Map<String, Double> named = new LinkedHashMap<>();
        named.put("Filtered Voltage", getFilteredVoltage());
        named.put("Active Current Regulator", ipState);
        named.put(reactiveStateName(), iqState);
        named.put("Active Power", p);
        named.put("Reactive Power", q);
        RenewableElectricalController controller = activeController();
        if(controller!=null&&controller.getHybridController()!=null){
            var port=controller.getHybridController();
            port.owner().getPlantNamedStates().forEach((key,value)->named.put("HPLNTDU."+key,value));
            port.owner().getSwitchingNamedStates().forEach((key,value)->named.put("HPLNTDU.switching."+key,value));
            port.getNamedStates().forEach((key,value)->named.put("REAXDU."+key,value));
        }
        if (controller != null && controller.getRepccController() != null)
            controller.getRepccController().getNamedStates().forEach((key,value)->named.put("REPCC."+key,value));
        if (controller != null && controller.getWeakGridController() != null)
            controller.getWeakGridController().getNamedStates().forEach(
                    (key, value) -> named.put("WTGWGOA." + key, value));
        if (controller != null && controller.getInertialResponseController() != null)
            controller.getInertialResponseController().getNamedStates().forEach(
                    (key, value) -> named.put("WTGIBFFRA." + key, value));
        if (reeca1Controller != null && reeca1Controller.getWindControlStack() != null) {
            Wtdtb1Model drivetrain = reeca1Controller.getWindControlStack().getTorsionalEmulation();
            if (drivetrain != null) drivetrain.getNamedStates().forEach(
                    (key, value) -> named.put("WTDTB1." + key, value));
            Wtptb1Model pitch=reeca1Controller.getWindControlStack().getPitchControllerB();
            if(pitch!=null)pitch.getNamedStates().forEach((key,value)->named.put("WTPTB1."+key,value));
        }
        return Collections.unmodifiableMap(named);
    }


    @Override public java.util.Map<String, Double> getIntegrationStates() {
        return java.util.Map.of("Filtered Voltage", vFiltered,
                "Active Current Regulator", ipState,
                reactiveStateName(), iqState);
    }
    @Override public IntegrationStateMonitor.Snapshot getStateDerivativeSnapshot() {
        return stateMonitor.snapshot();
    }

    /** Stored sign selector; zero means neither directional recovery boundary. */
    public double getInitialReactivePower() { return initialQ; }
    public int getReactiveRecoveryDirection() { return initialQ > 0 ? 1 : initialQ < 0 ? -1 : 0; }

    private RenewableElectricalController activeController() {
        return reeca1Controller != null ? reeca1Controller : electricalController;
    }

    private static boolean finite(double value) { return Double.isFinite(value); }
}
