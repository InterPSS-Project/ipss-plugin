package org.interpss.threePhase.dataparser;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.IpssCorePlugin;
import org.interpss.threePhase.dataParser.cim.CimDistributionDataParser;
import org.interpss.threePhase.powerflow.DistributionPFMethod;
import org.interpss.threePhase.powerflow.DistributionPowerFlowOptions;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

/** Unmodified public CIM inputs versus independently exported CIMHub/native OpenDSS references. */
class CimPublicFeederTest {
    @TempDir Path temporary;
    @BeforeAll static void initialize() { IpssCorePlugin.init(); }

    @ParameterizedTest(name = "{0}: unmodified public CIM feeder")
    @CsvFileSource(resources = "/cim/public/cases.csv", numLinesToSkip = 1)
    void preservesEveryNodeAndMatchesIndependentOpenDss(String name, String sha256, int connectivityNodes) throws Exception {
        byte[] xml;
        try (var resource = getClass().getResourceAsStream("/cim/public/" + name + ".xml.gz")) {
            assertNotNull(resource, name);
            try (var gzip = new GZIPInputStream(resource)) { xml = gzip.readAllBytes(); }
        }
        assertEquals(sha256, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(xml)),
                "Public XML must remain byte-for-byte unchanged");
        Path file = temporary.resolve(name + ".xml");
        Files.write(file, xml);
        var parser = new CimDistributionDataParser();
        var network = parser.parse(file);
        assertEquals(connectivityNodes, parser.getBusIds().size(), "No public connectivity nodes may be dropped");
        assertEquals(connectivityNodes + 1, network.getNoBus(), "One extra internal Thevenin source bus");
        assertEquals(1, network.getBusList().stream().filter(bus -> bus.isSwing()).count());
        var powerFlow = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(network);
        powerFlow.setPFMethod(DistributionPFMethod.Fixed_Point);
        powerFlow.setPowerFlowOptions(DistributionPowerFlowOptions.DEFAULTS);
        powerFlow.setTolerance(1e-8);
        powerFlow.setMaxIteration(200);
        assertTrue(powerFlow.powerflow(), name + " must converge");
        for (var bus : network.getBusList()) {
            var v = bus.get3PhaseVotlages();
            assertTrue(Double.isFinite(v.a_0.abs()) && Double.isFinite(v.b_1.abs()) && Double.isFinite(v.c_2.abs()),
                    bus.getId() + " finite voltage");
            // Absent phases can be zero; require each mapped bus to remain energized.
            assertTrue(v.absMax() > 0.1 && v.absMax() < 2, bus.getId() + " plausible energized voltage");
        }
        Map<String, String> ids = new HashMap<>();
        parser.getBusIds().forEach((uri, id) -> assertNull(ids.put(parser.getCimNames().get(uri)
                .toLowerCase(Locale.ROOT).replaceAll("[ .=+\\^$*|\\[\\]{}]", "_"), id), "Unique native bus names"));
        Map<String, Complex[]> reference = new LinkedHashMap<>();
        int expectedPhases = 0;
        for (String row : referenceLines(name + "-voltage.csv").stream().skip(1).toList()) {
            String[] f = row.split(",");
            int phase = Integer.parseInt(f[1]);
            assertTrue(phase >= 1 && phase <= 3, "Reference must contain physical phase nodes only");
            Complex[] phases = reference.computeIfAbsent(f[0], k -> new Complex[3]);
            assertNull(phases[phase - 1], "Duplicate reference phase");
            phases[phase - 1] = new Complex(Double.parseDouble(f[2]), Double.parseDouble(f[3]));
            expectedPhases++;
        }
        assertEquals(ids.keySet(), reference.keySet(), "Every physical CIM bus must have an independent reference");
        boolean largeLv = name.startsWith("IEEE390");
        double voltageLimit = 1e-5;
        double maxError = 0, maxMagnitude = 0, maxAngle = 0;
        int checked = 0;
        for (var entry : reference.entrySet()) {
            var bus = network.getBus(ids.get(entry.getKey()));
            var v = bus.get3PhaseVotlages();
            Complex[] actual = {v.a_0, v.b_1, v.c_2};
            // Isolated delta secondaries have no physical phase-to-ground reference.
            boolean floatingDelta = (name.startsWith("IEEE123") && entry.getKey().equals("610"))
                    || (name.equals("Transactive") && entry.getKey().equals("node_610"));
            for (int phase = 0; phase < 3; phase++) if (entry.getValue()[phase] != null) {
                Complex a = actual[phase], b = entry.getValue()[phase].divide(bus.getBaseVoltage() / Math.sqrt(3));
                if (floatingDelta) {
                    assertNotNull(entry.getValue()[(phase + 1) % 3]);
                    a = a.subtract(actual[(phase + 1) % 3]).divide(Math.sqrt(3));
                    b = entry.getValue()[phase].subtract(entry.getValue()[(phase + 1) % 3]).divide(bus.getBaseVoltage());
                }
                double error = a.subtract(b).abs();
                assertTrue(Double.isFinite(error) && error < voltageLimit,
                        name + " " + entry.getKey() + "." + (phase + 1) + " complex-voltage error=" + error);
                maxError = Math.max(maxError, error);
                maxMagnitude = Math.max(maxMagnitude, Math.abs(a.abs() - b.abs()));
                maxAngle = Math.max(maxAngle, Math.abs(Math.toDegrees(a.divide(b).getArgument())));
                checked++;
            }
        }
        assertEquals(expectedPhases, checked);
        var sourceBranch = network.getBranchList().stream()
                .filter(b -> network.getBus(b.getFromBus().getId()).isSwing()).findFirst().orElseThrow();
        var source = network.getBus(sourceBranch.getToBus().getId());
        var current = new Complex3x1();
        var voltage = source.get3PhaseVotlages();
        // External terminal: exclude the internal Thevenin impedance losses.
        for (var branch : network.getBranchList()) if (branch.isActive() && branch != sourceBranch) {
            var three = (com.interpss.core.threephase.IBranch3Phase) branch;
            if (branch.getFromBus().equals(source)) current = current.add(three.getYffabc().multiply(voltage)
                    .add(three.getYftabc().multiply(network.getBus(branch.getToBus().getId()).get3PhaseVotlages())));
            if (branch.getToBus().equals(source)) current = current.add(three.getYttabc().multiply(voltage)
                    .add(three.getYtfabc().multiply(network.getBus(branch.getFromBus().getId()).get3PhaseVotlages())));
        }
        current = current.subtract(source.calcLoad3PhEquivCurInj());
        Complex power = voltage.a_0.multiply(current.a_0.conjugate())
                .add(voltage.b_1.multiply(current.b_1.conjugate()))
                .add(voltage.c_2.multiply(current.c_2.conjugate())).multiply(network.getBaseMva() * 1000 / 3);
        String[] referencePower = referenceLines(name + "-power.csv").get(1).split(",");
        double pError = power.getReal() - Double.parseDouble(referencePower[0]);
        double qError = power.getImaginary() - Double.parseDouble(referencePower[1]);
        assertEquals(0, pError, largeLv ? 2.0 : 0.05, name + " external source kW");
        assertEquals(0, qError, largeLv ? 0.1 : 0.05, name + " external source kvar");
        String result = String.format(Locale.ROOT, "%s,%d,%d,%.12g,%.12g,%.12g,%.12g,%.12g%n",
                name, reference.size(), checked, maxError, maxMagnitude, maxAngle, pError, qError);
        Path reports = Path.of("target/cim-public-parity");
        Files.createDirectories(reports);
        Files.writeString(reports.resolve(name + ".csv"),
                "case,buses,phasors,max_complex_pu,max_magnitude_pu,max_angle_deg,source_kw_error,source_kvar_error\n" + result);
        System.out.print("CIM_PUBLIC_PARITY " + result);
    }

    private List<String> referenceLines(String file) throws Exception {
        byte[] bytes;
        try (var stream = getClass().getResourceAsStream("/cim/public/" + file)) {
            assertNotNull(stream, file); bytes = stream.readAllBytes();
        }
        try (var hashes = getClass().getResourceAsStream("/cim/public/reference-sha256.csv")) {
            assertNotNull(hashes);
            String hash = new String(hashes.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).lines()
                    .filter(row -> row.startsWith(file + ",")).findFirst().orElseThrow().split(",")[1];
            assertEquals(hash, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), file);
        }
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8).lines().toList();
    }
}
