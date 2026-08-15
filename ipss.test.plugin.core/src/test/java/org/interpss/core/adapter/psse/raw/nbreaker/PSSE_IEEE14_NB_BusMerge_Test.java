package org.interpss.core.adapter.psse.raw.nbreaker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.psse.PSSEDirectParser;
import org.junit.jupiter.api.Test;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.funcImpl.topo.SubstationNBreakerHelper;
import com.interpss.core.net.Substation;
import com.interpss.core.net.nb.NBNode;
import com.interpss.core.net.nb.NBSwitch;

/**
 * Mirrors {@link org.interpss.nbreaker.PSSE_IEEE14_NB_BusMerge_Sample}: open
 * {@code Sw-BusBars} (split Bus2), {@code closeSwitch} (merge back), then load flow.
 */
public class PSSE_IEEE14_NB_BusMerge_Test extends CorePluginTestSetup {

	private static final String CASE =
			"testData/psse/nbreaker/IEEE_14_bus_nodeBreaker_rev35_exported.raw";

	@Test
	public void testOpenThenCloseSwitchRoundTripAndLoadflow() throws Exception {
		AclfNetwork net = new PSSEDirectParser().parse(CASE);

		Substation sub2 = net.getSubstation("2");
		assertNotNull(sub2);
		assertEquals("STATION 5", sub2.getName());

		AclfBus bus2 = net.getBus("Bus2");
		assertNotNull(bus2);
		assertEquals(14, net.getNoActiveBus());
		assertEquals(1, bus2.getContributeGenList().size());
		assertEquals(1, bus2.getContributeLoadList().size());
		assertEquals(4, bus2.getBranchList().size());

		SubstationNBreakerHelper helper = new SubstationNBreakerHelper(sub2);
		assertTrue(helper.openSwitch("Sw-BusBars"));

		NBSwitch busBar = helper.findSwitchByName("Sw-BusBars");
		assertNotNull(busBar);
		assertEquals(0, busBar.getCurrentStatus());

		AclfBus bus2Split = net.getBus("Bus2_split");
		assertNotNull(bus2Split);
		assertEquals(15, net.getNoActiveBus());
		assertSame(bus2, busBar.getFromNBNode().getBus());
		assertSame(bus2Split, busBar.getToNBNode().getBus());

		assertTrue(helper.closeSwitch("Sw-BusBars"));

		assertEquals(1, busBar.getCurrentStatus());
		assertNull(net.getBus("Bus2_split"));
		assertEquals(14, net.getNoActiveBus());
		assertEquals(1, bus2.getContributeGenList().size());
		assertEquals(1, bus2.getContributeLoadList().size());
		assertEquals(4, bus2.getBranchList().size());
		assertEquals(AclfGenCode.GEN_PV, bus2.getGenCode());
		assertTrue(bus2.getLoadCode() != AclfLoadCode.NON_LOAD);

		for (String name : new String[] { "NB1", "NB2", "NL1", "NL3", "NL4", "NL5", "NG1", "NLd1" }) {
			assertNodeBus(helper, name, bus2);
		}

		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		assertTrue(algo.loadflow());
		assertTrue(net.isLfConverged());
	}

	private static void assertNodeBus(SubstationNBreakerHelper helper, String name, AclfBus bus) {
		NBNode node = helper.findNodeByName(name);
		assertNotNull(node, name);
		assertSame(bus, node.getBus(), name);
	}
}
