package org.interpss.core.adapter.psse.compare;

import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;
import static org.junit.Assert.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.interpss.plugin.pssl.plugin.IpssAdapter.PsseVersion;
import org.junit.Test;

import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.funcImpl.AclfNetObjectComparator;

public class PSSE_ACTIVSg25kObjectCompareTest extends CorePluginTestSetup {	
	@Test
	public void test() throws Exception {
		
		/*
		PSSEAdapter adapter = new PSSERawAdapter(PsseVersion.PSSE_33);
		assertTrue(adapter.parseInputFile("testData/psse/v33/ACTIVSg25k.RAW"));
		AclfModelParser parser =(AclfModelParser) adapter.getModel();
		
		//System.out.println(parser.toXmlDoc());
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.ACLF_NETWORK);
		
		if (!new ODMAclfParserMapper().map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
		}
		
		AclfNetwork net =simuCtx.getAclfNet();
		*/
		
		// load the test data V33
		AclfNetwork net = IpssAdapter.importAclfNet("testData/psse/v33/ACTIVSg25k.RAW")
				.setFormat(PSSE)
				.setPsseVersion(PsseVersion.PSSE_33) 
				.load()
				.getImportedObj();
		
		AclfNetwork copyNet = net.jsonCopy();

		//TODO: this is supposed to be true if everything is correct
		/** results indicated that the order of branches in the json file is different
		Value mismatch at /branchAry[Bus71173->Bus71174(1)]/id: 
		First:  "Bus71173->Bus71174(1)"
		Second: "Bus42121->Bus42123(1)"
		 *
		assertTrue(new AclfNetJsonComparator("Case1").compareJson(net, copyNet));
		*/
		
		AclfNetObjectComparator comp = new AclfNetObjectComparator(net, copyNet);
		comp.compareNetwork();
		
		assertTrue(comp.getDiffMsgList().isEmpty());
	}
}

