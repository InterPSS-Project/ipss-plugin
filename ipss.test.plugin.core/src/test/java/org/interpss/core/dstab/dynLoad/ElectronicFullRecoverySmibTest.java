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
    private record Reference(String dyr, List<Map<String, Double>> rows, double faultX, boolean distributed,
            double electronicFraction, double step, double duration, String omitted) { }
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
            load(mixed, true, "mixed-distributed-", .5, "diagnostic_profiles", ".1");
        }
        Path duration = Path.of(CONFIG.string("duration.archive", "testData/private/verification-artifacts/cmldblu1-distributed-duration-20261101/captures.zip"));
        if (Files.isRegularFile(duration)) {
            assertEquals("edc9467107056197587b69c791ab793042ce3d9802c58a985c7c0cfc84803bb3", sha(Files.readAllBytes(duration)));
            int previous = REFERENCES.size(); load(duration, true, "duration-", .5);
            assertEquals(previous + 2, REFERENCES.size());
        }
        Path partial = Path.of(CONFIG.string("partial.archive", "testData/private/verification-artifacts/cmldblu1-partial-distribution-20261101/captures.zip"));
        if (Files.isRegularFile(partial)) {
            assertEquals("01e6b5659580189d8c6928ec74245791bba0588f0699572335af616f4b966546", sha(Files.readAllBytes(partial)));
            int previous = REFERENCES.size(); load(partial, true, "partial-", .5);
            assertEquals(previous + 3, REFERENCES.size());
        }
    }
    private static void load(Path archive, boolean distributed) throws Exception {
        load(archive, distributed, distributed ? "distributed-" : "", 1);
    }
    private static void load(Path archive, boolean distributed, String prefix, double electronicFraction) throws Exception {
        load(archive, distributed, prefix, electronicFraction, "profiles", null);
    }
    private static void load(Path archive, boolean distributed, String prefix, double electronicFraction,
            String section, String selected) throws Exception {
        try (var z = new ZipFile(archive.toFile())) {
            var manifest = JsonParser.parseString(new String(z.getInputStream(z.getEntry("references.json")).readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            for (var e : manifest.getAsJsonObject("files").entrySet())
                assertEquals(e.getValue().getAsString(), sha(z.getInputStream(z.getEntry(e.getKey())).readAllBytes()));
            for (var e : manifest.getAsJsonObject(section).entrySet()) {
                if (selected != null && !selected.equals(e.getKey())) continue;
                var p = e.getValue().getAsJsonObject();
                var first = rows(z.getInputStream(z.getEntry(p.get("entry").getAsString())).readAllBytes());
                assertEquals(first, rows(z.getInputStream(z.getEntry(p.get("repeat_entry").getAsString())).readAllBytes()));
                assertEquals(2, first.stream().filter(row -> row.get("time_s") < 0).count());
                REFERENCES.put(prefix + e.getKey(), new Reference(new String(z.getInputStream(z.getEntry(p.get("dyr").getAsString())).readAllBytes(), StandardCharsets.UTF_8),
                        first.stream().filter(row -> row.get("time_s") >= 0).toList(), p.get("fault_x").getAsDouble(), distributed, electronicFraction,
                        p.has("step") ? p.get("step").getAsDouble() : .0005,
                        p.has("duration") ? p.get("duration").getAsDouble() : .5,
                        p.has("omitted") ? p.get("omitted").getAsString() : distributed ? "none" : "both"));
            }
        }
    }
    @AfterAll static void clear() { REFERENCES.clear(); }
    @ParameterizedTest @CsvSource({".06,false", ".06,true", ".01,false", ".01,true", ".1,false", ".1,true",
            "distributed-.1,false", "distributed-.1,true", "distributed-.01,false", "distributed-.01,true",
            "mixed-.1,false", "mixed-.1,true", "mixed-.01,false", "mixed-.01,true",
            "mixed-distributed-.1,false", "mixed-distributed-.1,true",
            "duration-quarter,false", "duration-quarter,true", "duration-fine,false", "duration-fine,true",
            "partial-no-transformer,false", "partial-no-transformer,true",
            "partial-no-feeder,false", "partial-no-feeder,true", "partial-no-feeder-low,false", "partial-no-feeder-low,true"})
    void repeatedDropoutAndRecoveryMatchIndependentBoundaries(String impedance, boolean staggered) throws Exception {
        var ref = REFERENCES.get(impedance);
        Assumptions.assumeTrue(ref != null, "Retain independent captures for this profile");
        var out = Files.createTempDirectory(Path.of("target"), "electronic-full-recovery-");
        var input = out.resolve("dynamic.dyr"); Files.writeString(input, ref.dyr());
        double x = ref.faultX();
        int steps = ref.rows().size() - 5; // Initial sample and both sides of four scheduled events.
        double h = ref.step(), endTime = steps * h;
        assertTrue(endTime >= ref.duration() - h / 2 && endTime < ref.duration() + .02);
        var actual = Cmldblu1SharedRecording.simulateStaticControl(input, staggered, h, x, x, endTime);
        actual.write(out.resolve("actual.csv.gz"));
        assertEquals(1, actual.model.getData().value(132));
        assertEquals(ref.electronicFraction(), actual.model.getFel()); assertEquals(ref.rows().size(), actual.frames.size());
        assertEquals(switch (ref.omitted()) { case "both" -> 3; case "none" -> 5; default -> 4; }, actual.algorithm.getNetwork().getNoBus());
        if (ref.omitted().equals("transformer")) {
            assertSame(actual.model.getDStabBus(), actual.model.getLowVoltBus());
            assertNotSame(actual.model.getLowVoltBus(), actual.model.getLoadBus());
        } else if (ref.omitted().equals("feeder")) {
            assertNotSame(actual.model.getDStabBus(), actual.model.getLowVoltBus());
            assertSame(actual.model.getLowVoltBus(), actual.model.getLoadBus());
        } else if (ref.distributed()) {
            assertNotSame(actual.model.getDStabBus(), actual.model.getLowVoltBus());
            assertNotSame(actual.model.getLowVoltBus(), actual.model.getLoadBus());
        } else assertSame(actual.model.getDStabBus(), actual.model.getLoadBus());
        double[] clock = new double[steps + 1]; float time = 0;
        for (int i = 1; i < clock.length; i++) { time += (float) h; clock[i] = time; }
        int duplicates = 0;
        for (int i = 0; i < actual.frames.size(); i++) {
            var frame = actual.frames.get(i); assertEquals(clock[(int) Math.round(frame.time() / h)], ref.rows().get(i).get("time_s"), ref.duration() > .5 ? 6e-11 : 6e-13);
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
            // Keep the short-run gate; ten-second controls use the separately requested 0.01-degree limit.
            double angleLimit = ref.duration() > .5 ? .01 : .0002;
            assertTrue(mapping[0].equals("ANGLE_DEG") ? rmse < angleLimit : hi > lo && rmse / (hi - lo) < .001,
                    mapping[0] + " RMSE=" + rmse + " range=" + (hi - lo));
            if (mapping[1].startsWith("STATIC.LowVoltage")) assertTrue(max < .000005, mapping[1] + " max=" + max);
        }
    }
}
