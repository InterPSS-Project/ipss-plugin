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
 * Sample: import PowSyBl twoSubstations node-breaker RAW (v35) and inspect substations.
 * <p>
 * case0 — basecase {@code topoAnalysis()} + intFlag checks (many alternate disconnectors open).<br>
 * case1 — open S1 bus-coupler (nodes 1↔2; switch names are blank) then {@code topoProcessing()}.
 */
public class PSSE_TwoSubstations_NB_TopoAnalysis_Sample {

	/** Relative to {@code ipss.plugin.core} (launch.json cwd). */
	private static final String CASE =
			"testData/psse/nbreaker/twoSubstations_rev35.raw";

	public static void main(String[] args) throws InterpssException {
		case0();
		case1();
	}

	private static void case0() throws InterpssException {
		System.out.println("Case 0: basecase condition");

		AclfNetwork net = new PSSEDirectParser().parse(CASE);

		net.getSubstationMap().forEach((subId, sub) -> {
			SubstationNBreakerHelper subHelper = new SubstationNBreakerHelper(sub);
			int groups = subHelper.topoAnalysis();
			System.out.println("Substation " + subId + " (" + sub.getName() + ") groups=" + groups
					+ " nodes=" + sub.getNbNodeList().size()
					+ " switches=" + sub.getNbSwitchList().size());
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

		// Offline gens in RAW (STAT=0)
		printGenStatus(net.getBus("Bus1"));
		printGenStatus(net.getBus("Bus3"));

		// 3W star bus inherits intFlag from from-bus (Bus2) topo group.
		AclfBus starBus = net.getBus("3WNDTR_2_4_5_2");
		AclfBus bus2 = net.getBus("Bus2");
		if (starBus != null && bus2 != null) {
			System.out.println("Star bus " + starBus.getId() + " intFlag=" + starBus.getIntFlag()
					+ " (Bus2 intFlag=" + bus2.getIntFlag() + ")");
		}
	}

	private static void case1() throws InterpssException {
		System.out.println("Case 1: open S1 bus-coupler (nodes 1↔2), then topoProcessing");

		AclfNetwork net = new PSSEDirectParser().parse(CASE);

		Substation s1 = net.getSubstation("1");
		SubstationNBreakerHelper helper = new SubstationNBreakerHelper(s1);

		int groupsBefore = helper.topoAnalysis();
		System.out.println("S1 groups before open: " + groupsBefore);

		NBSwitch coupler = findSwitchByNodeNumbers(s1, 1, 2);
		if (coupler == null) {
			System.out.println("Bus-coupler switch 1↔2 not found on S1");
			return;
		}
		// 0 open, 1 closed, 2 stuck closed (default 1)
		coupler.setCurrentStatus(0);
		System.out.println("Opened coupler " + coupler.getId()
				+ " (" + coupler.getFromNBNode().getName().trim()
				+ " ↔ " + coupler.getToNBNode().getName().trim()
				+ ") status=" + coupler.getCurrentStatus());

		int groupsAfter = helper.topoAnalysis();
		System.out.println("S1 groups after open: " + groupsAfter);
		helper.printTopoFlags();

		// Optionally activate offline gens to show connectivity vs. status
		AclfBus bus1 = net.getBus("Bus1");
		if (bus1 != null && !bus1.getContributeGenList().isEmpty()) {
			bus1.getContributeGenList().get(0).setStatus(true);
			System.out.println("Activated Bus1 gen; bus active=" + bus1.isActive());
		}

		new AclfNetTopoHelper(net).topoProcessing();

		net.getBusList().forEach(bus -> {
			if ("1".equals(bus.getSubstationId()) || "2".equals(bus.getSubstationId())) {
				System.out.println(bus.getId() + " @" + bus.getSubstationId()
						+ " active=" + bus.isActive() + " intFlag=" + bus.getIntFlag());
			}
		});
	}

	/** Find a switch by PSS/E node numbers (names are blank in this RAW). */
	static NBSwitch findSwitchByNodeNumbers(Substation sub, long ni, long nj) {
		return sub.getNbSwitchList().stream()
				.filter(sw -> {
					long a = sw.getFromNBNode().getNumber();
					long b = sw.getToNBNode().getNumber();
					return (a == ni && b == nj) || (a == nj && b == ni);
				})
				.findFirst()
				.orElse(null);
	}

	private static void printGenStatus(AclfBus bus) {
		if (bus == null || bus.getContributeGenList().isEmpty()) {
			return;
		}
		bus.getContributeGenList().forEach(gen ->
				System.out.println(bus.getId() + " gen " + gen.getId()
						+ " status=" + gen.isActive()));
	}
}
