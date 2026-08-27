package org.interpss.core.adapter.psse.raw.nbreaker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.psse.PSSEDirectParser;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.funcImpl.topo.AclfNetTopoHelper;
import com.interpss.core.funcImpl.topo.SubstationNBreakerHelper;

/**
 * Mirrors {@link org.interpss.nbreaker.PSSE_Sample_NB_TopoAnalysis_Sample}: basecase
 * substation {@code topoAnalysis()}, then activate open-switch equipment and
 * {@link AclfNetTopoHelper#topoProcessing()}.
 */
@Tag("extended")
public class PSSE_Sample_NB_TopoAnalysis_Test extends CorePluginTestSetup {

	private static final String CASE = "testData/private/sample_nb.raw";

	private static final Set<String> ACTIVATE_BUS_IDS = Set.of("Bus208", "Bus209", "Bus3012");
	private static final Set<String> ACTIVATE_BRANCH_IDS = Set.of(
			"3WNDTR_205_215_208_3->Bus208(3)",
			"Bus209->3WNDTR_209_217_218_4(4)",
			"3WNDTR_3008_3012_3010_2->Bus3012(2)");

	/** Buses with no SUBSTATION NODE overlay (see sample-nb-bus-301-401-402.md). */
	private static final Set<String> NO_SUBSTATION_BUS_IDS = Set.of("Bus301", "Bus401", "Bus402");

	@Test
	public void testBasecaseTopoAnalysisSetsIntFlags() throws Exception {
		AclfNetwork net = new PSSEDirectParser().parse(CASE);

		net.getSubstationMap().forEach((subName, sub) -> {
			new SubstationNBreakerHelper(sub).topoAnalysis();
		});

		// Active buses in a substation must receive a topo group (intFlag != 0)
		net.getBusList().forEach(bus -> {
			if (!bus.isActive()) {
				return;
			}
			if (NO_SUBSTATION_BUS_IDS.contains(bus.getId())) {
				assertNull(bus.getSubstation(), bus.getId());
				assertEquals(0, bus.getIntFlag(), bus.getId());
			} else if (bus.getSubstation() != null) {
				assertTrue(bus.getIntFlag() != 0,
						"Bus " + bus.getId() + " @" + bus.getSubstationId() + " has no intFlag set.");
			}
		});

		// Active branches must have intFlag on both ends
		net.getBranchList().forEach(branch -> {
			if (!branch.isActive()) {
				return;
			}
			assertTrue(branch.getFromBus().getIntFlag() != 0 || branch.getFromBus().getSubstation() == null,
					"Branch " + branch.getId() + " from-bus has no intFlag");
			assertTrue(branch.getToBus().getIntFlag() != 0 || branch.getToBus().getSubstation() == null,
					"Branch " + branch.getId() + " to-bus has no intFlag");
		});

		// 3W star bus inherits intFlag from the from-bus (Bus3008) topo group
		AclfBus starBus = net.getBus("3WNDTR_3008_3012_3010_2");
		AclfBus bus3008 = net.getBus("Bus3008");
		assertNotNull(starBus);
		assertNotNull(bus3008);
		assertEquals(bus3008.getIntFlag(), starBus.getIntFlag());
		assertEquals(1, starBus.getIntFlag());
	}

	@Test
	public void testTopoProcessingTurnsOffActivatedOpenSwitchEquipment() throws Exception {
		AclfNetwork net = new PSSEDirectParser().parse(CASE);

		activateBusBranch(net);
		for (String busId : ACTIVATE_BUS_IDS) {
			assertTrue(net.getBus(busId).isActive(), busId);
		}
		for (String branchId : ACTIVATE_BRANCH_IDS) {
			assertTrue(net.getBranch(branchId).isActive(), branchId);
		}

		new AclfNetTopoHelper(net).topoProcessing();

		// Open-switch equipment is not in a connected topo group → turned off
		for (String busId : ACTIVATE_BUS_IDS) {
			assertFalse(net.getBus(busId).isActive(), busId);
		}
		for (String branchId : ACTIVATE_BRANCH_IDS) {
			assertFalse(net.getBranch(branchId).isActive(), branchId);
		}
	}

	private static void activateBusBranch(AclfNetwork net) {
		net.getBusList().forEach(bus -> {
			if (ACTIVATE_BUS_IDS.contains(bus.getId())) {
				bus.setStatus(true);
			}
		});
		net.getBranchList().forEach(branch -> {
			if (ACTIVATE_BRANCH_IDS.contains(branch.getId())) {
				branch.setStatus(true);
			}
		});
	}
}
