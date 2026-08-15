package org.interpss.nbreaker;

import java.util.Set;

import org.interpss.fadapter.psse.PSSEDirectParser;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.funcImpl.topo.SubstationNBreakerHelper;
import com.interpss.core.funcImpl.topo.AclfNetTopoHelper;

/**
 * Sample: import PSS/E v36 sample_nb RAW (node-breaker overlay) and inspect substations.
 */
public class PSSE_Sample_NB_TopoAnalysis_Sample {

	/** Relative to {@code ipss.plugin.core} (launch.json cwd). */
	private static final String CASE = "testData/private/sample_nb.raw";

	private static Set<String> activateBusIds = Set.of("Bus208", "Bus209", "Bus3012");
	private static Set<String> activateBranchIds = Set.of("3WNDTR_205_215_208_3->Bus208(3)", "Bus209->3WNDTR_209_217_218_4(4)", "3WNDTR_3008_3012_3010_2->Bus3012(2)");
	/**
	 * The following branches are not active, not due to the topo configuration:
		Branch Bus3005->Bus3008(1) is not active
		Branch Bus153->Bus155(FACTS_DVCE_2) is not active
	 */

	public static void main(String[] args) throws InterpssException {
		case0();
		case1();
	}

	private static void case0() throws InterpssException {
		System.out.println("Case 0: basecase condition");

		AclfNetwork net = new PSSEDirectParser().parse(CASE);
		
		net.getSubstationMap().forEach((subName, sub) -> {	
			SubstationNBreakerHelper subHelper = new SubstationNBreakerHelper(sub);
			subHelper.topoAnalysis();
		});

		net.getSubstationMap().forEach((subName, sub) -> {	
			//AclfNetInfoHelper.outputSubstationAclfInfo(net, subName, false);
		});

		// at this stage, all active buses in the network should have intFlag set.
		net.getBusList().forEach( bus -> {
				if (bus.isActive() && bus.getIntFlag() == 0) {
					System.out.println("Bus " + bus.getId() + " @" + bus.getSubstationId() + " has no intFlag set.");
				}
			});

		// at this stage, all active branches in the network should have intFlag set.
		net.getBranchList().forEach( branch -> {
			if (!branch.isActive()) {
				System.out.println("Branch " + branch.getId() + " is not active");
			}
			if (branch.isActive() && (branch.getFromBus().getIntFlag() == 0 || branch.getToBus().getIntFlag() == 0)) {
				System.out.println("Branch " + branch.getId() + " @" + branch.getFromBus().getSubstationId() + " has no intFlag set.");
				System.out.println("Branch " + branch.getId() + " @" + branch.getToBus().getSubstationId() + " has no intFlag set.");
			}
		});

		// 3W star bus inherits intFlag from the from-bus (Bus3008) topo group.
		AclfBus starBus = net.getBus("3WNDTR_3008_3012_3010_2");
		AclfBus bus3008 = net.getBus("Bus3008");
		System.out.println("Star bus " + starBus.getId() + " intFlag=" + starBus.getIntFlag()
				+ " (Bus3008 intFlag=" + bus3008.getIntFlag() + ")");
	}

	private static void case1() throws InterpssException {
		System.out.println("Case 1: topo processed network");

		AclfNetwork net = new PSSEDirectParser().parse(CASE);

		activateBusBranch(net);

		new AclfNetTopoHelper(net).topoProcessing();

		// the active buses and branches should be turned off after the topo processing
		net.getBusList().forEach(bus -> {
			if (activateBusIds.contains(bus.getId())) {
				System.out.println(bus.getId() + ", " + bus.isActive());
			}
		});
		
		net.getBranchList().forEach(branch -> {
			if (activateBranchIds.contains(branch.getId())) {
				System.out.println(branch.getId() + ", " + branch.isActive());
			}
		});
	}
	
	public static void activateBusBranch(AclfNetwork net) throws InterpssException {
		net.getBusList().forEach(bus -> {
			if (activateBusIds.contains(bus.getId())) {
				System.out.println(bus.getId() + " set active");
				bus.setStatus(true);
			}
		});

		net.getBranchList().forEach(branch -> {
			if (activateBranchIds.contains(branch.getId())) {
				System.out.println(branch.getId() + " set active");
				branch.setStatus(true);
			}
		});
	}
}

/*
Case 1: fully connected network
Bus208 set active
Bus209 set active
Bus3012 set active
===========
Bus Bus208 @5 has no intFlag set.
Bus Bus209 @6 has no intFlag set.
Bus Bus3012 @9 has no intFlag set.

xxxxxxxxxxxxxx

x 3WNDTR_205_215_208_3->Bus208(3) set active
x Bus209->3WNDTR_209_217_218_4(4) set active
x3WNDTR_3008_3012_3010_2->Bus3012(2) set active
=================
Branch 3WNDTR_205_215_208_3->Bus208(3) to @5 has no intFlag set.
Branch Bus209->3WNDTR_209_217_218_4(4) from @6 has no intFlag set.
Branch 3WNDTR_3008_3012_3010_2->Bus3012(2) to @9 has no intFlag set.
 */