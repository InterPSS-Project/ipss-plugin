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
    private Svsmo3t2PeerRegistry peerRegistry;

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
        peerRegistry = controller.isEnabled() ? Svsmo3t2PeerRegistry.acquire(network) : null;
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
            peerRegistry.preflight();
            var binding = bindings[changed];
            boolean active = controller.isInService(changed);
            FixedMssBank.apply(network, binding.bus(), binding.device(), binding.nominal(), active);
            // An actual bank stamp is an electrical event, including for a
            // different tripped controller. Continuous state is not integrated.
            peerRegistry.acceptPreparedEvent();
        } catch (RuntimeException failure) {
            controller.restore(checkpoint);
            throw failure;
        }
    }

    Svsmo3t2MssController controller() { return controller; }
    Map<String, Double> getNamedStates() { return controller.getNamedStates(); }
}
