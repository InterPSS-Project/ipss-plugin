package org.interpss.dstab.dynLoad.impl;

import java.util.Arrays;
import java.util.Set;
import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.dynLoad.InductionMotor;
import org.interpss.dstab.dynLoad.Cmldznu2Data;
import com.interpss.dstab.BaseDStabBus;

/** Bus/load-specific 132-constant composite load using the shared distribution/motor engine. */
public final class Cmldblu1Model extends Cmldznu2Model {
    public static final int CONSTANT_COUNT = 132;
    public Cmldblu1Model(String id, BaseDStabBus<?,?> bus, Set<String> ids,
            Complex power, double[] constants) {
        super(id,bus,ids,power,legacyData(constants));
        setName("CMLDBLU1");setGroupId("bus:"+bus.getId());
        if (get1PhaseACMotor() instanceof LD1PACImpl motor)
            motor.setCharacteristicPolicy(LD1PACImpl.CharacteristicPolicy.LEGACY_COMPOSITE);
    }
    private static Cmldznu2Data legacyData(double[] constants) {
        if(constants==null || constants.length!=CONSTANT_COUNT)
            throw new IllegalArgumentException("CMLDBLU1 requires exactly 132 constants");
        double[] shared=Arrays.copyOf(constants,133);
        shared[132]=1.0; // Legacy realization restores its full electronic fraction after low voltage.
        return new Cmldznu2Data(shared,true);
    }
    public double[] getLegacyConstants() { return Arrays.copyOf(getData().values(),132); }

    @Override protected boolean distributionIncludesReactiveCompensation() { return true; }

    @Override protected double staticReactiveDemandRatio() {
        return Math.tan(Math.acos(Math.abs(getData().value(26))));
    }

    @Override protected Cmldznu2AlgebraicLoadModel createStaticComponent(double assignedActivePower) {
        if (getFmA() == 0 && getFmB() == 0
                && getFmC() == 0 && getFmD() == 0
                && getData().value(31) == 0 && getData().value(36) == 0) {
            if (getLoadBus() == getDStabBus()) return new LegacyStaticLoadPort(getLoadBus(), getId() + "_S", getData(),
                    assignedActivePower, getCollapsedParentOwnedShunt());
            if (distXfr != null && distFdr != null)
                return new LegacyDistributedStaticLoadPort(this, assignedActivePower);
        }
        return new LegacyStaticLoadModel(getLoadBus(), getId() + "_S", getStaticFraction(),
                getData(), assignedActivePower);
    }

    @Override protected void prepareAlgebraicNetworkInterface(Cmldznu2AlgebraicLoadModel staticComponent,
            Cmldznu2AlgebraicLoadModel electronicComponent) {
        if (staticComponent instanceof LegacyDistributedStaticLoadPort) {
            var low = getLowVoltBus();
            low.setLoadCode(com.interpss.core.aclf.AclfLoadCode.CONST_P);
            low.setInitLoad(Complex.ZERO);
            var shunt = new LegacyDistributionShuntPort(low, getId() + "_lowShunt", getData(), low.getShuntY());
            if (!shunt.initStates()) throw new IllegalStateException("Cannot initialize owned distribution shunt");
        }
        // The static port cancels the owned shunt numerically. Its first-entry
        // preparation also requires the electronic leaf to use a pure current interface.
        if (staticComponent instanceof LegacyStaticLoadPort && electronicComponent != null)
            electronicComponent.useCurrentSourceInterface();
    }

    Complex distributionBoundaryCurrent() {
        return getDStabBus().getVoltage().subtract(getLowVoltBus().getVoltage()
                .divide(distXfr.getToTurnRatio())).divide(distXfr.getZ());
    }

    double distributionPreparationVoltage(Complex incoming) {
        double tap = distXfr.getToTurnRatio();
        Complex low = getDStabBus().getVoltage().subtract(distXfr.getZ().multiply(incoming)).multiply(tap);
        Complex feeder = incoming.divide(tap).subtract(low.multiply(getLowVoltBus().getShuntY()));
        return low.subtract(distFdr.getZ().multiply(feeder)).abs();
    }

    @Override protected boolean initializeInductionMotor(InductionMotor motor) {
        double p = getLoadBus().getInitLoad().getReal() * motor.getLoadPercent() / 100;
        motor.setMvaBase(0);
        motor.setInitLoadPQ(new Complex(p, p * motorReactiveRatio(motor.getLoadFactor())));
        if (motor instanceof InductionMotorImpl implementation) implementation.setInitialReactiveDemandMatching(true);
        return motor.initStates();
    }

}
