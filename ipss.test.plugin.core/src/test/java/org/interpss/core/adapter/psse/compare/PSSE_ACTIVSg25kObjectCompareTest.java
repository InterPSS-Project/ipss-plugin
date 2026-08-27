package org.interpss.core.adapter.psse.compare;


import org.interpss.CorePluginTestSetup;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.funcImpl.compare.AclfNetObjectComparator;

import org.interpss.fadapter.psse.PSSEDirectParser;
public class PSSE_ACTIVSg25kObjectCompareTest extends CorePluginTestSetup {	
	@Test
	public void test() throws Exception {
		
		// load the test data V33
		AclfNetwork net = new PSSEDirectParser().parse("testData/psse/v33/ACTIVSg25k.RAW");
	
		AclfNetwork copyNet = net.jsonCopy();
		
		AclfNetObjectComparator comp = new AclfNetObjectComparator(net, copyNet);
		comp.compareNetwork();
		
		assertTrue(comp.getDiffMsgList().isEmpty());
	}
}

