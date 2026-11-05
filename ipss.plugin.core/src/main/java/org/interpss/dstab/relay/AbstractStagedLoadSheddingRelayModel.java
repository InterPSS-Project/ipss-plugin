package org.interpss.dstab.relay;

import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.dynLoad.impl.Cmldblu1Model;

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
    private final boolean wildcardTarget;
    private final StagedLoadSheddingRelayData data;
    private final double[] timers = new double[5];
    private final boolean[] pickedUp = new boolean[5];
    private final boolean[] operated = new boolean[5];
    private final Hashtable<String, Object> states = new Hashtable<>();
    private Complex initialStaticLoad = Complex.ZERO;
    private AclfLoad preparedStaticTarget;
    private boolean collapsedWildcard;
    private final java.util.IdentityHashMap<AclfLoad, StaticDefinition> collapsedStaticTargets = new java.util.IdentityHashMap<>();
    private StaticDefinition preparedStaticDefinition;
    private BaseDStabNetwork<?, ?> preparedNetwork;
    private final java.util.IdentityHashMap<DynLoadModel, Boolean> preparedDynamicTargets = new java.util.IdentityHashMap<>();
    private record StaticDefinition(com.interpss.core.aclf.AclfLoadCode code,Complex cp,Complex ci,Complex cz,
            boolean distributed,Complex distributedPower) {
        StaticDefinition(AclfLoad load) {
            this(load.getCode(),load.getLoadCP(),load.getLoadCI(),load.getLoadCZ(),load.isDistGenStatus(),load.getDistGenPower());
        }
        boolean matches(AclfLoad load) {
            return load.isActive() && code==load.getCode() && java.util.Objects.equals(cp,load.getLoadCP())
                    && java.util.Objects.equals(ci,load.getLoadCI()) && java.util.Objects.equals(cz,load.getLoadCZ())
                    && distributed==load.isDistGenStatus() && java.util.Objects.equals(distributedPower,load.getDistGenPower());
        }
    }
    private Complex initialShunt = Complex.ZERO;
    private double elapsedTime;
    private double shedFraction;
    private Object appliedYMatrix;
    private Cmldblu1Model compositeTarget;
    private boolean targetInitialized;
    private static final String LEDGER_KEY = AbstractStagedLoadSheddingRelayModel.class.getName()+".admittanceLedger";

    protected AbstractStagedLoadSheddingRelayModel(String modelName,
            BaseDStabBus<?, ?> loadBus, String loadId, StagedLoadSheddingRelayData data) {
        this(modelName, loadBus, loadId, data, true);
    }

    protected AbstractStagedLoadSheddingRelayModel(String modelName,
            BaseDStabBus<?, ?> loadBus, String loadId, StagedLoadSheddingRelayData data,
            boolean interpretSymbolAsWildcard) {
        this.modelName = modelName;
        this.loadBus = loadBus;
        this.loadId = loadId;
        this.wildcardTarget = interpretSymbolAsWildcard && (loadId.equals("*") || loadId.equals("#"));
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
        targetInitialized = false;
        if (bus != loadBus || bus.getNetwork() == null) return false;
        preparedNetwork=(BaseDStabNetwork<?, ?>)bus.getNetwork();
        preparedDynamicTargets.clear();
        boolean wildcard = wildcardTarget;
        compositeTarget = null;
        collapsedWildcard = false;
        collapsedStaticTargets.clear();
        if (bus.getInfoOnlyDynModel() instanceof Cmldblu1Model composite
                && (wildcard || composite.getTargetLoadIds().contains(loadId))) {
            double total = Math.min(1., data.stages().stream().mapToDouble(LoadSheddingStage::fraction).sum());
            // The dynamic matrix is built after device initialization. Resolve
            // source ownership here; validate the live matrix at the action boundary.
            // A collapsed wildcard also requires a separately resolved static
            // residual; do not count its owned leaves as parent static demand.
            if (!wildcard && composite.getTargetLoadIds().size()!=1
                    || !composite.canPrepareLoadChange(loadId,-total)) return false;
            compositeTarget = composite;
            collapsedWildcard = wildcard && composite.getLoadBus()==bus;
            // Unrelated dynamic loads have not yet initialized at this lifecycle point.
            // Their static remainder cannot be attributed safely from gross bus demand.
            if (collapsedWildcard && bus.getDynLoadModelList().stream()
                    .anyMatch(load -> load != composite && !composite.ownsCapacityLoad(load))) return false;
        }
        boolean hasDynamicTarget = loadBus.getDynLoadModelList() != null
                && loadBus.getDynLoadModelList().stream()
                    .anyMatch(load -> wildcard || loadId.equals(load.getId()));
        hasDynamicTarget |= compositeTarget != null;
        if(loadBus.getDynLoadModelList()!=null)for(DynLoadModel load:loadBus.getDynLoadModelList())
            if(selectsExternalDynamicTarget(load))preparedDynamicTargets.put(load,Boolean.TRUE);
        // Rebuild target ownership for each preparation. A previous static
        // target must not leave a second shedding stamp after dynamic attachment.
        initialStaticLoad = Complex.ZERO;
        preparedStaticTarget = null;
        preparedStaticDefinition = null;
        if (wildcard) {
            initialStaticLoad = collapsedWildcard ? compositeTarget.getCollapsedParentAllocation().staticPower()
                    : loadBus.calNetLoadResults();
            if (collapsedWildcard) for (AclfLoad load : loadBus.getContributeLoadList())
                if (load.isActive()) collapsedStaticTargets.put(load, new StaticDefinition(load));
        } else if (!hasDynamicTarget) {
            AclfLoad load = loadBus.getContributeLoad(loadId);
            if (load == null || !load.isActive()) return false;
            preparedStaticTarget = load;
            preparedStaticDefinition = new StaticDefinition(load);
            initialStaticLoad = load.getLoad(loadBus.getVoltageMag());
        }
        initialShunt = data.shedShunt() && loadBus.getShuntY() != null
                ? (collapsedWildcard ? compositeTarget.getCollapsedParentAllocation().shunt() : loadBus.getShuntY())
                : Complex.ZERO;
        return targetInitialized = reset() && Double.isFinite(monitoredValue());
    }

    @Override
    public boolean nextStep(double dt, DynamicSimuMethod method, int flag) {
        return Double.isFinite(dt) && dt >= 0.0;
    }

    @Override
    public boolean afterStep(double dt) {
        if (!targetOwnershipIntact() || !Double.isFinite(dt) || dt < 0.0) return false;
        for(int i=0;i<data.stages().size();i++)
            if(data.stages().get(i).enabled() && !operated[i] && !canApplyStage(i))return false;
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
                if (!canApplyStage(i)) return false;
                applyStage(i);
            }
        }
        return true;
    }

    protected final boolean canApplyStage(int stageIndex) {
        if (!targetOwnershipIntact()) return false;
        if (operated[stageIndex]) return true;
        double fraction = Math.min(data.stages().get(stageIndex).fraction(), 1.0 - shedFraction);
        return compositeTarget == null || loadBus.getInfoOnlyDynModel() == compositeTarget
                && (wildcardTarget || compositeTarget.getTargetLoadIds().size()==1
                    && compositeTarget.getTargetLoadIds().contains(loadId))
                && compositeTarget.canChangeLoad(loadId, -fraction);
    }

    private boolean selectsExternalDynamicTarget(DynLoadModel load) {
        return load!=compositeTarget && (compositeTarget==null || !compositeTarget.ownsCapacityLoad(load))
                && (wildcardTarget || loadId.equals(load.getId()));
    }

    private boolean targetOwnershipIntact() {
        if(!targetInitialized || getDStabBus()!=loadBus || loadBus.getNetwork()!=preparedNetwork
                || preparedNetwork.getBus(loadBus.getId())!=loadBus)return false;
        if(preparedStaticTarget!=null && (loadBus.getContributeLoad(loadId)!=preparedStaticTarget
                || preparedStaticTarget.getParentBus()!=loadBus || !preparedStaticDefinition.matches(preparedStaticTarget)))return false;
        if(compositeTarget!=null && loadBus.getInfoOnlyDynModel()!=compositeTarget)return false;
        if (collapsedWildcard) {
            int active=0;
            for (AclfLoad load : loadBus.getContributeLoadList()) if (load.isActive()) {
                StaticDefinition definition=collapsedStaticTargets.get(load);
                if (definition==null || !definition.matches(load)) return false;
                active++;
            }
            if (active!=collapsedStaticTargets.size()) return false;
        }
        int matches=0;
        if(loadBus.getDynLoadModelList()!=null)for(DynLoadModel load:loadBus.getDynLoadModelList())
            if(selectsExternalDynamicTarget(load)) {
                if(load.getDStabBus()!=loadBus || !preparedDynamicTargets.containsKey(load))return false;
                matches++;
            }
        return matches==preparedDynamicTargets.size();
    }

    protected final void applyStage(int stageIndex) {
        if (operated[stageIndex]) return;
        if (!canApplyStage(stageIndex)) throw new IllegalStateException("Load target cannot accept shedding action");
        double fraction = Math.min(data.stages().get(stageIndex).fraction(), 1.0 - shedFraction);
        if (fraction <= 0.0) { operated[stageIndex] = true; return; }
        if (compositeTarget != null && !compositeTarget.changeLoad(-fraction))
            throw new IllegalStateException("Composite load rejected a prepared shedding action");
        boolean wildcard = wildcardTarget;
        if (loadBus.getDynLoadModelList() != null) {
            for (DynLoadModel load : loadBus.getDynLoadModelList()) {
                if (load != compositeTarget && (compositeTarget==null || !compositeTarget.ownsCapacityLoad(load))
                        && load.isActive() && (wildcard || loadId.equals(load.getId()))) {
                    load.changeLoad(-fraction);
                }
            }
        }
        operated[stageIndex] = true;
        shedFraction += fraction;
        addAdmittanceDelta(fraction);
    }

    private void addAdmittanceDelta(double fraction) {
        @SuppressWarnings("unchecked")
        BaseDStabNetwork<?, ?> owner=(BaseDStabNetwork<?, ?>)loadBus.getNetwork();
        @SuppressWarnings("unchecked")
        java.util.List<AbstractStagedLoadSheddingRelayModel> ledger=(java.util.List<AbstractStagedLoadSheddingRelayModel>)owner.getExtraInfo()
                .computeIfAbsent(LEDGER_KEY,k->new java.util.ArrayList<AbstractStagedLoadSheddingRelayModel>());
        if(!ledger.contains(this))ledger.add(this);
        double v0 = loadBus.getInitVoltMag();
        if (v0 <= 0.0) v0 = loadBus.getVoltageMag();
        Complex delta = initialStaticLoad.multiply(-fraction).conjugate().divide(v0 * v0);
        if (data.shedShunt()) delta = delta.subtract(initialShunt.multiply(fraction));
        appliedYMatrix=owner.getYMatrix();
        if (delta.abs() > 0.0) {
            @SuppressWarnings("unchecked")
            BaseDStabNetwork<?, ?> network = (BaseDStabNetwork<?, ?>) loadBus.getNetwork();
            network.getYMatrix().addToA(delta, loadBus.getSortNumber(), loadBus.getSortNumber());
            network.setYMatrixDirty(true);
        }
    }

    /** Reapply persistent load-relay admittance changes after a topology rebuild. */
    public static void reapplyAfterNetworkRebuild(BaseDStabNetwork<?, ?> network) {
        Object stored=network.getExtraInfo().get(LEDGER_KEY);
        if(!(stored instanceof java.util.List<?> ledger))return;
        for(Object entry:ledger) {
            var relay=(AbstractStagedLoadSheddingRelayModel)entry;
            if(relay.shedFraction>0 && relay.appliedYMatrix!=network.getYMatrix())
                relay.addAdmittanceDelta(relay.shedFraction);
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
        appliedYMatrix=null;
        resetTransferTrips();
        return true;
    }

    protected final double elapsedTime() { return elapsedTime; }
    protected final BaseDStabBus<?, ?> loadBus() { return loadBus; }
    @Override public Object getOutputObject() {
        if (shedFraction == 0.0 || initialStaticLoad.abs() == 0.0) return Complex.ZERO;
        var voltage=loadBus.getVoltage();
        double magnitude=voltage.abs(),initial=loadBus.getInitVoltMag();
        if(magnitude<=1e-12 || initial<=0)return Complex.ZERO;
        var network=(BaseDStabNetwork<?,?>)loadBus.getNetwork();
        var active=loadBus.getActiveStaticLoadModel()==null?network.getStaticLoadModel():loadBus.getActiveStaticLoadModel();
        var reactive=loadBus.getReactiveStaticLoadModeel()==null?network.getReactiveStaticLoadModel():loadBus.getReactiveStaticLoadModeel();
        if(network.isStaticLoadIncludedInYMatrix())active=reactive=com.interpss.dstab.StaticLoadModel.CONST_Z;
        var removedPower=new Complex(initialStaticLoad.getReal()*loadFactor(active,magnitude,initial,network),
                initialStaticLoad.getImaginary()*loadFactor(reactive,magnitude,initial,network));
        // The matrix ledger removes an equivalent Z stamp. Restore its difference
        // from the actual voltage-dependent removed current on every network iteration.
        var equivalentCurrent=initialStaticLoad.conjugate().multiply(voltage).divide(initial*initial);
        return removedPower.divide(voltage).conjugate().subtract(equivalentCurrent).multiply(shedFraction);
    }
    private static double loadFactor(com.interpss.dstab.StaticLoadModel model,double v,double initial,
            BaseDStabNetwork<?,?> network) {
        if(model==com.interpss.dstab.StaticLoadModel.CONST_I)
            return v/initial*network.getConstantCurrentLoadVoltageCurve().currentMultiplier(v,network.getStaticLoadSwitchVolt());
        if(model==com.interpss.dstab.StaticLoadModel.CONST_P) {
            double threshold=network.getStaticLoadSwitchVolt();
            return threshold>0&&v<threshold?.5*(1-Math.cos(Math.PI*v/threshold)):1;
        }
        return v*v/(initial*initial);
    }
    @Override public boolean updateAttributes(boolean netChange) {
        reapplyAfterNetworkRebuild((BaseDStabNetwork<?,?>)loadBus.getNetwork());return true;
    }

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
