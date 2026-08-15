package investigation.powsybl.done;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.hvdc.HvdcLine2T;
import com.interpss.core.aclf.hvdc.HvdcLineMT;
import com.interpss.core.datatype.Mismatch;
import com.interpss.core.net.Branch;

import investigation.powsybl.PowSyBlMismatchInvSupport;

/**
 * Survey outlier: {@code parser/IEEE_14_bus_completed*.raw} → as-read {@code |maxMis|≈3.0} pu
 * (maxP=Bus12, maxQ=Bus14).
 * <p>
 * Closed (2026-08-14): MTDC {@code Bus401 on multiple DC buses} is fixture-invalid
 * (IB=401 on DC buses 1–4; converters Bus20–23; Bus401 absent) — InterPSS reject is correct
 * and MTDC does not inject. Residual is driven by 2T {@code WATL P1} (Bus12↔Bus14).
 * RAWX twin now imports {@code twotermdc} and shows the same ~3 pu residual band.
 */
public class Ieee14CompletedMismatchInvestigation {

	public static void main(String[] args) throws InterpssException {
		AclfNetwork baseline = PowSyBlMismatchInvSupport.parseRaw("ieee/IEEE_14_bus.raw");
		PowSyBlMismatchInvSupport.printAsReadMismatch(baseline, "Baseline IEEE_14_bus.raw");

		AclfNetwork v33 = PowSyBlMismatchInvSupport.parseRaw("parser/IEEE_14_bus_completed.raw");
		Mismatch m33 = PowSyBlMismatchInvSupport.printAsReadMismatch(v33, "IEEE_14_bus_completed.raw");
		System.out.println("HVDC MT count=" + v33.getHvdcLineMTList().size()
				+ "  specialBranches=" + v33.getSpecialBranchList().size());
		for (HvdcLineMT mt : v33.getHvdcLineMTList()) {
			System.out.println("  MTDC " + mt.getId() + " init=" + mt.initLoadflow()
					+ " topo=" + mt.validateTopology());
		}
		for (Branch br : v33.getSpecialBranchList()) {
			if (br instanceof HvdcLine2T) {
				System.out.println("  2T " + br.getId() + " status=" + br.isStatus()
						+ " " + br.getFromBusId() + "->" + br.getToBusId());
			}
		}
		PowSyBlMismatchInvSupport.printTopMismatchBuses(v33, 10);
		PowSyBlMismatchInvSupport.debugBus(v33, m33.maxPBus != null ? m33.maxPBus.getId() : "Bus12");
		PowSyBlMismatchInvSupport.debugBus(v33, m33.maxQBus != null ? m33.maxQBus.getId() : "Bus14");

		AclfNetwork v35 = PowSyBlMismatchInvSupport.parseRaw("parser/IEEE_14_bus_completed_rev35.raw");
		PowSyBlMismatchInvSupport.printAsReadMismatch(v35, "IEEE_14_bus_completed_rev35.raw");

		AclfNetwork rawx = PowSyBlMismatchInvSupport.parseRawx("rawx/IEEE_14_bus_completed_rev35.rawx");
		PowSyBlMismatchInvSupport.printAsReadMismatch(rawx,
				"RAWX IEEE_14_bus_completed_rev35.rawx (twotermdc imported → POW-2 band)");
	}
}
