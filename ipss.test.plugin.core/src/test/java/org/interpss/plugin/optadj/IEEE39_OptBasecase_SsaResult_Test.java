package org.interpss.plugin.optadj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.interpss.CorePluginTestSetup;
import org.interpss.plugin.optadj.algo.lf.AclfNetLoadFlowOptimizer;
import org.interpss.plugin.optadj.result.OptAdjResultContainer;
import org.interpss.plugin.optadj.result.SsaBranchOverLimitInfo;
import org.interpss.plugin.optadj.result.SsaResultContainer;
import org.junit.jupiter.api.Test;

import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBranch;
import com.interpss.core.algo.dclf.solver.IDclfSolver.CacheType;

/**
 * Regression test for {@code IEEE39_OptBasecase_SsaResult_Sample}: DCLF basecase with
 * SSA over-limit info, then {@link AclfNetLoadFlowOptimizer} at 100% loading limit.
 */
public class IEEE39_OptBasecase_SsaResult_Test extends CorePluginTestSetup {
	private static final double LOADING_LIMIT_PCT = 100.0;

	private static SsaResultContainer buildBaseOverLimitSsaResult(ContingencyAnalysisAlgorithm dclfAlgo) {
		SsaResultContainer ssaResult = new SsaResultContainer();
		ssaResult.setBaseLoadingThreshold(LOADING_LIMIT_PCT);

		double baseMva = dclfAlgo.getNetwork().getBaseMva();
		dclfAlgo.getDclfAlgoBranchList().forEach(dclfBranch -> {
			double flowMw = dclfBranch.getDclfFlow() * baseMva;
			double rating = dclfBranch.getBranch().getRatingMvaA();
			double loadingPct = Math.abs(flowMw / rating) * 100.0;
			if (loadingPct > ssaResult.getBaseLoadingThreshold()) {
				ssaResult.getBaseOverLimitInfo().add(
						new SsaBranchOverLimitInfo(dclfBranch.getId(), rating, flowMw));
			}
		});
		return ssaResult;
	}

	private static int countOverLimitBranches(ContingencyAnalysisAlgorithm dclfAlgo) {
		return countBranchesAboveLoading(dclfAlgo, LOADING_LIMIT_PCT, true);
	}

	private static int countBranchesAboveLoading(ContingencyAnalysisAlgorithm dclfAlgo, double loadingPctThreshold,
			boolean useRatingMva1) {
		double baseMva = dclfAlgo.getNetwork().getBaseMva();
		int count = 0;
		for (DclfAlgoBranch dclfBranch : dclfAlgo.getDclfAlgoBranchList()) {
			double flowMw = dclfBranch.getDclfFlow() * baseMva;
			double rating = useRatingMva1
					? dclfBranch.getBranch().getRatingMva1()
					: dclfBranch.getBranch().getRatingMvaA();
			double loadingPct = Math.abs(flowMw / rating) * 100.0;
			if (loadingPct > loadingPctThreshold) {
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
		AclfNetwork net = IEEE39_TestCaseInfo.createTestCaseNetwork();

		ContingencyAnalysisAlgorithm dclfAlgo = DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm(net,
				CacheType.SenNotCached, true);
		dclfAlgo.calculateDclf();

		SsaResultContainer ssaResult = buildBaseOverLimitSsaResult(dclfAlgo);
		int overLimitBefore = countOverLimitBranches(dclfAlgo);
		double maxLoadingBefore = maxBranchLoadingPct(dclfAlgo, false);

		assertTrue(overLimitBefore > 0,
				"Precondition: IEEE-39 case with 600 MVA ratings should have overloaded branches");
		assertEquals(overLimitBefore, ssaResult.getBaseOverLimitInfo().size(),
				"SSA result should capture all base over-limit branches");

		Map<String, OptAdjResultContainer.GenAdjustResult> adjustResults = new AclfNetLoadFlowOptimizer().optimize(dclfAlgo, ssaResult,
				LOADING_LIMIT_PCT);
		assertTrue(adjustResults.size() > 0, "Optimizer should dispatch at least one generator");

		dclfAlgo.calculateDclf(DclfMethod.INC_LOSS);

		int overLimitAfter = countBranchesAboveLoading(dclfAlgo, LOADING_LIMIT_PCT, true);
		double maxLoadingAfter = maxBranchLoadingPct(dclfAlgo, true);

		assertTrue(overLimitAfter < overLimitBefore,
				"Optimizer should reduce the number of branches above 100% loading");
		assertTrue(maxLoadingAfter < maxLoadingBefore,
				"Peak branch loading should decrease after optimization");

		// Regression anchors (IEEE39_OptBasecase_SsaResult_Sample, 600 MVA uniform ratings, 100% limit).
		// SSA-driven control-gen selection uses a smaller generator set than the null-result path.
		assertEquals(5, overLimitBefore, "Overloaded branch count before optimization");
		assertEquals(4, overLimitAfter, "Overloaded branch count after optimization");
		assertEquals(3, adjustResults.size(), "Generators with material dispatch adjustment");
		assertTrue(maxLoadingBefore > 138.0 && maxLoadingBefore < 139.0,
				"Peak loading before optimization (~138.3%)");
		assertTrue(maxLoadingAfter > 137.0 && maxLoadingAfter < 138.5,
				"Peak loading after optimization (~137.9%)");
	}
}
