package org.interpss.plugin.optadj.ieee39.sparse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.interpss.CorePluginTestSetup;
import org.interpss.plugin.optadj.ieee39.IEEE39_TestCaseInfo;
import org.junit.jupiter.api.Test;

import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBranch;
import com.interpss.core.algo.dclf.solver.IDclfSolver.CacheType;
import com.interpss.optadj.algo.lf.AclfNetLoadFlowOptimizer;
import com.interpss.optadj.algo.util.AclfNetSsaHelper;
import com.interpss.optadj.result.OptAdjResultContainer;
import com.interpss.optadj.result.SsaBranchOverLimitInfo;
import com.interpss.optadj.result.SsaResultContainer;

/**
 * Regression test for {@code IEEE39_OptBasecase_SsaResult_Sparse_Sample}: DCLF basecase with
 * SSA over-limit info via {@link AclfNetSsaHelper}, then sparse {@link AclfNetLoadFlowOptimizer}
 * at 100% loading limit.
 */
public class IEEE39_OptBasecase_SsaResult_Sparse_Test extends CorePluginTestSetup {
	private static final double SSA_SCAN_THRESHOLD_PCT = 50.0;
	private static final double OPT_ADJ_THRESHOLD_PCT = 100.0;

	private static int countBranchesAboveLoading(ContingencyAnalysisAlgorithm dclfAlgo, double loadingPctThreshold) {
		double baseMva = dclfAlgo.getNetwork().getBaseMva();
		int count = 0;
		for (DclfAlgoBranch dclfBranch : dclfAlgo.getDclfAlgoBranchList()) {
			double flowMw = dclfBranch.getDclfFlow() * baseMva;
			double rating = dclfBranch.getBranch().getRatingMvaA();
			double loadingPct = Math.abs(flowMw / rating) * 100.0;
			if (loadingPct > loadingPctThreshold) {
				count++;
			}
		}
		return count;
	}

	private static long countSsaEntriesAboveLoading(SsaResultContainer ssaResult, double loadingPctThreshold) {
		return ssaResult.getBaseOverLimitInfo().stream()
				.filter(info -> info.getLoadingPercent() > loadingPctThreshold)
				.count();
	}

	private static double maxSsaLoading(SsaResultContainer ssaResult) {
		return ssaResult.getBaseOverLimitInfo().stream()
				.mapToDouble(SsaBranchOverLimitInfo::getLoadingPercent)
				.max()
				.orElse(0.0);
	}

	@Test
	void basecaseLoadFlowOptimizerWithSsaResultReducesOverLimitBranches() throws Exception {
		AclfNetwork net = IEEE39_TestCaseInfo.createTestCaseNetwork();

		ContingencyAnalysisAlgorithm dclfAlgo = DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm(net,
				CacheType.SenNotCached, true);
		dclfAlgo.calculateDclf();

		SsaResultContainer ssaResult = new AclfNetSsaHelper(dclfAlgo).baseCaseScan(SSA_SCAN_THRESHOLD_PCT);
		int overLimitBefore = countBranchesAboveLoading(dclfAlgo, OPT_ADJ_THRESHOLD_PCT);

		assertTrue(overLimitBefore > 0,
				"Precondition: IEEE-39 case with 600 MVA ratings should have overloaded branches");
		assertTrue(ssaResult.getBaseOverLimitInfo().size() > overLimitBefore,
				"SSA scan at 50% should capture more branches than the 100% overload count");

		OptAdjResultContainer optAdjResult = new OptAdjResultContainer(ssaResult);
		Map<String, OptAdjResultContainer.GenAdjustResult> adjustResults = new AclfNetLoadFlowOptimizer(true).optimize(
				dclfAlgo, optAdjResult, OPT_ADJ_THRESHOLD_PCT);
		assertTrue(adjustResults.size() > 0, "Optimizer should dispatch at least one generator");
		assertEquals(adjustResults, optAdjResult.getOptAdjResults());

		dclfAlgo.calculateDclf(DclfMethod.INC_LOSS);

		SsaResultContainer ssaResultAfter = new AclfNetSsaHelper(dclfAlgo)
				.calBaseCaseLoading(ssaResult.getBaseOverLimitInfo());
		long overLimitAfter = countSsaEntriesAboveLoading(ssaResultAfter, OPT_ADJ_THRESHOLD_PCT);
		double maxLoadingBefore = maxSsaLoading(ssaResult);
		double maxLoadingAfter = maxSsaLoading(ssaResultAfter);

		assertTrue(overLimitAfter < overLimitBefore,
				"Optimizer should reduce the number of branches above 100% loading");
		assertTrue(maxLoadingAfter < maxLoadingBefore,
				"Peak branch loading should decrease after optimization");

		// Regression anchors (IEEE39_OptBasecase_SsaResult_Sparse_Sample, 600 MVA uniform ratings).
		assertEquals(5, overLimitBefore, "Overloaded branch count before optimization");
		assertTrue(overLimitAfter >= 0 && overLimitAfter <= 2,
				"Overloaded branch count after optimization (LP solver tolerance band)");
		assertEquals(5, adjustResults.size(), "Generators with material dispatch adjustment");
		assertTrue(maxLoadingBefore > 138.0 && maxLoadingBefore < 139.0,
				"Peak loading before optimization (~138.3%)");
		assertTrue(maxLoadingAfter >= 99.0 && maxLoadingAfter <= 103.0,
				"Peak loading after optimization (~102%)");
	}
}
