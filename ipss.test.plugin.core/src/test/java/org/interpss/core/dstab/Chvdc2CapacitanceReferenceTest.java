package org.interpss.core.dstab;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import com.fasterxml.jackson.databind.*;
import org.interpss.dstab.hvdc.Chvdc2Parameters;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Independent preparation-mode checks. Rejected reference trajectories cannot qualify dynamics. */
public class Chvdc2CapacitanceReferenceTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final Path ARCHIVE=Path.of("testData/private/verification-artifacts/chvdc2-capacitor-20261102/captures.zip");
    private static final Map<String,JsonNode> PROFILES=new TreeMap<>();
    private static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    @BeforeAll static void setup()throws Exception {
        Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE),"Retain independent capacitor controls");
        assertEquals("03701c023b4d419217368086599ecbe234d767d7993a45b14d0ba0a4965131e0",sha(Files.readAllBytes(ARCHIVE)));PROFILES.clear();
        try(var z=new ZipFile(ARCHIVE.toFile())) {
            var manifest=JSON.readTree(z.getInputStream(z.getEntry("manifest.json")));
            for(var f:manifest.path("files").properties())assertEquals(f.getValue().asText(),sha(z.getInputStream(z.getEntry(f.getKey())).readAllBytes()));
            for(var p:manifest.path("profiles"))if(p.path("repeat").asText().equals("a")) {
                String tag=p.path("tag").asText();var m=JSON.readTree(z.getInputStream(z.getEntry(tag+"/manifest.json")));
                assertEquals(m.path("entered_constants"),m.path("initialized_constants"));
                assertArrayEquals(z.getInputStream(z.getEntry(tag+"/native.csv")).readAllBytes(),z.getInputStream(z.getEntry(tag.substring(0,tag.length()-1)+"b/native.csv")).readAllBytes());
                PROFILES.put(tag,m);
            }
        }
        assertEquals(4,PROFILES.size());
    }
    static Stream<String> profiles(){return PROFILES.keySet().stream();}
    @ParameterizedTest @MethodSource("profiles")
    void preparedModeMatchesIndependentInitializationWithoutAcceptingRejectedDynamics(String tag)throws Exception {
        var m=PROFILES.get(tag);double[] constants=JSON.convertValue(m.path("entered_constants"),double[].class);
        List<Map<String,Double>> rows;JsonNode equipment;byte[] capture;
        try(var z=new ZipFile(ARCHIVE.toFile())) {
            // The rejected control explicitly preserves nonfinite samples in its CSV.
            capture=z.getInputStream(z.getEntry(tag+"/native.csv")).readAllBytes();
            String csv=new String(capture,java.nio.charset.StandardCharsets.UTF_8);
            rows=readIncludingNonfinite(csv);
            equipment=JSON.readTree(z.getInputStream(z.getEntry(tag+"/solved-equipment.json")));
        }
        var p=new Chvdc2Parameters("link",Chvdc2Parameters.Dialect.CHVDC2U1,new int[]{0,0,1},constants);
        var line=p.prepareDcLine(5,2,equipment.path("7").path("XC").asDouble(),equipment.path("9").path("XC").asDouble(),60,m.path("settings").path("step").asDouble());
        var first=rows.getFirst();boolean observedCapacitor=first.get("STATE16")!=0;
        assertEquals(observedCapacitor?3:1,line.staggeredStateCount());
        assertEquals(observedCapacitor?constants[47]*1e-6:0,line.capacitanceFarads(),0);assertArrayEquals(constants,p.copySourceConstants());
        line.initialize(first.get("VAR8")*1000+5*first.get("STATE15"),first.get("VAR8")*1000);
        assertEquals(first.get("STATE15"),line.rectifierAmperes(),1e-8);
        if(observedCapacitor)assertEquals(first.get("STATE16"),line.midpointVolts(),.1);
        if(m.path("status").asText().equals("rejected-nonfinite")) {
            assertTrue(observedCapacitor);assertFalse(m.path("finite_measurements").asBoolean());assertTrue(m.path("nonfinite_measurements").asInt()>0);
            assertTrue(rows.stream().flatMap(row->row.values().stream()).anyMatch(v->!Double.isFinite(v)));
            return; // Only the preparation decision, never this failed trajectory, is accepted.
        }
        assertEquals("complete",m.path("status").asText());assertTrue(m.path("initialization_and_network_valid").asBoolean());
        assertTrue(rows.stream().flatMap(row->row.values().stream()).allMatch(Double::isFinite));
        if(!observedCapacitor){
            // Complete zero-capacitance control from the earlier immutable controller archive.
            assertEquals("735013e7b2b6c8b56befbacb384b50455badd840efcf0cc64a8a81ec84597827",sha(capture));
            for(var row:rows){assertEquals(0,row.get("STATE16"));assertEquals(row.get("STATE15"),row.get("STATE17"));}
        }
        else {
            assertTrue(rows.stream().mapToDouble(row->Math.abs(row.get("STATE15")-row.get("STATE17"))).max().orElseThrow()>1);
            assertTrue(rows.stream().mapToDouble(row->Math.abs(row.get("STATE16")-first.get("STATE16"))).max().orElseThrow()>1);
        }
    }
    private static List<Map<String,Double>> readIncludingNonfinite(String csv) {
        var lines=csv.lines().toList();String[] keys=lines.getFirst().split(",");var rows=new ArrayList<Map<String,Double>>();
        for(int i=1;i<lines.size();i++) {
            String[] values=lines.get(i).split(",");assertEquals(keys.length,values.length);var row=new LinkedHashMap<String,Double>();
            for(int j=0;j<keys.length;j++)row.put(keys[j],switch(values[j].toLowerCase(Locale.ROOT)) {
                case "nan" -> Double.NaN;case "inf" -> Double.POSITIVE_INFINITY;case "-inf" -> Double.NEGATIVE_INFINITY;default -> Double.parseDouble(values[j]);
            });rows.add(row);
        }
        return rows;
    }
}
