package org.interpss.core.adapter.psse.raw.nbreaker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.psse.PSSEDirectParser;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.funcImpl.topo.SubstationNBreakerHelper;
import com.interpss.core.net.Substation;
import com.interpss.core.net.nb.NBNode;
import com.interpss.core.net.nb.NBSwitch;

/**
 * Mirrors {@link org.interpss.nbreaker.PSSE_IEEE14_NB_Topo_Sample}: closed-switch
 * connectivity on STATION 5, then {@code topoAnalysis()} after opening Sw-BusBars.
 */
public class PSSE_IEEE14_NB_Topo_Test extends CorePluginTestSetup {

	private static final String CASE =
			"testData/psse/nbreaker/IEEE_14_bus_nodeBreaker_rev35_exported.raw";

	@Test
	public void testConnectedComponentBeforeAndAfterOpenBusBar() throws Exception {
		AclfNetwork net = new PSSEDirectParser().parse(CASE);
		Substation sub2 = net.getSubstation("2");
		assertNotNull(sub2);
		assertEquals("STATION 5", sub2.getName());
		assertEquals(8, sub2.getNbNodeList().size());
		assertEquals(7, sub2.getNbSwitchList().size());

		SubstationNBreakerHelper helper = new SubstationNBreakerHelper(sub2);

		// All switches closed: gen node reaches every node in the station
		NBNode ng1 = helper.findNodeByName("NG1");
		assertNotNull(ng1);
		assertEquals(8, helper.markConnectedNode(ng1, 1));
		for (NBNode node : sub2.getNbNodeList()) {
			assertEquals(1, node.getIntFlag(), node.getName());
			assertTrue(node.isBooleanFlag(), node.getName());
		}
		for (NBSwitch sw : sub2.getNbSwitchList()) {
			assertEquals(1, sw.getCurrentStatus(), sw.getName());
			assertTrue(sw.isBooleanFlag(), sw.getName());
		}

		NBSwitch busBar = helper.findSwitchByName("Sw-BusBars");
		assertNotNull(busBar);
		assertEquals("NB1", busBar.getFromNBNode().getName());
		assertEquals("NB2", busBar.getToNBNode().getName());
		busBar.setCurrentStatus(0);
		assertEquals(0, busBar.getCurrentStatus());
		assertFalse(busBar.isActive());
		assertEquals(6, helper.getNbSwitchList(true).size());

		// Open busbar: topoAnalysis finds two components of 4 nodes each
		assertEquals(2, helper.topoAnalysis());

		assertNodeGroup(helper, "NB1", 1);
		assertNodeGroup(helper, "NL1", 1);
		assertNodeGroup(helper, "NG1", 1);
		assertNodeGroup(helper, "NLd1", 1);

		assertNodeGroup(helper, "NB2", 2);
		assertNodeGroup(helper, "NL3", 2);
		assertNodeGroup(helper, "NL4", 2);
		assertNodeGroup(helper, "NL5", 2);

		assertFalse(busBar.isBooleanFlag());
		assertTrue(helper.findSwitchByName("Sw-BranchToBus1").isBooleanFlag());
		assertTrue(helper.findSwitchByName("Sw-Gen1").isBooleanFlag());
		assertTrue(helper.findSwitchByName("Sw-Load1").isBooleanFlag());
		assertTrue(helper.findSwitchByName("Sw-BranchToBus3").isBooleanFlag());
		assertTrue(helper.findSwitchByName("Sw-BranchToBus4").isBooleanFlag());
		assertTrue(helper.findSwitchByName("Sw-BranchToBus5").isBooleanFlag());
	}

	private static void assertNodeGroup(SubstationNBreakerHelper helper, String name, int group) {
		NBNode node = helper.findNodeByName(name);
		assertNotNull(node, name);
		assertEquals(group, node.getIntFlag(), name);
		assertTrue(node.isBooleanFlag(), name);
	}
}
