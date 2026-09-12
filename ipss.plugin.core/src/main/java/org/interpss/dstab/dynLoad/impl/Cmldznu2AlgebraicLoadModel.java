package org.interpss.dstab.dynLoad.impl;

import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.dynLoad.Cmldznu2Data;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.ICMLStateProvider;
import com.interpss.dstab.dynLoad.impl.DynLoadModelImpl;

/** Algebraic static or electronic component embedded in CMLDZNU2. */
final class Cmldznu2AlgebraicLoadModel extends DynLoadModelImpl implements ICMLStateProvider {
    enum Kind { STATIC, ELECTRONIC }

    private static final double EPS = 1.0e-12;
    private final Kind kind;
    private final Cmldznu2Data data;
    private final double fraction;
    private final Hashtable<String, Object> states = new Hashtable<>();
    private double initialVoltage;
    private double p0;
    private double q0;
    private double minimumElectronicFraction = 1.0;
    private double electronicFraction = 1.0;

    Cmldznu2AlgebraicLoadModel(Kind kind, BaseDStabBus<?, ?> bus,
            String id, double fraction, Cmldznu2Data data) {
        this.kind = kind;
        this.data = data;
        this.fraction = fraction;
        setId(id);
        setDStabBus(bus);
        setExtendedDeviceId("CMLDZNU2_" + kind + "_" + id + "@" + bus.getId());
        setLoadPercent(100.0 * fraction);
        bus.getDynLoadModelList().add(this);
    }

    @Override
    public boolean initStates() {
        initialVoltage = getDStabBus().getVoltageMag();
        Complex total = getDStabBus().getInitLoad();
        if (total == null || initialVoltage <= EPS) return false;
        p0 = total.getReal() * fraction;
        double pf = kind == Kind.STATIC ? data.value(26) : data.value(23);
        q0 = reactiveFromPowerFactor(p0, pf);
        Complex initial = new Complex(p0, q0);
        setMvaBase(getDStabBus().getNetwork().getBaseMva());
        setInitLoadPQ(initial);
        setLoadPQ(initial);
        setEquivY(initial.conjugate().divide(initialVoltage * initialVoltage));
        return true;
    }

    Complex effectivePower() {
        double voltage = getDStabBus().getVoltageMag();
        double frequencyDeviation = getDStabBus().getFreq() - 1.0;
        double scale = 1.0 + accumulatedLoadChangeFactor;
        if (kind == Kind.STATIC) {
            double ratio = initialVoltage <= EPS ? 1.0 : voltage / initialVoltage;
            return new Complex(scale * p0 * data.staticActiveFactor(ratio, frequencyDeviation),
                    scale * q0 * data.staticReactiveFactor(ratio, frequencyDeviation));
        }
        updateElectronicFraction(voltage);
        return new Complex(scale * p0 * electronicFraction, scale * q0 * electronicFraction);
    }

    private void updateElectronicFraction(double voltage) {
        double available = data.electronicVoltageFraction(voltage);
        if (available < minimumElectronicFraction) minimumElectronicFraction = available;
        electronicFraction = available < 1.0 ? Math.min(available, electronicFraction)
                : minimumElectronicFraction
                        + data.value(132) * (1.0 - minimumElectronicFraction);
    }

    @Override
    public Complex getNortonCurInj() {
        Complex voltage = getDStabBus().getVoltage();
        if (voltage.abs() <= EPS) return Complex.ZERO;
        Complex power = effectivePower();
        setLoadPQ(power);
        Complex injection = getEquivY().multiply(voltage)
                .subtract(power.divide(voltage).conjugate());
        setNortonCurInj(injection);
        return injection;
    }

    @Override public Complex getPosSeqEquivY() { return getEquivY(); }
    @Override public Object getOutputObject() { return getNortonCurInj(); }
    @Override public boolean updateAttributes(boolean netChange) { getNortonCurInj(); return true; }
    @Override public boolean nextStep(double dt, DynamicSimuMethod method, int flag) { return true; }
    @Override public boolean afterStep(double dt) { return true; }

    @Override
    public boolean changeLoad(double factor) {
        if (factor < -1.0) return false;
        accumulatedLoadChangeFactor = Math.max(-1.0, accumulatedLoadChangeFactor + factor);
        return true;
    }

    @Override
    public Map<String, Double> getNamedStates() {
        Complex power = getLoadPQ() == null ? Complex.ZERO : getLoadPQ();
        Map<String, Double> result = new LinkedHashMap<>();
        result.put(kind + ".P", power.getReal());
        result.put(kind + ".Q", power.getImaginary());
        if (kind == Kind.ELECTRONIC) result.put("ELECTRONIC.Fraction", electronicFraction);
        return Map.copyOf(result);
    }

    @Override
    public Hashtable<String, Object> getStates(Object ref) {
        states.putAll(getNamedStates());
        return states;
    }

    double getElectronicFraction() { return electronicFraction; }

    private static double reactiveFromPowerFactor(double activePower, double powerFactor) {
        if (Math.abs(activePower) <= EPS || Math.abs(powerFactor) <= EPS) return 0.0;
        double magnitude = Math.abs(activePower) * Math.tan(Math.acos(Math.abs(powerFactor)));
        return Math.copySign(magnitude, powerFactor);
    }
}
