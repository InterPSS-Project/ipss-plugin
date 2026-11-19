package org.interpss.dstab.dynLoad.impl;

import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.dynLoad.Cmldznu2Data;
import com.interpss.dstab.BaseDStabBus;

/** Cancels one owned numerical shunt stamp while retaining its physical current. */
final class LegacyDistributionShuntPort extends Cmldznu2AlgebraicLoadModel {
    private final Complex ownedShunt;

    LegacyDistributionShuntPort(BaseDStabBus<?, ?> bus, String id,
            Cmldznu2Data data, Complex ownedShunt) {
        super(Kind.STATIC, bus, id, 0, data, 0);
        this.ownedShunt = java.util.Objects.requireNonNull(ownedShunt);
        if (ownedShunt.isNaN() || ownedShunt.isInfinite())
            throw new IllegalArgumentException("A finite owned distribution shunt is required");
    }

    @Override public boolean initStates() {
        if (!super.initStates()) return false;
        setEquivY(ownedShunt.negate());
        return true;
    }

    @Override public Complex getNortonCurInj() {
        Complex current = ownedShunt.multiply(getDStabBus().getVoltage()).negate();
        setNortonCurInj(current);
        return current;
    }
}
