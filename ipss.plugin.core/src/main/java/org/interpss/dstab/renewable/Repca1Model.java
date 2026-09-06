package org.interpss.dstab.renewable;

import org.apache.commons.math3.complex.Complex;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import org.interpss.numeric.datatype.Unit.UnitType;

/**
 * WECC REPC_A plant controller. Its outputs are incremental Pref/Qext commands
 * consumed by a REEC_A or REEC_B controller; all internal powers are on converter MVA base.
 */
public final class Repca1Model {
    private static final double EPS = 1.0e-9;

    private final Repca1Data data;
    private final Regca1Model converter;
    private BaseDStabBus<?, ?> regulatedBus;
    private AclfBranch monitoredBranch;
    private boolean branchStoredInRequestedDirection;
    private boolean zeroBranchFallback;
    private double systemBaseMva = 100.0;
    private double deviceBaseMva = 100.0;

    private double pext;
    private double qext;
    private double pMeasured;
    private double qOrVMeasured;
    private double pIntegral;
    private double qIntegral;
    private double leadLagState;
    private double pLagState;
    private double pReference;
    private double qvReference;
    private double effectivePmax;
    private double effectivePmin;
    private double effectiveQmax;
    private double effectiveQmin;

    public Repca1Model(Repca1Data data) {
        this(data, null);
    }

    public Repca1Model(Repca1Data data, Regca1Model converter) {
        this.data = data;
        this.converter = converter;
    }

    public void initialize(double p, double q, double v) {
        resolveMeasurements();
        Measurement measurement = measure(p, q, v, 1.0);
        pReference = pMeasured = measurement.p();
        qvReference = qOrVMeasured = reactiveMeasurement(measurement);

        // REPC_A outputs are increments into REEC_B, hence both PI paths must
        // initialize to zero even when measured P and Q are nonzero.
        pext = qext = pIntegral = qIntegral = leadLagState = pLagState = 0.0;
        effectivePmax = Math.max(data.pmax(), 0.0);
        effectivePmin = Math.min(data.pmin(), 0.0);
        effectiveQmax = Math.max(data.qmax(), 0.0);
        effectiveQmin = Math.min(data.qmin(), 0.0);
    }

    public void step(double dt, double p, double q, double v, double frequency) {
        Measurement measurement = measure(p, q, v, frequency);
        pMeasured = lag(pMeasured, measurement.p(), data.tp(), dt);
        qOrVMeasured = lag(qOrVMeasured, reactiveMeasurement(measurement), data.tfltr(), dt);

        double qError = qvReference - qOrVMeasured;
        qError = limit(deadband(qError, data.dbd1(), data.dbd2()), data.emin(), data.emax());
        // WECC/PowerWorld freeze the plant integrator below Vfrz; the proportional
        // path remains live, which is distinct from holding the entire output.
        boolean freezeIntegrator = measurement.vMag() < data.vfrz();
        qIntegral = integrateWithAntiWindup(qIntegral, data.ki(), qError, dt,
                data.kp(), effectiveQmin, effectiveQmax, freezeIntegrator);
        double qPi = limit(data.kp() * qError + qIntegral, effectiveQmin, effectiveQmax);
        qext = leadLag(qPi, dt);

        if (data.fFlag() == 1) {
            double fError = deadband(1.0 - measurement.frequency(), data.fdbd1(), data.fdbd2());
            double droop = fError >= 0.0 ? data.dup() * fError : data.ddn() * fError;
            double pError = limit(pReference - pMeasured + droop, data.femin(), data.femax());
            pIntegral = integrateWithAntiWindup(pIntegral, data.kig(), pError, dt,
                    data.kpg(), effectivePmin, effectivePmax, false);
            double pPi = limit(data.kpg() * pError + pIntegral, effectivePmin, effectivePmax);
            pLagState = lag(pLagState, pPi, data.tg(), dt);
            pext = pLagState;
        } else {
            pext = pLagState = 0.0;
        }
    }

    private void resolveMeasurements() {
        if (converter == null) return;
        BaseDStabBus<?, ?> localBus = converter.getDStabBus();
        systemBaseMva = localBus.getNetwork().getBaseMva();
        deviceBaseMva = converter.getParentGen().getMvaBase() > EPS
                ? converter.getParentGen().getMvaBase() : systemBaseMva;
        BaseDStabNetwork<?, ?> network = (BaseDStabNetwork<?, ?>) localBus.getNetwork();
        regulatedBus = data.remoteBus() == 0 ? localBus : network.getDStabBus(busId(data.remoteBus()));
        if (regulatedBus == null) {
            throw new IllegalStateException("REPCA1 remote bus not found: " + data.remoteBus());
        }

        zeroBranchFallback = data.branchFromBus() == 0 && data.branchToBus() == 0;
        if (zeroBranchFallback) return;
        String from = busId(data.branchFromBus());
        String to = busId(data.branchToBus());
        monitoredBranch = (AclfBranch) localBus.getNetwork().getBranch(from, to, data.branchId());
        branchStoredInRequestedDirection = monitoredBranch != null;
        if (monitoredBranch == null) {
            monitoredBranch = (AclfBranch) localBus.getNetwork().getBranch(to, from, data.branchId());
        }
        if (monitoredBranch == null) {
            throw new IllegalStateException("REPCA1 monitored branch not found: "
                    + data.branchFromBus() + "-" + data.branchToBus() + "-" + data.branchId());
        }
    }

    private Measurement measure(double localP, double localQ, double localV, double localFrequency) {
        if (converter == null) {
            Complex voltage = regulatedBus == null ? new Complex(localV, 0.0) : regulatedBus.getVoltage();
            double f = regulatedBus == null ? localFrequency : regulatedBus.getFreq();
            return new Measurement(localP, localQ, voltage, Complex.ZERO, f);
        }
        if (zeroBranchFallback) {
            // Compatibility with ANDES for BUS1=BUS2=0: retain the local/remote
            // voltage and frequency signals but define branch P/Q/I as zero.
            return new Measurement(0.0, 0.0, regulatedBus.getVoltage(), Complex.ZERO,
                    regulatedBus.getFreq());
        }
        Complex sSystem = branchStoredInRequestedDirection
                ? monitoredBranch.powerFrom2To(UnitType.PU) : monitoredBranch.powerTo2From(UnitType.PU);
        Complex branchVoltage = branchStoredInRequestedDirection
                ? ((BaseDStabBus<?, ?>) monitoredBranch.getFromBus()).getVoltage()
                : ((BaseDStabBus<?, ?>) monitoredBranch.getToBus()).getVoltage();
        // InterPSS branch flow is system-base PU. Convert it to the generator/model
        // base before Kc, Rc/Xc, droop, or PI calculations (the direct equivalent
        // of PowerWorld's model-MVA/PUflag normalization).
        double scale = systemBaseMva / deviceBaseMva;
        // The network API supplies system-base values. Materialize the input in
        // the basis declared by PUflag, then normalize it for model calculations.
        Complex declaredInput = data.puFlag() == 0 ? sSystem : sSystem.multiply(scale);
        Complex sDevice = toModelBase(declaredInput, data.puFlag(), systemBaseMva, deviceBaseMva);
        Complex currentDevice = sDevice.divide(nonzero(branchVoltage)).conjugate();
        return new Measurement(sDevice.getReal(), sDevice.getImaginary(), regulatedBus.getVoltage(),
                currentDevice, regulatedBus.getFreq());
    }

    private double reactiveMeasurement(Measurement measurement) {
        if (data.refFlag() == 0) return measurement.q();
        if (data.vcFlag() == 1) {
            // A zero Rc/Xc selects the actual monitored-line impedance, matching
            // the WECC/ANDES parameter-selection rule.
            Complex lineZ = monitoredBranch == null ? Complex.ZERO : monitoredBranch.getZ();
            double rc = Math.abs(data.rc()) > EPS ? data.rc() : lineZ.getReal();
            double xc = Math.abs(data.xc()) > EPS ? data.xc() : lineZ.getImaginary();
            Complex drop = new Complex(rc, xc).multiply(measurement.current());
            return measurement.voltage().subtract(drop).abs();
        }
        return measurement.vMag() + data.kc() * measurement.q();
    }

    private double leadLag(double input, double dt) {
        if (data.tfv() <= EPS) {
            leadLagState = input;
            return input;
        }
        leadLagState = lag(leadLagState, input, data.tfv(), dt);
        double ratio = data.tft() / data.tfv();
        return ratio * input + (1.0 - ratio) * leadLagState;
    }

    static double integrateWithAntiWindup(double integral, double gain, double error, double dt,
            double proportionalGain, double lower, double upper, boolean frozen) {
        if (frozen || dt <= 0.0) return integral;
        double output = proportionalGain * error + integral;
        if ((output >= upper && error > 0.0) || (output <= lower && error < 0.0)) return integral;
        return integral + gain * error * dt;
    }

    static Complex toModelBase(Complex value, int puFlag, double systemBase, double modelBase) {
        return puFlag == 0 ? value.multiply(systemBase / modelBase) : value;
    }

    public Repca1Data getData() { return data; }
    public double getPref() { return pext; }
    public double getQref() { return qext; }
    public double getMeasuredActivePower() { return pMeasured; }
    public double getMeasuredReactiveOrVoltage() { return qOrVMeasured; }
    public double getActiveControlIntegral() { return pIntegral; }
    public double getReactiveControlIntegral() { return qIntegral; }
    public double getLeadLagState() { return leadLagState; }
    public double getActiveLagState() { return pLagState; }
    public boolean isUsingZeroBranchFallback() { return zeroBranchFallback; }
    public double getDeviceBaseMva() { return deviceBaseMva; }

    static double lag(double state, double input, double timeConstant, double dt) {
        return timeConstant <= EPS ? input : state + dt * (input - state) / timeConstant;
    }

    static double deadband(double value, double lower, double upper) {
        if (value < lower) return value - lower;
        if (value > upper) return value - upper;
        return 0.0;
    }

    static double limit(double value, double lower, double upper) {
        return Math.max(lower, Math.min(upper, value));
    }

    private static Complex nonzero(Complex value) {
        return value.abs() > 0.01 ? value : new Complex(0.01, 0.0);
    }

    private static String busId(int number) { return "Bus" + number; }

    private record Measurement(double p, double q, Complex voltage, Complex current, double frequency) {
        double vMag() { return voltage.abs(); }
    }
}
