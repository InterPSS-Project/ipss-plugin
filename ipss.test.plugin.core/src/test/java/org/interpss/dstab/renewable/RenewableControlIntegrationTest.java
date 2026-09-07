package org.interpss.dstab.renewable;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Numerical-method contract shared by the hand-coded renewable controllers. */
public class RenewableControlIntegrationTest {

    @Test
    void firstOrderLagUsesTheExplicitModifiedEulerCorrector() {
        double state = 1.0;
        double input = 0.5;
        double timeConstant = 0.1;
        double dt = 0.02;

        double initialDerivative = (input - state) / timeConstant;
        double predicted = state + dt * initialDerivative;
        double correctedDerivative = (input - predicted) / timeConstant;
        double expected = state + 0.5 * dt
                * (initialDerivative + correctedDerivative);

        assertEquals(0.91, expected, 1.0e-15);
        assertEquals(expected, Repca1Model.lag(state, input, timeConstant, dt), 1.0e-15);
    }

    @Test
    void zeroTimeConstantRemainsAnAlgebraicBypass() {
        assertEquals(0.5, Repca1Model.lag(1.0, 0.5, 0.0, 0.02), 0.0);
    }
}
