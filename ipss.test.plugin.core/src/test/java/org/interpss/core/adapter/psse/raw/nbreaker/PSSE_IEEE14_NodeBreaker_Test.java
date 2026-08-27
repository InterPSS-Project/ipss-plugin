package org.interpss.core.adapter.psse.raw.nbreaker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.psse.PSSEDirectParser;
import org.junit.jupiter.api.Test;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.funcImpl.topo.SubstationNBreakerHelper;
import com.interpss.core.net.Substation;
import com.interpss.core.net.nb.NBEquipConnection;
import com.interpss.core.net.nb.NBModelEquipType;
import com.interpss.core.net.nb.NBModelSwitchType;
import com.interpss.core.net.nb.NBNode;
import com.interpss.core.net.nb.NBSwitch;

/**
 * PSS/E Substation Data Group → {@code com.interpss.core.net.nb} overlay import.
 */
public class PSSE_IEEE14_NodeBreaker_Test extends CorePluginTestSetup {

	private static final String CASE =
			"testData/psse/nbreaker/IEEE_14_bus_nodeBreaker_rev35_exported.raw";

	@Test
	public void testImportSubstationOverlay() throws Exception {
		AclfNetwork net = new PSSEDirectParser().parse(CASE);

		assertEquals(14, net.getNoActiveBus());
		assertEquals(2, net.getSubstationMap().size());

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

	@Test
	public void testBusBranchStillSolves() throws Exception {
		AclfNetwork net = new PSSEDirectParser().parse(CASE);
		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		assertTrue(algo.loadflow());
		assertTrue(net.isLfConverged());
	}
}
