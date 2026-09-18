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

/** Published WT3G2 Type-3 converter with LVPL, HVRC, and PLL dynamics. */
public final class Wt3g2Model extends DynamicBusDeviceImpl
        implements DynamicGenDevice, ICMLStateProvider, Wt3GeneratorModel {
    private static final double EPS = 1.0e-10;

    private final Wt3g2Data data;
    private final Hashtable<String, Object> states = new Hashtable<>();
    private DStabGen parentGen;
    private double deviceBaseMva;
    private double systemBaseMva;
    private double equivalentReactance;
    private double ipState;
    private double eqState;
    private double pllIntegral;
    private double angle;
    private double filteredVoltage;
    private double ipCommand;
    private double eqCommand;
    private double p;
    private double q;
    private double deltaQ;
    private State oldState;
    private Derivative predictor;
    private boolean initialized;
    private Wt3e1Model electricalController;
    private Wt3t1Model driveTrain;

    public Wt3g2Model(DStabGen parentGen, BaseDStabBus<?, ?> bus, String id,
            Wt3g2Data data) {
        this.data = data;
        setParentGen(parentGen);
        setDStabBus(bus);
        setId(id);
        setExtendedDeviceId("WT3G2_" + id + "@" + bus.getId());
    }

    @Override
    public boolean initStates(BaseDStabBus<?, ?> bus) {
        if (parentGen == null || bus == null || parentGen.getGen() == null) return false;
        Complex z = parentGen.getPosGenZ();
        if (z == null || Math.abs(z.getImaginary()) <= EPS) return false;
        equivalentReactance = z.getImaginary() * parentGen.getZMultiFactor();
        if (Math.abs(equivalentReactance) <= EPS) return false;
        systemBaseMva = bus.getNetwork().getBaseMva();
        deviceBaseMva = parentGen.getMvaBase() > EPS ? parentGen.getMvaBase() : systemBaseMva;
        double scale = systemBaseMva / deviceBaseMva;
        double voltage = bus.getVoltageMag();
        if (voltage <= EPS) return false;
        p = parentGen.getGen().getReal() * scale;
        q = parentGen.getGen().getImaginary() * scale;
        ipState = p / voltage;
        eqState = voltage + equivalentReactance * q / voltage;
        pllIntegral = 0.0;
        angle = bus.getVoltage().getArgument();
        filteredVoltage = voltage;
        ipCommand = ipState;
        eqCommand = eqState;
        deltaQ = 0.0;
        initialized = true;
        if (electricalController != null) electricalController.initialize();
        if (driveTrain != null) driveTrain.initialize(p,
                1.0 + (electricalController == null ? 0.0
                        : electricalController.getSpeedDeviation()),
                bus.getNetwork().getFrequency());
        states.put(DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID, getExtendedDeviceId());
        return finite(ipState) && finite(eqState) && finite(angle);
    }

    @Override
    public boolean nextStep(double dt, DynamicSimuMethod method, int flag) {
        if (!initialized || method != DynamicSimuMethod.MODIFIED_EULER || dt <= 0.0
                || (flag != 0 && flag != 1)) return false;
        if (driveTrain != null) {
            driveTrain.step(dt, p, flag);
            if (electricalController != null) {
                electricalController.setSpeedDeviation(driveTrain.getGeneratorSpeed() - 1.0);
            }
        }
        if (electricalController != null) electricalController.step(dt, flag);
        State current = state();
        Derivative derivative = derivatives(current);
        if (flag == 0) {
            oldState = current;
            predictor = derivative;
            apply(advance(oldState, predictor, dt));
        } else {
            apply(advance(oldState, predictor.add(derivative), .5 * dt));
            oldState = null;
            predictor = null;
        }
        ipState = Math.min(ipState, lowVoltagePowerLimit(filteredVoltage));
        pllIntegral = clamp(pllIntegral, -data.pllMaximum(), data.pllMaximum());
        return finite(ipState) && finite(eqState) && finite(pllIntegral)
                && finite(angle) && finite(filteredVoltage);
    }

    private Derivative derivatives(State s) {
        Complex voltage = generatorVoltage(s.angle);
        double omega0 = 2.0 * Math.PI * getDStabBus().getNetwork().getFrequency();
        double proportional = data.pllGain() * voltage.getImaginary() / omega0;
        double integralRate = data.pllIntegratorGain() * proportional;
        if ((s.pllIntegral >= data.pllMaximum() && integralRate > 0.0)
                || (s.pllIntegral <= -data.pllMaximum() && integralRate < 0.0)) {
            integralRate = 0.0;
        }
        double frequencyDeviation = clamp(proportional + s.pllIntegral,
                -data.pllMaximum(), data.pllMaximum());
        double ipRate = rate(ipCommand, s.ip, data.activeCommandTime());
        ipRate = directionalRecoveryRate(s.ip, ipRate,
                data.activeCurrentRecoveryRate());
        return new Derivative(ipRate,
                rate(eqCommand, s.eq, data.reactiveCommandTime()),
                integralRate, omega0 * frequencyDeviation,
                rate(getDStabBus().getVoltageMag(), s.filteredVoltage,
                        data.lvplVoltageFilterTime()));
    }

    @Override
    public Object getOutputObject() {
        if (!initialized || systemBaseMva <= EPS) {
            Complex z = parentGen == null ? null : parentGen.getPosGenZ();
            return z == null || z.abs() <= EPS || getDStabBus() == null
                    ? Complex.ZERO
                    : getDStabBus().getVoltage().divide(z.multiply(parentGen.getZMultiFactor()));
        }
        double voltage = getDStabBus().getVoltageMag();
        deltaQ = highVoltageCorrection(voltage, data.highVoltageThreshold(),
                data.highVoltageReactiveCurrentGain());
        double lowVoltageActiveGain = fixedLowVoltageActiveGain(voltage);
        Complex source = new Complex(ipState * lowVoltageActiveGain,
                -(eqState / equivalentReactance - deltaQ));
        return source.multiply(new Complex(Math.cos(angle), Math.sin(angle)))
                .multiply(deviceBaseMva / systemBaseMva);
    }

    @Override
    public boolean updateAttributes(boolean netChange) {
        if (!initialized || getDStabBus() == null) return false;
        Complex voltage = getDStabBus().getVoltage();
        Complex source = (Complex) getOutputObject();
        Complex z = parentGen.getPosGenZ();
        if (z == null || z.abs() <= EPS) return false;
        Complex physicalCurrent = source.subtract(
                voltage.divide(z.multiply(parentGen.getZMultiFactor())));
        Complex power = voltage.multiply(physicalCurrent.conjugate())
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

    @Override
    public void setCommands(double activeCurrentCommand, double internalVoltageCommand) {
        if (!finite(activeCurrentCommand) || !finite(internalVoltageCommand)) {
            throw new IllegalArgumentException("WT3G2 commands must be finite");
        }
        ipCommand = activeCurrentCommand;
        eqCommand = internalVoltageCommand;
    }

    public double lowVoltagePowerLimit(double voltage) {
        if (voltage <= data.lvplVoltage1()) return 0.0;
        if (voltage >= data.lvplVoltage2()) return data.lvplGain();
        return data.lvplGain() * (voltage - data.lvplVoltage1())
                / (data.lvplVoltage2() - data.lvplVoltage1());
    }

    /** Fixed WT3G2 low-voltage active-current logic: zero at 0.4 pu, unity at 0.8 pu. */
    public static double fixedLowVoltageActiveGain(double voltage) {
        return clamp((voltage - .4) / .4, 0.0, 1.0);
    }

    public static double highVoltageCorrection(double voltage, double threshold, double gain) {
        return gain * Math.max(0.0, voltage - threshold);
    }

    /**
     * Applies the published sign-dependent RIp_LVPL recovery boundary. Positive
     * current is upward-rate limited; negative current is downward-rate limited.
     */
    public static double directionalRecoveryRate(double activeCurrent,
            double unconstrainedRate, double recoveryRate) {
        double rate = unconstrainedRate;
        if (activeCurrent >= 0.0) rate = Math.min(rate, recoveryRate);
        if (activeCurrent <= 0.0) rate = Math.max(rate, -recoveryRate);
        return rate;
    }

    @Override
    public Hashtable<String, Object> getStates(Object ref) {
        states.putAll(getNamedStates());
        states.put("WT3G2 P", p);
        states.put("WT3G2 Q", q);
        states.put("WT3G2 deltaQ", deltaQ);
        return states;
    }

    @Override
    public Map<String, Double> getNamedStates() {
        if (!initialized) return Map.of();
        Map<String, Double> named = new LinkedHashMap<>();
        named.put("Converter lag for Ipcmd", ipState);
        named.put("Converter lag for Iqcmd", eqState);
        named.put("PLL first integrator", pllIntegral);
        named.put("PLL second integrator", angle);
        named.put("Voltage sensor for LVPL", filteredVoltage);
        if (driveTrain != null) named.putAll(driveTrain.getNamedStates());
        return Map.copyOf(named);
    }

    private State state() { return new State(ipState, eqState, pllIntegral, angle, filteredVoltage); }
    private void apply(State s) {
        ipState = s.ip;
        eqState = s.eq;
        pllIntegral = s.pllIntegral;
        angle = s.angle;
        filteredVoltage = s.filteredVoltage;
    }
    private Complex generatorVoltage(double delta) {
        return getDStabBus().getVoltage().multiply(new Complex(Math.cos(delta), -Math.sin(delta)));
    }
    private static State advance(State s, Derivative d, double h) {
        return new State(s.ip + h*d.ip, s.eq + h*d.eq,
                s.pllIntegral + h*d.pllIntegral, s.angle + h*d.angle,
                s.filteredVoltage + h*d.filteredVoltage);
    }
    private static double rate(double input, double state, double time) {
        return time <= EPS ? 0.0 : (input - state) / time;
    }
    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
    private static boolean finite(double value) { return Double.isFinite(value); }

    @Override public boolean afterStep(double dt) { return true; }
    @Override public DStabGen getParentGen() { return parentGen; }
    @Override public void setParentGen(DStabGen value) {
        parentGen = value;
        if (value != null) value.setDynamicGenDevice(this);
    }
    public Wt3g2Data getData() { return data; }
    @Override public double getAggregateRatedMw() { return data.aggregateRatedMw(); }
    @Override public double getP() { return p; }
    @Override public double getQ() { return q; }
    @Override public double getInternalVoltageState() { return eqState; }
    public double getActiveCurrentState() { return ipState; }
    public double getPllIntegralState() { return pllIntegral; }
    public double getPllAngleState() { return angle; }
    public double getFilteredVoltageState() { return filteredVoltage; }
    public double getDeltaQ() { return deltaQ; }
    @Override public Wt3e1Model getElectricalController() { return electricalController; }
    @Override public void setElectricalController(Wt3e1Model value) { electricalController = value; }
    @Override public Wt3t1Model getDriveTrain() { return driveTrain; }
    @Override public void setDriveTrain(Wt3t1Model value) { driveTrain = value; }

    private record State(double ip, double eq, double pllIntegral, double angle,
            double filteredVoltage) { }
    private record Derivative(double ip, double eq, double pllIntegral, double angle,
            double filteredVoltage) {
        Derivative add(Derivative other) {
            return new Derivative(ip + other.ip, eq + other.eq,
                    pllIntegral + other.pllIntegral, angle + other.angle,
                    filteredVoltage + other.filteredVoltage);
        }
    }
}
