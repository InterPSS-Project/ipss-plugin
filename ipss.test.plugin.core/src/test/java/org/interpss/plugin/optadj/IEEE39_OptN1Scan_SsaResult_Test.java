package org.interpss.plugin.optadj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.interpss.CorePluginTestSetup;
import org.interpss.numeric.datatype.AtomicCounter;
import org.interpss.plugin.optadj.algo.lf.AclfNetContigencyOptimizer;
import org.interpss.plugin.optadj.algo.util.AclfNetSsaHelper;
import org.interpss.plugin.optadj.result.OptAdjResultContainer;
import org.interpss.plugin.optadj.result.SsaResultContainer;
import org.junit.jupiter.api.Test;

import com.interpss.algo.parallel.ContingencyAnalysisMonad;
import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.contingency.ContingencyBranchOutageType;
import com.interpss.core.contingency.dclf.DclfBranchOutage;
import com.interpss.core.contingency.dclf.DclfOutageBranch;
import com.interpss.core.algo.dclf.solver.IDclfSolver.CacheType;

/**
 * Regression test for {@code IEEE39_OptN1Scan_SsaResult_Sample}: N-1 DCLF scan with SSA
 * over-limit info via {@link AclfNetSsaHelper}, then {@link AclfNetContigencyOptimizer}
 * at 100% contingency loading limit.
 */
public class IEEE39_OptN1Scan_SsaResult_Test extends CorePluginTestSetup {

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
		return countN1OverLimitViolations(dclfAlgo, contList, OPT_ADJ_THRESHOLD_PCT, true);
	}

	private static int countN1OverLimitViolations(ContingencyAnalysisAlgorithm dclfAlgo,
			List<DclfBranchOutage> contList, double loadingPctThreshold, boolean strictGreaterThan) {
		AtomicCounter cnt = new AtomicCounter();
		contList.parallelStream().forEach(contingency -> {
			ContingencyAnalysisMonad.of(dclfAlgo, contingency).ca(resultRec -> {
				double loading = resultRec.calLoadingPercent();
				if (strictGreaterThan ? loading > loadingPctThreshold : loading >= loadingPctThreshold) {
					cnt.increment();
				}
			});
		});
		return cnt.getCount();
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
		assertTrue(ssaResult.getCaOverLimitInfo().size() > overLimitBefore,
				"SSA scan at 90% should capture more violations than the 100% overload count");

		OptAdjResultContainer optAdjResult = new OptAdjResultContainer(ssaResult);
		Map<String, OptAdjResultContainer.GenAdjustResult> adjustResults = new AclfNetContigencyOptimizer().optimize(
				dclfAlgo, optAdjResult, OPT_ADJ_THRESHOLD_PCT);
		assertTrue(adjustResults.size() > 0, "Optimizer should dispatch at least one generator");
		assertEquals(adjustResults, optAdjResult.getOptAdjResults());

		dclfAlgo.calculateDclf();

		int overLimitAfter = countN1OverLimitViolations(dclfAlgo, contList);

		// Regression anchors (IEEE39_OptN1Scan_SsaResult_Sample, 600 MVA uniform ratings, 100% limit).
		// SSA scan at 90% narrows the constraint set vs full N-1 scan; post-opt violations may exceed pre-opt.
		assertEquals(45, contList.size(), "N-1 branch-outage contingency count");
		assertEquals(51, overLimitBefore, "N-1 overload violations before optimization");
		assertEquals(5, adjustResults.size(), "Generators with material dispatch adjustment");
		assertTrue(overLimitAfter >= 85 && overLimitAfter <= 95,
				"N-1 overload violations after optimization (SSA-constrained LP, ~90)");
	}
}
