package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** A zero-time API continuation does not identify internal protection callbacks. */
class Cdc6tCallBoundaryReferenceTest {
    @Test void unchangedCompleteOutputAndSnapshots()throws Exception {
        Path archive=Path.of("testData/private/verification-artifacts/cdc6t-call-boundary-20261104/captures.zip");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isRegularFile(archive));
        assertEquals("137a2a509fb94fadeda7c9ffd96abbd15c1a0c3d01549bf09e8fd39fbe2c50eb",hash(Files.readAllBytes(archive)));
        var json=new ObjectMapper();
        try(var zip=new ZipFile(archive.toFile())) {
            byte[] first=read(zip,"a/native.csv");assertArrayEquals(first,read(zip,"b/native.csv"));
            // Exact hash of the previously retained uninterrupted coarse-offset run.
            assertEquals("affbddd0a77117940a8159e6b22d153dcdce53d827e4e4b70925c80cc44cdcf9",hash(first));
            assertEquals(1006,Tiocr1ResetReferenceTest.csv(first).size());
            for(String repeat:List.of("a","b")) {
                var manifest=json.readTree(read(zip,repeat+"/manifest.json"));assertEquals("complete",manifest.get("status").asText());
                var snapshots=json.readTree(read(zip,repeat+"/snapshots.json"));
                com.fasterxml.jackson.databind.JsonNode before=null,after=null;
                for(var snapshot:snapshots) {
                    if(snapshot.get("stage").asText().equals("first-cleared"))before=snapshot;
                    if(snapshot.get("stage").asText().equals("post-clear-zero-time-continuation"))after=snapshot;
                }
                assertNotNull(before);assertNotNull(after);
                for(String coordinate:List.of("time","step","states","icons","variables"))assertEquals(before.get(coordinate),after.get(coordinate),coordinate);
                assertEquals(3,before.get("states").size());assertEquals(18,before.get("variables").size());assertEquals(3,before.get("icons").size());
                assertTrue(new String(read(zip,repeat+"/cdc6t_call_boundary_reference_probe.py"),java.nio.charset.StandardCharsets.UTF_8).contains("assert before==after"));
            }
        }
    }
    private static byte[] read(ZipFile zip,String name)throws Exception {return zip.getInputStream(zip.getEntry(name)).readAllBytes();}
    private static String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}
}
