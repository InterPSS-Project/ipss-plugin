package org.interpss.core.dstab.dynLoad;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.algo.DefaultDynamicSimulationSolver;
import org.interpss.dstab.algo.StaggeredEulerSolver;
import org.interpss.dstab.dynLoad.impl.Cmldblu1Model;
import org.interpss.dstab.dynLoad.impl.InductionMotorImpl;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.test.TestConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Independent distribution initialization, physical current balance and motor ownership. */
public class Cmldblu1InitializationTest {
    private static final TestConfig CONFIG = TestConfig.load("/test-config/ipss.test.plugin.core.json", "Cmldblu1InitializationTest");
    private static final Path ROOT = Path.of(CONFIG.string("reference.root",
            "testData/private/verification-artifacts/cmldblu1-initialization-extended-20261031/references"));
    private static JsonObject profiles;

    @BeforeAll static void setup() throws Exception {
        IpssCorePlugin.init(); IpssCorePlugin.configureSparseSolver("java-klu");
        Assumptions.assumeTrue(Files.isRegularFile(ROOT.resolve("manifest.json")),
                "Retain or regenerate the private synthetic initialization references");
        byte[] raw = Files.readAllBytes(ROOT.resolve("manifest.json"));
        assertEquals("8cfdcf20ead67e944f3800e3aca7072a0a3cf7486c10d3ca605d3c63b27ddab9", sha(raw));
        profiles = JsonParser.parseString(new String(raw, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("profiles");
        for (var profile : profiles.asMap().values()) for (var part : profile.getAsJsonObject().getAsJsonArray("parts")) {
            var metadata = part.getAsJsonObject();
            assertTrue(metadata.get("cold_pair_equal").getAsBoolean());
            byte[] archive = Files.readAllBytes(ROOT.resolve(metadata.get("file").getAsString()));
            assertEquals(metadata.get("archive_sha256").getAsString(), sha(archive));
            try (var zip = new GZIPInputStream(new ByteArrayInputStream(archive))) {
                byte[] csv = zip.readAllBytes();
                assertEquals(metadata.get("raw_sha256").getAsString(), sha(csv));
                assertEquals(metadata.get("rows").getAsInt()+1, new String(csv, StandardCharsets.UTF_8).lines().count());
            }
        }
    }
    private static String sha(byte[] raw) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));
    }
    static Stream<Arguments> profiles() {
        return profiles.keySet().stream().filter(name -> !name.equals("feeder-floor")).sorted()
                .flatMap(name -> Stream.of(false,true).map(shared -> Arguments.of(name,shared)));
    }
    private static double value(JsonObject row, String key) { return row.get(key).getAsDouble(); }

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void feederVoltageFloorRemainsAnExplicitInitializationDiagnostic(boolean staggered) throws Exception {
        distributionAndMotorInitializationMatchIndependentDiscriminators("feeder-floor",staggered);
    }

    @ParameterizedTest @MethodSource("profiles")
    void distributionAndMotorInitializationMatchIndependentDiscriminators(String name, boolean staggered) throws Exception {
        var profile = profiles.getAsJsonObject(name);
        boolean floorDiagnostic = name.equals("feeder-floor");
        var expected = profile.getAsJsonObject("initial");
        assertTrue(profile.get("common_channels_equal").getAsBoolean());
        var context = new PSSEMultiFileLoader().loadDStab(Cmldblu1SharedSmibTest.CASE.resolve("load-network.raw").toString(),
                Cmldblu1SharedSmibTest.CASE.resolve("cmldblu1.dyr").toString());
        var net = context.getDStabilityNet();
        var parent = net.getDStabBus("Bus2");
        var original = assertInstanceOf(Cmldblu1Model.class, parent.getInfoOnlyDynModel());
        double[] constants = original.getLegacyConstants();
        for (var override : profile.getAsJsonArray("overrides")) {
            String[] pair = override.getAsString().split("=");
            constants[Integer.parseInt(pair[0])] = Double.parseDouble(pair[1]);
        }
        var model = new Cmldblu1Model("initial", parent, original.getTargetLoadIds(),
                new Complex(parent.getLoadP(),parent.getLoadQ()), constants);
        var algorithm = context.getDynSimuAlgorithm();
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuStepSec(.0005);
        algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        algorithm.setSolver(staggered ? new StaggeredEulerSolver(algorithm) : new DefaultDynamicSimulationSolver(algorithm));
        assertTrue(algorithm.initialization());
        var transformer = net.getBranchList().stream().filter(branch -> branch.getToBus() == model.getLowVoltBus()).findFirst().orElseThrow();
        var feeder = net.getBranchList().stream().filter(branch -> branch.getToBus() == model.getLoadBus()).findFirst().orElseThrow();
        assertEquals(value(expected,"VAR12"), transformer.getToTurnRatio(), 2e-6, "Effective initial tap");
        assertEquals(value(expected,"VAR29"), model.getLoadBus().getVoltageMag(), floorDiagnostic?1e-4:2e-6, "Internal load voltage");
        assertEquals(value(expected,"VAR30"), model.getLowVoltBus().getVoltageMag(), 2e-6, "Transformer low-side voltage");
        if (floorDiagnostic) {
            assertEquals(.95,model.getLoadBus().getVoltageMag(),1e-12,"Analytic minimum-voltage endpoint");
            assertEquals(constants[2]/constants[3],feeder.getZ().getReal()/feeder.getZ().getImaginary(),1e-12);
        } else {
            assertEquals(value(expected,"VAR40"), feeder.getZ().getReal(), 2e-6, "Prepared feeder resistance");
            assertEquals(value(expected,"VAR41"), feeder.getZ().getImaginary(), 2e-6, "Prepared feeder reactance");
        }
        double bss = constants[1] * model.getDistEquivalent().getMva() / net.getBaseMva();
        assertEquals(value(expected,"VAR55"), model.getLowVoltBus().getShuntY().getImaginary()-bss, 2e-6, "Near-end compensation");
        if (!floorDiagnostic) assertEquals(value(expected,"VAR56"), model.getLoadBus().getShuntY().getImaginary(), 2e-6, "Far-end compensation");
        var motor = assertInstanceOf(InductionMotorImpl.class, model.getInductionMotorA());
        if (!floorDiagnostic) {
            assertEquals(value(expected,"VAR47"), motor.getInitLoadPQ().getReal(), 2e-6, "Allocated motor P");
            assertEquals(value(expected,"VAR48"), motor.getInitLoadPQ().getImaginary(), 2e-6, "Allocated motor Q");
        }
        String[] states = {"EPrimeD","EPrimeQ","EDoublePrimeD","EDoublePrimeQ","SpeedDeviation"};
        var stateErrors = new java.util.LinkedHashMap<String,Double>();
        for (int i=0; i<states.length; i++) stateErrors.put(states[i],model.getNamedState("MotorA."+states[i])-value(expected,"STATE"+i));
        stateErrors.put("InitialQAdmittance",motor.getCompensateShuntY().getImaginary()-value(expected,"VAR69"));
        stateErrors.put("FeederR",feeder.getZ().getReal()-value(expected,"VAR40"));
        stateErrors.put("FeederX",feeder.getZ().getImaginary()-value(expected,"VAR41"));
        stateErrors.put("FarCompensation",model.getLoadBus().getShuntY().getImaginary()-value(expected,"VAR56"));
        Path output = Files.createTempDirectory(Path.of("target"),"cmldblu1-initialization-");
        try (var writer = org.interpss.core.dstab.reference.BenchmarkOutputGuard.newWriter(output.resolve("motor-initial-errors.json"),10000)) {
            var report = java.util.Map.of("profile",name,"method",staggered?"staggered":"modified-euler",
                    "status",floorDiagnostic?"feeder-floor-source-endpoint-unqualified":"distribution-init-regression-motor-internal-qualification-incomplete","errors",stateErrors);
            new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report,writer);
        }
        // Distribution and allocated-P/Q gates above have independent evidence.
        // Complete motor coordinate/frame and initial solver parity remain open;
        // retain all measured differences without asserting that equivalence.
        assertTrue(stateErrors.values().stream().allMatch(Double::isFinite));
        for (var owner : model.getIntegrationStateOwners().values()) {
            var participant = assertInstanceOf(com.interpss.dstab.controller.runtime.StaggeredEulerParticipant.class, owner);
            double[] rates = new double[participant.staggeredStateCount()];
            participant.evaluateStaggered(.0005,rates,0);
            for (double rate : rates) assertEquals(0,rate,1e-8,"Initial state residual");
        }
        for (var bus : new com.interpss.dstab.BaseDStabBus<?,?>[] {model.getLoadBus(),model.getLowVoltBus()})
            assertEquals(0,net.getCustomBusCurrInjHashtable().getOrDefault(bus.getId(),Complex.ZERO).abs(),1e-9,
                    "Internal current balance must not depend on an artificial fixed injection");
        Complex voltage = model.getLoadBus().getVoltage();
        Complex demandCurrent = voltage.multiply(motor.getPosSeqEquivY()).subtract((Complex)motor.getOutputObject());
        assertEquals(0,voltage.multiply(demandCurrent.conjugate()).subtract(motor.getInitLoadPQ()).abs(),1e-10,
                "Physical matching admittance is included exactly once");
        var copy = new InductionMotorImpl.ModelState(motor).create(model.getLoadBus());
        assertEquals(motor.getPosSeqEquivY(),copy.getPosSeqEquivY());
        assertEquals(motor.getCompensateShuntY(),copy.getCompensateShuntY());
        assertEquals(motor.getOutputObject(),copy.getOutputObject());
        assertEquals(motor.getIntegrationStates(),copy.getIntegrationStates());
        assertThrows(IllegalStateException.class, () -> motor.setInitialReactiveDemandMatching(false));
    }
}
