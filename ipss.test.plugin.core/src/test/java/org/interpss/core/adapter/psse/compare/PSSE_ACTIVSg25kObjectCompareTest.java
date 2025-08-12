package org.interpss.core.adapter.psse.compare;


import org.interpss.CorePluginTestSetup;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;
import org.interpss.plugin.pssl.plugin.IpssAdapter.PsseVersion;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.funcImpl.AclfNetObjectComparator;

public class PSSE_ACTIVSg25kObjectCompareTest extends CorePluginTestSetup {	
	@Test
	public void test() throws Exception {
		
		// load the test data V33
		AclfNetwork net = IpssAdapter.importAclfNet("testData/psse/v33/ACTIVSg25k.RAW")
				.setFormat(PSSE)
				.setPsseVersion(PsseVersion.PSSE_33) 
				.load()
				.getImportedObj();
	
		AclfNetwork copyNet = net.jsonCopy();
		
		AclfNetObjectComparator comp = new AclfNetObjectComparator(net, copyNet);
		comp.compareNetwork();
		
		assertTrue(comp.getDiffMsgList().isEmpty());
	}
}

