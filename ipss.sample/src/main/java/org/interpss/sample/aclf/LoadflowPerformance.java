package org.interpss.sample.aclf;

import org.interpss.CorePluginFactory;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.util.PerformanceTimer;

import java.util.logging.Logger;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;


public class LoadflowPerformance {
	public static void main(String args[]) throws Exception {
		IpssCorePlugin.init();

	  	PerformanceTimer timer = new PerformanceTimer(Logger.getLogger(LoadflowPerformance.class.getName()));

	  	/*
	  	 * time loading data, create ODM and InterPSS Simulation object
	  	 */
	  	timer.start();
		AclfNetwork adjNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("ipss-plugin/ipss.sample/testData/UCTE_2000_WinterOffPeak.ieee")
				.getAclfNet();		  	
	  	timer.logStd("Time for loading the case: ");

		/*
		 * time running a full NR loadflow
		 */
		timer.start();
	  	LoadflowAlgorithm algoLF = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(adjNet);
	  	algoLF.setLfMethod(AclfMethodType.NR);
	  	algoLF.loadflow();
	  	timer.logStd("Time for running Loadflow: ");

	  	/*
	  	 * time running a step of NR method
	  	 */
		timer.start();
	  	algoLF.setLfMethod(AclfMethodType.NR_STEP);
	  	algoLF.loadflow();
	  	timer.logStd("Time for running one step NR Loadflow: ");
	}	
}
