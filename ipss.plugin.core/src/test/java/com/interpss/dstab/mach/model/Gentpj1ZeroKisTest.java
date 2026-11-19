package com.interpss.dstab.mach.model;

import com.interpss.dstab.mach.ieeevc.*;
import com.interpss.dstab.mach.model.*;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** The zero-current multiplier is a retained saturation boundary, not a model substitute. */
class Gentpj1ZeroKisTest {
    private static Gentpj1Data data(double kis) {
        return new Gentpj1Data(6.35, .041, .48, .067, 5.9, .12,
                .93, .88, .14, .205, .089, .071, .045, .035, .18, kis);
    }

    @Test void zeroRemovesOnlyTheCurrentContribution() {
        var zero = new Gentpj1Machine(data(0));
        assertEquals(0, zero.getGentpj1Data().kis(), 0);
        // Type-J native data uses scaled quadratic saturation.
        assertEquals(1, zero.getGenqecData().satFunc());
        for (double current : new double[] {0, .5, 2, 10}) {
            assertEquals(1.15, zero.saturationInput(1.15, current), 0);
        }
        var positive = new Gentpj1Machine(data(.12));
        assertEquals(1.15 + .12 * 2, positive.saturationInput(1.15, 2), 1e-14);
    }

    @Test void finiteInteriorAndZeroAreRetained() {
        for (double kis : new double[] {0, Double.MIN_VALUE, .12, Math.nextDown(1.0)}) {
            assertEquals(kis, data(kis).kis(), 0);
        }
    }

    @Test void zeroInitializesAndRetainsAnUnperturbedOperatingPoint() throws Exception {
        org.interpss.IpssCorePlugin.init();
        var network = com.interpss.dstab.util.sample.SampleDStabCase.createDStabTestNet();
        var machine = new org.interpss.fadapter.builder.DStabNetworkBuilder(network)
                .addGentpj1("Gen", "G1", 100, 1, data(0));
        var bus = network.getDStabBus("Gen");
        bus.initStates();
        assertTrue(machine.initStates(bus));
        double angle = machine.getAngle(), speed = machine.getSpeed();
        for (int step = 0; step < 40; step++) {
            machine.getIgen();
            assertTrue(machine.nextStep(.0025, com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER, 0));
            machine.getIgen();
            assertTrue(machine.nextStep(.0025, com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER, 1));
        }
        assertEquals(angle, machine.getAngle(), 2e-6);
        assertEquals(speed, machine.getSpeed(), 1e-7);
        assertTrue(Double.isFinite(machine.getEffectiveSaturationFactor()));
    }

    @Test void otherDomainBoundsRemainRejected() {
        for (double kis : new double[] {-Double.MIN_VALUE, -.1, 1, 2,
                Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> data(kis));
        }
    }
}
