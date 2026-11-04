package org.interpss.dstab.control.gov.psse.hyg3;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipFile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.interpss.core.dstab.reference.BenchmarkOutputGuard;
import org.interpss.test.TestConfig;

/** Selectively stage hash-pinned synthetic captures without copying the complete evidence tree. */
final class Hyg3ReferenceFixture {
    private static final TestConfig CONFIG = TestConfig.load("/test-config/ipss.test.plugin.core.json", "Hyg3StateConformanceTest");
    private final Path staged;
    private final TestConfig config;
    private final Path explicitArchive;
    private final String explicitHash;
    private boolean checked;
    Hyg3ReferenceFixture(Path staged) { this(staged,CONFIG); }
    Hyg3ReferenceFixture(Path staged, TestConfig config) { this.staged = staged; this.config = config; explicitArchive=null; explicitHash=null; }
    Hyg3ReferenceFixture(Path staged,Path archive,String hash) {
        this.staged=staged;config=null;explicitArchive=archive;explicitHash=hash;
    }
    private Path archive() throws Exception {
        Path path = explicitArchive==null?Path.of(config.string("referenceArchive")):explicitArchive;
        assumeTrue(Files.isRegularFile(path), "Retained synthetic HYG3 qualification archive is unavailable");
        if (!checked) {
            var hash = MessageDigest.getInstance("SHA-256");
            try (var stream = Files.newInputStream(path)) {
                byte[] buffer = new byte[65536]; int size;
                while ((size = stream.read(buffer)) >= 0) hash.update(buffer, 0, size);
            }
            assertEquals(explicitHash==null?config.string("archiveSha256"):explicitHash, HexFormat.of().formatHex(hash.digest())); checked = true;
        }
        return path;
    }
    JsonNode matrix() throws Exception {
        try (var zip = new ZipFile(archive().toFile()); var stream = zip.getInputStream(zip.getEntry("capture-matrix.json"))) {
            return new ObjectMapper().readTree(stream);
        }
    }
    JsonNode captureManifest(String tag) throws Exception {
        try (var zip = new ZipFile(archive().toFile())) {
            var entry=zip.getEntry(tag+"/manifest.json");assertNotNull(entry);
            assertTrue(entry.getSize()>0&&entry.getSize()<1024*1024);
            try(var stream=zip.getInputStream(entry)){return new ObjectMapper().readTree(stream);}
        }
    }
    Path stage(String tag) throws Exception {
        Path destination = staged.resolve(tag); assertTrue(destination.normalize().startsWith(staged.normalize()));
        Files.createDirectories(destination);
        try (var zip = new ZipFile(archive().toFile())) {
            for (String name : List.of("manifest.json", "network.raw", "dynamic.dyr", "native.csv")) {
                var entry = zip.getEntry(tag+"/"+name); assertNotNull(entry);
                assertTrue(entry.getSize() >= 0 && entry.getSize() < 16*1024*1024);
                assertTrue(Files.getFileStore(destination).getUsableSpace() >= entry.getSize()+BenchmarkOutputGuard.DEFAULT_MIN_FREE_BYTES);
                try (var stream = zip.getInputStream(entry)) { Files.copy(stream, destination.resolve(name), StandardCopyOption.REPLACE_EXISTING); }
            }
        }
        var manifest = new ObjectMapper().readTree(destination.resolve("manifest.json").toFile());
        assertEquals("complete", manifest.path("status").asText());
        for (String name : List.of("network.raw", "dynamic.dyr", "native.csv")) assertEquals(
                manifest.path("files").path(name).asText(), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(Files.readAllBytes(destination.resolve(name)))));
        assertEquals(List.of(0.,100.,0.,100.), new ObjectMapper().convertValue(manifest.path("load_conversion_percent"), List.class));
        return destination;
    }
}
