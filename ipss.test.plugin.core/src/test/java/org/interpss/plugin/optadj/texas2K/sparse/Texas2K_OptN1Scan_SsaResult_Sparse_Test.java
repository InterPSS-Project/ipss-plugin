package org.interpss.plugin.optadj.texas2K.sparse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.interpss.CorePluginTestSetup;
import org.interpss.numeric.datatype.AtomicCounter;
import org.interpss.plugin.optadj.texas2K.Texas2K_TestCaseInfo;
import org.junit.jupiter.api.Test;

import com.interpss.algo.parallel.ContingencyAnalysisMonad;
import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.contingency.dclf.DclfBranchOutage;
import com.interpss.optadj.algo.lf.AclfNetContigencyOptimizer;
import com.interpss.optadj.algo.util.AclfNetSsaHelper;
import com.interpss.optadj.result.OptAdjResultContainer;
import com.interpss.optadj.result.SsaBranchOverLimitInfo;
import com.interpss.optadj.result.SsaResultContainer;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.algo.dclf.solver.IDclfSolver.CacheType;

/**
 * Regression test for {@code Texas2K_OptN1Scan_SsaResult_Sparse_Sample}: N-1 DCLF scan with SSA
 * over-limit info via {@link AclfNetSsaHelper}, then sparse {@link AclfNetContigencyOptimizer}
 * at 100% contingency loading limit on monitored branches.
 */
public class Texas2K_OptN1Scan_SsaResult_Sparse_Test extends CorePluginTestSetup {

	private static final double SSA_SCAN_THRESHOLD_PCT = 100.0;
	private static final double OPT_ADJ_THRESHOLD_PCT = 100.0;

	private static int countN1OverLimitViolations(ContingencyAnalysisAlgorithm dclfAlgo,
			List<DclfBranchOutage> contList, Set<String> monitoredBranchIds) {
		AtomicCounter cnt = new AtomicCounter();
		contList.parallelStream().forEach(contingency -> {
			ContingencyAnalysisMonad.of(dclfAlgo, contingency).ca(resultRec -> {
				double loading = resultRec.calLoadingPercent();
				if (loading > OPT_ADJ_THRESHOLD_PCT
						&& monitoredBranchIds.contains(resultRec.aclfBranch.getId())) {
					cnt.increment();
				}
			});
		});
		return cnt.getCount();
	}

	private static long countSsaCaEntriesAboveLoading(SsaResultContainer ssaResult, double loadingPctThreshold) {
		return ssaResult.getCaOverLimitInfo().stream()
				.filter(info -> info.getLoadingPercent() > loadingPctThreshold)
				.count();
	}

	@Test
	void n1ContingencyOptimizerWithSsaResultReducesOverLimitViolations() throws Exception {
		AclfNetwork net = Texas2K_TestCaseInfo.createTestCaseNetwork();

		ContingencyAnalysisAlgorithm dclfAlgo = DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm(net,
				CacheType.SenNotCached, true);
		dclfAlgo.calculateDclf(DclfMethod.INC_LOSS);

		List<DclfBranchOutage> contList = Texas2K_TestCaseInfo.createContingencyList(dclfAlgo);
		Set<String> monitoredBranchIds = Texas2K_TestCaseInfo.createMonitoredBranchIds();
		assertTrue(contList.size() > 0, "N-1 contingency list should not be empty");
		assertTrue(monitoredBranchIds.size() > 0, "Monitored branch set should not be empty");

		SsaResultContainer ssaResult = new AclfNetSsaHelper(dclfAlgo).contingencyScan(
				contList, monitoredBranchIds, SSA_SCAN_THRESHOLD_PCT);
		int overLimitBefore = countN1OverLimitViolations(dclfAlgo, contList, monitoredBranchIds);
		assertTrue(overLimitBefore > 0,
				"Precondition: N-1 scan should find overloaded post-contingency branches");
		assertTrue(ssaResult.getCaOverLimitInfo().size() >= overLimitBefore,
				"SSA scan at 100% should capture at least the overloaded violation set");

		OptAdjResultContainer optAdjResult = new OptAdjResultContainer(ssaResult);
		Map<String, OptAdjResultContainer.GenAdjustResult> adjustResults = new AclfNetContigencyOptimizer(true).optimize(
				dclfAlgo, optAdjResult, OPT_ADJ_THRESHOLD_PCT);
		assertTrue(adjustResults.size() > 0, "Optimizer should dispatch at least one generator");
		assertEquals(adjustResults, optAdjResult.getOptAdjResults());

		dclfAlgo.calculateDclf(DclfMethod.INC_LOSS);

		SsaResultContainer ssaResultAfter = new AclfNetSsaHelper(dclfAlgo)
				.contingencyScan(contList, ssaResult.getCaOverLimitInfo());
		long overLimitAfter = countSsaCaEntriesAboveLoading(ssaResultAfter, OPT_ADJ_THRESHOLD_PCT);

		// Regression anchors (Texas2K_OptN1Scan_SsaResult_Sparse_Sample, 100% limit, monitored branches).
		assertEquals(2359, contList.size(), "N-1 contingency count");
		assertEquals(13, overLimitBefore, "N-1 overload violations before optimization");
		assertEquals(13, ssaResult.getCaOverLimitInfo().size(), "SSA contingency overload entries at 100%");
		assertEquals(13, ssaResultAfter.getCaOverLimitInfo().size(),
				"SSA-tracked contingency pairs after optimization");
		assertEquals(9, overLimitAfter,
				"SSA-tracked contingency overloads above 100% after optimization");

		// CSF regression anchors (Texas2K_OptN1Scan_SsaResult_Sparse_Sample, sparse optimizer).
		String monitorBranchId = "Bus4044->Bus4185(1)";
		String outageBranchId = "Bus4044->Bus4119(1)";
		SsaBranchOverLimitInfo overLimitInfo = ssaResultAfter.getCaOverLimitInfo().stream()
				.filter(info -> info.getOverLimitBranchId().equals(monitorBranchId)
						&& info.getOutageBranchId().equals(outageBranchId))
				.findFirst()
				.orElse(null);
		assertTrue(overLimitInfo != null,
				"Anchor monitored/outage pair should remain in SSA result after optimization");
		assertEquals(1.0, overLimitInfo.calCombinedShiftingFactor("Bus4045", dclfAlgo), 1.0e-6,
				"CSF on Bus4045 for anchor contingency pair");
		assertEquals(1.0, overLimitInfo.calCombinedShiftingFactor("Bus4046", dclfAlgo), 1.0e-6,
				"CSF on Bus4046 for anchor contingency pair");
		assertEquals(1.0, overLimitInfo.calCombinedShiftingFactor("Bus4047", dclfAlgo), 1.0e-6,
				"CSF on Bus4047 for anchor contingency pair");
	}
}
