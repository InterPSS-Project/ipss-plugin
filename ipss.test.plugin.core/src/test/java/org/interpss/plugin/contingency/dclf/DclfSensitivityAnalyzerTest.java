package org.interpss.plugin.contingency.dclf;

import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;
import static com.interpss.core.DclfAlgoObjectFactory.createCaOutageBranch;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import com.interpss.core.algo.dclf.DclfCgsfResult;
import com.interpss.core.algo.dclf.DclfSensitivityResult;
import org.junit.jupiter.api.Test;

import com.interpss.algo.parallel.BranchCAResultRec;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.algo.dclf.solver.DclfSensitivityAnalyzer;
import com.interpss.core.contingency.ContingencyBranchOutageType;
import com.interpss.core.contingency.dclf.DclfOutageBranch;

public class DclfSensitivityAnalyzerTest extends CorePluginTestSetup {
	private static final List<String> MONITOR_BRANCHES = List.of(
			"Bus1->Bus2(1)",
			"Bus1->Bus5(1)",
			"Bus2->Bus3(1)",
			"Bus4->Bus5(1)",
			"Bus9->Bus14(1)");

	@Test
	public void acceleratedGsfMatchesScalarCoreApi() throws Exception {
		AclfNetwork net = loadIeee14();
		ContingencyAnalysisAlgorithm scalar = createContingencyAnalysisAlgorithm(net);
		scalar.calculateDclf(DclfMethod.STD);

		DclfSensitivityAnalyzer.SensitivityRunResult result = new DclfSensitivityAnalyzer().runGsf(
				new DclfSensitivityAnalyzer.GsfRunRequest(
						net,
						DclfMethod.STD,
						MONITOR_BRANCHES,
						0.0,
						List.of("Bus2", "Bus3", "Bus8"),
						0.0,
						2));

		assertTrue(result.endpointFastPathUsed());
		assertFalse(result.results().isEmpty());
		assertEquals((long) MONITOR_BRANCHES.size() * 3L, result.candidateCount());
		for (DclfSensitivityResult row : result.results()) {
			double expected = scalar.calGenShiftFactor(row.sourceId(), net.getBranch(row.monitorBranchId()));
			assertEquals(expected, row.factor(), 1.0e-10);
		}
	}

	@Test
	public void acceleratedPtdfMatchesScalarCoreApi() throws Exception {
		AclfNetwork net = loadIeee14();
		ContingencyAnalysisAlgorithm scalar = createContingencyAnalysisAlgorithm(net);
		scalar.calculateDclf(DclfMethod.STD);

		DclfSensitivityAnalyzer.SensitivityRunResult result = new DclfSensitivityAnalyzer().runPtdf(
				new DclfSensitivityAnalyzer.PtdfRunRequest(
						net,
						DclfMethod.STD,
						MONITOR_BRANCHES,
						0.0,
						0.0,
						List.of(),
						2));

		assertTrue(result.endpointFastPathUsed());
		assertFalse(result.results().isEmpty());
		for (DclfSensitivityResult row : result.results()) {
			double expected = scalar.pTransferDistFactor(row.sourceId(), net.getBranch(row.monitorBranchId()));
			assertEquals(expected, row.factor(), 1.0e-10);
		}
	}

	@Test
	public void runCgsf_matchesCoreApi() throws Exception {
		AclfNetwork net = loadIeee14();
		ContingencyAnalysisAlgorithm scalar = createContingencyAnalysisAlgorithm(net);
		scalar.calculateDclf(DclfMethod.STD);

		String monitorBranchId = "Bus1->Bus2(1)";
		String outageBranchId = "Bus4->Bus5(1)";

		DclfSensitivityAnalyzer.CgsfRunResult result = new DclfSensitivityAnalyzer().runCgsf(
				new DclfSensitivityAnalyzer.CgsfRunRequest(
						net,
						DclfMethod.STD,
						monitorBranchId,
						outageBranchId,
						0.0,
						List.of(),
						0.0));

		assertFalse(result.results().isEmpty());
		for (DclfCgsfResult row : result.results()) {
			double expected = BranchCAResultRec.calCombinedShiftingFactor(
					row.genBusId(), scalar, outageBranchId, monitorBranchId);
			assertEquals(expected, row.cgsf(), 1.0e-10);
			assertEquals(monitorBranchId, row.monitorBranchId());
			assertEquals(outageBranchId, row.outageBranchId());
		}
	}

	@Test
	public void lodfRunUsesVectorPathAndMatchesScalarCoreApi() throws Exception {
		AclfNetwork net = loadIeee14();
		String outageBranchId = "Bus2->Bus3(1)";
		ContingencyAnalysisAlgorithm scalar = createContingencyAnalysisAlgorithm(net);
		scalar.calculateDclf(DclfMethod.STD);
		DclfOutageBranch outage = createCaOutageBranch(
				scalar.getDclfAlgoBranch(outageBranchId),
				ContingencyBranchOutageType.OPEN);

		DclfSensitivityAnalyzer.SensitivityRunResult result = new DclfSensitivityAnalyzer().runLodf(
				new DclfSensitivityAnalyzer.LodfRunRequest(
						net,
						DclfMethod.STD,
						MONITOR_BRANCHES,
						List.of(outageBranchId),
						0.0));

		assertFalse(result.results().isEmpty());
		assertEquals(MONITOR_BRANCHES.size(), result.candidateCount());
		for (DclfSensitivityResult row : result.results()) {
			double expected = outageBranchId.equals(row.monitorBranchId())
					? -1.0
					: scalar.lineOutageDFactor(outage, net.getBranch(row.monitorBranchId()));
			assertEquals(expected, row.factor(), 1.0e-10);
		}
	}

	@Test
	public void filtersGeneratorRatingLoadAndAreaInputs() throws Exception {
		AclfNetwork net = loadIeee14();
		DclfSensitivityAnalyzer analyzer = new DclfSensitivityAnalyzer(true);

		DclfSensitivityAnalyzer.SensitivityRunResult gsf = analyzer.runGsf(
				new DclfSensitivityAnalyzer.GsfRunRequest(
						net,
						DclfMethod.STD,
						MONITOR_BRANCHES,
						0.0,
						List.of(),
						DclfSensitivityAnalyzer.DEFAULT_MINIMUM_GSF_GENERATOR_RATING_MVA,
						2));
		assertTrue(gsf.candidateCount() < (long) MONITOR_BRANCHES.size() * net.getBusList().size());

		DclfSensitivityAnalyzer.SensitivityRunResult ptdf = analyzer.runPtdf(
				new DclfSensitivityAnalyzer.PtdfRunRequest(
						net,
						DclfMethod.STD,
						MONITOR_BRANCHES,
						0.0,
						10.0,
						List.of("1"),
						2));
		Map<String, List<DclfSensitivityResult>> bySource = ptdf.results().stream()
				.collect(Collectors.groupingBy(DclfSensitivityResult::sourceId));
		assertFalse(bySource.isEmpty());
		for (String sourceId : bySource.keySet()) {
			assertEquals("1", net.getBus(sourceId).getAreaId());
			assertTrue(DclfSensitivityAnalyzer.totalLoadMw(net.getBus(sourceId)) >= 10.0);
		}
	}

	private static AclfNetwork loadIeee14() throws Exception {
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/ieee14.ieee")
				.getAclfNet();
		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		algo.setLfMethod(AclfMethodType.NR);
		algo.loadflow();
		return net;
	}
}
