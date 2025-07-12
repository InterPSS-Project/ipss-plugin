package org.interpss.core.zeroz.topo;

import static org.junit.Assert.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.funcImpl.AclfNetObjectComparator;
import com.interpss.core.funcImpl.zeroz.AclfNetZeroZBranchHelper;
import com.interpss.core.funcImpl.zeroz.AclfNetZeroZDeconsolidator;


public class IEEE14ZeroZBranchDeconsolidateTest extends CorePluginTestSetup {
	@Test 
	public void test() throws  InterpssException {
		// Create an AclfNetwork object
		AclfNetwork net = IpssAdapter.importAclfNet("testData/odm/Ieee14Bus_breaker.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();
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

  		new AclfNetZeroZDeconsolidator(net).deconsolite();
		
	  	//System.out.println("Active Bus & Branch: " + net.getNoActiveBus() + " " + net.getNoActiveBranch());
  		assertTrue((net.getNoActiveBus() == 23 && net.getNoActiveBranch() == 30));
	}
	
	//@Test 
	public void testCompare() throws  InterpssException {
		// Create an AclfNetwork object
		AclfNetwork net = IpssAdapter.importAclfNet("testData/odm/Ieee14Bus_breaker.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();
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

  		new AclfNetZeroZDeconsolidator(net).deconsolite();
		
	  	//System.out.println("Active Bus & Branch: " + net.getNoActiveBus() + " " + net.getNoActiveBranch());
  		assertTrue((net.getNoActiveBus() == 23 && net.getNoActiveBranch() == 30));
  		
		AclfNetwork net1 = IpssAdapter.importAclfNet("testData/odm/Ieee14Bus_breaker.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();
		
  		AclfNetObjectComparator comp = new AclfNetObjectComparator(net, net1);
  		comp.compareNetwork();
  		
  		System.out.println("Differences found: " + comp.getDiffMsgList());
  		//assertTrue("" + comp.getDiffMsgList(), comp.getDiffMsgList().size() == 0);
	}
}
