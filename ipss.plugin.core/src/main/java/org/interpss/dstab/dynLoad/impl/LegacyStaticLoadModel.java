package org.interpss.dstab.dynLoad.impl;

import org.interpss.dstab.dynLoad.Cmldznu2Data;
import com.interpss.dstab.BaseDStabBus;

/** Legacy static power-factor magnitude policy over the shared polynomial kernel. */
class LegacyStaticLoadModel extends Cmldznu2AlgebraicLoadModel {
    LegacyStaticLoadModel(BaseDStabBus<?, ?> bus, String id, double fraction,
            Cmldznu2Data data, double assignedPower) {
        super(Kind.STATIC, bus, id, fraction, data, assignedPower);
    }

    @Override protected double initialPowerFactor() { return Math.abs(super.initialPowerFactor()); }
}
