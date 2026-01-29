package org.interpss.plugin.result;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.plugin.exchange.bean.AclfNetExchangeInfo;
import org.junit.Test;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;

public class AclfResultDFrameTest extends CorePluginTestSetup {	
	@Test
	public void test() throws Exception {
		AclfNetwork aclfNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/Ieee14Bus.ieee")
				.getAclfNet();	
		
		LoadflowAlgorithm aclfAlgo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(aclfNet);

		aclfAlgo.loadflow();
		
		assertTrue(aclfNet.isLfConverged());
		
  		AclfResultContainer results = new AclfResultDFrameAdapter()
  				/*
  				 * There seems to be a bug in dflib sorting when running in junit,
  				 */
  				//.busSortExpr(AclfResultDFrameAdapter.busVoltHigherSortExpr)
  				//.genSortExpr(AclfResultDFrameAdapter.genLargerSortExpr)
  				//.loadSortExpr(AclfResultDFrameAdapter.loadLargerSortExpr)
  				//.branchSortExpr(AclfResultDFrameAdapter.branchFlowLargerSortExpr)
  				.accept(aclfNet);
  		
  		// turn the results into a string
  		//String resultStr = results.toString();
  		//System.out.println(resultStr);
  		
  		// validate the results
  		assertTrue(results.getNetResults().isLoadflowConverged());
  		assertEquals(14, results.getBusResults().size());
  		assertEquals(5, results.getGenResults().size());
  		assertEquals(11, results.getLoadResults().size());
  		assertEquals(20, results.getBranchResults().size());
	}
}

