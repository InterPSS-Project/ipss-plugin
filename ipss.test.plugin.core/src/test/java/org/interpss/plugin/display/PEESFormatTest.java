package org.interpss.plugin.display;

import org.interpss.CorePluginFactory;
import org.interpss.fadapter.IpssFileAdapter;
import org.junit.jupiter.api.Test;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;

public class PEESFormatTest {
	@Test
	public void testCase1() throws Exception {
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.PSSE, IpssFileAdapter.Version.PSSE_30)
				.loadAclfNet("testData/adpter/psse/PSSE_5Bus_Test.raw");
		
	  	LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
	  	algo.setLfMethod(AclfMethodType.PQ);
	  	algo.loadflow();
	}
}
