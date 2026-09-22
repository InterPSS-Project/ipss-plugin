package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.IpssCorePlugin;
import org.interpss.dstab.mach.Wt3e1Model;
import org.interpss.dstab.mach.Wt3g2Data;
import org.interpss.dstab.mach.Wt3g2Model;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** Exact schema, composition, limits, and named-state checks for WT3G2. */
public class PsseWt3g2ModelTest {
    private static final Path CASE = Path.of("testData", "adpter", "psse", "v33", "SMIB");

    @Test
    void exactNativeRecordAttachesWithWt3e1AndInitializes() throws Exception {
        Fixture fixture = load();
        Wt3g2Model model = fixture.model;
        assertEquals(new Wt3g2Data(50, .018, .023, 20, .55, .12, 2,
                .48, .88, 1.15, 1.18, 2.10, 4.70, .025), model.getData());
        assertEquals(14, DynamicModelCatalog.find("WT3G2").orElseThrow().parameterCount());
        assertEquals(5, model.getNamedStates().size());
        assertInstanceOf(Wt3e1Model.class, model.getElectricalController());
        assertEquals(fixture.network.getBus("Bus1").getVoltage().getArgument(),
                model.getPllAngleState(), 1.0e-12);
        assertEquals(.5, model.getP(), 2.0e-6);
        assertEquals(100.0, model.getAggregateRatedMw(), 1.0e-12);
    }

    @Test
    void lvplAndHvrcUsePublishedPiecewiseBoundaries() throws Exception {
        Fixture fixture = load();
        Wt3g2Model model = fixture.model;
        assertEquals(0.0, model.lowVoltagePowerLimit(.47), 0.0);
        assertEquals(1.15 * .50, model.lowVoltagePowerLimit(.68), 1.0e-12);
        assertEquals(1.15, model.lowVoltagePowerLimit(.95), 1.0e-12);
        assertEquals(0.0, Wt3g2Model.fixedLowVoltageActiveGain(.4), 0.0);
        assertEquals(.5, Wt3g2Model.fixedLowVoltageActiveGain(.6), 1.0e-12);
        assertEquals(1.0, Wt3g2Model.fixedLowVoltageActiveGain(.8), 0.0);
        assertEquals(0.0, Wt3g2Model.highVoltageCorrection(1.17, 1.18, 2.1), 0.0);
        assertEquals(.084, Wt3g2Model.highVoltageCorrection(1.22, 1.18, 2.1), 1.0e-12);
        assertEquals(4.7, Wt3g2Model.directionalRecoveryRate(.2, 10.0, 4.7), 0.0);
        assertEquals(-10.0, Wt3g2Model.directionalRecoveryRate(.2, -10.0, 4.7), 0.0);
        assertEquals(10.0, Wt3g2Model.directionalRecoveryRate(-.2, 10.0, 4.7), 0.0);
        assertEquals(-4.7, Wt3g2Model.directionalRecoveryRate(-.2, -10.0, 4.7), 0.0);

        double initialIp = model.getActiveCurrentState();
        model.setCommands(initialIp + 1.0, model.getInternalVoltageState());
        assertTrue(model.nextStep(.001, DynamicSimuMethod.MODIFIED_EULER, 0));
        assertTrue(model.nextStep(.001, DynamicSimuMethod.MODIFIED_EULER, 1));
        assertTrue(model.getActiveCurrentState() - initialIp <= .0047 + 1.0e-12,
                "RIp_LVPL is the upward recovery-rate boundary");
    }

    @Test
    void rejectsExtraFieldAndInvalidBoundaries(@TempDir Path tempDir) throws Exception {
        Path dyr = tempDir.resolve("extra.dyr");
        Files.writeString(dyr,
                "1 'WT3G2' '1' 50 .018 .023 20 .55 .12 2 .48 .88 1.15 1.18 2.1 4.7 .025 99 /\n"
                        + "2 'GENCLS' '1' 99999 0 /\n");
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt3g2.raw").toString(), dyr.toString());
        DStabGen gen = (DStabGen) context.getDStabilityNet().getBus("Bus1")
                .getContributeGen("1");
        assertTrue(!(gen.getDynamicGenDevice() instanceof Wt3g2Model));
        assertThrows(IllegalArgumentException.class, () -> new Wt3g2Data(
                0, .02, .02, 1, 1, .1, 2, .4, .9, 2, 1.2, 2, 5, .02));
        assertThrows(IllegalArgumentException.class, () -> new Wt3g2Data(
                10, .02, .02, 1, 1, .1, 2, .9, .4, 2, 1.2, 2, 5, .02));
    }

    private static Fixture load() throws Exception {
        IpssCorePlugin.init();
        var context = new PSSEMultiFileLoader().loadDStab(
                CASE.resolve("SMIB_v33_wt3g2.raw").toString(),
                CASE.resolve("SMIB_v33_wt3g2.dyr").toString());
        var network = context.getDStabilityNet();
        var algorithm = context.getDynSimuAlgorithm();
        DStabGen gen = (DStabGen) network.getBus("Bus1").getContributeGen("1");
        Wt3g2Model model = assertInstanceOf(Wt3g2Model.class, gen.getDynamicGenDevice());
        algorithm.getAclfAlgorithm().getDataCheckConfig().setAllowGenWithoutMachine(true);
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(.0005);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.initialization());
        return new Fixture(network, model);
    }

    private record Fixture(com.interpss.dstab.BaseDStabNetwork<?, ?> network,
            Wt3g2Model model) { }
}
