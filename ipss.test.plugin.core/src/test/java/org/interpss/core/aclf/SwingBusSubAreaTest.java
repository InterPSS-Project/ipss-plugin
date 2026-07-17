package org.interpss.core.aclf;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.junit.jupiter.api.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.funcImpl.AclfNetHelper;

public class SwingBusSubAreaTest extends CorePluginTestSetup {
	@Test
	public void connectedSubAreaTest() throws InterpssException {
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IpssInternal)
				.load("testData/ipssdata/BUS1824.ipssdat")
				.getAclfNet();	

		//System.out.println(net.net2String());
  		assertTrue((net.getBusList().size() == 1824));
  	
		AclfNetHelper helper = new AclfNetHelper(net);
		Set<String> busSet = helper.calConnectedSubArea("1a");
	  	assertTrue(busSet.size() == 57);
	  	
		busSet = helper.calConnectedSubArea("1z");
	  	assertTrue(busSet.size() == 57);
	  	
	  	AclfNetwork subNet = net.createSubNet(busSet, false, false);
	  	assertTrue(subNet.getNoBus() == 57);
	  	
	  	LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(subNet);
	  	algo.setLfMethod(AclfMethodType.NR);
	  	algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
	  	algo.loadflow();
  		//System.out.println(net.net2String());
	  	
  		//System.out.println(AclfOutFunc.loadFlowSummary(subNet, true));
	  	
  		assertTrue(subNet.isLfConverged());	
	}
}

