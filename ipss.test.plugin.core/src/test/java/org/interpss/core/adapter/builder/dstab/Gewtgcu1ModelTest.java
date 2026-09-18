package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.IpssCorePlugin;
import org.interpss.dstab.mach.Gewtgcu1Data;
import org.interpss.dstab.mach.Gewtgcu1Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Exact allocation, initialization, curve, limit, and named-state checks. */
public class Gewtgcu1ModelTest {
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");

    @Test
    void exactNativeWrapperAttachesAndInitializes() throws Exception {
        Fixture fixture = load();
        Gewtgcu1Model model = fixture.model;
        assertEquals(data(0), model.getData());
        assertEquals(26, DynamicModelCatalog.find("GEWTGCU1").orElseThrow().parameterCount());
        assertEquals(3, model.getNamedStates().size());
        assertTrue(model.getNamedStates().containsKey("Converter lag for Eqcmd"));
        assertEquals(0.5, model.getP(), 2.0e-6);
    }

    @Test
    void publishedDfigCurvesAndRecoveryBoundaryAreApplied() throws Exception {
        Gewtgcu1Model model = load().model;
        assertEquals(0.0, model.dfigLvplLimit(0.50), 0.0);
        assertEquals(0.555, model.dfigLvplLimit(0.70), 1.0e-12);
        assertEquals(1.11, model.dfigLvplLimit(0.95), 1.0e-12);
        assertEquals(0.0, model.lowVoltageActiveGain(0.39), 0.0);
        assertEquals(0.65 / 0.90, model.lowVoltageActiveGain(0.65), 1.0e-12);
        assertEquals(1.0, model.lowVoltageActiveGain(0.95), 0.0);
        assertEquals(0.0, model.highVoltageReactiveCurrent(1.0), 0.0);
        assertEquals(0.5, model.highVoltageReactiveCurrent(1.1), 1.0e-12);
        assertEquals(1.0, model.highVoltageReactiveCurrent(1.3), 0.0);

        double initial = model.getActiveCurrentState();
        model.setCommands(initial + 1.0, model.getReactiveState());
        assertTrue(model.nextStep(0.001, DynamicSimuMethod.MODIFIED_EULER, 0));
        assertTrue(model.nextStep(0.001, DynamicSimuMethod.MODIFIED_EULER, 1));
        assertTrue(model.getActiveCurrentState() - initial <= 0.010000000001);
    }

    @Test
    void fullConverterUsesThreePointPowerCurveAndIqState() throws Exception {
        Gewtgcu1Data full = data(1);
        assertTrue(full.fullConverter());
        assertEquals(60.0, full.aggregateRatedMw(), 0.0);
        Fixture fixture = load();
        Gewtgcu1Model model = new Gewtgcu1Model(fixture.model.getParentGen(),
                fixture.model.getDStabBus(), "1", full);
        assertTrue(model.initStates(fixture.model.getDStabBus()));
        assertEquals(0.0, model.threePointPower(.4), 0.0);
        assertEquals(.275, model.threePointPower(.55), 1.0e-12);
        assertEquals(.775, model.threePointPower(.8), 1.0e-12);
        assertEquals(1.0, model.threePointPower(.95), 0.0);
        assertEquals(.775 / .8, model.activeCurrentLimit(.8, .8), 1.0e-12);
        assertTrue(model.getNamedStates().containsKey("Converter lag for Iqcmd"));
        assertThrows(IllegalArgumentException.class, () -> data(2));
        assertThrows(IllegalArgumentException.class, () -> new Gewtgcu1Data(
                40, 0, 1.5, .33403, .9, .5, 2.775, 1.2, 1,
                .4, .9, 10, .02, .4, 0, .7, .55, .9, 1, .1));
    }

    @Test
    void rejectsIncorrectAllocation(@TempDir Path tempDir) throws Exception {
        Path dyr = tempDir.resolve("invalid.dyr");
        Files.writeString(dyr,
                "1 'USRMDL' '1' 'GEWTGCU1' 101 1 2 18 3 4 40 0 "
                        + "1.5 .33403 .5 .9 2.775 1.2 1 .4 .9 10 .02 "
                        + ".4 0 .7 .55 .9 1 .1 /\n2 'GENCLS' '1' 99999 0 /\n");
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt2g1_psse36.raw").toString(), dyr.toString());
        DStabGen gen = (DStabGen) context.getDStabilityNet().getBus("Bus1")
                .getContributeGen("1");
        assertTrue(!(gen.getDynamicGenDevice() instanceof Gewtgcu1Model));
    }

    private static Gewtgcu1Data data(int fullConverterFlag) {
        return new Gewtgcu1Data(40, fullConverterFlag, 1.5, .33403, .5, .9, 2.775,
                1.2, 1.0, .4, .9, 10.0, .02, .4, 0, .7, .55, .9, 1, .1);
    }

    private static Fixture load() throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt2g1_psse36.raw").toString(),
                CASE.resolve("SMIB_v33_gewtgcu1_psse36.dyr").toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        DStabGen gen = (DStabGen) network.getBus("Bus1").getContributeGen("1");
        Gewtgcu1Model model = assertInstanceOf(Gewtgcu1Model.class,
                gen.getDynamicGenDevice());
        algorithm.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(.0005);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.initialization());
        return new Fixture(model);
    }

    private record Fixture(Gewtgcu1Model model) { }
}
