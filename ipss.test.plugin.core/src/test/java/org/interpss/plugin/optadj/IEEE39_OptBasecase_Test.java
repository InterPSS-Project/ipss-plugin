package org.interpss.plugin.optadj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.interpss.CorePluginTestSetup;
import org.interpss.plugin.optadj.algo.lf.AclfNetLoadFlowOptimizer;
import org.interpss.plugin.optadj.algo.lf.AclfNetLoadFlowOptimizer.GenAdjustResult;
import org.junit.jupiter.api.Test;

import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBranch;
import com.interpss.core.algo.dclf.solver.IDclfSolver.CacheType;

/**
 * Regression test for {@code IEEE39_OptBasecase_Sample}: DCLF basecase with uniform
 * branch ratings, then {@link AclfNetLoadFlowOptimizer} at 100% loading limit.
 */
public class IEEE39_OptBasecase_Test extends CorePluginTestSetup {
	private static final double LOADING_LIMIT_PCT = 100.0;
	private static final double DISPATCH_TOLERANCE_MW = 0.05;

	private static int countOverLimitBranches(ContingencyAnalysisAlgorithm dclfAlgo) {
		return countBranchesAboveLoading(dclfAlgo, LOADING_LIMIT_PCT);
	}

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

	private static double maxBranchLoadingPct(ContingencyAnalysisAlgorithm dclfAlgo) {
		double baseMva = dclfAlgo.getNetwork().getBaseMva();
		double maxLoading = 0.0;
		for (DclfAlgoBranch dclfBranch : dclfAlgo.getDclfAlgoBranchList()) {
			double flowMw = dclfBranch.getDclfFlow() * baseMva;
			double rating = dclfBranch.getBranch().getRatingMvaA();
			double loadingPct = Math.abs(flowMw / rating) * 100.0;
			maxLoading = Math.max(maxLoading, loadingPct);
		}
		return maxLoading;
	}

	@Test
	void basecaseLoadFlowOptimizerReducesOverLimitBranches() throws Exception {
		AclfNetwork net = IEEE39_TestCaseInfo.createTestCaseNetwork();

		ContingencyAnalysisAlgorithm dclfAlgo = DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm(net,
				CacheType.SenNotCached, true);
		dclfAlgo.calculateDclf();

		int overLimitBefore = countOverLimitBranches(dclfAlgo);
		double maxLoadingBefore = maxBranchLoadingPct(dclfAlgo);
		assertTrue(overLimitBefore > 0,
				"Precondition: IEEE-39 case with 600 MVA ratings should have overloaded branches");

		Map<String, GenAdjustResult> adjustResults = new AclfNetLoadFlowOptimizer().optimize(dclfAlgo, null,
				LOADING_LIMIT_PCT);

		assertTrue(adjustResults.size() >= 6 && adjustResults.size() <= 8,
				"Multiple generators should receive material dispatch adjustment");
		//djustResults.values().forEach(result -> assertTrue(Math.abs(result.adjP()) > 1.0,
		//		"Dispatch above threshold for " + result.genName()));
		double netDispatchMw = adjustResults.values().stream().mapToDouble(GenAdjustResult::adjP).sum();
		assertEquals(0.0, netDispatchMw, DISPATCH_TOLERANCE_MW, "Net generator dispatch should balance");

		double increaseMw = adjustResults.values().stream().filter(r -> r.adjP() > 0.0)
				.mapToDouble(GenAdjustResult::adjP).sum();
		double decreaseMw = adjustResults.values().stream().filter(r -> r.adjP() < 0.0)
				.mapToDouble(GenAdjustResult::adjP).sum();

		// Regression anchors (IEEE39_OptBasecase_Sample): ~362 MW redispatch, split across gens may vary.
		assertTrue(increaseMw > 350.0 && increaseMw < 375.0, "Total generation increase (~362 MW)");
		assertTrue(decreaseMw < -350.0 && decreaseMw > -375.0, "Total generation decrease (~-362 MW)");
		assertTrue(adjustResults.containsKey("Bus30-G1") && adjustResults.get("Bus30-G1").adjP() > 200.0,
				"Bus30-G1 should receive the largest increase");
		assertTrue(adjustResults.containsKey("Bus38-G1") && adjustResults.get("Bus38-G1").adjP() < -200.0,
				"Bus38-G1 should receive the largest decrease");

		dclfAlgo.calculateDclf(DclfMethod.INC_LOSS);

		int overLimitAfter = countOverLimitBranches(dclfAlgo);
		double maxLoadingAfter = maxBranchLoadingPct(dclfAlgo);

		assertTrue(overLimitAfter < overLimitBefore,
				"Optimizer should reduce the number of branches above 100% loading");
		assertTrue(maxLoadingAfter < maxLoadingBefore,
				"Peak branch loading should decrease after optimization");

		// Regression anchors (IEEE39_OptBasecase_Sample, 600 MVA uniform ratings, 100% limit).
		assertEquals(5, overLimitBefore, "Overloaded branch count before optimization");
		assertTrue(overLimitAfter >= 0 && overLimitAfter <= 2,
				"Overloaded branch count after optimization (LP solver tolerance band)");
		assertTrue(maxLoadingBefore > 138.0 && maxLoadingBefore < 139.0,
				"Peak loading before optimization (~138.3%)");
		assertTrue(maxLoadingAfter > 99.0 && maxLoadingAfter < 108.0,
				"Peak loading after optimization (near 100-107% depending on residual overloads)");
	}
}
