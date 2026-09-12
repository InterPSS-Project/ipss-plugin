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

/** Published three-state GEWTGCU1 wind generator/converter realization. */
public final class Gewtgcu1Model extends DynamicBusDeviceImpl
        implements DynamicGenDevice, ICMLStateProvider {
    private static final double EPS = 1.0e-10;
    private static final double CONVERTER_LAG = 0.020;

    private final Gewtgcu1Data data;
    private final Hashtable<String, Object> states = new Hashtable<>();
    private DStabGen parentGen;
    private double deviceBaseMva;
    private double systemBaseMva;
    private double ipState;
    private double reactiveState;
    private double filteredVoltage;
    private double ipCommand;
    private double reactiveCommand;
    private double p;
    private double q;
    private double deltaQ;
    private State oldState;
    private Derivative predictor;
    private Gewtecu1Model electricalController;
    private Gewt2mu1Model driveTrain;
    private Gewtaru1Model aerodynamicModel;
    private boolean initialized;

    public Gewtgcu1Model(DStabGen parentGen, BaseDStabBus<?, ?> bus, String id,
            Gewtgcu1Data data) {
        this.data = data;
        setParentGen(parentGen);
        setDStabBus(bus);
        setId(id);
        setExtendedDeviceId("GEWTGCU1_" + id + "@" + bus.getId());
    }

    @Override
    public boolean initStates(BaseDStabBus<?, ?> bus) {
        if (parentGen == null || bus == null || parentGen.getGen() == null) return false;
        systemBaseMva = bus.getNetwork().getBaseMva();
        deviceBaseMva = parentGen.getMvaBase() > EPS ? parentGen.getMvaBase() : systemBaseMva;
        double voltage = bus.getVoltageMag();
        if (voltage <= EPS || deviceBaseMva <= EPS || systemBaseMva <= EPS) return false;
        double scale = systemBaseMva / deviceBaseMva;
        p = parentGen.getGen().getReal() * scale;
        q = parentGen.getGen().getImaginary() * scale;
        ipState = p / voltage;
        reactiveState = data.fullConverter()
                ? q / voltage
                : voltage + data.equivalentReactance() * q / voltage;
        ipCommand = ipState;
        reactiveCommand = reactiveState;
        filteredVoltage = compensatedVoltage(voltage, reactiveState);
        initialized = true;
        if (driveTrain != null) {
            driveTrain.initialize(p, 1.0, bus.getNetwork().getFrequency());
        }
        if (aerodynamicModel != null) {
            double speed = driveTrain == null ? 1.0 : driveTrain.getTurbineSpeed();
            aerodynamicModel.initialize(p, speed, data.turbineRatedMw());
            if (driveTrain != null) driveTrain.setAerodynamicPower(
                    aerodynamicModel.getMechanicalPower(speed));
        }
        if (electricalController != null && !electricalController.initialize()) return false;
        if (electricalController != null && driveTrain != null) {
            electricalController.setRotorSpeed(driveTrain.getGeneratorSpeed());
        }
        states.put(DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID, getExtendedDeviceId());
        return finite(ipState) && finite(reactiveState) && finite(filteredVoltage);
    }

    @Override
    public boolean nextStep(double dt, DynamicSimuMethod method, int flag) {
        if (!initialized || method != DynamicSimuMethod.MODIFIED_EULER || dt <= 0.0
                || (flag != 0 && flag != 1)) return false;
        if (electricalController != null) {
            if (driveTrain != null) electricalController.setRotorSpeed(
                    driveTrain.getGeneratorSpeed());
            if (!electricalController.step(dt, flag)) return false;
        }
        State current = state();
        Derivative derivative = derivatives(current);
        if (flag == 0) {
            oldState = current;
            predictor = derivative;
            apply(advance(oldState, predictor, dt));
        } else {
            if (oldState == null || predictor == null) return false;
            apply(advance(oldState, predictor.add(derivative), 0.5 * dt));
            oldState = null;
            predictor = null;
        }
        if (data.voltageSensorTime() <= EPS) {
            filteredVoltage = compensatedVoltage(getDStabBus().getVoltageMag(), reactiveState);
        }
        ipState = Math.min(ipState, activeCurrentLimit(filteredVoltage,
                getDStabBus().getVoltageMag()));
        if (driveTrain != null) {
            if (aerodynamicModel != null) {
                double speed = driveTrain.getTurbineSpeed();
                aerodynamicModel.step(dt, speed, flag);
                driveTrain.setAerodynamicPower(aerodynamicModel.getMechanicalPower(speed));
            }
            driveTrain.step(dt, p, flag);
        }
        return finite(ipState) && finite(reactiveState) && finite(filteredVoltage);
    }

    private Derivative derivatives(State state) {
        double voltage = Math.max(EPS, getDStabBus().getVoltageMag());
        double ipRate = (ipCommand - state.ip) / CONVERTER_LAG;
        if (ipCommand > state.ip && data.activeCurrentRecoveryRate() > 0.0) {
            ipRate = Math.min(ipRate, data.activeCurrentRecoveryRate());
        }
        return new Derivative(ipRate,
                (reactiveCommand - state.reactive) / CONVERTER_LAG,
                data.voltageSensorTime() <= EPS ? 0.0
                        : (compensatedVoltage(voltage, state.reactive) - state.filteredVoltage)
                                / data.voltageSensorTime());
    }

    public double activeCurrentLimit(double filtered, double terminalVoltage) {
        if (!data.fullConverter()) {
            return dfigLvplLimit(filtered) / Math.max(data.lowVoltageActiveVoltage1(),
                    Math.max(EPS, terminalVoltage));
        }
        return threePointPower(filtered) / Math.max(EPS, terminalVoltage);
    }

    public double dfigLvplLimit(double voltage) {
        if (voltage <= data.lvplVoltage1()) return 0.0;
        double maximum = data.lvplGain() * (data.lvplVoltage2() - data.lvplVoltage1());
        if (voltage >= data.lvplVoltage2()) return maximum;
        return data.lvplGain() * (voltage - data.lvplVoltage1());
    }

    public double lowVoltageActiveGain(double voltage) {
        if (voltage <= data.lowVoltageActiveVoltage1()) {
            return 0.0;
        }
        if (voltage >= data.lowVoltageActiveVoltage2()) return 1.0;
        double lowGain = data.lowVoltageActiveVoltage1() / data.lowVoltageActiveVoltage2();
        return lowGain + (1.0 - lowGain)
                * (voltage - data.lowVoltageActiveVoltage1())
                / (data.lowVoltageActiveVoltage2() - data.lowVoltageActiveVoltage1());
    }

    public double threePointPower(double voltage) {
        if (voltage <= data.curveVoltage1()) return data.curvePower1();
        if (voltage <= data.curveVoltage2()) {
            return interpolate(voltage, data.curveVoltage1(), data.curvePower1(),
                    data.curveVoltage2(), data.curvePower2());
        }
        if (voltage <= data.curveVoltage3()) {
            return interpolate(voltage, data.curveVoltage2(), data.curvePower2(),
                    data.curveVoltage3(), data.curvePower3());
        }
        return data.curvePower3();
    }

    public double highVoltageReactiveCurrent(double voltage) {
        if (voltage <= 1.0 || data.highVoltageReactiveCurrent2() <= 0.0) return 0.0;
        if (data.highVoltageReactiveVoltage2() <= 1.0) return data.highVoltageReactiveCurrent2();
        return Math.min(data.highVoltageReactiveCurrent2(),
                data.highVoltageReactiveCurrent2() * (voltage - 1.0)
                        / (data.highVoltageReactiveVoltage2() - 1.0));
    }

    private double compensatedVoltage(double voltage, double reactive) {
        return data.fullConverter()
                ? voltage - data.compensationReactance() * reactive : voltage;
    }

    private Complex injectedCurrent(Complex voltage) {
        double magnitude = Math.max(EPS, voltage.abs());
        double active = ipState * (data.fullConverter() ? 1.0 : lowVoltageActiveGain(magnitude));
        deltaQ = highVoltageReactiveCurrent(magnitude);
        double reactive = data.fullConverter()
                ? reactiveState - deltaQ
                : (reactiveState - magnitude) / data.equivalentReactance() - deltaQ;
        double angle = voltage.getArgument();
        double scale = deviceBaseMva / systemBaseMva;
        return new Complex(scale * (active * Math.cos(angle) + reactive * Math.sin(angle)),
                scale * (active * Math.sin(angle) - reactive * Math.cos(angle)));
    }

    @Override
    public Object getOutputObject() {
        if (!initialized || getDStabBus() == null) return Complex.ZERO;
        Complex voltage = getDStabBus().getVoltage();
        Complex norton = injectedCurrent(voltage);
        Complex impedance = parentGen.getPosGenZ();
        if (impedance != null) {
            impedance = impedance.multiply(parentGen.getZMultiFactor());
            if (impedance.abs() > EPS) norton = norton.add(voltage.divide(impedance));
        }
        return norton;
    }

    @Override
    public boolean updateAttributes(boolean netChange) {
        if (!initialized || getDStabBus() == null) return false;
        Complex voltage = getDStabBus().getVoltage();
        Complex power = voltage.multiply(injectedCurrent(voltage).conjugate())
                .multiply(systemBaseMva / deviceBaseMva);
        double nextP = power.getReal();
        double nextQ = power.getImaginary();
        p = netChange ? 0.5 * (p + nextP) : nextP;
        q = netChange ? 0.5 * (q + nextQ) : nextQ;
        return finite(p) && finite(q);
    }

    public void setCommands(double activeCurrentCommand, double reactiveCommand) {
        if (!finite(activeCurrentCommand) || !finite(reactiveCommand)) {
            throw new IllegalArgumentException("GEWTGCU1 commands must be finite");
        }
        ipCommand = activeCurrentCommand;
        this.reactiveCommand = reactiveCommand;
    }

    @Override
    public Hashtable<String, Object> getStates(Object ref) {
        states.putAll(getNamedStates());
        states.put("GEWTGCU1 P", p);
        states.put("GEWTGCU1 Q", q);
        states.put("GEWTGCU1 deltaQ", deltaQ);
        return states;
    }

    @Override
    public Map<String, Double> getNamedStates() {
        if (!initialized) return Map.of();
        Map<String, Double> named = new LinkedHashMap<>();
        named.put("Converter lag for Ipcmd", ipState);
        named.put(data.fullConverter() ? "Converter lag for Iqcmd" : "Converter lag for Eqcmd",
                reactiveState);
        named.put("Voltage sensor for LVPL", filteredVoltage);
        if (driveTrain != null) named.putAll(driveTrain.getNamedStates());
        if (aerodynamicModel != null) named.putAll(aerodynamicModel.getNamedStates());
        return Map.copyOf(named);
    }

    private State state() { return new State(ipState, reactiveState, filteredVoltage); }
    private void apply(State value) {
        ipState = value.ip;
        reactiveState = value.reactive;
        filteredVoltage = value.filteredVoltage;
    }
    private static State advance(State state, Derivative derivative, double step) {
        return new State(state.ip + step * derivative.ip,
                state.reactive + step * derivative.reactive,
                state.filteredVoltage + step * derivative.filteredVoltage);
    }
    private static double interpolate(double x, double x1, double y1, double x2, double y2) {
        return y1 + (y2 - y1) * (x - x1) / (x2 - x1);
    }
    private static boolean finite(double value) { return Double.isFinite(value); }

    @Override public boolean afterStep(double dt) { return true; }
    @Override public DStabGen getParentGen() { return parentGen; }
    @Override public void setParentGen(DStabGen value) {
        parentGen = value;
        if (value != null) value.setDynamicGenDevice(this);
    }
    public Gewtgcu1Data getData() { return data; }
    public double getP() { return p; }
    public double getQ() { return q; }
    public double getActiveCurrentState() { return ipState; }
    public double getReactiveState() { return reactiveState; }
    public double getFilteredVoltageState() { return filteredVoltage; }
    public double getDeltaQ() { return deltaQ; }
    public double getDeviceBaseMva() { return deviceBaseMva; }
    public double getAggregateRatedMw() { return data.aggregateRatedMw(); }
    public Gewtecu1Model getElectricalController() { return electricalController; }
    public void setElectricalController(Gewtecu1Model value) { electricalController = value; }
    public Gewt2mu1Model getDriveTrain() { return driveTrain; }
    public void setDriveTrain(Gewt2mu1Model value) { driveTrain = value; }
    public Gewtaru1Model getAerodynamicModel() { return aerodynamicModel; }
    public void setAerodynamicModel(Gewtaru1Model value) { aerodynamicModel = value; }

    private record State(double ip, double reactive, double filteredVoltage) { }
    private record Derivative(double ip, double reactive, double filteredVoltage) {
        Derivative add(Derivative other) {
            return new Derivative(ip + other.ip, reactive + other.reactive,
                    filteredVoltage + other.filteredVoltage);
        }
    }
}
