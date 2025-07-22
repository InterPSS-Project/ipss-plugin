package org.interpss.plugin.jsonCompare;

import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.adapter.psse.raw.PSSERawAdapter;
import org.ieee.odm.model.aclf.AclfModelParser;
import org.interpss.CorePluginTestSetup;
import org.interpss.odm.mapper.ODMAclfParserMapper;
import org.interpss.util.AclfNetJsonComparator;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import com.interpss.simu.SimuObjectFactory;

public class AclfNetJsonCompareTest extends CorePluginTestSetup {	
	@Test
	public void test() throws Exception {
		
		PSSEAdapter adapter = new PSSERawAdapter(PsseVersion.PSSE_33);
		assertTrue(adapter.parseInputFile("testData/psse/v33/ACTIVSg25k.RAW"));
		AclfModelParser parser =(AclfModelParser) adapter.getModel();
		
		//System.out.println(parser.toXmlDoc());
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.ACLF_NETWORK);
		
		if (!new ODMAclfParserMapper().map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
		}
		
		AclfNetwork net =simuCtx.getAclfNet();

		// Debug: Check original network
		System.out.println("Original network - Bus count: " + net.getBusList().size() + 
		                  ", Branch count: " + net.getBranchList().size());
		
		// Show first few branch IDs
		System.out.println("First 5 branches in original:");
		int count = 0;
		for (AclfBranch branch : net.getBranchList()) {
			if (count++ < 5) {
				System.out.println("  " + branch.getId());
			} else break;
		}
		
		AclfNetwork copyNet = net.jsonCopy();

		// Debug: Check copied network
		System.out.println("Copied network - Bus count: " + copyNet.getBusList().size() + 
		                  ", Branch count: " + copyNet.getBranchList().size());
		
		// Show first few branch IDs
		System.out.println("First 5 branches in copy:");
		count = 0;
		for (AclfBranch branch : copyNet.getBranchList()) {
			if (count++ < 5) {
				System.out.println("  " + branch.getId());
			} else break;
		}

		//TODO: this is supposed to be true if everything is correct
		/** results indicated that the order of branches in the json file is different
		Value mismatch at /branchAry[Bus71173->Bus71174(1)]/id: 
		First:  "Bus71173->Bus71174(1)"
		Second: "Bus42121->Bus42123(1)"
		 */
		assertTrue(new AclfNetJsonComparator("Case1").compareJson(net, copyNet));
	}
	
	@Test 
	public void testSimple() throws Exception {
		// Create a simple test case to verify ID mapping
		PSSEAdapter adapter = new PSSERawAdapter(PsseVersion.PSSE_33);
		assertTrue(adapter.parseInputFile("testData/psse/v33/ACTIVSg25k.RAW"));
		AclfModelParser parser = (AclfModelParser) adapter.getModel();
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.ACLF_NETWORK);
		new ODMAclfParserMapper().map2Model(parser, simuCtx);
		AclfNetwork net = simuCtx.getAclfNet();
		
		// Create a manual copy and just change one branch parameter
		AclfNetwork copyNet = net.jsonCopy();
		
		// Change something small in the copy to test comparison
		if (copyNet.getBranchList().size() > 0) {
			AclfBranch firstBranch = copyNet.getBranchList().get(0);
			System.out.println("Modifying branch: " + firstBranch.getId());
			// Just change a small parameter to create a detectable difference
			firstBranch.setRatingMva1(999.99);
		}
		
		// This should detect the difference but still map correctly by ID
		boolean result = new AclfNetJsonComparator("SimpleTest").compareJson(net, copyNet);
		// We expect this to be false since we modified the copy
		System.out.println("Comparison result (should be false): " + result);
	}
}

