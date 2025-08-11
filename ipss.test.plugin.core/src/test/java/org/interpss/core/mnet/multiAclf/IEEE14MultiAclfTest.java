package org.interpss.core.mnet.multiAclf;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.display.AclfOutFunc;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.datatype.Unit.UnitType;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfSwingBusAdapter;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.state.aclf.AclfNetworkState;

public class IEEE14MultiAclfTest extends CorePluginTestSetup {
	@Test
	public void test() throws Exception {
		AclfNetwork net = CorePluginFactory
					.getFileAdapter(IpssFileAdapter.FileFormat.IpssInternal)
					.load("testData/ipssdata/Ieee14.ipssdat")
					.getAclfNet();	
		
  		//System.out.println(net.net2String());
  		assertTrue((net.getBusList().size() == 14 && net.getBranchList().size() == 20));

  		// Cache the AclfNetwork state for multiple AclfNetwork copy creation
  		AclfNetworkState aclfNetState = new AclfNetworkState(net);

  		for (int i = 0; i < net.getBusList().size(); i++) {
  			// create a copy of the AclfNetwork
	  		AclfNetwork netCopy = AclfNetworkState.create(aclfNetState);
	  		
		  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(netCopy);
		  	algo.loadflow();
	  		//System.out.println(net.net2String());
		  	
	  		assertTrue(netCopy.isLfConverged());
	  		
	  		/*
	  		 * Bus (id="1") is a swing bus. Make sure the P and Q results are with the expected values
	  		 */
	  		AclfBus swingBus = (AclfBus)netCopy.getBus("1");
	  		AclfSwingBusAdapter swing = swingBus.toSwingBus();
			//System.out.println(swing.getGenResults(UnitType.PU).getReal());
			//System.out.println(swing.getGenResults(UnitType.PU).getImaginary());
	  		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()-2.32373)<0.0001);
	  		assertTrue( Math.abs(swing.getGenResults(UnitType.PU).getImaginary()+0.17462)<0.0001);
  		}
	}
}

