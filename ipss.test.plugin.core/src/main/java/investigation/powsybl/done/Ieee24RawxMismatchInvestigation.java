package investigation.powsybl.done;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.datatype.Mismatch;

import investigation.powsybl.PowSyBlMismatchInvSupport;

/**
 * Survey outlier: {@code rawx/IEEE_24_bus_rev35.rawx} had as-read {@code |maxMis|≈0.825} pu
 * while RAW twins stayed ~0.011 — caused by missing {@code swshunt} import in
 * {@code PSSEJsonDirectParser} (fixed 2026-08-14).
 */
public class Ieee24RawxMismatchInvestigation {

	public static void main(String[] args) throws InterpssException {
		AclfNetwork raw = PowSyBlMismatchInvSupport.parseRaw("ieee/IEEE_24_bus_rev35.raw");
		Mismatch mRaw = PowSyBlMismatchInvSupport.printAsReadMismatch(raw, "IEEE_24_bus_rev35.raw");
		PowSyBlMismatchInvSupport.printTopMismatchBuses(raw, 5);

		AclfNetwork rawx = PowSyBlMismatchInvSupport.parseRawx("rawx/IEEE_24_bus_rev35.rawx");
		Mismatch mRawx = PowSyBlMismatchInvSupport.printAsReadMismatch(rawx, "IEEE_24_bus_rev35.rawx");
		PowSyBlMismatchInvSupport.printTopMismatchBuses(rawx, 8);

		System.out.println("\nSwitched-shunt footprint:");
		System.out.println("  RAW  buses with switched shunt: " + countSwitchedShuntBuses(raw));
		System.out.println("  RAWX buses with switched shunt: " + countSwitchedShuntBuses(rawx));

		String focus = mRawx.maxPBus != null ? mRawx.maxPBus.getId() : "Bus19";
		PowSyBlMismatchInvSupport.debugBus(raw, focus);
		PowSyBlMismatchInvSupport.debugBus(rawx, focus);

		System.out.println("\nDelta |maxMis| RAWX-RAW = " + (mRawx.maxMis.abs() - mRaw.maxMis.abs()));

		/*
		 * Closed (2026-08-14):
		 * - Missing RAWX swshunt import explained ~0.8 pu gap (Bus1/Bus6 Binit 1.2/1.0 pu).
		 * - After PSSEJsonDirectParser swshunt mapping, RAWX residual matches RAW (~0.011).
		 */
	}

	private static int countSwitchedShuntBuses(AclfNetwork net) {
		int n = 0;
		for (var bus : net.getBusList()) {
			if (bus.getSwitchedShuntList() != null && !bus.getSwitchedShuntList().isEmpty()) {
				n++;
			}
		}
		return n;
	}
}
