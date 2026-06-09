package org.interpss.plugin.optadj.texas2K.sparse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.interpss.CorePluginTestSetup;
import org.interpss.plugin.optadj.algo.lf.AclfNetLoadFlowOptimizer;
import org.interpss.plugin.optadj.algo.util.AclfNetSsaHelper;
import org.interpss.plugin.optadj.result.OptAdjResultContainer;
import org.interpss.plugin.optadj.result.SsaResultContainer;
import org.interpss.plugin.optadj.texas2K.Texas2K_TestCaseInfo;
import org.junit.jupiter.api.Test;

import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBranch;
import com.interpss.core.algo.dclf.solver.IDclfSolver.CacheType;

/**
 * Regression test for {@code Texas2K_OptBasecase_SsaResult_Sparse_Sample}: DCLF basecase with
 * SSA over-limit info via {@link AclfNetSsaHelper}, then sparse {@link AclfNetLoadFlowOptimizer}
 * at 90% loading limit.
 */
public class Texas2K_OptBasecase_SsaResult_Sparse_Test extends CorePluginTestSetup {
	private static final double SSA_SCAN_THRESHOLD_PCT = 90.0;
	private static final double OPT_ADJ_THRESHOLD_PCT = 90.0;

	private static int countOverLimitBranches(ContingencyAnalysisAlgorithm dclfAlgo) {
		return countBranchesAboveLoading(dclfAlgo, OPT_ADJ_THRESHOLD_PCT, false, true);
	}

	private static int countBranchesAboveLoading(ContingencyAnalysisAlgorithm dclfAlgo, double loadingPctThreshold,
			boolean useRatingMva1, boolean strictGreaterThan) {
		double baseMva = dclfAlgo.getNetwork().getBaseMva();
		int count = 0;
		for (DclfAlgoBranch dclfBranch : dclfAlgo.getDclfAlgoBranchList()) {
			double flowMw = dclfBranch.getDclfFlow() * baseMva;
			double rating = useRatingMva1
					? dclfBranch.getBranch().getRatingMva1()
					: dclfBranch.getBranch().getRatingMvaA();
			double loadingPct = Math.abs(flowMw / rating) * 100.0;
			if (strictGreaterThan ? loadingPct > loadingPctThreshold : loadingPct >= loadingPctThreshold) {
				count++;
			}
		}
		return count;
	}

	private static double maxBranchLoadingPct(ContingencyAnalysisAlgorithm dclfAlgo, boolean useRatingMva1) {
		double baseMva = dclfAlgo.getNetwork().getBaseMva();
		double maxLoading = 0.0;
		for (DclfAlgoBranch dclfBranch : dclfAlgo.getDclfAlgoBranchList()) {
			double flowMw = dclfBranch.getDclfFlow() * baseMva;
			double rating = useRatingMva1
					? dclfBranch.getBranch().getRatingMva1()
					: dclfBranch.getBranch().getRatingMvaA();
			double loadingPct = Math.abs(flowMw / rating) * 100.0;
			maxLoading = Math.max(maxLoading, loadingPct);
		}
		return maxLoading;
	}

	@Test
	void basecaseLoadFlowOptimizerWithSsaResultReducesOverLimitBranches() throws Exception {
		AclfNetwork net = Texas2K_TestCaseInfo.createTestCaseNetwork();

		ContingencyAnalysisAlgorithm dclfAlgo = DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm(net,
				CacheType.SenNotCached, true);
		dclfAlgo.calculateDclf(DclfMethod.INC_LOSS);

		SsaResultContainer ssaResult = new AclfNetSsaHelper(dclfAlgo).baseCaseScan(SSA_SCAN_THRESHOLD_PCT);
		int overLimitBefore = countOverLimitBranches(dclfAlgo);
		double maxLoadingBefore = maxBranchLoadingPct(dclfAlgo, false);

		assertTrue(overLimitBefore > 0,
				"Precondition: Texas-2K case should have branches above 90% loading");
		assertTrue(ssaResult.getBaseOverLimitInfo().size() >= overLimitBefore,
				"SSA scan at 90% should capture at least the overloaded branch set");

		OptAdjResultContainer optAdjResult = new OptAdjResultContainer(ssaResult);
		Map<String, OptAdjResultContainer.GenAdjustResult> adjustResults = new AclfNetLoadFlowOptimizer(true).optimize(
				dclfAlgo, optAdjResult, OPT_ADJ_THRESHOLD_PCT);
		assertTrue(adjustResults.size() > 0, "Optimizer should dispatch at least one generator");
		assertEquals(adjustResults, optAdjResult.getOptAdjResults());

		dclfAlgo.calculateDclf(DclfMethod.INC_LOSS);

		int overLimitAfter = countBranchesAboveLoading(dclfAlgo, OPT_ADJ_THRESHOLD_PCT, false, true);
		double maxLoadingAfter = maxBranchLoadingPct(dclfAlgo, false);

		assertTrue(maxLoadingAfter < maxLoadingBefore,
				"Peak branch loading should decrease after optimization");

		// Regression anchors (Texas2K_OptBasecase_SsaResult_Sparse_Sample, 90% limit, RatingMvaA).
		assertEquals(11, overLimitBefore, "Overloaded branch count before optimization");
		assertTrue(overLimitAfter >= 10 && overLimitAfter <= 11,
				"Overloaded branch count after optimization");
		assertEquals(15, adjustResults.size(), "Generators with material dispatch adjustment");
		assertTrue(maxLoadingBefore > 99.0 && maxLoadingBefore < 100.5,
				"Peak loading before optimization (~99.6%)");
		assertTrue(maxLoadingAfter > 93.0 && maxLoadingAfter < 96.0,
				"Peak loading after optimization (~94.8%)");
	}
}
