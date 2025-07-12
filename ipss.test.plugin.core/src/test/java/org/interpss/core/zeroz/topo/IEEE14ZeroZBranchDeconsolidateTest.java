package org.interpss.core.zeroz.topo;

import static org.junit.Assert.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.funcImpl.zeroz.AclfNetZeroZBranchHelper;
import com.interpss.core.net.BranchBusSide;
import com.interpss.core.net.StatusChangeType;


public class IEEE14ZeroZBranchDeconsolidateTest extends CorePluginTestSetup {
	@Test 
	public void test() throws  InterpssException {
		// Create an AclfNetwork object
		AclfNetwork net = IpssAdapter.importAclfNet("testData/odm/Ieee14Bus_breaker.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();
	  	//System.out.println(net.net2String());
		
	  	System.out.println("Active Bus & Branch: " + net.getNoActiveBus() + " " + net.getNoActiveBranch());
  		assertTrue((net.getNoActiveBus() == 23 && net.getNoActiveBranch() == 30));
  		
		/*
		 * process zeroZ branches by merging the zeroZ branches and connected buses to the retained bus
		 */
		AclfNetZeroZBranchHelper helper = new AclfNetZeroZBranchHelper(net);
		net.getBusList().forEach(bus -> {
			if (bus.isConnect2ZeroZBranch()) 
				helper.zeroZBranchBusMerge(bus.getId());
		});
		
	  	System.out.println("Active Bus & Branch: " + net.getNoActiveBus() + " " + net.getNoActiveBranch());
  		assertTrue((net.getNoActiveBus() == 14 && net.getNoActiveBranch() == 20));

		net.getBusList().forEach(bus -> {
			if (bus.getStatusChangeInfo() == StatusChangeType.OFF_ZBR_BUS_MERGE) {
				//System.out.println("Bus: " + bus.getId() + " is deactivated due to zeroZ branch bus merge");
				bus.setStatus(true);
				
				AclfBus merge2Bus = net.getBus(bus.getMerge2BusId());
				bus.setVoltage(merge2Bus.getVoltage().getReal(), merge2Bus.getVoltage().getImaginary());
			}
			else if (!bus.isActive()) {
				System.out.println("Bus: " + bus.toString());
			}
		});
	  	
	  	net.getBranchList().forEach(branch -> {
			if (branch.getStatusChangeInfo() == StatusChangeType.RECONNECT_ZBR_BUS_MERGE) {
				//System.out.println("Branch: " + branch.getId() + " is reconnected due to zeroZ branch bus merge");
			
				String originalBusId = findBranchIdDiff(branch.getOriginalBranchId(), branch.getId());
				AclfBus originalBus = net.getBus(originalBusId);
				BranchBusSide side = branch.isFromBus(originalBus)?
						BranchBusSide.FROM_SIDE : BranchBusSide.TO_SIDE;
				//System.out.println("Original Bus Id: " + originalBusId + ", From Side: " + side);
				
				branch.reconnect(originalBus, side, false);
			}	
			else if (branch.getStatusChangeInfo() == StatusChangeType.OFF_ZBR_BUS_MERGE) {
				branch.setStatus(true);
			}
		});
		
	  	System.out.println("Active Bus & Branch: " + net.getNoActiveBus() + " " + net.getNoActiveBranch());
  		assertTrue((net.getNoActiveBus() == 23 && net.getNoActiveBranch() == 30));
	}
	
	@Test
	public void findBranchIdDiffTest() {
		String busId = findBranchIdDiff("Bus13->Bus18(1)", "Bus13->Bus14(2)");
		//System.out.println("Bus Id Diff: " + busId);
		assertTrue(busId.equals("Bus18"));
		
		busId = findBranchIdDiff("Bus14->Bus14(1)", "Bus13->Bus14(2)");
		//System.out.println("Bus Id Diff: " + busId);
		assertTrue(busId.equals("Bus14"));
    }	
	
	/**
	 * from the pattern fromId->toId(cirNo), find the different id part of the original branch id as 
       compared with the changed branch id
	 *
	 * @param originalBranchId the original branch id, for example, "Bus13->Bus18(1)"
	 * @param changedBranchId the new branch id, for example, "Bus13->Bus14(2)"
	 */
	public static String findBranchIdDiff(String originalBranchId, String changedBranchId) {
		if (originalBranchId.equals(changedBranchId)) {
			return "";
		}
		
		String[] originalParts = originalBranchId.split("->|\\(|\\)");
		String[] changedParts = changedBranchId.split("->|\\(|\\)");
		
		for (int i = 0; i < originalParts.length; i++) {
			if (!originalParts[i].equals(changedParts[i])) {
				return originalParts[i];
			}
		}
		
		return "";
	}
}
