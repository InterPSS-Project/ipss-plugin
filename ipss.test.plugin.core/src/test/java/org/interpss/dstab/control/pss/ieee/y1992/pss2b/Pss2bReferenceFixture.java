package org.interpss.dstab.control.pss.ieee.y1992.pss2b;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.interpss.test.TestConfig;

/** Hash-pinned synthetic references shared by replay and coupled qualification. */
final class Pss2bReferenceFixture {
    private static final TestConfig CONFIG = TestConfig.load("/test-config/ipss.test.plugin.core.json", "Pss2bReferenceReplayTest");
    private final Path staged;
    private boolean checked;

    Pss2bReferenceFixture(Path staged) { this.staged = staged; }

    Path archive() throws Exception {
        Path path = Path.of(CONFIG.string("referenceArchive"));
        assumeTrue(Files.isRegularFile(path), "Retained private PSS2B qualification archive is unavailable");
        if (!checked) {
            var hash = java.security.MessageDigest.getInstance("SHA-256");
            try (var stream = Files.newInputStream(path)) {
                byte[] buffer = new byte[65536]; int size;
                while ((size = stream.read(buffer)) >= 0) hash.update(buffer, 0, size);
            }
            assertEquals(CONFIG.string("archiveSha256"), java.util.HexFormat.of().formatHex(hash.digest()));
            checked = true;
        }
        return path;
    }

    com.fasterxml.jackson.databind.JsonNode matrix() throws Exception {
        try (var zip = new java.util.zip.ZipFile(archive().toFile());
                var stream = zip.getInputStream(zip.getEntry("capture-matrix.json"))) {
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(stream);
        }
    }

    /** The retained runnable probe specifies an all-zero I/Z conversion percentage vector. */
    void requireConstantPowerLoads(String profile) throws Exception {
        try (var zip = new java.util.zip.ZipFile(archive().toFile());
                var stream = zip.getInputStream(zip.getEntry(profile + "/genrou_native_probe.py"))) {
            String source = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(source.contains("psspy.conl(0, 1, stage, [0, 0], [0.] * 4)"),
                    "Requalify the load-conversion policy if the retained probe changes");
        }
    }

    Path stage(String profile) throws Exception {
        Path root = staged.resolve(profile); Files.createDirectories(root);
        assertTrue(root.normalize().startsWith(staged.normalize()));
        try (var zip = new java.util.zip.ZipFile(archive().toFile())) {
            for (String name : List.of("manifest.json", "network.raw", "dynamic.dyr", "native.csv")) {
                var entry = zip.getEntry(profile + "/" + name); assertNotNull(entry, profile + "/" + name);
                assertTrue(entry.getSize() >= 0 && entry.getSize() < 16 * 1024 * 1024, "Bound staged fixture size");
                assertTrue(Files.getFileStore(root).getUsableSpace() >= entry.getSize()
                        + org.interpss.core.dstab.reference.BenchmarkOutputGuard.DEFAULT_MIN_FREE_BYTES,
                        "Staging retains the two-GiB free-space reserve");
                try (var stream = zip.getInputStream(entry)) {
                    Files.copy(stream, root.resolve(name), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        return root;
    }
}
