package org.interpss.core.adapter.ge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.epc.EpcDirectParser;
import org.interpss.fadapter.psse.PSSEDirectParser;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.interpss.plugin.pssl.plugin.IpssAdapter.PsseVersion;
import org.interpss.numeric.datatype.LimitType;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.util.QAUtil;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.Aclf3WBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfLoad;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.ShuntCompensator;
import com.interpss.core.aclf.adj.PVBusLimit;
import com.interpss.core.aclf.adj.RemoteQBus;
import com.interpss.core.aclf.adj.SwitchedShunt;
import com.interpss.core.aclf.adj.TapControl;
import com.interpss.core.aclf.adj.PSXfrPControl;
import com.interpss.core.aclf.adpter.AclfPVGenBusAdapter;
import com.interpss.core.aclf.adpter.AclfPSXformerAdapter;
import com.interpss.core.aclf.hvdc.HvdcLine2T;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.net.Branch;
import com.interpss.core.net.NameTag;

/**
 * Local Texas2k and TAMU ACTIVSg10k EPC model comparison against the RAW parser.
 */
public class Epc2k10kComparisonTest extends CorePluginTestSetup {
	private static final double FIELD_TOL = 5.0e-2;
	private static final double LIMIT_TOL = 1.0e-1;
	private static final double COMMON_BRANCH_MIN_COVERAGE = 0.80;
	private static final double IMPORTED_BUS_MISMATCH_TOL = 6.0e-2;
	private static final double SOURCE_PRECISION_RESIDUAL_TOL = 2.0e-2;

	private static final Path ACTIVS10K_DIR = Path.of(
			System.getProperty("ipss.epc.activs10k.dir",
					Path.of("C:", "Users", "carol", "OneDrive", "Documents", "qiuhua",
							"PSSE_testcases", "TamuTestCases", "ACTIVSg10k").toString()));
	private static final Path TEXAS2K_DIR = Path.of(
			System.getProperty("ipss.epc.texas2k.dir",
					Path.of("C:", "Users", "carol", "OneDrive", "Documents", "qiuhua",
							"PSSE_testcases", "Texas2k_series24_cases_with_dynamics",
							"Texas2k_series24_cases_with_dynamics").toString()));
	private static final List<String> TEXAS2K_CASES = List.of(
			"Texas2k_series24_case1_2016summerpeak/Texas2k_series24_case1_2016summerPeak",
			"Texas2k_series24_case2_2016lowload/Texas2k_series24_case2_2016lowload",
			"Texas2k_series24_case3_2024summerpeak/Texas2k_series24_case3_2024summerpeak",
			"Texas2k_series24_case4_2024lowload/Texas2k_series24_case4_2024lowload",
			"Texas2k_series24_case5_2024highrenewables/Texas2k_series24_case5_2024highrenewables",
			"Texas2k_series24_case6_2024lowloadwithgfm/Texas2k_series24_case6_2024lowloadwithgfm");

	@Test
	public void activs10k_epc_matchesRawBusCounts() throws Exception {
		Path epc = ACTIVS10K_DIR.resolve("ACTIVSg10k.EPC");
		Path raw = ACTIVS10K_DIR.resolve("ACTIVSg10k.RAW");
		assumeTrue(Files.isRegularFile(epc), () -> "Missing local EPC fixture: " + epc);
		assumeTrue(Files.isRegularFile(raw), () -> "Missing local RAW fixture: " + raw);

		AclfNetwork epcNet = new EpcDirectParser().parse(epc.toString());
		AclfNetwork rawNet = new PSSEDirectParser(33).parse(raw.toString());
		AclfBranch phaseShifter = rawNet.getBranch("Bus10784", "Bus10788", "1");
		assertNotNull(phaseShifter);
		PSXfrPControl phaseControl = phaseShifter.getPSXfrPControl();
		assertNotNull(phaseControl);
		assertEquals(3.0, phaseControl.getDesiredControlRange().getMax(), 1.0e-6);
		assertEquals(2.0, phaseControl.getDesiredControlRange().getMin(), 1.0e-6);

		assertEquals(rawNet.getNoBus(), epcNet.getNoBus(), "ACTIVSg10k EPC bus count should match RAW");
		assertCoverage("ACTIVSg10k", "branch", ids(rawNet.getBranchList()), ids(epcNet.getBranchList()),
				COMMON_BRANCH_MIN_COVERAGE);
	}

	@Test
	public void texas2k_series24_epc_cases_matchRawBusCounts() throws Exception {
		assumeTrue(Files.isDirectory(TEXAS2K_DIR), () -> "Missing local Texas2k fixture dir: " + TEXAS2K_DIR);

		for (String casePath : TEXAS2K_CASES) {
			Path epc = TEXAS2K_DIR.resolve(casePath + ".EPC");
			Path raw = TEXAS2K_DIR.resolve(casePath + ".RAW");
			assumeTrue(Files.isRegularFile(epc), () -> "Missing local EPC fixture: " + epc);
			assumeTrue(Files.isRegularFile(raw), () -> "Missing local RAW fixture: " + raw);

			AclfNetwork epcNet = new EpcDirectParser().parse(epc.toString());
			AclfNetwork rawNet = new PSSEDirectParser(psseVersionNumber(raw)).parse(raw.toString());

			assertSupportedParity(casePath, rawNet, epcNet, false);
		}
	}

	@Test
	public void epc2k10k_savedCasePowerflowMatchesRaw() throws Exception {
		Path activsEpc = ACTIVS10K_DIR.resolve("ACTIVSg10k.EPC");
		Path activsRaw = ACTIVS10K_DIR.resolve("ACTIVSg10k.RAW");
		assumeTrue(Files.isRegularFile(activsEpc), () -> "Missing local EPC fixture: " + activsEpc);
		assumeTrue(Files.isRegularFile(activsRaw), () -> "Missing local RAW fixture: " + activsRaw);
		assertSavedCaseSolvedLoadflowParity("ACTIVSg10k",
				new PSSEDirectParser(33).parse(activsRaw.toString()),
				new EpcDirectParser().parse(activsEpc.toString()));

		assumeTrue(Files.isDirectory(TEXAS2K_DIR), () -> "Missing local Texas2k fixture dir: " + TEXAS2K_DIR);
		for (String casePath : TEXAS2K_CASES) {
			Path epc = TEXAS2K_DIR.resolve(casePath + ".EPC");
			Path raw = TEXAS2K_DIR.resolve(casePath + ".RAW");
			assumeTrue(Files.isRegularFile(epc), () -> "Missing local EPC fixture: " + epc);
			assumeTrue(Files.isRegularFile(raw), () -> "Missing local RAW fixture: " + raw);
			assertSavedCaseSolvedLoadflowParity(casePath,
					new PSSEDirectParser(psseVersionNumber(raw)).parse(raw.toString()),
					new EpcDirectParser().parse(epc.toString()));
		}
	}

	@Test
	public void activs10k_importMismatchDiagnostic() throws Exception {
		assumeTrue(Boolean.getBoolean("ipss.epc.mismatch.diagnostic"),
				"Set -Dipss.epc.mismatch.diagnostic=true to print imported-state mismatch diagnostics");
		Path epc = ACTIVS10K_DIR.resolve("ACTIVSg10k.EPC");
		Path raw = ACTIVS10K_DIR.resolve("ACTIVSg10k.RAW");
		assumeTrue(Files.isRegularFile(epc), () -> "Missing local EPC fixture: " + epc);
		assumeTrue(Files.isRegularFile(raw), () -> "Missing local RAW fixture: " + raw);

		AclfNetwork epcNet = new EpcDirectParser().parse(epc.toString());
		AclfNetwork rawNet = new PSSEDirectParser(33).parse(raw.toString());
		List<BusMismatchDiff> diffs = busMismatchDiffs(rawNet, epcNet);
		List<BusMismatchResidual> residuals = sourcePrecisionResiduals(rawNet, epcNet);
		System.out.println("Top ACTIVSg10k imported-state bus mismatch differences");
		diffs.stream().limit(25).forEach(diff -> System.out.println(diff));
		System.out.println("Top ACTIVSg10k mismatch residuals after RAW bus voltages are rounded to EPC precision");
		residuals.stream().limit(10).forEach(residual -> System.out.println(residual));
		printIncidentBranches("RAW", rawNet, "Bus77239");
		printIncidentBranches("EPC", epcNet, "Bus77239");
		printIncidentBranches("RAW", rawNet, "Bus50203");
		printIncidentBranches("EPC", epcNet, "Bus50203");
		printIncidentBranches("RAW", rawNet, "Bus40939");
		printIncidentBranches("EPC", epcNet, "Bus40939");
		printIncidentBranches("RAW", rawNet, "Bus40840");
		printIncidentBranches("EPC", epcNet, "Bus40840");
		printIncidentBranches("RAW", rawNet, "Bus13562");
		printIncidentBranches("EPC", epcNet, "Bus13562");
		printIncidentBranches("RAW", rawNet, "Bus77254");
		printIncidentBranches("EPC", epcNet, "Bus77254");
		printIncidentBranches("RAW", rawNet, "Bus77262");
		printIncidentBranches("EPC", epcNet, "Bus77262");
		printIncidentBranches("RAW", rawNet, "Bus10193");
		printIncidentBranches("EPC", epcNet, "Bus10193");
		printIncidentBranches("RAW", rawNet, "Bus26058");
		printIncidentBranches("EPC", epcNet, "Bus26058");
		printSolvedVoltageDiffs("ACTIVSg10k", rawNet, epcNet);
		printBusGeneratorControls("RAW", rawNet, "Bus13549");
		printBusGeneratorControls("EPC", epcNet, "Bus13549");
		printIncidentBranches("RAW", rawNet, "Bus13549");
		printIncidentBranches("EPC", epcNet, "Bus13549");

		Path texasCase1Epc = TEXAS2K_DIR.resolve(TEXAS2K_CASES.get(0) + ".EPC");
		Path texasCase1Raw = TEXAS2K_DIR.resolve(TEXAS2K_CASES.get(0) + ".RAW");
		if (Files.isRegularFile(texasCase1Epc) && Files.isRegularFile(texasCase1Raw)) {
			AclfNetwork texasEpcNet = new EpcDirectParser().parse(texasCase1Epc.toString());
			AclfNetwork texasRawNet = new PSSEDirectParser(psseVersionNumber(texasCase1Raw)).parse(texasCase1Raw.toString());
			System.out.println("Top Texas2k case1 imported-state bus mismatch differences");
			busMismatchDiffs(texasRawNet, texasEpcNet).stream().limit(10).forEach(diff -> System.out.println(diff));
			System.out.println("Top Texas2k case1 mismatch residuals after RAW bus voltages are rounded to EPC precision");
			sourcePrecisionResiduals(texasRawNet, texasEpcNet).stream().limit(10).forEach(residual -> System.out.println(residual));
			printSignatureDiffs("Texas2k case1 transformer branch",
					transformerBranchSignatures(texasRawNet), transformerBranchSignatures(texasEpcNet));
			printSolvedVoltageDiffs("Texas2k case1", texasRawNet, texasEpcNet);
			printBusGeneratorControls("RAW", texasRawNet, "Bus4195");
			printBusGeneratorControls("EPC", texasEpcNet, "Bus4195");
			printBusGeneratorControls("RAW", texasRawNet, "Bus2013");
			printBusGeneratorControls("EPC", texasEpcNet, "Bus2013");
			printBusGeneratorControls("RAW", texasRawNet, "Bus7406");
			printBusGeneratorControls("EPC", texasEpcNet, "Bus7406");
			printIncidentBranches("RAW", texasRawNet, "Bus4195");
			printIncidentBranches("EPC", texasEpcNet, "Bus4195");
			printBusGeneratorControls("RAW", texasRawNet, "Bus7400");
			printBusGeneratorControls("EPC", texasEpcNet, "Bus7400");
			printIncidentBranches("RAW", texasRawNet, "Bus7366");
			printIncidentBranches("EPC", texasEpcNet, "Bus7366");
			printIncidentBranches("RAW", texasRawNet, "Bus7400");
			printIncidentBranches("EPC", texasEpcNet, "Bus7400");
			printIncidentBranches("RAW", texasRawNet, "Bus2017");
			printIncidentBranches("EPC", texasEpcNet, "Bus2017");
		}

		Path texasCase3Epc = TEXAS2K_DIR.resolve(TEXAS2K_CASES.get(2) + ".EPC");
		Path texasCase3Raw = TEXAS2K_DIR.resolve(TEXAS2K_CASES.get(2) + ".RAW");
		if (Files.isRegularFile(texasCase3Epc) && Files.isRegularFile(texasCase3Raw)) {
			AclfNetwork texasEpcNet = new EpcDirectParser().parse(texasCase3Epc.toString());
			AclfNetwork texasRawNet = new PSSEDirectParser(psseVersionNumber(texasCase3Raw)).parse(texasCase3Raw.toString());
			printSignatureDiffs("Texas2k case3 transformer branch",
					transformerBranchSignatures(texasRawNet), transformerBranchSignatures(texasEpcNet));
			printSolvedVoltageDiffs("Texas2k case3", texasRawNet, texasEpcNet);
			printBusGeneratorControls("RAW", texasRawNet, "Bus7061");
			printBusGeneratorControls("EPC", texasEpcNet, "Bus7061");
			printBusGeneratorControls("RAW", texasRawNet, "Bus7062");
			printBusGeneratorControls("EPC", texasEpcNet, "Bus7062");
			printIncidentBranches("RAW", texasRawNet, "Bus3062");
			printIncidentBranches("EPC", texasEpcNet, "Bus3062");
			printIncidentBranches("RAW", texasRawNet, "Bus7406");
			printIncidentBranches("EPC", texasEpcNet, "Bus7406");
			printIncidentBranches("RAW", texasRawNet, "Bus7058");
			printIncidentBranches("EPC", texasEpcNet, "Bus7058");
		}
	}

	@Test
	public void solvedLoadflowParityDiagnostic() throws Exception {
		assumeTrue(Boolean.getBoolean("ipss.epc.solved.diagnostic"),
				"Set -Dipss.epc.solved.diagnostic=true to print solved load-flow parity diagnostics");
		Path activsEpc = ACTIVS10K_DIR.resolve("ACTIVSg10k.EPC");
		Path activsRaw = ACTIVS10K_DIR.resolve("ACTIVSg10k.RAW");
		if (Files.isRegularFile(activsEpc) && Files.isRegularFile(activsRaw)) {
			printSolvedParitySummary("ACTIVSg10k", activsRaw, activsEpc, 33);
		}
		if (Files.isDirectory(TEXAS2K_DIR)) {
			for (String casePath : TEXAS2K_CASES) {
				Path epc = TEXAS2K_DIR.resolve(casePath + ".EPC");
				Path raw = TEXAS2K_DIR.resolve(casePath + ".RAW");
				if (Files.isRegularFile(epc) && Files.isRegularFile(raw)) {
					printSolvedParitySummary(casePath, raw, epc, psseVersionNumber(raw));
				}
			}
		}
	}

	@Test
	public void texas2kSolvedHotspotDiagnostic() throws Exception {
		assumeTrue(Boolean.getBoolean("ipss.epc.texas.hotspot.diagnostic"),
				"Set -Dipss.epc.texas.hotspot.diagnostic=true to print Texas2k solved hotspot diagnostics");
		assumeTrue(Files.isDirectory(TEXAS2K_DIR), () -> "Missing local Texas2k fixture dir: " + TEXAS2K_DIR);

		for (int caseIndex : List.of(0, 1, 2)) {
			String casePath = TEXAS2K_CASES.get(caseIndex);
			Path epc = TEXAS2K_DIR.resolve(casePath + ".EPC");
			Path raw = TEXAS2K_DIR.resolve(casePath + ".RAW");
			assumeTrue(Files.isRegularFile(epc), () -> "Missing local EPC fixture: " + epc);
			assumeTrue(Files.isRegularFile(raw), () -> "Missing local RAW fixture: " + raw);

			AclfNetwork rawNet = new PSSEDirectParser(psseVersionNumber(raw)).parse(raw.toString());
			AclfNetwork epcNet = new EpcDirectParser().parse(epc.toString());
			boolean rawSolved = runLoadflow(rawNet);
			boolean epcSolved = runLoadflow(epcNet);
			System.out.println("TEXAS_HOTSPOT label=" + casePath
					+ " rawSolved=" + rawSolved
					+ " epcSolved=" + epcSolved
					+ " maxBusVoltageDiff=" + QAUtil.getMaxBusVoltageDiffAngleAlignedByIsland(epcNet, rawNet)
					+ " maxBranchFlowDiffAbs=" + QAUtil.getMaxBranchFlowDiff(epcNet, rawNet, 1.0e-6).abs());
			for (String busId : List.of("Bus2123", "Bus4195", "Bus7366", "Bus7400", "Bus7406", "Bus2017")) {
				printSolvedBusPair(casePath, rawNet, epcNet, busId);
			}
			printBranchPair(casePath, rawNet, epcNet, "Bus7366", "Bus7400", "1");
		}
	}

	@Test
	public void activs10kSolvedHotspotDiagnostic() throws Exception {
		assumeTrue(Boolean.getBoolean("ipss.epc.activs.hotspot.diagnostic"),
				"Set -Dipss.epc.activs.hotspot.diagnostic=true to print ACTIVSg10k solved hotspot diagnostics");
		Path epc = ACTIVS10K_DIR.resolve("ACTIVSg10k.EPC");
		Path raw = ACTIVS10K_DIR.resolve("ACTIVSg10k.RAW");
		assumeTrue(Files.isRegularFile(epc), () -> "Missing local EPC fixture: " + epc);
		assumeTrue(Files.isRegularFile(raw), () -> "Missing local RAW fixture: " + raw);

		AclfNetwork rawNet = new PSSEDirectParser(33).parse(raw.toString());
		AclfNetwork epcNet = new EpcDirectParser().parse(epc.toString());
		boolean rawSolved = runLoadflow(rawNet);
		boolean epcSolved = runLoadflow(epcNet);
		System.out.println("ACTIVS_HOTSPOT rawSolved=" + rawSolved
				+ " epcSolved=" + epcSolved
				+ " maxBusVoltageDiff=" + QAUtil.getMaxBusVoltageDiffAngleAlignedByIsland(epcNet, rawNet)
				+ " maxBranchFlowDiffAbs=" + QAUtil.getMaxBranchFlowDiff(epcNet, rawNet, 1.0e-6).abs());
		for (String busId : List.of("Bus50382", "Bus20531", "Bus20530", "Bus50380", "Bus50381",
				"Bus50340", "Bus50458", "Bus50459", "Bus50460")) {
			printSolvedBusPair("ACTIVSg10k", rawNet, epcNet, busId);
		}
		printBranchPair("ACTIVSg10k", rawNet, epcNet, "Bus20531", "Bus20530", "1");
		printIncidentBranches("RAW", rawNet, "Bus50380");
		printIncidentBranches("EPC", epcNet, "Bus50380");
		printBranchPair("ACTIVSg10k", rawNet, epcNet, "Bus50380", "Bus50382", "1");
	}

	@Test
	public void generatorControlParityDiagnostic() throws Exception {
		assumeTrue(Boolean.getBoolean("ipss.epc.gen.control.diagnostic"),
				"Set -Dipss.epc.gen.control.diagnostic=true to print generator control parity diagnostics");
		Path activsEpc = ACTIVS10K_DIR.resolve("ACTIVSg10k.EPC");
		Path activsRaw = ACTIVS10K_DIR.resolve("ACTIVSg10k.RAW");
		if (Files.isRegularFile(activsEpc) && Files.isRegularFile(activsRaw)) {
			AclfNetwork epcNet = new EpcDirectParser().parse(activsEpc.toString());
			AclfNetwork rawNet = new PSSEDirectParser(33).parse(activsRaw.toString());
			printSignatureDiffs("ACTIVSg10k generator control",
					genControlSignatures(rawNet), genControlSignatures(epcNet));
		}
		if (Files.isDirectory(TEXAS2K_DIR)) {
			for (String casePath : TEXAS2K_CASES) {
				Path epc = TEXAS2K_DIR.resolve(casePath + ".EPC");
				Path raw = TEXAS2K_DIR.resolve(casePath + ".RAW");
				if (Files.isRegularFile(epc) && Files.isRegularFile(raw)) {
					AclfNetwork epcNet = new EpcDirectParser().parse(epc.toString());
					AclfNetwork rawNet = new PSSEDirectParser(psseVersionNumber(raw)).parse(raw.toString());
					printSignatureDiffs(casePath + " generator control",
							genControlSignatures(rawNet), genControlSignatures(epcNet));
				}
			}
		}
	}

	@Test
	public void modelSignatureParityDiagnostic() throws Exception {
		assumeTrue(Boolean.getBoolean("ipss.epc.model.signature.diagnostic"),
				"Set -Dipss.epc.model.signature.diagnostic=true to print model signature diagnostics");
		Path activsEpc = ACTIVS10K_DIR.resolve("ACTIVSg10k.EPC");
		Path activsRaw = ACTIVS10K_DIR.resolve("ACTIVSg10k.RAW");
		if (Files.isRegularFile(activsEpc) && Files.isRegularFile(activsRaw)) {
			AclfNetwork epcNet = new EpcDirectParser().parse(activsEpc.toString());
			AclfNetwork rawNet = new PSSEDirectParser(33).parse(activsRaw.toString());
			printModelSignatureDiffs("ACTIVSg10k", rawNet, epcNet);
		}
		if (Files.isDirectory(TEXAS2K_DIR)) {
			for (String casePath : TEXAS2K_CASES) {
				Path epc = TEXAS2K_DIR.resolve(casePath + ".EPC");
				Path raw = TEXAS2K_DIR.resolve(casePath + ".RAW");
				if (Files.isRegularFile(epc) && Files.isRegularFile(raw)) {
					AclfNetwork epcNet = new EpcDirectParser().parse(epc.toString());
					AclfNetwork rawNet = new PSSEDirectParser(psseVersionNumber(raw)).parse(raw.toString());
					printModelSignatureDiffs(casePath, rawNet, epcNet);
				}
			}
		}
	}

	private static void assertSupportedParity(String label, AclfNetwork rawNet,
			AclfNetwork epcNet, boolean compareSolvedResults) throws Exception {
		assertEquals(rawNet.getBaseKva(), epcNet.getBaseKva(), 1.0e-3, label + " base should match RAW");
		assertEquals(rawNet.getNoBus(), epcNet.getNoBus(), label + " EPC bus count should match RAW");
		assertEquals(rawNet.getNoActiveBus(), epcNet.getNoActiveBus(), label + " active bus count should match RAW");
		assertCoverage(label, "branch", ids(rawNet.getBranchList()), ids(epcNet.getBranchList()),
				COMMON_BRANCH_MIN_COVERAGE);

		Map<String, BusSignature> rawBuses = busSignatures(rawNet);
		Map<String, BusSignature> epcBuses = busSignatures(epcNet);
		assertCoverage(label, "physical bus", rawBuses.keySet(), epcBuses.keySet(), 1.0);
		assertCommonSignatures(label, "bus", rawBuses, epcBuses);
		assertInitialVoltageParity(label, rawNet, epcNet);

		assertCommonSignatures(label, "generator", genSignatures(rawNet), genSignatures(epcNet));
		assertCommonSignatures(label, "generator control", genControlSignatures(rawNet),
				genControlSignatures(epcNet));
		assertCommonSignatures(label, "bus generator control", busGenControlSignatures(rawNet),
				busGenControlSignatures(epcNet));
		assertCommonSignatures(label, "bus generator aggregate", genAggregateSignatures(rawNet),
				genAggregateSignatures(epcNet));
		assertLimitOrdering(label + " RAW", rawNet);
		assertLimitOrdering(label + " EPC", epcNet);

		assertCommonSignatures(label, "load", loadSignatures(rawNet), loadSignatures(epcNet));
		assertCommonSignatures(label, "bus load aggregate", loadAggregateSignatures(rawNet),
				loadAggregateSignatures(epcNet));
		assertImportedBusMismatchParity(label, rawNet, epcNet);

		assertCommonSignatures(label, "fixed shunt", fixedShuntSignatures(rawNet), fixedShuntSignatures(epcNet));
		assertCoverage(label, "fixed shunt", fixedShuntSignatures(rawNet).keySet(),
				fixedShuntSignatures(epcNet).keySet(), 0.80);

		assertCommonSignatures(label, "line branch", branchSignatures(rawNet), branchSignatures(epcNet));
		assertCommonSignatures(label, "transformer branch", transformerBranchSignatures(rawNet),
				transformerBranchSignatures(epcNet));
		Map<String, String> rawTransformerControls = transformerControlSignatures(rawNet);
		Map<String, String> epcTransformerControls = transformerControlSignatures(epcNet);
		assertCommonSignatures(label, "transformer control", rawTransformerControls, epcTransformerControls);
		assertUnsupportedGapVisible(label, "transformer control", rawTransformerControls.keySet(),
				epcTransformerControls.keySet());
		assertCommonSignatures(label, "area", areaSignatures(rawNet), areaSignatures(epcNet));
		assertCommonSignatures(label, "zone", zoneSignatures(rawNet), zoneSignatures(epcNet));

		Map<String, String> rawSwitchedShunts = switchedShuntSignatures(rawNet);
		Map<String, String> epcSwitchedShunts = switchedShuntSignatures(epcNet);
		assertCoverage(label, "switched shunt", rawSwitchedShunts.keySet(), epcSwitchedShunts.keySet(), 0.80);
		assertUnsupportedGapVisible(label, "switched shunt", rawSwitchedShunts.keySet(), epcSwitchedShunts.keySet());

		Map<String, String> rawHvdc = hvdcSignatures(rawNet);
		Map<String, String> epcHvdc = hvdcSignatures(epcNet);
		assertCommonSignatures(label, "HVDC", rawHvdc, epcHvdc);
		assertUnsupportedGapVisible(label, "HVDC", rawHvdc.keySet(), epcHvdc.keySet());

		if (compareSolvedResults) {
			assertSolvedLoadflowParity(label, rawNet, epcNet);
		}
	}

	private static void assertCommonSignatures(String label, String category,
			Map<String, ?> raw, Map<String, ?> epc) {
		Set<String> commonIds = new TreeSet<>(raw.keySet());
		commonIds.retainAll(epc.keySet());
		for (String id : commonIds) {
			assertEquals(raw.get(id), epc.get(id), label + " " + category + " differs for " + id);
		}
	}

	private static void assertCoverage(String label, String category,
			Set<String> rawIds, Set<String> epcIds, double minCoverage) {
		if (rawIds.isEmpty()) {
			assertTrue(epcIds.isEmpty(), label + " EPC has " + category + " records while RAW has none");
			return;
		}
		Set<String> commonIds = new TreeSet<>(rawIds);
		commonIds.retainAll(epcIds);
		double coverage = (double) commonIds.size() / rawIds.size();
		assertTrue(coverage >= minCoverage,
				() -> label + " EPC covers " + commonIds.size() + "/" + rawIds.size()
						+ " RAW " + category + " records; missing examples "
						+ examples(missing(rawIds, epcIds)));
	}

	private static void assertUnsupportedGapVisible(String label, String category,
			Set<String> rawIds, Set<String> epcIds) {
		Set<String> rawOnly = missing(rawIds, epcIds);
		if (!rawOnly.isEmpty()) {
			assertTrue(epcIds.size() <= rawIds.size(),
					label + " EPC should not invent " + category + " records while parser has RAW-only gaps");
		}
	}

	private static void assertSolvedLoadflowParity(String label, AclfNetwork rawNet,
			AclfNetwork epcNet) throws Exception {
		boolean rawSolved = runLoadflow(rawNet);
		boolean epcSolved = runLoadflow(epcNet);
		assertTrue(rawSolved, label + " RAW load-flow should converge");
		assertTrue(epcSolved, label + " EPC load-flow should converge");
		assertTrue(QAUtil.getMaxBusVoltageDiffAngleAlignedByIsland(epcNet, rawNet) < 1.0e-3,
				label + " solved bus voltages should match RAW");
		assertTrue(QAUtil.getMaxBranchFlowDiff(epcNet, rawNet, 1.0e-6).abs() < 1.0e-2,
				label + " solved branch flows should match RAW");
	}

	private static void assertSavedCaseSolvedLoadflowParity(String label, AclfNetwork rawNet,
			AclfNetwork epcNet) throws Exception {
		replaySolvedPvLimitState(rawNet, 1.0e-3);
		replaySolvedPvLimitState(epcNet, 1.0e-3);
		assertSolvedLoadflowParity(label, rawNet, epcNet);
	}

	private static boolean runLoadflow(AclfNetwork net) throws Exception {
		return runLoadflow(net, false);
	}

	private static boolean runLoadflow(AclfNetwork net, boolean applyAdjustments) throws Exception {
		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		algo.setLfMethod(AclfMethodType.NR);
		algo.setInitBusVoltage(false);
		algo.getLfAdjAlgo().setApplyAdjustAlgo(applyAdjustments);
		algo.setMaxIterations(30);
		return algo.loadflow();
	}

	private static void printSolvedParitySummary(String label, Path rawPath, Path epcPath, int psseVersion)
			throws Exception {
		AclfNetwork rawNet = new PSSEDirectParser(psseVersion).parse(rawPath.toString());
		AclfNetwork epcNet = new EpcDirectParser().parse(epcPath.toString());
		boolean rawSolved = runLoadflow(rawNet);
		boolean epcSolved = runLoadflow(epcNet);
		double maxBusVoltageDiff = rawSolved && epcSolved
				? QAUtil.getMaxBusVoltageDiffAngleAlignedByIsland(epcNet, rawNet)
				: Double.NaN;
		Complex maxBranchFlowDiff = rawSolved && epcSolved
				? QAUtil.getMaxBranchFlowDiff(epcNet, rawNet, 1.0e-6)
				: Complex.NaN;
		System.out.println("SOLVED_PARITY label=" + label
				+ " adjust=false"
				+ " rawSolved=" + rawSolved
				+ " epcSolved=" + epcSolved
				+ " maxBusVoltageDiff=" + maxBusVoltageDiff
				+ " maxBranchFlowDiffAbs=" + maxBranchFlowDiff.abs()
				+ " busParity=" + (maxBusVoltageDiff < 1.0e-3)
				+ " branchParity=" + (maxBranchFlowDiff.abs() < 1.0e-2));
		if (Boolean.getBoolean("ipss.epc.solved.adjust.diagnostic")) {
			AclfNetwork adjustedRawNet = new PSSEDirectParser(psseVersion).parse(rawPath.toString());
			AclfNetwork adjustedEpcNet = new EpcDirectParser().parse(epcPath.toString());
			boolean adjustedRawSolved = runLoadflow(adjustedRawNet, true);
			boolean adjustedEpcSolved = runLoadflow(adjustedEpcNet, true);
			double adjustedMaxBusVoltageDiff = adjustedRawSolved && adjustedEpcSolved
					? QAUtil.getMaxBusVoltageDiffAngleAlignedByIsland(adjustedEpcNet, adjustedRawNet)
					: Double.NaN;
			Complex adjustedMaxBranchFlowDiff = adjustedRawSolved && adjustedEpcSolved
					? QAUtil.getMaxBranchFlowDiff(adjustedEpcNet, adjustedRawNet, 1.0e-6)
					: Complex.NaN;
			System.out.println("SOLVED_PARITY label=" + label
					+ " adjust=true"
					+ " rawSolved=" + adjustedRawSolved
					+ " epcSolved=" + adjustedEpcSolved
					+ " maxBusVoltageDiff=" + adjustedMaxBusVoltageDiff
					+ " maxBranchFlowDiffAbs=" + adjustedMaxBranchFlowDiff.abs()
					+ " busParity=" + (adjustedMaxBusVoltageDiff < 1.0e-3)
					+ " branchParity=" + (adjustedMaxBranchFlowDiff.abs() < 1.0e-2));
		}
		if (Boolean.getBoolean("ipss.epc.solved.savedcase.diagnostic")) {
			AclfNetwork replayRawNet = new PSSEDirectParser(psseVersion).parse(rawPath.toString());
			AclfNetwork replayEpcNet = new EpcDirectParser().parse(epcPath.toString());
			int rawPvToPq = replaySolvedPvLimitState(replayRawNet, 1.0e-3);
			int epcPvToPq = replaySolvedPvLimitState(replayEpcNet, 1.0e-3);
			boolean replayRawSolved = runLoadflow(replayRawNet);
			boolean replayEpcSolved = runLoadflow(replayEpcNet);
			double replayMaxBusVoltageDiff = replayRawSolved && replayEpcSolved
					? QAUtil.getMaxBusVoltageDiffAngleAlignedByIsland(replayEpcNet, replayRawNet)
					: Double.NaN;
			Complex replayMaxBranchFlowDiff = replayRawSolved && replayEpcSolved
					? QAUtil.getMaxBranchFlowDiff(replayEpcNet, replayRawNet, 1.0e-6)
					: Complex.NaN;
			System.out.println("SOLVED_PARITY label=" + label
					+ " savedCasePvReplay=true"
					+ " rawPvToPq=" + rawPvToPq
					+ " epcPvToPq=" + epcPvToPq
					+ " rawSolved=" + replayRawSolved
					+ " epcSolved=" + replayEpcSolved
					+ " maxBusVoltageDiff=" + replayMaxBusVoltageDiff
					+ " maxBranchFlowDiffAbs=" + replayMaxBranchFlowDiff.abs()
					+ " busParity=" + (replayMaxBusVoltageDiff < 1.0e-3)
					+ " branchParity=" + (replayMaxBranchFlowDiff.abs() < 1.0e-2));
		}
	}

	private static int replaySolvedPvLimitState(AclfNetwork net, double threshold) {
		int changed = 0;
		for (AclfBus bus : net.getBusList()) {
			if (bus.isPVBusLimit()
					&& Math.abs(bus.getDesiredVoltMag() - bus.getVoltageMag()) > threshold) {
				AclfPVGenBusAdapter pvBus = bus.toPVBus();
				double qGen = pvBus.getGenResults().getImaginary();
				bus.getPVBusLimit().changeToGenPQBus(qGen);
				changed++;
			}
		}
		return changed;
	}

	private static void printSolvedVoltageDiffs(String label, AclfNetwork rawNet, AclfNetwork epcNet)
			throws Exception {
		boolean rawSolved = runLoadflow(rawNet);
		boolean epcSolved = runLoadflow(epcNet);
		System.out.println(label + " solved=" + rawSolved + "/" + epcSolved);
		if (!rawSolved || !epcSolved) {
			return;
		}
		double angleShift = referenceAngleShift(rawNet, epcNet);
		List<SolvedVoltageDiff> diffs = new ArrayList<>();
		for (AclfBus rawBus : rawNet.getBusList()) {
			AclfBus epcBus = epcNet.getBus(rawBus.getId());
			if (epcBus == null) {
				continue;
			}
			double rawVm = rawBus.getVoltageMag();
			double epcVm = epcBus.getVoltageMag();
			double rawVa = Math.toDegrees(rawBus.getVoltageAng());
			double epcVa = Math.toDegrees(epcBus.getVoltageAng()) - angleShift;
			diffs.add(new SolvedVoltageDiff(rawBus.getId(), rawBus.getName(),
					rawBus.getBaseVoltage() / 1000.0, rawVm, epcVm, rawVa, epcVa));
		}
		diffs.sort(Comparator.comparingDouble(SolvedVoltageDiff::abs).reversed());
		System.out.println("Top " + label + " solved voltage differences");
		diffs.stream().limit(20).forEach(diff -> System.out.println(diff));
	}

	private static double referenceAngleShift(AclfNetwork rawNet, AclfNetwork epcNet) {
		for (AclfBus rawBus : rawNet.getBusList()) {
			AclfBus epcBus = epcNet.getBus(rawBus.getId());
			if (epcBus != null && rawBus.isActive() && epcBus.isActive()) {
				return Math.toDegrees(epcBus.getVoltageAng() - rawBus.getVoltageAng());
			}
		}
		return 0.0;
	}

	private static void assertInitialVoltageParity(String label, AclfNetwork rawNet, AclfNetwork epcNet) {
		double maxVmDiff = 0.0;
		double maxVaDiff = 0.0;
		for (String busId : busSignatures(rawNet).keySet()) {
			AclfBus rawBus = rawNet.getBus(busId);
			AclfBus epcBus = epcNet.getBus(busId);
			if (epcBus == null) {
				continue;
			}
			maxVmDiff = Math.max(maxVmDiff, Math.abs(rawBus.getVoltageMag() - epcBus.getVoltageMag()));
			maxVaDiff = Math.max(maxVaDiff, Math.abs(rawBus.getVoltageAng() - epcBus.getVoltageAng()));
		}
		assertTrue(maxVmDiff < 5.0e-2,
				label + " initial bus voltage magnitudes should remain close to RAW, max diff=" + maxVmDiff);
		assertTrue(maxVaDiff < 5.0e-2,
				label + " initial bus voltage angles should remain close to RAW, max diff=" + maxVaDiff);
	}

	private static void assertLimitOrdering(String label, AclfNetwork net) {
		for (AclfBus bus : net.getBusList()) {
			assertOrdered(label + " voltage limit at " + bus.getId(), bus.getVLimit());
			for (AclfGen gen : bus.getContributeGenList()) {
				assertOrdered(label + " generator P limit at " + bus.getId() + "/" + gen.getId(),
						gen.getPGenLimit());
				assertOrdered(label + " generator Q limit at " + bus.getId() + "/" + gen.getId(),
						gen.getQGenLimit());
			}
			for (SwitchedShunt shunt : bus.getSwitchedShuntList()) {
				assertOrdered(label + " switched shunt B limit at " + bus.getId(), shunt.getBLimit());
				assertOrdered(label + " switched shunt control limit at " + bus.getId(),
						shunt.getDesiredControlRange());
			}
		}
		for (AclfBranch branch : net.getBranchList()) {
			assertTrue(branch.getRatingMva1() >= -1.0e-9,
					() -> label + " branch rating1 is negative at " + branch.getId());
			assertTrue(branch.getRatingMva2() >= -1.0e-9,
					() -> label + " branch rating2 is negative at " + branch.getId());
			assertTrue(branch.getRatingMva3() >= -1.0e-9,
					() -> label + " branch rating3 is negative at " + branch.getId());
			if (branch.isTapControl()) {
				assertOrdered(label + " tap control range at " + branch.getId(),
						branch.getTapControl().getDesiredControlRange());
				assertOrdered(label + " tap turn-ratio limit at " + branch.getId(),
						branch.getTapControl().getTurnRatioLimit());
			}
			if (branch.isPSXfrPControl()) {
				assertOrdered(label + " phase-shifter P range at " + branch.getId(),
						branch.getPSXfrPControl().getDesiredControlRange());
				assertOrdered(label + " phase-shifter angle limit at " + branch.getId(),
						branch.getPSXfrPControl().getAngLimit(UnitType.Deg));
			}
		}
	}

	private static void assertOrdered(String label, LimitType limit) {
		if (limit != null) {
			assertTrue(limit.getMax() + 1.0e-9 >= limit.getMin(),
					() -> label + " has max < min: " + limit.getMax() + " < " + limit.getMin());
		}
	}

	private static List<BusMismatchDiff> busMismatchDiffs(AclfNetwork rawNet, AclfNetwork epcNet) {
		List<BusMismatchDiff> result = new ArrayList<>();
		for (AclfBus rawBus : rawNet.getBusList()) {
			AclfBus epcBus = epcNet.getBus(rawBus.getId());
			if (epcBus == null) {
				continue;
			}
			Complex rawMismatch = rawBus.mismatch(AclfMethodType.NR);
			Complex epcMismatch = epcBus.mismatch(AclfMethodType.NR);
			result.add(new BusMismatchDiff(rawBus.getId(), rawBus.getName(),
					rawBus.getBaseVoltage() / 1000.0, rawBus.getAreaId(), rawBus.getZoneId(),
					rawMismatch, epcMismatch, epcMismatch.subtract(rawMismatch),
					genAggregate(rawBus), genAggregate(epcBus),
					rawBus.calNetLoadResults(), epcBus.calNetLoadResults(),
					rawBus.getShuntY(), epcBus.getShuntY(),
					rawBus.getBranchList().size(), epcBus.getBranchList().size()));
		}
		result.sort(Comparator.comparingDouble(BusMismatchDiff::diffAbs).reversed());
		return result;
	}

	private static void assertImportedBusMismatchParity(String label, AclfNetwork rawNet, AclfNetwork epcNet) {
		List<BusMismatchDiff> diffs = busMismatchDiffs(rawNet, epcNet);
		BusMismatchDiff maxDiff = diffs.isEmpty() ? null : diffs.get(0);
		if (maxDiff != null) {
			assertTrue(maxDiff.diffAbs() < IMPORTED_BUS_MISMATCH_TOL,
					() -> label + " imported bus mismatch differs from RAW beyond EPC source precision: " + maxDiff);
		}

		List<BusMismatchResidual> residuals = sourcePrecisionResiduals(rawNet, epcNet);
		BusMismatchResidual maxResidual = residuals.isEmpty() ? null : residuals.get(0);
		if (maxResidual != null) {
			assertTrue(maxResidual.residualAbs() < SOURCE_PRECISION_RESIDUAL_TOL,
					() -> label + " bus mismatch has unexplained residual after RAW voltage rounding: " + maxResidual);
		}
	}

	private static List<BusMismatchResidual> sourcePrecisionResiduals(AclfNetwork rawNet, AclfNetwork epcNet) {
		Map<String, Complex> savedVoltages = rawNet.getBusList().stream()
				.collect(Collectors.toMap(NameTag::getId, AclfBus::getVoltage));
		Map<String, Complex> rawMismatch = rawNet.getBusList().stream()
				.collect(Collectors.toMap(NameTag::getId, bus -> bus.mismatch(AclfMethodType.NR)));
		try {
			for (AclfBus bus : rawNet.getBusList()) {
				double vm = roundDecimal(bus.getVoltageMag(), 6);
				double va = Math.toRadians(roundDecimal(Math.toDegrees(bus.getVoltageAng()), 6));
				bus.setVoltage(new Complex(vm * Math.cos(va), vm * Math.sin(va)));
			}
			Map<String, Complex> rawRoundedMismatch = rawNet.getBusList().stream()
					.collect(Collectors.toMap(NameTag::getId, bus -> bus.mismatch(AclfMethodType.NR)));
			List<BusMismatchResidual> result = new ArrayList<>();
			for (AclfBus rawBus : rawNet.getBusList()) {
				AclfBus epcBus = epcNet.getBus(rawBus.getId());
				if (epcBus == null) {
					continue;
				}
				Complex rawDiffFromRounding = rawRoundedMismatch.get(rawBus.getId())
						.subtract(rawMismatch.get(rawBus.getId()));
				Complex epcDiff = epcBus.mismatch(AclfMethodType.NR)
						.subtract(rawMismatch.get(rawBus.getId()));
				result.add(new BusMismatchResidual(rawBus.getId(), rawBus.getName(),
						epcDiff, rawDiffFromRounding, epcDiff.subtract(rawDiffFromRounding)));
			}
			result.sort(Comparator.comparingDouble(BusMismatchResidual::residualAbs).reversed());
			return result;
		} finally {
			for (AclfBus bus : rawNet.getBusList()) {
				Complex voltage = savedVoltages.get(bus.getId());
				if (voltage != null) {
					bus.setVoltage(voltage);
				}
			}
		}
	}

	private static double roundDecimal(double value, int digits) {
		double scale = Math.pow(10.0, digits);
		return Math.rint(value * scale) / scale;
	}

	private static Complex genAggregate(AclfBus bus) {
		Complex total = Complex.ZERO;
		for (AclfGen gen : bus.getContributeGenList()) {
			if (gen.isActive()) {
				total = total.add(gen.getGen());
			}
		}
		return total;
	}

	private static void printSolvedBusPair(String label, AclfNetwork rawNet, AclfNetwork epcNet, String busId) {
		AclfBus rawBus = rawNet.getBus(busId);
		AclfBus epcBus = epcNet.getBus(busId);
		System.out.println("BUS_PAIR label=" + label + " id=" + busId);
		printSolvedBus("  RAW", rawBus);
		printSolvedBus("  EPC", epcBus);
	}

	private static void printSolvedBus(String prefix, AclfBus bus) {
		if (bus == null) {
			System.out.println(prefix + " missing");
			return;
		}
		System.out.println(prefix
				+ " code=" + bus.getGenCode()
				+ " active=" + bus.isActive()
				+ " vm=" + precise(bus.getVoltageMag())
				+ " vaDeg=" + precise(Math.toDegrees(bus.getVoltageAng()))
				+ " desiredV=" + precise(bus.getDesiredVoltMag())
				+ " genAgg=" + preciseComplex(genAggregate(bus))
				+ " load=" + preciseComplex(bus.calNetLoadResults())
				+ " shunt=" + preciseComplex(bus.getShuntY())
				+ " qLimit=" + bus.getQGenLimit()
				+ " pvLimit=" + pvLimitState(bus)
				+ " remoteQ=" + remoteQState(bus));
		for (AclfGen gen : bus.getContributeGenList()) {
			System.out.println(prefix
					+ " gen=" + gen.getId()
					+ " active=" + gen.isActive()
					+ " p=" + precise(gen.getGen().getReal())
					+ " q=" + precise(gen.getGen().getImaginary())
					+ " desiredV=" + precise(gen.getDesiredVoltMag())
					+ " qLimit=" + gen.getQGenLimit()
					+ " remote=" + nullToEmpty(gen.getRemoteVControlBusId()));
		}
		for (SwitchedShunt shunt : bus.getSwitchedShuntList()) {
			System.out.println(prefix
					+ " svd=" + shunt.getId()
					+ " active=" + shunt.isActive()
					+ " bInit=" + precise(shunt.getBInit())
					+ " bActual=" + precise(shunt.getBActual())
					+ " bLimit=" + shunt.getBLimit()
					+ " vRange=" + shunt.getDesiredControlRange());
		}
	}

	private static void printBranchPair(String label, AclfNetwork rawNet, AclfNetwork epcNet,
			String fromBusId, String toBusId, String circuitId) {
		AclfBranch rawBranch = rawNet.getBranch(fromBusId, toBusId, circuitId);
		AclfBranch epcBranch = epcNet.getBranch(fromBusId, toBusId, circuitId);
		System.out.println("BRANCH_PAIR label=" + label + " id=" + fromBusId + "->" + toBusId + "(" + circuitId + ")");
		printSolvedBranch("  RAW", rawBranch);
		printSolvedBranch("  EPC", epcBranch);
	}

	private static void printSolvedBranch(String prefix, AclfBranch branch) {
		if (branch == null) {
			System.out.println(prefix + " missing");
			return;
		}
		System.out.println(prefix
				+ " code=" + branch.getBranchCode()
				+ " active=" + branch.isActive()
				+ " z=" + preciseComplex(branch.getZ())
				+ " hShunt=" + preciseComplex(branch.getHShuntY())
				+ " fromV=" + preciseComplex(((AclfBus) branch.getFromBus()).getVoltage())
				+ " toV=" + preciseComplex(((AclfBus) branch.getToBus()).getVoltage())
				+ " sFrom=" + preciseComplex(branch.powerFrom2To())
				+ " sTo=" + preciseComplex(branch.powerTo2From()));
	}

	private static void printBusGeneratorControls(String label, AclfNetwork net, String busId) {
		AclfBus bus = net.getBus(busId);
		System.out.println(label + " generator controls for " + busId);
		if (bus == null) {
			System.out.println("  missing bus");
			return;
		}
		System.out.println("  busCode=" + bus.getGenCode()
				+ " active=" + bus.isActive()
				+ " pvLimit=" + pvLimitState(bus)
				+ " remoteQ=" + remoteQState(bus)
				+ " v=" + preciseComplex(bus.getVoltage())
				+ " desiredV=" + precise(bus.getDesiredVoltMag())
				+ " genAgg=" + preciseComplex(genAggregate(bus))
				+ " qLimit=" + bus.getQGenLimit());
		for (AclfGen gen : bus.getContributeGenList()) {
			System.out.println("  gen " + gen.getId()
					+ " active=" + gen.isActive()
					+ " gen=" + preciseComplex(gen.getGen())
					+ " desiredV=" + precise(gen.getDesiredVoltMag())
					+ " qLimit=" + gen.getQGenLimit()
					+ " pLimit=" + gen.getPGenLimit()
					+ " mbase=" + precise(gen.getMvaBase())
					+ " remote=" + nullToEmpty(gen.getRemoteVControlBusId()));
		}
	}

	private static void printIncidentBranches(String label, AclfNetwork net, String busId) {
		AclfBus bus = net.getBus(busId);
		System.out.println(label + " incident branches for " + busId);
		if (bus == null) {
			System.out.println("  missing bus");
			return;
		}
		for (Branch rawBranch : bus.getBranchList()) {
			if (rawBranch instanceof Aclf3WBranch branch3w) {
				System.out.println("  " + branch3w.getId() + " code=" + branch3w.getBranchCode()
						+ " starV=" + complex(((AclfBus) branch3w.getStarBus()).getVoltage()));
				printBranchLeg("    from", branch3w.getFromAclfBranch());
				printBranchLeg("    to", branch3w.getToAclfBranch());
				printBranchLeg("    tert", branch3w.getTertAclfBranch());
			} else if (rawBranch instanceof AclfBranch branch) {
				printBranchLeg("  ", branch);
			}
		}
	}

	private static void printBranchLeg(String prefix, AclfBranch branch) {
		AclfBus fromBus = (AclfBus) branch.getFromBus();
		AclfBus toBus = (AclfBus) branch.getToBus();
		System.out.println(prefix + " " + branch.getId()
				+ " class=" + branch.getClass().getName()
				+ " code=" + branch.getBranchCode()
				+ " isXfr=" + branch.isXfr()
				+ " isPS=" + branch.isPSXfr()
				+ " active=" + branch.isActive()
				+ " z=" + preciseComplex(branch.getZ())
				+ " zFactor=" + preciseComplex(branch.getZMultiplyFactor())
				+ " zTable=" + branch.getXfrZTableNumber()
				+ " hShunt=" + preciseComplex(branch.getHShuntY())
				+ " yFrom=" + preciseComplex(branch.getFromShuntY())
				+ " yTo=" + preciseComplex(branch.getToShuntY())
				+ " fromTap=" + precise(branch.getFromTurnRatio())
				+ " toTap=" + precise(branch.getToTurnRatio())
				+ " fromAng=" + precise(branch.getFromPSXfrAngle() * 180.0 / Math.PI)
				+ " toAng=" + precise(branch.getToPSXfrAngle() * 180.0 / Math.PI)
				+ " psAdapter=" + psAdapter(branch)
				+ " psCtrl=" + phaseControl(branch)
				+ " fromV=" + preciseComplex(fromBus.getVoltage())
				+ " toV=" + preciseComplex(toBus.getVoltage())
				+ " sFrom=" + preciseComplex(branch.powerFrom2To())
				+ " sTo=" + preciseComplex(branch.powerTo2From()));
	}

	private static String pvLimitState(AclfBus bus) {
		if (!bus.isPVBusLimit()) {
			return "none";
		}
		PVBusLimit pvLimit = bus.getPVBusLimit();
		return "control=" + pvLimit.isControlStatus()
				+ ",adjust=" + pvLimit.isAdjustStatus()
				+ ",limit=" + pvLimit.getQLimit();
	}

	private static String remoteQState(AclfBus bus) {
		if (!bus.isRemoteQBus()) {
			return "none";
		}
		RemoteQBus remoteQBus = bus.getRemoteQBus();
		return "control=" + remoteQBus.isControlStatus()
				+ ",adjust=" + remoteQBus.isAdjustStatus()
				+ ",type=" + remoteQBus.getRemoteQControlType()
				+ ",pct=" + precise(remoteQBus.getRemoteControlPercentage())
				+ ",limit=" + remoteQBus.getQLimit();
	}

	private static String psAdapter(AclfBranch branch) {
		if (!branch.isPSXfr()) {
			return "none";
		}
		AclfPSXformerAdapter ps = branch.toPSXfr();
		return "fromDeg=" + precise(ps.getFromAngle(UnitType.Deg))
				+ ",toDeg=" + precise(ps.getToAngle(UnitType.Deg));
	}

	private static String phaseControl(AclfBranch branch) {
		if (!branch.isPSXfrPControl()) {
			return "none";
		}
		PSXfrPControl control = branch.getPSXfrPControl();
		return "status=" + control.isStatus()
				+ ",range=" + preciseComplex(new Complex(
						control.getDesiredControlRange().getMax(),
						control.getDesiredControlRange().getMin()))
				+ ",p=" + precise(control.getPSpecified(UnitType.PU, branch.getNetwork().getBaseKva()))
				+ ",ang=" + preciseComplex(new Complex(
						control.getAngLimit(UnitType.Deg).getMax(),
						control.getAngLimit(UnitType.Deg).getMin()))
				+ ",controlFrom=" + control.isControlOnFromSide()
				+ ",flowFrom2To=" + control.isFlowFrom2To()
				+ ",meterFrom=" + control.isMeteredOnFromSide();
	}

	private static String preciseComplex(Complex value) {
		return String.format("%.12f+j%.12f", value.getReal(), value.getImaginary());
	}

	private static String precise(double value) {
		return String.format("%.12f", value);
	}

	private static Map<String, BusSignature> busSignatures(AclfNetwork net) {
		Map<String, BusSignature> result = new LinkedHashMap<>();
		net.getBusList().stream().sorted(Comparator.comparing(NameTag::getId)).forEach(bus ->
				{
					if (!bus.getId().startsWith("Bus")) {
						return;
					}
					result.put(bus.getId(), new BusSignature(
						bus.isActive(),
						round(bus.getBaseVoltage()),
						nullToEmpty(bus.getAreaId()),
						nullToEmpty(bus.getZoneId()),
						limit(bus.getVLimit())));
				});
		return result;
	}

	private static Map<String, String> genSignatures(AclfNetwork net) {
		Map<String, String> result = new LinkedHashMap<>();
		for (AclfBus bus : net.getBusList()) {
			for (AclfGen gen : bus.getContributeGenList()) {
				result.put(bus.getId() + "/" + gen.getId(),
						List.of(gen.isActive(), gen.isActive() ? complex(gen.getGen()) : complex(Complex.ZERO),
								round(gen.getMvaBase(), 1.0), nullToEmpty(gen.getRemoteVControlBusId())).toString());
			}
		}
		return sort(result);
	}

	private static Map<String, String> genControlSignatures(AclfNetwork net) {
		Map<String, String> result = new LinkedHashMap<>();
		for (AclfBus bus : net.getBusList()) {
			for (AclfGen gen : bus.getContributeGenList()) {
				result.put(bus.getId() + "/" + gen.getId(),
						List.of(round(gen.getDesiredVoltMag()), limit(gen.getQGenLimit()),
								limit(gen.getPGenLimit()), nullToEmpty(gen.getRemoteVControlBusId()),
								round(gen.getMvarControlPFactor()), round(gen.getMwControlPFactor()))
								.toString());
			}
		}
		return sort(result);
	}

	private static Map<String, String> busGenControlSignatures(AclfNetwork net) {
		Map<String, String> result = new LinkedHashMap<>();
		for (AclfBus bus : net.getBusList()) {
			if (bus.getId().startsWith("Bus") && bus.isGen()) {
				result.put(bus.getId(), List.of(bus.getGenCode().name(), round(bus.getDesiredVoltMag()),
						limit(bus.getQGenLimit()), pvLimitSignature(bus), remoteQSignature(bus)).toString());
			}
		}
		return sort(result);
	}

	private static String pvLimitSignature(AclfBus bus) {
		if (!bus.isPVBusLimit()) {
			return "";
		}
		PVBusLimit pvLimit = bus.getPVBusLimit();
		return List.of(pvLimit.isControlStatus(), pvLimit.isAdjustStatus(),
				limit(pvLimit.getQLimit())).toString();
	}

	private static String remoteQSignature(AclfBus bus) {
		if (!bus.isRemoteQBus()) {
			return "";
		}
		RemoteQBus remoteQBus = bus.getRemoteQBus();
		return List.of(remoteQBus.isControlStatus(), remoteQBus.isAdjustStatus(),
				remoteQBus.getRemoteQControlType().name(), round(remoteQBus.getRemoteControlPercentage()),
				limit(remoteQBus.getQLimit())).toString();
	}

	private static Map<String, String> genAggregateSignatures(AclfNetwork net) {
		Map<String, String> result = new LinkedHashMap<>();
		for (AclfBus bus : net.getBusList()) {
			if (bus.getId().startsWith("Bus")) {
				Complex total = Complex.ZERO;
				for (AclfGen gen : bus.getContributeGenList()) {
					if (gen.isActive()) {
						total = total.add(gen.getGen());
					}
				}
				result.put(bus.getId(), complex(total));
			}
		}
		return sort(result);
	}

	private static Map<String, String> loadSignatures(AclfNetwork net) {
		Map<String, String> result = new LinkedHashMap<>();
		for (AclfBus bus : net.getBusList()) {
			for (AclfLoad load : bus.getContributeLoadList()) {
				result.put(bus.getId() + "/" + load.getId(),
						List.of(load.isActive(), load.getCode().name(), complex(load.getLoadCP()),
								complex(load.getLoadCI()), complex(load.getLoadCZ()),
								complex(load.getDistGenPower()), load.isDistGenStatus()).toString());
			}
		}
		return sort(result);
	}

	private static Map<String, String> loadAggregateSignatures(AclfNetwork net) {
		Map<String, String> result = new LinkedHashMap<>();
		for (AclfBus bus : net.getBusList()) {
			if (bus.getId().startsWith("Bus")) {
				result.put(bus.getId(), List.of(round(bus.getLoadP()), round(bus.getLoadQ()),
						complex(bus.calNetLoadResults())).toString());
			}
		}
		return sort(result);
	}

	private static Map<String, String> fixedShuntSignatures(AclfNetwork net) {
		Map<String, String> result = new LinkedHashMap<>();
		for (AclfBus bus : net.getBusList()) {
			Complex fixedShuntY = aggregateFixedShuntY(bus);
			if (fixedShuntY.abs() > FIELD_TOL) {
				result.put(bus.getId(), complex(fixedShuntY));
			}
		}
		return sort(result);
	}

	private static Complex aggregateFixedShuntY(AclfBus bus) {
		Complex total = bus.getShuntY() == null ? Complex.ZERO : bus.getShuntY();
		for (ShuntCompensator shunt : bus.getCompensatorList()) {
			if (shunt.isActive()) {
				total = total.add(new Complex(0.0, shunt.getB()));
			}
		}
		return total;
	}

	private static Map<String, String> switchedShuntSignatures(AclfNetwork net) {
		Map<String, String> result = new LinkedHashMap<>();
		for (AclfBus bus : net.getBusList()) {
			boolean active = false;
			double bMax = 0.0;
			double bMin = 0.0;
			double bInit = 0.0;
			double bActual = 0.0;
			for (SwitchedShunt shunt : bus.getSwitchedShuntList()) {
				active |= shunt.isActive();
				if (shunt.isActive() && shunt.getBLimit() != null) {
					bMax += shunt.getBLimit().getMax();
					bMin += shunt.getBLimit().getMin();
				}
				if (shunt.isActive()) {
					bInit += shunt.getBInit();
					bActual += shunt.getBActual();
				}
			}
			if (!bus.getSwitchedShuntList().isEmpty()) {
				result.put(bus.getId(), List.of(active, round(bMax, LIMIT_TOL) + ":" + round(bMin, LIMIT_TOL),
						round(bInit), round(bActual)).toString());
			}
		}
		return sort(result);
	}

	private static Map<String, String> branchSignatures(AclfNetwork net) {
		Map<String, String> result = new LinkedHashMap<>();
		for (AclfBranch branch : net.getBranchList()) {
			if (branch.isXfr() || branch.isPSXfr()) {
				continue;
			}
			result.put(branch.getId(), List.of(branch.isActive(), normalizedBranchCode(branch),
					branch.getFromBusId(), branch.getToBusId(), nullToEmpty(branch.getCircuitNumber()),
					complex(branch.getZ()), complex(branch.getHShuntY()), complex(branch.getFromShuntY()),
					complex(branch.getToShuntY()), round(branch.getFromTurnRatio()),
					round(branch.getToTurnRatio())).toString());
		}
		return sort(result);
	}

	private static Map<String, String> transformerBranchSignatures(AclfNetwork net) {
		Map<String, String> result = new LinkedHashMap<>();
		for (AclfBranch branch : net.getBranchList()) {
			if (!branch.isXfr() && !branch.isPSXfr()) {
				continue;
			}
			result.put(branch.getId(), List.of(branch.isActive(), normalizedBranchCode(branch),
					branch.getFromBusId(), branch.getToBusId(), nullToEmpty(branch.getCircuitNumber()),
					complex(branch.getZ()), complex(branch.getHShuntY()), complex(branch.getFromShuntY()),
					complex(branch.getToShuntY()), round(branch.getFromTurnRatio()),
					round(branch.getToTurnRatio()), round(branch.getFromPSXfrAngle()),
					round(branch.getToPSXfrAngle()), branch.getXfrZTableNumber()).toString());
		}
		return sort(result);
	}

	private static void printSignatureDiffs(String label, Map<String, ?> raw, Map<String, ?> epc) {
		System.out.println("Top " + label + " signature differences");
		Set<String> commonIds = new TreeSet<>(raw.keySet());
		commonIds.retainAll(epc.keySet());
		int printed = 0;
		for (String id : commonIds) {
			if (!raw.get(id).equals(epc.get(id))) {
				System.out.println("  " + id + " RAW=" + raw.get(id) + " EPC=" + epc.get(id));
				printed++;
				if (printed >= 20) {
					break;
				}
			}
		}
		System.out.println("  rawOnly=" + examples(missing(raw.keySet(), epc.keySet()))
				+ " epcOnly=" + examples(missing(epc.keySet(), raw.keySet())));
	}

	private static void printModelSignatureDiffs(String label, AclfNetwork rawNet, AclfNetwork epcNet) {
		printSignatureDiffs(label + " bus", busSignatures(rawNet), busSignatures(epcNet));
		printSignatureDiffs(label + " generator", genSignatures(rawNet), genSignatures(epcNet));
		printSignatureDiffs(label + " generator control", genControlSignatures(rawNet), genControlSignatures(epcNet));
		printSignatureDiffs(label + " bus generator control", busGenControlSignatures(rawNet), busGenControlSignatures(epcNet));
		printSignatureDiffs(label + " bus generator aggregate", genAggregateSignatures(rawNet), genAggregateSignatures(epcNet));
		printSignatureDiffs(label + " load", loadSignatures(rawNet), loadSignatures(epcNet));
		printSignatureDiffs(label + " bus load aggregate", loadAggregateSignatures(rawNet), loadAggregateSignatures(epcNet));
		printSignatureDiffs(label + " fixed shunt", fixedShuntSignatures(rawNet), fixedShuntSignatures(epcNet));
		printSignatureDiffs(label + " switched shunt", switchedShuntSignatures(rawNet), switchedShuntSignatures(epcNet));
		printSignatureDiffs(label + " line branch", branchSignatures(rawNet), branchSignatures(epcNet));
		printSignatureDiffs(label + " transformer branch", transformerBranchSignatures(rawNet), transformerBranchSignatures(epcNet));
		printSignatureDiffs(label + " transformer control", transformerControlSignatures(rawNet), transformerControlSignatures(epcNet));
		printSignatureDiffs(label + " HVDC", hvdcSignatures(rawNet), hvdcSignatures(epcNet));
	}

	private static String normalizedBranchCode(AclfBranch branch) {
		return branch.isXfr() || branch.isPSXfr() ? "XFORMER" : branch.getBranchCode().name();
	}

	private static Map<String, String> transformerControlSignatures(AclfNetwork net) {
		Map<String, String> result = new LinkedHashMap<>();
		for (AclfBranch branch : net.getBranchList()) {
			if (branch.isTapControl()) {
				TapControl tap = branch.getTapControl();
				result.put(branch.getId() + "/tap", List.of(tap.isActive(), tap.isControlStatus(),
						tap.getAdjControlType().name(), tap.getControlMode().name(),
						tap.getTapControlType().name(), limit(tap.getDesiredControlRange()),
						limit(tap.getTurnRatioLimit()), round(tap.getVSpecified(UnitType.PU)),
						round(tap.getMvarSpecified(UnitType.PU, net.getBaseKva())),
						tap.isControlOnFromSide(), tap.isVcBusOnFromSide(),
						tap.isMeteredOnFromSide(), nullToEmpty(tap.getVcBusId())).toString());
			}
			if (branch.isPSXfrPControl()) {
				PSXfrPControl control = branch.getPSXfrPControl();
				result.put(branch.getId() + "/psxfr", List.of(control.isActive(), control.isControlStatus(),
						control.getAdjControlType().name(), control.getControlMode().name(),
						limit(control.getDesiredControlRange()), round(control.getPSpecified(UnitType.PU, net.getBaseKva())),
						limit(control.getAngLimit(UnitType.Deg)), control.isControlOnFromSide(),
						control.isFlowFrom2To(), control.isMeteredOnFromSide()).toString());
			}
		}
		return sort(result);
	}

	private static Map<String, String> areaSignatures(AclfNetwork net) {
		return net.getAreaMap().entrySet().stream()
				.collect(Collectors.toMap(Map.Entry::getKey,
						entry -> List.of(entry.getValue().getNumber()).toString(),
						(a, b) -> a, LinkedHashMap::new));
	}

	private static Map<String, String> zoneSignatures(AclfNetwork net) {
		return net.getZoneMap().entrySet().stream()
				.collect(Collectors.toMap(Map.Entry::getKey,
						entry -> List.of(entry.getValue().getNumber()).toString(),
						(a, b) -> a, LinkedHashMap::new));
	}

	private static Map<String, String> hvdcSignatures(AclfNetwork net) {
		Map<String, String> result = new LinkedHashMap<>();
		for (Branch branch : net.getSpecialBranchList()) {
			if (branch instanceof HvdcLine2T<?> hvdc) {
				result.put(branch.getId(), List.of(branch.isActive(), branch.getFromBusId(), branch.getToBusId(),
						round(hvdc.getMvaRating()), round(hvdc.getRdc(UnitType.PU))).toString());
			}
		}
		net.getHvdcLineMTList().forEach(hvdc -> result.put(hvdc.getId(),
				List.of(hvdc.isActive(), hvdc.getControlMode().name(),
						hvdc.getDcBusList().size(), hvdc.getDcLinkList().size(),
						hvdc.getConverterList().size()).toString()));
		return sort(result);
	}

	private static Set<String> ids(List<? extends NameTag> items) {
		return items.stream().map(NameTag::getId).collect(Collectors.toCollection(TreeSet::new));
	}

	private static Set<String> missing(Set<String> expected, Set<String> actual) {
		Set<String> missing = new TreeSet<>(expected);
		missing.removeAll(actual);
		return missing;
	}

	private static String examples(Set<String> ids) {
		return ids.stream().limit(5).collect(Collectors.joining(", "));
	}

	private static <T> Map<String, T> sort(Map<String, T> map) {
		return map.entrySet().stream()
				.sorted(Map.Entry.comparingByKey())
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
						(a, b) -> a, LinkedHashMap::new));
	}

	private static String complex(Complex value) {
		Complex nonNull = value != null ? value : Complex.ZERO;
		return round(nonNull.getReal()) + "+j" + round(nonNull.getImaginary());
	}

	private static String limit(LimitType limit) {
		return limit == null ? "" : round(limit.getMax(), LIMIT_TOL) + ":" + round(limit.getMin(), LIMIT_TOL);
	}

	private static double round(double value) {
		return round(value, FIELD_TOL);
	}

	private static double round(double value, double tolerance) {
		double normalized = Math.rint(value * 1.0e6) / 1.0e6;
		double rounded = Math.rint(normalized / tolerance) * tolerance;
		rounded = Math.rint(rounded * 1.0e6) / 1.0e6;
		return Math.abs(rounded) < tolerance ? 0.0 : rounded;
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

	private record BusSignature(
			boolean active,
			double baseVoltage,
			String areaId,
			String zoneId,
			String vLimit) {}

	private record BusMismatchDiff(
			String id,
			String name,
			double baseKv,
			String areaId,
			String zoneId,
			Complex rawMismatch,
			Complex epcMismatch,
			Complex diff,
			Complex rawGen,
			Complex epcGen,
			Complex rawLoad,
			Complex epcLoad,
			Complex rawShunt,
			Complex epcShunt,
			int rawBranches,
			int epcBranches) {
		double diffAbs() {
			return diff.abs();
		}

		@Override
		public String toString() {
			return id + " " + name + " " + round(baseKv, 1.0) + "kV"
					+ " area=" + areaId + " zone=" + zoneId
					+ " rawMis=" + complex(rawMismatch)
					+ " epcMis=" + complex(epcMismatch)
					+ " diff=" + complex(diff)
					+ " rawGen=" + complex(rawGen)
					+ " epcGen=" + complex(epcGen)
					+ " rawLoad=" + complex(rawLoad)
					+ " epcLoad=" + complex(epcLoad)
					+ " rawShunt=" + complex(rawShunt)
					+ " epcShunt=" + complex(epcShunt)
					+ " branches=" + rawBranches + "/" + epcBranches;
		}
	}

	private record BusMismatchResidual(
			String id,
			String name,
			Complex epcDiff,
			Complex rawRoundedDiff,
			Complex residual) {
		double residualAbs() {
			return residual.abs();
		}

		@Override
		public String toString() {
			return id + " " + name
					+ " epcDiff=" + preciseComplex(epcDiff)
					+ " rawRoundedDiff=" + preciseComplex(rawRoundedDiff)
					+ " unexplained=" + preciseComplex(residual);
		}
	}

	private record SolvedVoltageDiff(
			String id,
			String name,
			double baseKv,
			double rawVm,
			double epcVm,
			double rawVa,
			double epcVa) {
		double abs() {
			return Math.hypot(epcVm - rawVm, epcVa - rawVa);
		}

		@Override
		public String toString() {
			return id + " " + name + " " + round(baseKv, 1.0) + "kV"
					+ " rawVm=" + precise(rawVm)
					+ " epcVm=" + precise(epcVm)
					+ " dVm=" + precise(epcVm - rawVm)
					+ " rawVa=" + precise(rawVa)
					+ " epcVa=" + precise(epcVa)
					+ " dVa=" + precise(epcVa - rawVa);
		}
	}

	private static int psseVersionNumber(Path rawFile) throws Exception {
		PsseVersion version = IpssAdapter.parsePsseVersion(rawFile.toString());
		switch (version) {
			case PSSE_36: return 36;
			case PSSE_35: return 35;
			case PSSE_34: return 34;
			case PSSE_33: return 33;
			case PSSE_32: return 32;
			case PSSE_31: return 31;
			case PSSE_30: return 30;
			case PSSE_29: return 29;
			default: return 30;
		}
	}
}
