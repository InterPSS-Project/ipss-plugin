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

/** Published WT3G1 Type-3 converter generator and PLL realization. */
public final class Wt3g1Model extends DynamicBusDeviceImpl
        implements DynamicGenDevice, ICMLStateProvider {
    private static final double EPS = 1.0e-10;
    private static final double CONVERTER_LAG = 0.020;

    private final Wt3g1Data data;
    private final Hashtable<String, Object> states = new Hashtable<>();
    private DStabGen parentGen;
    private double deviceBaseMva;
    private double systemBaseMva;
    private double ipState;
    private double eqState;
    private double pllIntegral;
    private double angle;
    private double ipCommand;
    private double eqCommand;
    private double p;
    private double q;
    private double oldIpState;
    private double oldEqState;
    private double oldPllIntegral;
    private double oldAngle;
    private Derivatives predictorDerivative = new Derivatives(0.0, 0.0, 0.0, 0.0);
    private boolean initialized;
    private Wt3e1Model electricalController;

    public Wt3g1Model(DStabGen parentGen, BaseDStabBus<?, ?> bus, String id,
            Wt3g1Data data) {
        this.data = data;
        setParentGen(parentGen);
        setDStabBus(bus);
        setId(id);
        setExtendedDeviceId("WT3G1_" + id + "@" + bus.getId());
    }

    @Override
    public boolean initStates(BaseDStabBus<?, ?> bus) {
        if (parentGen == null || bus == null) return false;
        Complex initialPower = parentGen.getGen();
        if (initialPower == null) return false;
        systemBaseMva = bus.getNetwork().getBaseMva();
        deviceBaseMva = parentGen.getMvaBase() > EPS ? parentGen.getMvaBase() : systemBaseMva;
        double deviceScale = systemBaseMva / deviceBaseMva;
        Complex voltage = bus.getVoltage();
        double magnitude = voltage.abs();
        if (magnitude <= EPS) return false;
        p = initialPower.getReal() * deviceScale;
        q = initialPower.getImaginary() * deviceScale;
        ipState = p / magnitude;
        eqState = magnitude + data.equivalentReactance() * q / magnitude;
        pllIntegral = 0.0;
        angle = voltage.getArgument();
        ipCommand = ipState;
        eqCommand = eqState;
        initialized = true;
        if (electricalController != null) electricalController.initialize();
        states.put(DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID, getExtendedDeviceId());
        return finite(ipState) && finite(eqState) && finite(angle);
    }

    @Override
    public boolean nextStep(double dt, DynamicSimuMethod method, int flag) {
        if (!initialized || method != DynamicSimuMethod.MODIFIED_EULER || dt <= 0.0) {
            return false;
        }
        if (electricalController != null) electricalController.step(dt, flag);
        Derivatives current = derivatives(ipState, eqState, pllIntegral, angle);
        if (flag == 0) {
            oldIpState = ipState;
            oldEqState = eqState;
            oldPllIntegral = pllIntegral;
            oldAngle = angle;
            predictorDerivative = current;
            ipState = oldIpState + dt * current.ip;
            eqState = oldEqState + dt * current.eq;
            pllIntegral = boundedAdvance(oldPllIntegral, dt * current.pllIntegral,
                    -data.pllMaximum(), data.pllMaximum());
            angle = oldAngle + dt * current.angle;
        } else {
            ipState = oldIpState + 0.5 * dt * (predictorDerivative.ip + current.ip);
            eqState = oldEqState + 0.5 * dt * (predictorDerivative.eq + current.eq);
            pllIntegral = boundedAdvance(oldPllIntegral,
                    0.5 * dt * (predictorDerivative.pllIntegral + current.pllIntegral),
                    -data.pllMaximum(), data.pllMaximum());
            angle = oldAngle + 0.5 * dt * (predictorDerivative.angle + current.angle);
        }
        return finite(ipState) && finite(eqState) && finite(pllIntegral) && finite(angle);
    }

    private Derivatives derivatives(double ip, double eq, double integral, double delta) {
        Complex generatorVoltage = getDStabBus().getVoltage()
                .multiply(new Complex(Math.cos(delta), -Math.sin(delta)));
        double omega0 = 2.0 * Math.PI * getDStabBus().getNetwork().getFrequency();
        double proportional = data.pllGain() * generatorVoltage.getImaginary() / omega0;
        double integralDerivative = data.pllIntegratorGain() * proportional;
        if ((integral >= data.pllMaximum() && integralDerivative > 0.0)
                || (integral <= -data.pllMaximum() && integralDerivative < 0.0)) {
            integralDerivative = 0.0;
        }
        double frequencyDeviation = clamp(proportional + integral,
                -data.pllMaximum(), data.pllMaximum());
        return new Derivatives((ipCommand - ip) / CONVERTER_LAG,
                (eqCommand - eq) / CONVERTER_LAG,
                integralDerivative, omega0 * frequencyDeviation);
    }

    @Override
    public Object getOutputObject() {
        if (!initialized || systemBaseMva <= EPS) {
            Complex z = parentGen == null ? null : parentGen.getPosGenZ();
            return z == null || z.abs() <= EPS || getDStabBus() == null
                    ? Complex.ZERO
                    : getDStabBus().getVoltage().divide(z.multiply(parentGen.getZMultiFactor()));
        }
        Complex sourceInGeneratorFrame = new Complex(ipState,
                -eqState / data.equivalentReactance());
        Complex sourceInNetworkFrame = sourceInGeneratorFrame.multiply(
                new Complex(Math.cos(angle), Math.sin(angle)));
        return sourceInNetworkFrame.multiply(deviceBaseMva / systemBaseMva);
    }

    @Override
    public boolean updateAttributes(boolean netChange) {
        if (!initialized || getDStabBus() == null) return false;
        Complex voltage = getDStabBus().getVoltage();
        Complex source = (Complex) getOutputObject();
        Complex z = parentGen.getPosGenZ();
        if (z == null || z.abs() <= EPS) return false;
        Complex physicalCurrent = source.subtract(voltage.divide(z.multiply(parentGen.getZMultiFactor())));
        Complex power = voltage.multiply(physicalCurrent.conjugate())
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

    public void setCommands(double activeCurrentCommand, double internalVoltageCommand) {
        if (!finite(activeCurrentCommand) || !finite(internalVoltageCommand)) {
            throw new IllegalArgumentException("WT3G1 commands must be finite");
        }
        ipCommand = activeCurrentCommand;
        eqCommand = internalVoltageCommand;
    }

    @Override
    public Hashtable<String, Object> getStates(Object ref) {
        states.putAll(getNamedStates());
        Complex generatorVoltage = generatorVoltage();
        Complex generatorCurrent = generatorCurrent();
        states.put("WT3G1 Vx", generatorVoltage.getReal());
        states.put("WT3G1 Vy", generatorVoltage.getImaginary());
        states.put("WT3G1 Ixinj", generatorCurrent.getReal());
        states.put("WT3G1 Iyinj", generatorCurrent.getImaginary());
        states.put("WT3G1 P", p);
        states.put("WT3G1 Q", q);
        return states;
    }

    @Override
    public Map<String, Double> getNamedStates() {
        if (!initialized) return Map.of();
        Map<String, Double> named = new LinkedHashMap<>();
        named.put("Converter lag for Ipcmd", ipState);
        named.put("Converter lag for Eqcmd", eqState);
        named.put("PLL first integrator", pllIntegral);
        named.put("PLL second integrator", angle);
        return Map.copyOf(named);
    }

    private Complex generatorVoltage() {
        return getDStabBus().getVoltage().multiply(
                new Complex(Math.cos(angle), -Math.sin(angle)));
    }

    private Complex generatorCurrent() {
        return new Complex(ipState, -eqState / data.equivalentReactance());
    }

    @Override public boolean afterStep(double dt) { return true; }
    @Override public DStabGen getParentGen() { return parentGen; }
    @Override public void setParentGen(DStabGen value) {
        parentGen = value;
        if (value != null) value.setDynamicGenDevice(this);
    }

    public Wt3g1Data getData() { return data; }
    public double getActiveCurrentState() { return ipState; }
    public double getInternalVoltageState() { return eqState; }
    public double getPllIntegralState() { return pllIntegral; }
    public double getPllAngleState() { return angle; }
    public double getP() { return p; }
    public double getQ() { return q; }
    public Wt3e1Model getElectricalController() { return electricalController; }
    public void setElectricalController(Wt3e1Model value) { electricalController = value; }

    private static double boundedAdvance(double initial, double increment,
            double minimum, double maximum) {
        return clamp(initial + increment, minimum, maximum);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static boolean finite(double value) { return Double.isFinite(value); }

    private record Derivatives(double ip, double eq, double pllIntegral, double angle) { }
}
