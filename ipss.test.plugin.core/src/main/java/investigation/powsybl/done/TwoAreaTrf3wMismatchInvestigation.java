package investigation.powsybl.done;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.datatype.Mismatch;

import investigation.powsybl.PowSyBlMismatchInvSupport;

/**
 * Survey outlier: {@code ieee/two_area_case_trf3w.raw} → as-read {@code |maxMis|≈1.34} pu
 * (maxP=Bus9, maxQ=3W star {@code 3WNDTR_4_9_8_1}).
 * <p>
 * Track: 3W transformer / star-bus as-read residual vs 2W {@code two_area_case.raw} (small mismatch).
 */
public class TwoAreaTrf3wMismatchInvestigation {

	public static void main(String[] args) throws InterpssException {
		AclfNetwork twoW = PowSyBlMismatchInvSupport.parseRaw("ieee/two_area_case.raw");
		PowSyBlMismatchInvSupport.printAsReadMismatch(twoW, "two_area_case.raw (2W baseline)");

		AclfNetwork net = PowSyBlMismatchInvSupport.parseRaw("ieee/two_area_case_trf3w.raw");
		Mismatch m = PowSyBlMismatchInvSupport.printAsReadMismatch(net, "two_area_case_trf3w.raw");
		System.out.println("Special (3W) branches=" + net.getSpecialBranchList().size());
		PowSyBlMismatchInvSupport.printTopMismatchBuses(net, 10);

		PowSyBlMismatchInvSupport.debugBus(net, m.maxPBus != null ? m.maxPBus.getId() : "Bus9");
		String starId = m.maxQBus != null ? m.maxQBus.getId() : "3WNDTR_4_9_8_1";
		PowSyBlMismatchInvSupport.debugBus(net, starId);

		/*
		 * Status (2026-08-14 survey): |maxMis|≈1.33665; maxP=Bus9; maxQ=3WNDTR_4_9_8_1.
		 * two_area_case.raw (no 3W) stays small (~0.0049).
		 *
		 * Open questions:
		 * 1) Is the 3W star bus voltage/angle missing or defaulted (not solved-as-read)?
		 * 2) Are winding off-nominal taps / magnetizing branches mapped correctly?
		 * 3) Does PowSyBl treat this fixture as an equipment test rather than a solved LF case?
		 * 
		 * Conclusion:
		 *     - 3W transformer star bus voltage/angle is defaulted (not solved-as-read).
		 */
	}
}
