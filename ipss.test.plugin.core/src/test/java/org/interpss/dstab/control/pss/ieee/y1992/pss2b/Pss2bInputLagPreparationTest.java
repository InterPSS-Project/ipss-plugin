package org.interpss.dstab.control.pss.ieee.y1992.pss2b;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.interpss.IpssCorePlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

/** Independent input-transducer boundary controls, preserving both integration paths. */
public class Pss2bInputLagPreparationTest {
    @TempDir Path staged;
    private Pss2bReferenceFixture references() {
        return new Pss2bReferenceFixture(staged,
                Path.of("testData/private/verification-artifacts/pss2b-input-lags-20261102/captures.zip"),
                "db881e29663911b21a2b0b569f6be3734d831069aa4f3efde05d10694e413eee");
    }

    @Test void standardImportPreparesBothLagsWithoutMutatingEnteredConstants() throws Exception {
        IpssCorePlugin.init();
        var model = Pss2bCompiledStageTest.model(false, 5, 1);
        assertTrue(model.getData().isStandardInputLagPreparation(), "Standard record selects its preparation policy");
        for (double step : new double[]{.0005, .00025}) {
            double entered = (double)(float)(2*step);
            model.getData().setT6(entered); model.getData().setT7(entered);
            model.configureIntegrationStep(step);
            assertTrue(model.initStates(model.getMachine().getDStabBus(), model.getMachine()));
            assertEquals(0., model.t6); assertEquals(0., model.t7);
            assertEquals(entered, model.getData().getT6()); assertEquals(entered, model.getData().getT7());
            model.configureIntegrationStep(step/2);
            assertTrue(model.initStates(model.getMachine().getDStabBus(), model.getMachine()));
            assertEquals(entered, model.t6); assertEquals(entered, model.t7);
        }
    }

    @Test void typedExtensionRetainsItsExistingHalfStepPolicy() throws Exception {
        IpssCorePlugin.init();
        assertFalse(new Ieee1992PSS2BStabilizerData().isStandardInputLagPreparation());
        var model = Pss2bCompiledStageTest.model(false, 5, 1);
        model.getData().setStandardInputLagPreparation(false);
        model.getData().setT6(.000125); model.getData().setT7(.000375);
        model.configureIntegrationStep(.0005);
        assertTrue(model.initStates(model.getMachine().getDStabBus(), model.getMachine()));
        assertEquals(0., model.t6); assertEquals(.0005, model.t7);
    }

    @Test void independentCapturesDistinguishBypassBoundaryFromActiveLag() throws Exception {
        var refs = references();
        for (String grid : new String[]{"0p0005", "0p00025"}) for (int slot : new int[]{1, 2}) {
            var zero = Pss2bReferenceReplayTest.rows(refs.stage(tag(grid,slot,"0","a")).resolve("native.csv"));
            var boundary = Pss2bReferenceReplayTest.rows(refs.stage(tag(grid,slot,"2","a")).resolve("native.csv"));
            var active = Pss2bReferenceReplayTest.rows(refs.stage(tag(grid,slot,"2p1","a")).resolve("native.csv"));
            assertEquals(zero, boundary, "Two outer intervals bypass the lag across every recorded coordinate");
            double maximum = 0.; String coordinate = "PSS_STATE_" + (2+(slot-1)*3);
            assertEquals(zero.size(), active.size());
            for (int i=0;i<zero.size();i++) maximum=Math.max(maximum,
                    Math.abs(zero.get(i).get(coordinate)-active.get(i).get(coordinate)));
            assertTrue(maximum>.1, "The above-boundary lag must be independently excited");
            for (String ratio : new String[]{"0","2","2p1"})
                assertArrayEquals(Files.readAllBytes(refs.stage(tag(grid,slot,ratio,"a")).resolve("native.csv")),
                        Files.readAllBytes(refs.stage(tag(grid,slot,ratio,"b")).resolve("native.csv")), "Cold repeat");
        }
    }

    static java.util.stream.Stream<Arguments> profiles() {
        return java.util.stream.Stream.of("0p0005","0p00025").flatMap(grid->
                java.util.stream.Stream.of(1,2).flatMap(slot->
                java.util.stream.Stream.of("0","2","2p1").flatMap(ratio->
                java.util.stream.Stream.of(false,true).map(shared->Arguments.of(grid,slot,ratio,shared)))));
    }

    static java.util.stream.Stream<Arguments> sourceProfiles() {
        return java.util.stream.Stream.of("0p0005","0p00025").flatMap(grid->
                java.util.stream.Stream.of(1,2).flatMap(slot->
                java.util.stream.Stream.of("0","2","2p1").map(ratio->Arguments.of(grid,slot,ratio))));
    }

    @ParameterizedTest(name="output reconstruction {0}, input={1}, ratio={2}") @MethodSource("sourceProfiles")
    void independentStatesReconstructTheSampledOutput(String grid,int slot,String ratio) throws Exception {
        IpssCorePlugin.init();
        Path root=references().stage(tag(grid,slot,ratio,"a"));
        Pss2bReferenceReplayTest.reconstructPublishedOutput(root, true);
        if (slot == 2 && !ratio.equals("2p1")) {
            var rejected=assertThrows(AssertionError.class,
                    () -> Pss2bReferenceReplayTest.reconstructPublishedOutput(root));
            assertTrue(rejected.getMessage().contains("algebraic output at"),
                    "Current-input reconstruction must fail its output gate, not fixture loading");
        }
    }

    @ParameterizedTest(name="{0}, input={1}, ratio={2}, staggered={3}") @MethodSource("profiles")
    void completeFaultBoundaryTrajectories(String grid,int slot,String ratio,boolean staggered) throws Exception {
        IpssCorePlugin.init(); IpssCorePlugin.configureSparseSolver("java-klu");
        var refs=references(); String name=tag(grid,slot,ratio,"a");
        refs.requireConstantPowerLoads(name);
        var profile=new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode()
                .put("event","fault").put("variant","standard");
        Path output=Files.createTempDirectory(Path.of("target"),"pss2b-input-lag-");
        System.out.println("PSS2B input lag diagnostic "+output.getFileName());
        Object result=Pss2bCoupledBoundaryConformanceTest.run(refs.stage(name), profile, output, staggered);
        var diagnostic=new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(result);
        double peak=diagnostic.get("maximum_error").get("VOTHSG").asDouble();
        assertTrue(peak < (staggered ? .001 : .01),
                "Qualified short-lag sampled output, peak="+peak+", staggered="+staggered);
    }

    private static String tag(String grid,int slot,String ratio,String repeat) {
        return "pss2b-native-lag-boundary-h"+grid+"-i"+slot+"-r"+ratio+"-"+repeat+"-20261102";
    }
}
