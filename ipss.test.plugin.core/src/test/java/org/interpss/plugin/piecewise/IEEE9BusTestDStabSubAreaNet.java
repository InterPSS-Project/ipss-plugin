package org.interpss.plugin.piecewise;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.psse.PSSEMultiFileLoader;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.piecewise.subAreaNet.seq012.SubDStabNetwork;
import org.interpss.piecewise.subAreaNet.seq012.impl.SubAreaDStabProcessorImpl;
import org.interpss.piecewise.subAreaNet.seq012.impl.SubNetworkDStabProcessorImpl;
import org.junit.jupiter.api.Test;

import com.interpss.algo.subAreaNet.SubAreaNetProcessor;
import com.interpss.algo.subAreaNet.seq012.CuttingBranch012;
import com.interpss.algo.subAreaNet.seq012.SubArea012;
import com.interpss.common.exp.InterpssException;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabBranch;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabLoad;
import com.interpss.simu.SimuContext;

public class IEEE9BusTestDStabSubAreaNet {
	
	@Test
	public void testSubArea() throws Exception {
		IpssCorePlugin.init();
	
		BaseDStabNetwork dsNet = getTestNet();
	    
	    /*
	    	Cutting branches "Bus5->Bus7(0)", "Bus7->Bus8(0)"
	    
	    	It breaks the network into two sub-areas
	    
	       		[ Bus1, Bus3, Bus4, Bus5, Bus6, Bus8, Bus9 ]
           		[ Bus2, Bus7 ]
	     */
	    
		SubAreaNetProcessor<BaseDStabBus<DStabGen,DStabLoad>, DStabBranch, SubArea012, Complex3x1> proc = 
				new SubAreaDStabProcessorImpl<SubArea012>(dsNet, new CuttingBranch012[] { 
						new CuttingBranch012("Bus5->Bus7(0)"),
						new CuttingBranch012("Bus7->Bus8(0)")});	
  		
  		proc.processSubAreaNet();
  		
  		proc.getSubAreaNetList().forEach(subarea -> {
  			//System.out.println(subarea);
  		});		
  		// System.out.println(dsNet.net2String());

  		assertTrue(proc.getSubAreaNetList().size() == 2, "We should have total two SubAreas");

  		assertTrue(dsNet.getBus("Bus1").getSubAreaFlag() == 1, "Bus1 should be in the SubArea (1)");
  		assertTrue(dsNet.getBus("Bus9").getSubAreaFlag() == 1, "Bus9 should be in the SubArea (1)");
  		
  		assertTrue(dsNet.getBus("Bus2").getSubAreaFlag() == 2, "Bus2 should be in the SubArea (2)");
  		assertTrue(dsNet.getBus("Bus7").getSubAreaFlag() == 2, "Bus7 should be in the SubArea (2)");
	}

	@Test
	public void testSubNetwork() throws Exception {
		IpssCorePlugin.init();
	
		BaseDStabNetwork dsNet = getTestNet();
	    
	    SubAreaNetProcessor<BaseDStabBus<DStabGen,DStabLoad>, DStabBranch, SubDStabNetwork, Complex3x1> proc = 
				new SubNetworkDStabProcessorImpl<SubDStabNetwork>(dsNet, new CuttingBranch012[] { 
						new CuttingBranch012("Bus5->Bus7(0)"),
						new CuttingBranch012("Bus7->Bus8(0)")});	
  		
  		proc.processSubAreaNet();
  		
  		//proc.getSubAreaNetList().forEach(subarea -> {
  		//	System.out.println(subarea);
  		//});		
  		// System.out.println(dsNet.net2String());

  		assertTrue(proc.getSubAreaNetList().size() == 2, "We should have total two SubAreas");

  		assertTrue(proc.getSubAreaNet(1).getInterfaceBusIdList().size() == 2, "SubArea (1) should have 2 interface buses");
  		assertTrue(proc.getSubAreaNet(1).getSubNet().getBusList().size() == 7, "SubArea (1) should have 7 buses");

  		assertTrue(dsNet.getBus("Bus1").getSubAreaFlag() == 1, "Bus1 should be in the SubArea (1)");
  		assertTrue(dsNet.getBus("Bus9").getSubAreaFlag() == 1, "Bus9 should be in the SubArea (1)");
  		
  		assertTrue(proc.getSubAreaNet(2).getInterfaceBusIdList().size() == 1, "SubArea (1) should have 1 interface bus");
  		assertTrue(proc.getSubAreaNet(2).getSubNet().getBusList().size() == 2, "SubArea (2) should have 2 buses");

  		assertTrue(dsNet.getBus("Bus2").getSubAreaFlag() == 2, "Bus2 should be in the SubArea (2)");
  		assertTrue(dsNet.getBus("Bus7").getSubAreaFlag() == 2, "Bus7 should be in the SubArea (2)");
	}
	
	private BaseDStabNetwork getTestNet() throws Exception {
		SimuContext simuCtx = new PSSEMultiFileLoader(30).loadDStab(
				"testData/psse/v30/IEEE9Bus/ieee9.raw",
				"testData/psse/v30/IEEE9Bus/ieee9.seq",
				"testData/psse/v30/IEEE9Bus/ieee9_dyn_onlyGen.dyr");
		
	    return simuCtx.getDStabilityNet();
	}
}
