package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;
import org.apache.commons.math3.complex.Complex;
import org.junit.jupiter.api.Test;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.acsc.SequenceCode;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.StaticLoadModel;
import com.interpss.dstab.impl.BaseDStabBusImpl;

/** Physical power and matrix/current consistency for mixed static loads. */
public class StaticLoadMatrixSplitTest {
    private record Fixture(BaseDStabNetwork<?, ?> net, BaseDStabBus<?, ?> bus) {}

    private Fixture fixture(boolean split) {
        var net = DStabObjectFactory.createDStabilityNetwork();
        net.setStaticLoadIncludedInYMatrix(false);
        net.setStaticLoadZPartInYMatrix(split);
        var bus = DStabObjectFactory.createDStabBus("load", net).orElseThrow();
        bus.setLoadCode(AclfLoadCode.CONST_P);
        bus.setInitVoltMag(1.1);
        bus.setInitConstIStaticLoad(new Complex(.8, 0.));
        bus.setInitConstZStaticLoad(new Complex(0., .3));
        return new Fixture(net, bus);
    }

    private Complex stamp(BaseDStabBus<?, ?> bus) throws Exception {
        var method = BaseDStabBusImpl.class.getDeclaredMethod("calEquivYScBus", SequenceCode.class);
        method.setAccessible(true);
        return (Complex) method.invoke(bus, SequenceCode.POSITIVE);
    }

    private Complex consumed(BaseDStabBus<?, ?> bus, Complex y, double v) throws Exception {
        bus.setVoltage(new Complex(v * Math.cos(.37), v * Math.sin(.37)));
        var injection = bus.injCurDynamic(StaticLoadModel.CONST_I, .65, .05);
        return bus.getVoltage().multiply(y.multiply(bus.getVoltage()).subtract(injection).conjugate());
    }

    private void assertPower(Complex expected, Complex actual) {
        assertEquals(expected.getReal(), actual.getReal(), 2.e-14);
        assertEquals(expected.getImaginary(), actual.getImaginary(), 2.e-14);
    }

    @Test void optionDefaultsOffAndCannotChangeAnInitializedNetwork() {
        var net = DStabObjectFactory.createDStabilityNetwork();
        assertFalse(net.isStaticLoadZPartInYMatrix());
        net.setStaticLoadZPartInYMatrix(true);
        net.setDStabNetInitialized(true);
        assertDoesNotThrow(() -> net.setStaticLoadZPartInYMatrix(true));
        assertThrows(IllegalStateException.class, () -> net.setStaticLoadZPartInYMatrix(false));
    }

    @Test void stampsOnlyImpedanceAndPreservesPhysicalPower() throws Exception {
        var f = fixture(true);
        var y = stamp(f.bus);
        assertPower(new Complex(0., -.3 / 1.21), y);
        for (double v : new double[] {.8, 1., 1.1, 1.3})
            assertPower(new Complex(.8 * v / 1.1, .3 * v * v / 1.21), consumed(f.bus, y, v));
    }

    @Test void loadChangesRemainExactBeforeAndAfterMatrixRebuild() throws Exception {
        var f = fixture(true);
        var y = stamp(f.bus);
        for (double change : new double[] {-.4, .25, -1.}) {
            f.bus.setAccumulatedLoadChangeFactor(change);
            assertPower(new Complex(.8 / 1.1, .3 / 1.21).multiply(1. + change), consumed(f.bus, y, 1.));
            y = stamp(f.bus);
            assertPower(new Complex(.8 / 1.1, .3 / 1.21).multiply(1. + change), consumed(f.bus, y, 1.));
        }
    }

    @Test void preservesLowVoltageCurrentCutoffAndNonlinearLoadCurve() throws Exception {
        var split = fixture(true);
        var original = fixture(false);
        var y = stamp(split.bus);
        assertPower(Complex.ZERO, stamp(original.bus));
        for (double v : new double[] {0., 1.e-6, 9.99e-5, 1.001e-4, .1, .4, .65})
            assertPower(consumed(original.bus, Complex.ZERO, v), consumed(split.bus, y, v));
    }

    @Test void negativeAndMixedImpedanceRemainSigned() throws Exception {
        var f = fixture(true);
        f.bus.setInitConstZStaticLoad(new Complex(-.2, -.3));
        var y = stamp(f.bus);
        assertPower(new Complex(-.2, .3).divide(1.21), y);
        assertPower(new Complex(.8 / 1.1 - .2 / 1.21, -.3 / 1.21), consumed(f.bus, y, 1.));
    }

    @Test void disabledOptionPreservesOriginalCurrentOnlyPath() throws Exception {
        var f = fixture(false);
        assertPower(Complex.ZERO, stamp(f.bus));
        assertPower(new Complex(.8 / 1.1, .3 / 1.21), consumed(f.bus, Complex.ZERO, 1.));
    }

    @Test void nonLoadBusDoesNotStampStoredLoadData() throws Exception {
        var f = fixture(true);
        f.bus.setLoadCode(AclfLoadCode.NON_LOAD);
        assertPower(Complex.ZERO, stamp(f.bus));
        assertPower(Complex.ZERO, consumed(f.bus, Complex.ZERO, 1.));
    }

    @Test void completeLoadMatrixModeDoesNotStampTheImpedanceTwice() throws Exception {
        var f = fixture(false);
        f.bus.setLoadP(.8);
        f.bus.setLoadQ(.3);
        f.bus.setVoltage(new Complex(1.1));
        f.net.setStaticLoadIncludedInYMatrix(true);
        var original = stamp(f.bus);
        f.net.setStaticLoadZPartInYMatrix(true);
        assertPower(original, stamp(f.bus));
        assertPower(Complex.ZERO, f.bus.injCurDynamic(StaticLoadModel.CONST_Z, .65, .05));
    }

    @Test void updatedImpedanceIsCompensatedUntilTheNextStamp() throws Exception {
        var f = fixture(true);
        var oldY = stamp(f.bus);
        f.bus.setInitConstZStaticLoad(new Complex(.2, .5));
        var expected = new Complex(.8 / 1.1 + .2 / 1.21, .5 / 1.21);
        assertPower(expected, consumed(f.bus, oldY, 1.));
        var newY = stamp(f.bus);
        assertPower(new Complex(.2, -.5).divide(1.21), newY);
        assertPower(expected, consumed(f.bus, newY, 1.));
    }

    @Test void nonzeroImpedanceRequiresAValidInitialVoltage() {
        for (double voltage : new double[] {0., -1., Double.NaN}) {
            var f = fixture(true);
            f.bus.setVoltage(Complex.ZERO);
            f.bus.setInitVoltMag(voltage);
            var error = assertThrows(InvocationTargetException.class, () -> stamp(f.bus));
            assertInstanceOf(IllegalStateException.class, error.getCause());
        }
    }
}
