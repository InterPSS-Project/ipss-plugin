package org.interpss.core.adapter.builder.dstab;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.interpss.CorePluginTestSetup;
import org.interpss.dstab.control.gov.ieee.steamTCDR.IeeeSteamTCDRGovernor;
import org.interpss.dstab.control.gov.psse.gast.PsseGASTGasTurGovernor;
import org.interpss.dstab.control.gov.psse.ieesgo.PsseIEESGOSteamTurGovernor;
import org.interpss.dstab.control.gov.psse.tgov1.PsseTGov1SteamTurGovernor;
import org.interpss.dstab.control.gov.simple.SimpleGovernor;
import org.interpss.fadapter.builder.DStabNetworkBuilder;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for DStabNetworkBuilder governor APIs.
 */
public class DStabNetworkBuilderGovernorTest extends CorePluginTestSetup {

	private static final double TOL = 1.0E-6;

	@Test
	public void addGovTgov1_setsDataAndAttaches() throws Exception {
		DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
		PsseTGov1SteamTurGovernor gov = builder.addGovTgov1("Bus1", "1",
				0.05, 0.5, 1.0, 0.0, 1.0, 2.0, 0.0);

		assertNotNull(gov);
		assertEquals(0.05, gov.getData().getR(), TOL);
		assertEquals(0.5, gov.getData().getT1(), TOL);
		assertEquals(1.0, gov.getData().getvMax(), TOL);
		assertEquals(0.0, gov.getData().getvMin(), TOL);
		assertEquals(1.0, gov.getData().getT2(), TOL);
		assertEquals(2.0, gov.getData().getT3(), TOL);
		assertEquals(0.0, gov.getData().getDt(), TOL);
		assertSame(gov, builder.getDStabNetwork().getMachine("Bus1-mach1").getGovernor());
	}

	@Test
	public void addGovGast_setsData() throws Exception {
		DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
		PsseGASTGasTurGovernor gov = builder.addGovGast("Bus1", "1",
				0.05, 0.4, 0.1, 3.0, 1.0, 2.0, 1.0, -0.05, 0.0);

		assertNotNull(gov);
		assertEquals(0.05, gov.getData().getR(), TOL);
		assertEquals(0.4, gov.getData().getT1(), TOL);
		assertEquals(0.1, gov.getData().getT2(), TOL);
		assertEquals(3.0, gov.getData().getT3(), TOL);
		assertEquals(1.0, gov.getData().getLoadLimit(), TOL);
		assertEquals(2.0, gov.getData().getKt(), TOL);
		assertEquals(1.0, gov.getData().getVMax(), TOL);
		assertEquals(-0.05, gov.getData().getVMin(), TOL);
		assertEquals(0.0, gov.getData().getDturb(), TOL);
		assertSame(gov, builder.getDStabNetwork().getMachine("Bus1-mach1").getGovernor());
	}

	@Test
	public void addGovIeesgo_setsData() throws Exception {
		DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
		PsseIEESGOSteamTurGovernor gov = builder.addGovIeesgo("Bus1", "1",
				0.0, 0.0, 0.2, 0.1, 0.2, 0.3,
				0.3, 0.4, 0.3, 1.0, 0.0);

		assertNotNull(gov);
		assertEquals(0.2, gov.getData().getT3(), TOL);
		assertEquals(0.1, gov.getData().getT4(), TOL);
		assertEquals(0.3, gov.getData().getK1(), TOL);
		assertEquals(0.4, gov.getData().getK2(), TOL);
		assertEquals(0.3, gov.getData().getK3(), TOL);
		assertEquals(1.0, gov.getData().getPmax(), TOL);
		assertEquals(0.0, gov.getData().getPmin(), TOL);
		assertSame(gov, builder.getDStabNetwork().getMachine("Bus1-mach1").getGovernor());
	}

	@Test
	public void addGovIeeeg1_setsData() throws Exception {
		DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
		IeeeSteamTCDRGovernor gov = builder.addGovIeeeg1("Bus1", "1",
				20.0, 0.0, 0.0, 0.1,
				0.3, 0.3, 0.2,
				0.2, 5.0, 0.2, 5.0, 0.5,
				-0.1, 0.1, 1.0, 0.0);

		assertNotNull(gov);
		assertEquals(20.0, gov.getData().getK(), TOL);
		assertEquals(0.1, gov.getData().getT3(), TOL);
		assertEquals(0.3, gov.getData().getFvhp(), TOL);
		assertEquals(0.3, gov.getData().getFhp(), TOL);
		assertEquals(0.2, gov.getData().getTch(), TOL);
		assertEquals(0.2, gov.getData().getFip(), TOL);
		assertEquals(5.0, gov.getData().getTrh1(), TOL);
		assertEquals(0.2, gov.getData().getFlp(), TOL);
		assertEquals(5.0, gov.getData().getTrh2(), TOL);
		assertEquals(0.5, gov.getData().getTco(), TOL);
		assertEquals(-0.1, gov.getData().getPdown(), TOL);
		assertEquals(0.1, gov.getData().getPup(), TOL);
		assertEquals(1.0, gov.getData().getPmax(), TOL);
		assertEquals(0.0, gov.getData().getPmin(), TOL);
		assertSame(gov, builder.getDStabNetwork().getMachine("Bus1-mach1").getGovernor());
	}

	@Test
	public void addGovSimple_setsData() throws Exception {
		DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
		SimpleGovernor gov = builder.addGovSimple("Bus1", "1", 20.0, 0.1, 1.0, 0.0);

		assertNotNull(gov);
		assertEquals(20.0, gov.getData().getK(), TOL);
		assertEquals(0.1, gov.getData().getT1(), TOL);
		assertEquals(1.0, gov.getData().getPmax(), TOL);
		assertEquals(0.0, gov.getData().getPmin(), TOL);
		assertSame(gov, builder.getDStabNetwork().getMachine("Bus1-mach1").getGovernor());
	}

	@Test
	public void noMachine_returnsNull() throws Exception {
		DStabNetworkBuilder builder = DStabBuilderTestFixture.createBuilder();
		assertNull(builder.addGovTgov1("Bus1", "1", 0.05, 0.5, 1.0, 0.0, 1.0, 2.0, 0.0));
		assertNull(builder.addGovGast("Bus1", "1", 0.05, 0.4, 0.1, 3.0, 1.0, 2.0, 1.0, -0.05, 0.0));
		assertNull(builder.addGovIeesgo("Bus1", "1", 0, 0, 0.2, 0.1, 0.2, 0.3, 0.3, 0.4, 0.3, 1.0, 0.0));
		assertNull(builder.addGovIeeeg1("Bus1", "1",
				20.0, 0, 0, 0.1, 0.3, 0.3, 0.2, 0.2, 5.0, 0.2, 5.0, 0.5, -0.1, 0.1, 1.0, 0.0));
		assertNull(builder.addGovSimple("Bus1", "1", 20.0, 0.1, 1.0, 0.0));
	}

	@Test
	public void missingBus_returnsNull() throws Exception {
		DStabNetworkBuilder builder = DStabBuilderTestFixture.createWithMachine();
		assertNull(builder.addGovTgov1("missing", "1", 0.05, 0.5, 1.0, 0.0, 1.0, 2.0, 0.0));
		assertNull(builder.addGovSimple("missing", "1", 20.0, 0.1, 1.0, 0.0));
	}
}
