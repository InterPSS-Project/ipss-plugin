package org.interpss.core.dstab.dynLoad;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import com.google.gson.*;
import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.core.dstab.reference.BenchmarkOutputGuard;
import org.interpss.dstab.algo.DefaultDynamicSimulationSolver;
import org.interpss.dstab.algo.StaggeredEulerSolver;
import org.interpss.dstab.dynLoad.Cmldznu2Data;
import org.interpss.dstab.dynLoad.impl.*;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.test.TestConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

/** Independent operating-point controls distinguish the two composite compressor dialects. */
public class Cmldblu1CompressorInitializationTest {
    private static final TestConfig CONFIG=TestConfig.load("/test-config/ipss.test.plugin.core.json","Cmldblu1CompressorInitializationTest");
    private static final Path ARCHIVE=Path.of(CONFIG.string("reference.archive",
            "testData/private/verification-artifacts/cmldblu1-compressor-loading-20261031/captures-v2.zip"));
    private static final String ARCHIVE_SHA="fc690d7fd8b69a007d5434406d3df0607f2b784adf3c509534e1bd59921d8a7c";
    private static JsonObject profiles;
    private static String sha(byte[] raw) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));
    }
    @BeforeAll static void setup() throws Exception {
        IpssCorePlugin.init();IpssCorePlugin.configureSparseSolver("java-klu");
        Assumptions.assumeTrue(Files.isRegularFile(ARCHIVE),"Retain or regenerate the private initialization controls");
        assertEquals(ARCHIVE_SHA,sha(Files.readAllBytes(ARCHIVE)));
        try(var zip=new ZipFile(ARCHIVE.toFile())) {
            var manifest=JsonParser.parseString(new String(zip.getInputStream(zip.getEntry("references.json")).readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
            profiles=manifest.getAsJsonObject("profiles");
            for(var entry:manifest.getAsJsonObject("files").entrySet())
                assertEquals(entry.getValue().getAsString(),sha(zip.getInputStream(zip.getEntry(entry.getKey())).readAllBytes()));
            for(var profile:profiles.asMap().values()) {
                assertTrue(profile.getAsJsonObject().get("common_channels_equal").getAsBoolean());
                for(var part:profile.getAsJsonObject().getAsJsonArray("parts")) {
                    var metadata=part.getAsJsonObject();String name=metadata.get("entry").getAsString();
                    byte[] a=zip.getInputStream(zip.getEntry(name)).readAllBytes();
                    byte[] b=zip.getInputStream(zip.getEntry(name.replace("-a-part","-b-part"))).readAllBytes();
                    assertArrayEquals(a,b);assertEquals(metadata.get("sha256").getAsString(),sha(a));
                    assertEquals(metadata.get("rows").getAsInt()+1,new String(a,StandardCharsets.UTF_8).lines().count());
                }
            }
            for(var group:manifest.getAsJsonArray("additional_cold_pairs"))
                for(var part:group.getAsJsonObject().getAsJsonArray("parts")) {
                    var metadata=part.getAsJsonObject();String name=metadata.get("entry").getAsString();
                    byte[] a=zip.getInputStream(zip.getEntry(name)).readAllBytes();
                    assertArrayEquals(a,zip.getInputStream(zip.getEntry(name.replace("-a-part","-b-part"))).readAllBytes());
                    assertEquals(metadata.get("sha256").getAsString(),sha(a));
                }
        }
    }
    static Stream<Arguments> profiles() {
        return profiles.keySet().stream().sorted().flatMap(p->Stream.of(false,true).map(s->Arguments.of(p,s)));
    }
    private static double value(JsonObject source,String name) { return source.get(name).getAsDouble(); }
    @ParameterizedTest @MethodSource("profiles")
    void legacyOperatingPointAndZoneThresholdsFollowIndependentControls(String profileName,boolean shared) throws Exception {
        var profile=profiles.getAsJsonObject(profileName);String fixture=profile.get("fixture").getAsString();
        var context=new PSSEMultiFileLoader().loadDStab(Cmldblu1SharedSmibTest.CASE.resolve("load-network.raw").toString(),
                Cmldblu1SharedSmibTest.CASE.resolve(fixture+".dyr").toString());
        var bus=context.getDStabilityNet().getDStabBus("Bus2");
        var original=assertInstanceOf(Cmldznu2Model.class,bus.getInfoOnlyDynModel());
        double[] constants=original.getData().values();
        for(var override:profile.getAsJsonArray("overrides")) {
            String[] fields=override.getAsString().split("=");constants[Integer.parseInt(fields[0])]=Double.parseDouble(fields[1]);
        }
        Complex power=new Complex(bus.getLoadP(),bus.getLoadQ());
        var model=fixture.equals("cmldznu2")
                ? new Cmldznu2Model("loading",bus,original.getTargetLoadIds(),power,new Cmldznu2Data(constants))
                : new Cmldblu1Model("loading",bus,original.getTargetLoadIds(),power,Arrays.copyOf(constants,132));
        var algorithm=context.getDynSimuAlgorithm();assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuStepSec(.0005);algorithm.setSimuOutputHandler(new com.interpss.dstab.cache.StateMonitor());
        algorithm.setSolver(shared?new StaggeredEulerSolver(algorithm):new DefaultDynamicSimulationSolver(algorithm));
        assertTrue(algorithm.initialization());
        var motor=assertInstanceOf(LD1PACImpl.class,model.get1PhaseACMotor());var expected=profile.getAsJsonObject("initial");
        boolean zone=fixture.equals("cmldznu2");
        var errors=new LinkedHashMap<String,Double>();
        errors.put("VAR29",model.getLoadBus().getVoltageMag()-value(expected,"VAR29"));
        errors.put("VAR143",motor.getMvaBase()-value(expected,"VAR143"));
        errors.put("STATE20",motor.getNamedState("TemperatureA")-value(expected,"STATE20"));
        errors.put("STATE21",motor.getNamedState("TemperatureB")-value(expected,"STATE21"));
        errors.put("VAR141",motor.getNamedState("RunningP0")-value(expected,"VAR141"));
        errors.put("VAR142",motor.getNamedState("RunningQ0")-value(expected,"VAR142"));
        Path output=Files.createTempDirectory(Path.of("target"),"cmldblu1-compressor-initialization-");
        try(var writer=BenchmarkOutputGuard.newWriter(output.resolve("comparison.json"),100000)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("profile",profileName,"method",shared?"staggered":"modified-euler",
                    "status",zone?"threshold-controls-only-zone-distribution-initialization-incomplete":"legacy-compressor-initialization-gate",
                    "unmasked_initialization_errors",errors),writer);
        }
        if(!zone)for(var error:errors.entrySet())
            assertEquals(0,error.getValue(),error.getKey().equals("VAR29")||error.getKey().equals("VAR143")?2e-6:2e-7,error.getKey());
        assertEquals(value(expected,"VAR144"),motor.getVstall(),2e-7);
        assertEquals(value(expected,"VAR145"),motor.getVbrk(),2e-7);
        if(!zone)
            assertEquals(value(expected,"VAR132"),-motor.getCompensateShuntY().getImaginary()
                    *bus.getNetwork().getBaseMva()/motor.getMvaBase(),2e-7);
        var voltage=model.getLoadBus().getVoltage();
        var current=voltage.multiply(motor.getPosSeqEquivY()).subtract((Complex)motor.getOutputObject());
        assertEquals(0,voltage.multiply(current.conjugate()).subtract(motor.getInitLoadPQ()).abs(),1e-10,
                "Parallel reactive compensation must preserve physical initial demand");
        double[] rates=new double[4];motor.evaluateStaggered(.0005,rates,0);
        for(double rate:rates)assertEquals(0,rate,1e-10,"The independently checked initial states must be in equilibrium");
        assertThrows(IllegalStateException.class,()->motor.setCharacteristicPolicy(LD1PACImpl.CharacteristicPolicy.STANDALONE));
    }
}
