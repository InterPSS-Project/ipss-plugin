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
    private static final String SYNTHETIC_DYR = """
            1 'USRMDL' '1' 'GEWTGCU1' 101 1 2 18 3 3
              40 0
              1.5 0.33403 0.50 0.90 2.775 1.20 1.00 0.40 0.90 10.0 0.02
              0.40 0.00 0.70 0.55 0.90 1.00 0.10 /
            1 'USRMDL' '1' 'GEWTECU1' 102 0 9 67 18 16
              0 0 1 0 0 0 0 '1' 0
              0.05 1.0 5.0 0.0 0.0 0.10 0.5 0.05 1.2 0.0
              0.8 -0.8 1.2 0.02 2.0 -2.0 0.10 0.1 0.8 1.2
              5.0 -1.0 1.0 0.05 0.05 1.0 0.10 0.95 0.98 1.02
              1.05 0.8 0.9 1.0 1.1 1.2 0.0 0.10 0.02 0.70
              10.0 100.0 -100.0 -1.0 1000.0 0.5 1.0 1.0 1.2 1.2
              1.2 0.05 0.1 0.1 0.0 0.01 0.1 0.5 1.0 -1.0
              0.2 -0.2 0.2 -0.2 0.5 0.3 -0.3 /
            2 'GENCLS' '1' 99999.0 0.0 /
            """;

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
    void rejectsIncorrectAllocation(@TempDir Path tempDir) throws Exception {
        Path invalid = tempDir.resolve("invalid.dyr");
        Files.writeString(invalid,
                SYNTHETIC_DYR.replace("102 0 9 67 18 16", "102 0 9 66 18 16"));
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
        Path dyr = tempDir.resolve("synthetic-gewt-models.dyr");
        Files.writeString(dyr, SYNTHETIC_DYR);
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt2g1_psse36.raw").toString(),
                dyr.toString());
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
