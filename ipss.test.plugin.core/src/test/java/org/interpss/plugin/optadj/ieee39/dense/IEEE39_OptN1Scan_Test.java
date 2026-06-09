package org.interpss.plugin.optadj.ieee39.dense;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.interpss.CorePluginTestSetup;
import org.interpss.numeric.datatype.AtomicCounter;
import org.interpss.plugin.optadj.algo.lf.AclfNetContigencyOptimizer;
import org.interpss.plugin.optadj.ieee39.IEEE39_TestCaseInfo;
import org.interpss.plugin.optadj.result.OptAdjResultContainer;
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
 * Regression test for {@code IEEE39_OptN1Scan_Sample}: N-1 DCLF scan, then
 * {@link AclfNetContigencyOptimizer} at 100% contingency loading limit.
 */
public class IEEE39_OptN1Scan_Test extends CorePluginTestSetup {

	private static final double LOADING_LIMIT_PCT = 100.0;
	private static final double DISPATCH_TOLERANCE_MW = 0.05;

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
				if (resultRec.calLoadingPercent() > LOADING_LIMIT_PCT) {
					cnt.increment();
				}
			});
		});
		return cnt.getCount();
	}

	@Test
	void n1ContingencyOptimizerReducesOverLimitViolations() throws Exception {
		AclfNetwork net = IEEE39_TestCaseInfo.createTestCaseNetwork();

		ContingencyAnalysisAlgorithm dclfAlgo = DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm(net,
				CacheType.SenNotCached, true);
		dclfAlgo.calculateDclf();

		List<DclfBranchOutage> contList = buildBranchOutageContingencies(net, dclfAlgo);
		assertTrue(contList.size() > 0, "N-1 contingency list should not be empty");

		int overLimitBefore = countN1OverLimitViolations(dclfAlgo, contList);
		assertTrue(overLimitBefore > 0,
				"Precondition: N-1 scan should find overloaded post-contingency branches");

		Map<String, OptAdjResultContainer.GenAdjustResult> adjustResults = new AclfNetContigencyOptimizer().optimize(dclfAlgo, null,
				LOADING_LIMIT_PCT);

		assertTrue(adjustResults.size() >= 6 && adjustResults.size() <= 10,
				"Multiple generators should receive material dispatch adjustment");
		//adjustResults.values().forEach(result -> assertTrue(Math.abs(result.dP()) > 1.0,
		//		"Dispatch above threshold for " + result.genName()));
		double netDispatchMw = adjustResults.values().stream().mapToDouble(OptAdjResultContainer.GenAdjustResult::adjP).sum();
		assertEquals(0.0, netDispatchMw, DISPATCH_TOLERANCE_MW, "Net generator dispatch should balance");

		double increaseMw = adjustResults.values().stream().filter(r -> r.adjP() > 0.0)
				.mapToDouble(OptAdjResultContainer.GenAdjustResult::adjP).sum();
		double decreaseMw = adjustResults.values().stream().filter(r -> r.adjP() < 0.0)
				.mapToDouble(OptAdjResultContainer.GenAdjustResult::adjP).sum();

		// Regression anchors (IEEE39_OptN1Scan_Sample): ~362 MW redispatch, split across gens may vary.
		assertTrue(increaseMw > 350.0 && increaseMw < 375.0, "Total generation increase (~362 MW)");
		assertTrue(decreaseMw < -350.0 && decreaseMw > -375.0, "Total generation decrease (~-362 MW)");
		assertTrue(adjustResults.containsKey("Bus38-G1") && adjustResults.get("Bus38-G1").adjP() < -200.0,
				"Bus38-G1 should receive the largest decrease");
		assertTrue(adjustResults.values().stream().anyMatch(r -> r.adjP() > 70.0),
				"At least one generator should receive a major increase");

		dclfAlgo.calculateDclf();

		int overLimitAfter = countN1OverLimitViolations(dclfAlgo, contList);

		assertTrue(overLimitAfter < overLimitBefore,
				"Contingency optimizer should reduce N-1 overload violations");

		// Regression anchors (IEEE39_OptN1Scan_Sample, 600 MVA uniform ratings, 100% limit).
		assertEquals(45, contList.size(), "N-1 branch-outage contingency count");
		assertEquals(51, overLimitBefore, "N-1 overload violations before optimization");
		assertTrue(overLimitAfter >= 6 && overLimitAfter <= 8,
				"N-1 overload violations after optimization (LP solver tolerance band)");
	}
}
