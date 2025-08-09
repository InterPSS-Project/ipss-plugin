package org.interpss.core.result;
 
import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;
import static org.junit.Assert.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.interpss.plugin.pssl.plugin.IpssAdapter.PsseVersion;
import org.interpss.result.aclf.AclfResultAdapter;
import org.interpss.result.aclf.AclfResultContainer;
import org.interpss.util.FileUtil;
import org.junit.Test;

import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;

public class PSSE_IEEE9Bus_Result_Test extends CorePluginTestSetup { 
	@Test
	public void test() throws Exception {
		// load the test data V33
		AclfNetwork netV33 = IpssAdapter.importAclfNet("testdata/adpter/psse/v31/ieee9_v31.raw")
				.setFormat(PSSE)
				.setPsseVersion(PsseVersion.PSSE_31) 
				.load()
				.getImportedObj();
		
	  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(netV33);
	  	algo.loadflow();
	  	
  		assertTrue(netV33.isLfConverged());
  		
  		AclfResultContainer results = new AclfResultAdapter().accept(netV33);
  		
  		assertTrue("Loadflow converged", results.isLoadflowConverged());
  		assertTrue("", results.getBusResults().size() == 9);
  		assertTrue(""+results.getBranchResults().size(), results.getBranchResults().size() == 9);
  		
  		// turn the results into a string
  		String resultStr = results.toString();
  		//System.out.println(resultStr);
  		
  		// write the results to a file
  		FileUtil.writeText2File("output/ieee9_v31_result.json", resultStr);
	}
}


