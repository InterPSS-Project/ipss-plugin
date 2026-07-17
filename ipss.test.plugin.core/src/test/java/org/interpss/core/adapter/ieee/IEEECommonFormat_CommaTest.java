package org.interpss.core.adapter.ieee;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.datatype.Unit.UnitType;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfSwingBusAdapter;
import com.interpss.core.algo.LoadflowAlgorithm;

public class IEEECommonFormat_CommaTest extends CorePluginTestSetup {
	@Test 
	public void testCase1() throws Exception {
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/ieee14_comma.ieee")
				.getAclfNet();	
		
		assertTrue((net.getBusList().size() == 14 && net.getBranchList().size() == 20));

		assertTrue(net.getBus("Bus1").getId().equals("Bus1"));
		assertTrue(net.getBus("Bus1").getContributeGen("Bus1-G1") != null);
		assertTrue(net.getBus("Bus1").getContributeGen("Bus1-G1").getName().equals("Bus1-G1"));
		
		assertTrue(net.getBus("Bus2").getContributeGen("Bus2-G1") != null);
		assertTrue(net.getBus("Bus2").getContributeGen("Bus2-G1").getName().equals("Bus2-G1"));
		assertTrue(net.getBus("Bus2").getContributeLoad("Bus2-L1") != null);
		assertTrue(net.getBus("Bus2").getContributeLoad("Bus2-L1").getName().equals("Bus2-L1"));
		
		
		//System.out.println(adapter.getODMModelParser().toString());
		
	  	LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
	  	algo.loadflow();

	  	//System.out.println(net.net2String());
  		assertTrue(net.isLfConverged());		
  		AclfBus swingBus = (AclfBus)net.getBus("Bus1");
  		AclfSwingBusAdapter swing = swingBus.toSwingBus();
  		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()-2.32393)<0.0001);
  		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary()+0.16549)<0.0001);
  		
  		/*
  		System.out.println(CorePluginFunction.AclfResultBusStyle.f(net));
  		
  		System.out.println(net.getBus("Bus9").powerIntoNet());
  		System.out.println(NetInjectionHelper.powerIntoNet(net.getBus("Bus9")));
  		*/
	}
}

