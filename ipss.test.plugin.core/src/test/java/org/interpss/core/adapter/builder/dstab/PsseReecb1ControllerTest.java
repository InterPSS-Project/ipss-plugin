package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.renewable.Reecb1Data;
import org.interpss.dstab.renewable.Reecb1Model;
import org.interpss.dstab.renewable.Regca1Data;
import org.interpss.dstab.renewable.Regca1Model;
import org.interpss.dstab.renewable.Repca1Data;
import org.interpss.dstab.renewable.Repca1Model;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.interpss.fadapter.psse.dyr.DynamicModelSupportStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;

/** PowerWorld/WECC and ANDES conformance tests for REECB1. */
public class PsseReecb1ControllerTest extends CorePluginTestSetup {
    private static final double DT = 1.0 / 960.0;
    private static final double TOL = 1.0e-10;

    @Test
    void directParserMapsRealWecc240RecordAndCatalogsModel(@TempDir Path tempDir)
            throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        Path dyr = tempDir.resolve("reecb1.dyr");
        Files.writeString(dyr,
                "1 'REGCA1' 1 1 .02 10 .9 .4 1.22 1.2 .9 .5 -1.3 .02 0 100 -100 .7 /\n"
                + "1 'REECB1' 1 0 0 1 0 0 -99 99 .02 0 0 0 1.1 -1.1 0 .05 "
                + ".44 -.44 1.05 .9 0 .01 10 60 .02 99 -99 1 0 1.11 .02 /\n");

        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        Reecb1Model model = controller(builder);

        assertEquals(new Reecb1Data(0, 0, 1, 0, 0,
                -99, 99, .02, 0, 0, 0, 1.1, -1.1, 0, .05,
                .44, -.44, 1.05, .9, 0, .01, 10, 60, .02,
                99, -99, 1, 0, 1.11, .02), model.getData());
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
        var descriptor = DynamicModelCatalog.find("REECB1").orElseThrow();
        assertEquals(30, descriptor.parameterCount());
        assertEquals(DynamicModelSupportStatus.LOADABLE, descriptor.supportStatus());
    }

    @Test
    void sixStatesFollowThePublishedDirectQAndActivePowerEquations() {
        Reecb1Model model = new Reecb1Model(data(0, 0, 1.0, .1, .1, .2), null);
        model.initialize(.8, .2, 1.0);
        model.step(DT, .4, .1, .9, 1.0);

        double expectedV = 1.0 + DT * (.9 - 1.0) / .1;
        double expectedP = .8 + DT * (.4 - .8) / .1;
        double expectedQ = .2 + DT * (.2 / expectedV - .2) / .1;
        assertEquals(expectedV, model.getMeasuredVoltage(), TOL);
        assertEquals(expectedP, model.getMeasuredActivePower(), TOL);
        assertEquals(.8, model.getActivePowerOrder(), TOL);
        assertEquals(expectedQ, model.getReactiveCurrentState(), TOL);
        assertEquals(.8 / expectedV, model.getIpcmd(), TOL);
        assertEquals(-expectedQ, model.getIqcmd(), TOL);
    }

    @Test
    void algebraicReactiveInjectionRemainsActiveOutsideVoltageDip() {
        Reecb1Data data = new Reecb1Data(0, 0, 0, 0, 1,
                .8, 1.2, 0, -.02, .02, 2, 1, -1, 1.1, 0,
                10, -10, 10, -10, 0, 0, 0, 0, 0,
                10, -10, 10, 0, 10, 0);
        Reecb1Model model = new Reecb1Model(data, null);
        model.initialize(.5, .2, 1.0);
        model.step(DT, .5, .2, 1.0, 1.0);

        assertTrue(!model.isVoltageDip());
        assertEquals(-.36, model.getIqcmd(), TOL);
    }

    @Test
    void voltageDipFreezesControlStatesButNotMeasurementStates() {
        Reecb1Model model = new Reecb1Model(data(0, 0, .8, .1, .1, .1), null);
        model.initialize(.8, .2, 1.0);
        double pOrder = model.getActivePowerOrder();
        double qState = model.getReactiveCurrentState();

        model.step(DT, .4, .1, .5, 1.0);

        assertTrue(model.isVoltageDip());
        assertTrue(model.getMeasuredVoltage() < 1.0);
        assertTrue(model.getMeasuredActivePower() < .8);
        assertEquals(pOrder, model.getActivePowerOrder(), TOL);
        assertEquals(qState, model.getReactiveCurrentState(), TOL);
    }

    @Test
    void powerOrderAppliesDerivativeRateLimitBeforePositionLimit() {
        Reecb1Data data = data(0, 0, 1.0, 0, 0, .2);
        Reecb1Model model = new Reecb1Model(data, null);
        model.setPlantController(frequencyPlantController());
        model.initialize(.8, .2, 1.0);

        model.step(DT, .8, .2, 1.0, .9);

        assertEquals(.8 + .05 * DT, model.getActivePowerOrder(), TOL);
    }

    @Test
    void appliesPowerWorldCorrectionsAndAndesDisabledImaxConvention() {
        Reecb1Data source = new Reecb1Data(0, 0, 0, 0, 0,
                .8, 1.2, .004, 0, 0, 0, -1, 1, 0, .015,
                -1, 1, -2, 2, 0, 0, 0, 0, .004,
                -.1, .1, -.5, .5, 0, .015);
        Reecb1Model model = new Reecb1Model(source, null);
        model.configureIntegrationStep(.01, 2.0);
        model.initialize(2.0, 1.0, 1.0);
        model.step(.01, 2.0, 1.0, 1.0, 1.0);

        assertEquals(0.0, model.getEffectiveTrv(), TOL);
        assertEquals(.02, model.getEffectiveTp(), TOL);
        assertEquals(0.0, model.getEffectiveTiq(), TOL);
        assertEquals(.02, model.getEffectiveTpord(), TOL);
        assertEquals(2.0, model.getIpcmd(), TOL);
        assertEquals(-1.0, model.getIqcmd(), TOL);
        assertEquals(.004, source.trv(), TOL, "source parameters must remain unchanged");
        assertTrue(model.getEffectivePmax() >= 2.0);
        assertTrue(model.getEffectivePmin() <= 2.0);
    }

    @Test
    void fullRegcaReecbRepcaStackInitializesAndRuns() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        Regca1Model converter = builder.addRegca1("Bus1", "1", regcaData());
        Reecb1Model electrical = builder.addReecb1("Bus1", "1",
                data(0, 0, 1.11, .02, .05, .02));
        Repca1Model plant = builder.addRepca1("Bus1", "1", plantData());
        assertNotNull(converter);
        assertNotNull(electrical);
        assertNotNull(plant);

        var algorithm = DStabObjectFactory.createDynamicSimuAlgorithm(builder.getDStabNetwork());
        algorithm.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(1.0 / 120.0);
        algorithm.setTotalSimuTimeSec(.05);
        algorithm.setSimuOutputHandler(new StateMonitor());
        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        assertTrue(algorithm.initialization());
        assertTrue(algorithm.performSimulation());
        assertTrue(Double.isFinite(electrical.getIpcmd()));
        assertTrue(Double.isFinite(electrical.getIqcmd()));
    }

    private static Reecb1Model controller(DStabNetworkBuilder builder) {
        DStabGen gen = (DStabGen) builder.getDStabNetwork().getDStabBus("Bus1")
                .getContributeGen("1");
        return ((Regca1Model) gen.getDynamicGenDevice()).getElectricalController();
    }

    private static Reecb1Data data(int pfFlag, int qFlag, double imax,
            double trv, double tiq, double tpord) {
        return new Reecb1Data(0, pfFlag, 1, qFlag, 1,
                .8, 1.2, trv, 0, 0, 0, 10, -10, 0, .1,
                10, -10, 10, -10, 0, .1, 0, .1, tiq,
                .05, -.05, 10, 0, imax, tpord);
    }

    private static Regca1Data regcaData() {
        return new Regca1Data(1, .02, 10, .9, .4, 1.22, 1.2, .9,
                .5, -1.3, .02, .7, 0, 0, .7);
    }

    private static Repca1Model frequencyPlantController() {
        return new Repca1Model(new Repca1Data(0, 0, 0, "0", 0, 0, 1,
                0, 0, 0, 0, 0, 0, 0, 0, 0,
                10, -10, -1, 1, 10, -10, 1, 0, 0,
                -.01, .01, 10, -10, 10, -10, 0, 0, 1, 1));
    }

    private static Repca1Data plantData() {
        return new Repca1Data(0, 0, 0, "0", 0, 0, 1,
                .02, 18, 5, 0, .05, 0, 0, 0, 0,
                .1, -.1, -1, 1, .43, -.43, 1, .05, .25,
                -1, 1, 99, -99, 1, 0, .1, 0, 0, 0);
    }
}
