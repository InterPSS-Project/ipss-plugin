package org.interpss.core.adapter.ieee;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;

public class UCTE2000CasesTest extends CorePluginTestSetup {
	@Test 
	@Disabled("Requires missing fixture testData/adpter/ieee_format/UCTE_2000_WinterOffPeak.ieee")
	public void testCase1() throws Exception {
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/UCTE_2000_WinterOffPeak.ieee")
				.getAclfNet();
		
//		IpssFileAdapter adapter = PluginSpringCtx.getCustomFileAdapter("ieee");
//		SimuContext simuCtx = adapter.load("testData/adpter/ieee_format/UCTE_2000_WinterOffPeak.ieee");
//
//		AclfNetwork net = simuCtx.getAclfNet();
  		//assertTrue((net.getBusList().size() == 14 && net.getBranchList().size() == 20));

	  	LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		algo.setNonDivergent(true);
	  	algo.loadflow();
  		//System.out.println(net.net2String());
	  	
  		assertTrue(net.isLfConverged());		
	}

	//@Test 
	public void testCase2() throws Exception {
//		IpssFileAdapter adapter = CorePluginObjFactory.getCustomFileAdapter("ieee");
//		SimuContext simuCtx = adapter.load("testData/adpter/ieee_format/UCTE_2000_WinterPeak.ieee");
//
//		AclfNetwork net = simuCtx.getAclfNet();
		
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/UCTE_2000_WinterPeak.ieee")
				.getAclfNet();			
  		//assertTrue((net.getBusList().size() == 14 && net.getBranchList().size() == 20));

	  	LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
	  	algo.setLfMethod(AclfMethodType.PQ);
		algo.setNonDivergent(true);
	  	algo.loadflow();
  		//System.out.println(net.net2String());
	  	
  		assertTrue(net.isLfConverged());		
	}
}

