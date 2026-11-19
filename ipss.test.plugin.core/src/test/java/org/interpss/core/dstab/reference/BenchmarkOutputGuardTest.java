package org.interpss.core.dstab.reference;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.interpss.test.TestConfig;
import java.util.Map;

public class BenchmarkOutputGuardTest {
    @TempDir Path directory;

    @Test void budgetsComeFromImmutableJsonFixturesAndRetainLongPrecision() {
        var config = TestConfig.load("/test-config/ipss.test.plugin.core.json", "BenchmarkOutputGuard");
        assertEquals(BenchmarkOutputGuard.DEFAULT_MIN_FREE_BYTES,
                BenchmarkOutputGuard.setting(config, "ipss.benchmark.minFreeBytes", 1));
        var reviewed = config.withOverrides(Map.of("ipss.benchmark.minFreeBytes", 4L * 1024 * 1024 * 1024));
        assertEquals(4L * 1024 * 1024 * 1024,
                BenchmarkOutputGuard.setting(reviewed, "ipss.benchmark.minFreeBytes", 1));
        assertEquals(BenchmarkOutputGuard.DEFAULT_MIN_FREE_BYTES,
                BenchmarkOutputGuard.setting(config, "ipss.benchmark.minFreeBytes", 1));
    }

    @Test void invalidJsonBudgetCannotDisableTheGuard() {
        var config = TestConfig.load("/test-config/ipss.test.plugin.core.json", "BenchmarkOutputGuard");
        for(Object value : new Object[]{0,-1,"bad","1.5"}) {
            var invalid = config.withOverrides(Map.of("ipss.benchmark.maxFileBytes", value));
            assertThrows(IllegalArgumentException.class,
                    () -> BenchmarkOutputGuard.setting(invalid, "ipss.benchmark.maxFileBytes", 1));
        }
    }

    @Test void rejectsOversizedEstimateBeforeCreatingAFile() {
        Path path = directory.resolve("trace.csv");
        assertThrows(IOException.class, () -> BenchmarkOutputGuard.open(path, 101, 100, 10, () -> 1000));
        assertFalse(Files.exists(path));
    }

    @Test void retainsDiskReserveBeforeCreatingAFile() {
        Path path = directory.resolve("trace.csv");
        assertThrows(IOException.class, () -> BenchmarkOutputGuard.open(path, 50, 100, 100, () -> 149));
        assertFalse(Files.exists(path));
    }

    @Test void capsActualBytesAndRetainsTheCompletedPrefix() throws Exception {
        Path path = directory.resolve("trace.csv");
        try (var out = BenchmarkOutputGuard.open(path, 0, 4, 0, () -> 1000)) {
            out.write(new byte[]{1,2,3,4});
            assertThrows(IOException.class, () -> out.write(5));
            assertThrows(IOException.class, () -> out.write(6));
        }
        assertArrayEquals(new byte[]{1,2,3,4}, Files.readAllBytes(path));
    }

    @Test void noticesSpaceConsumedByAnotherProcessDuringAWrite() throws Exception {
        AtomicLong free = new AtomicLong(10_000_000);
        Path path = directory.resolve("trace.csv");
        try (var out = BenchmarkOutputGuard.open(path, 0, 5_000_000, 1_000_000, free::get)) {
            out.write(new byte[1024 * 1024]);
            free.set(999_999);
            assertThrows(IOException.class, () -> out.write(new byte[1024 * 1024]));
        }
        assertEquals(1024 * 1024, Files.size(path));
    }

    @Test void neverOverwritesAnExistingReferenceOrTrace() throws Exception {
        Path path = directory.resolve("trace.csv"); Files.writeString(path, "keep");
        assertThrows(IOException.class, () -> BenchmarkOutputGuard.open(path, 0, 100, 0, () -> 1000));
        assertEquals("keep", Files.readString(path));
    }

    @Test void compressedOutputPreservesEveryByteAndDuplicateTimestamp() throws Exception {
        byte[] samples = "time,value\n0,1\n0.1,1\n0.1,0\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Path path = directory.resolve("trace.csv.gz");
        try (var out = new GZIPOutputStream(BenchmarkOutputGuard.open(path, 0, 1024, 0, () -> 10000))) {
            out.write(samples);
        }
        try (var input = new GZIPInputStream(Files.newInputStream(path))) {
            assertArrayEquals(samples, input.readAllBytes());
        }
    }

    @Test void estimatesRejectTheOldHalfMillionSampleExport() {
        assertTrue(BenchmarkOutputGuard.estimateCsvBytes(500005, 200)
                > BenchmarkOutputGuard.DEFAULT_MAX_FILE_BYTES);
        assertThrows(ArithmeticException.class, () -> BenchmarkOutputGuard.estimateCsvBytes(Long.MAX_VALUE, 2));
    }
}
