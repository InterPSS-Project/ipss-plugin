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
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.DStabGen;

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

    private static Regfma1Model model(Regfma1Data data) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        Regfma1Model model = builder.addRegfma1("Bus1", "1", data);
        assertTrue(model.initStates(model.getDStabBus()));
        return model;
    }
}
