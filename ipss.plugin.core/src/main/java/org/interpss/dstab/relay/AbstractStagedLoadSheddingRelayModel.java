package org.interpss.dstab.relay;

import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;

import com.interpss.core.aclf.AclfLoad;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.dstab.controller.cml.ICMLStateProvider;
import com.interpss.dstab.device.DynamicBusDeviceType;
import com.interpss.dstab.device.impl.DynamicBusDeviceImpl;
import com.interpss.dstab.dynLoad.DynLoadModel;
import com.interpss.dstab.relay.IRelayModel;

/** Accepted-step implementation shared by native PSS/E LDS3BL and LVS3BL. */
public abstract class AbstractStagedLoadSheddingRelayModel extends DynamicBusDeviceImpl
        implements IRelayModel, ICMLStateProvider {
    private static final double TIME_EPS = 1.0e-12;
    private final String modelName;
    private final BaseDStabBus<?, ?> loadBus;
    private final String loadId;
    private final StagedLoadSheddingRelayData data;
    private final double[] timers = new double[5];
    private final boolean[] pickedUp = new boolean[5];
    private final boolean[] operated = new boolean[5];
    private final Hashtable<String, Object> states = new Hashtable<>();
    private Complex initialStaticLoad = Complex.ZERO;
    private Complex initialShunt = Complex.ZERO;
    private double elapsedTime;
    private double shedFraction;

    protected AbstractStagedLoadSheddingRelayModel(String modelName,
            BaseDStabBus<?, ?> loadBus, String loadId, StagedLoadSheddingRelayData data) {
        this.modelName = modelName;
        this.loadBus = loadBus;
        this.loadId = loadId;
        this.data = data;
        String relayId = modelName + "_" + loadBus.getId() + "_" + loadId;
        setId(relayId);
        setName(modelName);
        setExtendedDeviceId(relayId);
        setDeviceType(DynamicBusDeviceType.DYNAMIC_LOAD);
        setDStabBus(loadBus);
        loadBus.getDynamicBusDeviceList().add(this);
        states.put(DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID, relayId);
    }

    protected abstract double monitoredValue();
    protected abstract boolean violates(LoadSheddingStage stage, double value);
    protected void stagePickedUp(int stage) { }
    protected boolean advanceTransferTrips(double dt) { return true; }
    protected void resetTransferTrips() { }
    protected void addTransferNamedStates(Map<String, Double> snapshot) { }

    public final StagedLoadSheddingRelayData getData() { return data; }
    public final String getLoadId() { return loadId; }
    public final double getShedFraction() { return shedFraction; }
    public final double getStageTimer(int stage) { return timers[stage]; }
    public final boolean isStagePickedUp(int stage) { return pickedUp[stage]; }
    public final boolean isStageOperated(int stage) { return operated[stage]; }

    @Override
    public boolean initStates(BaseDStabBus<?, ?> bus) {
        if (bus != loadBus || bus.getNetwork() == null) return false;
        boolean wildcard = loadId.equals("*") || loadId.equals("#");
        boolean hasDynamicTarget = loadBus.getDynLoadModelList() != null
                && loadBus.getDynLoadModelList().stream()
                    .anyMatch(load -> wildcard || loadId.equals(load.getId()));
        if (wildcard) {
            initialStaticLoad = loadBus.calNetLoadResults();
        } else if (!hasDynamicTarget) {
            AclfLoad load = loadBus.getContributeLoad(loadId);
            if (load == null) return false;
            initialStaticLoad = load.getLoad(loadBus.getVoltageMag());
        }
        initialShunt = data.shedShunt() && loadBus.getShuntY() != null
                ? loadBus.getShuntY() : Complex.ZERO;
        return reset() && Double.isFinite(monitoredValue());
    }

    @Override
    public boolean nextStep(double dt, DynamicSimuMethod method, int flag) {
        return Double.isFinite(dt) && dt >= 0.0;
    }

    @Override
    public boolean afterStep(double dt) {
        if (!Double.isFinite(dt) || dt < 0.0) return false;
        elapsedTime += dt;
        if (!advanceTransferTrips(dt)) return false;
        double value = monitoredValue();
        if (!Double.isFinite(value)) return false;
        for (int i = 0; i < data.stages().size(); i++) {
            LoadSheddingStage stage = data.stages().get(i);
            if (!stage.enabled() || operated[i]) continue;
            if (!pickedUp[i]) {
                if (!violates(stage, value)) {
                    timers[i] = 0.0;
                    continue;
                }
                timers[i] += dt;
                if (timers[i] + TIME_EPS >= stage.pickupTime()) {
                    pickedUp[i] = true;
                    stagePickedUp(i);
                }
            } else {
                timers[i] += dt;
            }
            if (pickedUp[i] && timers[i] + TIME_EPS >= stage.pickupTime() + stage.breakerTime()) {
                applyStage(i);
            }
        }
        return true;
    }

    private void applyStage(int stageIndex) {
        if (operated[stageIndex]) return;
        double fraction = Math.min(data.stages().get(stageIndex).fraction(), 1.0 - shedFraction);
        operated[stageIndex] = true;
        if (fraction <= 0.0) return;
        boolean wildcard = loadId.equals("*") || loadId.equals("#");
        if (loadBus.getDynLoadModelList() != null) {
            for (DynLoadModel load : loadBus.getDynLoadModelList()) {
                if (load.isActive() && (wildcard || loadId.equals(load.getId()))) {
                    load.changeLoad(-fraction);
                }
            }
        }
        shedFraction += fraction;
        addAdmittanceDelta(fraction);
    }

    private void addAdmittanceDelta(double fraction) {
        double v0 = loadBus.getInitVoltMag();
        if (v0 <= 0.0) v0 = loadBus.getVoltageMag();
        Complex delta = initialStaticLoad.multiply(-fraction).conjugate().divide(v0 * v0);
        if (data.shedShunt()) delta = delta.subtract(initialShunt.multiply(fraction));
        if (delta.abs() > 0.0) {
            @SuppressWarnings("unchecked")
            BaseDStabNetwork<?, ?> network = (BaseDStabNetwork<?, ?>) loadBus.getNetwork();
            network.getYMatrix().addToA(delta, loadBus.getSortNumber(), loadBus.getSortNumber());
            network.setYMatrixDirty(true);
        }
    }

    /** Reapply persistent load-relay admittance changes after a topology rebuild. */
    public static void reapplyAfterNetworkRebuild(BaseDStabNetwork<?, ?> network) {
        for (Object busObject : network.getBusList()) {
            BaseDStabBus<?, ?> bus = (BaseDStabBus<?, ?>) busObject;
            for (Object device : bus.getDynamicBusDeviceList()) {
                if (device instanceof AbstractStagedLoadSheddingRelayModel relay
                        && relay.shedFraction > 0.0) {
                    relay.addAdmittanceDelta(relay.shedFraction);
                }
            }
        }
    }

    @Override public boolean action(double time) { return true; }
    @Override public boolean isActionTime(double time) { return false; }

    @Override
    public boolean reset() {
        java.util.Arrays.fill(timers, 0.0);
        java.util.Arrays.fill(pickedUp, false);
        java.util.Arrays.fill(operated, false);
        elapsedTime = 0.0;
        shedFraction = 0.0;
        resetTransferTrips();
        return true;
    }

    protected final double elapsedTime() { return elapsedTime; }
    protected final BaseDStabBus<?, ?> loadBus() { return loadBus; }
    @Override public Object getOutputObject() { return Complex.ZERO; }
    @Override public boolean updateAttributes(boolean netChange) { return true; }

    @Override
    public Map<String, Double> getNamedStates() {
        Map<String, Double> snapshot = new LinkedHashMap<>();
        for (int i = 0; i < timers.length; i++) snapshot.put("Stage " + (i + 1) + " timer", timers[i]);
        snapshot.put("Shed fraction", shedFraction);
        addTransferNamedStates(snapshot);
        return Map.copyOf(snapshot);
    }

    @Override
    public Hashtable<String, Object> getStates(Object ref) {
        states.putAll(getNamedStates());
        return states;
    }
}
