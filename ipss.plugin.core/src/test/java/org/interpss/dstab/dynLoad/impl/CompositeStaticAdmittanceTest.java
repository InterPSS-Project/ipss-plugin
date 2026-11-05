package org.interpss.dstab.dynLoad.impl;

import static org.junit.jupiter.api.Assertions.*;

import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.dynLoad.Cmldznu2Data;
import org.junit.jupiter.api.Test;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.dstab.DStabObjectFactory;

/** Exact linear eligibility, fresh observations, mutable-input fallback and allocation. */
class CompositeStaticAdmittanceTest {
    private static Cmldznu2AlgebraicLoadModel model(double[] constants) {
        var net = DStabObjectFactory.createDStabilityNetwork(); net.setBaseKva(100000); net.setFrequency(60);
        var bus = DStabObjectFactory.createDStabBus("B", net).orElseThrow(); bus.setLoadCode(AclfLoadCode.CONST_P);
        bus.setLoadP(.4); bus.setLoadQ(.2); bus.setInitLoad(new Complex(.4, .2)); bus.setVoltage(new Complex(1.03, .07)); bus.setFreq(1);
        var m = new Cmldznu2AlgebraicLoadModel(Cmldznu2AlgebraicLoadModel.Kind.STATIC, bus, "S", .7, new Cmldznu2Data(constants));
        assertTrue(m.initStates()); return m;
    }
    private static double[] impedance() {
        double[] c = new double[133]; c[23] = .96; c[26] = .94; c[27] = 2; c[28] = 1; c[32] = 2; c[33] = 1; return c;
    }
    private static Complex generic(Cmldznu2AlgebraicLoadModel m, Complex power) {
        var v = m.getDStabBus().getVoltage(); return m.getEquivY().multiply(v).subtract(power.divide(v).conjugate());
    }
    private static void sameBits(Complex expected, Complex actual) {
        assertEquals(Double.doubleToLongBits(expected.getReal()), Double.doubleToLongBits(actual.getReal()));
        assertEquals(Double.doubleToLongBits(expected.getImaginary()), Double.doubleToLongBits(actual.getImaginary()));
    }
    @Test void exactImpedanceLeavesThePhysicalStampAndFreshPowerAtEveryTrialVoltage() {
        var m = model(impedance()); double v0 = m.getDStabBus().getVoltageMag(), p0 = m.getInitLoadPQ().getReal(), q0 = m.getInitLoadPQ().getImaginary();
        for (Complex v : new Complex[]{new Complex(.8, .21), new Complex(.1, .3), new Complex(1.2, -.4), new Complex(-.1, -.4)}) {
            m.getDStabBus().setVoltage(v); assertSame(Complex.ZERO, m.getNortonCurInj());
            var expectedPower = new Complex(p0 * Math.pow(v.abs() / v0, 2), q0 * Math.pow(v.abs() / v0, 2)); sameBits(expectedPower, m.getLoadPQ());
            assertEquals(0, generic(m, expectedPower).abs(), 5e-16); assertEquals(expectedPower.getReal(), m.getNamedStates().get("STATIC.P")); assertEquals(expectedPower.getImaginary(), m.getNamedStates().get("STATIC.Q"));
        }
        assertTrue(m.afterStep(0)); assertTrue(m.afterStep(.004));
    }
    @Test void distributedImpedanceCoefficientsAndSignedReactiveDemandUseTheSamePhysicalStamp() {
        double[] c = impedance(); c[28] = .25; c[29] = 2; c[30] = .75; c[33] = .5; c[34] = 2; c[35] = .5; c[26] = -.94;
        var m = model(c); m.getDStabBus().setVoltage(new Complex(.2, .4)); assertSame(Complex.ZERO, m.getNortonCurInj()); assertTrue(m.getLoadPQ().getImaginary() < 0); assertEquals(0, generic(m, m.getLoadPQ()).abs(), 5e-16);
    }
    @Test void nonzeroFloatingResidualAndFrequencySensitivityCannotBeFoldedAway() {
        double[] c = impedance(); c[28] = .7; c[29] = 2; c[30] = .3; assertNotEquals(0, 1 - c[28] - c[30]);
        double[] cancellation = impedance(); cancellation[28] = 1e308; cancellation[29] = 2; cancellation[30] = -1e308;
        for (double[] profile : new double[][]{c, cancellation, withFrequency(impedance()), mixed()}) {
            var m = model(profile); m.getDStabBus().setVoltage(new Complex(.61, .28)); m.getDStabBus().setFreq(.982);
            var expected = generic(m, m.effectivePower()); var actual = m.getNortonCurInj(); assertNotSame(Complex.ZERO, actual); sameBits(expected, actual);
        }
    }
    private static double[] withFrequency(double[] c) { c[31] = -.4; c[36] = .6; return c; }
    private static double[] mixed() { double[] c = impedance(); c[28] = .25; c[29] = 1; c[30] = .5; c[33] = .65; c[34] = 0; c[35] = .1; return c; }
    @Test void loadChangesSheddingAndReplacementStampsKeepTheGenericEquation() {
        var m = model(impedance()); m.getDStabBus().setVoltage(new Complex(.7, -.2)); var original = m.getEquivY();
        assertTrue(m.changeLoad(.1)); sameBits(generic(m, m.effectivePower()), m.getNortonCurInj());
        assertTrue(m.changeLoad(-.1)); assertSame(Complex.ZERO, m.getNortonCurInj());
        assertTrue(m.changeLoad(-1)); sameBits(generic(m, Complex.ZERO), m.getNortonCurInj()); assertEquals(Complex.ZERO, m.getLoadPQ());
        assertTrue(m.changeLoad(1)); assertSame(Complex.ZERO, m.getNortonCurInj());
        m.setEquivY(original.add(new Complex(.1, -.05))); sameBits(generic(m, m.effectivePower()), m.getNortonCurInj());
        m.setEquivY(original); assertSame(Complex.ZERO, m.getNortonCurInj());
    }
    @Test void nonfiniteTrialInputsKeepTheNumericalLibraryFallback() {
        var m = model(impedance()); m.getDStabBus().setVoltage(new Complex(Double.POSITIVE_INFINITY, 1)); assertTrue(m.getNortonCurInj().isNaN());
        m.getDStabBus().setVoltage(new Complex(.8, .2)); m.getDStabBus().setFreq(Double.NaN); assertTrue(m.getNortonCurInj().isNaN());
    }
    @Test void warmedLinearPublicationAllocatesOnlyItsFreshPowerObservation() {
        var generic = java.lang.management.ManagementFactory.getThreadMXBean(); org.junit.jupiter.api.Assumptions.assumeTrue(generic instanceof com.sun.management.ThreadMXBean);
        var bean = (com.sun.management.ThreadMXBean) generic; org.junit.jupiter.api.Assumptions.assumeTrue(bean.isThreadAllocatedMemorySupported()); bean.setThreadAllocatedMemoryEnabled(true);
        var m = model(impedance()); m.getDStabBus().setVoltage(new Complex(.8, .2)); for (int i = 0; i < 100000; i++) m.getNortonCurInj();
        long id = Thread.currentThread().threadId(), before = bean.getThreadAllocatedBytes(id); for (int i = 0; i < 20000; i++) m.getNortonCurInj();
        long bytes = bean.getThreadAllocatedBytes(id) - before; assertTrue(bytes <= 20000L * 32 + 256, "Only the published immutable power escapes: " + bytes);
    }
}
