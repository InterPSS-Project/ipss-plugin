package org.interpss.dstab.dynLoad.impl;

import static org.junit.jupiter.api.Assertions.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.dynLoad.Cmldznu2Data;
import org.junit.jupiter.api.Test;
import com.interpss.dstab.DStabObjectFactory;

/** Full restoration is algebraic; partial restoration retains its distinct policy. */
class ElectronicFullRecoveryTest {
    private static Cmldznu2AlgebraicLoadModel model(double recovery) {
        var net = DStabObjectFactory.createDStabilityNetwork();
        net.setBaseKva(100000); net.setFrequency(60);
        var bus = DStabObjectFactory.createDStabBus("B", net).orElseThrow();
        bus.setVoltage(Complex.ONE); bus.setFreq(1);
        bus.setLoadCode(com.interpss.core.aclf.AclfLoadCode.CONST_P);
        bus.setLoadP(.4); bus.setLoadQ(.1); bus.setInitLoad(new Complex(.4, .1));
        double[] c = new double[133]; c[23] = .96; c[24] = .72; c[25] = .60;
        c[132] = recovery;
        var model = new Cmldznu2AlgebraicLoadModel(Cmldznu2AlgebraicLoadModel.Kind.ELECTRONIC,
                bus, "E", 1, new Cmldznu2Data(c));
        assertTrue(model.initStates()); return model;
    }
    private static Complex power(Cmldznu2AlgebraicLoadModel model, double voltage) {
        model.getDStabBus().setVoltage(new Complex(voltage));
        return model.effectivePower();
    }
    @Test void completeRecoveryDependsOnlyOnPresentVoltage() {
        var m = model(1);
        for (double v : new double[]{.5, .66, .8, .60, .72, .61, .9, .0, .67, 1}) {
            double available = v >= .72 ? 1 : v <= .60 ? 0 : (v - .60) / (.72 - .60);
            var expected = m.getInitLoadPQ().multiply(available);
            assertEquals(expected, power(m, v));
            assertEquals(available, m.getElectronicFraction());
            assertEquals(expected, m.effectivePower());
        }
    }
    @Test void partialRecoveryKeepsItsExistingMinimumVoltagePolicy() {
        var m = model(.25);
        assertEquals(.2, power(m, .66).getReal(), 1e-15);
        assertEquals(.25, power(m, .9).getReal(), 1e-15);
        assertEquals(0, power(m, .5).getReal());
        assertEquals(.1, power(m, .9).getReal(), 1e-15);
    }
    @Test void freshCurrentAndLoadScalingRemainPhysicalAfterDropout() {
        var m = model(1); power(m, .5); assertTrue(m.changeLoad(-.3));
        var p = power(m, .66);
        assertEquals(.14, p.getReal(), 1e-15);
        var v = m.getDStabBus().getVoltage();
        assertEquals(m.getEquivY().multiply(v).subtract(p.divide(v).conjugate()), m.getNortonCurInj());
        assertEquals(p, m.getLoadPQ());
        assertEquals(.28, power(m, .9).getReal(), 1e-15);
    }

    @Test void preparedCurrentInterfaceSurvivesRepeatedLeafInitialization() {
        var m = model(1); m.useCurrentSourceInterface();
        for (int i = 0; i < 2; i++) {
            assertTrue(m.initStates()); assertEquals(Complex.ZERO, m.getEquivY());
            var p = power(m, .66); var v = m.getDStabBus().getVoltage();
            assertEquals(p.divide(v).conjugate().negate(), m.getNortonCurInj());
        }
    }

    @Test void mixedPortOwnsOnlyItsLeavesAndRetainsUnrelatedParentShunts() {
        for (boolean frequencyDependent : new boolean[]{false, true}) {
            var net = DStabObjectFactory.createDStabilityNetwork(); net.setBaseKva(100000); net.setFrequency(60);
            var bus = DStabObjectFactory.createDStabBus("B", net).orElseThrow();
            bus.setLoadCode(com.interpss.core.aclf.AclfLoadCode.CONST_P);
            bus.setLoadP(.6); bus.setLoadQ(.15); bus.setInitLoad(new Complex(.6, .15));
            bus.setVoltage(Complex.ONE); bus.setFreq(1); var originalShunt = new Complex(0, .03); bus.setShuntY(originalShunt);
            double[] c = new double[132]; c[22] = .5; c[23] = .96; c[24] = .72; c[25] = .6; c[26] = .94;
            c[27] = c[32] = 2; c[28] = c[33] = 1; c[31] = frequencyDependent ? .5 : 0;
            var composite = new Cmldblu1Model("M", bus, java.util.Set.of("1"), new Complex(.4, .1), c);
            assertTrue(composite.initStates());
            var s = bus.getDynLoadModelList().stream().filter(v -> v.getId().equals("M_S")).findFirst().orElseThrow();
            var e = bus.getDynLoadModelList().stream().filter(v -> v.getId().equals("M_E")).findFirst().orElseThrow();
            assertEquals(!frequencyDependent, s instanceof LegacyStaticLoadPort);
            assertEquals(.2, s.getInitLoadPQ().getReal(), 1e-15); assertEquals(.2, e.getInitLoadPQ().getReal(), 1e-15);
            assertEquals(100.0 / 3, s.getLoadPercent(), 1e-12); assertEquals(100.0 / 3, e.getLoadPercent(), 1e-12);
            assertEquals(.6, bus.getInitLoad().getReal(), 1e-15);
            if (!frequencyDependent) {
                assertTrue(e.initStates()); assertEquals(Complex.ZERO, e.getEquivY());
                assertEquals(0, bus.getShuntY().add(s.getEquivY()).add(e.getEquivY()).subtract(originalShunt).abs(), 1e-15);
            } else assertTrue(e.getEquivY().abs() > 0);
        }
    }
}
