package org.interpss.core.ca;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.exp.IpssNumericException;
import org.junit.jupiter.api.Test;

import com.interpss.algo.parallel.BranchCAResultRec;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.DclfContingencyConfig;
import com.interpss.core.algo.dclf.DclfIslandingTreatment;
import com.interpss.core.algo.dclf.SenAnalysisAlgorithm;
import com.interpss.core.algo.dclf.solver.DclfContingencySolutionMethod;
import com.interpss.core.algo.dclf.solver.ParallelDclfContingencyAnalyzer;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBranch;
import com.interpss.core.common.OutageConnectivityException;
import com.interpss.core.common.ReferenceBusException;
import com.interpss.core.contingency.ContingencyBranchOutageType;
import com.interpss.core.contingency.dclf.DclfMultiOutage;
import com.interpss.core.contingency.dclf.DclfOutageBranch;

public class Ieee14_CA_Test extends CorePluginTestSetup {
	@Test
	public void singleOutageTest() throws InterpssException, ReferenceBusException, IpssNumericException  {
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/ieee14.ieee")
				.getAclfNet();

		// run Dclf
		SenAnalysisAlgorithm dclfAlgo = DclfAlgoObjectFactory.createSenAnalysisAlgorithm(net);
		dclfAlgo.calculateDclf();
		
		// set single outage branch
		DclfAlgoBranch dclfBranch1 = dclfAlgo.getDclfAlgoBranch("Bus5->Bus6(1)");
		DclfOutageBranch outageBranch = DclfAlgoObjectFactory.createCaOutageBranch(dclfBranch1, ContingencyBranchOutageType.OPEN);
        double outBanchPreFlow = outageBranch.getDclfFlow();
        
        double sum = 0.0;  // Bus4->Bus7(1), Bus4->Bus9(1), Bus5->Bus6(1) interface diff before and after the outage
        for (DclfAlgoBranch dclfBranch : dclfAlgo.getDclfAlgoBranchList()) {
       		double f = dclfAlgo.lineOutageDFactor(outageBranch, dclfBranch.getBranch());
           	double postFlow = dclfBranch.getDclfFlow() + f * outBanchPreFlow;
           	
           	// check CA results 
       		if (dclfBranch.getId().equals("Bus4->Bus7(1)") ||
       				dclfBranch.getId().equals("Bus4->Bus9(1)"))
       			sum += postFlow - dclfBranch.getDclfFlow();
       		else if (dclfBranch.getId().equals("Bus5->Bus6(1)"))
       			sum -= dclfBranch.getDclfFlow();
		}
        assertTrue(Math.abs(sum) < 0.00001);
	}

	@Test
	public void singleClosureTest() throws InterpssException, ReferenceBusException, IpssNumericException  {
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/ieee14.ieee")
				.getAclfNet();
		
		/*
		 *     Branch closure "Bus4->Bus5(1)", 
		 *            Before closure     0.0
		 *            After closure    -62.34
		 *     Monitoring branch "Bus->Bus11(1)"
		 *            Before closure    15.10
		 *            After closure      6.30
		 */
		
		net.getBranch("Bus4->Bus5(1)").setStatus(false);

		// run Dclf
		ContingencyAnalysisAlgorithm dclfAlgo = DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm(net);
		dclfAlgo.calculateDclf();
		//System.out.println("Before closure");			
		//System.out.println(DclfResult.f(dclfAlgo, false));	
		
		DclfOutageBranch closureBranch = DclfAlgoObjectFactory.createCaOutageBranch(
				dclfAlgo.getDclfAlgoBranch("Bus4->Bus5(1)"), ContingencyBranchOutageType.CLOSE);
		
  		double closureFlow = dclfAlgo.calBranchClosureFlow(closureBranch);
		//System.out.println("Branch Flow After closure: " + f3);
		assertTrue(Math.abs(closureFlow + 0.623398) < 0.00001);
		
		AclfBranch monitorBranch = net.getBranch("Bus6->Bus11(1)");
   		double f = dclfAlgo.lineOutageDFactor(closureBranch, monitorBranch);
       	double postFlow = dclfAlgo.getDclfAlgoBranch("Bus6->Bus11(1)").getDclfFlow() + f * closureFlow;
		System.out.println("Branch Flow After closure: " + postFlow);
		assertTrue(Math.abs(postFlow - 0.0630) < 0.001);
 	}

	@Test
	public void multipleOutageTest() throws InterpssException, ReferenceBusException, IpssNumericException, OutageConnectivityException  {
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/ieee14.ieee")
				.getAclfNet();
		
		// run Dclf
		ContingencyAnalysisAlgorithm dclfAlgo = DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm(net);
		dclfAlgo.calculateDclf();

		// define outage branches
		dclfAlgo.getOutageBranchList().clear();
		dclfAlgo.getOutageBranchList().add(
				DclfAlgoObjectFactory.createCaOutageBranch(
						dclfAlgo.getDclfAlgoBranch("Bus1->Bus5(1)"), ContingencyBranchOutageType.OPEN));
		dclfAlgo.getOutageBranchList().add(
				DclfAlgoObjectFactory.createCaOutageBranch(
						dclfAlgo.getDclfAlgoBranch("Bus3->Bus4(1)"), ContingencyBranchOutageType.OPEN));
		dclfAlgo.getOutageBranchList().add(
				DclfAlgoObjectFactory.createCaOutageBranch(
						dclfAlgo.getDclfAlgoBranch("Bus6->Bus11(1)"), ContingencyBranchOutageType.OPEN));

		// define reference bus for the multi-outage calculation. Since Bus1 is connected to an outage branch, we
		// to choice a different ref bus.
		dclfAlgo.setRefBus("Bus14");
		
		// calculate multi-outage LODF and return inv[I-PTDF]
		Object invE_PTDF = dclfAlgo.calMultiOutageInvE_PTDF("ContId");

        double baseMva = net.getBaseMva();
		for (DclfAlgoBranch dclfBranch : dclfAlgo.getDclfAlgoBranchList()) {
        	double preFlow = dclfBranch.getDclfFlow()*baseMva,
        		   postFlow = 0.0;
        	// calculate the LODF factors for the monitoring branch
        	// LODF factors are arranged in the OutageBranchList sequence
        	double[] factors = dclfAlgo.calMultiOutageLODFs(dclfBranch.getBranch(), invE_PTDF);
        	if (factors != null) {  // factors = null if branch is an outage branch
            	double sum = 0.0;
            	int cnt = 0;
        		for (DclfOutageBranch outBranch : dclfAlgo.getOutageBranchList()) {
        			double flow = outBranch.getDclfFlow();
        			sum += flow * factors[cnt++];
        		}
        		postFlow = sum*baseMva + preFlow;
        	}
      
/* check CA results against the number in the IEEE paper
Cont 1, Bus1->Bus5(1), 71.11943, 0.0000, 100.0000, 0.0000
Cont 1, Bus3->Bus4(1), -24.14976, 0.0000, 100.0000, 0.0000
Cont 1, Bus6->Bus11(1), 6.30476, 0.0000, 100.0000, 0.0000

Cont 1, Bus2->Bus5(1), 40.90397, 69.08805, 100.0000, 69.08805
Cont 1, Bus6->Bus13(1), 17.03369, 17.88058, 100.0000, 17.88058
 */
       		if (dclfBranch.getId().equals("Bus1->Bus5(1)") ||
       				dclfBranch.getId().equals("Bus3->Bus4(1)")||
       				dclfBranch.getId().equals("Bus6->Bus11(1)"))
       			assertTrue(postFlow == 0.0);
       		else if (dclfBranch.getId().equals("Bus2->Bus5(1)"))
       			assertTrue(Math.abs(postFlow - 69.08805) < 0.00001);
       		else if (dclfBranch.getId().equals("Bus6->Bus16(1)"))
       			assertTrue(Math.abs(postFlow - 17.03369) < 0.00001);

       		/*        	
        	System.out.println(
        			contId + ", " + 
        			branch.getId() + ", " +
        			Number2String.toStr(preFlow) + ", " +
        			Number2String.toStr(postFlow) + ", " +
        			Number2String.toStr(branch.getRatingMva1()) + ", " +
        			Number2String.toStr(branch.getRatingMva1() == 0? 0.0 : 100.0*Math.abs(postFlow)/branch.getRatingMva1())
        	);
*/        	
		}
	}

	@Test
	public void islandedBusCompensationTest() throws InterpssException {
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/ieee14.ieee")
				.getAclfNet();

		ContingencyAnalysisAlgorithm baseAlgo = DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm(net);
		assertTrue(baseAlgo.calculateDclf());
		double islandBusPower = baseAlgo.getBusPower("Bus14");
		double boundaryFlow9To14 = baseAlgo.getBranchFlow("Bus9->Bus14(1)");
		double boundaryFlow13To14 = baseAlgo.getBranchFlow("Bus13->Bus14(1)");
		double[] busInjectionCompensatedPostFlow = new double[net.getBranchList().size()];
		double[] boundaryInjectionCompensatedPostFlow = new double[net.getBranchList().size()];
		double[] reverseBoundaryInjectionCompensatedPostFlow = new double[net.getBranchList().size()];
		int index = 0;
		for (AclfBranch branch : net.getBranchList()) {
			double bus14Ptdf = baseAlgo.pTransferDistFactor("Bus14", branch);
			double bus9Ptdf = baseAlgo.pTransferDistFactor("Bus9", branch);
			double bus13Ptdf = baseAlgo.pTransferDistFactor("Bus13", branch);
			double baseFlow = baseAlgo.getBranchFlow(branch);
			busInjectionCompensatedPostFlow[index] = baseFlow - islandBusPower * bus14Ptdf;
			boundaryInjectionCompensatedPostFlow[index] =
					baseFlow - boundaryFlow9To14 * bus9Ptdf - boundaryFlow13To14 * bus13Ptdf;
			reverseBoundaryInjectionCompensatedPostFlow[index] =
					baseFlow + boundaryFlow9To14 * bus9Ptdf + boundaryFlow13To14 * bus13Ptdf;
			index++;
		}

		net.getBranch("Bus9->Bus14(1)").setStatus(false);
		net.getBranch("Bus13->Bus14(1)").setStatus(false);
		ContingencyAnalysisAlgorithm islandedAlgo = DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm(net);
		assertTrue(!islandedAlgo.calculateDclf());

		net.getBus("Bus14").setStatus(false);
		ContingencyAnalysisAlgorithm reducedAlgo = DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm(net);
		assertTrue(reducedAlgo.calculateDclf());

		double maxBusInjectionDiff = 0.0;
		double maxBoundaryInjectionDiff = 0.0;
		double maxReverseBoundaryInjectionDiff = 0.0;
		index = 0;
		for (AclfBranch branch : net.getBranchList()) {
			if (branch.isActive() && !branch.isConnect2RefBus()) {
				double reducedFlow = reducedAlgo.getBranchFlow(branch.getId());
				maxBusInjectionDiff = Math.max(
						maxBusInjectionDiff,
						Math.abs(reducedFlow - busInjectionCompensatedPostFlow[index]));
				maxBoundaryInjectionDiff = Math.max(
						maxBoundaryInjectionDiff,
						Math.abs(reducedFlow - boundaryInjectionCompensatedPostFlow[index]));
				maxReverseBoundaryInjectionDiff = Math.max(
						maxReverseBoundaryInjectionDiff,
						Math.abs(reducedFlow - reverseBoundaryInjectionCompensatedPostFlow[index]));
			}
			index++;
		}
		assertTrue(maxReverseBoundaryInjectionDiff < 5.0e-4,
				"PTDF island boundary compensation maxDiff=" + maxBoundaryInjectionDiff
						+ ", reverseBoundaryMaxDiff=" + maxReverseBoundaryInjectionDiff
						+ ", busInjectionMaxDiff=" + maxBusInjectionDiff
						+ ", islandBusPower=" + islandBusPower
						+ ", boundaryFlows=" + boundaryFlow9To14 + "," + boundaryFlow13To14);
		assertTrue(maxReverseBoundaryInjectionDiff < maxBusInjectionDiff,
				"Boundary-flow compensation should be closer than island-bus injection compensation");
	}

	@Test
	public void parallelAnalyzerOneBusIslandPolicyTest() throws InterpssException {
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/ieee14.ieee")
				.getAclfNet();
		ContingencyAnalysisAlgorithm dclfAlgo = DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm(net);
		assertTrue(dclfAlgo.calculateDclf());

		DclfMultiOutage contingency =
				DclfAlgoObjectFactory.createMultiOutageContingency(
						"OPEN:Bus14Island",
						ContingencyBranchOutageType.OPEN);
		contingency.getOutageEquips().add(outage(dclfAlgo, "Bus9->Bus14(1)"));
		contingency.getOutageEquips().add(outage(dclfAlgo, "Bus13->Bus14(1)"));

		net.getBranch("Bus9->Bus14(1)").setStatus(false);
		net.getBranch("Bus13->Bus14(1)").setStatus(false);
		assertTrue(!DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm(net).calculateDclf());
		net.getBus("Bus14").setStatus(false);
		ContingencyAnalysisAlgorithm reducedAlgo = DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm(net);
		assertTrue(reducedAlgo.calculateDclf());
		double reducedPostMw = reducedAlgo.getBranchFlow("Bus6->Bus13(1)") * net.getBaseMva();
		net.getBus("Bus14").setStatus(true);
		net.getBranch("Bus9->Bus14(1)").setStatus(true);
		net.getBranch("Bus13->Bus14(1)").setStatus(true);

		DclfContingencyConfig skipConfig = new DclfContingencyConfig();
		skipConfig.setOverloadThreshold(0.0);
		skipConfig.setDclfInclLoss(false);
		skipConfig.setSolutionMethod(DclfContingencySolutionMethod.WoodburyMatrixUpdate);
		skipConfig.setIslandingTreatment(DclfIslandingTreatment.SKIP);
		ConcurrentLinkedQueue<BranchCAResultRec> skipped =
				ParallelDclfContingencyAnalyzer.executeContingencyAnalysis(
						net,
						List.of(contingency),
						Set.of("Bus6->Bus13(1)"),
						skipConfig,
						1);
		assertTrue(skipped.isEmpty());

		DclfContingencyConfig compensateConfig = new DclfContingencyConfig();
		compensateConfig.setOverloadThreshold(0.0);
		compensateConfig.setDclfInclLoss(false);
		compensateConfig.setSolutionMethod(DclfContingencySolutionMethod.WoodburyMatrixUpdate);
		compensateConfig.setIslandingTreatment(DclfIslandingTreatment.ANCHORED_COMPENSATE_ONE_BUS);
		ConcurrentLinkedQueue<BranchCAResultRec> compensated =
				ParallelDclfContingencyAnalyzer.executeContingencyAnalysis(
						net,
						List.of(contingency),
						Set.of("Bus6->Bus13(1)"),
						compensateConfig,
						1);
		assertTrue(!compensated.isEmpty());
		BranchCAResultRec result = compensated.iterator().next();
		assertTrue(Math.abs(result.getPostFlowMW() - reducedPostMw) < 0.05,
				"Anchored policy post flow should track reduced-network DCLF. postFlow="
						+ result.getPostFlowMW() + ", reduced=" + reducedPostMw);

		DclfContingencyConfig fullReplayConfig = new DclfContingencyConfig();
		fullReplayConfig.setOverloadThreshold(0.0);
		fullReplayConfig.setDclfInclLoss(false);
		fullReplayConfig.setSolutionMethod(DclfContingencySolutionMethod.SparseEqnSolve);
		fullReplayConfig.setIslandingTreatment(DclfIslandingTreatment.FULL_DCLF_REPLAY);
		ConcurrentLinkedQueue<BranchCAResultRec> fullReplay =
				ParallelDclfContingencyAnalyzer.executeContingencyAnalysis(
						net,
						List.of(contingency),
						Set.of("Bus6->Bus13(1)"),
						fullReplayConfig,
						1);
		assertTrue(!fullReplay.isEmpty());
		BranchCAResultRec fullReplayResult = fullReplay.iterator().next();
		assertTrue(Math.abs(fullReplayResult.getPostFlowMW() - reducedPostMw) < 0.05,
				"FULL_DCLF_REPLAY should use local one-bus island handling for non-reference islands. fullReplay="
						+ fullReplayResult.getPostFlowMW() + ", reduced=" + reducedPostMw);
	}

	@Test
	public void defaultAnchoredPolicyMatchesReducedDclfForIeee14BusIslands() throws InterpssException {
		Set<String> accurateBusIds = new LinkedHashSet<>();
		StringBuilder mismatchSummary = new StringBuilder();
		for (int busNumber = 2; busNumber <= 14; busNumber++) {
			String busId = "Bus" + busNumber;
			AclfNetwork net = CorePluginFactory
					.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
					.load("testData/adpter/ieee_format/ieee14.ieee")
					.getAclfNet();
			ContingencyAnalysisAlgorithm dclfAlgo = DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm(net);
			assertTrue(dclfAlgo.calculateDclf(), "base DCLF failed for " + busId);

			List<String> outageBranchIds = activeIncidentBranchIds(net, busId);
			assertTrue(!outageBranchIds.isEmpty(), "No incident branches found for " + busId);
			DclfMultiOutage contingency =
					DclfAlgoObjectFactory.createMultiOutageContingency(
							"OPEN:" + busId + "Island",
							ContingencyBranchOutageType.OPEN);
			for (String branchId : outageBranchIds) {
				contingency.getOutageEquips().add(outage(dclfAlgo, branchId));
			}

			Set<String> monitoredBranchIds = monitoredNonIncidentBranchIds(net, outageBranchIds);
			assertTrue(!monitoredBranchIds.isEmpty(), "No monitored branches found for " + busId);
			DclfContingencyConfig compensateConfig = new DclfContingencyConfig();
			compensateConfig.setOverloadThreshold(0.0);
			compensateConfig.setDclfInclLoss(false);
			compensateConfig.setSolutionMethod(DclfContingencySolutionMethod.SparseEqnSolve);
			ConcurrentLinkedQueue<BranchCAResultRec> compensated =
					ParallelDclfContingencyAnalyzer.executeContingencyAnalysis(
							net,
							List.of(contingency),
							monitoredBranchIds,
							compensateConfig,
							1);
			assertTrue(compensated.size() == monitoredBranchIds.size(),
					"Missing compensated results for " + busId + ", expected="
							+ monitoredBranchIds.size() + ", actual=" + compensated.size());

			AclfNetwork reducedNet = CorePluginFactory
					.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
					.load("testData/adpter/ieee_format/ieee14.ieee")
					.getAclfNet();
			for (String branchId : outageBranchIds) {
				reducedNet.getBranch(branchId).setStatus(false);
			}
			reducedNet.getBus(busId).setStatus(false);
			deactivateBusesDisconnectedFromReference(reducedNet);
			ContingencyAnalysisAlgorithm reducedAlgo =
					DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm(reducedNet);
			assertTrue(reducedAlgo.calculateDclf(), "reduced-network DCLF failed for " + busId);

			double maxDiff = 0.0;
			String maxDiffBranchId = "";
			double maxCompensatedMw = 0.0;
			double maxReducedMw = 0.0;
			for (BranchCAResultRec result : compensated) {
				String branchId = result.aclfBranch.getId();
				double expectedPostMw = reducedAlgo.getBranchFlow(branchId) * reducedNet.getBaseMva();
				double diff = Math.abs(result.getPostFlowMW() - expectedPostMw);
				if (diff > maxDiff) {
					maxDiff = diff;
					maxDiffBranchId = branchId;
					maxCompensatedMw = result.getPostFlowMW();
					maxReducedMw = expectedPostMw;
				}
			}
			if (maxDiff >= 0.05) {
				mismatchSummary.append(busId)
						.append(" branch=").append(maxDiffBranchId)
						.append(" diff=").append(maxDiff)
						.append(" compensated=").append(maxCompensatedMw)
						.append(" reduced=").append(maxReducedMw)
						.append('\n');
			} else {
				accurateBusIds.add(busId);
			}
		}
		System.out.println("IEEE14 default anchored policy accurate bus islands=" + accurateBusIds);
		assertTrue(accurateBusIds.contains("Bus14"),
				"Expected Bus14 default anchored policy to match reduced-network DCLF");
		assertTrue(mismatchSummary.length() == 0,
				"Default anchored compensation policy mismatches:\n" + mismatchSummary);
	}

	@Test
	public void anchoredCompensationAccuracyAndPerformanceForIeee14BusIslands() throws InterpssException {
		CompensationComparison anchoredComparison =
				compareOneBusIslandTreatmentToReducedDclf(DclfIslandingTreatment.ANCHORED_COMPENSATE_ONE_BUS);
		System.out.println("IEEE14 anchored one-bus island compensation maxDiffMw="
				+ anchoredComparison.maxDiffMw
				+ ", maxDiffBus=" + anchoredComparison.maxDiffBus
				+ ", maxDiffBranch=" + anchoredComparison.maxDiffBranch
				+ ", accurateBusCount=" + anchoredComparison.accurateBusCount
				+ ", bus6MaxDiffMw=" + anchoredComparison.bus6MaxDiffMw
				+ ", perBusMaxDiffMw=" + anchoredComparison.busMaxDiffByBus);
		assertTrue(anchoredComparison.caseCount == 13, "Expected Bus2 through Bus14 island cases");
		assertTrue(anchoredComparison.maxDiffMw < 0.05,
				"Anchored compensation should match reduced-network DCLF for every non-reference IEEE14 bus. "
						+ anchoredComparison.busMaxDiffByBus);

		long anchoredNs = timeIeee14BusIslandSweep(
				DclfIslandingTreatment.ANCHORED_COMPENSATE_ONE_BUS,
				75);
		long replayNs = timeIeee14BusIslandSweep(
				DclfIslandingTreatment.FULL_DCLF_REPLAY,
				75);
		double anchoredMsPerCase = anchoredNs / 1_000_000.0 / (75.0 * 13.0);
		double replayMsPerCase = replayNs / 1_000_000.0 / (75.0 * 13.0);
		System.out.println("IEEE14 one-bus island performance ms/case: anchored="
				+ anchoredMsPerCase
				+ ", fullReplay=" + replayMsPerCase
				+ ", anchoredSpeedup=" + (replayMsPerCase / anchoredMsPerCase));
	}

	private static CompensationComparison compareOneBusIslandTreatmentToReducedDclf(
			DclfIslandingTreatment treatment)
			throws InterpssException {
		double maxDiff = 0.0;
		String maxDiffBusId = "";
		String maxDiffBranchId = "";
		int accurateBusCount = 0;
		int caseCount = 0;
		double bus6MaxDiff = 0.0;
		Map<String, Double> busMaxDiffByBus = new LinkedHashMap<>();
		for (int busNumber = 2; busNumber <= 14; busNumber++) {
			caseCount++;
			String busId = "Bus" + busNumber;
			AclfNetwork net = CorePluginFactory
					.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
					.load("testData/adpter/ieee_format/ieee14.ieee")
					.getAclfNet();
			ContingencyAnalysisAlgorithm dclfAlgo = DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm(net);
			assertTrue(dclfAlgo.calculateDclf(), "base DCLF failed for " + busId);

			List<String> outageBranchIds = activeIncidentBranchIds(net, busId);
			DclfMultiOutage contingency =
					DclfAlgoObjectFactory.createMultiOutageContingency(
							"OPEN:" + busId + "Island",
							ContingencyBranchOutageType.OPEN);
			for (String branchId : outageBranchIds) {
				contingency.getOutageEquips().add(outage(dclfAlgo, branchId));
			}
			Set<String> monitoredBranchIds = monitoredNonIncidentBranchIds(net, outageBranchIds);
			DclfContingencyConfig config = singleCoreIslandConfig(treatment);
			ConcurrentLinkedQueue<BranchCAResultRec> results =
					ParallelDclfContingencyAnalyzer.executeContingencyAnalysis(
							net,
							List.of(contingency),
							monitoredBranchIds,
							config,
							1);

			AclfNetwork reducedNet = CorePluginFactory
					.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
					.load("testData/adpter/ieee_format/ieee14.ieee")
					.getAclfNet();
			for (String branchId : outageBranchIds) {
				reducedNet.getBranch(branchId).setStatus(false);
			}
			reducedNet.getBus(busId).setStatus(false);
			deactivateBusesDisconnectedFromReference(reducedNet);
			ContingencyAnalysisAlgorithm reducedAlgo =
					DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm(reducedNet);
			assertTrue(reducedAlgo.calculateDclf(), "reduced-network DCLF failed for " + busId);

			double busMaxDiff = 0.0;
			for (BranchCAResultRec result : results) {
				String branchId = result.aclfBranch.getId();
				double expectedPostMw = reducedAlgo.getBranchFlow(branchId) * reducedNet.getBaseMva();
				double diff = Math.abs(result.getPostFlowMW() - expectedPostMw);
				busMaxDiff = Math.max(busMaxDiff, diff);
				if (diff > maxDiff) {
					maxDiff = diff;
					maxDiffBusId = busId;
					maxDiffBranchId = branchId;
				}
			}
			if (busMaxDiff < 0.05) {
				accurateBusCount++;
			}
			busMaxDiffByBus.put(busId, busMaxDiff);
			if ("Bus6".equals(busId)) {
				bus6MaxDiff = busMaxDiff;
			}
		}
		return new CompensationComparison(
				caseCount,
				accurateBusCount,
				maxDiff,
				maxDiffBusId,
				maxDiffBranchId,
				bus6MaxDiff,
				busMaxDiffByBus);
	}

	private static long timeIeee14BusIslandSweep(
			DclfIslandingTreatment treatment,
			int repetitions)
			throws InterpssException {
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/ieee14.ieee")
				.getAclfNet();
		ContingencyAnalysisAlgorithm dclfAlgo = DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm(net);
		assertTrue(dclfAlgo.calculateDclf());
		List<DclfMultiOutage> contingencies = new ArrayList<>();
		Set<String> monitoredBranchIds = new LinkedHashSet<>();
		for (int busNumber = 2; busNumber <= 14; busNumber++) {
			String busId = "Bus" + busNumber;
			List<String> outageBranchIds = activeIncidentBranchIds(net, busId);
			DclfMultiOutage contingency =
					DclfAlgoObjectFactory.createMultiOutageContingency(
							"OPEN:" + busId + "Island",
							ContingencyBranchOutageType.OPEN);
			for (String branchId : outageBranchIds) {
				contingency.getOutageEquips().add(outage(dclfAlgo, branchId));
			}
			contingencies.add(contingency);
			monitoredBranchIds.addAll(monitoredNonIncidentBranchIds(net, outageBranchIds));
		}
		DclfContingencyConfig config = singleCoreIslandConfig(treatment);
		ParallelDclfContingencyAnalyzer.executeContingencyAnalysis(
				net,
				List.copyOf(contingencies),
				monitoredBranchIds,
				config,
				1);
		long start = System.nanoTime();
		for (int i = 0; i < repetitions; i++) {
			ParallelDclfContingencyAnalyzer.executeContingencyAnalysis(
					net,
					List.copyOf(contingencies),
					monitoredBranchIds,
					config,
					1);
		}
		return System.nanoTime() - start;
	}

	private static DclfContingencyConfig singleCoreIslandConfig(DclfIslandingTreatment treatment) {
		DclfContingencyConfig config = new DclfContingencyConfig();
		config.setOverloadThreshold(0.0);
		config.setDclfInclLoss(false);
		config.setSolutionMethod(DclfContingencySolutionMethod.SparseEqnSolve);
		config.setIslandingTreatment(treatment);
		return config;
	}

	private static DclfOutageBranch outage(ContingencyAnalysisAlgorithm dclfAlgo, String branchId) {
		DclfOutageBranch outage = DclfAlgoObjectFactory.createCaOutageBranch(
				dclfAlgo.getDclfAlgoBranch(branchId),
				ContingencyBranchOutageType.OPEN);
		outage.setDclfFlow(dclfAlgo.getDclfAlgoBranch(branchId).getDclfFlow());
		return outage;
	}

	private static List<String> activeIncidentBranchIds(AclfNetwork net, String busId) {
		List<String> branchIds = new ArrayList<>();
		for (AclfBranch branch : net.getBranchList()) {
			if (branch.isActive()
					&& (branch.getFromBus().getId().equals(busId)
							|| branch.getToBus().getId().equals(busId))) {
				branchIds.add(branch.getId());
			}
		}
		return branchIds;
	}

	private static Set<String> monitoredNonIncidentBranchIds(AclfNetwork net, List<String> outageBranchIds) {
		Set<String> outageSet = new LinkedHashSet<>(outageBranchIds);
		Set<String> monitoredBranchIds = new LinkedHashSet<>();
		for (AclfBranch branch : net.getBranchList()) {
			if (branch.isActive()
					&& !branch.isConnect2RefBus()
					&& !outageSet.contains(branch.getId())) {
				monitoredBranchIds.add(branch.getId());
			}
		}
		return monitoredBranchIds;
	}

	private static void deactivateBusesDisconnectedFromReference(AclfNetwork net) {
		Set<String> connectedBusIds = activeReferenceComponentBusIds(net);
		for (Object rawBus : net.getBusList()) {
			if (rawBus instanceof com.interpss.core.net.Bus) {
				com.interpss.core.net.Bus bus = (com.interpss.core.net.Bus) rawBus;
				if (bus.isActive() && !connectedBusIds.contains(bus.getId())) {
					bus.setStatus(false);
				}
			}
		}
	}

	private static Set<String> activeReferenceComponentBusIds(AclfNetwork net) {
		Set<String> connectedBusIds = new LinkedHashSet<>();
		List<String> queue = new ArrayList<>();
		String refBusId = referenceBusId(net);
		if (refBusId == null) {
			return connectedBusIds;
		}
		if (net.getBus(refBusId) == null || !net.getBus(refBusId).isActive()) {
			return connectedBusIds;
		}
		queue.add(refBusId);
		for (int index = 0; index < queue.size(); index++) {
			String busId = queue.get(index);
			if (!connectedBusIds.add(busId)) {
				continue;
			}
			for (AclfBranch branch : net.getBranchList()) {
				if (!branch.isActive()
						|| branch.getFromBus() == null
						|| branch.getToBus() == null
						|| !branch.getFromBus().isActive()
						|| !branch.getToBus().isActive()) {
					continue;
				}
				String fromBusId = branch.getFromBus().getId();
				String toBusId = branch.getToBus().getId();
				if (fromBusId.equals(busId) && !connectedBusIds.contains(toBusId)) {
					queue.add(toBusId);
				} else if (toBusId.equals(busId) && !connectedBusIds.contains(fromBusId)) {
					queue.add(fromBusId);
				}
			}
		}
		return connectedBusIds;
	}

	private static String referenceBusId(AclfNetwork net) {
		for (Object rawBus : net.getBusList()) {
			if (rawBus instanceof com.interpss.core.net.Bus) {
				com.interpss.core.net.Bus bus = (com.interpss.core.net.Bus) rawBus;
				if (bus.isActive() && net.isRefBus(bus)) {
					return bus.getId();
				}
			}
		}
		return null;
	}

	private record CompensationComparison(
			int caseCount,
			int accurateBusCount,
			double maxDiffMw,
			String maxDiffBus,
			String maxDiffBranch,
			double bus6MaxDiffMw,
			Map<String, Double> busMaxDiffByBus) {
	}
}
