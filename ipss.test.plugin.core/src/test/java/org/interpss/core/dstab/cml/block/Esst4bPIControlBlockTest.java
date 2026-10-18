package org.interpss.core.dstab.cml.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.dstab.control.exc.ieee.y2005.st4b.Esst4bPIControlBlock;
import org.junit.jupiter.api.Test;

public class Esst4bPIControlBlockTest {
    private static final double TOL = 1.0e-12;

    @Test
    void nativeIntegralContinuesWhileOnlyTheTotalOutputClips() {
        var block = new Esst4bPIControlBlock(2.0, 1.0, 1.0, -1.0, 1.0);
        assertTrue(block.initStateY0(.2));
        step(block, 1.0, .01);
        assertEquals(.21, block.getStateX(), TOL);
        assertEquals(1.0, block.getY(), TOL);
        assertEquals(1.0, block.getDX_dt(), TOL);
        assertFalse(block.isIntegratorFrozen());
    }

    @Test
    void nativeIntegralReachesBothStateBoundsAndReleasesInward() {
        var block = new Esst4bPIControlBlock(2.0, 1.0, 1.0, -.87, 1.0);
        assertTrue(block.initStateY0(.995));
        step(block, 1.0, .01);
        assertEquals(1.0, block.getStateX(), TOL);
        assertTrue(block.isIntegratorFrozen());
        step(block, -.2, .01);
        assertEquals(.998, block.getStateX(), TOL);
        assertFalse(block.isIntegratorFrozen());

        assertTrue(block.initStateY0(-.865));
        step(block, -1.0, .01);
        assertEquals(-.87, block.getStateX(), TOL);
        assertTrue(block.isIntegratorFrozen());
        step(block, .2, .01);
        assertEquals(-.868, block.getStateX(), TOL);
        assertFalse(block.isIntegratorFrozen());
    }

    @Test
    void nativeStateProjectionIgnoresProportionalFeedthrough() {
        var block = new Esst4bPIControlBlock(2.0, 1.0, 1.0, -.87, 1.0);
        assertTrue(block.initStateY0(.2));
        block.eulerStep1(1.0, 0.0);
        assertEquals(.3, block.projectIntegralState(.3), TOL);
        assertEquals(1.0, block.projectIntegralState(1.2), TOL);
        assertEquals(-.87, block.projectIntegralState(-1.0), TOL);
    }

    @Test
    void zeroIntegralGainHasNoArtificialStateEvenWithPositiveOutputBounds() {
        var block = new Esst4bPIControlBlock(2.0, 0.0, 5.0, 1.0, 1.0);
        assertTrue(block.initStateY0(2.0));
        assertEquals(1.0, block.getU0(), TOL);
        assertEquals(2.0, block.getY(), TOL);
        step(block, 3.0, .01);
        assertEquals(0.0, block.getStateX(), TOL);
        assertEquals(0.0, block.projectIntegralState(0.0), TOL);
        assertEquals(5.0, block.getY(), TOL);
    }

    @Test
    void inactiveLimitsPreserveModifiedEulerWithChangingStageInputs() {
        for (double convention : new double[] {0.0, 1.0}) {
            var block = new Esst4bPIControlBlock(2.0, 1.0, 5.0, -5.0, convention);
            assertTrue(block.initStateY0(.2));
            block.eulerStep1(.2, .01);
            block.eulerStep2(.4, .01);
            assertEquals(.203, block.getStateX(), TOL);
            assertEquals(1.003, block.getY(), TOL);
        }
    }

    @Test
    void explicitLegacyConventionRetainsOutputFreezeAndRelease() {
        var block = new Esst4bPIControlBlock(2.0, 1.0, 1.0, -1.0, 0.0);
        assertTrue(block.initStateY0(.2));
        step(block, 1.0, .01);
        assertEquals(.2, block.getStateX(), TOL);
        assertTrue(block.isIntegratorFrozen());
        assertEquals(1.2, block.projectIntegralState(1.2), TOL);
        step(block, -.2, .01);
        assertEquals(.198, block.getStateX(), TOL);
        assertFalse(block.isIntegratorFrozen());
    }

    private static void step(Esst4bPIControlBlock block, double input, double dt) {
        block.eulerStep1(input, dt);
        block.eulerStep2(input, dt);
    }
}
