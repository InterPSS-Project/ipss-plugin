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
        int changed = controller.advance(time, current);
        if (changed < 0) return;
        var binding = bindings[changed];
        boolean active = controller.isInService(changed);
        var delta = binding.nominal().multiply(active ? 1 : -1);
        binding.device().setStatus(active);
        // B is represented by the named compensator; G is retained separately
        // on the bus. Update cached sequence data and only this physical stamp.
        binding.bus().setShuntY(binding.bus().getShuntY().add(new Complex(delta.getReal(), 0)));
        binding.bus().applyPositiveSequenceShuntAdmittanceChange(delta);
        if (binding.bus().isActive()) {
            int index = binding.bus().getSortNumber();
            network.getYMatrix().addToA(delta, index, index);
            network.setYMatrixDirty(true);
        }
    }

    Svsmo3t2MssController controller() { return controller; }
    Map<String, Double> getNamedStates() { return controller.getNamedStates(); }
}
