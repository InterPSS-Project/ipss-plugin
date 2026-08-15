package investigation.powsybl.done;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfLoad;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.datatype.Mismatch;

import investigation.powsybl.PowSyBlMismatchInvSupport;

/**
 * Survey outlier: {@code ieee/IEEE_14_buses_zip_load.raw} → as-read {@code |maxMis|≈0.105} pu
 * at Bus2.
 * <p>
 * Closed (2026-08-14): ZIP is voltage-dependent and <b>is</b> used in
 * {@code AclfBusMismatchHelper} via {@code calLoadPQ()} → {@code load.getLoad(|V|)}.
 * Residual ≈ ZIP(V₂)−PL because the fixture keeps const-PQ IEEE14 voltages while Bus2
 * load has non-zero IP/YP (CI/CZ).
 */
public class Ieee14ZipLoadMismatchInvestigation {

	public static void main(String[] args) throws InterpssException {
		AclfNetwork baseline = PowSyBlMismatchInvSupport.parseRaw("ieee/IEEE_14_bus.raw");
		PowSyBlMismatchInvSupport.printAsReadMismatch(baseline, "Baseline IEEE_14_bus.raw");

		AclfNetwork net = PowSyBlMismatchInvSupport.parseRaw("ieee/IEEE_14_buses_zip_load.raw");
		Mismatch m = PowSyBlMismatchInvSupport.printAsReadMismatch(net, "IEEE_14_buses_zip_load.raw");
		PowSyBlMismatchInvSupport.printTopMismatchBuses(net, 8);

		AclfBus bus2 = net.getBus("Bus2");
		PowSyBlMismatchInvSupport.debugBus(net, "Bus2");

		AclfLoad load = bus2.getContributeLoadList().get(0);
		double v = bus2.getVoltageMag();
		double zipP = load.getLoad(v).getReal();
		double constP = load.getLoadCP().getReal();
		System.out.println("\nBus2 ZIP vs const-P at |V|=" + v + ":");
		System.out.println("  CP=" + load.getLoadCP() + " CI=" + load.getLoadCI() + " CZ=" + load.getLoadCZ());
		System.out.println("  ZIP(P)=" + zipP + "  CP.P=" + constP + "  ZIP-CP=" + (zipP - constP));
		System.out.println("  |maxMis|=" + m.maxMis.abs() + " (matches ZIP-CP when snapshot is const-PQ solved)");
	}
}
