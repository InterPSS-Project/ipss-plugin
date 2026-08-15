package org.interpss.core.adapter.psse.raw.nbreaker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.psse.PSSEDirectParser;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.funcImpl.topo.SubstationNBreakerHelper;
import com.interpss.core.net.Substation;
import com.interpss.core.net.nb.NBSwitch;

/**
 * PowSyBl five_bus exported / split-buses variants.
 */
public class PSSE_FiveBus_NB_ExportImport_Test extends CorePluginTestSetup {

	private static final String EXPORTED =
			"testData/psse/powsybl/nbreaker/five_bus_nodeBreaker_rev35_exported.raw";
	private static final String SPLIT =
			"testData/psse/powsybl/nbreaker/five_bus_nodeBreaker_rev35_split_buses_exported.raw";

	@Test
	public void testExportedFiveBusStationsAndClosedTopo() throws Exception {
		AclfNetwork net = new PSSEDirectParser().parse(EXPORTED);

		assertEquals(5, net.getSubstationMap().size());
		assertEquals("STATION 1", net.getSubstation("1").getName());
		assertEquals("STATION 5", net.getSubstation("5").getName());

		net.getSubstationMap().forEach((id, sub) -> {
			assertEquals(1, new SubstationNBreakerHelper(sub).topoAnalysis(),
					"substation " + id);
		});
	}

	@Test
	public void testSplitBusesExportOpenBusBarsOnStation1() throws Exception {
		AclfNetwork net = new PSSEDirectParser().parse(SPLIT);

		Substation sub1 = net.getSubstation("1");
		assertNotNull(sub1);
		NBSwitch busBar = new SubstationNBreakerHelper(sub1).findSwitchByName("Sw-BusBars");
		assertNotNull(busBar);
		assertEquals(0, busBar.getCurrentStatus());
		assertEquals(2, new SubstationNBreakerHelper(sub1).topoAnalysis());
	}
}
