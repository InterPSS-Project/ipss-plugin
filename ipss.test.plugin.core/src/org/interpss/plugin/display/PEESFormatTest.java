package org.interpss.plugin.display;

import static org.interpss.CorePluginFunction.aclfResultBusStyle;
import static org.junit.Assert.assertTrue;

import org.ieee.odm.adapter.IODMAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.model.aclf.AclfModelParser;
import org.interpss.CorePluginFactory;
import org.interpss.mapper.odm.ODMAclfNetMapper;
import org.junit.Test;

import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethod;
import com.interpss.core.algo.LoadflowAlgorithm;

public class PEESFormatTest {
	@Test
	public void testCase1() throws Exception {
		IODMAdapter adapter = new PSSEAdapter(PSSEAdapter.PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile("testData/adpter/psse/PSSE_5Bus_Test.raw"));		
		
		AclfNetwork net = CorePluginFactory
				.getOdm2AclfParserMapper(ODMAclfNetMapper.XfrBranchModel.InterPSS)
				.map2Model((AclfModelParser)adapter.getModel())
				.getAclfNet();	
		
	  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	algo.setLfMethod(AclfMethod.PQ);
	  	algo.loadflow();
	  	
	  	//System.out.println(LfResultBusStyle2.f(net, AclfOut_PSSE.Format.GUI));

	  	//System.out.println(aclfResultBusStyle.apply(net));
	}
}
