package org.interpss.dstab.svc;

import org.apache.commons.math3.complex.Complex;

import com.interpss.core.aclf.ShuntCompensator;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;

/** Physical switch operation shared by the SVC shunt controls. */
final class FixedMssBank {
    private FixedMssBank() {}

    static void apply(BaseDStabNetwork<?, ?> network, BaseDStabBus<?, ?> bus,
            ShuntCompensator device, Complex nominal, boolean active) {
        if (network.getYMatrix() == null) {
            throw new IllegalStateException("Initialize dynamic Y before MSS dispatch");
        }
        if (device.getB() != nominal.getImaginary()) {
            throw new IllegalStateException("Fixed MSS susceptance changed after import: " + device.getId());
        }
        if (device.isActive() == active) return;
        Complex delta = nominal.multiply(active ? 1 : -1);
        var cache = bus.captureSequenceEquivalentState();
        var oldShunt = bus.getShuntY();
        boolean oldDirty = network.isYMatrixDirty();
        int index = bus.getSortNumber();
        var oldDiagonal = bus.isActive() ? network.getYMatrix().getA(index, index) : null;
        try {
            device.setStatus(active);
            // B is counted through the individual compensator. Its nominal
            // conductance is held in the bus's fixed shunt term.
            bus.setShuntY(oldShunt.add(new Complex(delta.getReal(), 0)));
            bus.applyPositiveSequenceShuntAdmittanceChange(delta);
            if (bus.isActive()) {
                network.getYMatrix().addToA(delta, index, index);
                network.setYMatrixDirty(true);
            }
        } catch (RuntimeException failure) {
            device.setStatus(!active);
            bus.setShuntY(oldShunt);
            bus.restoreSequenceEquivalentState(cache);
            if (oldDiagonal != null) network.getYMatrix().setA(oldDiagonal, index, index);
            network.setYMatrixDirty(oldDirty);
            throw failure;
        }
    }
}
