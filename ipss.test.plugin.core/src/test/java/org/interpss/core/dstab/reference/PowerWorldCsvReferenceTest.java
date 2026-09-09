package org.interpss.core.dstab.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

public class PowerWorldCsvReferenceTest {
    private static final Path REFERENCE = Path.of(
            "testData", "reference", "powerworld", "smib-gensal", "powerworld.csv");

    @Test
    void preservesEventOccurrencesAndResolvesFields() throws Exception {
        PowerWorldCsvReference result = PowerWorldCsvReference.read(REFERENCE);
        assertEquals(13, result.fields().size());
        assertEquals(245, result.samples().size());
        assertEquals(243, result.postEventSamples().size());
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
    void sampleValuesAreDefensiveAndMalformedWidthsFail() throws Exception {
        PowerWorldCsvReference result = PowerWorldCsvReference.read(REFERENCE);
        double[] copy = result.samples().get(0).values();
        copy[0] = -999.0;
        assertNotEquals(copy[0], result.samples().get(0).value(0));

        Path malformed = Files.createTempFile("powerworld-width", ".csv");
        try {
            Files.writeString(malformed, "ObjectFields\n"
                    + "ObjectType, PrimaryKey, SecondaryKey, Label, VariableName, ColHeader\n"
                    + "Bus,1,BUS_100,,TSVpu,V pu\nEND\n"
                    + "Results,\"Fault\"\n0,\nEND\n");
            assertThrows(IllegalArgumentException.class,
                    () -> PowerWorldCsvReference.read(malformed));
        } finally {
            Files.deleteIfExists(malformed);
        }
    }
}
