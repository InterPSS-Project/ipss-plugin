package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.junit.jupiter.api.Test;

import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.mach.EConstMachine;
import com.interpss.dstab.mach.Eq1Ed1Machine;
import com.interpss.dstab.mach.Eq1Machine;
import com.interpss.dstab.mach.MachineModelType;
import com.interpss.dstab.mach.RoundRotorMachine;
import com.interpss.dstab.mach.SalientPoleMachine;

/**
 * Unit tests for DStabNetworkBuilder machine APIs.
 */
public class DStabNetworkBuilderMachineTest extends CorePluginTestSetup {

	private static final double TOL = 1.0E-6;

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
				5.0, 0.0,
				1.8, 1.7, 0.3, 0.55, 0.25, 0.15,
				0.1, 0.2);

		assertNotNull(mach);
		assertEquals("Bus1-mach1", mach.getId());
		assertEquals(MachineModelType.EQ11_ED11_ROUND_ROTOR, mach.getMachType());
		assertEquals(5.0, mach.getH(), TOL);
		assertEquals(0.0, mach.getD(), TOL);
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
