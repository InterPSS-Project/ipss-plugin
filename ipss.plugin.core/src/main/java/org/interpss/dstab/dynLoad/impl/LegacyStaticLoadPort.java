package org.interpss.dstab.dynLoad.impl;

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.dynLoad.Cmldznu2Data;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.common.DStabSimuException;
import com.interpss.dstab.device.NetworkIterationAware;

/** Static composite current port with solve-scoped low-voltage preparation. */
class LegacyStaticLoadPort extends LegacyStaticLoadModel implements NetworkIterationAware {
    private static final double SWITCH_VOLTAGE = .7;
    private final Complex ownedShunt;
    private double conductance = Double.NaN, susceptance = Double.NaN;
    private double trialConductance, trialSusceptance;
    private Complex precedingPower, trialPower;
    private boolean solving;

    LegacyStaticLoadPort(BaseDStabBus<?, ?> bus, String id, Cmldznu2Data data,
            double assignedPower, Complex ownedShunt) {
        this(bus, id, data, assignedPower, ownedShunt, 1);
    }

    LegacyStaticLoadPort(BaseDStabBus<?, ?> bus, String id, Cmldznu2Data data,
            double assignedPower, Complex ownedShunt, double fraction) {
        super(bus, id, fraction, data, assignedPower);
        this.ownedShunt = java.util.Objects.requireNonNull(ownedShunt);
        if (ownedShunt.isNaN() || ownedShunt.isInfinite())
            throw new IllegalArgumentException("A finite owned shunt is required");
    }

    @Override public boolean initStates() {
        if (!super.initStates()) return false;
        // Cancel only this composite's numerical matrix pair. The original
        // physical shunt and its current remain in the network equations.
        setEquivY(ownedShunt.negate());
        conductance = susceptance = Double.NaN;
        precedingPower = getInitLoadPQ();
        solving = false;
        return true;
    }

    @Override public void beginNetworkSolution(Context context) throws DStabSimuException {
        if (solving) throw new DStabSimuException("A static-load network solve is already active");
        trialConductance = conductance;
        trialSusceptance = susceptance;
        trialPower = precedingPower;
        solving = true;
    }

    @Override public void beforeNetworkIteration(int iteration) throws DStabSimuException {
        if (!solving) throw new DStabSimuException("Static-load network preparation requires begin");
        double voltage = preparationVoltage();
        if (!Double.isFinite(voltage)) throw new DStabSimuException("Nonfinite static-load voltage");
        if (voltage < SWITCH_VOLTAGE && voltage > 1e-12 && Double.isNaN(trialConductance)) {
            double squared = voltage * voltage;
            trialConductance = trialPower.getReal() / squared;
            trialSusceptance = -trialPower.getImaginary() / squared;
            if (!Double.isFinite(trialConductance) || !Double.isFinite(trialSusceptance))
                throw new DStabSimuException("Nonfinite prepared static-load admittance");
            preparedAdmittance(trialConductance, trialSusceptance);
        } else if (voltage >= SWITCH_VOLTAGE) trialPower = unscaledPolynomialPower();
    }

    /** The omitted-distribution port prepares directly from its terminal iterate. */
    protected double preparationVoltage() { return getDStabBus().getVoltageMag(); }

    /** An expanded distribution port may condition its numerical matrix pair. */
    protected void preparedAdmittance(double g, double b) { }

    private Complex unscaledPolynomialPower() {
        double scale = 1 + accumulatedLoadChangeFactor;
        Complex power = super.effectivePower();
        return scale == 1 ? power : scale == 0 ? precedingPower : power.divide(scale);
    }

    @Override Complex effectivePower() {
        double g = solving ? trialConductance : conductance;
        double b = solving ? trialSusceptance : susceptance;
        double voltage = getDStabBus().getVoltageMag();
        if (voltage < SWITCH_VOLTAGE && !Double.isNaN(g)) {
            double scale = voltage * voltage * (1 + accumulatedLoadChangeFactor);
            return new Complex(g * scale, -b * scale);
        }
        return super.effectivePower();
    }

    @Override public Complex getNortonCurInj() {
        Complex voltage = getDStabBus().getVoltage();
        if (voltage.abs() <= 1e-12) return Complex.ZERO;
        Complex power = effectivePower(), y = getEquivY();
        if (voltage.isNaN() || voltage.isInfinite() || power.isNaN() || power.isInfinite()
                || y.isNaN() || y.isInfinite()) return super.getNortonCurInj();
        setLoadPQ(power);
        double vr = voltage.getReal(), vi = voltage.getImaginary();
        double p = power.getReal(), q = power.getImaginary(), real, imaginary;
        // Preserve the numerical library's scaled division and operation order.
        if (Math.abs(vr) < Math.abs(vi)) {
            double ratio = vr / vi, denominator = vr * ratio + vi;
            real = (p * ratio + q) / denominator;
            imaginary = (q * ratio - p) / denominator;
        } else {
            double ratio = vi / vr, denominator = vi * ratio + vr;
            real = (q * ratio + p) / denominator;
            imaginary = (q - p * ratio) / denominator;
        }
        Complex injection = new Complex(y.getReal() * vr - y.getImaginary() * vi - real,
                y.getReal() * vi + y.getImaginary() * vr + imaginary);
        setNortonCurInj(injection);
        return injection;
    }

    @Override public void afterNetworkConvergence() {
        // Publish at the accepted voltage; getters do not prepare or commit G/B.
        getNortonCurInj();
        if (getDStabBus().getVoltageMag() >= SWITCH_VOLTAGE) trialPower = unscaledPolynomialPower();
    }

    @Override public void endNetworkSolution(boolean converged) {
        if (solving && converged) {
            conductance = trialConductance;
            susceptance = trialSusceptance;
            precedingPower = trialPower;
        }
        solving = false;
        trialPower = null;
    }

    @Override public Map<String, Double> getNamedStates() {
        var states = new LinkedHashMap<>(super.getNamedStates());
        states.put("STATIC.LowVoltageG", Double.isNaN(conductance) ? 0 : conductance);
        states.put("STATIC.LowVoltageB", Double.isNaN(susceptance) ? 0 : susceptance);
        states.put("STATIC.PrecedingP", precedingPower == null ? 0 : precedingPower.getReal());
        states.put("STATIC.PrecedingQ", precedingPower == null ? 0 : precedingPower.getImaginary());
        return Map.copyOf(states);
    }
}
