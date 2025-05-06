package org.interpss.core.adapter.ieee;

import static org.junit.Assert.assertTrue;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.NumericUtil;
import org.junit.Test;

import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfSwingBusAdapter;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.funcImpl.zeroz.dep.ZeroZBranchNetHelper;


public class IEEE009Bus_Test extends CorePluginTestSetup{
	@Test
	public void testLF() throws Exception{
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/009ieee.cf")
				.getAclfNet();	

		
  		assertTrue((net.getBusList().size() == 9&& net.getBranchList().size() == 9));

	  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	algo.loadflow();
	  	
  		assertTrue(net.isLfConverged());		
 		AclfBus swingBus = (AclfBus)net.getBus("Bus1");
 		AclfSwingBusAdapter swing = swingBus.toSwingBus();
		//System.out.println(swing.getGenResults(UnitType.PU));
		//System.out.println(AclfOutFunc.loadFlowSummary(net));
  		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()-0.7164098)<1.0E-5);
  		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary()-0.2704474)<1.0E-5);
  		
  		ZeroZBranchNetHelper helper = new ZeroZBranchNetHelper(true);
  		for (AclfBus bus : net.getBusList()) 
  			if (!NumericUtil.equals(helper.powerIntoNet(bus), bus.powerIntoNet(), 0.0002))
  				System.out.println("Large mismatch: " + bus.getId() + " : " + helper.powerIntoNet(bus) + ", " + bus.powerIntoNet());
	}
}
