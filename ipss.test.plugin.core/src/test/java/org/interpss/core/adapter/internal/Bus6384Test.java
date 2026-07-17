package org.interpss.core.adapter.internal;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.junit.jupiter.api.Test;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;

public class Bus6384Test extends CorePluginTestSetup {
	@Test
	public void testCase1() throws Exception {
		
//  		System.out.println("Start loading data ...");
//		IpssFileAdapter adapter = PluginSpringFactory.getCustomFileAdapter("ipssdat");
//		SimuContext simuCtx = adapter.load("testData/ipssdata/BUS6384.ipssdat");
//  		System.out.println("End loading data ...");
//
//		AclfNetwork net = simuCtx.getAclfNet();
		
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IpssInternal)
				.load("testData/ipssdata/BUS6384.ipssdat")
				.getAclfNet();	
		
  		//System.out.println(net.net2String());
  		assertTrue((net.getBusList().size() == 6384));

        long starttime = System.currentTimeMillis() ;
	  	LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
	  	algo.setLfMethod(AclfMethodType.NR);
	  	algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
	  	algo.loadflow();
  		//System.out.println(net.net2String());
		System.out.println("time for loadflow calculation : " + (System.currentTimeMillis() - starttime)*0.001);
	  	
  		assertTrue( net.isLfConverged());		
	}
}

