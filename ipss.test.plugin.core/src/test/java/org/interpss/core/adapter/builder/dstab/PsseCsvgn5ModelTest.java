package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.svc.Csvgn5Data;
import org.interpss.dstab.svc.Csvgn5Model;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuMethod;

public class PsseCsvgn5ModelTest extends CorePluginTestSetup {
    private static final double TOL = 1.0e-10;

    @Test
    void parserRetainsPublishedIconAndFourteenConstants(@TempDir Path tempDir)
            throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        Path dyr = tempDir.resolve("csvgn5.dyr");
        Files.writeString(dyr, "1 'CSVGN5' '1' 1 .017 .083 .013 .071 .019 .097 "
                + "73 2.4 1.41 1.23 -.51 -.62 .043 .061 /\n");

        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        DStabGen gen = (DStabGen) builder.getDStabNetwork().getDStabBus("Bus1")
                .getContributeGen("1");
        Csvgn5Model model = assertInstanceOf(Csvgn5Model.class,
                gen.getDynamicGenDevice());

        assertEquals(data(1), model.getData());
        assertEquals("Bus1", model.getRemoteBus().getId());
        assertEquals(15, DynamicModelCatalog.find("CSVGN5").orElseThrow().parameterCount());
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void initializationUsesPsseLeadLagCoordinatesAndMachineBase() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        DStabGen gen = (DStabGen) builder.getDStabNetwork().getDStabBus("Bus1")
                .getContributeGen("1");
        gen.setMvaBase(50.0);
        gen.setGen(new Complex(0.0, 0.10));
        Csvgn5Model model = builder.addCsvgn5("Bus1", "1", data(1));

        assertTrue(model.initStates(model.getDStabBus()));
        double localVoltage = model.getDStabBus().getVoltageMag();
        double deviceB = .10 * 100.0 / 50.0 / (localVoltage * localVoltage);
        double input = deviceB / 73.0;
        assertEquals(input * (1.0 - .013 / .071),
                model.getFirstRegulatorState(), TOL);
        assertEquals(input * (1.0 - .019 / .097),
                model.getSecondRegulatorState(), TOL);
        assertEquals(.10 / (localVoltage * localVoltage),
                model.getSystemBaseSusceptance(), TOL);
        assertEquals(model.getFirstRegulatorState(),
                model.getNamedState("First regulator state"), TOL);
    }

    @Test
    void fastOverrideAndThyristorLimitFollowPublishedBoundaries() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
        DStabGen gen = (DStabGen) builder.getDStabNetwork().getDStabBus("Bus1")
                .getContributeGen("1");
        gen.setGen(new Complex(0.0, 0.10));
        Csvgn5Model model = builder.addCsvgn5("Bus1", "1", data(1));
        assertTrue(model.initStates(model.getDStabBus()));
        model.getRemoteBus().setVoltage(new Complex(.70, 0.0));

        for (int index = 0; index < 300; index++) step(model, .0005);

        assertEquals(1.41, model.getThyristorDelay(), 1.0e-8);
        assertEquals(1.41, model.getNamedState("Thyristor delay"), 1.0e-8);
    }

    @Test
    void invalidDenominatorsAndInvertedLimitsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Csvgn5Data(
                1, 0, .1, 0, 0, 0, 0, 10, 0, 1, 1, -1, -1, .1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Csvgn5Data(
                1, 0, .1, 0, .1, 0, 0, 10, 0, -1, 1, -1, 1, .1, 0));
    }

    private static Csvgn5Data data(int remoteBus) {
        return new Csvgn5Data(remoteBus, .017, .083, .013, .071, .019, .097,
                73.0, 2.4, 1.41, 1.23, -.51, -.62, .043, .061);
    }

    private static void step(Csvgn5Model model, double dt) {
        assertTrue(model.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, 0));
        assertTrue(model.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, 1));
    }
}
