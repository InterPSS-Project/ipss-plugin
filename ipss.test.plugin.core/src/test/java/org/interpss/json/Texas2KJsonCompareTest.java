package org.interpss.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.plugin.optadj.texas2K.Texas2K_TestCaseInfo;
import org.interpss.util.AclfNetJsonComparator;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfNetwork;
import com.interpss.state.StateObjectFactory;
import com.interpss.state.aclf.AclfNetworkState;

/**
 * Regression test for {@code Texas2K_JSon_Sample}: Texas-2K PSSE import must
 * survive AclfNetworkState JSON serialize/deserialize round-trip unchanged.
 */
public class Texas2KJsonCompareTest extends CorePluginTestSetup {

	@Test
	void jsonRoundTripMatchesOriginal() throws Exception {
		AclfNetwork aclfNet = Texas2K_TestCaseInfo.createTestCaseNetwork();

		String json = new AclfNetworkState(aclfNet).toString();
		AclfNetworkState state = StateObjectFactory.GSON.fromJson(json, AclfNetworkState.class);
		AclfNetwork aclfNetCopy = AclfNetworkState.create(state);

		assertTrue(new AclfNetJsonComparator("Texas2k JSON round-trip")
				.compareJson(aclfNet, aclfNetCopy));

		// Regression anchors (Texas2K_JSon_Sample).
		assertEquals(2000, aclfNet.getNoBus(), "Bus count");
		assertEquals(3220, aclfNet.getNoBranch(), "Branch count");
		assertEquals(aclfNet.getNoBus(), aclfNetCopy.getNoBus(), "Round-trip bus count");
		assertEquals(aclfNet.getNoBranch(), aclfNetCopy.getNoBranch(), "Round-trip branch count");
	}
}
