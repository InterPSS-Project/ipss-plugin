package org.interpss.core.zeroz;

import static org.junit.Assert.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetModelType;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfSwingBusAdapter;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.funcImpl.AclfNetObjectComparator;
import com.interpss.core.funcImpl.zeroz.AclfNetZeroZBranchHelper;
import com.interpss.core.funcImpl.zeroz.AclfNetZeroZDeconsolidator;

//ZeroZBranch Mark : IEEE14Bus Zero Z Branch Deconsolidation Test
public class IEEE14ZeroZBranchDeconsolidateTest extends CorePluginTestSetup {
	@Test 
	public void test() throws  InterpssException {
		// Create an AclfNetwork object
		AclfNetwork net = IpssAdapter.importAclfNet("testData/odm/zeroz/Ieee14Bus_breaker_loop.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();
		net.setAclfNetModelType(AclfNetModelType.ZBR_MODEL);
	  	//System.out.println(net.net2String());
		
	  	//System.out.println("Active Bus & Branch: " + net.getNoActiveBus() + " " + net.getNoActiveBranch());
  		assertTrue((net.getNoActiveBus() == 23 && net.getNoActiveBranch() == 30));
  		
		/*
		 * process zeroZ branches by merging the zeroZ branches and connected buses to the retained bus
		 */
		AclfNetZeroZBranchHelper.NotMergeBusConnectXfr = false;
  		new AclfNetZeroZBranchHelper(net).consolidate();
		
	  	//System.out.println("Active Bus & Branch: " + net.getNoActiveBus() + " " + net.getNoActiveBranch());
  		assertTrue((net.getNoActiveBus() == 14 && net.getNoActiveBranch() == 20));
  		assertTrue(net.getBus("Bus14").getContributeLoadList().size() == 2);
  		assertTrue(net.getBus("Bus14_1").getContributeLoadList().size() == 1);
  		assertTrue(net.getBus("Bus14_1").getContributeLoadList().get(0).isActive() == false);
  		
  		new AclfNetZeroZDeconsolidator(net).deconsolidate(false);
		
	  	//System.out.println("Active Bus & Brancßh: " + net.getNoActiveBus() + " " + net.getNoActiveBranch());
  		assertTrue((net.getNoActiveBus() == 23 && net.getNoActiveBranch() == 30));
  		assertTrue(net.getBus("Bus14").getContributeLoadList().size() == 1);
  		assertTrue(net.getBus("Bus14").getContributeLoadList().get(0).isActive() == true);
  		assertTrue(net.getBus("Bus14_1").getContributeLoadList().size() == 1);
  		assertTrue(net.getBus("Bus14_1").getContributeLoadList().get(0).isActive());
	}
	
	@Test 
	public void testAclf() throws  InterpssException {
		// Create an AclfNetwork object
		AclfNetwork net = IpssAdapter.importAclfNet("testData/odm/zeroz/Ieee14Bus_breaker.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();
		net.setAclfNetModelType(AclfNetModelType.ZBR_MODEL);
	  	//System.out.println(net.net2String());
		
		/*
		 * (1) process zeroZ branches by merging the zeroZ branches and connected buses to the retained bus
		 */
		AclfNetZeroZBranchHelper helper = new AclfNetZeroZBranchHelper(net);
		AclfNetZeroZBranchHelper.NotMergeBusConnectXfr = false;
		helper.consolidate();

		// (2) Deconsolidate the network by restoring the buses and branches 
		// that were deactivated during the zero-Z branch bus merge process
  		new AclfNetZeroZDeconsolidator(net).deconsolidate(false);
		
		/*
		 * (3) process zeroZ branches again.
		 */
  		helper.consolidate();

  		assertTrue((net.getNoActiveBus() == 14 && net.getNoActiveBranch() == 20));
  		
  		/*
  		 * (4) run loadflow calculation. There should be no difference in the results
  		 */
  		//IpssLogger.getLogger().setLevel(Level.INFO);
	  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	algo.loadflow();
  		//System.out.println(net.net2String());
	  	
	  	/*
	  	 * Check if loadflow has converged
	  	 */
  		assertTrue(net.isLfConverged());
  		
	  	// output loadflow calculation results
	  	//System.out.println(AclfOutFunc.loadFlowSummary(net));
  		
  		// See IEEE14Bus_odm_Test.java for the expected values
  		AclfBus swingBus = (AclfBus)net.getBus("Bus1");
  		AclfSwingBusAdapter swing = swingBus.toSwingBus();
		//System.out.println(swing.getGenResults(UnitType.PU).getReal());
		//System.out.println(swing.getGenResults(UnitType.PU).getImaginary());
 		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()-2.3239)<0.0001);
  		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary()+0.1654)<0.0001);
	}
	
	// Compare the network before and after zero-Z branch consolidation.
	// Since the zero-Z branch consolidation is not a 100% reversible process, this test is commented out.
	//@Test 
	public void testCompare() throws  InterpssException {
		// Create an AclfNetwork object
		AclfNetwork net = IpssAdapter.importAclfNet("testData/odm/zeroz/Ieee14Bus_breaker.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();
		net.setAclfNetModelType(AclfNetModelType.ZBR_MODEL);
	  	//System.out.println(net.net2String());
		
	  	//System.out.println("Active Bus & Branch: " + net.getNoActiveBus() + " " + net.getNoActiveBranch());
  		assertTrue((net.getNoActiveBus() == 23 && net.getNoActiveBranch() == 30));
  		
		/*
		 * process zeroZ branches by merging the zeroZ branches and connected buses to the retained bus
		 */
		AclfNetZeroZBranchHelper helper = new AclfNetZeroZBranchHelper(net);
		net.getBusList().forEach(bus -> {
			if (bus.isConnect2ZeroZBranch()) 
				helper.zeroZBranchBusMerge(bus.getId());
		});
		
	  	//System.out.println("Active Bus & Branch: " + net.getNoActiveBus() + " " + net.getNoActiveBranch());
  		assertTrue((net.getNoActiveBus() == 14 && net.getNoActiveBranch() == 20));

  		new AclfNetZeroZDeconsolidator(net).deconsolidate(true);
		
	  	//System.out.println("Active Bus & Branch: " + net.getNoActiveBus() + " " + net.getNoActiveBranch());
  		assertTrue((net.getNoActiveBus() == 23 && net.getNoActiveBranch() == 30));
  		
		AclfNetwork net1 = IpssAdapter.importAclfNet("testData/odm/zeroz/Ieee14Bus_breaker.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();
		
  		AclfNetObjectComparator comp = new AclfNetObjectComparator(net, net1);
  		comp.compareNetwork();
  		
  		System.out.println("Differences found: " + comp.getDiffMsgList());
  		//assertTrue("" + comp.getDiffMsgList(), comp.getDiffMsgList().size() == 0);
	}
}
