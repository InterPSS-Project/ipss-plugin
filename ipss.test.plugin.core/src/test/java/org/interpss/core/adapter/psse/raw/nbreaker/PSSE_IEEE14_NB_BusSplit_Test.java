package org.interpss.core.adapter.psse.raw.nbreaker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.psse.PSSEDirectParser;
import org.interpss.numeric.datatype.LimitType;
import org.junit.jupiter.api.Test;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.funcImpl.topo.SubstationBusSplitMergeHelper;
import com.interpss.core.funcImpl.topo.SubstationNBreakerHelper;
import com.interpss.core.net.Substation;
import com.interpss.core.net.nb.NBEquipConnection;
import com.interpss.core.net.nb.NBModelEquipType;
import com.interpss.core.net.nb.NBNode;
import com.interpss.core.net.nb.NBSwitch;

/**
 * Mirrors {@link org.interpss.nbreaker.PSSE_IEEE14_NB_BusSplit_Sample}: relax PV
 * Q limits on Bus3/Bus6, open {@code Sw-BusBars} on STATION 5 (topo + Bus2 split),
 * then solve load flow on the expanded network.
 */
public class PSSE_IEEE14_NB_BusSplit_Test extends CorePluginTestSetup {

	private static final String CASE =
			"testData/psse/nbreaker/IEEE_14_bus_nodeBreaker_rev35_exported.raw";

	@Test
	public void testOpenBusBarSplitAndLoadflow() throws Exception {
		AclfNetwork net = new PSSEDirectParser().parse(CASE);

		// Same Q-limit relaxation as the sample (needed for post-split convergence)
		AclfBus bus3 = net.getBus("Bus3");
		assertNotNull(bus3);
		bus3.getContributeGenList().get(0).setQGenLimit(new LimitType(1.0, 0));
		AclfBus bus6 = net.getBus("Bus6");
		assertNotNull(bus6);
		bus6.getContributeGenList().get(0).setQGenLimit(new LimitType(0.5, 0));

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
		assertFalse(busBar.isActive());

		assertEquals(1, helper.findNodeByName("NB1").getIntFlag());
		assertEquals(1, helper.findNodeByName("NL1").getIntFlag());
		assertEquals(1, helper.findNodeByName("NG1").getIntFlag());
		assertEquals(1, helper.findNodeByName("NLd1").getIntFlag());
		assertEquals(2, helper.findNodeByName("NB2").getIntFlag());
		assertEquals(2, helper.findNodeByName("NL3").getIntFlag());
		assertEquals(2, helper.findNodeByName("NL4").getIntFlag());
		assertEquals(2, helper.findNodeByName("NL5").getIntFlag());

		AclfBus bus2Split = net.getBus("Bus2_split");
		assertNotNull(bus2Split);
		assertEquals("Bus 2 Split", bus2Split.getName());
		assertEquals(15, net.getNoActiveBus());
		assertSame(sub2, bus2Split.getSubstation());

		// Group 1 stays on Bus2: gen, load, branch to Bus1
		assertEquals(1, bus2.getContributeGenList().size());
		assertEquals(1, bus2.getContributeLoadList().size());
		assertEquals(1, bus2.getBranchList().size());
		assertEquals(AclfGenCode.GEN_PV, bus2.getGenCode());
		assertTrue(bus2.getLoadCode() != AclfLoadCode.NON_LOAD);
		assertEquals("Bus1", ((AclfBranch) bus2.getBranchList().get(0)).getFromBusId());
		assertEquals("Bus2", ((AclfBranch) bus2.getBranchList().get(0)).getToBusId());

		// Group 2 moved onto Bus2_split: branches to Bus3/4/5
		assertEquals(0, bus2Split.getContributeGenList().size());
		assertEquals(0, bus2Split.getContributeLoadList().size());
		assertEquals(3, bus2Split.getBranchList().size());
		assertEquals(AclfGenCode.NON_GEN, bus2Split.getGenCode());
		assertEquals(AclfLoadCode.NON_LOAD, bus2Split.getLoadCode());

		// Group-1 nodes stay on Bus2; all group-2 nodes (incl. bus-bar NB2) on Bus2_split.
		assertNodeBus(helper, "NB1", bus2);
		assertNodeBus(helper, "NL1", bus2);
		assertNodeBus(helper, "NG1", bus2);
		assertNodeBus(helper, "NLd1", bus2);
		assertNodeBus(helper, "NB2", bus2Split);
		assertNodeBus(helper, "NL3", bus2Split);
		assertNodeBus(helper, "NL4", bus2Split);
		assertNodeBus(helper, "NL5", bus2Split);

		SubstationBusSplitMergeHelper splitHelper = new SubstationBusSplitMergeHelper(sub2);
		assertEquals(3, splitHelper.getEquipByGroup(1).size());
		assertEquals(3, splitHelper.getEquipByGroup(2).size());

		assertEquipOnBus(splitHelper, 1, NBModelEquipType.MACHINE, bus2);
		assertEquipOnBus(splitHelper, 1, NBModelEquipType.LOAD, bus2);
		assertEquipOnBus(splitHelper, 1, NBModelEquipType.ACLF_BRANCH, bus2);
		for (NBEquipConnection term : splitHelper.getEquipByGroup(2)) {
			assertEquals(NBModelEquipType.ACLF_BRANCH, term.getEquipType());
			assertSame(bus2Split, term.getFromBus(), term.getId());
			assertTrue(term.getEquip() instanceof AclfBranch);
			assertSame(bus2Split, ((AclfBranch) term.getEquip()).getFromBus(), term.getId());
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

	private static void assertEquipOnBus(SubstationBusSplitMergeHelper helper, int group,
			NBModelEquipType type, AclfBus bus) {
		NBEquipConnection term = helper.getEquipByGroup(group).stream()
				.filter(t -> t.getEquipType() == type)
				.findFirst()
				.orElse(null);
		assertNotNull(term, type.name());
		assertSame(bus, term.getFromBus(), type.name());
	}
}
