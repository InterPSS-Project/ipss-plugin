package org.interpss.core.aclf;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.display.AclfOutFunc;
import org.interpss.fadapter.IpssFileAdapter;
import org.junit.jupiter.api.Test;

import com.interpss.core.CoreObjectFactory;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.Aclf3WXformerAdapter;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;

public class IEEE14_3WXfrTest extends CorePluginTestSetup {
	@Test 
	public void bus14testCase() throws Exception {
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/Ieee14Bus.ieee")
				.getAclfNet();		
		
		net.removeBranch("Bus4->Bus7(1)");
		net.removeBranch("Bus4->Bus9(1)");
		net.removeBranch("Bus7->Bus9(1)");
		
		Aclf3WXformerAdapter xfr3W = CoreObjectFactory.createAclf3WXfr("Bus4", "Bus7", "Bus9", net);
		
		xfr3W.setZ(new Complex(0.0, 0.01), new Complex(0.0, 0.03), new Complex(0.0, 0.01));
		
		AclfBus bus = net.getBusList().get(2);
  		assertTrue(bus.isPVBusLimit());		
  		assertTrue(!bus.isRemoteQBus());		
  		//bus.getRemoteQBus();
		
		//System.out.println(net.net2String());
		
	  	LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
	  	algo.setInitBusVoltage(true);
	  	algo.setLfMethod(AclfMethodType.NR);
	  	
	  	//Note: NR with flat start or PQ method produces converged power flow.
	  	algo.loadflow();
	  	
  		//System.out.println(net.net2String());
	  	
 		//System.out.println(AclfOutFunc.loadFlowSummary(net));
	  	
  		assertTrue(net.isLfConverged());		
	}
}

