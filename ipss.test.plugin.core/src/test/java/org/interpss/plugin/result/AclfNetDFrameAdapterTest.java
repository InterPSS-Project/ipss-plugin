package org.interpss.plugin.result;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.dflib.DataFrame;
import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.plugin.exchange.bean.AclfNetExchangeInfo;
import org.interpss.plugin.result.dframe.AclfNetDFrameAdapter;
import org.junit.Test;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;

public class AclfNetDFrameAdapterTest extends CorePluginTestSetup {	
	@Test
	public void test() throws Exception {
		AclfNetwork aclfNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load(TEST_ROOT + "testData/adpter/ieee_format/Ieee14Bus.ieee")
				.getAclfNet();	
		
		LoadflowAlgorithm aclfAlgo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(aclfNet);

		aclfAlgo.loadflow();
		
		assertTrue(aclfNet.isLfConverged());
		
	  	AclfNetDFrameAdapter dfAdapter = new AclfNetDFrameAdapter();
	  	dfAdapter.adapt(aclfNet);
	  	
    	DataFrame dfBus = dfAdapter.getDfBus();    	

		// print # of rows in dfBus
		System.out.println("Number of rows in dfBus: " + dfBus.height());
		
		// validate the results
		assertEquals(14, dfBus.height());
		
		DataFrame dfBranch = dfAdapter.getDfBranch();
		System.out.println("Number of rows in dfBranch: " + dfBranch.height());
		assertEquals(20, dfBranch.height());
		
		DataFrame dfGen = dfAdapter.getDfGen();
		System.out.println("Number of rows in dfGen: " + dfGen.height());
		assertEquals(5, dfGen.height());
		
		DataFrame dfLoad = dfAdapter.getDfLoad();
		System.out.println("Number of rows in dfLoad: " + dfLoad.height());
		assertEquals(11, dfLoad.height());
	}
}

