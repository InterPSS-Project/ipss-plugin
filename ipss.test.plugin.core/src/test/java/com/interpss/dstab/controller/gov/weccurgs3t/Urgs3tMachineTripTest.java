package com.interpss.dstab.controller.gov.weccurgs3t;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.interpss.core.dstab.reference.BenchmarkOutputGuard;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

/** Independent machine trips separate physical inactivity from stale allocated-state drift. */
public class Urgs3tMachineTripTest {
    private static final Path ARCHIVE=Path.of("testData/private/verification-artifacts/urgs3t-initial-20261102/machine-trip-captures.zip");
    private static final String SHA="234757579e724636ba4d24514a98902db21ffe7bffcd00c20ffde34f9dfc09d9";
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final Map<String,JsonNode> PROFILES=new TreeMap<>();
    private static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    @BeforeAll static void setup()throws Exception {
        Urgs3tReferenceTest.setup();Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE),"Retain independent machine-trip references");
        assertEquals(SHA,sha(Files.readAllBytes(ARCHIVE)));PROFILES.clear();
        try(var z=new ZipFile(ARCHIVE.toFile())) {
            var matrix=JSON.readTree(z.getInputStream(z.getEntry("capture-matrix.json")));assertEquals(14,matrix.size());
            for(var entry:matrix) {
                String tag=entry.path("tag").asText();var m=JSON.readTree(z.getInputStream(z.getEntry(tag+"/manifest.json")));
                assertEquals("complete",m.path("status").asText());assertEquals(m.path("entered_constants"),m.path("initialized_constants"));
                assertTrue(entry.path("trip_bus").asInt()==1||entry.path("trip_bus").asInt()==2);
                for(var file:m.path("files").properties())assertEquals(file.getValue().asText(),sha(z.getInputStream(z.getEntry(tag+"/"+file.getKey())).readAllBytes()));
                if(entry.path("repeat").asText().equals("a")) {
                    assertArrayEquals(z.getInputStream(z.getEntry(tag+"/native.csv")).readAllBytes(),z.getInputStream(z.getEntry(tag.substring(0,tag.length()-1)+"b/native.csv")).readAllBytes());
                    PROFILES.put(tag,entry);
                }
            }
        }
        assertEquals(7,PROFILES.size());
    }
    private static Path stage(String tag)throws Exception {
        Path out=Files.createTempDirectory(Path.of("target"),"urgs3t-machine-trip-");
        try(var z=new ZipFile(ARCHIVE.toFile())) {
            for(String name:List.of("manifest.json","network.raw","dynamic.dyr","native.csv")) {
                var e=z.getEntry(tag+"/"+name);assertNotNull(e);assertTrue(e.getSize()>=0&&e.getSize()<16*1024*1024);
                try(var in=z.getInputStream(e)){Files.copy(in,out.resolve(name));}
            }
        }
        return out;
    }
    static Stream<Arguments> profiles(){return PROFILES.keySet().stream().flatMap(tag->Stream.of(false,true).map(shared->Arguments.of(tag,shared)));}
    @Test void independentInactiveOutputsHoldWhileAllocatedStatesDrift()throws Exception {
        int checked=0;
        for(var entry:PROFILES.entrySet()) {
            if(entry.getValue().path("trip_bus").asInt()!=1)continue;
            var rows=Urgs3tReferenceTest.rows(stage(entry.getKey()).resolve("native.csv"));
            var inactive=rows.stream().filter(r->r.get("time_s")>=0&&r.get("EFD")==0.).toList();assertTrue(inactive.size()>600);
            for(var row:inactive) {
                for(String k:List.of("P_PU","Q_PU","PMECH","EFD","SPEED","ANGLE_DEG"))assertEquals(0.,row.get(k),0.);
                for(int i=0;i<5;i++)assertEquals(inactive.getFirst().get("VAR"+i),row.get("VAR"+i),0.);
            }
            assertTrue(inactive.getLast().get("STATE0")-inactive.getFirst().get("STATE0")>.2);
            assertTrue(inactive.getLast().get("STATE1")-inactive.getFirst().get("STATE1")>.07);
            double h=entry.getValue().path("step").asDouble();
            var first=inactive.get(3);var last=inactive.getLast();
            double increment=(last.get("STATE0")-first.get("STATE0"))/(inactive.size()-4);
            for(int i=4;i<inactive.size();i++)assertEquals(increment,inactive.get(i).get("STATE0")-inactive.get(i-1).get("STATE0"),1.3e-7,"Held allocated rate, not a live governor feedback update; h="+h);
            checked++;
        }
        assertEquals(6,checked);
    }
    @ParameterizedTest @MethodSource("profiles")
    void physicalTripFreezesOnlyTheDisconnectedHost(String tag,boolean shared)throws Exception {
        Path out=stage(tag);var run=Urgs3tReferenceTest.simulate(out,shared,1e-6);int bus=PROFILES.get(tag).path("trip_bus").asInt();
        var frames=run.frames();var after=frames.stream().filter(r->r.get("time_s")>.15+1e-10).toList();assertFalse(after.isEmpty());
        int sides=0;for(int i=1;i<frames.size();i++)if(Math.abs(frames.get(i).get("time_s")-.15)<1e-9&&frames.get(i).get("time_s").equals(frames.get(i-1).get("time_s")))sides++;
        assertEquals(1,sides,"Preserve both trip event sides");
        var g=run.governor();
        if(bus==1) {
            assertFalse(g.getMachine().isActive());assertNotNull(run.recording().historiesAtTrip);
            assertEquals(run.recording().historiesAtTrip,g.getIntegrationHistories());assertEquals(run.recording().evaluationsAtTrip,g.getStaggeredEvaluationCount());
            for(var frame:after) {
                assertEquals(0.,frame.get("HOST_ACTIVE"),0.);
                for(String k:List.of("P_PU","Q_PU","PMECH","EFD","SPEED","ANGLE_DEG"))assertEquals(0.,frame.get(k),0.);
                for(int i=0;i<5;i++){assertEquals(after.getFirst().get("STATE"+i),frame.get("STATE"+i),0.);assertEquals(after.getFirst().get("VAR"+i),frame.get("VAR"+i),0.);}
                assertTrue(frame.get("P_PU_2")>.9,"The remaining source supplies the connected load");
            }
        } else {
            assertTrue(g.getMachine().isActive());assertNull(run.recording().historiesAtTrip);
            assertTrue(Math.abs(after.getLast().get("STATE0")-after.getFirst().get("STATE0"))>.01,"Surviving governor must continue responding");
            for(var frame:after){assertEquals(1.,frame.get("HOST_ACTIVE"),0.);assertEquals(0.,frame.get("P_PU_2"),0.);assertEquals(0.,frame.get("PMECH_2"),0.);}
        }
        try(var writer=BenchmarkOutputGuard.newWriter(out.resolve("lifecycle.json"),30000)) {
            JSON.writerWithDefaultPrettyPrinter().writeValue(writer,Map.of("profile",tag,"method",shared?"staggered":"modified-euler","trip_bus",bus,"physical_lifecycle_passed",true,"host_state_updates_after_trip",bus==1?"frozen":"active","final_histories",g.getIntegrationHistories(),"shared_evaluations",g.getStaggeredEvaluationCount(),"trajectory_acceptance_is_separate",true));
        }
    }
    @ParameterizedTest @MethodSource("profiles")
    void completeTripTrajectoriesRetainActiveStateAndBoundaryGates(String tag,boolean shared)throws Exception {
        Urgs3tReferenceTest.compareCompleteTrajectory(stage(tag),tag,shared);
    }
}
