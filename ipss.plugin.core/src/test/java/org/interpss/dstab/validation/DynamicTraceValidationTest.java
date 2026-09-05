package org.interpss.dstab.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DynamicTraceValidationTest {
    @Test
    void csvRoundTripPreservesCanonicalLongForm(@TempDir Path tempDir) throws Exception {
        List<DynamicTraceSample> samples = List.of(
                sample(.1, "Bus1-mach1", "SPEED", 1.001, "pu", "machine:100MVA"),
                sample(0, "Bus1-mach1", "SPEED", 1.0, "pu", "machine:100MVA"),
                sample(0, "Bus,2", "BUS_VOLTAGE", .99, "pu", "system:100MVA"));
        Path csv = tempDir.resolve("nested").resolve("trace.csv");

        DynamicTraceCsv.write(csv, samples);

        assertEquals(DynamicTraceCsv.HEADER, Files.readAllLines(csv).get(0));
        assertEquals(List.of(samples.get(2), samples.get(1), samples.get(0)),
                DynamicTraceCsv.read(csv));
    }

    @Test
    void comparatorInterpolatesOrdinarySamplesButRequiresEventBoundaries() {
        List<DynamicTraceSample> reference = List.of(
                sample(0, "Bus1", "BUS_VOLTAGE", 1.0, "pu", "system:100MVA"),
                sample(.05, "Bus1", "BUS_VOLTAGE", .8, "pu", "system:100MVA"),
                sample(.10, "Bus1", "BUS_VOLTAGE", .9, "pu", "system:100MVA"));
        List<DynamicTraceSample> missingBoundary = List.of(
                sample(0, "Bus1", "BUS_VOLTAGE", 1.0, "pu", "system:100MVA"),
                sample(.025, "Bus1", "BUS_VOLTAGE", .9, "pu", "system:100MVA"),
                sample(.075, "Bus1", "BUS_VOLTAGE", .85, "pu", "system:100MVA"),
                sample(.10, "Bus1", "BUS_VOLTAGE", .9, "pu", "system:100MVA"));

        assertThrows(IllegalArgumentException.class, () -> DynamicTraceComparator.compare(
                missingBoundary, reference, DynamicTraceToleranceProfile.engineering(),
                1e-9, List.of(.05)));

        List<DynamicTraceSample> actual = List.of(missingBoundary.get(0), missingBoundary.get(1),
                sample(.05, "Bus1", "BUS_VOLTAGE", .8, "pu", "system:100MVA"),
                missingBoundary.get(2), missingBoundary.get(3));
        DynamicTraceComparison result = DynamicTraceComparator.compare(actual, reference,
                DynamicTraceToleranceProfile.strict(1e-12), 1e-9, List.of(.05));
        assertTrue(result.passed());
        assertEquals(0.0, result.metrics().get(0).eventAbsoluteErrors().get(.05), 0.0);
    }

    @Test
    void comparatorRanksWorstSignalAndReportsFirstCrossing() {
        List<DynamicTraceSample> reference = List.of(
                sample(0, "Bus1-mach1", "SPEED", 1.0, "pu", "machine:100MVA"),
                sample(.1, "Bus1-mach1", "SPEED", 1.0, "pu", "machine:100MVA"),
                sample(.2, "Bus1-mach1", "SPEED", 1.0, "pu", "machine:100MVA"),
                sample(0, "Bus1", "BUS_VOLTAGE", 1.0, "pu", "system:100MVA"),
                sample(.1, "Bus1", "BUS_VOLTAGE", .9, "pu", "system:100MVA"),
                sample(.2, "Bus1", "BUS_VOLTAGE", 1.0, "pu", "system:100MVA"));
        List<DynamicTraceSample> actual = List.of(
                sample(0, "Bus1-mach1", "SPEED", 1.0, "pu", "machine:100MVA"),
                sample(.1, "Bus1-mach1", "SPEED", 1.0001, "pu", "machine:100MVA"),
                sample(.2, "Bus1-mach1", "SPEED", 1.0002, "pu", "machine:100MVA"),
                sample(0, "Bus1", "BUS_VOLTAGE", 1.0, "pu", "system:100MVA"),
                sample(.1, "Bus1", "BUS_VOLTAGE", .89, "pu", "system:100MVA"),
                sample(.2, "Bus1", "BUS_VOLTAGE", .98, "pu", "system:100MVA"));

        DynamicTraceComparison result = DynamicTraceComparator.compare(actual, reference,
                DynamicTraceToleranceProfile.engineering(), 1e-9, List.of(.1, .2));

        assertFalse(result.passed());
        assertEquals("BUS_VOLTAGE", result.metrics().get(0).key().signal());
        assertEquals(.1, result.metrics().get(0).firstThresholdCrossingSeconds(), 0.0);
        assertEquals(.02, result.metrics().get(0).maximumChangeFromInitialError(), 1e-12);
        assertEquals(2.0e-4, result.metrics().get(1).maximumAbsoluteError(), 1e-12);
    }

    @Test
    void comparatorRejectsUnitOrBaseMismatch() {
        List<DynamicTraceSample> reference = List.of(
                sample(0, "Gen1", "P", 1, "pu", "machine:50MVA"));
        List<DynamicTraceSample> actual = List.of(
                sample(0, "Gen1", "P", 1, "MW", "system:100MVA"));

        assertThrows(IllegalArgumentException.class, () -> DynamicTraceComparator.compare(
                actual, reference, DynamicTraceToleranceProfile.engineering(), 1e-9, List.of()));
    }

    private static DynamicTraceSample sample(double time, String device, String signal,
            double value, String unit, String base) {
        return new DynamicTraceSample(time, device, signal, value, unit, base);
    }
}
