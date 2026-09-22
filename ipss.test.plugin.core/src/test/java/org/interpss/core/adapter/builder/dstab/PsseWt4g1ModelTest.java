package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.IpssCorePlugin;
import org.interpss.dstab.mach.Wt4g1Data;
import org.interpss.dstab.mach.Wt4g1Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Exact schema, initialization, limits, and named-state checks for WT4G1. */
public class PsseWt4g1ModelTest {
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");

    @Test
    void exactPublishedRecordAttachesAndInitializes() throws Exception {
        Fixture fixture = load();
        Wt4g1Model model = fixture.model;
        assertEquals(new Wt4g1Data(.027, .019, .37, .93, 1.07,
                1.23, 1.80, 1.70, .031), model.getData());
        assertEquals(9, DynamicModelCatalog.find("WT4G1").orElseThrow().parameterCount());
        assertEquals(3, model.getNamedStates().size());
        assertEquals(.5, model.getP(), 2.0e-6);
        assertEquals(model.getP() / fixture.network.getBus("Bus1").getVoltageMag(),
                model.getActiveCurrentState(), 1.0e-12);
    }

    @Test
    void lvplHvrcAndDirectionalRecoveryUsePublishedBoundaries() throws Exception {
        Wt4g1Model model = load().model;
        assertEquals(0.0, model.lowVoltagePowerLimit(.36), 0.0);
        assertEquals(1.07 * .50, model.lowVoltagePowerLimit(.65), 1.0e-12);
        assertEquals(1.07, model.lowVoltagePowerLimit(.94), 0.0);
        assertEquals(0.0, Wt4g1Model.fixedLowVoltageActiveGain(.4), 0.0);
        assertEquals(.5, Wt4g1Model.fixedLowVoltageActiveGain(.6), 1.0e-12);
        assertEquals(1.0, Wt4g1Model.fixedLowVoltageActiveGain(.8), 0.0);
        assertEquals(0.0, Wt4g1Model.highVoltageCorrection(1.22, 1.23, 1.8), 0.0);
        assertEquals(.09, Wt4g1Model.highVoltageCorrection(1.28, 1.23, 1.8), 1.0e-12);
        assertEquals(1.7, Wt4g1Model.directionalRecoveryRate(.2, 10.0, 1.7), 0.0);
        assertEquals(-10.0, Wt4g1Model.directionalRecoveryRate(.2, -10.0, 1.7), 0.0);
        assertEquals(10.0, Wt4g1Model.directionalRecoveryRate(-.2, 10.0, 1.7), 0.0);
        assertEquals(-1.7, Wt4g1Model.directionalRecoveryRate(-.2, -10.0, 1.7), 0.0);

        double initial = model.getActiveCurrentState();
        model.setCommands(initial + 1.0, model.getReactiveCurrentState());
        assertTrue(model.nextStep(.001, DynamicSimuMethod.MODIFIED_EULER, 0));
        assertTrue(model.nextStep(.001, DynamicSimuMethod.MODIFIED_EULER, 1));
        assertTrue(model.getActiveCurrentState() - initial <= .0017 + 1.0e-12);
    }

    @Test
    void rejectsExtraFieldsAndInvalidBoundaries(@TempDir Path tempDir) throws Exception {
        Path dyr = tempDir.resolve("extra.dyr");
        Files.writeString(dyr,
                "1 'WT4G1' '1' .027 .019 .37 .93 1.07 1.23 1.8 1.7 .031 99 /\n"
                        + "2 'GENCLS' '1' 99999 0 /\n");
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt3g2.raw").toString(), dyr.toString());
        DStabGen gen = (DStabGen) context.getDStabilityNet().getBus("Bus1")
                .getContributeGen("1");
        assertTrue(!(gen.getDynamicGenDevice() instanceof Wt4g1Model));
        assertThrows(IllegalArgumentException.class, () -> new Wt4g1Data(
                -.01, .02, .4, .9, 1, 1.2, 2, 5, .02));
        assertThrows(IllegalArgumentException.class, () -> new Wt4g1Data(
                .01, .02, .9, .4, 1, 1.2, 2, 5, .02));
    }

    private static Fixture load() throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt3g2.raw").toString(),
                CASE.resolve("SMIB_v33_wt4g1.dyr").toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        DStabGen gen = (DStabGen) network.getBus("Bus1").getContributeGen("1");
        Wt4g1Model model = assertInstanceOf(Wt4g1Model.class, gen.getDynamicGenDevice());
        algorithm.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(.0005);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.initialization());
        return new Fixture(network, model);
    }

    private record Fixture(com.interpss.dstab.BaseDStabNetwork<?, ?> network,
            Wt4g1Model model) { }
}
