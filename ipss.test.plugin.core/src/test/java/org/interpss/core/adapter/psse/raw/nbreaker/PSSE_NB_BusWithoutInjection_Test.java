package org.interpss.core.adapter.psse.raw.nbreaker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.psse.PSSEDirectParser;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.funcImpl.topo.SubstationNBreakerHelper;

/**
 * PowSyBl bus-without-injection NB edge cases — import must not NPE.
 * Bus type 4 (disconnected) may leave fewer active buses than total.
 */
public class PSSE_NB_BusWithoutInjection_Test extends CorePluginTestSetup {

	private static final String WITH_SWITCHES =
			"testData/psse/powsybl/nbreaker/busWithoutInjectionInNodeBreakerModelWithSwitches.raw";
	private static final String WITHOUT_SWITCHES =
			"testData/psse/powsybl/nbreaker/busWithoutInjectionInNodeBreakerModelWithoutSwitches.raw";
	private static final String ISOLATED =
			"testData/psse/powsybl/nbreaker/busWithoutInjectionInNodeBreakerModelWithIsolatedInternalConnection.raw";
	private static final String BUS_BREAKER =
			"testData/psse/powsybl/nbreaker/busWithoutInjectionInBusBreakerModel.raw";

	@Test
	public void testWithSwitches() throws Exception {
		AclfNetwork net = new PSSEDirectParser().parse(WITH_SWITCHES);
		assertTrue(net.getNoBus() >= 3);
		assertEquals(3, net.getSubstationMap().size());
		// Substations 1–2 have closed switches → ≥1 component; sub 3 has STATUS=0 switch
		assertTrue(new SubstationNBreakerHelper(net.getSubstation("1")).topoAnalysis() >= 1);
		assertTrue(new SubstationNBreakerHelper(net.getSubstation("2")).topoAnalysis() >= 1);
		assertNotNull(net.getSubstation("3"));
	}

	@Test
	public void testWithoutSwitches() throws Exception {
		AclfNetwork net = new PSSEDirectParser().parse(WITHOUT_SWITCHES);
		assertTrue(net.getNoBus() >= 3);
		assertNotNull(net.getSubstationMap());
		assertEquals(0, net.getSubstationMap().size());
	}

	@Test
	public void testIsolatedInternalConnection() throws Exception {
		AclfNetwork net = new PSSEDirectParser().parse(ISOLATED);
		assertTrue(net.getNoBus() >= 3);
		assertEquals(3, net.getSubstationMap().size());
		assertNotNull(net.getSubstation("3"));
	}

	@Test
	public void testBusBreakerModelWithoutInjection() throws Exception {
		AclfNetwork net = new PSSEDirectParser().parse(BUS_BREAKER);
		assertTrue(net.getNoBus() >= 3, "import must not NPE on bus-breaker empty-injection case");
	}
}
