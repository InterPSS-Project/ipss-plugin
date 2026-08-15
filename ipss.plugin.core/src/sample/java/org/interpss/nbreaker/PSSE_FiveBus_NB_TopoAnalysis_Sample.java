package org.interpss.nbreaker;

import org.interpss.fadapter.psse.PSSEDirectParser;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.funcImpl.topo.AclfNetTopoHelper;
import com.interpss.core.funcImpl.topo.SubstationNBreakerHelper;
import com.interpss.core.net.Substation;
import com.interpss.core.net.nb.NBSwitch;

/**
 * Sample: import PowSyBl five_bus node-breaker RAW (v35) and inspect substations.
 * <p>
 * case0 — basecase {@code topoAnalysis()} + intFlag checks (all switches closed).<br>
 * case1 — open {@code Sw-BusBars} on STATION 1, then {@code topoProcessing()}.
 */
public class PSSE_FiveBus_NB_TopoAnalysis_Sample {

	/** Relative to {@code ipss.plugin.core} (launch.json cwd). */
	private static final String CASE =
			"testData/psse/nbreaker/five_bus_nodeBreaker_rev35.raw";

	public static void main(String[] args) throws InterpssException {
		case0();
		case1();
	}

	private static void case0() throws InterpssException {
		System.out.println("Case 0: basecase condition (all switches closed)");

		AclfNetwork net = new PSSEDirectParser().parse(CASE);

		net.getSubstationMap().forEach((subId, sub) -> {
			SubstationNBreakerHelper subHelper = new SubstationNBreakerHelper(sub);
			int groups = subHelper.topoAnalysis();
			System.out.println("Substation " + subId + " (" + sub.getName() + ") groups=" + groups);
		});

		net.getBusList().forEach(bus -> {
			if (bus.isActive() && bus.getSubstation() != null && bus.getIntFlag() == 0) {
				System.out.println("Bus " + bus.getId() + " @" + bus.getSubstationId() + " has no intFlag set.");
			}
		});

		net.getBranchList().forEach(branch -> {
			if (!branch.isActive()) {
				System.out.println("Branch " + branch.getId() + " is not active");
			}
			if (branch.isActive()
					&& ((branch.getFromBus().getSubstation() != null && branch.getFromBus().getIntFlag() == 0)
							|| (branch.getToBus().getSubstation() != null && branch.getToBus().getIntFlag() == 0))) {
				System.out.println("Branch " + branch.getId()
						+ " from@" + branch.getFromBus().getSubstationId()
						+ " to@" + branch.getToBus().getSubstationId()
						+ " has end-bus with no intFlag set.");
			}
		});

		// 3W star bus inherits intFlag from the from-bus (Bus1) topo group.
		AclfBus starBus = net.getBus("3WNDTR_1_3_4_1");
		AclfBus bus1 = net.getBus("Bus1");
		if (starBus != null && bus1 != null) {
			System.out.println("Star bus " + starBus.getId() + " intFlag=" + starBus.getIntFlag()
					+ " (Bus1 intFlag=" + bus1.getIntFlag() + ")");
		}
	}

	private static void case1() throws InterpssException {
		System.out.println("Case 1: open Sw-BusBars on STATION 1, then topoProcessing");

		AclfNetwork net = new PSSEDirectParser().parse(CASE);

		Substation sub1 = net.getSubstation("1");
		SubstationNBreakerHelper helper = new SubstationNBreakerHelper(sub1);
		NBSwitch busBar = helper.findSwitchByName("Sw-BusBars");
		// 0 open, 1 closed, 2 stuck closed (default 1)
		busBar.setCurrentStatus(0);
		System.out.println("Opened " + busBar.getName()
				+ " (" + busBar.getFromNBNode().getName()
				+ " ↔ " + busBar.getToNBNode().getName() + ")");

		int groups = helper.topoAnalysis();
		System.out.println("STATION 1 groups after open: " + groups);
		helper.printTopoFlags();

		new AclfNetTopoHelper(net).topoProcessing();

		net.getBusList().forEach(bus -> {
			if (bus.getSubstationId() != null && bus.getSubstationId().equals("1")) {
				System.out.println(bus.getId() + " active=" + bus.isActive() + " intFlag=" + bus.getIntFlag());
			}
		});
	}
}
