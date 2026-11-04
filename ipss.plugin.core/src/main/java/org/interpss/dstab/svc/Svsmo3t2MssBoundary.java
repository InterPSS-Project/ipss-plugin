package org.interpss.dstab.svc;

import java.util.HashSet;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.fadapter.builder.FixedShuntAdmittance;

import com.interpss.core.aclf.ShuntCompensator;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;

/** Prepared physical MSS slots. No aggregate-bus admittance inference is used. */
final class Svsmo3t2MssBoundary {
    private record Binding(BaseDStabBus<?, ?> bus, ShuntCompensator device, Complex nominal) { }
    private final BaseDStabNetwork<?, ?> network;
    private final Binding[] bindings = new Binding[8];
    private final Svsmo3t2MssController controller;
    private final Svsmo3t2MssController.Checkpoint checkpoint = new Svsmo3t2MssController.Checkpoint();
    private record PeerBus(BaseDStabBus<?, ?> bus, Object[] devices) { }
    private PeerBus[] peerBuses = new PeerBus[0];
    private Svsmo3t2Model[] peers = new Svsmo3t2Model[0];

    Svsmo3t2MssBoundary(BaseDStabNetwork<?, ?> network, Svsmo3t2Data data) {
        this.network = java.util.Objects.requireNonNull(network);
        controller = new Svsmo3t2MssController(data);
        if (data.mssSwitchingFlag() == 0) return;
        var seen = new HashSet<Svsmo3t2Data.MssDevice>();
        for (int i = 0; i < 8; i++) {
            var key = data.mssDevices().get(i);
            if (key.busNumber() == 0) continue;
            if (!seen.add(key)) throw new IllegalArgumentException("Duplicate SVSMO3T2 MSS slot: " + key);
            var bus = network.getDStabBus("Bus" + key.busNumber());
            if (bus == null) throw new IllegalArgumentException("SVSMO3T2 MSS bus not found: " + key);
            var matches = bus.getCompensatorList().stream().filter(device -> key.id().equals(device.getId())).toList();
            if (matches.size() != 1) throw new IllegalArgumentException("One named fixed MSS required: " + key);
            var device = matches.getFirst();
            var nominal = FixedShuntAdmittance.find(device).orElseThrow(() ->
                    new IllegalArgumentException("Individual nominal MSS G+jB required: " + key));
            if (nominal.getImaginary() == 0) throw new IllegalArgumentException("MSS requires nonzero reactive admittance: " + key);
            bindings[i] = new Binding(bus, device, nominal);
        }
    }

    void initialize() {
        var b = new double[8];
        var active = new boolean[8];
        for (int i = 0; i < 8; i++) if (bindings[i] != null) {
            b[i] = bindings[i].nominal().getImaginary();
            active[i] = bindings[i].device().isActive();
        }
        controller.initialize(b, active);
        if (!controller.isEnabled()) { peerBuses = new PeerBus[0]; peers = new Svsmo3t2Model[0]; return; }
        var preparedBuses = new java.util.ArrayList<PeerBus>();
        var preparedPeers = new java.util.ArrayList<Svsmo3t2Model>();
        for (var bus : network.getBusList()) {
            preparedBuses.add(new PeerBus(bus, bus.getDynamicBusDeviceList().toArray()));
            for (var device : bus.getDynamicBusDeviceList())
                if (device instanceof Svsmo3t2Model model) preparedPeers.add(model);
        }
        peerBuses = preparedBuses.toArray(PeerBus[]::new);
        peers = preparedPeers.toArray(Svsmo3t2Model[]::new);
    }

    void advance(double time, double current) {
        if (!controller.isEnabled()) return;
        if (network.getYMatrix() == null) throw new IllegalStateException("Initialize dynamic Y before MSS dispatch");
        for (int i = 0; i < 8; i++) if (bindings[i] != null) {
            var binding = bindings[i];
            if (binding.device().isActive() != controller.isInService(i)
                    || binding.device().getB() != binding.nominal().getImaginary())
                throw new IllegalStateException("External MSS status/admittance changed; reinitialize before switching");
        }
        controller.save(checkpoint);
        try {
            int changed = controller.advance(time, current);
            if (changed < 0) return;
            preflightPeers();
            var binding = bindings[changed];
            boolean active = controller.isInService(changed);
            var delta = binding.nominal().multiply(active ? 1 : -1);
            var bus = binding.bus();
            var cache = bus.captureSequenceEquivalentState();
            var oldShunt = bus.getShuntY();
            boolean oldStatus = binding.device().isActive();
            boolean oldDirty = network.isYMatrixDirty();
            int index = bus.getSortNumber();
            var oldDiagonal = bus.isActive() ? network.getYMatrix().getA(index, index) : null;
            try {
                binding.device().setStatus(active);
                bus.setShuntY(oldShunt.add(new Complex(delta.getReal(), 0)));
                bus.applyPositiveSequenceShuntAdmittanceChange(delta);
                if (bus.isActive()) {
                    network.getYMatrix().addToA(delta, index, index);
                    network.setYMatrixDirty(true);
                }
            } catch (RuntimeException failure) {
                binding.device().setStatus(oldStatus);
                bus.setShuntY(oldShunt);
                bus.restoreSequenceEquivalentState(cache);
                if (oldDiagonal != null) network.getYMatrix().setA(oldDiagonal, index, index);
                network.setYMatrixDirty(oldDirty);
                throw failure;
            }
            // An actual bank stamp is an electrical event, including for a
            // different tripped controller. Continuous state is not integrated.
            for (var peer : peers)
                if (peer.isActive() && peer.getDStabBus().isActive()) peer.acceptPreparedNetworkEvent();
        } catch (RuntimeException failure) {
            controller.restore(checkpoint);
            throw failure;
        }
    }

    private void preflightPeers() {
        if (network.getBusList().size() != peerBuses.length)
            throw new IllegalStateException("Network ownership changed; reinitialize MSS before switching");
        for (int i = 0; i < peerBuses.length; i++) {
            var prepared = peerBuses[i];
            var devices = prepared.bus().getDynamicBusDeviceList();
            if (network.getBusList().get(i) != prepared.bus() || devices.size() != prepared.devices().length)
                throw new IllegalStateException("Device ownership changed; reinitialize MSS before switching");
            for (int j = 0; j < prepared.devices().length; j++) {
                if (devices.get(j) != prepared.devices()[j])
                    throw new IllegalStateException("Device ownership changed; reinitialize MSS before switching");
                if (devices.get(j) instanceof Svsmo3t2Model peer) {
                    if (peer.getDStabBus() != prepared.bus())
                        throw new IllegalStateException("Controller host changed; reinitialize MSS before switching");
                    if (peer.isActive() && prepared.bus().isActive()) peer.checkNetworkEventReady();
                }
            }
        }
    }

    Svsmo3t2MssController controller() { return controller; }
    Map<String, Double> getNamedStates() { return controller.getNamedStates(); }
}
