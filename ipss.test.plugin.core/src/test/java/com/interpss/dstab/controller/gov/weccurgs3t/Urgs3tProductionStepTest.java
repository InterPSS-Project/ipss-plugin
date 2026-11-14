package com.interpss.dstab.controller.gov.weccurgs3t;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

/** Ten-second independent captures retain all channels at production and refined intervals. */
public class Urgs3tProductionStepTest {
    private static final Path ARCHIVE=Path.of("testData/private/verification-artifacts/urgs3t-initial-20261102/production-step-captures.zip");
    private static final String SHA="d685c38c70655969f38851008c47e43c5f214266443cda808ecdd8dc86e8a047";
    private static final SortedSet<String> TAGS=new TreeSet<>();
    private static String sha(byte[] data)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));}
    @BeforeAll static void setup()throws Exception {
        Urgs3tReferenceTest.setup();
        Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE),"Retain independent ten-second governor captures");
        assertEquals(SHA,sha(Files.readAllBytes(ARCHIVE)));TAGS.clear();
        try(var z=new ZipFile(ARCHIVE.toFile())) {
            var matrix=new ObjectMapper().readTree(z.getInputStream(z.getEntry("capture-matrix.json")));assertEquals(20,matrix.size());
            for(var entry:matrix) {
                String tag=entry.path("tag").asText();
                var manifest=new ObjectMapper().readTree(z.getInputStream(z.getEntry(tag+"/manifest.json")));
                assertEquals("complete",manifest.path("status").asText());assertEquals(10.,manifest.path("settings").path("duration").asDouble(),0.);
                assertEquals(manifest.path("entered_constants"),manifest.path("initialized_constants"),"Entered constants remain unchanged");
                for(var file:manifest.path("files").properties())assertEquals(file.getValue().asText(),sha(z.getInputStream(z.getEntry(tag+"/"+file.getKey())).readAllBytes()));
                if(entry.path("repeat").asText().equals("a")) {
                    assertArrayEquals(z.getInputStream(z.getEntry(tag+"/native.csv")).readAllBytes(),z.getInputStream(z.getEntry(tag.substring(0,tag.length()-1)+"b/native.csv")).readAllBytes());
                    TAGS.add(tag);
                }
            }
        }
        assertEquals(10,TAGS.size());
    }
    static Stream<Arguments> profiles(){return TAGS.stream().flatMap(tag->Stream.of(false,true).map(shared->Arguments.of(tag,shared)));}
    @ParameterizedTest @MethodSource("profiles")
    void tenSecondStateAndBoundaryGates(String tag,boolean shared)throws Exception {
        Path out=Files.createTempDirectory(Path.of("target"),"urgs3t-production-comparison-");
        try(var z=new ZipFile(ARCHIVE.toFile())) {
            for(String name:List.of("manifest.json","network.raw","dynamic.dyr","native.csv")) {
                var e=z.getEntry(tag+"/"+name);assertNotNull(e);assertTrue(e.getSize()>=0&&e.getSize()<16*1024*1024);
                try(var in=z.getInputStream(e)){Files.copy(in,out.resolve(name));}
            }
        }
        var settings=new ObjectMapper().readTree(out.resolve("manifest.json").toFile()).path("settings");
        double h=settings.path("step").asDouble();int intervals=Urgs3tReferenceTest.referenceIntervals(out.resolve("native.csv"),h);
        String last=Files.readAllLines(out.resolve("native.csv")).getLast();
        try(var writer=org.interpss.core.dstab.reference.BenchmarkOutputGuard.newWriter(out.resolve("intervals.json"),10000)) {
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(writer,Map.of("requested_duration_s",settings.path("duration").asDouble(),
                    "matched_intervals",intervals,"integration_duration_s",intervals*h,"serialized_reference_end_s",Double.parseDouble(last.substring(0,last.indexOf(',')))));
        }
        Urgs3tReferenceTest.compareCompleteTrajectory(out,tag,shared);
    }
}
