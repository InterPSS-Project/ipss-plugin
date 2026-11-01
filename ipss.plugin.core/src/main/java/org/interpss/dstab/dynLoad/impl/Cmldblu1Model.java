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

    @Override protected void initializeDistributionEquivalent(Complex voltage, Complex incomingCurrent, double mvaFactor) {
        var data = getData();
        double fixedRatio = data.value(7) / data.value(6);
        if (!Double.isFinite(fixedRatio) || fixedRatio <= 0)
            throw new IllegalArgumentException("Composite fixed transformer taps must be positive");
        Complex highSide = voltage.subtract(distXfr.getZ().multiply(incomingCurrent));
        double tap = fixedRatio, step = data.value(11), target = .5 * (data.value(12) + data.value(13));
        if (step > 0) {
            Complex compensationDrop = new Complex(data.value(16), data.value(17)).multiply(mvaFactor).multiply(incomingCurrent);
            double a = highSide.abs() * highSide.abs();
            double b = target * target + 2 * highSide.multiply(compensationDrop.conjugate()).getReal();
            double c = compensationDrop.abs() * compensationDrop.abs();
            double discriminant = b * b - 4 * a * c;
            if (discriminant < 0 || !(a > 0)) throw new IllegalArgumentException("No valid compensated initial transformer ratio");
            double continuous = Math.sqrt((b + Math.sqrt(discriminant)) / (2 * a));
            long moves = Math.round((continuous - fixedRatio) / step);
            if (moves > 0) moves = Math.min(moves, Math.max(0, Math.round((data.value(10) - fixedRatio) / step)));
            else if (moves < 0) moves = Math.max(moves, Math.min(0, Math.round((data.value(9) - fixedRatio) / step)));
            tap = fixedRatio + moves * step;
        }
        distXfr.setFromTurnRatio(1); distXfr.setToTurnRatio(tap);
        Complex lowVoltage = highSide.multiply(tap);
        getLowVoltBus().setVoltage(lowVoltage);
        double initialReactiveDemandRatio = (getFmA() > 1e-10 ? getFmA() * motorReactiveRatio(data.value(38)) : 0)
                + (getFmB() > 1e-10 ? getFmB() * motorReactiveRatio(data.value(58)) : 0)
                + (getFmC() > 1e-10 ? getFmC() * motorReactiveRatio(data.value(78)) : 0)
                + getFmD() * reactiveRatio(data.value(102)) + getFel() * reactiveRatio(data.value(23))
                + getStaticFraction() * reactiveRatio(data.value(26));
        double fromCompensation = Math.max(0, Math.min(1, data.value(4)))
                * (totalLoad.getReal() * initialReactiveDemandRatio - totalLoad.getImaginary())
                / (lowVoltage.abs() * lowVoltage.abs());
        double lowSusceptance = data.value(1) / mvaFactor + fromCompensation;
        getLowVoltBus().setShuntY(new Complex(0, lowSusceptance));
        Complex feederCurrent = incomingCurrent.divide(tap).subtract(lowVoltage.multiply(getLowVoltBus().getShuntY()));
        Complex loadVoltage = lowVoltage.subtract(distFdr.getZ().multiply(feederCurrent));
        if (loadVoltage.abs() < .95) {
            if (lowVoltage.abs() < .95) throw new IllegalArgumentException("Composite low-side voltage cannot support its minimum load voltage");
            Complex drop = distFdr.getZ().multiply(feederCurrent);
            double a = drop.abs() * drop.abs(), projection = lowVoltage.multiply(drop.conjugate()).getReal();
            double c = lowVoltage.abs() * lowVoltage.abs() - .95 * .95;
            double scale = (projection - Math.sqrt(Math.max(0, projection * projection - a * c))) / a;
            distFdr.setZ(distFdr.getZ().multiply(Math.max(0, Math.min(1, scale))));
            loadVoltage = lowVoltage.subtract(distFdr.getZ().multiply(feederCurrent));
        }
        Complex loadPower = loadVoltage.multiply(feederCurrent.conjugate());
        double grossReactiveDemand = loadPower.getReal() * initialReactiveDemandRatio;
        getLoadBus().setVoltage(loadVoltage); getLoadBus().setLoadP(loadPower.getReal()); getLoadBus().setLoadQ(grossReactiveDemand);
        // The engine subtracts owned component demand from this gross load.
        // The explicitly stamped far-end capacitor already supplies the Q difference.
        getLoadBus().setInitLoad(new Complex(loadPower.getReal(), grossReactiveDemand));
        double farCompensation = (loadPower.getReal() * initialReactiveDemandRatio - loadPower.getImaginary())
                / (loadVoltage.abs() * loadVoltage.abs());
        getLoadBus().setShuntY(new Complex(0, farCompensation));
    }

    private static double reactiveRatio(double powerFactor) { return Math.tan(Math.acos(powerFactor)); }
    private static double motorReactiveRatio(double loading) {
        if (!(loading > 0 && loading <= 1)) throw new IllegalArgumentException("Composite motor loading factor must be in (0,1]");
        return Math.sqrt(1 / (loading * loading) - 1);
    }

    @Override protected boolean initializeInductionMotor(InductionMotor motor) {
        double p = getLoadBus().getInitLoad().getReal() * motor.getLoadPercent() / 100;
        motor.setMvaBase(0);
        motor.setInitLoadPQ(new Complex(p, p * motorReactiveRatio(motor.getLoadFactor())));
        if (motor instanceof InductionMotorImpl implementation) implementation.setInitialReactiveDemandMatching(true);
        return motor.initStates();
    }

}
