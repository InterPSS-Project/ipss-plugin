package org.interpss.core.dstab.reference;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.interpss.test.TestConfig;

/** Bounds generated traces without dropping samples or changing simulation steps. */
public final class BenchmarkOutputGuard {
    public static final long DEFAULT_MIN_FREE_BYTES = 2L * 1024 * 1024 * 1024;
    public static final long DEFAULT_MAX_FILE_BYTES = 512L * 1024 * 1024;
    private static final long CHECK_INTERVAL = 1024 * 1024;
    private static final TestConfig CONFIG = TestConfig.load(
            "/test-config/ipss.test.plugin.core.json", "BenchmarkOutputGuard");

    private BenchmarkOutputGuard() { }

    @FunctionalInterface
    interface FreeSpace { long bytes() throws IOException; }

    static long setting(TestConfig config, String name, long fallback) {
        long value = config.longValue(name, fallback);
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    /** Conservative uncompressed CSV estimate, including clock and event columns. */
    public static long estimateCsvBytes(long samples, int channels) {
        if (samples <= 0 || channels <= 0) throw new IllegalArgumentException("Invalid trace dimensions");
        return Math.multiplyExact(Math.multiplyExact(samples, (long) channels + 2), 24L);
    }

    public static BufferedWriter newWriter(Path path, long estimatedBytes) throws IOException {
        return new BufferedWriter(new OutputStreamWriter(newOutputStream(path, estimatedBytes),
                StandardCharsets.UTF_8));
    }

    /** Place this stream below compression so the limit measures stored bytes. */
    public static OutputStream newOutputStream(Path path, long estimatedBytes) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        return open(path, estimatedBytes,
                setting(CONFIG, "ipss.benchmark.maxFileBytes", DEFAULT_MAX_FILE_BYTES),
                setting(CONFIG, "ipss.benchmark.minFreeBytes", DEFAULT_MIN_FREE_BYTES),
                () -> Files.getFileStore(parent).getUsableSpace());
    }

    static OutputStream open(Path path, long estimate, long maximum, long reserve,
            FreeSpace space) throws IOException {
        if (estimate < 0 || maximum <= 0 || reserve < 0)
            throw new IllegalArgumentException("Invalid output budget");
        if (estimate > maximum)
            throw new IOException("Estimated benchmark trace exceeds the file budget: " + estimate
                    + " > " + maximum + " bytes. Choose output channels/cadence explicitly or increase the reviewed budget.");
        requireSpace(space, reserve, estimate);
        OutputStream delegate = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
        return new OutputStream() {
            private long written;
            private long checkedAt;
            private boolean failed;

            private void check(int length) throws IOException {
                if (failed) throw new IOException("Benchmark output already stopped by its storage budget");
                try {
                    if (length > maximum - written)
                        throw new IOException("Benchmark trace reached its " + maximum + " byte limit");
                    if (written == 0 || written - checkedAt + length >= CHECK_INTERVAL) {
                        requireSpace(space, reserve, length);
                        checkedAt = written;
                    }
                } catch (IOException failure) { failed = true; throw failure; }
            }

            @Override public void write(int value) throws IOException {
                check(1); delegate.write(value); written++;
            }
            @Override public void write(byte[] values, int offset, int length) throws IOException {
                java.util.Objects.checkFromIndexSize(offset, length, values.length);
                check(length); delegate.write(values, offset, length); written += length;
            }
            @Override public void flush() throws IOException { delegate.flush(); }
            @Override public void close() throws IOException { delegate.close(); }
        };
    }

    private static void requireSpace(FreeSpace space, long reserve, long additional) throws IOException {
        long free = space.bytes();
        if (free < reserve || additional > free - reserve)
            throw new IOException("Benchmark output stopped to retain " + reserve
                    + " free bytes; available=" + free + ", required output=" + additional);
    }
}
