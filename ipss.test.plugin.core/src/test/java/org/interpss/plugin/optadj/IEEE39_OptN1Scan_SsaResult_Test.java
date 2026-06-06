package org.interpss.plugin.optadj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.interpss.CorePluginTestSetup;
import org.interpss.numeric.datatype.AtomicCounter;
import org.interpss.plugin.optadj.algo.lf.AclfNetContigencyOptimizer;
import org.interpss.plugin.optadj.result.OptAdjResultContainer;
import org.interpss.plugin.optadj.result.SsaBranchOverLimitInfo;
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
 * over-limit info, then {@link AclfNetContigencyOptimizer} at 100% contingency loading limit.
 */
public class IEEE39_OptN1Scan_SsaResult_Test extends CorePluginTestSetup {

	private static final double LOADING_LIMIT_PCT = 100.0;

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

	private static SsaResultContainer buildCaOverLimitSsaResult(ContingencyAnalysisAlgorithm dclfAlgo,
			List<DclfBranchOutage> contList) {
		SsaResultContainer ssaResult = new SsaResultContainer();
		ssaResult.setCaLoadingThreshold(LOADING_LIMIT_PCT);

		contList.parallelStream().forEach(contingency -> {
			ContingencyAnalysisMonad.of(dclfAlgo, contingency).ca(resultRec -> {
				double loading = resultRec.calLoadingPercent();
				if (loading > LOADING_LIMIT_PCT) {
					DclfOutageBranch outageBranch = ((DclfBranchOutage) resultRec.contingency).getOutageEquip();
					ssaResult.getCaOverLimitInfo().add(new SsaBranchOverLimitInfo(
							outageBranch.getBranch().getId(), resultRec.aclfBranch.getId(),
							resultRec.aclfBranch.getRatingMvaB(), resultRec.preFlowMW, resultRec.getPostFlowMW()));
				}
			});
		});
		return ssaResult;
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
	void n1ContingencyOptimizerWithSsaResultReducesOverLimitViolations() throws Exception {
		AclfNetwork net = IEEE39_TestCaseInfo.createTestCaseNetwork();

		ContingencyAnalysisAlgorithm dclfAlgo = DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm(net,
				CacheType.SenNotCached, true);
		dclfAlgo.calculateDclf();

		List<DclfBranchOutage> contList = buildBranchOutageContingencies(net, dclfAlgo);
		assertTrue(contList.size() > 0, "N-1 contingency list should not be empty");

		SsaResultContainer ssaResult = buildCaOverLimitSsaResult(dclfAlgo, contList);
		int overLimitBefore = countN1OverLimitViolations(dclfAlgo, contList);
		assertTrue(overLimitBefore > 0,
				"Precondition: N-1 scan should find overloaded post-contingency branches");
		assertEquals(overLimitBefore, ssaResult.getCaOverLimitInfo().size(),
				"SSA result should capture all N-1 over-limit violations");

		Map<String, OptAdjResultContainer.GenAdjustResult> adjustResults = new AclfNetContigencyOptimizer().optimize(dclfAlgo, ssaResult,
				LOADING_LIMIT_PCT);
		assertTrue(adjustResults.size() > 0, "Optimizer should dispatch at least one generator");

		dclfAlgo.calculateDclf();

		int overLimitAfter = countN1OverLimitViolations(dclfAlgo, contList);

		assertTrue(overLimitAfter < overLimitBefore,
				"Contingency optimizer should reduce N-1 overload violations");

		// Regression anchors (IEEE39_OptN1Scan_SsaResult_Sample, 600 MVA uniform ratings, 100% limit).
		assertEquals(45, contList.size(), "N-1 branch-outage contingency count");
		assertEquals(51, overLimitBefore, "N-1 overload violations before optimization");
		assertEquals(7, overLimitAfter, "N-1 overload violations after optimization");
	}
}
