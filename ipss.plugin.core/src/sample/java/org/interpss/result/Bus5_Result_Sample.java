package org.interpss.result;
 
import org.interpss.plugin.result.AclfResultAdapter;
import org.interpss.plugin.result.AclfResultContainer;
import org.interpss.util.FileUtil;

import com.interpss.core.CoreObjectFactory;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.simu.util.sample.SampleTestingCases;

public class Bus5_Result_Sample { 
	public static void main(String args[]) throws Exception {
		AclfNetwork net = CoreObjectFactory.createAclfNetwork();
		SampleTestingCases.load_LF_5BusSystem(net);
		
	  	LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
	  	algo.loadflow();
  		
  		AclfResultContainer results = new AclfResultAdapter().accept(net);
  		
  		// turn the results into a string
  		String resultStr = results.toString();
  		//System.out.println(resultStr);
  		
  		// write the results to a file
  		FileUtil.writeText2File("output/bus5_result.json", resultStr);
	}
}


