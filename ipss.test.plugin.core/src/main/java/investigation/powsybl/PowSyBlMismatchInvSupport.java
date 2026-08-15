package investigation.powsybl;

import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.hvdc.HvdcLine2T;
import com.interpss.core.aclf.hvdc.HvdcLineMT;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.datatype.Mismatch;
import com.interpss.core.funcImpl.AclfNetInfoHelper;
import com.interpss.core.net.Branch;
import com.interpss.common.exp.InterpssException;
import org.interpss.fadapter.psse.PSSEDirectParser;
import org.interpss.fadapter.psse.PSSEJsonDirectParser;

/**
 * Shared helpers for PowSyBl as-read mismatch investigations.
 * Run from repo root with working directory {@code ipss.test.plugin.core/} or set {@link #ROOT}.
 */
public final class PowSyBlMismatchInvSupport {
	/** Prefix when launched from the Maven multi-module root. */
	public static String ROOT = "ipss.test.plugin.core/";

	private PowSyBlMismatchInvSupport() {
	}

	public static AclfNetwork parseRaw(String relativeUnderPowsybl) throws InterpssException {
		return new PSSEDirectParser().parse(ROOT + "testData/psse/powsybl/" + relativeUnderPowsybl);
	}

	public static AclfNetwork parseRawx(String relativeUnderPowsybl) throws InterpssException {
		return new PSSEJsonDirectParser().parse(ROOT + "testData/psse/powsybl/" + relativeUnderPowsybl);
	}

	public static void initHvdc(AclfNetwork net) {
		for (HvdcLineMT mt : net.getHvdcLineMTList()) {
			mt.initLoadflow();
		}
		for (Branch branch : net.getSpecialBranchList()) {
			if (branch instanceof HvdcLine2T<?> hvdc) {
				hvdc.initLoadflow();
			}
		}
	}

	public static Mismatch printAsReadMismatch(AclfNetwork net, String label) {
		initHvdc(net);
		Mismatch m = net.maxMismatch(AclfMethodType.NR);
		System.out.println("--- " + label + " ---");
		System.out.println("Buses/Branches: " + net.getNoBus() + " / " + net.getNoBranch());
		System.out.println("As-read maxMismatch(NR): " + m);
		System.out.println("|maxMis|=" + m.maxMis.abs()
				+ "  maxP=" + (m.maxPBus != null ? m.maxPBus.getId() : "-")
				+ "  maxQ=" + (m.maxQBus != null ? m.maxQBus.getId() : "-"));
		return m;
	}

	public static void printTopMismatchBuses(AclfNetwork net, int topN) {
		System.out.println("Top |" + topN + "| bus |mismatch(NR)|:");
		net.getBusList().stream()
				.map(b -> (AclfBus) b)
				.sorted((a, b) -> Double.compare(
						b.mismatch(AclfMethodType.NR).abs(),
						a.mismatch(AclfMethodType.NR).abs()))
				.limit(topN)
				.forEach(b -> System.out.println("  " + b.getId() + "  "
						+ b.mismatch(AclfMethodType.NR) + "  |abs|="
						+ b.mismatch(AclfMethodType.NR).abs()));
	}

	public static void debugBus(AclfNetwork net, String busId) {
		System.out.println("\n===== Bus debug: " + busId + " =====");
		AclfNetInfoHelper.outputBusAclfDebugInfo(net, busId, false);
	}
}
