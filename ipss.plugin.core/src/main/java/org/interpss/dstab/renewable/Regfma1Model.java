package org.interpss.dstab.renewable;

import java.util.Hashtable;

import org.apache.commons.math3.complex.Complex;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.dstab.device.DynamicGenDevice;
import com.interpss.dstab.device.impl.DynamicBusDeviceImpl;

/** WECC REGFM_A1 droop-controlled grid-forming inverter. */
public final class Regfma1Model extends DynamicBusDeviceImpl implements DynamicGenDevice {
    private static final double EPS = 1.0e-9;

    private final Regfma1Data data;
    private final Hashtable<String, Object> states = new Hashtable<>();
    private DStabGen parentGen;
    private double deviceBaseMva;
    private double systemBaseMva;
    private double p;
    private double q;
    private double pMeasured;
    private double qMeasured;
    private double vMeasured;
    private double pReference;
    private double qReference;
    private double vReference;
    private double prefOffset;
    private double qvOffset;
    private double angle;
    private double eDroop;
    private double voltageIntegral;
    private double pUpperIntegral;
    private double pLowerIntegral;
    private double qUpperIntegral;
    private double qLowerIntegral;
    private double speed = 1.0;
    private boolean currentLimited;

    public Regfma1Model(DStabGen parentGen, BaseDStabBus<?, ?> bus, String id,
            Regfma1Data data) {
        this.data = data;
        setParentGen(parentGen);
        setDStabBus(bus);
        setId(id);
        setExtendedDeviceId("REGFMA1_" + id + "@" + bus.getId());
        // PSS/E stores the source impedance on machine base.  The core applies
        // getZMultiFactor() when assembling the system-base dynamic Y matrix.
        parentGen.setPosGenZ(new Complex(data.re(), data.xe()));
    }

    @Override
    public boolean initStates(BaseDStabBus<?, ?> bus) {
        if (parentGen == null || bus == null || parentGen.getGen() == null) return false;
        systemBaseMva = bus.getNetwork().getBaseMva();
        deviceBaseMva = parentGen.getMvaBase() > EPS ? parentGen.getMvaBase() : systemBaseMva;
        double scale = systemBaseMva / deviceBaseMva;
        Complex voltage = bus.getVoltage();
        Complex power = parentGen.getGen().multiply(scale);
        Complex current = power.divide(nonzero(voltage)).conjugate();
        Complex internal = voltage.add(couplingImpedance().multiply(current));

        p = pMeasured = pReference = power.getReal();
        q = qMeasured = power.getImaginary();
        vMeasured = voltage.abs();
        angle = internal.getArgument();
        eDroop = internal.abs();
        qReference = data.qvflag() == 0 ? q : 0.0;
        // Vflag=0 bypasses the terminal-voltage PI controller, so the direct
        // voltage command must initialize to the internal source magnitude.
        // Vflag<>0 instead initializes Vcmd to measured terminal voltage and
        // lets the PI integrator supply the required internal voltage.
        double initializedVoltageCommand = data.vflag() == 0 ? eDroop : vMeasured;
        vReference = initializedVoltageCommand
                - data.mq() * (qReference - qMeasured);
        voltageIntegral = eDroop;
        pUpperIntegral = pLowerIntegral = qUpperIntegral = qLowerIntegral = 0.0;
        speed = 1.0;
        currentLimited = false;
        states.put(DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID, getExtendedDeviceId());
        return finite(eDroop) && finite(angle);
    }

    @Override
    public boolean nextStep(double dt, DynamicSimuMethod method, int flag) {
        // Composed renewable controls in this plugin advance on predictor calls;
        // the second modified-Euler callback is reserved for network correction.
        if (flag != 0) return true;
        Complex voltage = getDStabBus().getVoltage();
        pMeasured = lag(pMeasured, p, data.tpf(), dt);
        qMeasured = lag(qMeasured, q, data.tqf(), dt);
        vMeasured = lag(vMeasured, voltage.abs(), data.tvf(), dt);

        double pHighError = data.pmax() - pMeasured;
        double pLowError = data.pmin() - pMeasured;
        pUpperIntegral = Math.min(0.0,
                pUpperIntegral + data.kipmax() * pHighError * dt);
        pLowerIntegral = Math.max(0.0,
                pLowerIntegral + data.kipmax() * pLowError * dt);
        double pLimit = Math.min(0.0, data.kppmax() * pHighError + pUpperIntegral)
                + Math.max(0.0, data.kppmax() * pLowError + pLowerIntegral);
        double frequencyDeviation = data.mp() * (pReference + prefOffset - pMeasured) + pLimit;
        speed = 1.0 + frequencyDeviation;
        angle += 2.0 * Math.PI * getDStabBus().getNetwork().getFrequency()
                * frequencyDeviation * dt;

        double qHighError = data.qmax() - qMeasured;
        double qLowError = data.qmin() - qMeasured;
        qUpperIntegral = Math.min(0.0,
                qUpperIntegral + data.kiqmax() * qHighError * dt);
        qLowerIntegral = Math.max(0.0,
                qLowerIntegral + data.kiqmax() * qLowError * dt);
        double qLimit = Math.min(0.0, data.kpqmax() * qHighError + qUpperIntegral)
                + Math.max(0.0, data.kpqmax() * qLowError + qLowerIntegral);
        double vCommand = vReference + qvOffset
                + data.mq() * (qReference - qMeasured) + qLimit;
        if (data.vflag() == 0) {
            eDroop = limit(vCommand, data.emin(), data.emax());
        } else {
            double error = vCommand - vMeasured;
            voltageIntegral = integrateWithAntiWindup(voltageIntegral, data.kiv(), error, dt,
                    data.kpv(), data.emin(), data.emax());
            eDroop = limit(data.kpv() * error + voltageIntegral, data.emin(), data.emax());
        }
        return finite(eDroop) && finite(angle) && finite(speed);
    }

    @Override
    public Object getOutputObject() {
        Complex voltage = getDStabBus().getVoltage();
        Complex impedance = couplingImpedance();
        Complex internal = polar(eDroop, angle);
        Complex outputCurrent = internal.subtract(voltage).divide(impedance);
        currentLimited = data.imax() > EPS && outputCurrent.abs() > data.imax();
        if (currentLimited) {
            outputCurrent = polar(data.imax(), outputCurrent.getArgument());
            internal = voltage.add(impedance.multiply(outputCurrent));
        }
        Complex power = voltage.multiply(outputCurrent.conjugate());
        p = power.getReal();
        q = power.getImaginary();
        // The dynamic Y matrix contains 1/Z, so the device supplies E/Z.
        return internal.divide(impedance);
    }

    @Override
    public Hashtable<String, Object> getStates(Object ref) {
        states.put("REGFMA1_ANGLE", angle);
        states.put("REGFMA1_SPEED", speed);
        states.put("REGFMA1_E", eDroop);
        states.put("REGFMA1_P", p);
        states.put("REGFMA1_Q", q);
        states.put("REGFMA1_CURRENT_LIMITED", currentLimited ? 1.0 : 0.0);
        return states;
    }

    /** Incremental plant-controller inputs; zero preserves the initialized operating point. */
    public void setReferenceOffsets(double activePower, double reactiveOrVoltage) {
        prefOffset = activePower;
        qvOffset = reactiveOrVoltage;
    }

    static double integrateWithAntiWindup(double integral, double gain, double error, double dt,
            double proportionalGain, double lower, double upper) {
        double output = proportionalGain * error + integral;
        if ((output >= upper && error > 0.0) || (output <= lower && error < 0.0)) return integral;
        return integral + gain * error * dt;
    }

    private Complex couplingImpedance() {
        return parentGen.getPosGenZ().multiply(parentGen.getZMultiFactor());
    }

    private static double lag(double state, double input, double timeConstant, double dt) {
        return timeConstant <= EPS ? input : state + dt * (input - state) / timeConstant;
    }

    private static double limit(double value, double lower, double upper) {
        return Math.max(lower, Math.min(upper, value));
    }

    private static Complex polar(double magnitude, double angle) {
        return new Complex(magnitude * Math.cos(angle), magnitude * Math.sin(angle));
    }

    private static Complex nonzero(Complex value) {
        return value.abs() > 0.01 ? value : new Complex(0.01, 0.0);
    }

    private static boolean finite(double value) { return Double.isFinite(value); }

    @Override public boolean updateAttributes(boolean netChange) { return true; }
    @Override public boolean afterStep(double dt) { return true; }
    @Override public DStabGen getParentGen() { return parentGen; }
    @Override public void setParentGen(DStabGen parentGen) {
        this.parentGen = parentGen;
        if (parentGen != null) parentGen.setDynamicGenDevice(this);
    }

    public Regfma1Data getData() { return data; }
    public double getSpeed() { return speed; }
    public double getInternalVoltage() { return eDroop; }
    public boolean isCurrentLimited() { return currentLimited; }
}
