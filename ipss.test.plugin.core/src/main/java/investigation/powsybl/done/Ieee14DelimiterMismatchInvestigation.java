package investigation.powsybl.done;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.datatype.Mismatch;

import investigation.powsybl.PowSyBlMismatchInvSupport;

/**
 * Survey outlier: {@code ieee/IEEE_14_bus_delimiter.raw} → as-read {@code |maxMis|=Infinity} at Bus5.
 * <p>
 * Track: whether space/odd delimiter parsing produces zero-X / NaN Y-bus entries.
 */
public class Ieee14DelimiterMismatchInvestigation {

	public static void main(String[] args) throws InterpssException {
		// Compare against a clean IEEE14 baseline
		AclfNetwork baseline = PowSyBlMismatchInvSupport.parseRaw("ieee/IEEE_14_bus.raw");
		PowSyBlMismatchInvSupport.printAsReadMismatch(baseline, "Baseline IEEE_14_bus.raw");

		AclfNetwork net = PowSyBlMismatchInvSupport.parseRaw("ieee/IEEE_14_bus_delimiter.raw");
		Mismatch m = PowSyBlMismatchInvSupport.printAsReadMismatch(net, "IEEE_14_bus_delimiter.raw");
		PowSyBlMismatchInvSupport.printTopMismatchBuses(net, 8);

		String focus = m.maxPBus != null ? m.maxPBus.getId() : "Bus5";
		PowSyBlMismatchInvSupport.debugBus(net, focus);

		/*
		 * Status (2026-08-14 survey): |maxMis|=Infinity, maxP=Bus5, maxQ=Bus5.
		 *
		 * Open questions:
		 * 1) Does PSSEDirectParser mis-tokenize branch R/X because of non-standard delimiters?
		 * 2) Is there a zero-impedance or missing branch reactance feeding Inf into Y-bus?
		 * 3) Is the fixture intentionally malformed for PowSyBl delimiter tests (not a solved case)?
		 * 
		 * Conclusion:
		 *     - InterPSS do not support non-standard delimiters.
		 */
	}
}
