package org.interpss.nbreaker;

import org.interpss.fadapter.psse.PSSEDirectParser;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.net.Substation;
import com.interpss.core.net.nb.NBSwitch;

/**
 * Sample: import IEEE14 node-breaker RAW and print Substation → Bus → NBNode → equip terminals.
 */
public class PSSE_IEEE14_NodeBreaker_Sample {

	/** Relative to {@code ipss.plugin.core} (launch.json cwd). */
	private static final String CASE =
			"testData/psse/nbreaker/IEEE_14_bus_nodeBreaker_rev35_exported.raw";

	public static void main(String[] args) throws InterpssException {
		AclfNetwork net = new PSSEDirectParser(35).parse(CASE);

		Substation sub2 = net.getSubstation("2");

		SubstationNBreakerHelper subHelper = new SubstationNBreakerHelper(sub2);
		subHelper.printSubstationTree();

		NBSwitch busBar = subHelper.findSwitchByName("Sw-BusBars");
		// 0 open, 1 closed, 2 stuck closed (default 1)
		busBar.setCurrentStatus(0);

		System.out.println("BusBar Id: " + busBar.getId());
		System.out.println("BusBar from Node: " + busBar.getFromNBNode().getName());
		System.out.println("BusBar to Node: " + busBar.getToNBNode().getName());

		subHelper.printSubstationTree();

		sub2.getNbNodeList().stream().forEach(node -> {
			
		});

		sub2.getNbSwitchList().stream().forEach(sw -> {
		});
	}
}
