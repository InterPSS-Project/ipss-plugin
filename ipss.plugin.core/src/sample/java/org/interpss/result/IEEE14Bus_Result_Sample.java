package org.interpss.result;
 
import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.IEEECommonFormat;

import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.interpss.plugin.result.AclfResultAdapter;
import org.interpss.plugin.result.AclfResultContainer;
import org.interpss.util.FileUtil;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;

public class IEEE14Bus_Result_Sample { 
	static String filePath = "data/ieee/ieee14.ieee";
	
	public static void main(String args[]) throws Exception {
		// load the test data V33
		AclfNetwork netV33 = IpssAdapter.importAclfNet(filePath)
				.setFormat(IEEECommonFormat)
				.load()
				.getImportedObj();
		
	  	LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(netV33);
	  	algo.loadflow();
  		
  		AclfResultContainer results = new AclfResultAdapter().accept(netV33);
  		
  		// turn the results into a string
  		String resultStr = results.toString();
  		//System.out.println(resultStr);
  		
  		// write the results to a file
  		FileUtil.writeText2File("output/ieee14_result.json", resultStr);
	}
}


