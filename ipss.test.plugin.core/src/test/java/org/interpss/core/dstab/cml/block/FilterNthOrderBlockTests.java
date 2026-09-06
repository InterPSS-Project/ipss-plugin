package org.interpss.core.dstab.cml.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.interpss.dstab.controller.cml.field.block.FilterNthOrderBlock;

public class FilterNthOrderBlockTests {
    @Test
    void twoPoleStepMatchesDeclaredRampFilterTransferFunction() {
        double timeConstant = 0.1;
        double dt = 0.0005;
        double elapsed = 0.1;
        FilterNthOrderBlock block = new FilterNthOrderBlock(0.0, timeConstant, 1, 2);

        assertTrue(block.initStateY0(0.0));
        for (int i = 0; i < Math.round(elapsed / dt); i++) {
            block.eulerStep1(1.0, dt);
            block.eulerStep2(1.0, dt);
        }

        double normalizedTime = elapsed / timeConstant;
        double expected = 1.0 - Math.exp(-normalizedTime) * (1.0 + normalizedTime);
        assertEquals(expected, block.getY(), 2.0e-3);
    }

    @Test
    void zeroOverallOrderBypassesRampFilter() {
        FilterNthOrderBlock block = new FilterNthOrderBlock(0.4, 0.1, 0, 0);

        assertTrue(block.initStateY0(0.0));
        block.eulerStep1(0.375, 0.01);
        block.eulerStep2(0.375, 0.01);

        assertEquals(0.375, block.getY(), 1.0e-12);
    }

    @Test
    void equalLeadAndLagWithUnitDenominatorOrderIsUnityForAnyOverallOrder() {
        FilterNthOrderBlock block = new FilterNthOrderBlock(0.1, 0.1, 1, 4);

        assertTrue(block.initStateY0(-0.2));
        block.eulerStep1(0.6, 0.01);
        block.eulerStep2(0.6, 0.01);

        assertEquals(0.6, block.getY(), 1.0e-12);
    }
}
