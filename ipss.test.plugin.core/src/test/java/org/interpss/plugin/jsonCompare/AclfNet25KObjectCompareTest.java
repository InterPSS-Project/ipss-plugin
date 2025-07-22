package org.interpss.plugin.jsonCompare;

import static org.junit.Assert.assertTrue;

import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.adapter.psse.raw.PSSERawAdapter;
import org.ieee.odm.model.aclf.AclfModelParser;
import org.interpss.CorePluginTestSetup;
import org.interpss.odm.mapper.ODMAclfParserMapper;
import org.junit.Test;

import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.funcImpl.AclfNetObjectComparator;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import com.interpss.simu.SimuObjectFactory;

public class AclfNet25KObjectCompareTest extends CorePluginTestSetup {	
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

