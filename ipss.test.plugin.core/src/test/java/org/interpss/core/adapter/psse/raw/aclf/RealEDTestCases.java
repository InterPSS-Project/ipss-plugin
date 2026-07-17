package org.interpss.core.adapter.psse.raw.aclf;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.junit.jupiter.api.Test;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;

public class RealEDTestCases extends CorePluginTestSetup {
	@Test
	public void testCase1() throws Exception {
//		IpssFileAdapter adapter = CorePluginObjFactory.getCustomFileAdapter("psse");
//		//SimuContext simuCtx = adapter.load("testData/psse/test_model_V29.raw", SpringAppContext.getIpssMsgHub());
//		SimuContext simuCtx = adapter.load("testData/psse/test_model_V30.raw");
//  		//System.out.println(simuCtx.getAclfNet().net2String());
//
//		AclfNetwork net = simuCtx.getAclfNet();
		
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.PSSE)
				.load("testData/adpter/psse/test_model_V30.raw")
				.getAclfNet();			
		
	  	LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		algo.setNonDivergent(true);
	  	algo.setLfMethod(AclfMethodType.NR);
	  	net.setBypassDataCheck(true);
	  	algo.loadflow();
  		//System.out.println(net.net2String());
	}
}

