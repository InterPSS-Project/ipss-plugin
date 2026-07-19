package org.interpss.core.adapter.psse.raw.aclf;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;

public class MMWG_2007series_2008S_Test extends CorePluginTestSetup {
	@Test
	@Disabled("Requires missing fixture testData/adpter/psse/MMWG_2007series_2008S_Final.raw")
	public void testCase1() throws Exception {
//		IpssFileAdapter adapter = CorePluginObjFactory.getCustomFileAdapter("psse");
//		SimuContext simuCtx = adapter.load("testData/psse/MMWG_2007series_2008S_Final.raw");
////  		System.out.println(simuCtx.getAclfNet().net2String());

//		AclfNetwork net = simuCtx.getAclfNet();
		
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.PSSE)
				.load("testData/adpter/psse/MMWG_2007series_2008S_Final.raw")
				.getAclfNet();			
		

		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
	  	algo.setLfMethod(AclfMethodType.PQ);
	  	algo.loadflow();
  		//System.out.println(net.net2String());
	}
}

