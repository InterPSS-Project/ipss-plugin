package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelImportStatus;
import org.interpss.fadapter.psse.dyr.DynamicModelCatalog;
import org.interpss.dstab.mach.GenqecMachine;
import org.interpss.dstab.mach.GenqejMachine;
import org.interpss.dstab.mach.Gentpj1Machine;
import org.interpss.dstab.mach.GentraMachine;
import org.interpss.dstab.mach.IeeeVoltageCompensatedMachine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.mach.EConstMachine;
import com.interpss.dstab.mach.Eq1Ed1Machine;
import com.interpss.dstab.mach.Eq1Machine;
import com.interpss.dstab.mach.MachineModelType;
import com.interpss.dstab.mach.RoundRotorMachine;
import com.interpss.dstab.mach.SalientPoleMachine;
import org.interpss.numeric.datatype.Unit.UnitType;

/**
 * Unit tests for DStabNetworkBuilder machine APIs.
 */
public class DStabNetworkBuilderMachineTest extends CorePluginTestSetup {

	private static final double TOL = 1.0E-6;

	@TempDir
	Path tempDir;

	@Test
	public void ctorAndAccessor() throws Exception {
		DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
		DStabilityNetwork net = builder.getDStabNetwork();
		assertNotNull(net);
		assertEquals(1, net.getNoBus());
	}

	@Test
	public void addGenrou_setsRoundRotorFields() throws Exception {
		DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
		RoundRotorMachine mach = builder.addGenrou("Bus1", "1",
				100.0, 16.5,
				8.0, 0.03, 0.4, 0.05,
				5.0, 5.0,
				1.8, 1.7, 0.3, 0.55, 0.25, 0.15,
				0.1, 0.2);

		assertNotNull(mach);
		assertTrue(mach instanceof IeeeVoltageCompensatedMachine);
		assertEquals("Bus1-mach1", mach.getId());
		assertEquals(MachineModelType.EQ11_ED11_ROUND_ROTOR, mach.getMachType());
		assertEquals(5.0, mach.getH(), TOL);
		// PSS/E D is pu torque / pu speed. The core stores percent MW/Hz.
		assertEquals(5.0 * 100.0 / builder.getDStabNetwork().getFrequency(),
				mach.getD(), TOL);
		assertEquals(0.0, mach.getRa(), TOL);
		assertEquals(2, mach.getPoles());
		assertEquals(1.8, mach.getMachData().getXd(), TOL);
		assertEquals(1.7, mach.getXq(), TOL);
		assertEquals(0.3, mach.getXd1(), TOL);
		assertEquals(0.55, mach.getXq1(), TOL);
		assertEquals(0.25, mach.getXd11(), TOL);
		assertEquals(0.25, mach.getXq11(), TOL);
		assertEquals(0.15, mach.getXl(), TOL);
		assertEquals(8.0, mach.getTd01(), TOL);
		assertEquals(0.03, mach.getTd011(), TOL);
		assertEquals(0.4, mach.getTq01(), TOL);
		assertEquals(0.05, mach.getTq011(), TOL);
		assertEquals(0.85, mach.getSliner(), TOL);
		assertEquals(0.1, mach.getSe100(), TOL);
		assertEquals(0.2, mach.getSe120(), TOL);

		DStabGen gen = (DStabGen) builder.getDStabNetwork().getDStabBus("Bus1").getContributeGen("1");
		assertSame(mach, gen.getMach());
		assertSame(mach, builder.getDStabNetwork().getMachine("Bus1-mach1"));
	}

	@Test
	public void parseGenqec_createsDedicatedMachine() throws Exception {
		DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
		Path dyr = tempDir.resolve("genqec.dyr");
		Files.writeString(dyr,
				"1 'GENQEC' '1' 0.25 6.81 0.02 0.85 0.02 3.17 0.0 "
				+ "2.37 1.87 0.32 0.52 0.28 0.20 0.19 0.233 0.797 "
				+ "0.10 1 /\n");

		new PSSEDStabDirectParser(builder).parseDynFile(dyr.toString());
		GenqecMachine mach = (GenqecMachine) builder.getDStabNetwork().getMachine("Bus1-mach1");
		assertNotNull(mach);
		assertEquals(0.28, mach.getXd11(), TOL);
		assertEquals(0.20, mach.getXq11(), TOL);
		assertEquals(0.0, mach.getRa(), TOL);
		assertEquals(0.0, mach.getGenqecData().rcomp(), TOL);
		assertEquals(0.0, mach.getGenqecData().xcomp(), TOL);
		assertEquals(0.10, mach.getGenqecData().kw(), TOL);
		assertEquals(0.25, mach.getGenqecData().accel(), TOL);
		assertEquals(1, mach.getGenqecData().satFunc());
	}

	@Test
	public void parseGenqecu_mapsNativeWrapperWithoutPrivateFixtureData() throws Exception {
		DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
		Path dyr = tempDir.resolve("genqecu.dyr");
		Files.writeString(dyr,
				"1 'USRMDL' '1' 'GENQECU' 1 1 1 16 6 1 1 "
				+ "6.40 0.032 0.68 0.043 4.40 0.07 2.05 1.74 0.34 0.49 "
				+ "0.089 0.12 0.05 0.05 0.24 0.035 /\n");

		PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
		parser.parseDynFile(dyr.toString());
		GenqecMachine mach = (GenqecMachine) builder.getDStabNetwork().getMachine("Bus1-mach1");
		assertNotNull(mach);
		assertEquals("GENQEC", mach.getName());
		assertEquals(0.035, mach.getGenqecData().kw(), TOL);
		assertEquals(0.0, mach.getGenqecData().accel(), TOL);
		assertEquals(1, mach.getGenqecData().satFunc());
		assertEquals(6, mach.getNamedStates().size());
		assertEquals(23, DynamicModelCatalog.find("GENQECU").orElseThrow()
				.recordSchema().acceptedParameterCounts().stream().mapToInt(Integer::intValue)
				.max().orElseThrow());
		assertEquals(1, parser.getLastImportReport().count(DynamicModelImportStatus.ATTACHED));
	}

	@Test
	public void parseGenqecu_rejectsIncorrectNativeAllocationHeader() throws Exception {
		Path dyr = tempDir.resolve("genqecu-invalid-allocation.dyr");
		Files.writeString(dyr,
				"1 'USRMDL' '1' 'GENQECU' 1 1 1 16 5 1 1 "
				+ "6.40 0.032 0.68 0.043 4.40 0.07 2.05 1.74 0.34 0.49 "
				+ "0.089 0.12 0.05 0.05 0.24 0.035 /\n");
		PSSEDStabDirectParser parser = new PSSEDStabDirectParser(
				DStabBuilderTestFixture.createBuilder()).setStrictImport(true);
		assertThrows(com.interpss.common.exp.InterpssException.class,
				() -> parser.parseDynFile(dyr.toString()));
	}

	@Test
	public void parseGenqej_reusesGenqecDynamicsAndMapsKis() throws Exception {
		DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
		Path dyr = tempDir.resolve("genqej.dyr");
		Files.writeString(dyr,
				"1 'USRMDL' '1' 'GENQEJU' 1 1 1 16 6 1 1 "
				+ "6.81 0.02 0.85 0.02 3.17 0.0 2.37 1.87 0.32 0.52 "
				+ "0.28 0.20 0.19 0.233 0.797 0.15 /\n");

		PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
		parser.parseDynFile(dyr.toString());
		GenqejMachine mach = (GenqejMachine) builder.getDStabNetwork().getMachine("Bus1-mach1");
		assertNotNull(mach);
		assertEquals("GENQEJ", mach.getName());
		assertEquals(0.15, mach.getGenqejData().kis(), TOL);
		assertEquals(0.0, mach.getGenqejData().accel(), TOL);
		assertEquals(0.0, mach.getGenqecData().kw(), TOL);
		assertEquals(6, mach.getNamedStates().size());
		assertEquals(23, DynamicModelCatalog.find("GENQEJU").orElseThrow()
				.recordSchema().acceptedParameterCounts().stream().mapToInt(Integer::intValue)
				.max().orElseThrow());
		assertEquals(1, parser.getLastImportReport().count(DynamicModelImportStatus.ATTACHED));
	}

	@Test
	public void parseGenqej_rejectsIncorrectNativeAllocationHeader() throws Exception {
		Path dyr = tempDir.resolve("genqej-invalid-allocation.dyr");
		Files.writeString(dyr,
				"1 'USRMDL' '1' 'GENQEJU' 1 1 1 15 6 1 1 "
				+ "6.81 0.02 0.85 0.02 3.17 0.0 2.37 1.87 0.32 0.52 "
				+ "0.28 0.20 0.19 0.233 0.797 0.15 /\n");
		PSSEDStabDirectParser parser = new PSSEDStabDirectParser(
				DStabBuilderTestFixture.createBuilder()).setStrictImport(true);
		assertThrows(com.interpss.common.exp.InterpssException.class,
				() -> parser.parseDynFile(dyr.toString()));
	}

	@Test
	public void parseGentpj1_mapsNativeSchemaWithoutPrivateFixtureData() throws Exception {
		DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
		DStabGen gen = (DStabGen) builder.getDStabNetwork().getDStabBus("Bus1")
				.getContributeGen("1");
		gen.setMvaBase(125.0);
		gen.setSourceZ(new org.apache.commons.math3.complex.Complex(0.00625, 0.24));
		Path dyr = tempDir.resolve("gentpj1-synthetic.dyr");
		// Deliberately synthetic, perturbed constants; no private case record is copied.
		Files.writeString(dyr, "1 'GENTPJ1' '1' 7.25 0.035 0.72 0.045 4.15 1.30 "
				+ "2.05 1.91 0.36 0.59 0.27 0.23 0.16 0.075 0.31 0.22 /\n");

		PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
		parser.parseDynFile(dyr.toString());

		Gentpj1Machine mach = (Gentpj1Machine) builder.getDStabNetwork()
				.getMachine("Bus1-mach1");
		assertNotNull(mach);
		assertEquals("GENTPJ1", mach.getName());
		assertEquals(7.25, mach.getTd01(), TOL);
		assertEquals(0.035, mach.getTd011(), TOL);
		assertEquals(0.72, mach.getTq01(), TOL);
		assertEquals(0.045, mach.getTq011(), TOL);
		assertEquals(4.15, mach.getH(), TOL);
		assertEquals(1.30 * 100.0 / builder.getDStabNetwork().getFrequency(), mach.getD(), TOL);
		assertEquals(2.05, mach.getMachData().getXd(), TOL);
		assertEquals(1.91, mach.getXq(), TOL);
		assertEquals(0.36, mach.getXd1(), TOL);
		assertEquals(0.59, mach.getXq1(), TOL);
		assertEquals(0.27, mach.getXd11(), TOL);
		assertEquals(0.23, mach.getXq11(), TOL);
		assertEquals(0.16, mach.getXl(), TOL);
		assertEquals(0.22, mach.getGentpj1Data().kis(), TOL);
		assertEquals(2, mach.getGenqecData().satFunc());
		assertEquals(0.0078125, mach.getRa(), TOL);
		assertEquals(1, parser.getLastImportReport().count(DynamicModelImportStatus.ATTACHED));
		assertTrue(parser.getLastImportReport().isStrictlyComplete());
	}

	@Test
	public void parseGentra_mapsExactNativeSchemaAndTransientBoundary() throws Exception {
		DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
		DStabGen gen = (DStabGen) builder.getDStabNetwork().getDStabBus("Bus1")
				.getContributeGen("1");
		gen.setMvaBase(120.0);
		gen.setSourceZ(new org.apache.commons.math3.complex.Complex(0.0048, 0.34));
		Path dyr = tempDir.resolve("gentra-synthetic.dyr");
		// Deliberately synthetic constants; no supplied case record is copied.
		Files.writeString(dyr,
				"1 'GENTRA' '1' 5.45 4.10 0.90 1.69 1.56 0.34 0.07 0.29 0.18 /\n");

		PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
		parser.parseDynFile(dyr.toString());

		GentraMachine mach = (GentraMachine) builder.getDStabNetwork()
				.getMachine("Bus1-mach1");
		assertNotNull(mach);
		assertEquals("GENTRA", mach.getName());
		assertEquals(MachineModelType.EQ1_MODEL, mach.getMachType());
		assertEquals(5.45, mach.getTd01(), TOL);
		assertEquals(4.10, mach.getH(), TOL);
		assertEquals(0.90 * 100.0 / builder.getDStabNetwork().getFrequency(),
				mach.getD(), TOL);
		assertEquals(1.69, mach.getMachData().getXd(), TOL);
		assertEquals(1.56, mach.getXq(), TOL);
		assertEquals(0.34, mach.getXd1(), TOL);
		assertEquals(0.34, mach.getXl(), TOL);
		assertEquals(0.00576, mach.getRa(), TOL);
		assertEquals(0.07, mach.getSe100(), TOL);
		assertEquals(0.29, mach.getSe120(), TOL);
		assertEquals(0.18, mach.getGentraData().accelerationFactor(), TOL);
		assertEquals(3, mach.getNamedStates().size());
		assertEquals(1, parser.getLastImportReport().count(DynamicModelImportStatus.ATTACHED));
		assertTrue(parser.getLastImportReport().isStrictlyComplete());
	}

	@Test
	public void parseIeeeVc_defersUntilMachineAndMapsMachineBaseConstants() throws Exception {
		DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
		DStabGen gen = (DStabGen) builder.getDStabNetwork().getDStabBus("Bus1")
				.getContributeGen("1");
		gen.setMvaBase(120.0);
		Path dyr = tempDir.resolve("ieeevc-synthetic.dyr");
		// IEEEVC intentionally precedes its machine to prove order-independent attachment.
		Files.writeString(dyr,
				"1 'IEEEVC' '1' 0.013 -0.087 /\n"
				+ "1 'GENROU' '1' 5.45 0.04 0.51 0.07 4.2 0.7 "
				+ "1.71 1.66 0.30 0.56 0.22 0.13 0.08 0.31 /\n");

		PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
		parser.parseDynFile(dyr.toString());

		IeeeVoltageCompensatedMachine machine = (IeeeVoltageCompensatedMachine)
				builder.getDStabNetwork().getMachine("Bus1-mach1");
		assertNotNull(machine);
		assertEquals(0.013, machine.getIeeeVcData().rc(), TOL);
		assertEquals(-0.087, machine.getIeeeVcData().xc(), TOL);
		assertEquals(100.0 / 120.0, machine.getZMultiFactor(), TOL);
		assertEquals(2, parser.getLastImportReport().count(DynamicModelImportStatus.ATTACHED));
		assertTrue(parser.getLastImportReport().isStrictlyComplete());
	}

	@Test
	public void parseGenrou_mapsEveryPsseParameterToCoreMachine() throws Exception {
		DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
		DStabGen gen = (DStabGen) builder.getDStabNetwork().getDStabBus("Bus1")
				.getContributeGen("1");
		gen.setMvaBase(125.0);
		// RAW ZSORCE is on system base here: 0.012 machine-pu Ra * 100/125.
		gen.setSourceZ(new org.apache.commons.math3.complex.Complex(0.0096, 0.20));
		Path dyr = tempDir.resolve("genrou.dyr");
		Files.writeString(dyr, "1 'GENROE' '1' 8.0 0.03 0.4 0.05 5.0 3.0 "
				+ "1.8 1.7 0.3 0.55 0.25 0.15 0.10 0.20 /\n");
		PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);

		parser.parseDynFile(dyr.toString());

		RoundRotorMachine mach = (RoundRotorMachine) builder.getDStabNetwork()
				.getMachine("Bus1-mach1");
		assertNotNull(mach);
		assertEquals(8.0, mach.getTd01(), TOL);
		assertEquals(0.03, mach.getTd011(), TOL);
		assertEquals(0.4, mach.getTq01(), TOL);
		assertEquals(0.05, mach.getTq011(), TOL);
		assertEquals(5.0, mach.getH(), TOL);
		assertEquals(3.0 * 100.0 / builder.getDStabNetwork().getFrequency(),
				mach.getD(), TOL);
		assertEquals(1.8, mach.getMachData().getXd(), TOL);
		assertEquals(1.7, mach.getXq(), TOL);
		assertEquals(0.3, mach.getXd1(), TOL);
		assertEquals(0.55, mach.getXq1(), TOL);
		assertEquals(0.25, mach.getXd11(), TOL);
		assertEquals(0.25, mach.getXq11(), TOL);
		assertEquals(0.15, mach.getXl(), TOL);
		assertEquals(0.012, mach.getRa(), TOL);
		assertEquals(125.0, mach.getRating(UnitType.mVA,
				builder.getDStabNetwork().getBaseKva()), TOL);
		assertEquals(10.0, mach.getSe100(), TOL);
		assertEquals(20.0, mach.getSe120(), TOL);
		assertEquals("GENROU", parser.getLastImportReport().entries().get(0)
				.canonicalModelName());
		assertTrue(parser.getLastImportReport().isStrictlyComplete());
	}

	@Test
	public void parseGensal_mapsEveryPsseParameterToCoreMachine() throws Exception {
		DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
		DStabGen gen = (DStabGen) builder.getDStabNetwork().getDStabBus("Bus1")
				.getContributeGen("1");
		gen.setSourceZ(new org.apache.commons.math3.complex.Complex(0.004, 0.25));
		Path dyr = tempDir.resolve("gensal.dyr");
		Files.writeString(dyr, "1 'GENSAE' '1' 8.0 0.04 0.06 4.0 2.0 "
				+ "1.8 1.7 0.3 0.25 0.15 0.10 0.20 /\n");
		PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);

		parser.parseDynFile(dyr.toString());

		SalientPoleMachine mach = (SalientPoleMachine) builder.getDStabNetwork()
				.getMachine("Bus1-mach1");
		assertNotNull(mach);
		assertEquals(8.0, mach.getTd01(), TOL);
		assertEquals(0.04, mach.getTd011(), TOL);
		assertEquals(0.06, mach.getTq011(), TOL);
		assertEquals(4.0, mach.getH(), TOL);
		assertEquals(2.0 * 100.0 / builder.getDStabNetwork().getFrequency(),
				mach.getD(), TOL);
		assertEquals(1.8, mach.getMachData().getXd(), TOL);
		assertEquals(1.7, mach.getXq(), TOL);
		assertEquals(0.3, mach.getXd1(), TOL);
		assertEquals(0.25, mach.getXd11(), TOL);
		assertEquals(0.25, mach.getXq11(), TOL);
		assertEquals(0.15, mach.getXl(), TOL);
		assertEquals(0.004, mach.getRa(), TOL);
		assertEquals(10.0, mach.getSe100(), TOL);
		assertEquals(20.0, mach.getSe120(), TOL);
		assertEquals("GENSAL", parser.getLastImportReport().entries().get(0)
				.canonicalModelName());
	}

	@Test
	public void addGensal_setsSalientPoleFields() throws Exception {
		DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
		SalientPoleMachine mach = builder.addGensal("Bus1", "1",
				100.0, 16.5,
				8.96, 0.04, 0.06,
				23.64, 0.0,
				0.146, 0.0969, 0.0608, 0.04, 0.0336,
				0.0, 0.0);

		assertNotNull(mach);
		assertEquals("Bus1-mach1", mach.getId());
		assertEquals(MachineModelType.EQ11_SALIENT_POLE, mach.getMachType());
		assertEquals(23.64, mach.getH(), TOL);
		assertEquals(0.0336, mach.getXl(), TOL);
		assertEquals(0.0608, mach.getXd1(), TOL);
		assertEquals(8.96, mach.getTd01(), TOL);
		assertEquals(0.04, mach.getTd011(), TOL);
		assertEquals(0.06, mach.getTq011(), TOL);
		assertEquals(0.04, mach.getXd11(), TOL);
		assertEquals(0.04, mach.getXq11(), TOL);
		assertEquals(0.0, mach.getRa(), TOL);
	}

	@Test
	public void addEq1Ed1_setsFields() throws Exception {
		DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
		Eq1Ed1Machine mach = builder.addEq1Ed1("Bus1", "1",
				100.0, 16.5,
				6.0, 0.5,
				4.0, 0.0,
				1.6, 1.5, 0.3, 0.5, 0.12,
				0.0, 0.0);

		assertNotNull(mach);
		assertEquals(MachineModelType.EQ1_ED1_MODEL, mach.getMachType());
		assertEquals(4.0, mach.getH(), TOL);
		assertEquals(1.6, mach.getMachData().getXd(), TOL);
		assertEquals(1.5, mach.getXq(), TOL);
		assertEquals(0.3, mach.getXd1(), TOL);
		assertEquals(0.5, mach.getXq1(), TOL);
		assertEquals(0.12, mach.getXl(), TOL);
		assertEquals(6.0, mach.getTd01(), TOL);
		assertEquals(0.5, mach.getTq01(), TOL);
	}

	@Test
	public void addEq1_setsFields() throws Exception {
		DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
		Eq1Machine mach = builder.addEq1("Bus1", "1",
				100.0, 16.5,
				6.0,
				4.0, 0.0,
				1.6, 1.5, 0.3, 0.12,
				0.0, 0.0);

		assertNotNull(mach);
		assertEquals(MachineModelType.EQ1_MODEL, mach.getMachType());
		assertEquals(4.0, mach.getH(), TOL);
		assertEquals(0.3, mach.getXd1(), TOL);
		assertEquals(6.0, mach.getTd01(), TOL);
		assertEquals(0.12, mach.getXl(), TOL);
	}

	@Test
	public void addGencls_setsClassicalFields() throws Exception {
		DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
		EConstMachine mach = builder.addGencls("Bus1", "1",
				100.0, 16.5,
				5.0, 0.0, 0.01, 0.2);

		assertNotNull(mach);
		assertEquals(MachineModelType.ECONSTANT, mach.getMachType());
		assertEquals("Bus1-mach1", mach.getId());
		assertEquals(5.0, mach.getH(), TOL);
		assertEquals(0.01, mach.getRa(), TOL);
		assertEquals(0.2, mach.getXd1(), TOL);
	}

	@Test
	public void addInfiniteMachine_attachesEConst() throws Exception {
		DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
		EConstMachine mach = builder.addInfiniteMachine("Bus1", "1");

		assertNotNull(mach);
		assertEquals("Bus1-mach1", mach.getId());
		DStabGen gen = (DStabGen) builder.getDStabNetwork().getDStabBus("Bus1").getContributeGen("1");
		assertSame(mach, gen.getMach());
	}

	@Test
	public void missingBus_returnsNull() throws Exception {
		DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
		assertNull(builder.addGenrou("missing", "1",
				100.0, 16.5, 8.0, 0.03, 0.4, 0.05, 5.0, 0.0,
				1.8, 1.7, 0.3, 0.55, 0.25, 0.15, 0.0, 0.0));
		assertNull(builder.addGensal("missing", "1",
				100.0, 16.5, 8.0, 0.04, 0.06, 5.0, 0.0,
				0.15, 0.1, 0.06, 0.04, 0.03, 0.0, 0.0));
		assertNull(builder.addEq1Ed1("missing", "1",
				100.0, 16.5, 6.0, 0.5, 4.0, 0.0,
				1.6, 1.5, 0.3, 0.5, 0.12, 0.0, 0.0));
		assertNull(builder.addEq1("missing", "1",
				100.0, 16.5, 6.0, 4.0, 0.0,
				1.6, 1.5, 0.3, 0.12, 0.0, 0.0));
		assertNull(builder.addGencls("missing", "1", 100.0, 16.5, 5.0, 0.0, 0.0, 0.2));
		assertNull(builder.addInfiniteMachine("missing", "1"));
	}
}
