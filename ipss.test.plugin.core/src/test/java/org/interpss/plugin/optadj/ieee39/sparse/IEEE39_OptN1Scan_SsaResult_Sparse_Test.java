package org.interpss.plugin.optadj.ieee39.sparse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.interpss.CorePluginTestSetup;
import org.interpss.numeric.datatype.AtomicCounter;
import org.interpss.plugin.optadj.ieee39.IEEE39_TestCaseInfo;
import org.junit.jupiter.api.Test;

import com.interpss.algo.parallel.ContingencyAnalysisMonad;
import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.contingency.ContingencyBranchOutageType;
import com.interpss.core.contingency.dclf.DclfBranchOutage;
import com.interpss.core.contingency.dclf.DclfOutageBranch;
import com.interpss.optadj.algo.lf.AclfNetContigencyOptimizer;
import com.interpss.optadj.algo.util.AclfNetSsaHelper;
import com.interpss.optadj.result.OptAdjResultContainer;
import com.interpss.optadj.result.SsaBranchOverLimitInfo;
import com.interpss.optadj.result.SsaResultContainer;
import com.interpss.core.algo.dclf.solver.IDclfSolver.CacheType;

/**
 * Regression test for {@code IEEE39_OptN1Scan_SsaResult_Sparse_Sample}: N-1 DCLF scan with SSA
 * over-limit info via {@link AclfNetSsaHelper}, then sparse {@link AclfNetContigencyOptimizer}
 * at 100% contingency loading limit.
 */
public class IEEE39_OptN1Scan_SsaResult_Sparse_Test extends CorePluginTestSetup {

	private static final double SSA_SCAN_THRESHOLD_PCT = 90.0;
	private static final double OPT_ADJ_THRESHOLD_PCT = 100.0;

	private static List<DclfBranchOutage> buildBranchOutageContingencies(
			AclfNetwork net, ContingencyAnalysisAlgorithm dclfAlgo) {
		List<DclfBranchOutage> contList = new ArrayList<>();
		net.getBranchList().stream()
				.filter(branch -> !((AclfBranch) branch).isConnect2RefBus())
				.forEach(branch -> {
					DclfBranchOutage cont = DclfAlgoObjectFactory.createContingency("contBranch:" + branch.getId());
					DclfOutageBranch outage = DclfAlgoObjectFactory.createCaOutageBranch(
							dclfAlgo.getDclfAlgoBranch(branch.getId()),
							ContingencyBranchOutageType.OPEN);
					cont.setOutageEquip(outage);
					contList.add(cont);
				});
		return contList;
	}

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

	private static double maxSsaCaLoading(SsaResultContainer ssaResult) {
		return ssaResult.getCaOverLimitInfo().stream()
				.mapToDouble(SsaBranchOverLimitInfo::getLoadingPercent)
				.max()
				.orElse(0.0);
	}

	@Test
	void n1ContingencyOptimizerWithSsaResultReducesOverLimitViolations() throws Exception {
		AclfNetwork net = IEEE39_TestCaseInfo.createTestCaseNetwork();

		ContingencyAnalysisAlgorithm dclfAlgo = DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm(net,
				CacheType.SenNotCached, true);
		dclfAlgo.calculateDclf();

		List<DclfBranchOutage> contList = buildBranchOutageContingencies(net, dclfAlgo);
		assertTrue(contList.size() > 0, "N-1 contingency list should not be empty");

		SsaResultContainer ssaResult = new AclfNetSsaHelper(dclfAlgo).contingencyScan(contList, SSA_SCAN_THRESHOLD_PCT);
		int overLimitBefore = countN1OverLimitViolations(dclfAlgo, contList);
		assertTrue(overLimitBefore > 0,
				"Precondition: N-1 scan should find overloaded post-contingency branches");
		assertTrue(ssaResult.getCaOverLimitInfo().size() > 0,
				"SSA scan at 90% should identify material contingency overloads");

		OptAdjResultContainer optAdjResult = new OptAdjResultContainer(ssaResult);
		Map<String, OptAdjResultContainer.GenAdjustResult> adjustResults = new AclfNetContigencyOptimizer(true).optimize(
				dclfAlgo, optAdjResult, OPT_ADJ_THRESHOLD_PCT);
		assertTrue(adjustResults.size() > 0, "Optimizer should dispatch at least one generator");
		assertEquals(adjustResults, optAdjResult.getOptAdjResults());

		dclfAlgo.calculateDclf();

		SsaResultContainer ssaResultAfter = new AclfNetSsaHelper(dclfAlgo)
				.contingencyScan(contList, ssaResult.getCaOverLimitInfo());
		long overLimitAfter = countSsaCaEntriesAboveLoading(ssaResultAfter, OPT_ADJ_THRESHOLD_PCT);
		double maxLoadingBefore = maxSsaCaLoading(ssaResult);
		double maxLoadingAfter = maxSsaCaLoading(ssaResultAfter);

		assertTrue(maxLoadingAfter < maxLoadingBefore,
				"Peak contingency loading on SSA-tracked pairs should decrease");

		// Regression anchors (IEEE39_OptN1Scan_SsaResult_Sparse_Sample, 600 MVA uniform ratings, 100% limit).
		assertEquals(45, contList.size(), "N-1 branch-outage contingency count");
		assertEquals(19, ssaResult.getCaOverLimitInfo().size(), "SSA contingency overload entries at 90%");
		assertEquals(51, overLimitBefore, "N-1 overload violations before optimization");
		assertEquals(4, adjustResults.size(), "Generators with material dispatch adjustment");
		assertEquals(19, ssaResultAfter.getCaOverLimitInfo().size(),
				"SSA-tracked contingency pairs after optimization");
		assertEquals(3, overLimitAfter,
				"SSA-tracked contingency overloads above 100% after optimization");
		assertTrue(maxLoadingBefore > 138.0 && maxLoadingBefore < 140.0,
				"Peak contingency loading before optimization (~139.5%)");
		assertTrue(maxLoadingAfter > 124.0 && maxLoadingAfter < 127.0,
				"Peak contingency loading after optimization (~125.6%)");
	}
}
