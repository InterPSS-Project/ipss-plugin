package org.interpss.dstab.mach;

import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.dstab.controller.cml.ICMLStateProvider;
import com.interpss.dstab.device.DynamicGenDevice;
import com.interpss.dstab.device.impl.DynamicBusDeviceImpl;

/** Published Type-4 current-source generator with LVPL and HVRC behavior. */
public final class Wt4g1Model extends DynamicBusDeviceImpl
        implements DynamicGenDevice, ICMLStateProvider {
    private static final double EPS = 1.0e-10;

    private final Wt4g1Data data;
    private final Hashtable<String, Object> states = new Hashtable<>();
    private DStabGen parentGen;
    private double deviceBaseMva;
    private double systemBaseMva;
    private double ipState;
    private double iqState;
    private double filteredVoltage;
    private double ipCommand;
    private double iqCommand;
    private double p;
    private double q;
    private double deltaQ;
    private State oldState;
    private Derivative predictor;
    private Wt4e1Model electricalController;
    private boolean initialized;

    public Wt4g1Model(DStabGen parentGen, BaseDStabBus<?, ?> bus, String id,
            Wt4g1Data data) {
        this.data = data;
        setParentGen(parentGen);
        setDStabBus(bus);
        setId(id);
        setExtendedDeviceId("WT4G1_" + id + "@" + bus.getId());
    }

    @Override
    public boolean initStates(BaseDStabBus<?, ?> bus) {
        if (parentGen == null || bus == null || parentGen.getGen() == null) return false;
        systemBaseMva = bus.getNetwork().getBaseMva();
        deviceBaseMva = parentGen.getMvaBase() > EPS ? parentGen.getMvaBase() : systemBaseMva;
        double voltage = bus.getVoltageMag();
        if (voltage <= EPS || systemBaseMva <= EPS || deviceBaseMva <= EPS) return false;
        double scale = systemBaseMva / deviceBaseMva;
        p = parentGen.getGen().getReal() * scale;
        q = parentGen.getGen().getImaginary() * scale;
        ipState = p / voltage;
        iqState = q / voltage;
        filteredVoltage = voltage;
        ipCommand = ipState;
        iqCommand = iqState;
        deltaQ = highVoltageCorrection(voltage, data.highVoltageThreshold(),
                data.highVoltageReactiveCurrentGain());
        initialized = true;
        if (electricalController != null) electricalController.initialize();
        states.put(DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID, getExtendedDeviceId());
        return finite(ipState) && finite(iqState) && finite(filteredVoltage);
    }

    @Override
    public boolean nextStep(double dt, DynamicSimuMethod method, int flag) {
        if (!initialized || method != DynamicSimuMethod.MODIFIED_EULER || dt <= 0.0
                || (flag != 0 && flag != 1)) return false;
        if (electricalController != null) electricalController.step(dt, flag);
        State current = state();
        Derivative derivative = derivatives(current);
        if (flag == 0) {
            oldState = current;
            predictor = derivative;
            apply(advance(oldState, predictor, dt));
        } else {
            if (oldState == null || predictor == null) return false;
            apply(advance(oldState, predictor.add(derivative), .5 * dt));
            oldState = null;
            predictor = null;
        }
        if (data.activeCommandTime() <= EPS) ipState = ipCommand;
        if (data.reactiveCommandTime() <= EPS) iqState = iqCommand;
        if (data.lvplVoltageFilterTime() <= EPS) filteredVoltage = getDStabBus().getVoltageMag();
        ipState = Math.min(ipState, lowVoltagePowerLimit(filteredVoltage));
        return finite(ipState) && finite(iqState) && finite(filteredVoltage);
    }

    private Derivative derivatives(State state) {
        double ipRate = rate(ipCommand, state.ip, data.activeCommandTime());
        ipRate = directionalRecoveryRate(state.ip, ipRate,
                data.activeCurrentRecoveryRate());
        return new Derivative(ipRate,
                rate(iqCommand, state.iq, data.reactiveCommandTime()),
                rate(getDStabBus().getVoltageMag(), state.filteredVoltage,
                        data.lvplVoltageFilterTime()));
    }

    @Override
    public Object getOutputObject() {
        if (getDStabBus() == null || parentGen == null) return Complex.ZERO;
        Complex voltage = getDStabBus().getVoltage();
        Complex norton = injectedCurrent(voltage);
        Complex impedance = parentGen.getPosGenZ();
        if (impedance != null) {
            impedance = impedance.multiply(parentGen.getZMultiFactor());
            if (impedance.abs() > EPS) norton = norton.add(voltage.divide(impedance));
        }
        return norton;
    }

    private Complex injectedCurrent(Complex voltage) {
        double magnitude = voltage.abs();
        double activeCurrent = ipState * fixedLowVoltageActiveGain(magnitude);
        deltaQ = highVoltageCorrection(magnitude, data.highVoltageThreshold(),
                data.highVoltageReactiveCurrentGain());
        double reactiveCurrent = iqState - deltaQ;
        double angle = voltage.getArgument();
        double scale = systemBaseMva > EPS ? deviceBaseMva / systemBaseMva : 0.0;
        return new Complex(
                scale * (activeCurrent * Math.cos(angle) + reactiveCurrent * Math.sin(angle)),
                scale * (activeCurrent * Math.sin(angle) - reactiveCurrent * Math.cos(angle)));
    }

    @Override
    public boolean updateAttributes(boolean netChange) {
        if (!initialized || getDStabBus() == null) return false;
        Complex voltage = getDStabBus().getVoltage();
        Complex power = voltage.multiply(injectedCurrent(voltage).conjugate())
                .multiply(systemBaseMva / deviceBaseMva);
        double newP = power.getReal();
        double newQ = power.getImaginary();
        if (netChange) {
            p = .5 * (p + newP);
            q = .5 * (q + newQ);
        } else {
            p = newP;
            q = newQ;
        }
        return finite(p) && finite(q);
    }

    public void setCommands(double activeCurrentCommand, double reactiveCurrentCommand) {
        if (!finite(activeCurrentCommand) || !finite(reactiveCurrentCommand)) {
            throw new IllegalArgumentException("WT4G1 commands must be finite");
        }
        ipCommand = activeCurrentCommand;
        iqCommand = reactiveCurrentCommand;
    }

    public double lowVoltagePowerLimit(double voltage) {
        if (voltage <= data.lvplVoltage1()) return 0.0;
        if (voltage >= data.lvplVoltage2()) return data.lvplGain();
        return data.lvplGain() * (voltage - data.lvplVoltage1())
                / (data.lvplVoltage2() - data.lvplVoltage1());
    }

    /** Fixed low-voltage active-current reduction: zero at 0.4 pu and unity at 0.8 pu. */
    public static double fixedLowVoltageActiveGain(double voltage) {
        return clamp((voltage - .4) / .4, 0.0, 1.0);
    }

    public static double highVoltageCorrection(double voltage, double threshold, double gain) {
        return gain * Math.max(0.0, voltage - threshold);
    }

    /** Applies the sign-dependent active-current recovery-rate boundary. */
    public static double directionalRecoveryRate(double activeCurrent,
            double unconstrainedRate, double recoveryRate) {
        double result = unconstrainedRate;
        if (activeCurrent >= 0.0) result = Math.min(result, recoveryRate);
        if (activeCurrent <= 0.0) result = Math.max(result, -recoveryRate);
        return result;
    }

    @Override
    public Hashtable<String, Object> getStates(Object ref) {
        states.putAll(getNamedStates());
        states.put("WT4G1 P", p);
        states.put("WT4G1 Q", q);
        states.put("WT4G1 deltaQ", deltaQ);
        return states;
    }

    @Override
    public Map<String, Double> getNamedStates() {
        if (!initialized) return Map.of();
        Map<String, Double> named = new LinkedHashMap<>();
        named.put("Converter lag for Ipcmd", ipState);
        named.put("Converter lag for Eqcmd", iqState);
        named.put("Voltage sensor for LVPL", filteredVoltage);
        return Map.copyOf(named);
    }

    private State state() { return new State(ipState, iqState, filteredVoltage); }
    private void apply(State state) {
        ipState = state.ip;
        iqState = state.iq;
        filteredVoltage = state.filteredVoltage;
    }
    private static State advance(State state, Derivative derivative, double step) {
        return new State(state.ip + step * derivative.ip,
                state.iq + step * derivative.iq,
                state.filteredVoltage + step * derivative.filteredVoltage);
    }
    private static double rate(double input, double state, double time) {
        return time <= EPS ? 0.0 : (input - state) / time;
    }
    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
    private static boolean finite(double value) { return Double.isFinite(value); }

    @Override public boolean afterStep(double dt) { return true; }
    @Override public DStabGen getParentGen() { return parentGen; }
    @Override public void setParentGen(DStabGen value) {
        parentGen = value;
        if (value != null) value.setDynamicGenDevice(this);
    }
    public Wt4g1Data getData() { return data; }
    public double getP() { return p; }
    public double getQ() { return q; }
    public double getActiveCurrentState() { return ipState; }
    public double getReactiveCurrentState() { return iqState; }
    public double getFilteredVoltageState() { return filteredVoltage; }
    public double getDeltaQ() { return deltaQ; }
    public Wt4e1Model getElectricalController() { return electricalController; }
    public void setElectricalController(Wt4e1Model value) { electricalController = value; }

    private record State(double ip, double iq, double filteredVoltage) { }
    private record Derivative(double ip, double iq, double filteredVoltage) {
        Derivative add(Derivative other) {
            return new Derivative(ip + other.ip, iq + other.iq,
                    filteredVoltage + other.filteredVoltage);
        }
    }
}
