package org.interpss.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.plugin.optadj.texas2K.Texas2K_TestCaseInfo;
import org.interpss.util.AclfNetJsonComparator;
import org.interpss.util.FileUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.interpss.core.aclf.AclfNetwork;
import com.interpss.state.StateObjectFactory;
import com.interpss.state.aclf.AclfNetworkState;

/**
 * Regression test for {@code Texas2K_JSon_Sample}: Texas-2K PSSE import must
 * survive AclfNetworkState JSON serialize/deserialize round-trip unchanged.
 */
public class Texas2KJsonCompareTest extends CorePluginTestSetup {

	@Test
	void jsonRoundTripMatchesOriginal(@TempDir Path tempDir) throws Exception {
		AclfNetwork aclfNet = Texas2K_TestCaseInfo.createTestCaseNetwork();

		Path jsonFile = tempDir.resolve("texas2k.json");
		FileUtil.writeText2File(jsonFile.toString(), new AclfNetworkState(aclfNet).toString());

		AclfNetwork aclfNetCopy = StateObjectFactory.createAclfNetwork(jsonFile.toString());

		assertTrue(new AclfNetJsonComparator("Texas2k JSON round-trip")
				.compareJson(aclfNet, aclfNetCopy));

		// Regression anchors (Texas2K_JSon_Sample).
		assertEquals(2000, aclfNet.getNoBus(), "Bus count");
		assertEquals(3206, aclfNet.getNoBranch(), "Branch count");
		assertEquals(aclfNet.getNoBus(), aclfNetCopy.getNoBus(), "Round-trip bus count");
		assertEquals(aclfNet.getNoBranch(), aclfNetCopy.getNoBranch(), "Round-trip branch count");
	}
}
