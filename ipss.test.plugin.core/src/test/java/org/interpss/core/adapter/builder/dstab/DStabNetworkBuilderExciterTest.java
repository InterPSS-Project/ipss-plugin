package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.exc.ieee.y1968.type1.Ieee1968Type1Exciter;
import org.interpss.dstab.control.exc.ieee.y1981.dc1.IEEE1981DC1Exciter;
import org.interpss.dstab.control.exc.psse.ieeex1.Ieeex1Exciter;
import org.interpss.dstab.control.exc.ieee.y1981.st1.IEEE1981ST1Exciter;
import org.interpss.dstab.control.exc.ieee.y2005.st3a.IEEE2005ST3AExciter;
import org.interpss.dstab.control.exc.psse.esdc1a.Esdc1aExciter;
import org.interpss.dstab.control.exc.psse.esdc2a.Esdc2aExciter;
import org.interpss.dstab.control.pss.psse.ieeest.IeeestStabilizer;
import org.interpss.dstab.control.exc.simple.SimpleExciter;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.controller.cml.field.block.GainBlock;

/**
 * Unit tests for DStabNetworkBuilder exciter APIs.
 */
public class DStabNetworkBuilderExciterTest extends CorePluginTestSetup {

	private static final double TOL = 1.0E-6;

	@TempDir
	Path tempDir;

	@Test
	public void addExcIeeet1_setsDataAndAttaches() throws Exception {
		DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
		Ieee1968Type1Exciter exc = builder.addExcIeeet1("Bus1", "1",
				0.02, 200.0, 0.05, 5.0, -5.0,
				1.0, 0.5, 0.03, 1.0,
				3.0, 0.1, 4.0, 0.2);

		assertNotNull(exc);
		assertEquals(0.02, exc.getData().getTr(), TOL);
		assertEquals(200.0, exc.getData().getKa(), TOL);
		assertEquals(0.05, exc.getData().getTa(), TOL);
		assertEquals(5.0, exc.getData().getVrmax(), TOL);
		assertEquals(-5.0, exc.getData().getVrmin(), TOL);
		assertEquals(1.0, exc.getData().getKe(), TOL);
		assertEquals(0.5, exc.getData().getTe(), TOL);
		assertEquals(0.03, exc.getData().getKf(), TOL);
		assertEquals(1.0, exc.getData().getTf(), TOL);
		assertEquals(3.0, exc.getData().getE1(), TOL);
		assertEquals(0.1, exc.getData().getSeE1(), TOL);
		assertEquals(4.0, exc.getData().getE2(), TOL);
		assertEquals(0.2, exc.getData().getSeE2(), TOL);

		Machine mach = builder.getDStabNetwork().getMachine("Bus1-mach1");
		assertSame(exc, mach.getExciter());
	}

	@Test
	public void addExcIeee1981Dc1_setsData_andTeZeroBecomes001() throws Exception {
		DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
		IEEE1981DC1Exciter exc = builder.addExcIeee1981Dc1("Bus1", "1",
				40.0, 0.05, 1.0, 1.0,
				5.0, -5.0,
				1.0, 0.0, 0.03, 1.0,
				3.0, 0.1, 4.0, 0.2);

		assertNotNull(exc);
		assertEquals(40.0, exc.getData().getKa(), TOL);
		assertEquals(0.05, exc.getData().getTa(), TOL);
		assertEquals(1.0, exc.getData().getTc(), TOL);
		assertEquals(1.0, exc.getData().getTb(), TOL);
		assertEquals(0.001, exc.getData().getTe(), TOL);
		assertEquals(0.1, exc.getData().getSe_e1(), TOL);
		assertEquals(0.2, exc.getData().getSe_e2(), TOL);
		assertSame(exc, builder.getDStabNetwork().getMachine("Bus1-mach1").getExciter());
	}

	@Test
	public void addExcIeee1981St1_setsData() throws Exception {
		DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
		IEEE1981ST1Exciter exc = builder.addExcIeee1981St1("Bus1", "1",
				200.0, 0.01, 1.0, 1.0,
				5.0, -5.0,
				0.03, 1.0, 0.1,
				0.2, -0.2);

		assertNotNull(exc);
		assertEquals(200.0, exc.getData().getKa(), TOL);
		assertEquals(0.01, exc.getData().getTa(), TOL);
		assertEquals(0.1, exc.getData().getKc(), TOL);
		assertEquals(0.2, exc.getData().getVimax(), TOL);
		assertEquals(-0.2, exc.getData().getVimin(), TOL);
		assertSame(exc, builder.getDStabNetwork().getMachine("Bus1-mach1").getExciter());
	}

	@Test
	public void ieeex1_appliesPowerWorldTimeStepCorrectionsWithoutChangingSourceData()
			throws Exception {
		DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
		IEEE1981DC1Exciter exc = builder.addExcIeeex1("Bus1", "1",
				0.004, 40.0, 0.012, 0.015, 0.0,
				5.0, -5.0, 1.0, 0.005, 0.03, 0.003,
				3.0, 0.1, 4.0, 0.2);

		assertNotNull(exc);
		exc.configureIntegrationStep(0.01, 2.0);
		Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
		machine.setEfd(1.2);
		assertEquals(true, exc.initStates(machine.getDStabBus(), machine));

		assertEquals(0.0, exc.tr, TOL);
		assertEquals(0.02, exc.ta, TOL);
		assertEquals(0.02, exc.tb, TOL);
		assertEquals(0.02, exc.te, TOL);
		assertEquals(0.02, exc.tf, TOL);
		assertEquals(50.0, exc.kint, TOL);
		assertEquals(1.5, exc.k, TOL);

		assertEquals(0.004, exc.getSourceTransducerTimeConstant(), TOL);
		assertEquals(0.012, exc.getData().getTa(), TOL);
		assertEquals(0.015, exc.getData().getTb(), TOL);
		assertEquals(0.005, exc.getData().getTe(), TOL);
		assertEquals(0.003, exc.getData().getTf(), TOL);
	}

	@Test
	public void ieeex1_normalizesAndExpandsOnlyEffectiveRegulatorLimits()
			throws Exception {
		DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
		IEEE1981DC1Exciter exc = builder.addExcIeeex1("Bus1", "1",
				0.02, 40.0, 0.02, 0.0, 0.0,
				-0.1, 0.1, 1.0, 0.6, 0.0, 0.0,
				3.0, 0.0, 4.0, 0.0);

		assertNotNull(exc);
		Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
		machine.setEfd(1.2);
		assertEquals(true, exc.initStates(machine.getDStabBus(), machine));

		assertEquals(1.2, exc.vrmax, TOL);
		assertEquals(-0.1, exc.vrmin, TOL);
		assertEquals(-0.1, exc.getData().getVrmax(), TOL);
		assertEquals(0.1, exc.getData().getVrmin(), TOL);
	}

	@Test
	public void ieeex1_supportsAlgebraicTeAndAdditiveLimiterPorts() throws Exception {
		DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
		Ieeex1Exciter exc = builder.addExcIeeex1("Bus1", "1",
				0.0, 1.0, 0.0, 0.0, 0.0,
				10.0, -10.0, 1.0, 0.0, 0.0, 0.0, 3.0,
				3.0, 0.0, 4.0, 0.0);

		Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
		machine.setEfd(1.2);
		assertEquals(true, exc.initStates(machine.getDStabBus(), machine));
		assertEquals(1.2, exc.getOutput(machine), TOL);
		assertEquals(3.0, exc.getSwitchValue(), TOL);

		exc.setVuel(0.1);
		assertEquals(1.3, exc.getOutput(machine), TOL);
		exc.setVoel(0.2);
		assertEquals(1.5, exc.getOutput(machine), TOL);
	}

	@Test
	public void addExcEsdc2a_usesDedicatedModelAndAttaches() throws Exception {
		DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
		Esdc2aExciter exc = builder.addExcEsdc2a("Bus1", "1",
				0.02, 50.0, 0.05, 0.0, 0.02, 0.0, -3.0,
				0.0, 0.512, 0.07, 1.3, 3.9825, 0.5, 5.31, 1.049);

		assertNotNull(exc);
		assertEquals(Esdc2aExciter.class, exc.getClass());
		assertEquals(0.02, exc.getData().getTr(), TOL);
		assertEquals(0.0, exc.getData().getVrmax(), TOL);
		assertEquals(-3.0, exc.getData().getVrmin(), TOL);
		assertEquals(1.3, exc.getData().getTf(), TOL);
		assertEquals(0.0, exc.getData().getSpdmlt(), TOL);
		assertSame(exc, builder.getDStabNetwork().getMachine("Bus1-mach1").getExciter());
	}

	@Test
	public void parseEsdc2a_mapsSpeedMultiplierAndAppliesItToEfd() throws Exception {
		DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
		Path dyr = tempDir.resolve("esdc2a.dyr");
		Files.writeString(dyr, "1 'ESDC2A' '1' .02 50 .05 .02 0 0 -3 0 .512 .07 1.3 1 3.9825 .5 5.31 1.049 /\n");
		new PSSEDStabDirectParser(builder).setStrictImport(true).parseDynFile(dyr.toString());

		Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
		machine.setSpeed(1.0);
		machine.setEfd(1.2);
		Esdc2aExciter exc = (Esdc2aExciter) machine.getExciter();
		assertEquals(1.0, exc.getData().getSpdmlt(), TOL);
		assertEquals(3.9825, exc.getData().getE1(), TOL);
		assertEquals(1.049, exc.getData().getSe2(), TOL);
		assertEquals(0.8, exc.regulatorLimitScale.eval(new double[] {0.8}), TOL);
		assertEquals(true, exc.initStates(machine.getDStabBus(), machine));
		assertEquals(1.2, exc.getOutput(machine), TOL);

		machine.setSpeed(0.98);
		assertEquals(1.176, exc.getOutput(machine), TOL);
	}

	@Test
	public void parseEsdc1a_reusesDcChainWithConstantRegulatorLimits() throws Exception {
		DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
		Path dyr = tempDir.resolve("esdc1a.dyr");
		Files.writeString(dyr, "1 'ESDC1A' '1' .02 40 .05 .5 1 5 -5 1 .8 .03 1 0 3 .1 4 .2 /\n");
		new PSSEDStabDirectParser(builder).setStrictImport(true).parseDynFile(dyr.toString());

		Machine machine = builder.getDStabNetwork().getMachine("Bus1-mach1");
		machine.setSpeed(1.0);
		machine.setEfd(1.2);
		Esdc1aExciter exc = (Esdc1aExciter) machine.getExciter();
		assertNotNull(exc);
		assertEquals(1.0, exc.getData().getTc(), TOL);
		assertEquals(0.5, exc.getData().getTb(), TOL);
		assertEquals(1.0, exc.regulatorLimitScale.eval(new double[] {0.8}), TOL);
		assertEquals(true, exc.initStates(machine.getDStabBus(), machine));
		assertEquals(1.2, exc.getOutput(machine), TOL);
	}

	@Test
	public void addExcEsst3a_reusesExistingModelAndMapsAllTailParameters() throws Exception {
		DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
		IEEE2005ST3AExciter exc = builder.addExcEsst3a("Bus1", "1",
				0.02, 0.3, -0.2, 8.0, 1.0, 5.0, 20.0, 0.0,
				99.0, -99.0, 1.0, 3.67, 0.435, 6.48, 0.01,
				0.0098, 4.86, 3.33, 0.4, 99.0, 0.0);

		assertNotNull(exc);
		assertEquals(IEEE2005ST3AExciter.class, exc.getClass());
		assertEquals(0.02, exc.getData().getTr(), TOL);
		assertEquals(0.3, exc.getData().getVimax(), TOL);
		assertEquals(-0.2, exc.getData().getVimin(), TOL);
		assertEquals(8.0, exc.getData().getKm(), TOL);
		assertEquals(1.0, exc.getData().getTc(), TOL);
		assertEquals(5.0, exc.getData().getTb(), TOL);
		assertEquals(20.0, exc.getData().getKa(), TOL);
		assertEquals(0.0, exc.getData().getTa(), TOL);
		assertEquals(99.0, exc.getData().getVrmax(), TOL);
		assertEquals(-99.0, exc.getData().getVrmin(), TOL);
		assertEquals(1.0, exc.getData().getKg(), TOL);
		assertEquals(3.67, exc.getData().getKp(), TOL);
		assertEquals(0.435, exc.getData().getKi(), TOL);
		assertEquals(6.48, exc.getData().getVbmax(), TOL);
		assertEquals(0.01, exc.getData().getKc(), TOL);
		assertEquals(0.0098, exc.getData().getXl(), TOL);
		assertEquals(4.86, exc.getData().getVgmax(), TOL);
		assertEquals(3.33, exc.getData().getAngKp(), TOL);
		assertEquals(0.4, exc.getData().getTm(), TOL);
		assertEquals(99.0, exc.getData().getVmmax(), TOL);
		assertEquals(0.0, exc.getData().getVmmin(), TOL);
		var kgField = IEEE2005ST3AExciter.class.getDeclaredField("kgGainBlock");
		// PowerWorld's ESST3A diagram defines KG feedback as an algebraic
		// limited gain; it is not an additional dynamic state.
		assertEquals(GainBlock.class, kgField.getType());
		assertSame(exc, builder.getDStabNetwork().getMachine("Bus1-mach1").getExciter());
	}

	@Test
	public void parseEsst3a_mapsPowerWorldPsseRecordOrder() throws Exception {
		DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
		Path dyr = tempDir.resolve("esst3a.dyr");
		Files.writeString(dyr,
				"1 'ESST3A' '1' 0.02 0.3 -0.2 8.0 1.0 5.0 20.0 0.0 "
				+ "99.0 -99.0 1.0 3.67 0.435 6.48 0.01 0.0098 4.86 3.33 "
				+ "0.4 99.0 0.0 /\n");

		new PSSEDStabDirectParser(builder).parseDynFile(dyr.toString());
		IEEE2005ST3AExciter exc = (IEEE2005ST3AExciter) builder.getDStabNetwork()
				.getMachine("Bus1-mach1").getExciter();
		assertNotNull(exc);
		assertEquals(8.0, exc.getData().getKm(), TOL);
		assertEquals(20.0, exc.getData().getKa(), TOL);
		assertEquals(3.67, exc.getData().getKp(), TOL);
		assertEquals(0.435, exc.getData().getKi(), TOL);
		assertEquals(6.48, exc.getData().getVbmax(), TOL);
		assertEquals(4.86, exc.getData().getVgmax(), TOL);
		assertEquals(3.33, exc.getData().getAngKp(), TOL);
		assertEquals(0.4, exc.getData().getTm(), TOL);
		assertEquals(99.0, exc.getData().getVmmax(), TOL);
		assertEquals(0.0, exc.getData().getVmmin(), TOL);
	}

	@Test
	public void parseIeeest_createsDedicatedStabilizerAndMapsWeccParameters() throws Exception {
		DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
		builder.addExcSimple("Bus1", "1", 50.0, 0.05, 5.0, -5.0);
		Path dyr = tempDir.resolve("ieeest.dyr");
		Files.writeString(dyr,
				"1 'IEEEST' '1' 3 0 0 0 0 0 0 0 0 0 0 0.75 1.0 4.2 -2.0 0.1 -0.1 0 0 /\n");

		new PSSEDStabDirectParser(builder).parseDynFile(dyr.toString());
		IeeestStabilizer pss = (IeeestStabilizer) builder.getDStabNetwork()
				.getMachine("Bus1-mach1").getStabilizer();
		assertNotNull(pss);
		assertEquals(3, pss.getData().mode());
		assertEquals(0.75, pss.getData().t4(), TOL);
		assertEquals(1.0, pss.getData().t5(), TOL);
		assertEquals(4.2, pss.getData().t6(), TOL);
		assertEquals(-2.0, pss.getData().ks(), TOL);
		assertEquals(0.1, pss.getData().lsmax(), TOL);
		assertEquals(-0.1, pss.getData().lsmin(), TOL);
	}

	@Test
	public void addExcSimple_setsData() throws Exception {
		DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
		SimpleExciter exc = builder.addExcSimple("Bus1", "1", 50.0, 0.05, 5.0, -5.0);

		assertNotNull(exc);
		assertEquals(50.0, exc.getData().getKa(), TOL);
		assertEquals(0.05, exc.getData().getTa(), TOL);
		assertEquals(5.0, exc.getData().getVrmax(), TOL);
		assertEquals(-5.0, exc.getData().getVrmin(), TOL);
		assertSame(exc, builder.getDStabNetwork().getMachine("Bus1-mach1").getExciter());
	}

	@Test
	public void noMachine_returnsNull() throws Exception {
		DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
		assertNull(builder.addExcIeeet1("Bus1", "1",
				0.02, 200.0, 0.05, 5.0, -5.0, 1.0, 0.5, 0.03, 1.0, 3.0, 0.1, 4.0, 0.2));
		assertNull(builder.addExcIeee1981Dc1("Bus1", "1",
				40.0, 0.05, 1.0, 1.0, 5.0, -5.0, 1.0, 0.5, 0.03, 1.0, 3.0, 0.1, 4.0, 0.2));
		assertNull(builder.addExcIeee1981St1("Bus1", "1",
				200.0, 0.01, 1.0, 1.0, 5.0, -5.0, 0.03, 1.0, 0.1, 0.2, -0.2));
		assertNull(builder.addExcSimple("Bus1", "1", 50.0, 0.05, 5.0, -5.0));
	}

	@Test
	public void missingBus_returnsNull() throws Exception {
		DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
		assertNull(builder.addExcIeeet1("missing", "1",
				0.02, 200.0, 0.05, 5.0, -5.0, 1.0, 0.5, 0.03, 1.0, 3.0, 0.1, 4.0, 0.2));
		assertNull(builder.addExcSimple("missing", "1", 50.0, 0.05, 5.0, -5.0));
	}
}
