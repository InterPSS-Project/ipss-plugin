package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.pss.ieee.y1992.pss2a.Ieee1992PSS2AStabilizer;
import org.interpss.dstab.control.pss.ieee.y1992.pss1a.Ieee1992PSS1AStabilizer;
import org.interpss.fadapter.builder.AclfNetworkBuilder;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelImportStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import org.apache.commons.math3.complex.Complex;
import com.interpss.common.exp.InterpssException;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.core.net.OriginalDataFormat;

public class DStabNetworkBuilderStabilizerTest extends CorePluginTestSetup {
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
    void strictPss2aImportRejectsMissingRemoteBus() throws Exception {
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
    void strictPss2aImportAttachesResolvableRemoteBusSignal() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        AclfNetworkBuilder topology = new AclfNetworkBuilder(builder.getDStabNetwork());
        topology.setNetworkInfo("ut", "ut", 100000.0, OriginalDataFormat.PSSE);
        topology.addBus("Bus2", "Remote", 2L, 16500.0, 0.97, 0.0, null, null, null);
        Path dyr = tempDir.resolve("pss2a-valid-remote.dyr");
        Files.writeString(dyr, "1 'PSS2A' '1' 2 2 3 0 5 1 "
                + "10 10 0 10 0 10 1.47 1 0.5 0.1 2.4 "
                + "0.16 0.02 0.16 0.02 0.1 -0.1 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);

        parser.parseDynFile(dyr.toString());

        assertTrue(parser.getLastImportReport().isStrictlyComplete());
        assertEquals(1, parser.getLastImportReport().count(DynamicModelImportStatus.ATTACHED));
    }

    @ParameterizedTest(name = "PSS2A input {0}, selector {1}")
    @CsvSource({
            "1,1", "1,2", "1,3", "1,4", "1,5", "1,6",
            "2,1", "2,2", "2,3", "2,4", "2,5", "2,6"
    })
    void pss2aSupportsEveryDocumentedLocalInputSelector(int input, int selector)
            throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        boolean first = input == 1;
        Ieee1992PSS2AStabilizer pss = builder.addPss2a(
                "Bus1", "1",
                first ? selector : 1, 0, first ? 1 : selector, 0,
                1, 1,
                0.20, first ? 0.20 : 0.0, 0.05,
                0.20, first ? 0.0 : 0.20, 0.05,
                1.0, 1.0, 0.10, 0.05, 2.0,
                0.05, 0.05, 0.05, 0.05, 1.0, -1.0);
        assertNotNull(pss);
        assertTrue(pss.initStates(machine.getDStabBus(), machine));
        assertEquals(0.0, pss.getOutput(machine), TOL);

        perturbSelectedSignal(selector, machine);
        double maxAbs = 0.0;
        for (int i = 0; i < 100; i++) {
            assertTrue(pss.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
            assertTrue(pss.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
            maxAbs = Math.max(maxAbs, Math.abs(pss.getOutput(machine)));
        }
        assertTrue(maxAbs > 1.0e-7, "Selected PSS2A input path produced no response");
        assertTrue(maxAbs <= 1.0 + TOL);
    }

    private static void perturbSelectedSignal(int selector, Machine machine) {
        switch (selector) {
            case 1 -> machine.setSpeed(machine.getSpeed() + 0.01);
            case 2 -> machine.getDStabBus().setFreq(machine.getDStabBus().getFreq() + 0.01);
            case 3 -> machine.setPe(machine.getPe() + 0.10);
            case 4 -> machine.setPm(machine.getPe() + 0.10);
            case 5, 6 -> machine.getDStabBus().setVoltage(
                    machine.getDStabBus().getVoltage().multiply(1.01));
            default -> throw new IllegalArgumentException("selector=" + selector);
        }
    }

    @Test
    void pss2aMapsAndAppliesPowerWorldOnlyOutputParameters() throws Exception {
        double lowKs4 = pss2aSecondInputResponse(1.0, 1.0, 0.0, 0.5);
        double highKs4 = pss2aSecondInputResponse(1.0, 1.0, 0.0, 2.0);

        assertTrue(lowKs4 < 0.0);
        assertEquals(4.0, highKs4 / lowKs4, 1.0e-6);

        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Ieee1992PSS2AStabilizer pss = builder.addPss2a(
                "Bus1", "1", 1, 0, 3, 0, 0, 0,
                0.2, 0.0, 0.0, 0.2, 0.0, 0.0,
                1.0, 0.0, 0.4, 0.1, 1.0,
                0.0, 0.0, 0.0, 0.0, 1.0, -1.0,
                0.75, 0.15, 0.05, 1.25);
        assertNotNull(pss);
        assertEquals(0.75, pss.getData().getA(), TOL);
        assertEquals(0.15, pss.getData().getTa(), TOL);
        assertEquals(0.05, pss.getData().getTb(), TOL);
        assertEquals(1.25, pss.getData().getKs4(), TOL);

        assertNull(builder.addPss2a(
                "Bus1", "1", 1, 0, 3, 0, 0, 0,
                0.2, 0.0, 0.0, 0.2, 0.0, 0.0,
                1.0, 0.0, 0.4, 0.1, 1.0,
                0.0, 0.0, 0.0, 0.0, 1.0, -1.0,
                1.0, 0.1, 0.0, 1.0));
    }

    @Test
    void pss2aAppliesKs1Ks2AndKs3AtPublishedLocations() throws Exception {
        double base = pss2aSecondInputResponse(1.0, 1.0, 1.0, 0.0);
        double highKs1 = pss2aSecondInputResponse(3.0, 1.0, 1.0, 0.0);
        double highKs2 = pss2aSecondInputResponse(1.0, 4.0, 1.0, 0.0);
        double highKs3 = pss2aSecondInputResponse(1.0, 1.0, 4.0, 0.0);

        assertTrue(base > 0.0);
        assertEquals(3.0, highKs1 / base, 1.0e-6,
                "Ks1 must scale the combined signal after both summing junctions");
        assertEquals(4.0, highKs2 / base, 1.0e-6,
                "Ks2 must scale input 2 before its Ks3/Ks4 branches");
        assertEquals(4.0, highKs3 / base, 1.0e-6,
                "Ks3 must scale input 2 into the pre-ramp summing junction");
    }

    private static double pss2aSecondInputResponse(
            double ks1, double ks2, double ks3, double ks4) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        Ieee1992PSS2AStabilizer pss = builder.addPss2a(
                "Bus1", "1", 1, 0, 3, 0, 0, 0,
                0.2, 0.0, 0.0, 0.2, 0.0, 0.0,
                ks2, ks3, 0.0, 0.1, ks1,
                0.0, 0.0, 0.0, 0.0, 10.0, -10.0,
                1.0, 0.0, 0.0, ks4);
        assertNotNull(pss);
        assertTrue(pss.initStates(machine.getDStabBus(), machine));

        machine.setPe(machine.getPe() + 0.1);
        for (int i = 0; i < 20; i++) {
            assertTrue(pss.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
            assertTrue(pss.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
        }
        return pss.getOutput(machine);
    }

    @ParameterizedTest(name = "PSS2A remote bus selector {0}")
    @CsvSource({"2", "5", "6"})
    void pss2aResolvesRemoteBusForBusBasedSignals(int selector) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        AclfNetworkBuilder topology = new AclfNetworkBuilder(builder.getDStabNetwork());
        topology.setNetworkInfo("ut", "ut", 100000.0, OriginalDataFormat.PSSE);
        topology.addBus("Bus2", "Remote", 2L, 16500.0, 0.97, 0.0, null, null, null);
        var remote = builder.getDStabNetwork().getDStabBus("Bus2");
        remote.setFreq(0.985);

        Ieee1992PSS2AStabilizer pss = builder.addPss2a(
                "Bus1", "1", selector, 2, 3, 999, 0, 0,
                0.2, 0.0, 0.0, 0.2, 0.0, 0.0,
                0.0, 0.0, 0.0, 0.1, 1.0,
                0.0, 0.0, 0.0, 0.0, 1.0, -1.0);
        assertNotNull(pss, "REMBUS is ignored for the local generator-power second input");
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        assertTrue(pss.initStates(machine.getDStabBus(), machine));

        if (selector == 2) {
            assertEquals(0.985, pss.input1Signal, TOL);
        } else if (selector == 5) {
            assertEquals(0.97, pss.input1Signal, TOL);
        } else {
            remote.setVoltage(new Complex(0.96, 0.0));
            assertTrue(pss.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
            assertEquals(-2.0, pss.input1Signal, TOL);
        }
    }

    @Test
    void pss2aRejectsMissingRemoteBusOnlyForBusBasedSignals() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();

        assertNull(builder.addPss2a(
                "Bus1", "1", 5, 999, 3, 0, 0, 0,
                0.2, 0.0, 0.0, 0.2, 0.0, 0.0,
                1.0, 0.0, 0.0, 0.1, 1.0,
                0.0, 0.0, 0.0, 0.0, 1.0, -1.0));

        assertNotNull(builder.addPss2a(
                "Bus1", "1", 1, 999, 3, 999, 0, 0,
                0.2, 0.0, 0.0, 0.2, 0.0, 0.0,
                1.0, 0.0, 0.0, 0.1, 1.0,
                0.0, 0.0, 0.0, 0.0, 1.0, -1.0));
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
    void parsePss1a_supportsBusFrequencyInput() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("pss1a-frequency.dyr");
        Files.writeString(dyr, "1 'PSS1A' '1' 2 0.061 0.0017 "
                + "0.30 0.03 0.30 0.03 10.0 0.05 5.0 0.05 -0.05 0 0 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);

        parser.parseDynFile(dyr.toString());

        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        Ieee1992PSS1AStabilizer pss = (Ieee1992PSS1AStabilizer) machine.getStabilizer();
        assertTrue(pss.initStates(machine.getDStabBus(), machine));
        assertEquals(1.0, pss.freqGain, TOL);
        machine.getDStabBus().setFreq(1.01);
        for (int i = 0; i < 100; i++) {
            assertTrue(pss.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
            assertTrue(pss.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
        }
        assertTrue(pss.getOutput(machine) > 0.0);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void parsePss1a_supportsFilteredBusVoltageDerivativeInput() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("pss1a-voltage-derivative.dyr");
        Files.writeString(dyr, "1 'PSS1A' '1' 6 0.061 0.0017 "
                + "0.30 0.03 0.30 0.03 10.0 0.05 5.0 0.05 -0.05 0 0 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);

        parser.parseDynFile(dyr.toString());

        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        Ieee1992PSS1AStabilizer pss = (Ieee1992PSS1AStabilizer) machine.getStabilizer();
        assertTrue(pss.initStates(machine.getDStabBus(), machine));
        assertEquals(1.0, pss.derivativeGain, TOL);
        assertEquals(20.0, pss.derivativeK, TOL);
        machine.getDStabBus().setVoltage(new Complex(1.01, 0.0));
        double maxAbsOutput = 0.0;
        for (int i = 0; i < 100; i++) {
            assertTrue(pss.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
            assertTrue(pss.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
            maxAbsOutput = Math.max(maxAbsOutput, Math.abs(pss.getOutput(machine)));
        }
        assertTrue(maxAbsOutput > 1.0e-6);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void strictPss1aImportRejectsInvalidInputCode() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("pss1a-invalid.dyr");
        Files.writeString(dyr, "1 'PSS1A' '1' 7 0.061 0.0017 "
                + "0.30 0.03 0.30 0.03 10.0 0.05 5.0 0.05 -0.05 0 0 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);

        assertThrows(InterpssException.class, () -> parser.parseDynFile(dyr.toString()));
        assertEquals(1, parser.getLastImportReport().count(DynamicModelImportStatus.REJECTED));
    }
}
