package org.interpss.core.adapter.psse.raw.aclf.powsybl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.psse.PSSEDirectParser;
import org.interpss.fadapter.psse.PSSEJsonDirectParser;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.hvdc.HvdcLine2T;
import com.interpss.core.aclf.hvdc.HvdcLineMT;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.datatype.Mismatch;
import com.interpss.core.net.Branch;

/**
 * As-read NR bus mismatch survey for PowSyBl PSS/E fixtures.
 * <p>
 * Hypothesis: files that represent a solved load-flow case should have a small
 * {@code |maxMismatch(NR)|} after import, without running NR.
 */
public class PSSE_PowSyBl_InitMismatch_Test extends CorePluginTestSetup {

	private static final String ROOT = "testData/psse/powsybl/";
	/** Allowlist bound (pu); raise to 1e-1 only if swing/HVDC init quirks require it. */
	private static final double ALLOWLIST_LIMIT = 1e-2;

	private record CaseResult(String file, int nBus, double absMis, String maxPBus, String maxQBus) {
		String row(String tier) {
			return String.format("| `%s` | %.6g | %s | %s | %s |", file, absMis, maxPBus, maxQBus, tier);
		}

		String logLine(String tier) {
			return String.format("%-55s nBus=%-4d |maxMis|=%.6g  maxP=%s  maxQ=%s  [%s]",
					file, nBus, absMis, maxPBus, maxQBus, tier);
		}
	}

	private record Fixture(String relativePath, int[] versions, boolean rawx) {
		Fixture(String relativePath, int version, boolean rawx) {
			this(relativePath, new int[] { version }, rawx);
		}
	}

	@Test
	public void allowlistIeeeSolvedAsRead() throws Exception {
		List<Fixture> allowlist = List.of(
				new Fixture("ieee/IEEE_14_bus.raw", 33, false),
				new Fixture("ieee/IEEE_14_bus_rev35.raw", 35, false),
				new Fixture("ieee/IEEE_57_bus.raw", 33, false),
				new Fixture("ieee/IEEE_118_bus.raw", 33, false),
				new Fixture("rawx/IEEE_14_bus_rev35.rawx", 35, true));

		List<CaseResult> results = new ArrayList<>();
		for (Fixture f : allowlist) {
			CaseResult r = measure(f);
			results.add(r);
			System.out.println(r.logLine("allowlist"));
			assertTrue(r.absMis() < ALLOWLIST_LIMIT,
					() -> f.relativePath + " as-read |maxMis|=" + r.absMis()
							+ " (limit " + ALLOWLIST_LIMIT + ") maxP=" + r.maxPBus()
							+ " maxQ=" + r.maxQBus());
		}
		assertTrue(results.size() == allowlist.size());
	}

	@Test
	public void surveyCandidateFixtures() throws Exception {
		List<Fixture> survey = List.of(
				new Fixture("ieee/IEEE_24_bus.raw", 33, false),
				new Fixture("ieee/IEEE_24_bus_rev35.raw", 35, false),
				new Fixture("ieee/IEEE_14_bus_delimiter.raw", 33, false),
				new Fixture("ieee/IEEE_14_buses_zip_load.raw", 33, false),
				new Fixture("ieee/IEEE_14_isolated_buses.raw", 33, false),
				new Fixture("ieee/two_area_case.raw", 33, false),
				new Fixture("ieee/two_area_case_trf3w.raw", 33, false),
				new Fixture("parser/IEEE_14_bus_completed.raw", 33, false),
				new Fixture("parser/IEEE_14_bus_completed_rev35.raw", 35, false),
				new Fixture("parser/IEEE_14_bus_Q_record_rev35.raw", 35, false),
				new Fixture("parser/IEEE_14_bus_non_induction_machine_data.raw", 33, false),
				new Fixture("parser/IEEE_14_bus_non_induction_machine_data_rev35.raw", 35, false),
				new Fixture("parser/IEEE_14_isolated_buses_exported.raw", 33, false),
				new Fixture("parser/IEEE_14_bus_whitespaceAsDelimiter_exported.raw", 33, false),
				new Fixture("rawx/IEEE_24_bus_rev35.rawx", 35, true),
				new Fixture("rawx/IEEE_14_bus_completed_rev35.rawx", 35, true),
				new Fixture("nbreaker/IEEE_14_bus_nodeBreaker_rev35.raw", 35, false),
				new Fixture("illinois/IEEE_14_bus.raw", new int[] { 33, 30 }, false),
				new Fixture("illinois/IEEE_57_bus.RAW", new int[] { 33, 30 }, false),
				new Fixture("illinois/IEEE_118_bus.RAW", new int[] { 33, 30 }, false));

		System.out.println("--- PowSyBl as-read NR mismatch survey ---");
		for (Fixture f : survey) {
			CaseResult r = measure(f);
			System.out.println(r.logLine("survey"));
			System.out.println(r.row("survey"));
		}
	}

	private static CaseResult measure(Fixture f) throws Exception {
		AclfNetwork net;
		if (f.rawx()) {
			net = new PSSEJsonDirectParser().parse(ROOT + f.relativePath());
		} else {
			Exception last = null;
			net = null;
			for (int v : f.versions()) {
				try {
					net = new PSSEDirectParser(v).parse(ROOT + f.relativePath());
					last = null;
					break;
				} catch (Exception e) {
					last = e;
				}
			}
			if (net == null) {
				throw last != null ? last : new IllegalStateException("no version tried for " + f.relativePath());
			}
		}
		initHvdc(net);
		Mismatch m = net.maxMismatch(AclfMethodType.NR);
		String maxP = m.maxPBus != null ? m.maxPBus.getId() : "-";
		String maxQ = m.maxQBus != null ? m.maxQBus.getId() : "-";
		return new CaseResult(f.relativePath(), net.getNoBus(), m.maxMis.abs(), maxP, maxQ);
	}

	private static void initHvdc(AclfNetwork net) {
		for (HvdcLineMT mt : net.getHvdcLineMTList()) {
			mt.initLoadflow();
		}
		for (Branch branch : net.getSpecialBranchList()) {
			if (branch instanceof HvdcLine2T<?> hvdc) {
				hvdc.initLoadflow();
			}
		}
	}
}
