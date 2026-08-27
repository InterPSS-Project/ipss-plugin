package org.interpss.core.adapter.psse.raw.nbreaker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.psse.PSSEDirectParser;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.funcImpl.topo.AclfNetTopoHelper;
import com.interpss.core.funcImpl.topo.SubstationNBreakerHelper;
import com.interpss.core.net.Substation;
import com.interpss.core.net.nb.NBNode;
import com.interpss.core.net.nb.NBSwitch;

/**
 * Mirrors {@link org.interpss.nbreaker.PSSE_FiveBus_NB_TopoAnalysis_Sample}:
 * basecase intFlags, then open Sw-BusBars on STATION 1.
 * <p>
 * Fixture is PowSyBl's {@code five_bus_nodeBreaker_rev35.raw}
 * ({@code PsseImporterTest#importFiveBusNodeBreaker}). Expected components after
 * opening Sw-BusBars match the RAW switch graph (all STATUS=1 except the opened
 * busbar): {NB1, NDCLINE, NGEN} vs {NB2, NLINE, NT2W, NT3W}.
 */
public class PSSE_FiveBus_NB_TopoAnalysis_Test extends CorePluginTestSetup {

	private static final String CASE =
			"testData/psse/nbreaker/five_bus_nodeBreaker_rev35.raw";

	@Test
	public void testImportAndBasecaseTopoAnalysisSetsIntFlags() throws Exception {
		AclfNetwork net = new PSSEDirectParser().parse(CASE);

		assertEquals(5, net.getSubstationMap().size());
		assertEquals("STATION 1", net.getSubstation("1").getName());
		assertEquals("STATION 5", net.getSubstation("5").getName());

		net.getSubstationMap().forEach((subId, sub) -> {
			int groups = new SubstationNBreakerHelper(sub).topoAnalysis();
			assertEquals(1, groups, "substation " + subId + " should be one connected group");
		});

		net.getBusList().forEach(bus -> {
			if (!bus.isActive() || bus.getSubstation() == null) {
				return;
			}
			assertTrue(bus.getIntFlag() != 0,
					"Bus " + bus.getId() + " @" + bus.getSubstationId() + " has no intFlag set.");
		});

		net.getBranchList().forEach(branch -> {
			if (!branch.isActive()) {
				return;
			}
			assertTrue(branch.getFromBus().getIntFlag() != 0 || branch.getFromBus().getSubstation() == null,
					"Branch " + branch.getId() + " from-bus has no intFlag");
			assertTrue(branch.getToBus().getIntFlag() != 0 || branch.getToBus().getSubstation() == null,
					"Branch " + branch.getId() + " to-bus has no intFlag");
		});

		AclfBus starBus = net.getBus("3WNDTR_1_3_4_1");
		AclfBus bus1 = net.getBus("Bus1");
		assertNotNull(starBus);
		assertNotNull(bus1);
		assertEquals(bus1.getIntFlag(), starBus.getIntFlag());
	}

	@Test
	public void testOpenBusBarsSplitsStation1IntoTwoGroups() throws Exception {
		AclfNetwork net = new PSSEDirectParser().parse(CASE);

		Substation sub1 = net.getSubstation("1");
		assertNotNull(sub1);
		assertEquals(7, sub1.getNbNodeList().size());
		assertEquals(6, sub1.getNbSwitchList().size());

		SubstationNBreakerHelper helper = new SubstationNBreakerHelper(sub1);
		assertEquals(1, helper.topoAnalysis());

		NBSwitch busBar = helper.findSwitchByName("Sw-BusBars");
		assertNotNull(busBar);
		assertEquals("NB1", busBar.getFromNBNode().getName());
		assertEquals("NB2", busBar.getToNBNode().getName());
		busBar.setCurrentStatus(0);

		assertEquals(2, helper.topoAnalysis());

		// Group membership from closed-switch BFS (PowSyBl RAW graph)
		assertNodeGroup(helper, "NB1", 1);
		assertNodeGroup(helper, "NDCLINE", 1);
		assertNodeGroup(helper, "NGEN", 1);
		assertNodeGroup(helper, "NB2", 2);
		assertNodeGroup(helper, "NLINE", 2);
		assertNodeGroup(helper, "NT2W", 2);
		assertNodeGroup(helper, "NT3W", 2);

		new AclfNetTopoHelper(net).topoProcessing();

		// Bus1 still has at least one connected component (gen / DC side on NB1)
		assertTrue(net.getBus("Bus1").getIntFlag() != 0);
	}

	private static void assertNodeGroup(SubstationNBreakerHelper helper, String name, int group) {
		NBNode node = helper.findNodeByName(name);
		assertNotNull(node, name);
		assertEquals(group, node.getIntFlag(), name);
		assertTrue(node.isBooleanFlag(), name);
	}
}
