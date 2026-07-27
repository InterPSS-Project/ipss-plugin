package org.interpss.nbreaker;

import org.interpss.fadapter.psse.PSSEDirectParser;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.net.Substation;
import com.interpss.core.net.nb.NBSwitch;

/**
 * Sample: import IEEE14 node-breaker RAW and split substation into groups by bus bars.
 */
public class PSSE_IEEE14_NB_BusSplit_Sample {

	/** Relative to {@code ipss.plugin.core} (launch.json cwd). */
	private static final String CASE =
			"testData/psse/nbreaker/IEEE_14_bus_nodeBreaker_rev35_exported.raw";

	public static void main(String[] args) throws InterpssException {
		AclfNetwork net = new PSSEDirectParser(35).parse(CASE);

		Substation sub2 = net.getSubstation("2");

		SubstationNBreakerHelper subHelper = new SubstationNBreakerHelper(sub2);

		NBSwitch busBar = subHelper.findSwitchByName("Sw-BusBars");
		// 0 open, 1 closed, 2 stuck closed (default 1)
		busBar.setCurrentStatus(0);

		int totalGroupNo = subHelper.topoAnalysis();
		System.out.println("Total number of groups: " + totalGroupNo);

		subHelper.printTopoFlags();
	
		SubstationBusSplitMergeHelper busSplitMergeHelper = new SubstationBusSplitMergeHelper(sub2);
		busSplitMergeHelper.printEquipByGroup(totalGroupNo);

		// split the bus into bus and bus_split
		int groupN1 = busBar.getFromNBNode().getIntFlag();
		int groupN2 = busBar.getToNBNode().getIntFlag();
		busSplitMergeHelper.splitBus(groupN1, groupN2);
	}
}
