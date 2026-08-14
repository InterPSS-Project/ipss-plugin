package investigation.powsybl;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.datatype.Mismatch;

/**
 * Survey outlier: {@code ieee/IEEE_14_buses_zip_load.raw} → as-read {@code |maxMis|≈0.105} pu
 * (maxP=Bus2, maxQ=Bus5).
 * <p>
 * Track: ZIP / voltage-dependent load modeling vs constant PQ assumption in mismatch.
 */
public class Ieee14ZipLoadMismatchInvestigation {

	public static void main(String[] args) throws InterpssException {
		AclfNetwork baseline = PowSyBlMismatchInvSupport.parseRaw("ieee/IEEE_14_bus.raw", 33);
		PowSyBlMismatchInvSupport.printAsReadMismatch(baseline, "Baseline IEEE_14_bus.raw");

		AclfNetwork net = PowSyBlMismatchInvSupport.parseRaw("ieee/IEEE_14_buses_zip_load.raw", 33);
		Mismatch m = PowSyBlMismatchInvSupport.printAsReadMismatch(net, "IEEE_14_buses_zip_load.raw");
		PowSyBlMismatchInvSupport.printTopMismatchBuses(net, 8);

		String focus = m.maxPBus != null ? m.maxPBus.getId() : "Bus2";
		PowSyBlMismatchInvSupport.debugBus(net, focus);
		PowSyBlMismatchInvSupport.debugBus(net, m.maxQBus != null ? m.maxQBus.getId() : "Bus5");

		System.out.println("\nLoad summary (contribute loads / ZIP flags if present):");
		for (AclfBus bus : net.getBusList()) {
			if (bus.getContributeLoadList() == null || bus.getContributeLoadList().isEmpty()) {
				continue;
			}
			System.out.println("  " + bus.getId() + " loads=" + bus.getContributeLoadList().size()
					+ " loadP=" + bus.getLoadP() + " loadQ=" + bus.getLoadQ());
		}

		/*
		 * Status (2026-08-14 survey): |maxMis|≈0.105182 at Bus2 / Bus5.
		 *
		 * Open questions:
		 * 1) Are ZIP coefficients imported into InterPSS load objects?
		 * 2) Does as-read mismatch evaluate ZIP at scheduled VM, or as constant PQ?
		 * 3) Is ~0.1 pu expected for this PowSyBl fixture (partial ZIP), or an import gap?
		 */
	}
}
