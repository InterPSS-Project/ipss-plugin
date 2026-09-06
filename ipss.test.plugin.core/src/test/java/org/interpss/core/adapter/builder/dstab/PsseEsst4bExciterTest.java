package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.ieee.y2005.st4b.IEEE2005ST4BExciter;
import org.interpss.dstab.control.exc.ieee.y2005.st4b.IEEE2005ST4BExciterData;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.mach.Machine;
import com.interpss.core.algo.LoadflowAlgorithm;

public class PsseEsst4bExciterTest extends CorePluginTestSetup {
    private static final double TOL = 1.0e-9;

    @Test
    void directParserMapsAllSeventeenPsseParameters(@TempDir Path tempDir) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("esst4b.dyr");
        Files.writeString(dyr,
                "1 'ESST4B' 1 0 6.46 6.45 1 -0.87 0.01667 1 0 99 -99 "
                        + "0.2 5.47 0.3 11.63 0.12 0.04 15 /\n");

        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        IEEE2005ST4BExciter exciter = (IEEE2005ST4BExciter) builder.getDStabNetwork()
                .getMachine("Bus1-mach1").getExciter();
        assertNotNull(exciter);
        IEEE2005ST4BExciterData d = exciter.getData();
        assertEquals(0, d.getTr(), TOL); assertEquals(6.46, d.getKpr(), TOL);
        assertEquals(6.45, d.getKir(), TOL); assertEquals(1, d.getVrmax(), TOL);
        assertEquals(-.87, d.getVrmin(), TOL); assertEquals(.01667, d.getTa(), TOL);
        assertEquals(1, d.getKpm(), TOL); assertEquals(0, d.getKim(), TOL);
        assertEquals(99, d.getVmmax(), TOL); assertEquals(-99, d.getVmmin(), TOL);
        assertEquals(.2, d.getKg(), TOL); assertEquals(5.47, d.getKp(), TOL);
        assertEquals(.3, d.getKi(), TOL); assertEquals(11.63, d.getVbmax(), TOL);
        assertEquals(.12, d.getKc(), TOL); assertEquals(.04, d.getXl(), TOL);
        assertEquals(15, d.getAngKp(), TOL);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void builderAttachesAndCmlInitializesAtSolvedFieldVoltage() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        LoadflowAlgorithm loadflow = com.interpss.core.LoadflowAlgoObjectFactory
                .createLoadflowAlgorithm(builder.getDStabNetwork());
        assertTrue(loadflow.loadflow());
        assertTrue(machine.initStates(builder.getDStabNetwork().getDStabBus("Bus1")));
        IEEE2005ST4BExciterData data = texasData();
        IEEE2005ST4BExciter exciter = builder.addExcEsst4b("Bus1", "1", data);

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

    @Test
    void zeroGainPairsUseDocumentedRuntimeDefaultsWithoutMutatingSourceData() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        LoadflowAlgorithm loadflow = com.interpss.core.LoadflowAlgoObjectFactory
                .createLoadflowAlgorithm(builder.getDStabNetwork());
        assertTrue(loadflow.loadflow());
        assertTrue(machine.initStates(builder.getDStabNetwork().getDStabBus("Bus1")));
        IEEE2005ST4BExciterData data = texasData();
        data.setKpr(0.0);
        data.setKir(0.0);
        data.setKpm(0.0);
        data.setKim(0.0);
        IEEE2005ST4BExciter exciter = builder.addExcEsst4b("Bus1", "1", data);

        assertTrue(exciter.initStates(machine.getDStabBus(), machine));
        assertEquals(40.0, exciter.Kpr, TOL);
        assertEquals(1.0, exciter.Kpm, TOL);
        assertEquals(0.0, data.getKpr(), TOL);
        assertEquals(0.0, data.getKpm(), TOL);
        assertEquals(machine.getEfd(), exciter.getOutput(machine), 1.0e-8);
    }

    private static IEEE2005ST4BExciterData texasData() {
        IEEE2005ST4BExciterData d = new IEEE2005ST4BExciterData();
        d.setTr(0); d.setKpr(6.46); d.setKir(6.46); d.setVrmax(1); d.setVrmin(-.87);
        d.setTa(.01667); d.setKpm(1); d.setKim(0); d.setVmmax(99); d.setVmmin(-99);
        d.setKg(0); d.setKp(5.47); d.setKi(0); d.setVbmax(11.63); d.setKc(.12);
        d.setXl(0); d.setAngKp(0);
        return d;
    }
}
