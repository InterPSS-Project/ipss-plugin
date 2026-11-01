package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.GZIPInputStream;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Paired independent small-network captures, with every event-side sample preserved. */
public class HspModelReferenceConformanceTest {
    private static final org.interpss.test.TestConfig CONFIG=org.interpss.test.TestConfig.load(
            "/test-config/ipss.test.plugin.core.json","HspModelReferenceConformanceTest");
    private static final Path ROOT=Path.of(CONFIG.string("hsp.referenceRoot",
            "testData/private/verification-artifacts/2026-10-30-hsp-usage/references"));
    private static JsonObject MANIFEST;
    @BeforeAll static void requireRetainedIndependentCaptures() {
        Assumptions.assumeTrue(Files.isRegularFile(ROOT.resolve("manifest.json")),"Regenerate the private HSP reference captures to enable independent conformance");
        MANIFEST=readManifest();
    }
    private static JsonObject readManifest() {
        try(var reader=Files.newBufferedReader(ROOT.resolve("manifest.json"))) {return JsonParser.parseReader(reader).getAsJsonObject();}
        catch(IOException error){throw new UncheckedIOException(error);}
    }
    private static String sha(byte[] data) throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));}
    private static byte[] referenceBytes(String profile) throws Exception {
        var entry=MANIFEST.getAsJsonObject("profiles").getAsJsonObject(profile);
        byte[] archive=Files.readAllBytes(ROOT.resolve(entry.get("file").getAsString()));
        assertEquals(entry.get("archive_sha256").getAsString(),sha(archive));
        byte[] raw;
        try(var stream=new GZIPInputStream(new ByteArrayInputStream(archive))) {raw=stream.readAllBytes();}
        assertEquals(entry.get("raw_sha256").getAsString(),sha(raw));
        return raw;
    }
    private static List<Map<String,Double>> csv(byte[] bytes) {
        String[] lines=new String(bytes,StandardCharsets.UTF_8).strip().split("\\R");
        String[] names=lines[0].split(",");var rows=new ArrayList<Map<String,Double>>();
        for(int i=1;i<lines.length;i++) {
            String[] fields=lines[i].split(",");assertEquals(names.length,fields.length);
            var row=new LinkedHashMap<String,Double>();
            for(int k=0;k<names.length;k++)if(!names[k].equals("sample_kind")) {
                double value=Double.parseDouble(fields[k]);assertTrue(Double.isFinite(value));row.put(names[k],value);
            }
            rows.add(row);
        }
        return rows;
    }
    @Test void allColdCapturesHaveIntactCompleteChannelsAndEventDuplicates() throws Exception {
        assertEquals("d064ddf3cd340ecf365dadfffacc5125e5ae6b31fd47f06ec50094cce4973fd9",
                sha(Files.readAllBytes(ROOT.resolve("manifest.json"))),
                "Retained reference identity must be requalified explicitly, not learned from changed data");
        for(var item:MANIFEST.getAsJsonObject("profiles").entrySet()) {
            var rows=csv(referenceBytes(item.getKey()));var metadata=item.getValue().getAsJsonObject();
            assertTrue(metadata.get("cold_pair_equal").getAsBoolean());
            assertEquals(metadata.get("rows").getAsInt(),rows.size());
            assertEquals(metadata.getAsJsonArray("channels").size()+1,rows.getFirst().size());
            long duplicates=0;double last=Double.NEGATIVE_INFINITY;
            for(var row:rows) {double t=row.get("time_s");assertTrue(t>=last);if(t==last)duplicates++;last=t;}
            assertTrue(duplicates>=2,item.getKey()+" fault-side duplicates");
        }
    }
    @ParameterizedTest
    @ValueSource(strings={"pss2b","pss2b-remote","hyg3u1-0","hyg3u1-1","hyg3u1-deadband","hyg3u1-deadband-zero",
            "hyg3u1-asymmetric","hyg3u1-partial","wsieg1","wsieg1-curve","wsieg1-dual","wsieg1-unused-lp",
            "tiocr1-0","tiocr1-1","tiocr1-current","cmldblu1","cmldblu1-electronic"})
    void coupledFaultMatchesEveryReferenceBoundarySample(String fixture) throws Exception {
        HspUsageRankedModelTest.run(fixture,false);
        String profile=fixture+(fixture.startsWith("cmldblu1")?"-part0":"");
        var raw=csv(referenceBytes(profile));
        // Startup is a separate initialization convention; retain it in the artifact.
        var reference=raw.stream().filter(r->r.get("time_s")>=0).toList();
        var actual=csv(Files.readAllBytes(Path.of("target/hsp-model-actual-20261030",fixture+"-fault.csv")));
        assertEquals(reference.size(),actual.size(),fixture+" complete grid and event-side count");
        var bounds=new LinkedHashMap<String,Double>();
        boolean composite=fixture.startsWith("cmldblu1");
        bounds.put("V1",composite?.00045:fixture.startsWith("tiocr1-")?.00027:.00021);bounds.put("V2",composite?.00085:.00008);
        bounds.put("P_PU",composite?.005:.00014);bounds.put("Q_PU",composite?.0031:.00185);
        bounds.put("ANGLE_DEG",fixture.equals("cmldblu1-electronic")?.0001:composite?.00043:fixture.equals("pss2b-remote")?.00072:.00025);bounds.put("PMECH",.00007);
        bounds.put("SPEED",fixture.equals("cmldblu1-electronic")?2.95e-6:composite?1.4e-6:1.6e-6);bounds.put("STABILIZER",.0009);
        if(fixture.equals("wsieg1-dual")) {bounds.put("PMECH_LP",.00001);bounds.put("SPEED_LP",1.6e-6);}
        if(fixture.startsWith("hyg3")) {
            String[] names={"Input filter","Integral","Derivative lag","Valve","Gate","Electrical power","Water flow","Double derivative 1","Double derivative 2"};
            for(int state=0;state<names.length;state++)assertEquals(reference.getFirst().get("STATE"+state),
                    actual.getFirst().get("GOV."+names[state]),1e-6,fixture+" initialized state "+state);
        }
        var maxima=new LinkedHashMap<String,Double>();bounds.keySet().forEach(k->maxima.put(k,0.));
        for(int i=0;i<reference.size();i++) {
            var r=reference.get(i);var a=actual.get(i);
            assertEquals(r.get("time_s"),a.get("time_s"),5e-6,"Accumulated single-precision clock; row "+i);
            for(String k:bounds.keySet())maxima.put(k,Math.max(maxima.get(k),Math.abs(a.get(k)-r.get(k))));
        }
        System.out.println("HSP boundary maximum errors "+fixture+": "+maxima);
        for(var bound:bounds.entrySet())assertTrue(maxima.get(bound.getKey())<=bound.getValue(),
                fixture+" "+bound.getKey()+" error "+maxima.get(bound.getKey())+" exceeds "+bound.getValue());
    }
}
