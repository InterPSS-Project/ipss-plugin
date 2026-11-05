package org.interpss.dstab.dynLoad.impl;

import static org.junit.jupiter.api.Assertions.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.dynLoad.Cmldznu2Data;
import org.junit.jupiter.api.Test;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.device.NetworkIterationAware;

/** Prepared network memory, read-only queries and retained physical capacitor current. */
class LegacyStaticLoadPortTest {
    private static final NetworkIterationAware.Context CONTEXT =
            new NetworkIterationAware.Context("INITIAL", 0, 0, 50, 1e-9);
    private static LegacyStaticLoadPort model() {
        var net = DStabObjectFactory.createDStabilityNetwork(); net.setBaseKva(100000); net.setFrequency(60);
        var bus = DStabObjectFactory.createDStabBus("B", net).orElseThrow();
        bus.setLoadCode(com.interpss.core.aclf.AclfLoadCode.CONST_P); bus.setLoadP(.4); bus.setLoadQ(.2);
        bus.setVoltage(Complex.ONE); bus.setFreq(1); bus.setInitLoad(new Complex(.4, .2));
        double[] c = new double[133]; c[23] = .96; c[26] = .94;
        c[27] = c[32] = 2; c[28] = c[33] = 1;
        var m = new LegacyStaticLoadPort(bus, "S", new Cmldznu2Data(c), .4, new Complex(0, .04));
        assertTrue(m.initStates()); return m;
    }
    private static void voltage(LegacyStaticLoadPort m, double value) { m.getDStabBus().setVoltage(new Complex(value)); }
    private static double g(LegacyStaticLoadPort m) { return m.getNamedStates().get("STATIC.LowVoltageG"); }
    private static void accept(LegacyStaticLoadPort m, double v) throws Exception {
        voltage(m, v); m.beginNetworkSolution(CONTEXT); m.beforeNetworkIteration(0);
        m.afterNetworkConvergence(); m.endNetworkSolution(true);
    }
    @Test void gettersNeverPrepareOrCommitWorkingAdmittance() throws Exception {
        var m = model(); voltage(m, .5);
        for (int i = 0; i < 3; i++) { m.getNortonCurInj(); m.getStates(null); assertEquals(0, g(m)); }
        m.beginNetworkSolution(CONTEXT); m.beforeNetworkIteration(0);
        assertEquals(.4, m.effectivePower().getReal(), 1e-15); assertEquals(0, g(m));
        voltage(m, .4); assertEquals(.256, m.effectivePower().getReal(), 1e-15);
        for (int i = 0; i < 3; i++) m.getNortonCurInj(); assertEquals(0, g(m));
        m.afterNetworkConvergence(); m.endNetworkSolution(true); assertEquals(1.6, g(m), 1e-15);
        voltage(m, .9); assertEquals(.324, m.effectivePower().getReal(), 1e-15);
        accept(m, .3); assertEquals(1.6, g(m), 1e-15); assertEquals(.144, m.effectivePower().getReal(), 1e-15);
        assertThrows(UnsupportedOperationException.class, () -> m.getNamedStates().put("STATIC.LowVoltageG", 0.0));
    }
    @Test void failedSolveDiscardsItsFirstLowVoltagePreparation() throws Exception {
        var m = model(); voltage(m, .5); m.beginNetworkSolution(CONTEXT); m.beforeNetworkIteration(0);
        m.getNortonCurInj(); m.endNetworkSolution(false); assertEquals(0, g(m));
        accept(m, .4); assertEquals(2.5, g(m), 1e-15);
        m.beginNetworkSolution(CONTEXT); voltage(m, .3); m.beforeNetworkIteration(0);
        m.endNetworkSolution(false); assertEquals(2.5, g(m), 1e-15);
    }
    @Test void precedingAcceptedPowerIsSeparateFromOutputQueriesAndNominalPower() throws Exception {
        var m = model(); accept(m, .8); voltage(m, 1.2); m.getNortonCurInj();
        assertEquals(.4 * .8 * .8, m.getNamedStates().get("STATIC.PrecedingP"), 1e-15);
        accept(m, .5); assertEquals(.4 * .8 * .8 / .25, g(m), 1e-15);
        assertNotEquals(.4 / .25, g(m));
    }
    @Test void onlyTheQualifiedWholeStaticPortUsesThisPreparationPolicy() {
        for (int frequencyIndex : new int[]{-1, 31, 36}) {
            var net = DStabObjectFactory.createDStabilityNetwork(); net.setBaseKva(100000); net.setFrequency(60);
            var bus = DStabObjectFactory.createDStabBus("B", net).orElseThrow();
            bus.setLoadCode(com.interpss.core.aclf.AclfLoadCode.CONST_P); bus.setLoadP(.4); bus.setLoadQ(.1);
            bus.setInitLoad(new Complex(.4, .1)); bus.setVoltage(Complex.ONE); bus.setFreq(1);
            double[] c = new double[132]; c[23] = .96; c[26] = -.94; c[27] = c[32] = 2; c[28] = c[33] = 1;
            if (frequencyIndex >= 0) c[frequencyIndex] = .5;
            var m = new Cmldblu1Model("C", bus, java.util.Set.of("1"), new Complex(.4, .1), c);
            assertTrue(m.initStates());
            var component = bus.getDynLoadModelList().getFirst();
            assertEquals(frequencyIndex < 0, component instanceof LegacyStaticLoadPort);
            assertTrue(component.getInitLoadPQ().getImaginary() > 0);
            assertEquals(-.94, m.getLegacyConstants()[26]); assertEquals(-.94, m.getData().value(26));
            assertTrue(Cmldznu2Model.isAlgebraicComponent(component));
        }
    }
    @Test void exactThresholdDoesNotPrepareButTheLowerSideDoes() throws Exception {
        var m = model(); accept(m, .7); assertEquals(0, g(m));
        accept(m, Math.nextDown(.7)); assertTrue(g(m) > 0);
        assertEquals(.4, g(m), 1e-14);
    }
    @Test void loadFactorIsAppliedOnceBeforeAndAfterPreparation() throws Exception {
        var m = model(); assertTrue(m.changeLoad(.1)); accept(m, .9); accept(m, .5);
        assertEquals(.4 * .9 * .9 / .25, g(m), 1e-15);
        assertEquals(.4 * .9 * .9 * 1.1, m.getLoadPQ().getReal(), 1e-15);
        assertTrue(m.changeLoad(-1)); assertTrue(m.changeLoad(-.1)); assertEquals(Complex.ZERO, m.effectivePower());
        assertTrue(m.changeLoad(1.1)); assertEquals(.4 * .9 * .9 * 1.1, m.effectivePower().getReal(), 1e-15);
    }
    @Test void numericalPairRetainsThePhysicalCapacitorCurrent() {
        var m = model(); m.getDStabBus().setVoltage(new Complex(.8, .2));
        var v = m.getDStabBus().getVoltage(); var shunt = new Complex(0, .04);
        assertEquals(Complex.ZERO, shunt.add(m.getPosSeqEquivY()));
        var physical = m.effectivePower().divide(v).conjugate().add(shunt.multiply(v));
        assertEquals(0, physical.add(m.getNortonCurInj()).abs(), 1e-15);
    }
    @Test void scalarCurrentKeepsTheGenericArithmeticForBothDivisionBranches() throws Exception {
        var m = model(); accept(m, .5);
        for (Complex voltage : new Complex[]{new Complex(.8, .2), new Complex(.2, .8), new Complex(0, .4),
                new Complex(-.9, .2), new Complex(-.2, -.8), new Complex(.4, -0.0)}) {
            m.getDStabBus().setVoltage(voltage);
            var stored = m.getDStabBus().getVoltage();
            var expected = m.getEquivY().multiply(stored).subtract(m.effectivePower().divide(stored).conjugate());
            var actual = m.getNortonCurInj();
            assertEquals(Double.doubleToLongBits(expected.getReal()), Double.doubleToLongBits(actual.getReal()));
            assertEquals(Double.doubleToLongBits(expected.getImaginary()), Double.doubleToLongBits(actual.getImaginary()));
        }
        m.getDStabBus().setVoltage(Complex.NaN); assertTrue(m.getNortonCurInj().isNaN());
    }
    @Test void callbackMisuseAndInvalidVoltageDoNotAcceptMemory() throws Exception {
        var m = model(); assertThrows(Exception.class, () -> m.beforeNetworkIteration(0));
        m.beginNetworkSolution(CONTEXT); assertThrows(Exception.class, () -> m.beginNetworkSolution(CONTEXT));
        m.getDStabBus().setVoltage(Complex.NaN); assertThrows(Exception.class, () -> m.beforeNetworkIteration(0));
        m.endNetworkSolution(false); assertEquals(0, g(m));
    }
    @Test void warmedCurrentQueriesReuseStorageApartFromPublishedPowerAndCurrent() {
        var generic = java.lang.management.ManagementFactory.getThreadMXBean();
        org.junit.jupiter.api.Assumptions.assumeTrue(generic instanceof com.sun.management.ThreadMXBean);
        var bean = (com.sun.management.ThreadMXBean) generic;
        org.junit.jupiter.api.Assumptions.assumeTrue(bean.isThreadAllocatedMemorySupported()); bean.setThreadAllocatedMemoryEnabled(true);
        var m = model(); voltage(m, .8); for (int i = 0; i < 100000; i++) m.getNortonCurInj();
        long id = Thread.currentThread().threadId(), before = bean.getThreadAllocatedBytes(id);
        for (int i = 0; i < 20000; i++) m.getNortonCurInj();
        long bytes = bean.getThreadAllocatedBytes(id) - before;
        assertTrue(bytes <= 20000L * 64 + 512, "Fresh power/current only: " + bytes);
    }
}
