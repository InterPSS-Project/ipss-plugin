package org.interpss.plugin.result;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.plugin.exchange.bean.AclfNetExchangeInfo;
import org.junit.jupiter.api.Test;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;

public class AclfResultDFrameAdapterTest extends CorePluginTestSetup {	
	@Test
	public void test() throws Exception {
		AclfNetwork aclfNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load(TEST_ROOT + "testData/adpter/ieee_format/Ieee14Bus.ieee")
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
  		
  		AclfResultContainer results1 = new AclfResultDFrameAdapter()
  				.accept(aclfNet, bus -> true, gen -> true, load -> true, branch -> true);
  		
  		// validate the results
  		assertTrue(results1.getNetResults().isLoadflowConverged());
  		assertEquals(14, results1.getBusResults().size());
  		assertEquals(5, results1.getGenResults().size());
  		assertEquals(11, results1.getLoadResults().size());
  		assertEquals(20, results1.getBranchResults().size());
  		
  		AclfResultContainer results2 = new AclfResultDFrameAdapter()
  				.accept(aclfNet, 
  						bus -> bus.getVoltageMag() > 1.05, 
  						gen -> gen.getGen().getReal() > 0.0, 
  						load -> load.getLoadCP().getReal() > 0.5, 
  						branch -> branch.powerFrom2To().getReal() > 0.5);
  		
  		// validate the results
  		assertTrue(results2.getNetResults().isLoadflowConverged());
  		assertEquals(9, results2.getBusResults().size());
  		assertEquals(2, results2.getGenResults().size());
  		assertEquals(1, results2.getLoadResults().size());
  		assertEquals(4, results2.getBranchResults().size());
	}
}

