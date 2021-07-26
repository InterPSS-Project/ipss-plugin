package org.interpss.core.mnet;

import static org.interpss.pssl.plugin.IpssAdapter.FileFormat.PSSE;
import static org.junit.Assert.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.IpssCorePlugin;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.pssl.plugin.IpssAdapter;
import org.interpss.pssl.plugin.IpssAdapter.PsseVersion;
import org.junit.Test;

import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfSwingBusAdapter;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.algo.impl.solver.DefaultMultiNetLfSolver;
import com.interpss.core.net.BranchBusSide;
import com.interpss.core.net.childnet.ChildNetInterfaceBranch;
import com.interpss.core.net.childnet.ChildNetwork;
import com.interpss.core.net.childnet.solver.ChildNetworkProcessor;

public class MNet_IEEE9_PSSE_Test extends CorePluginTestSetup {
	
	@Test
	public void testFullNetLF() throws Exception{
		IpssCorePlugin.init();
		
		AclfNetwork net =IpssAdapter.importAclfNet("testdata/adpter/psse/v31/ieee9_v31.raw")
                .setFormat(PSSE)
                .setPsseVersion(PsseVersion.PSSE_31)
                .load()
                .getImportedObj();	

		
  		assertTrue((net.getBusList().size() == 9&& net.getBranchList().size() == 9));

	  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	algo.loadflow();
	  	
  		assertTrue(net.isLfConverged());		
 		AclfBus swingBus = (AclfBus)net.getBus("Bus1");
 		AclfSwingBusAdapter swing = swingBus.toSwingBus();
		//System.out.println(swing.getGenResults(UnitType.PU));
		//System.out.println(AclfOutFunc.loadFlowSummary(net));
  		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()-0.7164)<1.0E-4);
  		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary()-0.2710)<1.0E-4);
	}
	
	@Test
	public void testMultiNetLF() throws Exception{
		//IpssCorePlugin.init();
		
		//load the IEEE 9 Bus system
		AclfNetwork net =IpssAdapter.importAclfNet("testdata/adpter/psse/v31/ieee9_v31.raw")
		                            .setFormat(PSSE)
		                            .setPsseVersion(PsseVersion.PSSE_31)
		                            .load()
		                            .getImportedObj();	

		//Create childNet
        ChildNetwork<AclfBus,AclfBranch> childNet = CoreObjectFactory.createChildAclfNet(net, "childNet");
		
		/*
		 * define three interfacing branches.
		 */
		ChildNetInterfaceBranch intBranch_7_8 = CoreObjectFactory.createChildNetInerfaceBranch(childNet);	
		intBranch_7_8.setBranchId("Bus7->Bus8(0)");
		intBranch_7_8.setInterfaceBusSide(BranchBusSide.FROM_SIDE);		
		intBranch_7_8.setChildNetSide(BranchBusSide.FROM_SIDE);
		childNet.getInterfaceBranches().add(intBranch_7_8);	

		ChildNetInterfaceBranch intBranch_4_5 = CoreObjectFactory.createChildNetInerfaceBranch(childNet);	
		intBranch_4_5.setBranchId("Bus4->Bus5(0)");
		intBranch_4_5.setInterfaceBusSide(BranchBusSide.TO_SIDE);		
		intBranch_4_5.setChildNetSide(BranchBusSide.TO_SIDE);
		childNet.getInterfaceBranches().add(intBranch_4_5);	
		
		// split the parent/child network
		new ChildNetworkProcessor(net).processChildNet();
				
		assertTrue((net.getBusList().size() == 8 && net.getBranchList().size() == 7));
		assertTrue(net.getChildNetList().size() > 0);
		
		// check the created child network
		AclfNetwork childAclfNet = (AclfNetwork)net.getChildNet("childNet").getNetwork();
		assertTrue((childAclfNet.getBusList().size() == 3 && childAclfNet.getBranchList().size() == 2));
		
		for (ChildNetInterfaceBranch cbranch : childNet.getInterfaceBranches()) {
			// interface bus should still be in the parent net
			assertTrue(net.getBus(cbranch.getInterfaceBusParentNet().getId()) != null);
			// interface bus should be defined as Swing bus in the child net
			assertTrue(childAclfNet.getBus(cbranch.getInterfaceBusIdChildNet()).isSwing());
		}
		
		/*
		 * Run multi-network Loadflow
		 */
		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
		algo.setMultiNetSolver(new DefaultMultiNetLfSolver(algo));
	  	algo.loadflow();
	  	
  		assertTrue(net.isLfConverged());		
 		AclfBus swingBus = (AclfBus)net.getBus("Bus1");
 		AclfSwingBusAdapter swing = swingBus.toSwingBus();
		//System.out.println(swing.getGenResults(UnitType.PU));
		//System.out.println(AclfOutFunc.loadFlowSummary(net));
  		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()-0.7164)<1.0E-4);
  		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary()-0.2710)<1.0E-4);
	}
}


