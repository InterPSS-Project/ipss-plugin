package org.interpss.core.adapter.pwd;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfNetwork;

public class PWDIEEE14BusTestCase extends CorePluginTestSetup {
	@Test
	public void testCase() throws Exception {
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.PWD)
				.loadAclfNet("testData/adpter/pwd/ieee14.AUX");

		assertNotNull(net);
		assertTrue(net.getNoBus() > 0);
	}
}

