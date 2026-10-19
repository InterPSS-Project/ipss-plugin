package org.interpss.dstab.renewable;

import org.interpss.dstab.control.util.IntegrationStateAccess;
import org.interpss.dstab.control.util.IntegrationStateMonitor;
import org.interpss.dstab.control.util.IntegrationStateMonitor.Stage;

import java.util.Collections;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.control.util.IntegrationStepAware;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.dstab.controller.cml.ICMLStateProvider;
import com.interpss.dstab.device.DynamicGenDevice;
import com.interpss.dstab.device.impl.DynamicBusDeviceImpl;

/**
 * Positive-sequence WECC REGC_A converter model. REECB1 and REPCA1 are
 * composed as controllers while this device owns the network current source.
 */
public final class Regca1Model extends DynamicBusDeviceImpl
        implements DynamicGenDevice, IntegrationStepAware, ICMLStateProvider, IntegrationStateAccess {
    public enum IntegrationScheme { MODIFIED_EULER, TWO_STEP }
    private static final double EPS = 1.0e-9;

    private final IntegrationStateMonitor stateMonitor = new IntegrationStateMonitor(
            "Filtered Voltage", "Active Current Regulator", "Reactive Current Regulator");
    private final Regca1Data data;
    private final Hashtable<String, Object> states = new Hashtable<>();
    private DStabGen parentGen;
    private RenewableElectricalController electricalController;
    private Reeca1Model reeca1Controller;
    private double deviceBaseMva;
    private double systemBaseMva;
    private double vFiltered;
    /** Output of the REGC_A active-current regulator, before algebraic LVG. */
    private double ipState;
    /** Output of the REGC_A reactive-current regulator, before algebraic HV logic. */
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

    @Override
    public void configureIntegrationStep(double timeStepSec) {
        integrationStep = timeStepSec;
        RenewableElectricalController controller = activeController();
        if (controller != null) controller.configureIntegrationStep(timeStepSec);
    }

    public Regca1Model(DStabGen parentGen, BaseDStabBus<?, ?> bus, String id, Regca1Data data) {
        this.data = data;
        setParentGen(parentGen);
        setDStabBus(bus);
        setId(id);
        setExtendedDeviceId("REGCA1_" + id + "@" + bus.getId());
    }

    @Override
    public boolean initStates(BaseDStabBus<?, ?> bus) {
        stateMonitor.reset();
        twoStep.reset();
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
        iqState = q / v;
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
        if (integrationScheme == IntegrationScheme.TWO_STEP && reeca1Controller != null)
            reeca1Controller.captureNetworkEventLeftBoundary(
                    Math.max(0.01, getDStabBus().getVoltageMag()));
    }

    @Override
    public boolean nextStep(double dt, DynamicSimuMethod method, int flag) {
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
        if (reeca1Controller == null)
            throw new IllegalStateException("Two-step REGCA1 requires a REECA1 controller");
        if (method != DynamicSimuMethod.MODIFIED_EULER || (flag != 0 && flag != 1)
                || !Double.isFinite(dt) || dt < 0.0)
            throw new IllegalArgumentException("Two-step REGCA1 requires finite dt and stages 0/1");
        // Read power at the solved boundary, not the preceding bus-attribute update.
        // During a two-step update flag 1 refreshes algebraic outputs only.
        if (!updateAttributes(false)) return false;
        double v = Math.max(0.01, getDStabBus().getVoltageMag());
        double frequency = getDStabBus().getFreq();
        double controlVoltage = reeca1Controller.getWeakGridController() == null
                ? v : getDStabBus().getVoltageMag();
        reeca1Controller.refreshTwoStep(p, q, controlVoltage, frequency);
        if (flag == 0 && dt > 0.0) {
            double dv = derivative(v, vFiltered, data.tfltr());
            double dip = activeCurrentDerivative(reeca1Controller.getTwoStepIpcmd(), ipState);
            double diq = reactiveCurrentDerivative(reeca1Controller.getTwoStepIqcmd(), iqState);
            stateMonitor.begin(Stage.TWO_STEP);
            stateMonitor.record(0, vFiltered, dv);
            stateMonitor.record(1, ipState, dip);
            stateMonitor.record(2, iqState, diq);
            reeca1Controller.stepTwoStep(dt, p, q, controlVoltage, frequency);
            twoStep.begin(dt);
            vFiltered = twoStep.advance(0, vFiltered, dv);
            ipState = twoStep.advance(1, ipState, dip);
            iqState = twoStep.advance(2, iqState, diq);
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
        double derivative = derivative(-command, state, data.tg());
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
        double ip = activeCurrentOutput() * lowVoltageActiveGain(voltage.abs(), data.lvpnt0(), data.lvpnt1());
        double iq = highVoltageReactiveOutput(reactiveCurrentOutput(), voltage.abs(),
                data.volim(), data.khv(), data.iolim());
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
        states.put("REGCA1_V", getFilteredVoltage());
        states.put("REGCA1_IP", getIp());
        states.put("REGCA1_IQ", getIq());
        states.put("REGCA1_P", p);
        states.put("REGCA1_Q", q);
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
    @Override public boolean afterStep(double dt) {
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
        return data.tg() <= EPS ? -activeController().getIqcmd() : iqState;
    }
    public double getIpRegulatorState() { return ipState; }
    public double getIqRegulatorState() { return iqState; }
    public double getIp() {
        double v = getDStabBus() == null ? vFiltered : getDStabBus().getVoltageMag();
        return activeCurrentOutput() * lowVoltageActiveGain(v, data.lvpnt0(), data.lvpnt1());
    }
    public double getIq() {
        double v = getDStabBus() == null ? vFiltered : getDStabBus().getVoltageMag();
        return highVoltageReactiveOutput(reactiveCurrentOutput(), v, data.volim(), data.khv(), data.iolim());
    }

    @Override
    public Map<String, Double> getNamedStates() {
        Map<String, Double> named = new LinkedHashMap<>();
        named.put("Filtered Voltage", getFilteredVoltage());
        named.put("Active Current Regulator", ipState);
        named.put("Reactive Current Regulator", iqState);
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
                "Reactive Current Regulator", iqState);
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
