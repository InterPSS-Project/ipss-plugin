package org.interpss.core.adapter.psse.json.aclf.powsybl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.psse.PSSEDirectParser;
import org.interpss.fadapter.psse.PSSEJsonDirectParser;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.funcImpl.topo.SubstationNBreakerHelper;
import com.interpss.core.net.Substation;
import com.interpss.core.net.nb.NBEquipConnection;
import com.interpss.core.net.nb.NBModelEquipType;
import com.interpss.core.net.nb.NBModelSwitchType;
import com.interpss.core.net.nb.NBNode;
import com.interpss.core.net.nb.NBSwitch;

/**
 * PowSyBl RAWX smoke coverage via {@link PSSEJsonDirectParser}.
 */
public class PSSE_PowSyBl_RAWX_Smoke_Test extends CorePluginTestSetup {

	private static final String DIR = "testData/psse/powsybl/rawx/";

	@Test
	public void ieee14Rev35Rawx() throws Exception {
		AclfNetwork net = new PSSEJsonDirectParser().parse(DIR + "IEEE_14_bus_rev35.rawx");
		assertTrue(net.getNoActiveBus() >= 14);
		assertTrue(net.getNoBranch() > 0);
	}

	@Test
	public void ieee14RawVsRawxBusCount() throws Exception {
		AclfNetwork raw = new PSSEDirectParser().parse("testData/psse/powsybl/ieee/IEEE_14_bus_rev35.raw");
		AclfNetwork rawx = new PSSEJsonDirectParser().parse(DIR + "IEEE_14_bus_rev35.rawx");
		assertEquals(raw.getNoActiveBus(), rawx.getNoActiveBus());
	}

	@Test
	public void ieee24Rev35Rawx() throws Exception {
		AclfNetwork net = new PSSEJsonDirectParser().parse(DIR + "IEEE_24_bus_rev35.rawx");
		assertTrue(net.getNoActiveBus() >= 24);
		assertTrue(net.getBus("Bus1").isSwitchedShunt());
		assertTrue(net.getBus("Bus6").isSwitchedShunt());
		assertEquals(1.2, net.getBus("Bus1").getFirstSwitchedShunt(true).getBInit(), 1e-6);
		assertEquals(1.0, net.getBus("Bus6").getFirstSwitchedShunt(true).getBInit(), 1e-6);
	}

	@Test
	public void ieee24RawVsRawxSwitchedShunt() throws Exception {
		AclfNetwork raw = new PSSEDirectParser().parse("testData/psse/powsybl/ieee/IEEE_24_bus_rev35.raw");
		AclfNetwork rawx = new PSSEJsonDirectParser().parse(DIR + "IEEE_24_bus_rev35.rawx");
		assertEquals(raw.getBus("Bus1").getFirstSwitchedShunt(true).getBInit(),
				rawx.getBus("Bus1").getFirstSwitchedShunt(true).getBInit(), 1e-9);
		assertEquals(raw.getBus("Bus6").getFirstSwitchedShunt(true).getBInit(),
				rawx.getBus("Bus6").getFirstSwitchedShunt(true).getBInit(), 1e-9);
	}

	@Test
	public void twoSubstationsRawx() throws Exception {
		AclfNetwork net = new PSSEJsonDirectParser().parse(DIR + "twoSubstations_rev35.rawx");
		assertTrue(net.getNoActiveBus() > 0);
		assertEquals(2, net.getSubstationMap().size());
		assertTrue(net.isNodeBreakerModel());
	}

	@Test
	public void minimalExampleRawx() throws Exception {
		AclfNetwork net = new PSSEJsonDirectParser().parse(DIR + "MinimalExample.rawx");
		assertTrue(net.getNoBus() > 0);
	}

	@Test
	public void ieee25BusRawx() throws Exception {
		AclfNetwork net = new PSSEJsonDirectParser().parse(DIR + "ieee_25_bus.rawx");
		assertTrue(net.getNoBus() >= 25);
	}

	@Test
	public void ieee14CompletedRev35Rawx() throws Exception {
		AclfNetwork net = new PSSEJsonDirectParser().parse(DIR + "IEEE_14_bus_completed_rev35.rawx");
		assertTrue(net.getNoBus() >= 14);
	}

	@Test
	public void specialCharactersRawx() throws Exception {
		AclfNetwork net = new PSSEJsonDirectParser().parse(DIR + "RawxCaseWithSpecialCharacters.rawx");
		assertTrue(net.getNoBus() > 0);
	}

	@Test
	public void ieee14NodeBreakerRawx() throws Exception {
		AclfNetwork net = new PSSEJsonDirectParser().parse(DIR + "IEEE_14_bus_nodeBreaker_rev35.rawx");

		assertEquals(2, net.getSubstationMap().size());
		assertTrue(net.isNodeBreakerModel());

		Substation s1 = net.getSubstation("1");
		assertNotNull(s1);
		assertEquals("STATION 1", s1.getName());
		assertEquals(0.1, s1.getGroundingResistance(), 1e-9);
		assertEquals(5, s1.getNbNodeList().size());
		assertEquals(4, s1.getNbSwitchList().size());
		assertEquals(3, s1.getNbEquipConnectList().size());

		Substation s2 = net.getSubstation("2");
		assertNotNull(s2);
		assertEquals("STATION 5", s2.getName());
		assertEquals(8, s2.getNbNodeList().size());
		assertEquals(7, s2.getNbSwitchList().size());
		assertEquals(6, s2.getNbEquipConnectList().size());

		SubstationNBreakerHelper s1h = new SubstationNBreakerHelper(s1);
		NBNode ng1 = s1h.findNodeByName("NG1");
		assertNotNull(ng1);
		assertEquals("NBNode_1-5@STATION 1", ng1.getId());
		assertSame(net.getBus("Bus1"), ng1.getBus());
		assertEquals(1.0, ng1.getVoltageMag(), 1e-9);

		NBSwitch busBar = s1h.findSwitchByName("Sw-BusBars");
		assertNotNull(busBar);
		assertEquals("NBSwitch_1-1-2-1@STATION 1", busBar.getId());
		assertEquals(NBModelSwitchType.BREAKER, busBar.getSwitchType());
		assertEquals(1, busBar.getCurrentStatus());
		assertEquals(1, busBar.getNormalStatus());
		assertEquals("1", busBar.getCircuitId());
		assertEquals(0.0001, busBar.getXpu(), 1e-9);

		NBEquipConnection genTerm = s1h.findEquip(NBModelEquipType.MACHINE);
		assertNotNull(genTerm);
		assertNotNull(genTerm.getEquip());
		assertEquals("1", genTerm.getEquip().getId());
		assertSame(net.getBus("Bus1"), genTerm.getFromBus());

		NBEquipConnection br12 = s1h.findBranchEquip("Bus1", "Bus2");
		assertNotNull(br12);
		assertNotNull(br12.getEquip());

		SubstationNBreakerHelper s2h = new SubstationNBreakerHelper(s2);
		NBEquipConnection loadTerm = s2h.findEquip(NBModelEquipType.LOAD);
		assertNotNull(loadTerm);
		assertNotNull(loadTerm.getEquip());
		assertSame(net.getBus("Bus2"), loadTerm.getFromBus());
	}
}
