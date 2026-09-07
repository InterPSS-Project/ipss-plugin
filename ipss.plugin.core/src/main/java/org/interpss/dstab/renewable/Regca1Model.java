package org.interpss.dstab.renewable;

import java.util.Hashtable;

import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.control.util.IntegrationStepAware;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.dstab.device.DynamicGenDevice;
import com.interpss.dstab.device.impl.DynamicBusDeviceImpl;

/**
 * Positive-sequence WECC REGC_A converter model. REECB1 and REPCA1 are
 * composed as controllers while this device owns the network current source.
 */
public final class Regca1Model extends DynamicBusDeviceImpl
        implements DynamicGenDevice, IntegrationStepAware {
    private static final double EPS = 1.0e-9;

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

    @Override
    public boolean nextStep(double dt, DynamicSimuMethod method, int flag) {
        double v = Math.max(0.01, getDStabBus().getVoltageMag());
        double frequency = getDStabBus().getFreq();
        // The enclosing DStab solver invokes flag 0 and flag 1 for one modified-Euler
        // step. Deliver both stages to the composed control so stage-aware REEC/REPC
        // implementations can sample the corrected network endpoint. The interface
        // default preserves the former predictor-only behavior for legacy controls.
        RenewableElectricalController controller = activeController();
        controller.step(dt, p, q, v, frequency, flag);
        double ipcmd = controller.getIpcmd();
        double iqcmd = controller.getIqcmd();

        if (flag == 0) {
            oldVFiltered = vFiltered;
            oldIpState = ipState;
            oldIqState = iqState;
            dv0 = derivative(v, vFiltered, data.tfltr());
            dip0 = activeCurrentDerivative(ipcmd, ipState);
            diq0 = reactiveCurrentDerivative(iqcmd, iqState);
            vFiltered = advance(vFiltered, v, dv0, data.tfltr(), dt);
            ipState = advance(ipState, ipcmd, dip0, data.tg(), dt);
            iqState = advance(iqState, -iqcmd, diq0, data.tg(), dt);
        } else {
            double dv1 = derivative(v, vFiltered, data.tfltr());
            double dip1 = activeCurrentDerivative(ipcmd, ipState);
            double diq1 = reactiveCurrentDerivative(iqcmd, iqState);
            vFiltered = data.tfltr() <= EPS ? v : oldVFiltered + 0.5 * dt * (dv0 + dv1);
            ipState = data.tg() <= EPS ? limitedActiveCommand(ipcmd) :
                    oldIpState + 0.5 * dt * (dip0 + dip1);
            iqState = data.tg() <= EPS ? -iqcmd :
                    oldIqState + 0.5 * dt * (diq0 + diq1);
        }
        ipState = Math.min(ipState, lowVoltagePowerLimit());
        return finite(ipState) && finite(iqState);
    }

    private double activeCurrentDerivative(double command, double state) {
        double derivative = derivative(command, state, data.tg());
        double lvpl = lowVoltagePowerLimit();
        if (state >= lvpl && derivative > 0.0) derivative = 0.0;
        return data.rrpwr() > 0.0 ? Math.min(data.rrpwr(), derivative) : derivative;
    }

    private double reactiveCurrentDerivative(double command, double state) {
        double derivative = derivative(-command, state, data.tg());
        if (initialQ > EPS && data.iqrmax() > 0.0) derivative = Math.min(data.iqrmax(), derivative);
        if (initialQ < -EPS && data.iqrmin() < 0.0) derivative = Math.max(data.iqrmin(), derivative);
        return derivative;
    }

    private double limitedActiveCommand(double command) {
        return Math.min(command, lowVoltagePowerLimit());
    }

    private double lowVoltagePowerLimit() {
        if (data.lvplsw() == 0 || data.lvpl1() < 0.0 || vFiltered >= data.brkpt()) {
            return Double.POSITIVE_INFINITY;
        }
        if (vFiltered <= data.zerox()) return 0.0;
        return (vFiltered - data.zerox()) * data.lvpl1() / (data.brkpt() - data.zerox());
    }

    static double lowVoltageActiveGain(double value, double low, double high) {
        if (value <= low) return 0.0;
        if (value >= high) return 1.0;
        return (value - low) / (high - low);
    }

    static double highVoltageReactiveOutput(double iqState, double voltage,
            double volim, double khv, double iolim) {
        return Math.max(iolim, iqState - khv * Math.max(0.0, voltage - volim));
    }

    private static double derivative(double input, double state, double timeConstant) {
        return timeConstant <= EPS ? 0.0 : (input - state) / timeConstant;
    }

    private static double advance(double state, double algebraicInput, double rate,
            double timeConstant, double dt) {
        return timeConstant <= EPS ? algebraicInput : state + dt * rate;
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
        double ip = ipState * lowVoltageActiveGain(voltage.abs(), data.lvpnt0(), data.lvpnt1());
        double iq = highVoltageReactiveOutput(iqState, voltage.abs(),
                data.volim(), data.khv(), data.iolim());
        double theta = voltage.getArgument();
        double scale = deviceBaseMva / systemBaseMva;
        return new Complex(
                scale * (ip * Math.cos(theta) + iq * Math.sin(theta)),
                scale * (ip * Math.sin(theta) - iq * Math.cos(theta)));
    }

    @Override
    public Hashtable<String, Object> getStates(Object ref) {
        states.put("REGCA1_V", vFiltered);
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
    @Override public boolean afterStep(double dt) { return true; }
    @Override public DStabGen getParentGen() { return parentGen; }

    @Override
    public void setParentGen(DStabGen parentGen) {
        this.parentGen = parentGen;
        if (parentGen != null) parentGen.setDynamicGenDevice(this);
    }

    public Regca1Data getData() { return data; }
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
        this.electricalController = electricalController;
        this.reeca1Controller = null;
        if (electricalController != null && integrationStep > 0.0) {
            electricalController.configureIntegrationStep(integrationStep);
        }
    }
    public Reeca1Model getReeca1Controller() { return reeca1Controller; }
    public void setReeca1Controller(Reeca1Model controller) {
        this.reeca1Controller = controller;
        this.electricalController = null;
        if (controller != null && integrationStep > 0.0) {
            controller.configureIntegrationStep(integrationStep);
        }
    }
    public RenewableElectricalController getActiveElectricalController() { return activeController(); }
    public double getFilteredVoltage() { return vFiltered; }
    public double getIpRegulatorState() { return ipState; }
    public double getIqRegulatorState() { return iqState; }
    public double getIp() {
        double v = getDStabBus() == null ? vFiltered : getDStabBus().getVoltageMag();
        return ipState * lowVoltageActiveGain(v, data.lvpnt0(), data.lvpnt1());
    }
    public double getIq() {
        double v = getDStabBus() == null ? vFiltered : getDStabBus().getVoltageMag();
        return highVoltageReactiveOutput(iqState, v, data.volim(), data.khv(), data.iolim());
    }

    private RenewableElectricalController activeController() {
        return reeca1Controller != null ? reeca1Controller : electricalController;
    }

    private static boolean finite(double value) { return Double.isFinite(value); }
}
