package org.interpss.core.dstab.dynLoad;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import com.google.gson.*;
import org.interpss.IpssCorePlugin;
import org.interpss.test.TestConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Complete coupled electronic dropout, ramp and recovery under both integration methods. */
public class ElectronicFullRecoverySmibTest {
    private static final TestConfig CONFIG = TestConfig.load("/test-config/ipss.test.plugin.core.json", "ElectronicFullRecoverySmibTest");
    private record Reference(String dyr, List<Map<String, Double>> rows) { }
    private static final Map<String, Reference> REFERENCES = new HashMap<>();
    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    private static List<Map<String, Double>> rows(byte[] bytes) {
        String[] lines = new String(bytes, StandardCharsets.UTF_8).strip().split("\\R"), keys = lines[0].split(",");
        var rows = new ArrayList<Map<String, Double>>();
        for (int i = 1; i < lines.length; i++) {
            String[] values = lines[i].split(","); assertEquals(keys.length, values.length);
            var row = new LinkedHashMap<String, Double>();
            for (int j = 0; j < keys.length; j++) {
                double value = Double.parseDouble(values[j]); assertTrue(Double.isFinite(value)); row.put(keys[j], value);
            }
            rows.add(row);
        }
        return rows;
    }
    @BeforeAll static void setup() throws Exception {
        IpssCorePlugin.init(); IpssCorePlugin.configureSparseSolver("java-klu");
        Path archive = Path.of(CONFIG.string("reference.archive", "testData/private/verification-artifacts/cmldblu1-electronic-full-recovery-20261101/captures.zip"));
        Assumptions.assumeTrue(Files.isRegularFile(archive), "Retain independent electronic-load captures");
        assertEquals("e9127105c27bd2f060155f3df301009b96e3c106fcb8d876c6d74b653edcc83d", sha(Files.readAllBytes(archive)));
        try (var z = new ZipFile(archive.toFile())) {
            var manifest = JsonParser.parseString(new String(z.getInputStream(z.getEntry("references.json")).readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            for (var e : manifest.getAsJsonObject("files").entrySet())
                assertEquals(e.getValue().getAsString(), sha(z.getInputStream(z.getEntry(e.getKey())).readAllBytes()));
            for (var e : manifest.getAsJsonObject("profiles").entrySet()) {
                var p = e.getValue().getAsJsonObject();
                var first = rows(z.getInputStream(z.getEntry(p.get("entry").getAsString())).readAllBytes());
                assertEquals(first, rows(z.getInputStream(z.getEntry(p.get("repeat_entry").getAsString())).readAllBytes()));
                assertEquals(2, first.stream().filter(row -> row.get("time_s") < 0).count());
                REFERENCES.put(e.getKey(), new Reference(new String(z.getInputStream(z.getEntry(p.get("dyr").getAsString())).readAllBytes(), StandardCharsets.UTF_8),
                        first.stream().filter(row -> row.get("time_s") >= 0).toList()));
            }
        }
        assertEquals(3, REFERENCES.size());
    }
    @AfterAll static void clear() { REFERENCES.clear(); }
    @ParameterizedTest @CsvSource({".06,false", ".06,true", ".01,false", ".01,true", ".1,false", ".1,true"})
    void repeatedDropoutAndRecoveryMatchIndependentBoundaries(String impedance, boolean staggered) throws Exception {
        var ref = REFERENCES.get(impedance); var out = Files.createTempDirectory(Path.of("target"), "electronic-full-recovery-");
        var input = out.resolve("dynamic.dyr"); Files.writeString(input, ref.dyr());
        double x = Double.parseDouble(impedance);
        var actual = Cmldblu1SharedRecording.simulateStaticControl(input, staggered, .0005, x, x);
        actual.write(out.resolve("actual.csv.gz"));
        assertEquals(1, actual.model.getData().value(132));
        assertEquals(1, actual.model.getFel()); assertEquals(ref.rows().size(), actual.frames.size());
        double[] clock = new double[1001]; float time = 0;
        for (int i = 1; i < clock.length; i++) { time += .0005f; clock[i] = time; }
        int duplicates = 0;
        for (int i = 0; i < actual.frames.size(); i++) {
            var frame = actual.frames.get(i); assertEquals(clock[(int) Math.round(frame.time() / .0005)], ref.rows().get(i).get("time_s"), 6e-13);
            if (i > 0 && frame.time() == actual.frames.get(i - 1).time()) duplicates++;
        }
        assertEquals(4, duplicates);
        for (String[] mapping : new String[][]{{"V1","V1"},{"V2","V2"},{"V3","V3"},
                {"P_PU","P_PU"},{"Q_PU","Q_PU"},{"SPEED","SPEED"},{"ANGLE_DEG","ANGLE_DEG"},
                {"VAR27","ELECTRONIC.P"},{"VAR28","ELECTRONIC.Q"}}) {
            double sum = 0, lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < actual.frames.size(); i++) {
                double expected = ref.rows().get(i).get(mapping[0]);
                double error = actual.frames.get(i).values().get(mapping[1]) - expected;
                assertTrue(Double.isFinite(error)); sum += error * error; lo = Math.min(lo, expected); hi = Math.max(hi, expected);
            }
            double rmse = Math.sqrt(sum / actual.frames.size());
            assertTrue(mapping[0].equals("ANGLE_DEG") ? rmse < .0002 : hi > lo && rmse / (hi - lo) < .001,
                    mapping[0] + " RMSE=" + rmse + " range=" + (hi - lo));
        }
    }
}
