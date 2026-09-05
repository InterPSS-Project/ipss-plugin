package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.interpss.fadapter.psse.PSSEDStabDirectParser;
import org.interpss.fadapter.psse.dyr.DynamicModelImportStatus;
import org.interpss.dstab.mach.GenqecMachine;
import org.interpss.dstab.mach.GenqejMachine;
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
		assertEquals("com.interpss.dstab.mach.impl.RoundRotorMachineImpl",
				mach.getClass().getName());
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
				"1 'GENQEC' '1' 6.81 0.02 0.85 0.02 3.17 0.0 "
				+ "2.37 1.87 0.32 0.52 0.28 0.20 0.19 0.233 0.797 "
				+ "0.003 0.01 0.02 0.10 1 /\n");

		new PSSEDStabDirectParser(builder).parseDynFile(dyr.toString());
		GenqecMachine mach = (GenqecMachine) builder.getDStabNetwork().getMachine("Bus1-mach1");
		assertNotNull(mach);
		assertEquals(0.28, mach.getXd11(), TOL);
		assertEquals(0.20, mach.getXq11(), TOL);
		assertEquals(0.003, mach.getRa(), TOL);
		assertEquals(0.01, mach.getGenqecData().rcomp(), TOL);
		assertEquals(0.02, mach.getGenqecData().xcomp(), TOL);
		assertEquals(0.10, mach.getGenqecData().kw(), TOL);
		assertEquals(1, mach.getGenqecData().satFunc());
	}

	@Test
	public void parseGenqej_reusesGenqecDynamicsAndMapsKis() throws Exception {
		DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
		Path dyr = tempDir.resolve("genqej.dyr");
		Files.writeString(dyr,
				"1 'GENQEJU' '1' 6.81 0.02 0.85 0.02 3.17 0.0 "
				+ "2.37 1.87 0.32 0.52 0.28 0.20 0.19 0.233 0.797 "
				+ "0.003 0.01 0.02 0.15 1 /\n");

		PSSEDStabDirectParser parser = new PSSEDStabDirectParser(builder).setStrictImport(true);
		parser.parseDynFile(dyr.toString());
		GenqejMachine mach = (GenqejMachine) builder.getDStabNetwork().getMachine("Bus1-mach1");
		assertNotNull(mach);
		assertEquals("GENQEJ", mach.getName());
		assertEquals(0.15, mach.getGenqejData().kis(), TOL);
		assertEquals(0.0, mach.getGenqecData().kw(), TOL);
		assertEquals(1, parser.getLastImportReport().count(DynamicModelImportStatus.ATTACHED));
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
