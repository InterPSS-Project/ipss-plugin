package org.interpss.core.dstab.cml.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.dstab.control.pss.ieee.y1992.pss2a.Pss2aLeadLagBlock;
import org.junit.jupiter.api.Test;

public class Pss2aLeadLagBlockTest {
    @Test
    void properLeadLagMatchesPowerWorldTransferFunctionStepResponse() {
        double a = 0.5;
        double ta = 0.2;
        double tb = 0.1;
        double dt = 0.0002;
        double elapsed = 0.2;
        Pss2aLeadLagBlock block = new Pss2aLeadLagBlock(a, ta, tb, 10.0, -10.0);

        assertTrue(block.initStateY0(0.0));
        for (int i = 0; i < Math.round(elapsed / dt); i++) {
            block.eulerStep1(1.0, dt);
            block.eulerStep2(1.0, dt);
        }

        double directGain = ta / tb;
        double expected = a + (directGain - a) * Math.exp(-elapsed / tb);
        assertEquals(expected, block.getY(), 2.0e-4);
    }

    @Test
    void psseDefaultsAreIdentityAndFinalLimitsAreApplied() {
        Pss2aLeadLagBlock block = new Pss2aLeadLagBlock(1.0, 0.0, 0.0, 0.1, -0.05);

        assertTrue(block.initStateY0(0.0));
        block.eulerStep1(0.2, 0.01);
        block.eulerStep2(0.2, 0.01);
        assertEquals(0.1, block.getY(), 1.0e-12);

        block.eulerStep1(-0.2, 0.01);
        block.eulerStep2(-0.2, 0.01);
        assertEquals(-0.05, block.getY(), 1.0e-12);
        assertFalse(block.initStateY0(0.2));
    }
}
