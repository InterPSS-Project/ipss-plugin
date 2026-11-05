package org.interpss.core.dstab.dynLoad;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Diagnoses the source-machine angle recurrence without changing a load trajectory gate. */
public class Cmldblu1RotorAngleHistoryReferenceTest {
    @ParameterizedTest
    @ValueSource(strings = {"overlap", "overlap-zero"})
    void firstFaultOnAngleStepsUsePriorSpeedHistory(String scenario) throws Exception {
        var archive = CompositeMotorOverlapTest.ARCHIVE;
        Assumptions.assumeTrue(Files.isRegularFile(archive));
        assertEquals("85107e36eaafd1939df3e46cee2b917fec95a5b096975c018aff9cc2c9b1c9c3",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))));
        String prefix = "target/cmld-motor-uv-enabled-" + scenario
                + "-cleared-20261104/cmldblu1-mixed-repeated-faults-h0.0005-";
        List<Map<String, Double>> rows;
        try (var zip = new ZipFile(archive.toFile())) {
            byte[] first = CompositeMotorRecloseTest.read(zip, prefix + "a-part1/native.csv");
            assertArrayEquals(first, CompositeMotorRecloseTest.read(zip, prefix + "b-part1/native.csv"));
            rows = CompositeMotorRecloseTest.csv(new String(first, StandardCharsets.UTF_8));
        }
        assertEquals(1007, rows.size());
        int event = -1;
        for (int i = 1; i < rows.size(); i++) {
            if (Math.abs(value(rows, i, "time_s") - .05) < 5e-6
                    && Math.abs(value(rows, i - 1, "time_s") - .05) < 5e-6) {
                event = i;
                break;
            }
        }
        assertTrue(event > 1, "Two fault-side observations at one physical time");
        assertTrue(value(rows, event - 1, "V2") > value(rows, event, "V2"));
        double electricalRadiansPerSecond = 2 * Math.PI * 60;
        for (int i = event + 1; i <= event + 3; i++) {
            double h = value(rows, i, "time_s") - value(rows, i - 1, "time_s");
            assertEquals(.0005, h, 5e-6);
            double observed = value(rows, i, "GEN_STATE5") - value(rows, i - 1, "GEN_STATE5");
            double prior = value(rows, i - 2, "GEN_STATE4");
            double left = value(rows, i - 1, "GEN_STATE4");
            double right = value(rows, i, "GEN_STATE4");
            double twoStep = h * electricalRadiansPerSecond * (1.5 * left - .5 * prior);
            double trapezoid = h * electricalRadiansPerSecond * .5 * (left + right);
            assertEquals(twoStep, observed, 1e-8, "Prior-speed recurrence at row " + i);
            assertTrue(Math.abs(observed - trapezoid) > 1e-7,
                    "A current-interval trapezoid cannot explain reference row " + i);
        }
    }

    private static double value(List<Map<String, Double>> rows, int i, String key) {
        return rows.get(i).get(key);
    }
}
