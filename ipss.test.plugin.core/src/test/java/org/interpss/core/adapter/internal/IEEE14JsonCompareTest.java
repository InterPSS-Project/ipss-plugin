package org.interpss.core.adapter.internal;

import java.io.File;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.util.AclfNetJsonComparator;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfNetwork;

public class IEEE14JsonCompareTest extends CorePluginTestSetup {
	@Test
	public void test() throws Exception {
		AclfNetwork aclfNet = CorePluginFactory
					.getFileAdapter(IpssFileAdapter.FileFormat.IpssInternal)
					.load("testData/ipssdata/Ieee14.ipssdat")
					.getAclfNet();	
		
		String jsonFile = "testData/json/inter_format/ieee14Bus.json";

		// The historical JSON fixture predates the network-level low-voltage load
		// configuration. Verify the imported defaults explicitly while keeping the
		// fixture focused on the internal-format network data.
		assertTrue(aclfNet.getBusLoadLowVoltConfig().isApplyVoltAdjust());
		assertEquals(0.7, aclfNet.getBusLoadLowVoltConfig().getVConstPMin(), 1.0e-12);
		assertEquals(0.5, aclfNet.getBusLoadLowVoltConfig().getVConstIMin(), 1.0e-12);
		
		// output aclfNet state to json file
		//FileUtil.writeText2File(jsonFile, new AclfNetworkState(aclfNet).toString());
		
		// compare the json file with the aclfNet
		assertTrue(new AclfNetJsonComparator("Internal format ieee14Bus",
				path -> !path.endsWith("/extUID")
						&& !path.equals("/deviceUIDType")
						&& !path.equals("/nodeBreakerModel")
						&& !path.startsWith("/busLoadLowVoltConfig"))
							.compareJson(aclfNet, new File(jsonFile)));
	}
}

