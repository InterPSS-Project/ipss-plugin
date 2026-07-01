package org.interpss.plugin.optadj.texas2K.dense;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.interpss.CorePluginTestSetup;
import org.interpss.numeric.datatype.AtomicCounter;
import org.interpss.plugin.optadj.texas2K.Texas2K_TestCaseInfo;
import org.junit.jupiter.api.Test;

import com.interpss.algo.parallel.ContingencyAnalysisMonad;
import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.algo.dclf.solver.IDclfSolver.CacheType;
import com.interpss.core.contingency.dclf.DclfBranchOutage;
import com.interpss.optadj.algo.lf.AclfNetContigencyOptimizer;
import com.interpss.optadj.algo.util.AclfNetSsaHelper;
import com.interpss.optadj.result.OptAdjResultContainer;
import com.interpss.optadj.result.SsaResultContainer;

/**
 * Regression test for {@code Texas2K_OptN1Scan_SsaResult_Sample1}: full N-1 branch-outage
 * DCLF scan with SSA over-limit info via {@link AclfNetSsaHelper}, then dense
 * {@link AclfNetContigencyOptimizer} at 100% contingency loading limit on all branches.
 */
public class Texas2K_OptN1Scan_SsaResult_Test1 extends CorePluginTestSetup {

	private static final double SSA_SCAN_THRESHOLD_PCT = 100.0;
	private static final double OPT_ADJ_THRESHOLD_PCT = 100.0;

	private static int countN1OverLimitViolations(ContingencyAnalysisAlgorithm dclfAlgo,
			List<DclfBranchOutage> contList) {
		AtomicCounter cnt = new AtomicCounter();
		contList.parallelStream().forEach(contingency -> {
			ContingencyAnalysisMonad.of(dclfAlgo, contingency).ca(resultRec -> {
				if (resultRec.calLoadingPercent() > OPT_ADJ_THRESHOLD_PCT) {
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
	void n1BranchOutageOptimizerWithSsaResultReducesOverLimitViolations() throws Exception {
		AclfNetwork net = Texas2K_TestCaseInfo.createTestCaseNetwork();

		ContingencyAnalysisAlgorithm dclfAlgo = DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm(net,
				CacheType.SenNotCached, true);
		dclfAlgo.calculateDclf(DclfMethod.INC_LOSS);

		List<DclfBranchOutage> contList = Texas2K_TestCaseInfo.createBranchOutageContingencyList(net, dclfAlgo);
		assertTrue(contList.size() > 0, "N-1 contingency list should not be empty");

		SsaResultContainer ssaResult = new AclfNetSsaHelper(dclfAlgo).contingencyScan(contList,
				SSA_SCAN_THRESHOLD_PCT);
		int overLimitBefore = countN1OverLimitViolations(dclfAlgo, contList);
		assertTrue(overLimitBefore > 0,
				"Precondition: N-1 scan should find overloaded post-contingency branches");
		assertTrue(ssaResult.getCaOverLimitInfo().size() > 0,
				"SSA scan at 100% should identify contingency overloads");

		OptAdjResultContainer optAdjResult = new OptAdjResultContainer(ssaResult);
		Map<String, OptAdjResultContainer.GenAdjustResult> adjustResults = new AclfNetContigencyOptimizer(false)
				.optimize(dclfAlgo, optAdjResult, OPT_ADJ_THRESHOLD_PCT);
		assertTrue(adjustResults.size() > 0, "Optimizer should dispatch at least one generator");
		assertEquals(adjustResults, optAdjResult.getOptAdjResults());

		dclfAlgo.calculateDclf(DclfMethod.INC_LOSS);

		SsaResultContainer ssaResultAfter = new AclfNetSsaHelper(dclfAlgo)
				.contingencyScan(contList, ssaResult.getCaOverLimitInfo());
		long overLimitAfter = countSsaCaEntriesAboveLoading(ssaResultAfter, OPT_ADJ_THRESHOLD_PCT);

		// Regression anchors (Texas2K_OptN1Scan_SsaResult_Sample1, 100% limit, all branches).
		assertEquals(3216, contList.size(), "N-1 branch-outage contingency count");
		assertEquals(14, overLimitBefore, "N-1 overload violations before optimization");
		assertEquals(14, ssaResult.getCaOverLimitInfo().size(), "SSA contingency overload entries at 100%");
		//assertEquals(21, adjustResults.size(), "Generators with material dispatch adjustment");
		assertEquals(14, ssaResultAfter.getCaOverLimitInfo().size(),
				"SSA-tracked contingency pairs after optimization");
		assertEquals(10, overLimitAfter,
				"SSA-tracked contingency overloads above 100% after optimization");
	}
}
