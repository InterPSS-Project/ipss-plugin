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
 * PowSyBl {@code IEEE_14_bus_nodeBreaker_rev35_split_bus_exported.raw}:
 * Sw-BusBars STATUS=0 on both stations → topoAnalysis yields 2 groups without
 * calling {@code openSwitch}.
 */
public class PSSE_IEEE14_NB_SplitBus_Import_Test extends CorePluginTestSetup {

	private static final String SPLIT =
			"testData/psse/powsybl/nbreaker/IEEE_14_bus_nodeBreaker_rev35_split_bus_exported.raw";
	private static final String SOURCE =
			"testData/psse/powsybl/nbreaker/IEEE_14_bus_nodeBreaker_rev35.raw";

	@Test
	public void testSplitExportOpenBusBarsYieldsTwoGroups() throws Exception {
		AclfNetwork net = new PSSEDirectParser().parse(SPLIT);

		assertEquals(14, net.getNoActiveBus());
		assertEquals(2, net.getSubstationMap().size());

		Substation s1 = net.getSubstation("1");
		assertNotNull(s1);
		NBSwitch busBar1 = new SubstationNBreakerHelper(s1).findSwitchByName("Sw-BusBars");
		assertNotNull(busBar1);
		assertEquals(0, busBar1.getCurrentStatus());
		assertEquals(2, new SubstationNBreakerHelper(s1).topoAnalysis());

		Substation s2 = net.getSubstation("2");
		assertNotNull(s2);
		NBSwitch busBar2 = new SubstationNBreakerHelper(s2).findSwitchByName("Sw-BusBars");
		assertNotNull(busBar2);
		assertEquals(0, busBar2.getCurrentStatus());
		assertEquals(2, new SubstationNBreakerHelper(s2).topoAnalysis());
	}

	@Test
	public void testNonExportedSourceHasClosedBusBars() throws Exception {
		AclfNetwork net = new PSSEDirectParser().parse(SOURCE);

		assertEquals(2, net.getSubstationMap().size());
		Substation s1 = net.getSubstation("1");
		assertNotNull(s1);
		NBSwitch busBar = new SubstationNBreakerHelper(s1).findSwitchByName("Sw-BusBars");
		assertNotNull(busBar);
		assertEquals(1, busBar.getCurrentStatus());
		assertEquals(1, new SubstationNBreakerHelper(s1).topoAnalysis());
	}
}
