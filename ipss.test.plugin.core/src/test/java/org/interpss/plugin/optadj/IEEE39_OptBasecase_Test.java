package org.interpss.plugin.optadj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter.FileFormat;
import org.interpss.numeric.datatype.LimitType;
import org.interpss.plugin.optadj.algo.lf.AclfNetLoadFlowOptimizer;
import org.junit.jupiter.api.Test;

import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranch;
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

		new AclfNetLoadFlowOptimizer().optimize(dclfAlgo, null, LOADING_LIMIT_PCT);

		dclfAlgo.calculateDclf(DclfMethod.INC_LOSS);

		int overLimitAfter = countOverLimitBranches(dclfAlgo);
		double maxLoadingAfter = maxBranchLoadingPct(dclfAlgo);

		assertTrue(overLimitAfter < overLimitBefore,
				"Optimizer should reduce the number of branches above 100% loading");
		assertTrue(maxLoadingAfter < maxLoadingBefore,
				"Peak branch loading should decrease after optimization");

		// Regression anchors (IEEE39_OptBasecase_Sample, 600 MVA uniform ratings, 100% limit).
		assertEquals(5, overLimitBefore, "Overloaded branch count before optimization");
		assertEquals(3, overLimitAfter, "Overloaded branch count after optimization");
		assertTrue(maxLoadingBefore > 138.0 && maxLoadingBefore < 139.0,
				"Peak loading before optimization (~138.3%)");
		assertTrue(maxLoadingAfter > 106.5 && maxLoadingAfter < 108.0,
				"Peak loading after optimization (~107.1%)");
	}
}
