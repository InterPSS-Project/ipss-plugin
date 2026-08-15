package org.interpss.core.adapter.psse.raw.aclf.powsybl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * As-read NR bus mismatch checks for PowSyBl PSS/E fixtures.
 * <p>
 * Hypothesis: files that represent a solved load-flow case should have a small
 * {@code |maxMismatch(NR)|} after import, without running NR.
 * <p>
 * Closed investigation notes:
 * {@code investigation.powsybl.inv_note.md} / {@code investigation.powsybl.done.*}.
 */
public class PSSE_PowSyBl_InitMismatch_Test extends CorePluginTestSetup {

	private static final String ROOT = "testData/psse/powsybl/";
	/** Allowlist bound (pu) for clean solved-as-read IEEE-style cases. */
	private static final double ALLOWLIST_LIMIT = 1e-2;
	/** IEEE24 sits just above 1e-2 (~0.011); still treated as near-solved. */
	private static final double IEEE24_LIMIT = 2e-2;

	private record CaseResult(String file, int nBus, double absMis, String maxPBus, String maxQBus) {
		String logLine(String tier) {
			return String.format("%-55s nBus=%-4d |maxMis|=%.6g  maxP=%s  maxQ=%s  [%s]",
					file, nBus, absMis, maxPBus, maxQBus, tier);
		}
	}

	private record Fixture(String relativePath, boolean rawx) {
	}

	@Test
	public void allowlistIeeeSolvedAsRead() throws Exception {
		List<Fixture> allowlist = List.of(
				new Fixture("ieee/IEEE_14_bus.raw", false),
				new Fixture("ieee/IEEE_14_bus_rev35.raw", false),
				new Fixture("ieee/IEEE_14_isolated_buses.raw", false),
				new Fixture("ieee/IEEE_57_bus.raw", false),
				new Fixture("ieee/IEEE_118_bus.raw", false),
				new Fixture("ieee/two_area_case.raw", false),
				new Fixture("parser/IEEE_14_bus_Q_record_rev35.raw", false),
				new Fixture("parser/IEEE_14_bus_non_induction_machine_data.raw", false),
				new Fixture("parser/IEEE_14_bus_non_induction_machine_data_rev35.raw", false),
				new Fixture("parser/IEEE_14_isolated_buses_exported.raw", false),
				new Fixture("parser/IEEE_14_bus_whitespaceAsDelimiter_exported.raw", false),
				new Fixture("rawx/IEEE_14_bus_rev35.rawx", true),
				new Fixture("nbreaker/IEEE_14_bus_nodeBreaker_rev35.raw", false),
				new Fixture("illinois/IEEE_14_bus.raw", false),
				new Fixture("illinois/IEEE_57_bus.RAW", false),
				new Fixture("illinois/IEEE_118_bus.RAW", false));

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
		assertEquals(allowlist.size(), results.size());
	}

	@Test
	public void ieee24RawAndRawxNearSolved() throws Exception {
		CaseResult raw = measure(new Fixture("ieee/IEEE_24_bus_rev35.raw", false));
		CaseResult rawx = measure(new Fixture("rawx/IEEE_24_bus_rev35.rawx", true));
		System.out.println(raw.logLine("ieee24"));
		System.out.println(rawx.logLine("ieee24"));

		assertTrue(raw.absMis() < IEEE24_LIMIT,
				() -> "IEEE24 RAW |maxMis|=" + raw.absMis());
		assertTrue(rawx.absMis() < IEEE24_LIMIT,
				() -> "IEEE24 RAWX |maxMis|=" + rawx.absMis());
		// POW-5: swshunt import made RAWX match RAW
		assertEquals(raw.absMis(), rawx.absMis(), 1e-9,
				"IEEE24 RAWX should match RAW after swshunt import");
	}

	/**
	 * Closed POW outliers: assert expected residual bands (not allowlist).
	 * See {@code investigation.powsybl.done.*}.
	 */
	@Test
	public void knownOutliersDocumentedResiduals() throws Exception {
		// POW-1: non-standard delimiter → Inf / non-finite
		CaseResult delim = measure(new Fixture("ieee/IEEE_14_bus_delimiter.raw", false));
		System.out.println(delim.logLine("POW-1"));
		assertFalse(Double.isFinite(delim.absMis()),
				() -> "delimiter fixture expected non-finite |maxMis|, got " + delim.absMis());

		// POW-2: 2T WATL extend-load ~3 pu (MTDC fixture-invalid, does not inject).
		// RAWX twin matches after twotermdc import (was allowlist when HVDC tables were skipped).
		CaseResult completed = measure(new Fixture("parser/IEEE_14_bus_completed.raw", false));
		CaseResult completed35 = measure(new Fixture("parser/IEEE_14_bus_completed_rev35.raw", false));
		CaseResult completedRawx = measure(new Fixture("rawx/IEEE_14_bus_completed_rev35.rawx", true));
		System.out.println(completed.logLine("POW-2"));
		System.out.println(completed35.logLine("POW-2"));
		System.out.println(completedRawx.logLine("POW-2"));
		assertTrue(completed.absMis() > 2.5 && completed.absMis() < 3.5,
				() -> "completed RAW expected ~3 pu, got " + completed.absMis());
		assertEquals(completed.absMis(), completed35.absMis(), 1e-6);
		assertTrue(completedRawx.absMis() > 2.5 && completedRawx.absMis() < 3.5,
				() -> "completed RAWX expected ~3 pu, got " + completedRawx.absMis());
		assertEquals("Bus12", completedRawx.maxPBus());
		assertEquals("Bus14", completedRawx.maxQBus());

		// POW-3: ZIP(V)−PL at Bus2 ≈ 0.105 (V-dependent ZIP in mismatch)
		/*
		The ~0.105 pu residual is expected: the fixture keeps const-PQ IEEE14 voltages while Bus2 is ZIP, 
		and mismatch correctly uses ZIP(|V|) via calLoadPQ(). At |V|=1.045, ZIP−PL ≈ 0.105, 
		which matches |maxMis|.
		*/
		CaseResult zip = measure(new Fixture("ieee/IEEE_14_buses_zip_load.raw", false));
		System.out.println(zip.logLine("POW-3"));
		assertTrue(zip.absMis() > 0.09 && zip.absMis() < 0.12,
				() -> "zip-load expected ~0.105 pu, got " + zip.absMis());
		assertEquals("Bus2", zip.maxPBus());

		// POW-4: 3W star as-read residual ~1.34 (star V/θ not solved-as-read)
		CaseResult trf3w = measure(new Fixture("ieee/two_area_case_trf3w.raw", false));
		System.out.println(trf3w.logLine("POW-4"));
		assertTrue(trf3w.absMis() > 1.0 && trf3w.absMis() < 1.5,
				() -> "two_area trf3w expected ~1.34 pu, got " + trf3w.absMis());
	}

	private static CaseResult measure(Fixture f) throws Exception {
		AclfNetwork net;
		if (f.rawx()) {
			net = new PSSEJsonDirectParser().parse(ROOT + f.relativePath());
		} else {
			net = new PSSEDirectParser().parse(ROOT + f.relativePath());
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
