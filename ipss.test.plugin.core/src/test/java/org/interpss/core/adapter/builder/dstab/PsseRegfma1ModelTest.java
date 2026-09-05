package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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

class PsseRegfma1ModelTest extends CorePluginTestSetup {

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
        assertEquals(.04, gen.getPosGenZ().getImaginary(), 1.0e-12);
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
                new Regfma1Data(0, .02, .02, .15, 1.25, 1.2, 0, 1, 0, 1, -1,
                        .01, .05, .01, .1, 3, 20, 0, 6));
        assertTrue(model.initStates(model.getDStabBus()));

        model.getDStabBus().setVoltage(new Complex(.1, 0));
        Complex norton = (Complex) model.getOutputObject();
        Complex z = model.getParentGen().getPosGenZ()
                .multiply(model.getParentGen().getZMultiFactor());
        Complex terminalCurrent = norton.subtract(model.getDStabBus().getVoltage().divide(z));

        assertTrue(model.isCurrentLimited());
        assertEquals(1.25, terminalCurrent.abs(), 1.0e-10);
    }
}
