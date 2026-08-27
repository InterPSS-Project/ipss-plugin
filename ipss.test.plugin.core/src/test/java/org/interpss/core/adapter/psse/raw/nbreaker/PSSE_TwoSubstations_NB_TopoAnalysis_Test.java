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
 * Mirrors {@link org.interpss.nbreaker.PSSE_TwoSubstations_NB_TopoAnalysis_Sample}:
 * basecase intFlags, then open S1 bus-coupler by node numbers (blank switch names).
 * <p>
 * Fixture is PowSyBl's {@code twoSubstations_rev35.raw}
 * ({@code PsseImporterTest#importTwoSubstationsTest}). Alternate disconnectors
 * STATUS=0; closed paths keep S1 as one component. Opening coupler nodes 1↔2
 * yields {1} vs {2,3,4,5,6}.
 */
public class PSSE_TwoSubstations_NB_TopoAnalysis_Test extends CorePluginTestSetup {

	private static final String CASE =
			"testData/psse/nbreaker/twoSubstations_rev35.raw";

	@Test
	public void testImportAndBasecaseTopoAnalysisSetsIntFlags() throws Exception {
		AclfNetwork net = new PSSEDirectParser().parse(CASE);

		assertEquals(2, net.getSubstationMap().size());
		assertEquals("S1", net.getSubstation("1").getName().trim());
		assertEquals("S2", net.getSubstation("2").getName().trim());

		Substation s1 = net.getSubstation("1");
		assertEquals(6, s1.getNbNodeList().size());
		assertEquals(7, s1.getNbSwitchList().size());

		Substation s2 = net.getSubstation("2");
		assertEquals(28, s2.getNbNodeList().size());

		net.getSubstationMap().forEach((subId, sub) -> {
			int groups = new SubstationNBreakerHelper(sub).topoAnalysis();
			assertTrue(groups >= 1, "substation " + subId);
		});

		net.getBusList().forEach(bus -> {
			if (!bus.isActive() || bus.getSubstation() == null) {
				return;
			}
			assertTrue(bus.getIntFlag() != 0,
					"Bus " + bus.getId() + " @" + bus.getSubstationId() + " has no intFlag set.");
		});

		AclfBus starBus = net.getBus("3WNDTR_2_4_5_2");
		AclfBus bus2 = net.getBus("Bus2");
		if (starBus != null && bus2 != null) {
			assertEquals(bus2.getIntFlag(), starBus.getIntFlag());
		}
	}

	@Test
	public void testOpenS1CouplerIncreasesGroupCount() throws Exception {
		AclfNetwork net = new PSSEDirectParser().parse(CASE);

		Substation s1 = net.getSubstation("1");
		assertNotNull(s1);

		SubstationNBreakerHelper helper = new SubstationNBreakerHelper(s1);
		int groupsBefore = helper.topoAnalysis();
		assertEquals(1, groupsBefore);

		NBSwitch coupler = findSwitchByNodeNumbers(s1, 1, 2);
		assertNotNull(coupler, "S1 bus-coupler nodes 1↔2");
		assertEquals(1, coupler.getCurrentStatus());
		coupler.setCurrentStatus(0);

		int groupsAfter = helper.topoAnalysis();
		assertTrue(groupsAfter > groupsBefore,
				"expected more groups after opening coupler: before=" + groupsBefore
						+ " after=" + groupsAfter);
		assertEquals(2, groupsAfter);

		// Node 1 isolated; nodes 2–6 remain connected via closed disconnectors/breakers
		assertNodeGroupByNumber(s1, 1, 1);
		assertNodeGroupByNumber(s1, 2, 2);
		assertNodeGroupByNumber(s1, 3, 2);
		assertNodeGroupByNumber(s1, 4, 2);
		assertNodeGroupByNumber(s1, 5, 2);
		assertNodeGroupByNumber(s1, 6, 2);

		new AclfNetTopoHelper(net).topoProcessing();
		assertTrue(net.getBus("Bus1").getIntFlag() != 0);
	}

	private static NBSwitch findSwitchByNodeNumbers(Substation sub, long ni, long nj) {
		return sub.getNbSwitchList().stream()
				.filter(sw -> {
					long a = sw.getFromNBNode().getNumber();
					long b = sw.getToNBNode().getNumber();
					return (a == ni && b == nj) || (a == nj && b == ni);
				})
				.findFirst()
				.orElse(null);
	}

	private static void assertNodeGroupByNumber(Substation sub, long nodeNum, int group) {
		NBNode node = sub.getNbNodeList().stream()
				.filter(n -> n.getNumber() == nodeNum)
				.findFirst()
				.orElse(null);
		assertNotNull(node, "node " + nodeNum);
		assertEquals(group, node.getIntFlag(), "node " + nodeNum);
		assertTrue(node.isBooleanFlag(), "node " + nodeNum);
	}
}
