package org.interpss.dstab.control.pss.psse.ieeest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SecondOrderLeadLagBlockTest {
    private static final double TOLERANCE = 1.0e-12;

    @Test
    void zeroQuadraticDenominatorRetainsThePublishedFirstOrderPole() {
        SecondOrderLeadLagBlock block = new SecondOrderLeadLagBlock(0.2, 0.0, 0.05, 0.0);
        assertTrue(block.initStateU0(0.0));

        block.eulerStep1(1.0, 0.01);
        block.eulerStep2(1.0, 0.01);

        double expectedState = 0.04875;
        double expectedOutput = expectedState + 0.05 * (1.0 - expectedState) / 0.2;
        assertEquals(expectedState, block.getStateX(), TOLERANCE);
        assertEquals(expectedOutput, block.getY(), TOLERANCE);
    }

    @Test
    void zeroDenominatorCoefficientsAreTheOnlyAlgebraicBypass() {
        SecondOrderLeadLagBlock lag = new SecondOrderLeadLagBlock(0.02, 0.0, 0.0, 0.0);
        assertTrue(lag.initStateU0(0.0));
        lag.eulerStep1(1.0, 0.001);
        lag.eulerStep2(1.0, 0.001);
        assertTrue(lag.getY() > 0.0 && lag.getY() < 1.0);

        SecondOrderLeadLagBlock bypass = new SecondOrderLeadLagBlock(0.0, 0.0, 0.0, 0.0);
        assertTrue(bypass.initStateU0(0.0));
        bypass.eulerStep1(1.0, 0.001);
        bypass.eulerStep2(1.0, 0.001);
        assertEquals(1.0, bypass.getY(), TOLERANCE);
    }
}
