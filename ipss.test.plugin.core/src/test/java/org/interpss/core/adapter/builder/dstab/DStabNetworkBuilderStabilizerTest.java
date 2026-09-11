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
import org.interpss.dstab.control.pss.ieee.y1992.pss2b.Ieee1992PSS2BStabilizer;
import org.interpss.dstab.control.pss.ieee.y2016.pss2c.Ieee2016PSS2CStabilizer;
import org.interpss.dstab.control.pss.ieee.y2016.pss3c.Ieee2016PSS3CStabilizer;
import org.interpss.dstab.control.pss.ieee.y2016.pss4c.Ieee2016PSS4CStabilizer;
import org.interpss.dstab.control.pss.ieee.y2016.pss4c.Ieee2016PSS4CStabilizerData;
import org.interpss.dstab.control.pss.ieee.y2016.pss5c.Ieee2016PSS5CStabilizer;
import org.interpss.dstab.control.pss.ieee.y2016.pss5c.Ieee2016PSS5CStabilizerData;
import org.interpss.dstab.control.pss.ieee.y2016.pss6c.Ieee2016PSS6CStabilizer;
import org.interpss.dstab.control.pss.ieee.y2016.pss6c.Ieee2016PSS6CStabilizerData;
import org.interpss.dstab.control.pss.ieee.y2016.pss7c.Ieee2016PSS7CStabilizer;
import org.interpss.dstab.control.pss.ieee.y2016.pss7c.Ieee2016PSS7CStabilizerData;
import org.interpss.dstab.control.pss.ieee.y2005.pss3b.Ieee2005PSS3BStabilizer;
import org.interpss.dstab.control.pss.ieee.y2005.pss4b.Ieee2005PSS4BStabilizer;
import org.interpss.dstab.control.pss.ieee.y2005.pss4b.Ieee2005PSS4BStabilizerData;
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
	void pss2a_appliesDocumentedPowerWorldCorrectionsWithoutChangingImportedData()
			throws Exception {
		DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
		Ieee1992PSS2AStabilizer pss = builder.addPss2a(
				"Bus1", "1", 1, 0, 3, 0, 1, 1,
				0.005, 0.004, 0.0015, 0.015, 0.015, 0.004,
				1.0, 1.0, 0.0, 0.003, 1.0,
				0.1, 0.0015, 0.1, 0.0008, -0.1, 0.2,
				1.0, 0.0, 0.003, 1.0);
		assertNotNull(pss);
		pss.configureIntegrationStep(0.01, 2.0);
		Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
		assertTrue(pss.initStates(machine.getDStabBus(), machine));

		assertEquals(0.02, pss.tw1, TOL);
		assertEquals(0.0, pss.tw2, TOL);
		assertEquals(0.0, pss.t6, TOL);
		assertEquals(0.02, pss.tw3, TOL);
		assertEquals(0.02, pss.tw4, TOL);
		assertEquals(0.0, pss.t7, TOL);
		assertEquals(0.005, pss.t9, TOL);
		assertEquals(0.002, pss.t2, TOL);
		assertEquals(0.0, pss.t4, TOL);
		assertEquals(0.005, pss.tb, TOL);
		assertEquals(0.2, pss.vstmax, TOL);
		assertEquals(-0.1, pss.vstmin, TOL);

		assertEquals(0.005, pss.getData().getTw1(), TOL);
		assertEquals(0.004, pss.getData().getTw2(), TOL);
		assertEquals(0.0015, pss.getData().getT6(), TOL);
		assertEquals(0.003, pss.getData().getT9(), TOL);
		assertEquals(0.0015, pss.getData().getT2(), TOL);
		assertEquals(0.0008, pss.getData().getT4(), TOL);
		assertEquals(0.003, pss.getData().getTb(), TOL);
		assertEquals(-0.1, pss.getData().getVstmax(), TOL);
		assertEquals(0.2, pss.getData().getVstmin(), TOL);
	}

	@Test
	void pss2a_rejectsInvalidIntegrationStepConfiguration() throws Exception {
		DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
		Ieee1992PSS2AStabilizer pss = builder.addPss2a(
				"Bus1", "1", 1, 0, 3, 0, 0, 0,
				0.2, 0.0, 0.0, 0.2, 0.0, 0.0,
				1.0, 1.0, 0.0, 0.0, 1.0,
				0.0, 0.0, 0.0, 0.0, 1.0, -1.0);
		assertNotNull(pss);

		assertThrows(IllegalArgumentException.class,
				() -> pss.configureIntegrationStep(Double.NaN));
		assertThrows(IllegalArgumentException.class,
				() -> pss.configureIntegrationStep(-0.01));
		assertThrows(IllegalArgumentException.class,
				() -> pss.configureIntegrationStep(0.01, -1.0));
	}

	@Test
	void pss2a_preservesPowerWorldAutocorrectionBoundaryValues() throws Exception {
		DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
		Ieee1992PSS2AStabilizer pss = builder.addPss2a(
				"Bus1", "1", 1, 0, 3, 0, 1, 1,
				0.02, 0.01, 0.0025, 0.02, 0.01, 0.01,
				1.0, 1.0, 0.0, 0.0025, 1.0,
				0.1, 0.001, 0.1, 0.001, 0.1, -0.1,
				1.0, 0.0, 0.0025, 1.0);
		assertNotNull(pss);
		pss.configureIntegrationStep(0.01, 2.0);
		Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
		assertTrue(pss.initStates(machine.getDStabBus(), machine));

		assertEquals(0.02, pss.tw1, TOL);
		assertEquals(0.01, pss.tw2, TOL);
		assertEquals(0.0025, pss.t6, TOL);
		assertEquals(0.02, pss.tw3, TOL);
		assertEquals(0.01, pss.tw4, TOL);
		assertEquals(0.01, pss.t7, TOL);
		assertEquals(0.0025, pss.t9, TOL);
		assertEquals(0.001, pss.t2, TOL);
		assertEquals(0.001, pss.t4, TOL);
		assertEquals(0.0025, pss.tb, TOL);
	}

    @Test
    void parsePss2b_mapsCompleteRecordAndRunsDedicatedThreeLeadLagModel()
            throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("pss2b.dyr");
        Files.writeString(dyr, "1 'PSS2B' '1' 1 3 5 1 "
                + "10 10 0 10 0 10 1.47 1 0.5 0.1 2.4 "
                + "0.16 0.02 0.16 0.02 0.12 0.03 "
                + "0.2 -0.2 0.3 -0.3 0.1 -0.1 1 0 0 1 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);

        parser.parseDynFile(dyr.toString());

        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        Ieee1992PSS2BStabilizer pss =
                (Ieee1992PSS2BStabilizer) machine.getStabilizer();
        assertNotNull(pss);
        assertSame(machine, pss.getMachine());
        var data = pss.getData();
        assertEquals(1, data.getIcs1());
        assertEquals(3, data.getIcs2());
        assertEquals(5, data.getM());
        assertEquals(1, data.getN());
        assertEquals(10.0, data.getTw1(), TOL);
        assertEquals(10.0, data.getTw2(), TOL);
        assertEquals(0.0, data.getT6(), TOL);
        assertEquals(10.0, data.getTw3(), TOL);
        assertEquals(0.0, data.getTw4(), TOL);
        assertEquals(10.0, data.getT7(), TOL);
        assertEquals(1.47, data.getKs2(), TOL);
        assertEquals(1.0, data.getKs3(), TOL);
        assertEquals(0.5, data.getT8(), TOL);
        assertEquals(0.1, data.getT9(), TOL);
        assertEquals(2.4, data.getKs1(), TOL);
        assertEquals(0.16, data.getT1(), TOL);
        assertEquals(0.02, data.getT2(), TOL);
        assertEquals(0.16, data.getT3(), TOL);
        assertEquals(0.02, data.getT4(), TOL);
        assertEquals(0.12, data.getT10(), TOL);
        assertEquals(0.03, data.getT11(), TOL);
        assertEquals(0.2, data.getVsi1max(), TOL);
        assertEquals(-0.2, data.getVsi1min(), TOL);
        assertEquals(0.3, data.getVsi2max(), TOL);
        assertEquals(-0.3, data.getVsi2min(), TOL);
        assertEquals(0.1, data.getVstmax(), TOL);
        assertEquals(-0.1, data.getVstmin(), TOL);
        assertEquals(1.0, data.getA(), TOL);
        assertEquals(0.0, data.getTa(), TOL);
        assertEquals(0.0, data.getTb(), TOL);
        assertEquals(1.0, data.getKs4(), TOL);
        assertTrue(pss.initStates(machine.getDStabBus(), machine));
        machine.setSpeed(1.01);
        double maximumOutput = 0.0;
        for (int i = 0; i < 100; i++) {
            assertTrue(pss.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
            assertTrue(pss.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
            maximumOutput = Math.max(maximumOutput, Math.abs(pss.getOutput(machine)));
        }
        assertTrue(maximumOutput > 1.0e-7);
        assertTrue(maximumOutput <= 0.1 + TOL);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void parsePss2b_acceptsNativePsseRecordAndDefaultsPowerWorldExtensions()
            throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("pss2b-native-psse.dyr");
        Files.writeString(dyr, "1 'PSS2B' '1' 1 3 5 1 "
                + "10 10 0 10 0 10 1.47 1 0.5 0.1 2.4 "
                + "0.16 0.02 0.16 0.02 0.12 0.03 "
                + "0.2 -0.2 0.3 -0.3 0.1 -0.1 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);

        parser.parseDynFile(dyr.toString());

        Ieee1992PSS2BStabilizer pss = (Ieee1992PSS2BStabilizer) builder
                .getDStabNetwork().getMachine("Bus1-mach1").getStabilizer();
        assertNotNull(pss);
        assertEquals(1.0, pss.getData().getA(), TOL);
        assertEquals(0.0, pss.getData().getTa(), TOL);
        assertEquals(0.0, pss.getData().getTb(), TOL);
        assertEquals(1.0, pss.getData().getKs4(), TOL);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void pss2b_appliesPowerWorldCorrectionsWithoutChangingImportedData()
            throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Ieee1992PSS2BStabilizer pss = builder.addPss2b("Bus1", "1",
                1, 3, 1, 1,
                0.005, 0.004, 0.015, 0.005, 0.015, 0.004,
                1.0, 1.0, 0.0, 0.015, 1.0,
                0.1, 0.001, 0.1, 0.004, 0.1, 0.004,
                -0.2, 0.2, -0.3, 0.3,
                -0.1, 0.2, 1.0, 0.0, 0.015, 1.0);
        assertNotNull(pss);
        pss.configureIntegrationStep(0.01, 2.0);
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        assertTrue(pss.initStates(machine.getDStabBus(), machine));

        assertEquals(0.02, pss.tw1, TOL);
        assertEquals(0.0, pss.tw2, TOL);
        assertEquals(0.02, pss.t6, TOL);
        assertEquals(0.02, pss.tw3, TOL);
        assertEquals(0.02, pss.tw4, TOL);
        assertEquals(0.0, pss.t7, TOL);
        assertEquals(0.02, pss.t9, TOL);
        assertEquals(0.0, pss.t2, TOL);
        assertEquals(0.005, pss.t4, TOL);
        assertEquals(0.005, pss.t11, TOL);
        assertEquals(0.02, pss.tb, TOL);
        assertEquals(0.2, pss.vsi1max, TOL);
        assertEquals(-0.2, pss.vsi1min, TOL);
        assertEquals(0.3, pss.vsi2max, TOL);
        assertEquals(-0.3, pss.vsi2min, TOL);
        assertEquals(0.2, pss.vstmax, TOL);
        assertEquals(-0.1, pss.vstmin, TOL);

        assertEquals(0.005, pss.getData().getTw1(), TOL);
        assertEquals(0.004, pss.getData().getTw2(), TOL);
        assertEquals(0.001, pss.getData().getT2(), TOL);
        assertEquals(0.004, pss.getData().getT11(), TOL);
        assertEquals(-0.1, pss.getData().getVstmax(), TOL);
        assertEquals(0.2, pss.getData().getVstmin(), TOL);
    }

    @Test
    void pss2b_limitsSelectedSignalsBeforeTheDynamicInputPaths() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Ieee1992PSS2BStabilizer pss = builder.addPss2b("Bus1", "1",
                1, 3, 0, 0,
                0.2, 0.0, 0.0, 0.2, 0.0, 0.0,
                1.0, 0.0, 0.0, 0.0, 1.0,
                0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                0.005, -0.005, 0.02, -0.02,
                1.0, -1.0, 1.0, 0.0, 0.0, 0.0);
        assertNotNull(pss);
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1.02);
        machine.setPe(0.10);
        assertTrue(pss.initStates(machine.getDStabBus(), machine));
        assertEquals(0.005, pss.input1Signal, TOL);
        assertEquals(0.02, pss.input2Signal, TOL);
    }

    @Test
    void parsePss2c_mapsRealWeccRecordAndRunsFourLeadLagModel() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("pss2c.dyr");
        Files.writeString(dyr, "1 'PSS2C' '1' 1 0 3 0 5 1 "
                + "2 2 0 2 0 2 0.28885 1 1 0.2 4 "
                + "0.13 0.03 0.13 0.03 0.14 0.03 "
                + "2 -2 2 -2 0.05 -0.05 1 1 0.15 0.10 0 0 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);

        parser.parseDynFile(dyr.toString());

        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        Ieee2016PSS2CStabilizer pss =
                (Ieee2016PSS2CStabilizer) machine.getStabilizer();
        assertNotNull(pss);
        assertSame(machine, pss.getMachine());
        var data = pss.getData();
        assertEquals(1, data.getIcs1());
        assertEquals(0, data.getRemoteBus1());
        assertEquals(3, data.getIcs2());
        assertEquals(0, data.getRemoteBus2());
        assertEquals(5, data.getM());
        assertEquals(1, data.getN());
        assertEquals(0.14, data.getT10(), TOL);
        assertEquals(0.03, data.getT11(), TOL);
        assertEquals(1.0, data.getT12(), TOL);
        assertEquals(1.0, data.getT13(), TOL);
        assertEquals(0.15, data.getPssActivation(), TOL);
        assertEquals(0.10, data.getPssDeactivation(), TOL);
        assertEquals(0.0, data.getTpgfilt(), TOL);
        assertEquals(0.0, data.getXcomp(), TOL);
        assertEquals(0.0, data.getTcomp(), TOL);
        machine.setPe(0.20);
        assertTrue(pss.initStates(machine.getDStabBus(), machine));
        assertTrue(pss.isPssActive(), "pe=" + machine.getPe()
                + ", filtered=" + pss.getFilteredPgen()
                + ", on=" + data.getPssActivation()
                + ", off=" + data.getPssDeactivation());
        machine.setPe(0.20);
        machine.setSpeed(1.01);
        double maximumOutput = 0.0;
        for (int i = 0; i < 100; i++) {
            assertTrue(pss.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
            assertTrue(pss.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
            maximumOutput = Math.max(maximumOutput, Math.abs(pss.getOutput(machine)));
        }
        assertTrue(maximumOutput > 1.0e-7);
        assertTrue(maximumOutput <= 0.05 + TOL);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void pss2c_appliesDocumentedCorrectionsWithoutChangingImportedData() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("pss2c-corrections.dyr");
        Files.writeString(dyr, "1 'PSS2C' '1' 1 0 3 0 1 1 "
                + "0.005 0.004 0.015 0.005 0.015 0.004 1 1 0 0.015 1 "
                + "0.1 0.001 0.1 0.004 0.1 0.004 "
                + "-0.2 0.2 -0.3 0.3 -0.1 0.2 0.1 0.004 0.15 0.10 0 0 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        Ieee2016PSS2CStabilizer pss =
                (Ieee2016PSS2CStabilizer) machine.getStabilizer();
        pss.configureIntegrationStep(0.01, 2.0);
        assertTrue(pss.initStates(machine.getDStabBus(), machine));

        assertEquals(0.02, pss.tw1, TOL);
        assertEquals(0.02, pss.tw2, TOL);
        assertEquals(0.02, pss.tw3, TOL);
        assertEquals(0.02, pss.tw4, TOL);
        assertEquals(0.02, pss.t6, TOL);
        assertEquals(0.0, pss.t7, TOL);
        assertEquals(0.02, pss.t9, TOL);
        assertEquals(0.0, pss.t2, TOL);
        assertEquals(0.005, pss.t4, TOL);
        assertEquals(0.005, pss.t11, TOL);
        assertEquals(0.005, pss.t13, TOL);
        assertEquals(0.2, pss.vsi1max, TOL);
        assertEquals(-0.2, pss.vsi1min, TOL);
        assertEquals(0.3, pss.vsi2max, TOL);
        assertEquals(-0.3, pss.vsi2min, TOL);
        assertEquals(0.2, pss.vstmax, TOL);
        assertEquals(-0.1, pss.vstmin, TOL);

        assertEquals(0.005, pss.getData().getTw1(), TOL);
        assertEquals(0.004, pss.getData().getTw2(), TOL);
        assertEquals(0.004, pss.getData().getT13(), TOL);
        assertEquals(-0.1, pss.getData().getVstmax(), TOL);
        assertEquals(0.2, pss.getData().getVstmin(), TOL);
    }

    @Test
    void pss2c_appliesPowerActivationHysteresis() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("pss2c-logic.dyr");
        Files.writeString(dyr, "1 'PSS2C' '1' 1 0 3 0 0 0 "
                + "0.2 0 0 0.2 0 0 0 0 0 0 1 "
                + "0 0 0 0 0 0 10 -10 10 -10 1 -1 0 0 0.15 0.10 0 0 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
        parser.parseDynFile(dyr.toString());
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        Ieee2016PSS2CStabilizer pss =
                (Ieee2016PSS2CStabilizer) machine.getStabilizer();
        machine.setPe(0.20);
        assertTrue(pss.initStates(machine.getDStabBus(), machine));
        assertTrue(pss.isPssActive(), "pe=" + machine.getPe()
                + ", filtered=" + pss.getFilteredPgen()
                + ", on=" + pss.getData().getPssActivation()
                + ", off=" + pss.getData().getPssDeactivation());

        machine.setPe(0.12);
        assertTrue(pss.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
        machine.setPe(0.12);
        assertTrue(pss.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
        assertTrue(pss.isPssActive(), "PSS must retain its state inside the hysteresis band");

        machine.setPe(0.05);
        assertTrue(pss.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
        machine.setPe(0.05);
        assertTrue(pss.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
        assertTrue(!pss.isPssActive());
        assertEquals(0.0, pss.getOutput(machine), TOL);

        machine.setPe(0.20);
        assertTrue(pss.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
        machine.setPe(0.20);
        assertTrue(pss.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
        assertTrue(pss.isPssActive());
    }

    @Test
    void pss2c_computesCompensatedFrequencyInput() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("pss2c-compensated-frequency.dyr");
        Files.writeString(dyr, "1 'PSS2C' '1' 7 0 3 0 0 0 "
                + "0.2 0 0 0.2 0 0 0 0 0 0 1 "
                + "0 0 0 0 0 0 10 -10 10 -10 1 -1 0 0 0 -1 0 0 /\n");
        new PSSEDStabDirectParser(builder).setStrictImport(true).parseDynFile(dyr.toString());
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        Ieee2016PSS2CStabilizer pss =
                (Ieee2016PSS2CStabilizer) machine.getStabilizer();
        assertTrue(pss.initStates(machine.getDStabBus(), machine));

        machine.getDStabBus().setVoltage(new Complex(Math.cos(0.01), Math.sin(0.01)));
        assertTrue(pss.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER, machine, 0));

        assertEquals(0.01 / (2.0 * Math.PI
                        * machine.getDStabBus().getNetwork().getFrequency() * 0.01),
                pss.input1Signal, 1.0e-8);
    }

    @Test
    void parsePss3b_mapsAllParametersAndRunsTwoNotchFilters() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("pss3b.dyr");
        Files.writeString(dyr, "1 'PSS3B' '1' 3 998 1 999 "
                + "0.2 0.02 1.5 4 0.03 2 0.4 "
                + "0.1 0.01 0.2 0.02 0.3 0.03 0.4 0.04 0.1 -0.1 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);

        parser.parseDynFile(dyr.toString());

        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        Ieee2005PSS3BStabilizer pss =
                (Ieee2005PSS3BStabilizer) machine.getStabilizer();
        assertNotNull(pss);
        assertSame(machine, pss.getMachine());
        var data = pss.getData();
        assertEquals(3, data.ics1());
        assertEquals(998, data.remoteBus1());
        assertEquals(1, data.ics2());
        assertEquals(999, data.remoteBus2());
        assertEquals(0.2, data.ks1(), TOL);
        assertEquals(0.02, data.t1(), TOL);
        assertEquals(1.5, data.tw1(), TOL);
        assertEquals(4.0, data.ks2(), TOL);
        assertEquals(0.03, data.t2(), TOL);
        assertEquals(2.0, data.tw2(), TOL);
        assertEquals(0.4, data.tw3(), TOL);
        assertEquals(0.1, data.a1(), TOL);
        assertEquals(0.01, data.a2(), TOL);
        assertEquals(0.2, data.a3(), TOL);
        assertEquals(0.02, data.a4(), TOL);
        assertEquals(0.3, data.a5(), TOL);
        assertEquals(0.03, data.a6(), TOL);
        assertEquals(0.4, data.a7(), TOL);
        assertEquals(0.04, data.a8(), TOL);
        assertEquals(0.1, data.vstmax(), TOL);
        assertEquals(-0.1, data.vstmin(), TOL);

        machine.setPe(0.8);
        assertTrue(pss.initStates(machine.getDStabBus(), machine));
        machine.setSpeed(1.01);
        double maximumOutput = 0.0;
        for (int i = 0; i < 200; i++) {
            assertTrue(pss.nextStep(0.001, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
            assertTrue(pss.nextStep(0.001, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
            maximumOutput = Math.max(maximumOutput, Math.abs(pss.getOutput(machine)));
        }
        assertTrue(maximumOutput > 1.0e-7);
        assertTrue(maximumOutput <= 0.1 + TOL);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void pss3b_appliesDocumentedCorrectionsWithoutChangingSourceData() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("pss3b-corrections.dyr");
        Files.writeString(dyr, "1 'PSS3B' '1' 3 0 1 0 "
                + "0.2 0.004 0.005 4 0.015 0.005 0.015 "
                + "0 0 0 0 0 0 0 0 -0.1 0.2 /\n");
        new PSSEDStabDirectParser(builder).setStrictImport(true).parseDynFile(dyr.toString());
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        Ieee2005PSS3BStabilizer pss =
                (Ieee2005PSS3BStabilizer) machine.getStabilizer();
        pss.configureIntegrationStep(0.01, 2.0);
        assertTrue(pss.initStates(machine.getDStabBus(), machine));

        assertEquals(0.0, pss.t1, TOL);
        assertEquals(0.02, pss.tw1, TOL);
        assertEquals(0.02, pss.t2, TOL);
        assertEquals(0.02, pss.tw2, TOL);
        assertEquals(0.02, pss.tw3, TOL);
        assertEquals(0.2, pss.vstmax, TOL);
        assertEquals(-0.1, pss.vstmin, TOL);

        assertEquals(0.004, pss.getData().t1(), TOL);
        assertEquals(0.005, pss.getData().tw1(), TOL);
        assertEquals(0.015, pss.getData().tw3(), TOL);
        assertEquals(-0.1, pss.getData().vstmax(), TOL);
        assertEquals(0.2, pss.getData().vstmin(), TOL);
    }

    @Test
    void pss3b_matchesDynawoReferenceBranchStepResponse() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("pss3b-dynawo.dyr");
        Files.writeString(dyr, "1 'PSS3B' '1' 3 0 1 0 "
                + "0.2 0.02 1.5 0 1.5 1.5 -1 "
                + "0 0 0 0 0 0 0 0 0.1 -0.1 /\n");
        new PSSEDStabDirectParser(builder).setStrictImport(true).parseDynFile(dyr.toString());
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        Ieee2005PSS3BStabilizer pss =
                (Ieee2005PSS3BStabilizer) machine.getStabilizer();
        machine.setPe(0.8);
        assertTrue(pss.initStates(machine.getDStabBus(), machine));

        machine.setPe(0.9);
        double dt = 0.0005;
        double elapsed = 0.2;
        for (int i = 0; i < (int) (elapsed / dt); i++) {
            assertTrue(pss.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
            assertTrue(pss.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
        }

        double expected = 0.1 * 0.2 * 1.5 / (1.5 - 0.02)
                * (Math.exp(-elapsed / 1.5) - Math.exp(-elapsed / 0.02));
        assertEquals(expected, pss.getOutput(machine), 2.0e-5);
    }

    @ParameterizedTest(name = "PSS3B input selector {0}")
    @CsvSource({"1", "2", "3", "4", "5", "6"})
    void pss3bSupportsEveryDocumentedInputSelector(int selector) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        Ieee2005PSS3BStabilizer pss = builder.addPss3b(
                "Bus1", "1", selector, 1,
                1.0, 0.02, 0.20,
                0.0, 0.02, 0.20, 0.0,
                0.0, 0.0, 0.0, 0.0,
                0.0, 0.0, 0.0, 0.0,
                1.0, -1.0);
        assertNotNull(pss);
        assertTrue(pss.initStates(machine.getDStabBus(), machine));
        assertEquals(0.0, pss.getOutput(machine), TOL);

        perturbSelectedSignal(selector, machine);
        double maximumOutput = 0.0;
        for (int i = 0; i < 100; i++) {
            assertTrue(pss.nextStep(0.001, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
            assertTrue(pss.nextStep(0.001, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
            maximumOutput = Math.max(maximumOutput, Math.abs(pss.getOutput(machine)));
        }
        assertTrue(maximumOutput > 1.0e-7,
                "Selected PSS3B input path produced no response");
        assertTrue(maximumOutput <= 1.0 + TOL);
    }

    @ParameterizedTest(name = "PSS3B remote bus selector {0}")
    @CsvSource({"2", "5", "6"})
    void pss3bResolvesRemoteBusForBusBasedSignals(int selector) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        AclfNetworkBuilder topology = new AclfNetworkBuilder(builder.getDStabNetwork());
        topology.setNetworkInfo("ut", "ut", 100000.0, OriginalDataFormat.PSSE);
        topology.addBus("Bus2", "Remote", 2L, 16500.0, 0.97, 0.0,
                null, null, null);
        var remote = builder.getDStabNetwork().getDStabBus("Bus2");
        remote.setFreq(0.985);

        Ieee2005PSS3BStabilizer pss = builder.addPss3b(
                "Bus1", "1", selector, 2, 3, 999,
                1.0, 0.02, 0.20,
                0.0, 0.02, 0.20, 0.0,
                0.0, 0.0, 0.0, 0.0,
                0.0, 0.0, 0.0, 0.0,
                1.0, -1.0);
        assertNotNull(pss, "REMBUS is ignored for the local generator-power second input");
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        assertTrue(pss.initStates(machine.getDStabBus(), machine));

        if (selector == 2) {
            remote.setFreq(0.98);
            assertTrue(pss.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
            assertEquals(-0.005, pss.input1Signal, TOL);
        } else {
            remote.setVoltage(new Complex(0.96, 0.0));
            assertTrue(pss.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
            assertEquals(selector == 5 ? -0.01 : -2.0, pss.input1Signal, TOL);
        }
    }

    @Test
    void pss3bRejectsMissingRemoteBusOnlyForBusBasedSignals() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        assertNull(builder.addPss3b(
                "Bus1", "1", 5, 999, 3, 0,
                1.0, 0.02, 0.20, 0.0, 0.02, 0.20, 0.0,
                0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                1.0, -1.0));
        assertNotNull(builder.addPss3b(
                "Bus1", "1", 3, 999, 1, 999,
                1.0, 0.02, 0.20, 0.0, 0.02, 0.20, 0.0,
                0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                1.0, -1.0));
    }

    @Test
    void parsePss3c_mapsCompleteRecordAndReusesPss3bDynamicChain() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("pss3c.dyr");
        Files.writeString(dyr, "1 'PSS3C' '1' 6 998 3 999 "
                + "1 0.02 1.5 0.5 0.03 2.0 0.6 "
                + "0.1 0.01 0.2 0.02 0.3 0.03 0.4 0.04 "
                + "0.1 -0.1 0.15 0.10 0.2 0.02 0.05 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);

        parser.parseDynFile(dyr.toString());

        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        Ieee2016PSS3CStabilizer pss =
                (Ieee2016PSS3CStabilizer) machine.getStabilizer();
        assertNotNull(pss);
        assertSame(machine, pss.getMachine());
        var data = pss.getPss3cData();
        assertEquals(6, data.ics1());
        assertEquals(998, data.remoteBus1());
        assertEquals(3, data.ics2());
        assertEquals(999, data.remoteBus2());
        assertEquals(1.0, data.k1(), TOL);
        assertEquals(0.02, data.t1(), TOL);
        assertEquals(1.5, data.tw1(), TOL);
        assertEquals(0.5, data.k2(), TOL);
        assertEquals(0.03, data.t2(), TOL);
        assertEquals(2.0, data.tw2(), TOL);
        assertEquals(0.6, data.tw3(), TOL);
        assertEquals(0.1, data.a1(), TOL);
        assertEquals(0.04, data.a8(), TOL);
        assertEquals(0.1, data.vstmax(), TOL);
        assertEquals(-0.1, data.vstmin(), TOL);
        assertEquals(0.15, data.pssActivation(), TOL);
        assertEquals(0.10, data.pssDeactivation(), TOL);
        assertEquals(0.05, data.tpgfilt(), TOL);
        assertEquals(0.2, data.xcomp(), TOL);
        assertEquals(0.02, data.tcomp(), TOL);
        machine.setPe(0.2);
        assertTrue(pss.initStates(machine.getDStabBus(), machine));
        assertTrue(pss.isPssActive());
        assertEquals(0.0, pss.getOutput(machine), TOL);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void pss3c_computesCompensatedFrequencyInput() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        double[] p = pss3cParameters();
        p[0] = 6;
        p[23] = 0.2;
        p[24] = 0.02;
        p[21] = 0.0;
        p[22] = -1.0;
        Ieee2016PSS3CStabilizer pss = builder.addPss3c("Bus1", "1", p);
        assertNotNull(pss);
        assertTrue(pss.initStates(machine.getDStabBus(), machine));

        machine.getDStabBus().setVoltage(new Complex(0.99, 0.0));
        assertTrue(pss.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER, machine, 0));

        assertTrue(Double.isFinite(pss.getCompensatedFrequencySignal()));
        assertEquals(pss.getCompensatedFrequencySignal(), pss.input1Signal, 1.0e-12);
    }

    @Test
    void pss3c_matchesInheritedPss3bAnalyticResponse() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        double[] p = pss3cParameters();
        p[0] = 3;
        p[4] = 0.2;
        p[5] = 0.02;
        p[6] = 1.5;
        p[7] = 0.0;
        p[8] = 1.5;
        p[9] = 1.5;
        p[10] = -1.0;
        Ieee2016PSS3CStabilizer pss = builder.addPss3c("Bus1", "1", p);
        machine.setPe(0.8);
        assertTrue(pss.initStates(machine.getDStabBus(), machine));

        machine.setPe(0.9);
        double dt = 0.0005;
        double elapsed = 0.2;
        for (int i = 0; i < (int) (elapsed / dt); i++) {
            assertTrue(pss.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
            assertTrue(pss.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
        }

        double expected = 0.1 * 0.2 * 1.5 / (1.5 - 0.02)
                * (Math.exp(-elapsed / 1.5) - Math.exp(-elapsed / 0.02));
        assertEquals(expected, pss.getOutput(machine), 2.0e-5);
    }

    @Test
    void pss3c_filtersPowerForActivationHysteresis() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        double[] p = pss3cParameters();
        p[21] = 0.15;
        p[22] = 0.10;
        p[25] = 0.02;
        Ieee2016PSS3CStabilizer pss = builder.addPss3c("Bus1", "1", p);
        machine.setPe(0.20);
        assertTrue(pss.initStates(machine.getDStabBus(), machine));
        assertTrue(pss.isPssActive());

        machine.setPe(0.05);
        for (int i = 0; i < 20; i++) {
            assertTrue(pss.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
            assertTrue(pss.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
        }
        assertTrue(pss.getFilteredPgen() < 0.10);
        assertTrue(!pss.isPssActive());
        assertEquals(0.0, pss.getOutput(machine), TOL);

        machine.setPe(0.20);
        for (int i = 0; i < 20; i++) {
            assertTrue(pss.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
            assertTrue(pss.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
        }
        assertTrue(pss.getFilteredPgen() > 0.15);
        assertTrue(pss.isPssActive());
    }

    private static double[] pss3cParameters() {
        return new double[] {
                1, 0, 3, 0,
                1.0, 0.02, 1.5,
                0.0, 0.03, 2.0, 0.6,
                0.0, 0.0, 0.0, 0.0,
                0.0, 0.0, 0.0, 0.0,
                0.1, -0.1,
                0.0, -1.0, 0.0, 0.0, 0.0
        };
    }

    @Test
    void parsePss4b_mapsExact75ParameterRecord() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("pss4b.dyr");
        StringBuilder record = new StringBuilder("1 'PSS4B' '1'");
        for (int i = 1; i <= Ieee2005PSS4BStabilizerData.PARAMETER_COUNT; i++) {
            record.append(' ').append(i);
        }
        Files.writeString(dyr, record.append(" /\n").toString());
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);

        parser.parseDynFile(dyr.toString());

        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        Ieee2005PSS4BStabilizer pss =
                (Ieee2005PSS4BStabilizer) machine.getStabilizer();
        assertNotNull(pss);
        assertSame(machine, pss.getMachine());
        var data = pss.getData();
        assertEquals(1.0, data.input().cli(), TOL);
        assertEquals(16.0, data.input().wh2(), TOL);
        assertEquals(17.0, data.lowBand().k1(), TOL);
        assertEquals(35.0, data.lowBand().min(), TOL);
        assertEquals(36.0, data.intermediateBand().k1(), TOL);
        assertEquals(54.0, data.intermediateBand().min(), TOL);
        assertEquals(55.0, data.highBand().k1(), TOL);
        assertEquals(73.0, data.highBand().min(), TOL);
        assertEquals(74.0, data.vstmax(), TOL);
        assertEquals(75.0, data.vstmin(), TOL);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void pss4b_matchesAnalyticSingleBandStepResponse() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        double[] p = new double[Ieee2005PSS4BStabilizerData.PARAMETER_COUNT];
        // Low/intermediate speed transducer and all four notch filters bypass.
        // Low-band upper path is 1/(1 + 0.1s), followed by two bypass blocks.
        p[16] = 1.0; // KL1
        p[17] = 1.0; // KL11
        p[19] = 0.1; // TL2
        p[32] = 2.0; // KL
        p[33] = 1.0; // VLmax
        p[34] = -1.0; // VLmin
        p[73] = 1.0;
        p[74] = -1.0;
        Ieee2005PSS4BStabilizer pss = builder.addPss4b("Bus1", "1", p);
        assertNotNull(pss);
        assertTrue(pss.initStates(machine.getDStabBus(), machine));

        machine.setSpeed(machine.getSpeed() + 0.01);
        double dt = 0.0005;
        double elapsed = 0.2;
        for (int i = 0; i < (int) (elapsed / dt); i++) {
            assertTrue(pss.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
            assertTrue(pss.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
        }

        double expected = 0.02 * (1.0 - Math.exp(-elapsed / 0.1));
        assertEquals(expected, pss.getOutput(machine), 2.0e-5);
        assertEquals(expected, pss.getLowOutput(), 2.0e-5);
        assertEquals(0.0, pss.getIntermediateOutput(), TOL);
        assertEquals(0.0, pss.getHighOutput(), TOL);
    }

    @Test
    void pss4b_runsElectricalPowerTransducerAndHighBandNotches() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        double[] p = new double[Ieee2005PSS4BStabilizerData.PARAMETER_COUNT];
        p[8] = 0.02; // TH
        p[9] = 0.01; // AH
        p[10] = 0.10; // BH
        p[11] = 0.05; // H coefficient (2H in the published transducer)
        p[12] = 0.20; // BWH1
        p[13] = 8.0; // WH1
        p[14] = 0.30; // BWH2
        p[15] = 12.0; // WH2
        p[54] = 1.0; // KH1
        p[55] = 1.0; // KH11
        p[57] = 0.05; // TH2
        p[70] = 2.0; // KH
        p[71] = 0.5;
        p[72] = -0.5;
        p[73] = 0.1;
        p[74] = -0.1;
        Ieee2005PSS4BStabilizer pss = builder.addPss4b("Bus1", "1", p);
        assertNotNull(pss);
        machine.setPe(0.8);
        assertTrue(pss.initStates(machine.getDStabBus(), machine));

        machine.setPe(0.9);
        double maximumHighInput = 0.0;
        double maximumOutput = 0.0;
        for (int i = 0; i < 1000; i++) {
            assertTrue(pss.nextStep(0.0001, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
            assertTrue(pss.nextStep(0.0001, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
            maximumHighInput = Math.max(maximumHighInput, Math.abs(pss.getHighInput()));
            maximumOutput = Math.max(maximumOutput, Math.abs(pss.getOutput(machine)));
        }

        assertTrue(maximumHighInput > 1.0e-6);
        assertTrue(maximumOutput > 1.0e-6);
        assertTrue(maximumOutput <= 0.1 + TOL);
        assertEquals(0.0, pss.getLowOutput(), TOL);
        assertEquals(0.0, pss.getIntermediateOutput(), TOL);
    }

    @Test
    void pss4b_appliesPowerWorldCorrectionsWithoutChangingSourceData() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        double[] p = new double[Ieee2005PSS4BStabilizerData.PARAMETER_COUNT];
        p[33] = -0.2;
        p[34] = 0.1;
        p[52] = -0.3;
        p[53] = 0.2;
        p[71] = -0.4;
        p[72] = 0.3;
        p[73] = -0.5;
        p[74] = 0.4;
        Ieee2005PSS4BStabilizer pss = builder.addPss4b("Bus1", "1", p);
        pss.configureIntegrationStep(0.01, 2.0);
        assertTrue(pss.initStates(machine.getDStabBus(), machine));

        var effective = pss.getEffectiveData();
        assertEquals(0.02, effective.input().th(), TOL);
        assertEquals(0.02, effective.input().ah(), TOL);
        assertEquals(2.0 * machine.getH(), effective.input().h(), TOL);
        assertEquals(0.02, effective.lowBand().k1(), TOL);
        assertEquals(0.02, effective.lowBand().k2(), TOL);
        assertEquals(0.1, effective.lowBand().max(), TOL);
        assertEquals(-0.2, effective.lowBand().min(), TOL);
        assertEquals(0.2, effective.intermediateBand().max(), TOL);
        assertEquals(-0.3, effective.intermediateBand().min(), TOL);
        assertEquals(0.3, effective.highBand().max(), TOL);
        assertEquals(-0.4, effective.highBand().min(), TOL);
        assertEquals(0.4, effective.vstmax(), TOL);
        assertEquals(-0.5, effective.vstmin(), TOL);

        assertEquals(0.0, pss.getData().input().th(), TOL);
        assertEquals(0.0, pss.getData().input().h(), TOL);
        assertEquals(0.0, pss.getData().lowBand().k1(), TOL);
        assertEquals(-0.2, pss.getData().lowBand().max(), TOL);
        assertEquals(0.1, pss.getData().lowBand().min(), TOL);
        assertEquals(-0.5, pss.getData().vstmax(), TOL);
        assertEquals(0.4, pss.getData().vstmin(), TOL);
    }

    @Test
    void parsePss4c_mapsPowerWorld94ParameterExtension() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("pss4c.dyr");
        StringBuilder record = new StringBuilder("1 'PSS4C' '1'");
        for (int i = 1; i <= Ieee2016PSS4CStabilizerData.PARAMETER_COUNT; i++) {
            record.append(' ').append(i);
        }
        Files.writeString(dyr, record.append(" /\n").toString());
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);

        parser.parseDynFile(dyr.toString());

        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        Ieee2016PSS4CStabilizer pss =
                (Ieee2016PSS4CStabilizer) machine.getStabilizer();
        assertNotNull(pss);
        assertSame(machine, pss.getMachine());
        assertEquals(1.0, pss.getData().threeBandData().input().cli(), TOL);
        assertEquals(74.0, pss.getData().threeBandData().vstmax(), TOL);
        assertEquals(75.0, pss.getData().threeBandData().vstmin(), TOL);
        assertEquals(76.0, pss.getData().veryLowBand().k1(), TOL);
        assertEquals(94.0, pss.getData().veryLowBand().min(), TOL);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void pss4c_addsVeryLowBandAndUsesMachineInertiaWhenHIsZero() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        double[] p = new double[Ieee2016PSS4CStabilizerData.PARAMETER_COUNT];
        p[73] = 0.01;
        p[74] = -1.0;
        p[75] = 1.0; // KVL1
        p[76] = 1.0; // KVL11
        p[78] = 0.1; // TVL2
        p[91] = 2.0; // KVL
        p[92] = 1.0;
        p[93] = -1.0;
        Ieee2016PSS4CStabilizer pss = builder.addPss4c("Bus1", "1", p);
        assertNotNull(pss);
        assertTrue(pss.initStates(machine.getDStabBus(), machine));
        assertEquals(2.0 * machine.getH(), pss.getEffectiveInertiaCoefficient(), TOL);

        machine.setSpeed(machine.getSpeed() + 0.01);
        double dt = 0.0005;
        double elapsed = 0.2;
        for (int i = 0; i < (int) (elapsed / dt); i++) {
            assertTrue(pss.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
            assertTrue(pss.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
        }

        double expected = 0.02 * (1.0 - Math.exp(-elapsed / 0.1));
        assertEquals(expected, pss.getVeryLowOutput(), 2.0e-5);
        assertEquals(0.01, pss.getOutput(machine), TOL);
        assertEquals(0.0, pss.getLowOutput(), TOL);
        assertEquals(0.0, pss.getIntermediateOutput(), TOL);
        assertEquals(0.0, pss.getHighOutput(), TOL);
    }

    @Test
    void parsePss5c_mapsPowerWorld21ParameterExtension() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("pss5c.dyr");
        StringBuilder record = new StringBuilder("1 'PSS5C' '1'");
        for (int i = 1; i <= Ieee2016PSS5CStabilizerData.PARAMETER_COUNT; i++) {
            record.append(' ').append(i);
        }
        Files.writeString(dyr, record.append(" /\n").toString());
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);

        parser.parseDynFile(dyr.toString());

        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        Ieee2016PSS5CStabilizer pss =
                (Ieee2016PSS5CStabilizer) machine.getStabilizer();
        assertNotNull(pss);
        assertEquals(1.0, pss.getData().veryLowBand().gain(), TOL);
        assertEquals(8.0, pss.getData().lowBand().min(), TOL);
        assertEquals(13.0, pss.getData().highBand().gain(), TOL);
        assertEquals(17.0, pss.getData().k1(), TOL);
        assertEquals(21.0, pss.getData().vstmin(), TOL);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void pss5c_matchesPublishedLowIntermediateAndHighFrequencyResponses() throws Exception {
        assertPss5cSinusoidalResponse(false);
        assertPss5cSinusoidalResponse(true);
    }

    @Test
    void pss5c_appliesPowerWorldCorrectionsWithoutMutatingSourceData() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1.0);
        double[] p = new double[Ieee2016PSS5CStabilizerData.PARAMETER_COUNT];
        p[2] = -0.2; p[3] = 0.1;
        p[6] = -0.3; p[7] = 0.2;
        p[10] = -0.4; p[11] = 0.3;
        p[14] = -0.5; p[15] = 0.4;
        p[19] = -0.6; p[20] = 0.5;
        Ieee2016PSS5CStabilizer pss = builder.addPss5c("Bus1", "1", p);
        pss.configureIntegrationStep(0.01, 2.0);
        assertTrue(pss.initStates(machine.getDStabBus(), machine));

        var effective = pss.getEffectiveData();
        assertEquals(0.02, effective.veryLowBand().gain(), TOL);
        assertEquals(0.02, effective.veryLowBand().frequency(), TOL);
        assertEquals(0.02, effective.lowBand().frequency(), TOL);
        assertEquals(0.02, effective.intermediateBand().frequency(), TOL);
        assertEquals(0.02, effective.highBand().frequency(), TOL);
        assertEquals(0.02, effective.k1(), TOL);
        assertEquals(0.02, effective.k2(), TOL);
        assertEquals(0.02, effective.k3(), TOL);
        assertEquals(0.5, effective.vstmax(), TOL);
        assertEquals(-0.6, effective.vstmin(), TOL);
        assertEquals(0.1, effective.veryLowBand().max(), TOL);
        assertEquals(-0.2, effective.veryLowBand().min(), TOL);

        assertEquals(0.0, pss.getData().veryLowBand().gain(), TOL);
        assertEquals(0.0, pss.getData().veryLowBand().frequency(), TOL);
        assertEquals(0.0, pss.getData().k1(), TOL);
        assertEquals(-0.6, pss.getData().vstmax(), TOL);
    }

    private static void assertPss5cSinusoidalResponse(boolean highBand) throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1.0);
        double[] p = new double[Ieee2016PSS5CStabilizerData.PARAMETER_COUNT];
        double frequency = highBand ? 2.0 : 0.6;
        double gain = highBand ? 80.0 : 20.0;
        int offset = highBand ? 12 : 8;
        p[offset] = gain;
        p[offset + 1] = frequency;
        p[offset + 2] = 1.0;
        p[offset + 3] = -1.0;
        p[16] = 5.736;
        p[17] = 6.883;
        p[18] = 8.259;
        p[19] = 1.0;
        p[20] = -1.0;
        Ieee2016PSS5CStabilizer pss = builder.addPss5c("Bus1", "1", p);
        assertTrue(pss.initStates(machine.getDStabBus(), machine));

        double amplitude = 0.001;
        double omega = 2.0 * Math.PI * frequency;
        double dt = 0.0005;
        double duration = highBand ? 4.0 : 8.0;
        double measureAfter = duration - 2.0 / frequency;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        int steps = (int) (duration / dt);
        for (int i = 1; i <= steps; i++) {
            double time = i * dt;
            machine.setSpeed(1.0 + amplitude * Math.sin(omega * time));
            assertTrue(pss.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
            assertTrue(pss.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
            if (time >= measureAfter) {
                min = Math.min(min, pss.getOutput(machine));
                max = Math.max(max, pss.getOutput(machine));
            }
        }

        Complex s = new Complex(0.0, omega);
        Complex upper = Complex.ONE.add(s.divide(8.259 * frequency))
                .divide(Complex.ONE.add(s.divide(6.883 * frequency)));
        Complex lower = Complex.ONE.add(s.divide(6.883 * frequency))
                .divide(Complex.ONE.add(s.divide(5.736 * frequency)));
        Complex transducer;
        if (highBand) {
            transducer = s.multiply(s).multiply(80.0).divide(
                    s.multiply(s).multiply(s)
                            .add(s.multiply(s).multiply(82.0))
                            .add(s.multiply(161.0)).add(80.0));
        } else {
            transducer = Complex.ONE.add(s.multiply(1.759e-3)).divide(
                    Complex.ONE.add(s.multiply(1.7823e-2))
                            .add(s.multiply(s).multiply(1.2739e-4)));
        }
        double expectedAmplitude = amplitude * transducer.multiply(
                upper.subtract(lower)).multiply(gain).abs();
        double measuredAmplitude = 0.5 * (max - min);
        assertEquals(expectedAmplitude, measuredAmplitude,
                expectedAmplitude * 0.01 + 1.0e-7);
    }

    @Test
    void parsePss6c_mapsNative35ParameterRecord() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("pss6c.dyr");
        StringBuilder record = new StringBuilder("1 'PSS6C' '1'");
        for (int i = 0; i < Ieee2016PSS6CStabilizerData.PARAMETER_COUNT; i++) {
            double value = i < 4 ? (i % 2 == 0 ? 1 : 0)
                    : i == 30 ? -1.0 : i == 31 ? -2.0 : i + 1;
            record.append(' ').append(value);
        }
        Files.writeString(dyr, record.append(" /\n").toString());
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);

        parser.parseDynFile(dyr.toString());

        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        Ieee2016PSS6CStabilizer pss = (Ieee2016PSS6CStabilizer) machine.getStabilizer();
        assertNotNull(pss);
        assertEquals(1, pss.getData().ics1());
        assertEquals(0, pss.getData().remoteBus1());
        assertEquals(5.0, pss.getData().ks1(), TOL);
        assertEquals(35.0, pss.getData().tcomp(), TOL);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void pss6c_matchesPublishedCanonicalFrequencyResponse() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1.0);
        double[] p = pss6cParameters();
        p[4] = 0.0; p[5] = 0.2; p[6] = 0.3;
        p[7] = 0.0; p[8] = 0.0; p[9] = 0.1; p[10] = 0.2;
        p[11] = 1.0;
        p[12] = 1.0; p[13] = 0.4;
        p[19] = 3.0; p[20] = 0.5;
        Ieee2016PSS6CStabilizer pss = builder.addPss6c("Bus1", "1", p);
        assertTrue(pss.initStates(machine.getDStabBus(), machine));

        double frequency = 1.0;
        double amplitude = 0.001;
        double omega = 2.0 * Math.PI * frequency;
        double dt = 0.0005;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 1; i <= (int) (8.0 / dt); i++) {
            double time = i * dt;
            machine.setSpeed(1.0 + amplitude * Math.sin(omega * time));
            assertTrue(pss.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
            assertTrue(pss.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
            if (time >= 6.0) {
                min = Math.min(min, pss.getOutput(machine));
                max = Math.max(max, pss.getOutput(machine));
            }
        }

        Complex s = new Complex(0.0, omega);
        Complex inputLag = Complex.ONE.divide(Complex.ONE.add(s.multiply(0.2)));
        Complex washout = s.multiply(1.0).divide(Complex.ONE.add(s.multiply(1.0)));
        Complex canonical = s.multiply(0.5).add(0.4)
                .divide(Complex.ONE.add(s.multiply(0.5)));
        double expected = amplitude * inputLag.multiply(washout)
                .multiply(canonical).multiply(3.0).abs();
        assertEquals(expected, 0.5 * (max - min), expected * 0.01 + 1.0e-7);
    }

    @Test
    void pss6c_appliesPowerWorldCorrectionsWithoutMutatingSourceData() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1.0);
        double[] p = pss6cParameters();
        p[12] = 0.0; p[19] = -1.0;
        p[24] = -0.2; p[25] = 0.1;
        p[26] = -0.3; p[27] = 0.2;
        p[28] = -0.4; p[29] = 0.3;
        Ieee2016PSS6CStabilizer pss = builder.addPss6c("Bus1", "1", p);
        pss.configureIntegrationStep(0.01, 2.0);
        assertTrue(pss.initStates(machine.getDStabBus(), machine));

        assertEquals(0.02, pss.getEffectiveData().k0(), TOL);
        assertEquals(0.02, pss.getEffectiveData().ks(), TOL);
        assertEquals(0.1, pss.getEffectiveData().vsi1max(), TOL);
        assertEquals(-0.2, pss.getEffectiveData().vsi1min(), TOL);
        assertEquals(0.3, pss.getEffectiveData().vstmax(), TOL);
        assertEquals(-0.4, pss.getEffectiveData().vstmin(), TOL);
        assertEquals(0.0, pss.getData().k0(), TOL);
        assertEquals(-1.0, pss.getData().ks(), TOL);
    }

    @Test
    void pss6c_computesCompensatedFrequencyInput() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        double[] p = pss6cParameters();
        p[0] = 7;
        p[33] = 0.0;
        p[34] = 0.0;
        Ieee2016PSS6CStabilizer pss = builder.addPss6c("Bus1", "1", p);
        assertTrue(pss.initStates(machine.getDStabBus(), machine));

        machine.getDStabBus().setVoltage(new Complex(Math.cos(0.01), Math.sin(0.01)));
        assertTrue(pss.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER, machine, 0));

        double expected = 0.01 / (2.0 * Math.PI
                * machine.getDStabBus().getNetwork().getFrequency() * 0.01);
        assertEquals(expected, pss.getInput1Signal(), 1.0e-8);
    }

    @Test
    void pss6c_filtersPowerForActivationHysteresis() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        double[] p = pss6cParameters();
        p[30] = 0.15;
        p[31] = 0.10;
        p[32] = 0.02;
        Ieee2016PSS6CStabilizer pss = builder.addPss6c("Bus1", "1", p);
        machine.setPe(0.20);
        assertTrue(pss.initStates(machine.getDStabBus(), machine));
        assertTrue(pss.isPssActive());

        machine.setPe(0.05);
        for (int i = 0; i < 20; i++) {
            assertTrue(pss.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
            assertTrue(pss.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
        }
        assertTrue(pss.getFilteredPgen() < 0.10);
        assertTrue(!pss.isPssActive());
        assertEquals(0.0, pss.getOutput(machine), TOL);

        machine.setPe(0.20);
        for (int i = 0; i < 20; i++) {
            assertTrue(pss.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
            assertTrue(pss.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
        }
        assertTrue(pss.getFilteredPgen() > 0.15);
        assertTrue(pss.isPssActive());
    }

    private static double[] pss6cParameters() {
        double[] p = new double[Ieee2016PSS6CStabilizerData.PARAMETER_COUNT];
        p[0] = 1; p[1] = 0; p[2] = 1; p[3] = 0;
        p[24] = 100.0; p[25] = -100.0;
        p[26] = 100.0; p[27] = -100.0;
        p[28] = 100.0; p[29] = -100.0;
        p[30] = -1.0; p[31] = -2.0;
        return p;
    }

    @Test
    void parsePss7c_mapsPowerWorld39ParameterRecord() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("pss7c.dyr");
        double[] values = pss7cParameters();
        values[4] = 2; values[5] = 3; values[6] = 4.5;
        values[38] = 0.125;
        StringBuilder record = new StringBuilder("1 'PSS7C' '1'");
        for (double value : values) record.append(' ').append(value);
        Files.writeString(dyr, record.append(" /\n").toString());
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);

        parser.parseDynFile(dyr.toString());

        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        Ieee2016PSS7CStabilizer pss = (Ieee2016PSS7CStabilizer) machine.getStabilizer();
        assertNotNull(pss);
        assertEquals(2, pss.getData().m());
        assertEquals(3, pss.getData().n());
        assertEquals(4.5, pss.getData().ks1(), TOL);
        assertEquals(0.125, pss.getData().tcomp(), TOL);
        assertTrue(parser.getLastImportReport().isStrictlyComplete());
    }

    @Test
    void pss7c_matchesPublishedDualWashoutCanonicalFrequencyResponse() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        machine.setSpeed(1.0);
        double[] p = pss7cParameters();
        p[4] = 1; p[5] = 2; p[15] = 0.05; p[16] = 0.10;
        p[6] = 3.0; p[7] = 0.0; p[8] = 0.0;
        p[9] = 0.2; p[11] = 1.0; p[12] = 0.5;
        p[17] = 1.0; p[18] = 0.4; p[19] = 0.2;
        p[22] = 0.0; p[23] = 0.0;
        p[24] = 0.5; p[25] = 0.4; p[26] = 0.3; p[27] = 0.2;
        Ieee2016PSS7CStabilizer pss = builder.addPss7c("Bus1", "1", p);
        assertTrue(pss.initStates(machine.getDStabBus(), machine));
        assertEquals(0.5, pss.getEffectiveData().tw2(), TOL);

        double frequency = 1.0;
        double amplitude = 0.001;
        double omega = 2.0 * Math.PI * frequency;
        double dt = 0.0005;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 1; i <= (int) (8.0 / dt); i++) {
            double time = i * dt;
            machine.setSpeed(1.0 + amplitude * Math.sin(omega * time));
            assertTrue(pss.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
            assertTrue(pss.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
            if (time >= 6.0) {
                min = Math.min(min, pss.getOutput(machine));
                max = Math.max(max, pss.getOutput(machine));
            }
        }

        Complex s = new Complex(0.0, omega);
        Complex wash1 = s.multiply(1.0).divide(Complex.ONE.add(s.multiply(1.0)));
        Complex wash2 = s.multiply(0.5).divide(Complex.ONE.add(s.multiply(0.5)));
        Complex lag = Complex.ONE.divide(Complex.ONE.add(s.multiply(0.2)));
        Complex ramp = Complex.ONE.add(s.multiply(0.05))
                .divide(Complex.ONE.add(s.multiply(0.10))).pow(2);
        Complex a1 = Complex.ONE.divide(s.multiply(0.5));
        Complex a2 = a1.divide(s.multiply(0.4));
        Complex canonical = Complex.ONE.add(a1.multiply(0.4)).add(a2.multiply(0.2))
                .divide(Complex.ONE.add(a1).add(a2));
        double expected = amplitude * wash1.multiply(wash2).multiply(lag)
                .multiply(ramp).multiply(canonical).multiply(3.0).abs();
        assertEquals(expected, 0.5 * (max - min), expected * 0.012 + 1.0e-7);
    }

    @Test
    void pss7c_appliesPowerWorldCorrectionsWithoutMutatingSourceData() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        double[] p = pss7cParameters();
        p[6] = -1.0; p[11] = 0.0; p[17] = 0.0; p[24] = -1.0;
        p[28] = -0.2; p[29] = 0.1;
        p[32] = -0.4; p[33] = 0.3;
        Ieee2016PSS7CStabilizer pss = builder.addPss7c("Bus1", "1", p);
        pss.configureIntegrationStep(0.01, 2.0);
        assertTrue(pss.initStates(machine.getDStabBus(), machine));

        assertEquals(0.02, pss.getEffectiveData().ks1(), TOL);
        assertEquals(0.02, pss.getEffectiveData().tw1(), TOL);
        assertEquals(0.02, pss.getEffectiveData().k0(), TOL);
        assertEquals(0.02, pss.getEffectiveData().ti1(), TOL);
        assertEquals(0.1, pss.getEffectiveData().vsi1max(), TOL);
        assertEquals(-0.2, pss.getEffectiveData().vsi1min(), TOL);
        assertEquals(0.3, pss.getEffectiveData().vstmax(), TOL);
        assertEquals(-0.4, pss.getEffectiveData().vstmin(), TOL);
        assertEquals(-1.0, pss.getData().ks1(), TOL);
        assertEquals(0.0, pss.getData().tw1(), TOL);
    }

    @Test
    void pss7c_computesCompensatedFrequencyInput() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        double[] p = pss7cParameters();
        p[0] = 7;
        p[37] = 0.0;
        p[38] = 0.0;
        Ieee2016PSS7CStabilizer pss = builder.addPss7c("Bus1", "1", p);
        assertTrue(pss.initStates(machine.getDStabBus(), machine));

        machine.getDStabBus().setVoltage(new Complex(Math.cos(0.01), Math.sin(0.01)));
        assertTrue(pss.nextStep(0.01, DynamicSimuMethod.MODIFIED_EULER, machine, 0));

        double expected = 0.01 / (2.0 * Math.PI
                * machine.getDStabBus().getNetwork().getFrequency() * 0.01);
        assertEquals(expected, pss.getInput1Signal(), 1.0e-8);
    }

    @Test
    void pss7c_filtersPowerForActivationHysteresis() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        double[] p = pss7cParameters();
        p[34] = 0.15; p[35] = 0.10; p[36] = 0.02;
        Ieee2016PSS7CStabilizer pss = builder.addPss7c("Bus1", "1", p);
        machine.setPe(0.20);
        assertTrue(pss.initStates(machine.getDStabBus(), machine));
        assertTrue(pss.isPssActive());

        machine.setPe(0.05);
        for (int i = 0; i < 20; i++) {
            assertTrue(pss.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
            assertTrue(pss.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
        }
        assertTrue(pss.getFilteredPgen() < 0.10);
        assertTrue(!pss.isPssActive());

        machine.setPe(0.20);
        for (int i = 0; i < 20; i++) {
            assertTrue(pss.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
            assertTrue(pss.nextStep(0.005, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
        }
        assertTrue(pss.getFilteredPgen() > 0.15);
        assertTrue(pss.isPssActive());
    }

    private static double[] pss7cParameters() {
        double[] p = new double[Ieee2016PSS7CStabilizerData.PARAMETER_COUNT];
        p[0] = 1; p[1] = 0; p[2] = 1; p[3] = 0;
        p[4] = 0; p[5] = 0; p[6] = 1.0;
        p[11] = 1.0; p[12] = 1.0; p[13] = 1.0; p[14] = 1.0;
        p[17] = 1.0; p[24] = 1.0; p[25] = 1.0; p[26] = 1.0; p[27] = 1.0;
        p[28] = 100.0; p[29] = -100.0;
        p[30] = 100.0; p[31] = -100.0;
        p[32] = 100.0; p[33] = -100.0;
        p[34] = -1.0; p[35] = -2.0;
        return p;
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

    @Test
    void pss2aRejectsImproperRampFilterButAllowsDefinedBypasses() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();

        assertNull(builder.addPss2a(
                "Bus1", "1", 1, 0, 3, 0, 1, 1,
                0.2, 0.0, 0.0, 0.2, 0.0, 0.0,
                1.0, 1.0, 0.1, 0.0, 1.0,
                0.0, 0.0, 0.0, 0.0, 1.0, -1.0),
                "T9=0 with active nonzero T8 is an improper differentiating transfer function");

        assertNotNull(builder.addPss2a(
                "Bus1", "1", 1, 0, 3, 0, 1, 1,
                0.2, 0.0, 0.0, 0.2, 0.0, 0.0,
                1.0, 1.0, 0.0, 0.0, 1.0,
                0.0, 0.0, 0.0, 0.0, 1.0, -1.0),
                "T8=T9=0 is an algebraic unity stage");

        assertNotNull(builder.addPss2a(
                "Bus1", "1", 1, 0, 3, 0, 0, 0,
                0.2, 0.0, 0.0, 0.2, 0.0, 0.0,
                1.0, 1.0, 0.1, 0.0, 1.0,
                0.0, 0.0, 0.0, 0.0, 1.0, -1.0),
                "N=0 bypasses the complete ramp-tracking filter");
    }

    @Test
    void strictPss2aImportRejectsImproperRampFilter() throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Path dyr = tempDir.resolve("pss2a-improper-ramp-filter.dyr");
        Files.writeString(dyr, "1 'PSS2A' '1' 1 0 3 0 1 1 "
                + "0.2 0 0 0.2 0 0 1 1 0.1 0 1 0 0 0 0 1 -1 /\n");
        PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);

        assertThrows(InterpssException.class, () -> parser.parseDynFile(dyr.toString()));
        assertEquals(1, parser.getLastImportReport().count(DynamicModelImportStatus.REJECTED));
    }

    @ParameterizedTest(name = "PSS2A single-washout {0} response")
    @CsvSource({"step", "ramp", "sine"})
    void pss2aSyntheticResponsesMatchPublishedWashoutTransferFunction(String waveform)
            throws Exception {
        DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
        Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
        Ieee1992PSS2AStabilizer pss = builder.addPss2a(
                "Bus1", "1", 3, 0, 3, 0, 0, 0,
                0.2, 0.0, 0.0, 0.2, 0.0, 0.0,
                0.0, 0.0, 0.0, 0.0, 1.0,
                0.0, 0.0, 0.0, 0.0, 10.0, -10.0,
                1.0, 0.0, 0.0, 0.0);
        assertNotNull(pss);
        assertTrue(pss.initStates(machine.getDStabBus(), machine));

        double initialPe = machine.getPe();
        double dt = 0.0005;
        double elapsed = 0.2;
        for (int i = 0; i < Math.round(elapsed / dt); i++) {
            double time = (i + 1) * dt;
            machine.setPe(initialPe + pss2aSyntheticInput(waveform, time));
            assertTrue(pss.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 0));
            assertTrue(pss.nextStep(dt, DynamicSimuMethod.MODIFIED_EULER, machine, 1));
        }

        assertEquals(pss2aSyntheticWashoutExpected(waveform, elapsed),
                pss.getOutput(machine), 2.0e-4);
    }

    private static double pss2aSyntheticInput(String waveform, double time) {
        return switch (waveform) {
            case "step" -> 0.1;
            case "ramp" -> 0.1 * time;
            case "sine" -> 0.1 * Math.sin(2.0 * Math.PI * time);
            default -> throw new IllegalArgumentException("waveform=" + waveform);
        };
    }

    private static double pss2aSyntheticWashoutExpected(String waveform, double time) {
        double washoutTime = 0.2;
        double decay = Math.exp(-time / washoutTime);
        return switch (waveform) {
            case "step" -> 0.1 * decay;
            case "ramp" -> 0.1 * washoutTime * (1.0 - decay);
            case "sine" -> {
                double omegaT = 2.0 * Math.PI * washoutTime;
                yield 0.1 * (omegaT * omegaT * Math.sin(2.0 * Math.PI * time)
                        + omegaT * Math.cos(2.0 * Math.PI * time)
                        - omegaT * decay) / (1.0 + omegaT * omegaT);
            }
            default -> throw new IllegalArgumentException("waveform=" + waveform);
        };
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
