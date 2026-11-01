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
    private record Reference(String dyr, List<Map<String, Double>> rows, double faultX, boolean distributed, double electronicFraction) { }
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
        load(archive, false);
        Path distribution = Path.of(CONFIG.string("distribution.archive", "testData/private/verification-artifacts/cmldblu1-electronic-distribution-20261101/captures.zip"));
        // Missing optional evidence must not suppress the previously available controls.
        if (Files.isRegularFile(distribution)) {
            assertEquals("e8a6b99b00d4fd63465475fb134a00a83726ae940b1404974ccfb0775181c372", sha(Files.readAllBytes(distribution)));
            load(distribution, true);
            assertEquals(5, REFERENCES.size());
        } else assertEquals(3, REFERENCES.size());
        Path mixed = Path.of(CONFIG.string("mixed.archive", "testData/private/verification-artifacts/cmldblu1-static-electronic-mix-20261101/captures.zip"));
        if (Files.isRegularFile(mixed)) {
            assertEquals("b96d98e706628220ea5d598feb6c3fe5afaa0d0c58d515c755c94a7ed7e38d16", sha(Files.readAllBytes(mixed)));
            int previous = REFERENCES.size(); load(mixed, false, "mixed-", .5);
            assertEquals(previous + 2, REFERENCES.size());
        }
    }
    private static void load(Path archive, boolean distributed) throws Exception {
        load(archive, distributed, distributed ? "distributed-" : "", 1);
    }
    private static void load(Path archive, boolean distributed, String prefix, double electronicFraction) throws Exception {
        try (var z = new ZipFile(archive.toFile())) {
            var manifest = JsonParser.parseString(new String(z.getInputStream(z.getEntry("references.json")).readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            for (var e : manifest.getAsJsonObject("files").entrySet())
                assertEquals(e.getValue().getAsString(), sha(z.getInputStream(z.getEntry(e.getKey())).readAllBytes()));
            for (var e : manifest.getAsJsonObject("profiles").entrySet()) {
                var p = e.getValue().getAsJsonObject();
                var first = rows(z.getInputStream(z.getEntry(p.get("entry").getAsString())).readAllBytes());
                assertEquals(first, rows(z.getInputStream(z.getEntry(p.get("repeat_entry").getAsString())).readAllBytes()));
                assertEquals(2, first.stream().filter(row -> row.get("time_s") < 0).count());
                REFERENCES.put(prefix + e.getKey(), new Reference(new String(z.getInputStream(z.getEntry(p.get("dyr").getAsString())).readAllBytes(), StandardCharsets.UTF_8),
                        first.stream().filter(row -> row.get("time_s") >= 0).toList(), p.get("fault_x").getAsDouble(), distributed, electronicFraction));
            }
        }
    }
    @AfterAll static void clear() { REFERENCES.clear(); }
    @ParameterizedTest @CsvSource({".06,false", ".06,true", ".01,false", ".01,true", ".1,false", ".1,true",
            "distributed-.1,false", "distributed-.1,true", "distributed-.01,false", "distributed-.01,true",
            "mixed-.1,false", "mixed-.1,true", "mixed-.01,false", "mixed-.01,true"})
    void repeatedDropoutAndRecoveryMatchIndependentBoundaries(String impedance, boolean staggered) throws Exception {
        var ref = REFERENCES.get(impedance);
        Assumptions.assumeTrue(ref != null, "Retain independent captures for this profile");
        var out = Files.createTempDirectory(Path.of("target"), "electronic-full-recovery-");
        var input = out.resolve("dynamic.dyr"); Files.writeString(input, ref.dyr());
        double x = ref.faultX();
        var actual = Cmldblu1SharedRecording.simulateStaticControl(input, staggered, .0005, x, x);
        actual.write(out.resolve("actual.csv.gz"));
        assertEquals(1, actual.model.getData().value(132));
        assertEquals(ref.electronicFraction(), actual.model.getFel()); assertEquals(ref.rows().size(), actual.frames.size());
        assertEquals(ref.distributed() ? 5 : 3, actual.algorithm.getNetwork().getNoBus());
        if (ref.distributed()) {
            assertNotSame(actual.model.getDStabBus(), actual.model.getLowVoltBus());
            assertNotSame(actual.model.getLowVoltBus(), actual.model.getLoadBus());
        } else assertSame(actual.model.getDStabBus(), actual.model.getLoadBus());
        double[] clock = new double[1001]; float time = 0;
        for (int i = 1; i < clock.length; i++) { time += .0005f; clock[i] = time; }
        int duplicates = 0;
        for (int i = 0; i < actual.frames.size(); i++) {
            var frame = actual.frames.get(i); assertEquals(clock[(int) Math.round(frame.time() / .0005)], ref.rows().get(i).get("time_s"), 6e-13);
            if (i > 0 && frame.time() == actual.frames.get(i - 1).time()) duplicates++;
        }
        assertEquals(4, duplicates);
        var mappings = new ArrayList<String[]>(Arrays.asList(new String[][]{{"V1","V1"},{"V2","V2"},{"V3","V3"},
                {"P_PU","P_PU"},{"Q_PU","Q_PU"},{"SPEED","SPEED"},{"ANGLE_DEG","ANGLE_DEG"},
                {"VAR27","ELECTRONIC.P"},{"VAR28","ELECTRONIC.Q"},{"VAR29","Load.V"},{"VAR30","Low.V"}}));
        if (ref.electronicFraction() < 1) Collections.addAll(mappings,
                new String[]{"VAR25", "STATIC.P"}, new String[]{"VAR26", "STATIC.Q"},
                new String[]{"VAR57", "STATIC.LowVoltageG"}, new String[]{"VAR58", "STATIC.LowVoltageB"});
        for (String[] mapping : mappings) {
            double sum = 0, max = 0, lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < actual.frames.size(); i++) {
                double expected = ref.rows().get(i).get(mapping[0]);
                double error = actual.frames.get(i).values().get(mapping[1]) - expected;
                assertTrue(Double.isFinite(error)); sum += error * error; max = Math.max(max, Math.abs(error)); lo = Math.min(lo, expected); hi = Math.max(hi, expected);
            }
            double rmse = Math.sqrt(sum / actual.frames.size());
            assertTrue(mapping[0].equals("ANGLE_DEG") ? rmse < .0002 : hi > lo && rmse / (hi - lo) < .001,
                    mapping[0] + " RMSE=" + rmse + " range=" + (hi - lo));
            if (mapping[1].startsWith("STATIC.LowVoltage")) assertTrue(max < .000005, mapping[1] + " max=" + max);
        }
    }
}
