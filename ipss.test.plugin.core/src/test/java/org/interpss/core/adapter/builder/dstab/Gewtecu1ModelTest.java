package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.IpssCorePlugin;
import org.interpss.dstab.mach.Gewtecu1Model;
import org.interpss.dstab.mach.Gewtgcu1Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Exact wrapper, initialization, curve, limit, and named-state checks. */
public class Gewtecu1ModelTest {
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");
    private static final Path MODEL_DYR = CASE.resolve(
            "SMIB_v33_gewtgcu1_gewtecu1.dyr");

    @Test
    void exactNativeWrapperAttachesAndInitializesAllPublishedStates(@TempDir Path tempDir)
            throws Exception {
        Fixture fixture = load(tempDir);
        assertEquals(82, DynamicModelCatalog.find("GEWTECU1").orElseThrow().parameterCount());
        assertEquals(18, fixture.controller.getNamedStates().size());
        assertTrue(fixture.controller.getNamedStates().containsKey("Filter in Voltage regulator"));
        assertTrue(fixture.controller.getNamedStates().containsKey("Washout in WindInertia"));
        assertNotNull(fixture.generator.getElectricalController());
    }

    @Test
    void frequencyCurveInterpolatesPublishedFourPointCharacteristic(@TempDir Path tempDir)
            throws Exception {
        Gewtecu1Model model = load(tempDir).controller;
        assertEquals(.8, model.frequencyPower(.94), 0.0);
        assertEquals(.85, model.frequencyPower(.965), 1.0e-12);
        assertEquals(.95, model.frequencyPower(1.0), 1.0e-12);
        assertEquals(1.05, model.frequencyPower(1.035), 1.0e-12);
        assertEquals(1.1, model.frequencyPower(1.06), 0.0);
    }

    @Test
    void tripEnvelopeAndPredictorCorrectorAreActive(@TempDir Path tempDir) throws Exception {
        Fixture fixture = load(tempDir);
        fixture.controller.setExternalSignals(1.0, 10.0, .9, .97,
                .01, 0.0, 0.0);
        assertTrue(fixture.controller.step(.0005, 0));
        assertTrue(fixture.controller.step(.0005, 1));
        assertTrue(!fixture.controller.isTripped());
        fixture.controller.setExternalSignals(-2.1, 10.0, .9, 1.0,
                0.0, 0.0, 0.0);
        assertTrue(fixture.controller.step(.0005, 0));
        assertTrue(fixture.controller.step(.0005, 1));
        assertTrue(fixture.controller.isTripped());
    }

    @Test
    void terminalReactivePowerBypassesBranchSynthesizingImpedance(@TempDir Path tempDir)
            throws Exception {
        Gewtecu1Model model = load(tempDir).controller;
        model.setMeasuredPower(0.5, -0.4);
        model.setExternalSignals(0.2, 10.0, 0.9, 1.0, 0.0, 0.0, -0.4);
        assertTrue(model.step(0.0005, 0));
        assertTrue(model.step(0.0005, 1));
        assertEquals(-0.00398,
                model.getNamedState("Filter in Reactive Droop"), 1.0e-12);
    }

    @Test
    void downstreamVoltageLimitCoordinatesUpstreamAntiWindup(@TempDir Path tempDir)
            throws Exception {
        Gewtecu1Model model = load(tempDir).controller;
        model.setMeasuredPower(0.5, -2.0);
        model.setExternalSignals(0.2, 10.0, 0.9, 1.0, 0.0, 0.0, -2.0);
        for (int index = 0; index < 200; index++) {
            assertTrue(model.step(0.0005, 0));
            assertTrue(model.step(0.0005, 1));
        }
        assertEquals(1.0, model.getNamedState("Volt/Mvar integrator"), 0.0);
        double upstreamAtLimit = model.getNamedState("Mvar/Volt integrator");
        for (int index = 0; index < 20; index++) {
            assertTrue(model.step(0.0005, 0));
            assertTrue(model.step(0.0005, 1));
        }
        assertEquals(upstreamAtLimit,
                model.getNamedState("Mvar/Volt integrator"), 0.0);
    }

    @Test
    void brakingStateIntegratesLimitedExcessPower(@TempDir Path tempDir)
            throws Exception {
        Gewtecu1Model model = load(tempDir).controller;
        model.setMeasuredPower(0.4, 0.0);
        model.setExternalSignals(0.2, 10.0, 0.9, 1.0,
                0.0, 0.0, 0.0);
        assertTrue(model.step(0.001, 0));
        assertTrue(model.step(0.001, 1));

        // Initial WPCMD is 5/6 pu on turbine base.  Measured power is
        // 0.4 * 100/60 = 2/3 pu, so the published braking-power input is 1/6 pu.
        assertEquals(1.0 / 6000.0,
                model.getNamedState("Braking resistor integrator"), 1.0e-12);
    }

    @Test
    void rejectsIncorrectAllocation(@TempDir Path tempDir) throws Exception {
        Path invalid = tempDir.resolve("invalid.dyr");
        Files.writeString(invalid, Files.readString(MODEL_DYR)
                .replace("102 0 9 67 18 16", "102 0 9 66 18 16"));
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt2g1_psse36.raw").toString(), invalid.toString());
        DStabGen gen = (DStabGen) context.getDStabilityNet().getBus("Bus1")
                .getContributeGen("1");
        Gewtgcu1Model generator = assertInstanceOf(Gewtgcu1Model.class,
                gen.getDynamicGenDevice());
        assertTrue(generator.getElectricalController() == null);
    }

    private static Fixture load(Path tempDir) throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt2g1_psse36.raw").toString(),
                MODEL_DYR.toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        DStabGen gen = (DStabGen) network.getBus("Bus1").getContributeGen("1");
        Gewtgcu1Model generator = assertInstanceOf(Gewtgcu1Model.class,
                gen.getDynamicGenDevice());
        Gewtecu1Model controller = generator.getElectricalController();
        assertNotNull(controller);
        algorithm.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(.0005);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.initialization());
        return new Fixture(generator, controller);
    }

    private record Fixture(Gewtgcu1Model generator, Gewtecu1Model controller) { }
}
