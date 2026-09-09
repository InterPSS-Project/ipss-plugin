package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.renewable.Regfma1Data;
import org.interpss.dstab.renewable.Regfma1Model;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.net.OriginalDataFormat;

public class PsseRegfma1ModelTest extends CorePluginTestSetup {

    @Test
    void directParserMapsTheNineteenParameterPsseRecord(@TempDir Path tempDir) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        Path dyr = tempDir.resolve("regfma1.dyr");
        Files.writeString(dyr,
                "1 'REGFMA1' 1 0 .02 .03 .04 2 1.2 0 1 0 1 -1 .01 .05 .01 .1 3 20 0 6 /\n");

        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());

        DStabGen gen = (DStabGen) builder.getDStabNetwork().getDStabBus("Bus1")
                .getContributeGen("1");
        Regfma1Model model = assertInstanceOf(Regfma1Model.class, gen.getDynamicGenDevice());
        assertEquals(new Regfma1Data(0, .02, .03, .04, 2, 1.2, 0, 1, 0, 1, -1,
                .01, .05, .01, .1, 3, 20, 0, 6), model.getData());
        assertEquals(.02, model.getData().tpf(), 1.0e-12);
        assertEquals(.03, model.getData().tqf(), 1.0e-12);
        assertEquals(.04, model.getData().tvf(), 1.0e-12);
        assertEquals(0, model.getData().vflag());
        assertEquals(0, model.getData().qvflag());
        assertEquals(.15, gen.getPosGenZ().getImaginary(), 1.0e-12);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void powerWorldValidationRulesAreAppliedAtTheDataBoundary() {
        Regfma1Data corrected = new Regfma1Data(0, 0, 0, 0, 0, 0,
                0, 1.2, -1, 1, -2, 2, .01, .05, 0, 6,
                .01, .1, 3, 20, 0, 0);
        assertEquals(.0001, corrected.xe(), 0.0);
        assertEquals(1.2, corrected.emax(), 0.0);
        assertEquals(0.0, corrected.emin(), 0.0);
        assertEquals(1.0, corrected.pmax(), 0.0);
        assertEquals(-1.0, corrected.pmin(), 0.0);
        assertEquals(2.0, corrected.qmax(), 0.0);
        assertEquals(-2.0, corrected.qmin(), 0.0);
    }

    @Test
    void severeVoltageDepressionActivatesTheAlgebraicFaultCurrentLimit() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        Regfma1Model model = builder.addRegfma1("Bus1", "1",
                new Regfma1Data(0, .02, .02, .02, 1.25, 1.2, 0, 1, 0, 1, -1,
                        .01, .05, .01, .1, 3, 20, 0, 6));
        assertTrue(model.initStates(model.getDStabBus()));

        model.getDStabBus().setVoltage(new Complex(.1, 0));
        Complex norton = (Complex) model.getOutputObject();
        Complex z = model.getParentGen().getPosGenZ()
                .multiply(model.getParentGen().getZMultiFactor());
        Complex terminalCurrent = norton.subtract(model.getDStabBus().getVoltage().divide(z));

        assertTrue(model.isCurrentLimited());
        assertEquals(1.25, terminalCurrent.abs(), 1.0e-10);

        model.getDStabBus().setVoltage(new Complex(1.04, 0));
        model.getOutputObject();
        assertEquals(false, model.isCurrentLimited());
    }

    @Test
    void directVoltageModeAppliesPlantPowerAndReactiveOffsets() throws Exception {
        Regfma1Model model = model(new Regfma1Data(0, 0, 0, 0, .15, 0,
                2, 0, 2, -2, 2, -2, .1, .2, 0, 1,
                0, 0, 0, 0, 0, 0));
        double initialVoltage = model.getInternalVoltage();

        model.setReferenceOffsets(.2, .1);
        assertTrue(model.nextStep(.1,
                com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER, 0));

        assertEquals(1.02, model.getSpeed(), 1.0e-12);
        assertEquals(initialVoltage + .1, model.getInternalVoltage(), 1.0e-12);
    }

    @Test
    void piVoltageModeIntegratesThePlantVoltageOffset() throws Exception {
        Regfma1Model model = model(new Regfma1Data(0, 0, 0, 0, .15, 0,
                2, 0, 2, -2, 2, -2, 0, 0, 2, 3,
                0, 0, 0, 0, 1, 1));
        double initialVoltage = model.getInternalVoltage();

        model.setReferenceOffsets(0, .1);
        assertTrue(model.nextStep(.1,
                com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER, 0));

        assertEquals(initialVoltage + .03, model.getVoltageIntegral(), 1.0e-12);
        assertEquals(initialVoltage + .23, model.getInternalVoltage(), 1.0e-12);
    }

    @Test
    void activeAndReactiveLimitControllersOpposeLimitViolations() throws Exception {
        Regfma1Model model = model(new Regfma1Data(0, 0, 0, 0, .15, 0,
                2, 0, .5, -2, .1, -2, .1, 0, 0, 1,
                1, 2, 1, 2, 0, 0));
        double initialVoltage = model.getInternalVoltage();

        assertTrue(model.nextStep(.1,
                com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER, 0));

        assertTrue(model.getActiveUpperLimitIntegral() < 0.0);
        assertTrue(model.getReactiveUpperLimitIntegral() < 0.0);
        assertTrue(model.getSpeed() < 1.0);
        assertTrue(model.getInternalVoltage() < initialVoltage);
    }

    @Test
    void measurementFiltersUseBothModifiedEulerStages() throws Exception {
        Regfma1Model model = model(new Regfma1Data(.1, .1, .1, 0, .15, 0,
                2, 0, 2, -2, 2, -2, 0, 0, 0, 1,
                0, 0, 0, 0, 0, 0));
        double oldP = model.getMeasuredActivePower();
        double oldQ = model.getMeasuredReactivePower();
        double oldV = model.getMeasuredVoltage();
        model.getDStabBus().setVoltage(new Complex(.9, 0));
        model.getOutputObject();
        double endpointP0 = model.getActivePower();
        double endpointQ0 = model.getReactivePower();

        double dt = .05;
        assertTrue(model.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, 0));
        double predictedP = oldP + dt * (endpointP0 - oldP) / .1;
        double predictedQ = oldQ + dt * (endpointQ0 - oldQ) / .1;
        double predictedV = oldV + dt * (.9 - oldV) / .1;
        assertEquals(predictedP, model.getMeasuredActivePower(), 1.0e-12);
        assertEquals(predictedQ, model.getMeasuredReactivePower(), 1.0e-12);
        assertEquals(predictedV, model.getMeasuredVoltage(), 1.0e-12);

        model.getOutputObject();
        double endpointP1 = model.getActivePower();
        double endpointQ1 = model.getReactivePower();
        assertTrue(model.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, 1));
        assertEquals(oldP + .5 * dt * ((endpointP0 - oldP) / .1
                        + (endpointP1 - predictedP) / .1),
                model.getMeasuredActivePower(), 1.0e-12);
        assertEquals(oldQ + .5 * dt * ((endpointQ0 - oldQ) / .1
                        + (endpointQ1 - predictedQ) / .1),
                model.getMeasuredReactivePower(), 1.0e-12);
        assertEquals(oldV + .5 * dt * ((.9 - oldV) / .1
                        + (.9 - predictedV) / .1),
                model.getMeasuredVoltage(), 1.0e-12);
    }

    @Test
    void networkPowerFeedbackIsConvertedToConverterMvaBase() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        DStabGen gen = (DStabGen) builder.getDStabNetwork().getDStabBus("Bus1")
                .getContributeGen("1");
        Complex sourcePower = gen.getGen();
        gen.setMvaBase(50.0);
        gen.setZMultiFactor(2.0);
        Regfma1Model model = builder.addRegfma1("Bus1", "1",
                new Regfma1Data(0, .02, .02, .02, 0, 1.2, 0,
                        2, -2, 2, -2, 0, 0, 0, 1,
                        0, 0, 0, 1));
        assertTrue(model.initStates(model.getDStabBus()));

        model.getOutputObject();

        assertEquals(2.0 * sourcePower.getReal(), model.getActivePower(), 1.0e-10);
        assertEquals(2.0 * sourcePower.getImaginary(), model.getReactivePower(), 1.0e-10);
    }

    @Test
    void repca1AttachesToRegfma1IndependentOfDyrRecordOrder(@TempDir Path tempDir)
            throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        Path dyr = tempDir.resolve("regfma1-repca1.dyr");
        Files.writeString(dyr,
                "1 'REPCA1' 1 0 0 0 0 1 1 1 .02 4 2.2 0 1.52 .7 0 0 1 1 -1 "
                + "0 0 1 -1 .3 .45 .02 0 0 1 -1 2 0 .1 20 20 /\n"
                + "1 'REGFMA1' 1 0 .02 .02 .02 2 1.2 0 1 0 1 -1 "
                + ".01 .05 .01 .1 3 20 0 6 /\n");

        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());

        DStabGen gen = (DStabGen) builder.getDStabNetwork().getDStabBus("Bus1")
                .getContributeGen("1");
        Regfma1Model model = assertInstanceOf(Regfma1Model.class, gen.getDynamicGenDevice());
        assertNotNull(model.getPlantController());
        assertEquals(2, parser.getLastImportReport().totalRecordCount());
        assertTrue(parser.getLastImportReport().isStrictlyComplete());

        assertTrue(model.initStates(model.getDStabBus()));
        model.getDStabBus().setFreq(.99);
        assertTrue(model.nextStep(.01,
                com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER, 0));
        assertTrue(model.getPlantController().getPref() > 0.0);
        assertTrue(model.getSpeed() > 1.0);
    }

    @Test
    void twoBusGfmSurvivesAndRecoversFromAThreeCycleTerminalFault() throws Exception {
        DStabilityNetwork network = DStabObjectFactory.createDStabilityNetwork();
        network.setBaseKva(100000.0);
        AclfNetworkBuilder topology = new AclfNetworkBuilder(network);
        topology.setNetworkInfo("regfma1-fault", "regfma1-fault", 100000.0,
                OriginalDataFormat.PSSE);
        topology.addBus("Gfm", "GFM", 1L, 230000.0, 1.0, 0.0,
                null, null, null);
        topology.setPVBus("Gfm", .5, 1.0, 1.0, -1.0, true);
        topology.addContributeGen("Gfm", "1", true, .5, 0, 100, 1.0,
                1, -1, 1, -1, new Complex(0, .15), null, 0, null, 0, 0);
        topology.addBus("Grid", "Infinite grid", 2L, 230000.0, 1.0, 0.0,
                null, null, null);
        topology.setSwingBus("Grid", 1.0, 0.0);
        topology.addContributeGen("Grid", "1", true, 0, 0, 100, 1.0,
                0, 0, 0, 0, new Complex(0, .01), null, 0, null, 0, 0);
        topology.addLine("Gfm", "Grid", "1", new Complex(.01, .2), Complex.ZERO,
                null, null, 0, 0, 0, true);

        DStabNetworkBuilder dynamics = new DStabNetworkBuilder(network);
        Regfma1Model model = dynamics.addRegfma1("Gfm", "1",
                new Regfma1Data(0, .02, .02, .02, 1.25, 1.2, 0,
                        1, 0, 1, -1, .01, .05, .01, .1, 3, 20, 0, 6));
        dynamics.addInfiniteMachine("Grid", "1");

        DynamicSimuAlgorithm algorithm = DStabObjectFactory.createDynamicSimuAlgorithm(network);
        algorithm.setSimuMethod(com.interpss.dstab.algo.DynamicSimuMethod.MODIFIED_EULER);
        algorithm.setSimuStepSec(1.0 / 240.0);
        algorithm.setTotalSimuTimeSec(.3);
        algorithm.setOutPutPerSteps(1);
        StateMonitor monitor = new StateMonitor();
        monitor.addBusStdMonitor(new String[] {"Gfm"});
        algorithm.setSimuOutputHandler(monitor);

        assertTrue(algorithm.getAclfAlgorithm().loadflow());
        network.addDynamicEvent(DStabObjectFactory.createBusFaultEvent(
                "Gfm", network, SimpleFaultCode.GROUND_3P,
                new Complex(0, 1.0e-4), null, .05, .05), "ThreeCycleFault@Gfm");
        assertTrue(algorithm.initialization());
        assertTrue(algorithm.performSimulation());

        var voltage = monitor.getBusVoltTable().get("Gfm");
        double minimum = voltage.values().stream().mapToDouble(value -> value.value)
                .min().orElseThrow();
        double finalVoltage = voltage.get(voltage.size() - 1).value;
        assertTrue(minimum < .2, "terminal fault must depress GFM voltage");
        assertTrue(finalVoltage > .9, "GFM voltage must recover after clearing");
        assertTrue(Double.isFinite(model.getSpeed()));
        assertTrue(Double.isFinite(model.getInternalVoltage()));
    }

    private static Regfma1Model model(Regfma1Data data) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        Regfma1Model model = builder.addRegfma1("Bus1", "1", data);
        assertTrue(model.initStates(model.getDStabBus()));
        return model;
    }
}
