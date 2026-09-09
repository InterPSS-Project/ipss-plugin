package org.interpss.core.dstab.cml.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.dstab.control.util.FreezeNonWindupPIControlBlock;
import org.junit.jupiter.api.Test;

public class FreezeNonWindupPIControlBlockTest {
    private static final double TOL = 1.0e-12;

    @Test
    void zeroIntegralGainInitializesAsPureProportionalPath() {
        FreezeNonWindupPIControlBlock block =
                new FreezeNonWindupPIControlBlock(2.0, 0.0, 5.0, -5.0);

        assertTrue(block.initStateY0(0.2));
        assertEquals(0.0, block.getStateX(), TOL);
        assertEquals(0.1, block.getU0(), TOL);
        assertEquals(0.2, block.getY(), TOL);
    }

    @Test
    void saturatedIntegratorFreezesAndReleasesForInwardInput() {
        FreezeNonWindupPIControlBlock block =
                new FreezeNonWindupPIControlBlock(2.0, 1.0, 1.0, -1.0);
        assertTrue(block.initStateY0(0.2));

        step(block, 1.0, 0.01);
        assertEquals(0.2, block.getStateX(), TOL);
        assertEquals(1.0, block.getY(), TOL);

        step(block, -0.2, 0.01);
        assertEquals(0.198, block.getStateX(), TOL);
        assertEquals(-0.202, block.getY(), TOL);
    }

    @Test
    void unconstrainedMotionUsesModifiedEulerCorrector() {
        FreezeNonWindupPIControlBlock block =
                new FreezeNonWindupPIControlBlock(2.0, 1.0, 5.0, -5.0);
        assertTrue(block.initStateY0(0.2));

        step(block, 0.5, 0.01);
        assertEquals(0.205, block.getStateX(), TOL);
        assertEquals(1.205, block.getY(), TOL);
    }

    private static void step(FreezeNonWindupPIControlBlock block, double input, double dt) {
        block.eulerStep1(input, dt);
        block.eulerStep2(input, dt);
    }
}
