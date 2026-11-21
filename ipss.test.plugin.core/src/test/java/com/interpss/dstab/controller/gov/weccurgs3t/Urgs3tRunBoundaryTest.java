package com.interpss.dstab.controller.gov.weccurgs3t;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Independent run-resume controls separate output publication from reference commands. */
public class Urgs3tRunBoundaryTest {
    private static final Path ARCHIVE=Path.of("testData/private/verification-artifacts/urgs3t-initial-20261102/machine-command-captures.zip");
    private static final String SHA="a2c4fbc8b9f7d38bcc4a562c70d3ca875c52430d3ca63df319c39743ef07fdec";
    private static String sha(byte[] data)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));}
    @BeforeAll static void verifyArchive()throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE),"Retain independent run-boundary captures");
        assertEquals(SHA,sha(Files.readAllBytes(ARCHIVE)));
        try(var z=new ZipFile(ARCHIVE.toFile())) {
            var matrix=new ObjectMapper().readTree(z.getInputStream(z.getEntry("capture-matrix.json")));assertEquals(8,matrix.size());
            for(var entry:matrix) {
                String tag=entry.path("tag").asText();var m=new ObjectMapper().readTree(z.getInputStream(z.getEntry(tag+"/manifest.json")));
                assertEquals("complete",m.path("status").asText());
                for(var file:m.path("files").properties())assertEquals(file.getValue().asText(),sha(z.getInputStream(z.getEntry(tag+"/"+file.getKey())).readAllBytes()));
                if(entry.path("repeat").asText().equals("a"))assertArrayEquals(z.getInputStream(z.getEntry(tag+"/native.csv")).readAllBytes(),z.getInputStream(z.getEntry(tag.substring(0,tag.length()-1)+"b/native.csv")).readAllBytes());
            }
        }
    }
    static Path stage(String tag)throws Exception {
        Path out=Files.createTempDirectory(Path.of("target"),"urgs3t-run-boundary-");
        try(var z=new ZipFile(ARCHIVE.toFile())) {
            for(String name:List.of("manifest.json","network.raw","dynamic.dyr","native.csv")) {
                var e=z.getEntry(tag+"/"+name);assertNotNull(e);assertTrue(e.getSize()>=0&&e.getSize()<16*1024*1024);
                try(var in=z.getInputStream(e)){Files.copy(in,out.resolve(name));}
            }
        }
        return out;
    }
    private static double rate(Map<String,Double> r){return (r.get("PMECH")/(1+r.get("SPEED"))-r.get("P_PU"))/12.8;}
    @ParameterizedTest @ValueSource(booleans={false,true})
    void bothIntegratorsPublishOnlyAtExplicitRunBoundaries(boolean shared)throws Exception {
        Urgs3tReferenceTest.setup();
        for(String profile:List.of("temperature-limits","rate-limits")) {
            Path input=stage(profile+"-h0p0005-t0p5-command-pause0.2-machines-a");
            var reference=Urgs3tReferenceTest.rows(input.resolve("native.csv"));
            var actual=Urgs3tReferenceTest.simulate(input,shared,1e-6);
            int events=0;
            for(int i=1;i<actual.frames().size();i++) {
                var pre=actual.frames().get(i-1);var post=actual.frames().get(i);
                if(!post.get("time_s").equals(pre.get("time_s")))continue;
                events++;assertEquals(post.get("VAR4"),post.get("PMECH"),1e-14,"Explicit resume publishes accepted turbine output");
                for(int state=0;state<5;state++)assertEquals(pre.get("STATE"+state),post.get("STATE"+state),0.,"Resume does not advance controller states");
                for(int state=0;state<6;state++)assertEquals(pre.get("GEN1_STATE"+state),post.get("GEN1_STATE"+state),0.,"Resume does not advance machine states");
                if(Math.abs(post.get("time_s")-.2)<1e-9)assertTrue(Math.abs(post.get("PMECH")-pre.get("PMECH"))>3e-6,"No-command pause activates the refresh");
            }
            assertEquals(4,events);
            var result=new LinkedHashMap<String,Object>();result.put("profile",profile);result.put("method",shared?"staggered":"modified-euler");result.put("diagnostic_only",true);
            result.putAll(Urgs3tCommandBoundaryTest.compare(input,"explicit-resume",actual,reference));
            try(var writer=org.interpss.core.dstab.reference.BenchmarkOutputGuard.newWriter(input.resolve("diagnostic.json"),100000)) {
                new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(writer,result);
            }
        }
    }
    @ParameterizedTest @ValueSource(strings={"temperature-limits","rate-limits"})
    void noCommandPauseRefreshesPowerWithoutAdvancingStates(String profile)throws Exception {
        for(boolean pause:new boolean[]{false,true}) {
            Path input=stage(profile+"-h0p0005-t0p5-command-"+(pause?"pause0.2-":"")+"machines-a");
            var manifest=new ObjectMapper().readTree(input.resolve("manifest.json").toFile());
            assertEquals(pause,manifest.path("settings").path("extra_pause").isNumber());
            assertEquals(6,manifest.path("machine_layouts").path("1").path("NSTATE").asInt());
            assertTrue(Files.readAllLines(input.resolve("dynamic.dyr")).getFirst().contains("0.078 6.4 0.0"),"Undamped synthetic machine with declared inertia");
            var rows=Urgs3tReferenceTest.rows(input.resolve("native.csv"));int events=0,noCommand=0;double wrong=0;
            for(int i=2;i<rows.size()-1;i++) {
                var pre=rows.get(i-1);var post=rows.get(i);
                if(post.get("time_s")<=0||!pre.get("time_s").equals(post.get("time_s")))continue;
                events++;
                for(String key:post.keySet())if(!key.equals("PMECH"))assertEquals(pre.get(key),post.get(key),0.,"Only published mechanical power changes at resume: "+key);
                assertEquals(post.get("VAR4"),post.get("PMECH"),0.,"Resume publishes the current accepted turbine output");
                if(Math.abs(post.get("time_s")-.2)<1e-6){noCommand++;assertTrue(Math.abs(post.get("PMECH")-pre.get("PMECH"))>3e-6);}
                double increment=rows.get(i+1).get("GEN1_STATE4")-post.get("GEN1_STATE4");
                double old=rate(rows.get(i-2)),left=rate(pre),right=rate(post),h=.0005;
                assertEquals(h*(.75*(left+right)-.5*old),increment,1.2e-11,"Fresh left/right mechanical rates with retained older history");
                wrong=Math.max(wrong,Math.abs(increment-h*(1.5*right-.5*old)));
            }
            assertEquals(pause?4:3,events);assertEquals(pause?1:0,noCommand);
            assertTrue(wrong>7e-9,"The unblended two-step increment must be distinguished");
        }
    }
}
