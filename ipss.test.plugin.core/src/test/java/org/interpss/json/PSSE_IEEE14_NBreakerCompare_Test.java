package org.interpss.json;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.psse.PSSEDirectParser;
import org.interpss.util.AclfNetJsonComparator;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfNetwork;
import com.interpss.state.StateObjectFactory;
import com.interpss.state.aclf.AclfNetworkState;

/**
 * PSS/E Substation Data Group → {@code com.interpss.core.net.nb} overlay import.
 */
public class PSSE_IEEE14_NBreakerCompare_Test extends CorePluginTestSetup {

	private static final String CASE =
			"testData/psse/nbreaker/IEEE_14_bus_nodeBreaker_rev35_exported.raw";

	@Test
	public void test() throws Exception {
		AclfNetwork net = new PSSEDirectParser().parse(CASE);

		String json = new AclfNetworkState(net).toString();
		AclfNetworkState state = StateObjectFactory.GSON.fromJson(json, AclfNetworkState.class);
		AclfNetwork aclfNetCopy = AclfNetworkState.create(state);

		assertTrue(new AclfNetJsonComparator("PSSE IEEE14 NBreaker JSON round-trip")
				.compareJson(net, aclfNetCopy));
	}
}
