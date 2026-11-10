package org.interpss.dstab.hvdc;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Objects;

import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.control.util.StaggeredEulerParticipant;

import com.google.gson.Gson;
import com.interpss.core.aclf.hvdc.HvdcLine2TLCC;
import com.interpss.core.aclf.hvdc.HvdcOperationMode;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.device.DynamicBusDevice;
import com.interpss.dstab.device.DynamicBusDeviceType;
import com.interpss.dstab.device.impl.DynamicBusDeviceImpl;
import com.interpss.state.dstab.DynamicBusDeviceState;

/** Retains imported source data until solved-network initialization can prepare the terminal pair. */
public final class Chvdc2DyrModel extends DynamicBusDeviceImpl implements StaggeredEulerParticipant {
    private record Source(String name, Chvdc2Parameters.Dialect dialect, int[] flags, double[] constants,
            String id, boolean active, String ownerId, String inverterBusId) {}
    private static final Gson JSON = new Gson();
    static {
        DynamicBusDeviceState.registerExtension(Chvdc2DyrModel.class, new DynamicBusDeviceState.Extension(
                device -> {
                    var model = (Chvdc2DyrModel) device;
                    if (model.ports != null) throw new IllegalArgumentException("Capture CHVDC2 before dynamic initialization");
                    return JSON.toJson(new Source(model.parameters.linkName(), model.parameters.dialect(),
                            model.parameters.copySourceFlags(), model.parameters.copySourceConstants(),
                            model.getId(), model.isActive(), model.capturedOwner().getId(), model.capturedOwner().getToBus().getId()));
                }, (bus, json) -> {
                    var source = JSON.fromJson(json, Source.class);
                    // Bus-device restoration precedes special-branch restoration.
                    // Retain exact owner identity and resolve it after the copy is complete.
                    var model = new Chvdc2DyrModel(bus, null,
                            new Chvdc2Parameters(source.name(), source.dialect(), source.flags(), source.constants()));
                    model.copiedOwnerId = source.ownerId(); model.copiedInverterId = source.inverterBusId();
                    model.setId(source.id()); model.setStatus(source.active());
                    return model;
                }));
    }
    private final Chvdc2Parameters parameters;
    private HvdcLine2TLCC<?> owner;
    private BaseDStabBus<?, ?> originalInverter;
    private String copiedOwnerId, copiedInverterId;
    private Chvdc2NetworkPreparation.Binding binding;
    private Chvdc2TerminalPorts ports;

    private Chvdc2DyrModel(BaseDStabBus<?, ?> bus, HvdcLine2TLCC<?> owner, Chvdc2Parameters parameters) {
        this.owner = owner; this.parameters = parameters;
        this.originalInverter = owner == null ? null : (BaseDStabBus<?, ?>) owner.getToBus();
        setName(parameters.dialect().name()); setId("CHVDC2:" + parameters.linkName());
        setExtendedDeviceId(getId()); setDeviceType(DynamicBusDeviceType.DYNAMIC_LOAD); setDStabBus(bus);
    }
    public Chvdc2Parameters getParameters() { return parameters; }
    public Chvdc2NetworkPreparation.Binding getBinding() { return binding; }
    public Chvdc2TerminalPorts getPorts() { return ports; }

    private HvdcLine2TLCC<?> capturedOwner() {
        if (copiedOwnerId == null) return owner;
        var network = (BaseDStabNetwork<?, ?>) getDStabBus().getNetwork();
        var matches = network.getSpecialBranchList().stream()
                .filter(branch -> branch instanceof HvdcLine2TLCC<?> && copiedOwnerId.equals(branch.getId())).toList();
        if (matches.size() != 1) throw new IllegalArgumentException("Copied CHVDC2 owner is missing or ambiguous");
        var candidate = (HvdcLine2TLCC<?>) matches.getFirst();
        if (candidate.getFromBus() != getDStabBus() || !copiedInverterId.equals(candidate.getToBus().getId()))
            throw new IllegalArgumentException("Copied CHVDC2 terminal identity changed");
        if (!parameters.linkName().equals(candidate.getName()) && !("Hvdc line: " + candidate.getId()).equals(candidate.getName()))
            throw new IllegalArgumentException("Copied CHVDC2 owner has a conflicting name");
        return candidate;
    }

    /** Resolve exact ownership before mutation, leaving static injections available for load flow. */
    public static Chvdc2DyrModel attach(BaseDStabNetwork<?, ?> network, Chvdc2Parameters parameters) {
        Objects.requireNonNull(network); Objects.requireNonNull(parameters);
        if (network.isDStabNetInitialized()) throw new IllegalStateException("Import CHVDC2 before dynamic initialization");
        HvdcLine2TLCC<?> owner = null;
        for (var branch : network.getSpecialBranchList())
            if (branch instanceof HvdcLine2TLCC<?> candidate && parameters.linkName().equals(candidate.getName())) {
                if (owner != null) throw new IllegalArgumentException("Ambiguous static DC-link name: " + parameters.linkName());
                owner = candidate;
            }
        if (owner == null) throw new IllegalArgumentException("No static DC-link owner named " + parameters.linkName());
        if (!owner.isActive() || owner.getOperationMode() != HvdcOperationMode.REC1_INV1
                || owner.getRectifier2() != null || owner.getInverter2() != null || owner.isPuBasedPowerFlowAlgo()
                || owner.getRectifier() == null || owner.getInverter() == null
                || !(owner.getFromBus() instanceof BaseDStabBus<?, ?> rectifier)
                || !(owner.getToBus() instanceof BaseDStabBus<?, ?> inverter)
                || rectifier == inverter || !rectifier.isActive() || !inverter.isActive()
                || rectifier.getNetwork() != network || inverter.getNetwork() != network)
            throw new IllegalArgumentException("CHVDC2 requires an active ordinary converter pair in its dynamic network");
        for (var model : models(network)) if (model.owner == owner)
            throw new IllegalArgumentException("Duplicate CHVDC2 owner: " + parameters.linkName());
        for (var model : Cdc6tDyrModel.models(network)) if (model.getParameters().linkName().equals(parameters.linkName()))
            throw new IllegalArgumentException("Static link already has a CDC6T owner");
        for (var bus : List.of(rectifier, inverter)) for (var device : bus.getDynamicBusDeviceList())
            if (device.getId().equals("CHVDC2:" + parameters.linkName())
                    || device.getId().equals(parameters.linkName() + ":rectifier")
                    || device.getId().equals(parameters.linkName() + ":inverter"))
                throw new IllegalArgumentException("Conflicting terminal owner: " + parameters.linkName());
        return new Chvdc2DyrModel(rectifier, owner, parameters);
    }
    public static List<Chvdc2DyrModel> models(BaseDStabNetwork<?, ?> network) {
        var result = new ArrayList<Chvdc2DyrModel>();
        for (var bus : network.getBusList()) for (var device : bus.getDynamicBusDeviceList())
            if (device instanceof Chvdc2DyrModel model) result.add(model);
        return List.copyOf(result);
    }
    /** Prepare all links before replacing ports. The outer timestep controls frozen coefficients. */
    public static void prepareNetwork(BaseDStabNetwork<?, ?> network, double step) {
        var models = models(network);
        if (models.isEmpty()) return;
        var prepared = new ArrayList<Chvdc2TerminalPorts>();
        var oldPorts = new java.util.HashSet<DynamicBusDevice>();
        for (var model : models) {
            if (model.copiedOwnerId != null) {
                model.owner = model.capturedOwner();
                model.originalInverter = (BaseDStabBus<?, ?>) model.owner.getToBus();
                model.owner.setName(model.parameters.linkName());
            }
            if (model.ports != null) { oldPorts.add(model.ports.rectifierPort()); oldPorts.add(model.ports.inverterPort()); }
            if (model.owner.getNetwork() != network || !network.getSpecialBranchList().contains(model.owner)
                    || model.owner.getFromBus() != model.getDStabBus() || model.owner.getToBus() != model.originalInverter
                    || !model.parameters.linkName().equals(model.owner.getName()))
                throw new IllegalArgumentException("Imported CHVDC2 ownership changed");
            prepared.add(model.isActive() ? new Chvdc2TerminalPorts(
                    Chvdc2NetworkPreparation.prepare(network, model.parameters, step, 0.0)) : null);
        }
        for (int i = 0; i < models.size(); i++) {
            var ports = prepared.get(i);
            if (ports == null) continue;
            for (var bus : List.of(models.get(i).owner.getFromBus(), models.get(i).owner.getToBus()))
                for (var device : ((BaseDStabBus<?, ?>) bus).getDynamicBusDeviceList())
                    if (!oldPorts.contains(device) && (device.getId().equals(ports.rectifierPort().getId())
                            || device.getId().equals(ports.inverterPort().getId())))
                        throw new IllegalArgumentException("Conflicting CHVDC2 runtime port");
        }
        for (var port : oldPorts) port.setDStabBus(null);
        for (int i = 0; i < models.size(); i++) {
            var ports = prepared.get(i);
            if (ports != null) ports.attach();
            models.get(i).ports = ports;
            models.get(i).binding = ports == null ? null : ports.bindingForImport();
        }
        network.setDStabNetInitialized(false);
    }
    @Override public boolean initStates(BaseDStabBus<?, ?> bus) {
        if (bus != getDStabBus() || ports == null)
            throw new IllegalStateException("Imported CHVDC2 requires DefaultDynamicSimulationSolver or StaggeredEulerSolver initialization");
        return ports.rectifierPort().attachmentIntact();
    }
    @Override public Object getOutputObject() { return Complex.ZERO; }
    @Override public boolean nextStep(double step, com.interpss.dstab.algo.DynamicSimuMethod method, int stage) { return true; }
    @Override public boolean afterStep(double step) { return true; }
    @Override public boolean updateAttributes(boolean changed) { return true; }
    @Override public Hashtable<String, Object> getStates(Object reference) {
        var states = new Hashtable<String, Object>();
        states.put(com.interpss.dstab.common.DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID, getExtendedDeviceId());
        return states;
    }
    @Override public int staggeredStateCount() { return 0; }
    @Override public void readStaggeredState(double[] values, int offset) { Objects.checkFromIndexSize(offset, 0, values.length); }
    @Override public void evaluateStaggered(double step, double[] values, int offset) { Objects.checkFromIndexSize(offset, 0, values.length); }
    @Override public void applyStaggeredState(double[] values, int offset, double step) { Objects.checkFromIndexSize(offset, 0, values.length); }
}
