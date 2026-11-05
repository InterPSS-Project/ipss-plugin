package org.interpss.dstab.control.gov.wecc.wsieg1;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.interpss.core.dstab.reference.BenchmarkOutputGuard;
import org.interpss.test.TestConfig;

/** Selective staging of immutable, hash-pinned synthetic model references. */
final class Wsieg1ReferenceFixture {
    static final TestConfig CONFIG=TestConfig.load("/test-config/ipss.test.plugin.core.json","Wsieg1StateConformanceTest");
    private final Path staged;private final TestConfig config;private boolean checked;
    Wsieg1ReferenceFixture(Path staged){this(staged,CONFIG);}
    Wsieg1ReferenceFixture(Path staged,TestConfig config){this.staged=staged;this.config=config;}
    static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    Path archive()throws Exception {
        Path path=Path.of(config.string("referenceArchive"));assumeTrue(Files.isRegularFile(path),"Retained synthetic governor captures are unavailable");
        if(!checked){assertEquals(config.string("archiveSha256"),sha(Files.readAllBytes(path)));checked=true;}return path;
    }
    List<JsonNode> matrix()throws Exception {
        try(var zip=new ZipFile(archive().toFile());var stream=zip.getInputStream(zip.getEntry("capture-matrix.json"))) {
            var result=new ArrayList<JsonNode>();new ObjectMapper().readTree(stream).forEach(result::add);return result;
        }
    }
    Path stage(String tag)throws Exception {
        return stage(tag,"complete");
    }
    /** Rejected captures can only be opened by an explicit diagnostic-status check. */
    Path stage(String tag,String expectedStatus)throws Exception {
        assertTrue(expectedStatus.equals("complete")||expectedStatus.equals("rejected-quality"));
        Path destination=staged.resolve(tag).normalize();assertTrue(destination.startsWith(staged.normalize()));Files.createDirectories(destination);
        try(var zip=new ZipFile(archive().toFile())) {
            for(String name:List.of("manifest.json","network.raw","dynamic.dyr","native.csv")) {
                var entry=zip.getEntry(tag+"/"+name);assertNotNull(entry);assertTrue(entry.getSize()>=0&&entry.getSize()<16*1024*1024);
                assertTrue(Files.getFileStore(destination).getUsableSpace()>entry.getSize()+BenchmarkOutputGuard.DEFAULT_MIN_FREE_BYTES);
                try(var stream=zip.getInputStream(entry)){Files.copy(stream,destination.resolve(name),StandardCopyOption.REPLACE_EXISTING);}
            }
        }
        var manifest=new ObjectMapper().readTree(destination.resolve("manifest.json").toFile());assertEquals(expectedStatus,manifest.path("status").asText());
        for(String name:List.of("network.raw","dynamic.dyr","native.csv"))assertEquals(manifest.path("files").path(name).asText(),sha(Files.readAllBytes(destination.resolve(name))));
        assertEquals(List.of(0.,100.,0.,100.),new ObjectMapper().convertValue(manifest.path("load_conversion_percent"),List.class));
        return destination;
    }
}
