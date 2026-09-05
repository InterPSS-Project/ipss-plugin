package org.interpss.dstab.control.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AsymmetricDeadbandBlockTest {
    private static final double TOL = 1.0e-12;

    @Test
    void applyIsContinuousAtBothAsymmetricThresholds() {
        assertEquals(0.0, AsymmetricDeadbandBlock.apply(0.002, 0.002, -0.003), TOL);
        assertEquals(0.0, AsymmetricDeadbandBlock.apply(-0.003, 0.002, -0.003), TOL);
        assertEquals(0.008, AsymmetricDeadbandBlock.apply(0.010, 0.002, -0.003), TOL);
        assertEquals(-0.007, AsymmetricDeadbandBlock.apply(-0.010, 0.002, -0.003), TOL);
    }

    @Test
    void rejectsThresholdsThatDoNotBracketZero() {
        AsymmetricDeadbandBlock block = new AsymmetricDeadbandBlock();
        assertThrows(IllegalArgumentException.class,
                () -> block.setThresholds(-0.001, -0.003));
        assertThrows(IllegalArgumentException.class,
                () -> block.setThresholds(0.003, 0.001));
    }
}
