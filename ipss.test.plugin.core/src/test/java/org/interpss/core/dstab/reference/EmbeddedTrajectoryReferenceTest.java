package org.interpss.core.dstab.reference;

import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class EmbeddedTrajectoryReferenceTest {
    private static final String REFERENCE = "smib-gensal";

    @Test
    void preservesEventOccurrencesAndResolvesFields() throws Exception {
        EmbeddedTrajectoryReference result = EmbeddedTrajectoryReference.embedded(REFERENCE);
        assertEquals(13, result.fields().size());
        assertTrue(!result.samples().isEmpty());
        assertTrue(!result.postEventSamples().isEmpty());
        assertEquals(2, result.samples().stream()
                .filter(sample -> Math.abs(sample.time() - 0.05) < 1.0e-12).count());
        assertEquals(0, result.samples().stream()
                .filter(sample -> Math.abs(sample.time() - 0.05) < 1.0e-12)
                .findFirst().orElseThrow().occurrence());
        assertEquals(1, result.samples().stream()
                .filter(sample -> Math.abs(sample.time() - 0.05) < 1.0e-12)
                .skip(1).findFirst().orElseThrow().occurrence());
        assertEquals(9, result.fieldIndex("Generator", "1 1", "TSRotorAngle"));
        assertEquals(12, result.fieldIndex("Generator", "2 1", "TSSpeed"));
    }

    @Test
    void sampleValuesAreDefensiveAndUnknownDatasetsFail() {
        EmbeddedTrajectoryReference result = EmbeddedTrajectoryReference.embedded(REFERENCE);
        double[] copy = result.samples().get(0).values();
        copy[0] = -999.0;
        assertNotEquals(copy[0], result.samples().get(0).value(0));
        assertThrows(IllegalArgumentException.class,
                () -> EmbeddedTrajectoryReference.embedded("unknown-dataset"));
    }
}
