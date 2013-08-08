package org.interpss.plugin.display;

import static org.interpss.CorePluginFunction.AclfResultBusStyle;
import static org.junit.Assert.assertTrue;

import org.ieee.odm.adapter.IODMAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.model.aclf.AclfModelParser;
import org.interpss.mapper.odm.ODMAclfNetMapper;
import org.interpss.spring.CorePluginSpringFactory;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethod;
import com.interpss.core.algo.LoadflowAlgorithm;

public class PEESFormatTest {
	@Test
	public void testCase1() throws Exception {
		IODMAdapter adapter = new PSSEAdapter(PSSEAdapter.PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile("testData/psse/PSSE_5Bus_Test.raw"));		
		
		AclfNetwork net = CorePluginSpringFactory
				.getOdm2AclfParserMapper(ODMAclfNetMapper.XfrBranchModel.InterPSS)
				.map2Model((AclfModelParser)adapter.getModel())
				.getAclfNet();	
		
	  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	algo.setLfMethod(AclfMethod.PQ);
	  	algo.loadflow();
	  	
	  	//System.out.println(LfResultBusStyle2.f(net, AclfOut_PSSE.Format.GUI));

	  	System.out.println(AclfResultBusStyle.f(net));
	}
}
