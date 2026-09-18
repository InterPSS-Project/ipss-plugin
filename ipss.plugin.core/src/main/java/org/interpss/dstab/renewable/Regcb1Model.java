package org.interpss.dstab.renewable;

import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.control.util.IntegrationStepAware;

import com.interpss.common.exp.InterpssRuntimeException;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.dstab.controller.cml.ICMLStateProvider;
import com.interpss.dstab.device.DynamicGenDevice;
import com.interpss.dstab.device.impl.DynamicBusDeviceImpl;

/** Published behind-impedance renewable converter model REGCB1. */
public final class Regcb1Model extends DynamicBusDeviceImpl
        implements DynamicGenDevice, IntegrationStepAware, ICMLStateProvider {
    private static final double EPS = 1.0e-9;
    private static final int IP = 0, IQ = 1, V = 2, EQ = 3, ED = 4;

    private final Regcb1Data data;
    private final Hashtable<String, Object> outputStates = new Hashtable<>();
    private final double[] state = new double[5];
    private final double[] trial = new double[5];
    private final double[] derivative0 = new double[5];
    private double[] active = state;
    private DStabGen parentGen;
    private RenewableElectricalController controller;
    private double deviceBaseMva;
    private double systemBaseMva;
    private double effectiveTg;
    private double ipcmd;
    private double iqcmd;
    private double initialP;
    private double initialQ;
    private double p;
    private double q;

    public Regcb1Model(DStabGen parentGen, BaseDStabBus<?, ?> bus,
            String id, Regcb1Data data) {
        if (parentGen == null || bus == null || data == null) {
            throw new IllegalArgumentException("REGCB1 requires a generator, bus, and data");
        }
        this.data = data;
        setParentGen(parentGen);
        setDStabBus(bus);
        setId(id);
        setExtendedDeviceId("REGCB1_" + id + "@" + bus.getId());
    }

    @Override
    public void configureIntegrationStep(double timeStepSec) {
        effectiveTg = Math.max(data.tg(), 2.0 * timeStepSec);
        if (controller != null) controller.configureIntegrationStep(timeStepSec);
    }

    @Override
    public boolean initStates(BaseDStabBus<?, ?> bus) {
        if (bus == null || parentGen.getGen() == null || sourceImpedance().abs() <= EPS) return false;
        systemBaseMva = bus.getNetwork().getBaseMva();
        deviceBaseMva = parentGen.getMvaBase() > EPS ? parentGen.getMvaBase() : systemBaseMva;
        if (effectiveTg <= EPS) effectiveTg = data.tg();
        double scale = systemBaseMva / deviceBaseMva;
        Complex initial = parentGen.getGen();
        initializeWithSignals(bus.getVoltageMag(), initial.getReal() * scale,
                initial.getImaginary() * scale);
        if (controller != null) controller.initialize(p, q, state[V]);
        outputStates.put(DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID, getExtendedDeviceId());
        return finite(state);
    }

    /** Initializes all five published coordinates from device-base terminal signals. */
    public void initializeWithSignals(double voltage, double realPower, double reactivePower) {
        double safeVoltage = nonzero(voltage);
        p = realPower;
        q = reactivePower;
        initialP = realPower;
        initialQ = reactivePower;
        state[V] = safeVoltage;
        state[IP] = data.rateFlag() == 1 ? realPower : realPower / safeVoltage;
        state[IQ] = reactivePower / safeVoltage;
        ipcmd = realPower / safeVoltage;
        iqcmd = -state[IQ];
        Complex target = internalVoltageTarget(realPower / safeVoltage, state[IQ], safeVoltage);
        state[ED] = target.getReal();
        state[EQ] = target.getImaginary();
        System.arraycopy(state, 0, trial, 0, state.length);
        active = state;
    }

    @Override
    public boolean nextStep(double dt, DynamicSimuMethod method, int flag) {
        if (controller != null) {
            controller.step(dt, p, q, getDStabBus().getVoltageMag(), getDStabBus().getFreq(), flag);
            ipcmd = controller.getIpcmd();
            iqcmd = controller.getIqcmd();
        }
        return nextStepWithSignals(dt, method, flag, getDStabBus().getVoltageMag(), ipcmd, iqcmd);
    }

    /** Advances one predictor/corrector stage with prescribed terminal/command inputs. */
    public boolean nextStepWithSignals(double dt, DynamicSimuMethod method, int flag,
            double voltage, double activeCommand, double reactiveCommand) {
        if (method != DynamicSimuMethod.MODIFIED_EULER) {
            throw new InterpssRuntimeException("REGCB1 supports MODIFIED_EULER only");
        }
        if (dt < 0.0 || !Double.isFinite(dt)) return false;
        effectiveTg = Math.max(data.tg(), 2.0 * dt);
        ipcmd = activeCommand;
        iqcmd = reactiveCommand;
        Signals signals = new Signals(voltage, activeCommand, reactiveCommand);
        if (flag == 0) {
            derivatives(state, signals, derivative0);
            for (int i = 0; i < state.length; i++) trial[i] = state[i] + dt * derivative0[i];
            normalize(trial, signals);
            active = trial;
        } else if (flag == 1) {
            double[] derivative1 = new double[state.length];
            derivatives(trial, signals, derivative1);
            for (int i = 0; i < state.length; i++)
                state[i] += .5 * dt * (derivative0[i] + derivative1[i]);
            normalize(state, signals);
            active = state;
        } else {
            throw new InterpssRuntimeException("REGCB1 invalid integration flag: " + flag);
        }
        return finite(active);
    }

    private void derivatives(double[] x, Signals signals, double[] dx) {
        double filteredVoltage = data.tfltr() > EPS ? x[V] : nonzero(signals.voltage);
        CurrentCommands limited = limitCurrent(signals.ipcmd, signals.iqcmd);
        double numeratorCommand = data.rateFlag() == 1
                ? limited.ip * filteredVoltage : limited.ip;
        dx[IP] = directionalActiveRate(lagRate(numeratorCommand, x[IP], effectiveTg));
        dx[IQ] = directionalReactiveRate(lagRate(-limited.iq, x[IQ], effectiveTg));
        dx[V] = lagRate(nonzero(signals.voltage), x[V], data.tfltr());

        double id = data.rateFlag() == 1 ? x[IP] / nonzero(filteredVoltage) : x[IP];
        Complex target = internalVoltageTarget(id, x[IQ], signals.voltage);
        dx[EQ] = lagRate(target.getImaginary(), x[EQ], data.te());
        dx[ED] = lagRate(target.getReal(), x[ED], data.te());
    }

    private void normalize(double[] x, Signals signals) {
        if (data.tfltr() <= EPS) x[V] = nonzero(signals.voltage);
        x[V] = Math.max(.01, x[V]);
        double id = data.rateFlag() == 1 ? x[IP] / nonzero(x[V]) : x[IP];
        Complex target = internalVoltageTarget(id, x[IQ], signals.voltage);
        if (data.te() <= EPS) {
            x[EQ] = target.getImaginary();
            x[ED] = target.getReal();
        }
    }

    private CurrentCommands limitCurrent(double ip, double iq) {
        if (data.pqFlag() == 1) {
            double limitedIp = clamp(ip, -data.imax(), data.imax());
            double iqMax = remaining(data.imax(), limitedIp);
            return new CurrentCommands(limitedIp, clamp(iq, -iqMax, iqMax));
        }
        double limitedIq = clamp(iq, -data.imax(), data.imax());
        double ipMax = remaining(data.imax(), limitedIq);
        return new CurrentCommands(clamp(ip, -ipMax, ipMax), limitedIq);
    }

    private double directionalActiveRate(double rate) {
        if (initialP >= 0.0) return Math.min(data.rrpwr(), rate);
        return Math.max(-data.rrpwr(), rate);
    }

    private double directionalReactiveRate(double rate) {
        if (initialQ > EPS) return Math.min(data.iqrmax(), rate);
        if (initialQ < -EPS) return Math.max(data.iqrmin(), rate);
        return rate;
    }

    private Complex internalVoltageTarget(double id, double iqState, double voltage) {
        Complex z = parentGen.getPosGenZ();
        double resistance = z == null ? 0.0 : z.getReal();
        double reactance = z == null ? 0.0 : z.getImaginary();
        double ed = voltage + id * resistance + iqState * reactance;
        double eq = id * reactance - iqState * resistance;
        return new Complex(ed, eq);
    }

    private Complex sourceImpedance() {
        Complex z = parentGen.getPosGenZ();
        return z == null ? Complex.ZERO : z.multiply(parentGen.getZMultiFactor());
    }

    private Complex internalVoltage(Complex terminalVoltage) {
        Complex dq = new Complex(active[ED], active[EQ]);
        return dq.multiply(new Complex(Math.cos(terminalVoltage.getArgument()),
                Math.sin(terminalVoltage.getArgument())));
    }

    private Complex unlimitedPhysicalCurrent(Complex terminalVoltage) {
        Complex z = sourceImpedance();
        if (z.abs() <= EPS) return Complex.ZERO;
        return internalVoltage(terminalVoltage).subtract(terminalVoltage).divide(z);
    }

    private Complex physicalCurrent(Complex terminalVoltage) {
        Complex systemCurrent = unlimitedPhysicalCurrent(terminalVoltage);
        double deviceScale = systemBaseMva > EPS && deviceBaseMva > EPS
                ? systemBaseMva / deviceBaseMva : 1.0;
        Complex local = systemCurrent
                .multiply(deviceScale)
                .multiply(new Complex(Math.cos(-terminalVoltage.getArgument()),
                        Math.sin(-terminalVoltage.getArgument())));
        double id = local.getReal();
        double iq = local.getImaginary();
        if (data.pqFlag() == 1) {
            id = clamp(id, -data.imax(), data.imax());
            iq = clamp(iq, -remaining(data.imax(), id), remaining(data.imax(), id));
        } else {
            iq = clamp(iq, -data.imax(), data.imax());
            id = clamp(id, -remaining(data.imax(), iq), remaining(data.imax(), iq));
        }
        return new Complex(id, iq)
                .multiply(new Complex(Math.cos(terminalVoltage.getArgument()),
                        Math.sin(terminalVoltage.getArgument())))
                .divide(deviceScale);
    }

    @Override
    public Object getOutputObject() {
        Complex z = sourceImpedance();
        if (z.abs() <= EPS) return Complex.ZERO;
        Complex voltage = getDStabBus().getVoltage();
        return physicalCurrent(voltage).add(voltage.divide(z));
    }

    @Override
    public boolean updateAttributes(boolean netChange) {
        if (systemBaseMva <= EPS || deviceBaseMva <= EPS) return false;
        Complex voltage = getDStabBus().getVoltage();
        Complex power = voltage.multiply(physicalCurrent(voltage).conjugate())
                .multiply(systemBaseMva / deviceBaseMva);
        if (netChange) {
            p = .5 * (p + power.getReal());
            q = .5 * (q + power.getImaginary());
        } else {
            p = power.getReal();
            q = power.getImaginary();
        }
        return Double.isFinite(p) && Double.isFinite(q);
    }

    @Override public boolean afterStep(double dt) { return dt >= 0.0 && Double.isFinite(dt); }

    @Override
    public Hashtable<String, Object> getStates(Object ref) {
        outputStates.put("REGCB1_P", p);
        outputStates.put("REGCB1_Q", q);
        return outputStates;
    }

    @Override
    public Map<String, Double> getNamedStates() {
        Map<String, Double> named = new LinkedHashMap<>();
        named.put("Converter lag for Ipcmd", active[IP]);
        named.put("Converter lag for Iqcmd", active[IQ]);
        named.put("Voltage measurement filter", active[V]);
        named.put("Inner-control lag for Eq", active[EQ]);
        named.put("Inner-control lag for Ed", active[ED]);
        return Map.copyOf(named);
    }

    @Override public DStabGen getParentGen() { return parentGen; }
    @Override public void setParentGen(DStabGen gen) {
        parentGen = gen;
        if (gen != null) gen.setDynamicGenDevice(this);
    }

    public Regcb1Data getData() { return data; }
    public double[] getStateSnapshot() { return active.clone(); }
    public double getP() { return p; }
    public double getQ() { return q; }
    public double getEffectiveTg() { return effectiveTg; }
    public void setCommands(double activeCommand, double reactiveCommand) {
        ipcmd = activeCommand;
        iqcmd = reactiveCommand;
    }
    public void setActiveElectricalController(RenewableElectricalController value) {
        controller = value;
    }
    public RenewableElectricalController getActiveElectricalController() { return controller; }

    private static double lagRate(double input, double state, double timeConstant) {
        return timeConstant > EPS ? (input - state) / timeConstant : 0.0;
    }
    private static double nonzero(double value) { return Math.max(.01, Math.abs(value)); }
    private static double remaining(double total, double priority) {
        return Math.sqrt(Math.max(0.0, total * total - priority * priority));
    }
    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
    private static boolean finite(double[] values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
    private record Signals(double voltage, double ipcmd, double iqcmd) { }
    private record CurrentCommands(double ip, double iq) { }
}
