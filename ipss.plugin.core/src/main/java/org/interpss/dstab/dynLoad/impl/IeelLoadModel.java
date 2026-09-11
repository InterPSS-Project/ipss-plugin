package org.interpss.dstab.dynLoad.impl;

import java.util.Hashtable;

import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.dynLoad.IeelLoadData;

import com.interpss.core.aclf.AclfLoad;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.dstab.dynLoad.impl.DynLoadModelImpl;

/** PSS/E IEEL algebraic voltage- and frequency-dependent load model. */
public final class IeelLoadModel extends DynLoadModelImpl {
    private static final double EPS = 1.0e-12;

    private final String sourceModelName;
    private final AclfLoad parentLoad;
    private final IeelLoadData data;
    private final Hashtable<String, Object> states = new Hashtable<>();
    private double activePowerAtOnePu;
    private double reactivePowerAtOnePu;
    private double initialVoltage;

    public IeelLoadModel(String sourceModelName, BaseDStabBus<?, ?> bus,
            AclfLoad parentLoad, IeelLoadData data) {
        this.sourceModelName = sourceModelName;
        this.parentLoad = parentLoad;
        this.data = data;
        setId(parentLoad.getId());
        setDStabBus(bus);
        setExtendedDeviceId(sourceModelName + "_" + parentLoad.getId() + "@" + bus.getId());
        bus.getDynLoadModelList().add(this);
    }

    @Override
    public boolean initStates() {
        BaseDStabBus<?, ?> bus = getDStabBus();
        if (bus == null || parentLoad == null || !parentLoad.isActive()) return false;
        initialVoltage = bus.getVoltageMag();
        Complex initialPower = parentLoad.getLoad(initialVoltage);
        if (initialPower == null || initialVoltage <= EPS) return false;

        double activeFactor = data.activeVoltageFactor(initialVoltage);
        double reactiveFactor = data.reactiveVoltageFactor(initialVoltage);
        if (Math.abs(activeFactor) <= EPS && Math.abs(initialPower.getReal()) > EPS) return false;
        if (Math.abs(reactiveFactor) <= EPS && Math.abs(initialPower.getImaginary()) > EPS) return false;
        activePowerAtOnePu = Math.abs(initialPower.getReal()) <= EPS
                ? 0.0 : initialPower.getReal() / activeFactor;
        reactivePowerAtOnePu = Math.abs(initialPower.getImaginary()) <= EPS
                ? 0.0 : initialPower.getImaginary() / reactiveFactor;

        setMvaBase(bus.getNetwork().getBaseMva());
        setInitLoadPQ(initialPower);
        setLoadPQ(initialPower);
        setLoadPercent(100.0);
        setEquivY(initialPower.conjugate().divide(initialVoltage * initialVoltage));
        states.put(DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID, getExtendedDeviceId());
        return finite(activePowerAtOnePu) && finite(reactivePowerAtOnePu);
    }

    public Complex effectivePower() {
        BaseDStabBus<?, ?> bus = getDStabBus();
        double voltage = bus.getVoltageMag();
        double frequencyDeviation = bus.getFreq() - 1.0;
        double scale = 1.0 + accumulatedLoadChangeFactor;
        return new Complex(
                scale * activePowerAtOnePu * data.activeVoltageFactor(voltage)
                        * (1.0 + data.a7() * frequencyDeviation),
                scale * reactivePowerAtOnePu * data.reactiveVoltageFactor(voltage)
                        * (1.0 + data.a8() * frequencyDeviation));
    }

    @Override
    public Complex getNortonCurInj() {
        Complex voltage = getDStabBus().getVoltage();
        if (voltage.abs() <= EPS) return Complex.ZERO;
        Complex power = effectivePower();
        setLoadPQ(power);
        Complex loadCurrent = power.divide(voltage).conjugate();
        Complex injection = getEquivY().multiply(voltage).subtract(loadCurrent);
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
    public Hashtable<String, Object> getStates(Object ref) {
        Complex power = effectivePower();
        states.put("IEEL_P", power.getReal());
        states.put("IEEL_Q", power.getImaginary());
        return states;
    }

    public String getSourceModelName() { return sourceModelName; }
    public AclfLoad getParentLoad() { return parentLoad; }
    public IeelLoadData getData() { return data; }
    public double getInitialVoltage() { return initialVoltage; }
    public double getActivePowerAtOnePu() { return activePowerAtOnePu; }
    public double getReactivePowerAtOnePu() { return reactivePowerAtOnePu; }

    private static boolean finite(double value) { return Double.isFinite(value); }
}
