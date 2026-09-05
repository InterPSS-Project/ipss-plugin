package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.pss.ieee.y1992.pss2a.Ieee1992PSS2AStabilizer;
import org.interpss.dstab.control.pss.ieee.y1992.pss1a.Ieee1992PSS1AStabilizer;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelImportStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.common.exp.InterpssException;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.algo.DynamicSimuMethod;

class DStabNetworkBuilderStabilizerTest extends CorePluginTestSetup {
    private static final double TOL = 1.0e-9;

    @TempDir
    Path tempDir;

    @Test
    void parsePss2a_mapsTexas2kRecordAndAttachesRuntimeModel() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("pss2a.dyr");
        Files.writeString(dyr, "1 'PSS2A' '1' 1 0 3 0 5 1 "
                + "10 10 0 10 0 10 1.47 1 0.5 0.1 2.4 "
                + "0.16 0.02 0.16 0.02 0.1 -0.1 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);

        parser.parseDynFile(dyr.toString());

        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        Ieee1992PSS2AStabilizer pss = (Ieee1992PSS2AStabilizer) machine.getStabilizer();
        assertNotNull(pss);
        assertSame(machine, pss.getMachine());
        var data = pss.getData();
        assertEquals(1, data.getIcs1());
        assertEquals(0, data.getRemoteBus1());
        assertEquals(3, data.getIcs2());
        assertEquals(0, data.getRemoteBus2());
        assertEquals(5, data.getM());
        assertEquals(1, data.getN());
        assertEquals(10.0, data.getTw1(), TOL);
        assertEquals(10.0, data.getTw2(), TOL);
        assertEquals(0.0, data.getT6(), TOL);
        assertEquals(1.47, data.getKs2(), TOL);
        assertEquals(1.0, data.getKs3(), TOL);
        assertEquals(0.5, data.getT8(), TOL);
        assertEquals(0.1, data.getT9(), TOL);
        assertEquals(2.4, data.getKs1(), TOL);
        assertEquals(0.16, data.getT1(), TOL);
        assertEquals(0.02, data.getT2(), TOL);
        assertEquals(0.16, data.getT3(), TOL);
        assertEquals(0.02, data.getT4(), TOL);
        assertEquals(0.1, data.getVstmax(), TOL);
        assertEquals(-0.1, data.getVstmin(), TOL);
        assertTrue(pss.initStates(machine.getDStabBus(), machine));
        assertEquals(0.0, pss.getOutput(machine), TOL);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void strictPss2aImportRejectsUnimplementedSignalSelector() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("pss2a-remote.dyr");
        Files.writeString(dyr, "1 'PSS2A' '1' 2 5 3 0 5 1 "
                + "10 10 0 10 0 10 1.47 1 0.5 0.1 2.4 "
                + "0.16 0.02 0.16 0.02 0.1 -0.1 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);

        assertThrows(InterpssException.class, () -> parser.parseDynFile(dyr.toString()));

        assertEquals(1, parser.getLastImportReport().count(DynamicModelImportStatus.REJECTED));
    }

    @Test
    void parsePss1a_mapsAllParametersAndRunsTheRepairedCmlChain() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("pss1a.dyr");
        Files.writeString(dyr, "1 'PSS1A' '1' 1 0.061 0.0017 "
                + "0.30 0.03 0.30 0.03 10.0 0.05 5.0 0.05 -0.05 0 0 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);

        parser.parseDynFile(dyr.toString());

        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        Ieee1992PSS1AStabilizer pss = (Ieee1992PSS1AStabilizer) machine.getStabilizer();
        assertNotNull(pss);
        assertSame(machine, pss.getMachine());
        var data = pss.getData();
        assertEquals(1, data.getIcs());
        assertEquals(0.061, data.getA1(), TOL);
        assertEquals(0.0017, data.getA2(), TOL);
        assertEquals(0.30, data.getT1(), TOL);
        assertEquals(0.03, data.getT2(), TOL);
        assertEquals(0.30, data.getT3(), TOL);
        assertEquals(0.03, data.getT4(), TOL);
        assertEquals(10.0, data.getT5(), TOL);
        assertEquals(0.05, data.getT6(), TOL);
        assertEquals(5.0, data.getKs(), TOL);
        assertEquals(0.05, data.getVstmax(), TOL);
        assertEquals(-0.05, data.getVstmin(), TOL);
        assertEquals(0.0, data.getVcu(), TOL);
        assertEquals(0.0, data.getVcl(), TOL);
        assertTrue(pss.initStates(machine.getDStabBus(), machine));
        assertEquals(1.0, pss.speedGain, TOL);
        assertEquals(0.0, pss.peGain, TOL);
        // Ks belongs only to the washout. The lead/lag blocks have unity gain.
        assertEquals(5.0, pss.ks, TOL);

        machine.setSpeed(1.01);
        for (int i = 0; i < 100; i++) {
            assertTrue(pss.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
            assertTrue(pss.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
        }
        double output = pss.getOutput(machine);
        assertTrue(output > 0.0);
        assertTrue(output <= data.getVstmax());
        pss.vcu = 0.5;
        assertEquals(0.0, pss.getOutput(machine), TOL);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void strictPss1aImportRejectsUnsupportedFrequencyDerivativeInput() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("pss1a-unsupported.dyr");
        Files.writeString(dyr, "1 'PSS1A' '1' 6 0.061 0.0017 "
                + "0.30 0.03 0.30 0.03 10.0 0.05 5.0 0.05 -0.05 0 0 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);

        assertThrows(InterpssException.class, () -> parser.parseDynFile(dyr.toString()));
        assertEquals(1, parser.getLastImportReport().count(DynamicModelImportStatus.UNSUPPORTED));
    }
}
