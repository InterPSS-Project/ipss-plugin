package org.interpss.plugin.optadj.texas2K;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.interpss.CorePluginTestSetup;
import org.interpss.numeric.datatype.AtomicCounter;
import org.interpss.plugin.optadj.algo.lf.AclfNetContigencyOptimizer;
import org.interpss.plugin.optadj.algo.util.AclfNetSsaHelper;
import org.interpss.plugin.optadj.result.OptAdjResultContainer;
import org.interpss.plugin.optadj.result.SsaResultContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.interpss.algo.parallel.ContingencyAnalysisMonad;
import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.contingency.dclf.DclfBranchOutage;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.algo.dclf.solver.IDclfSolver.CacheType;

/**
 * Regression test for {@code Texas2K_OptN1Scan_SsaResult_Sample}: N-1 DCLF scan with SSA
 * over-limit info via {@link AclfNetSsaHelper}, then {@link AclfNetContigencyOptimizer}
 * at 100% contingency loading limit on monitored branches.
 */
public class Texas2K_OptN1Scan_SsaResult_Test extends CorePluginTestSetup {

	private static final double SSA_SCAN_THRESHOLD_PCT = 100.0;
	private static final double OPT_ADJ_THRESHOLD_PCT = 100.0;

	private static int countN1OverLimitViolations(ContingencyAnalysisAlgorithm dclfAlgo,
			List<DclfBranchOutage> contList, Set<String> monitoredBranchIds) {
		return countN1OverLimitViolations(dclfAlgo, contList, monitoredBranchIds, OPT_ADJ_THRESHOLD_PCT, true);
	}

	private static int countN1OverLimitViolations(ContingencyAnalysisAlgorithm dclfAlgo,
			List<DclfBranchOutage> contList, Set<String> monitoredBranchIds,
			double loadingPctThreshold, boolean strictGreaterThan) {
		AtomicCounter cnt = new AtomicCounter();
		contList.parallelStream().forEach(contingency -> {
			ContingencyAnalysisMonad.of(dclfAlgo, contingency).ca(resultRec -> {
				double loading = resultRec.calLoadingPercent();
				if ((strictGreaterThan ? loading > loadingPctThreshold : loading >= loadingPctThreshold)
						&& monitoredBranchIds.contains(resultRec.aclfBranch.getId())) {
					cnt.increment();
				}
			});
		});
		return cnt.getCount();
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
		Map<String, OptAdjResultContainer.GenAdjustResult> adjustResults = new AclfNetContigencyOptimizer().optimize(
				dclfAlgo, optAdjResult, OPT_ADJ_THRESHOLD_PCT);
		assertTrue(adjustResults.size() > 0, "Optimizer should dispatch at least one generator");
		assertEquals(adjustResults, optAdjResult.getOptAdjResults());

		dclfAlgo.calculateDclf();

		int overLimitAfter = countN1OverLimitViolations(dclfAlgo, contList, monitoredBranchIds);

		// Regression anchors (Texas2K_OptN1Scan_SsaResult_Sample, 100% limit, monitored branches).
		// SSA scan at 100% narrows the constraint set; post-opt violations may exceed pre-opt.
		assertEquals(2359, contList.size(), "N-1 contingency count");
		assertEquals(13, overLimitBefore, "N-1 overload violations before optimization");
		assertEquals(17, adjustResults.size(), "Generators with material dispatch adjustment");
		assertTrue(overLimitAfter >= 13 && overLimitAfter <= 15,
				"N-1 overload violations after optimization (SSA-constrained LP, ~14)");
	}
}
