package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import org.interpss.dstab.algo.ControllerStepStability;
import org.junit.jupiter.api.Test;

/** Stability-region checks independent of any particular controller parameters. */
class ControllerStepStabilityTest {
    @Test void retainsSlowModesAndResolvesFastDecay() {
        assertEquals(1, ControllerStepStability.substeps(new double[][]{{-10}}, 1./240));
        // The two-step rule has a negative-real stability interval [-1, 0].
        // A 20 percent margin requires at least four pieces for this pole.
        assertEquals(4, ControllerStepStability.substeps(new double[][]{{-480}}, 1./240));
    }

    @Test void resolvesDampedOscillationsRatherThanOnlyRealTimeConstants() {
        assertEquals(2, ControllerStepStability.substeps(new double[][]{{-100, -300}, {300, -100}}, 1./240));
    }

    @Test void preservesNeutralAndPhysicallyGrowingModes() {
        assertEquals(1, ControllerStepStability.substeps(new double[][]{{0}}, 1./240));
        assertEquals(1, ControllerStepStability.substeps(new double[][]{{10}}, 1./240));
        assertEquals(4, ControllerStepStability.substeps(new double[][]{{480}}, 1./240));
    }

    @Test void rejectsInvalidAndUnresolvableInputs() {
        assertThrows(IllegalArgumentException.class, () -> ControllerStepStability.substeps(new double[][]{{1}}, 0));
        assertThrows(IllegalArgumentException.class, () -> ControllerStepStability.substeps(new double[][]{{1, 2}}, .01));
        assertThrows(IllegalArgumentException.class, () -> ControllerStepStability.substeps(new double[][]{{Double.NaN}}, .01));
        assertThrows(IllegalArgumentException.class, () -> ControllerStepStability.substeps(new double[][]{{-1e12}}, .01));
    }
}
