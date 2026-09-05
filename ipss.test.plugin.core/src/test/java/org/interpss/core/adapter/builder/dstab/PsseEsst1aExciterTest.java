package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.ieee.y1981.st1.IEEE1981ST1Exciter;
import org.interpss.dstab.control.exc.ieee.y1981.st1.IEEE1981ST1ExciterData;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Machine;

class PsseEsst1aExciterTest extends CorePluginTestSetup {
    private static final double TOL = 1.0e-9;

    @Test
    void directParserMapsAllTwentyPsseParameters(@TempDir Path tempDir) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("esst1a.dyr");
        Files.writeString(dyr, "1 'ESST1A' 1 1 1 0 0.1 -0.1 1.22 4.4 1 4.44 "
                + "472 0 5 -5 5 -5 0.17 0.2 1.1 0.3 0.4 /\n");

        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        IEEE1981ST1Exciter exciter = (IEEE1981ST1Exciter) builder.getDStabNetwork()
                .getMachine("Bus1-mach1").getExciter();
        assertNotNull(exciter);
        IEEE1981ST1ExciterData d = exciter.getData();
        assertEquals(1, d.getUel()); assertEquals(1, d.getVos());
        assertEquals(0, d.getTr(), TOL); assertEquals(.1, d.getVimax(), TOL);
        assertEquals(-.1, d.getVimin(), TOL); assertEquals(1.22, d.getTc(), TOL);
        assertEquals(4.4, d.getTb(), TOL); assertEquals(1, d.getTc1(), TOL);
        assertEquals(4.44, d.getTb1(), TOL); assertEquals(472, d.getKa(), TOL);
        assertEquals(0, d.getTa(), TOL); assertEquals(5, d.getVamax(), TOL);
        assertEquals(-5, d.getVamin(), TOL); assertEquals(5, d.getVrmax(), TOL);
        assertEquals(-5, d.getVrmin(), TOL); assertEquals(.17, d.getKc(), TOL);
        assertEquals(.2, d.getKf(), TOL); assertEquals(1.1, d.getTf(), TOL);
        assertEquals(.3, d.getKlr(), TOL); assertEquals(.4, d.getIlr(), TOL);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void texasProfileInitializesAndRemainsAtSolvedFieldVoltage() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        LoadflowAlgorithm loadflow = com.interpss.core.LoadflowAlgoObjectFactory
                .createLoadflowAlgorithm(builder.getDStabNetwork());
        assertTrue(loadflow.loadflow());
        assertTrue(machine.initStates(builder.getDStabNetwork().getDStabBus("Bus1")));
        IEEE1981ST1Exciter exciter = builder.addExcEsst1a("Bus1", "1",
                1, 1, 0, .1, -.1, 1.22, 4.4, 1, 4.44,
                472, 0, 5, -5, 5, -5, .17, 0, 1, 0, 0);

        assertNotNull(exciter);
        assertSame(exciter, machine.getExciter());
        assertTrue(exciter.initStates(builder.getDStabNetwork().getDStabBus("Bus1"), machine));
        assertEquals(machine.getEfd(), exciter.getOutput(machine), 1.0e-8);
        for (int i = 0; i < 20; i++) {
            exciter.nextStep(.005, DynamicSimuMethod.MODIFIED_EULER, machine, 0);
            exciter.nextStep(.005, DynamicSimuMethod.MODIFIED_EULER, machine, 1);
        }
        assertEquals(machine.getEfd(), exciter.getOutput(machine), 1.0e-6);
    }
}
