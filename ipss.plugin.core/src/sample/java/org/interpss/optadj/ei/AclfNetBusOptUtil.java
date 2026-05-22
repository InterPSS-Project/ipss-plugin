package org.interpss.optadj.ei;

import java.util.Map;
import java.util.Set;

import org.interpss.numeric.datatype.AtomicCounter;
import org.interpss.numeric.datatype.Counter;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.PerformanceTimer;
import org.interpss.plugin.optadj.algo.AclfNetBusOptimizer;
import org.interpss.plugin.optadj.algo.result.AclfNetSsaResultContainer;
import org.interpss.plugin.optadj.algo.result.BranchOptAdjustResultRec;
import org.interpss.plugin.optadj.algo.util.Sen2DMatrix;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.DclfMethod;

public class AclfNetBusOptUtil {
	static class DblBuffer {
		double val;
	}
	
	static void printOverloadSummary(ContingencyAnalysisAlgorithm dclfAlgo, double thresholdPercent) {
		printOverloadSummary(dclfAlgo, thresholdPercent, null);
	}

	static void printOverloadSummary(ContingencyAnalysisAlgorithm dclfAlgo, double thresholdPercent, AclfNetSsaResultContainer ssaResults) {
		Counter cnt = new Counter(0);
		DblBuffer maxLoading = new DblBuffer();
		dclfAlgo.getDclfAlgoBranchList().forEach(dclfBranch -> {
			AclfBranch branch = dclfBranch.getBranch();
			double powerFlowMW = dclfAlgo.getBranchFlow(branch, UnitType.mW);
			double ratingMVA = branch.getRatingMvaA();
			double loadingPercent = ratingMVA > 0 ? (Math.abs(powerFlowMW) / ratingMVA) * 100.0 : 0.0;
			if (loadingPercent > thresholdPercent) {
				cnt.increment();
				// add the over limit branch to the SSA result container
				if (ssaResults != null)
					ssaResults.getBaseOverLimitInfo().add(new BranchOptAdjustResultRec(dclfBranch));
				System.out.printf("Branch: %s  %.2f  rating: %.2f  loading: %.2f%n",
				dclfBranch.getId(), powerFlowMW, ratingMVA, loadingPercent);
			}
			if (loadingPercent > maxLoading.val) {
				maxLoading.val = loadingPercent;
			}
		});
		System.out.println("Number of overloaded branches: " + cnt.getCount());
		System.out.println("Max loading percent: " + maxLoading.val);
	}

	static void runBusOptimization(ContingencyAnalysisAlgorithm dclfAlgo, AclfNetwork aclfNet, double OPT_THRESHOLD,
		                           boolean adjustGenOnly, String label) throws Exception {
		runBusOptimization(dclfAlgo, aclfNet, OPT_THRESHOLD, adjustGenOnly, label, null);
	}
  
	static void runBusOptimization(ContingencyAnalysisAlgorithm dclfAlgo, AclfNetwork aclfNet, double OPT_THRESHOLD,
			                       boolean adjustGenOnly, String label, AclfNetSsaResultContainer ssaResults) throws Exception {
		System.out.println();
		System.out.println("=== " + label + " bus optimization ===");

		PerformanceTimer timer = new PerformanceTimer();
		AclfNetBusOptimizer optimizer = new AclfNetBusOptimizer(dclfAlgo);
		optimizer.optimize(OPT_THRESHOLD, adjustGenOnly);
		timer.log("Opt-" + label);

		System.out.println("Control bus count: " + optimizer.getControlBusIdSet().size());
		System.out.println("Control load bus count: " + optimizer.getControlLoadBusIdSet().size());

		Map<String, Double> resultMap = optimizer.getResultMap();
		System.out.println("Optimization result: " + resultMap);
		System.out.println("Optimization variable size: " + optimizer.getOptimizer().getGenSize());
		System.out.println("Optimization device constrain size: "
				+ optimizer.getOptimizer().getGenConstrainDataList().size());
		System.out.println("Optimization sec constrain size: "
				+ optimizer.getOptimizer().getSecConstrainDataList().size());

		if (ssaResults != null)
			ssaResults.setOptAdjBaseResultMap(resultMap);		

		dclfAlgo.calculateDclf(DclfMethod.INC_LOSS);

		double baseMVA = aclfNet.getBaseMva();
		Set<String> controlBusIdSet = optimizer.getControlBusIdSet();
		Map<String, AclfNetBusOptimizer.ControlBusRole> controlBusRoleMap = optimizer.getControlBusRoleMap();
		Sen2DMatrix controlBusGfsMatrix = controlBusIdSet.isEmpty()
				? null
				: optimizer.getGFSsHelper().calGFS(controlBusIdSet);

		// check the branch loading after the optimization adjustment
		Map<String, BranchOptAdjustResultRec> baseOverLimitInfoMap = ssaResults != null ? ssaResults.toBaseOverLimitInfoMap() : null;
		AtomicCounter cnt1 = new AtomicCounter();
		DblBuffer maxLoading = new DblBuffer();
		dclfAlgo.getDclfAlgoBranchList().forEach(dclfBranch -> {
			double flowMw = dclfBranch.getDclfFlow() * baseMVA;
			double rating = dclfBranch.getBranch().getRatingMvaA();
			double loading = rating > 0 ? Math.abs(flowMw / rating) * 100 : 0.0;
			if (loading > OPT_THRESHOLD) {
				cnt1.increment();
				int branchNo = dclfBranch.getBranch().getSortNumber();
				double maxAbsGenGfs = controlBusGfsMatrix == null ? 0.0
						: controlBusIdSet.stream()
								.mapToDouble(busId -> {
									int busNo = aclfNet.getBus(busId).getSortNumber();
									return controlBusRoleMap.get(busId) == AclfNetBusOptimizer.ControlBusRole.GEN ? 
									Math.abs(controlBusGfsMatrix.get(busNo, branchNo)) : 0.0;
								})
								.max()
								.orElse(0.0);
				double maxAbsLoadGfs = controlBusGfsMatrix == null || adjustGenOnly? 0.0
						: controlBusIdSet.stream()
								.mapToDouble(busId -> {
									int busNo = aclfNet.getBus(busId).getSortNumber();
									return controlBusRoleMap.get(busId) == AclfNetBusOptimizer.ControlBusRole.LOAD ? 
									Math.abs(controlBusGfsMatrix.get(busNo, branchNo)) : 0.0;
								})
								.max()
								.orElse(0.0);				

				System.out.printf("Branch: %s  %.2f  rating: %.2f  loading: %.2f  max |GenGFS|: %.2f",
						dclfBranch.getId(), flowMw, rating, loading, maxAbsGenGfs);
				if (adjustGenOnly)
					System.out.printf("%n");
				else
					System.out.printf("  max |LoadGFS|: %.2f%n", maxAbsLoadGfs);		
			}
			if (loading > maxLoading.val) {
				maxLoading.val = loading;
			}

			// update the adjusted flow and loading percent
			if (ssaResults != null) {
				BranchOptAdjustResultRec rec = baseOverLimitInfoMap.get(dclfBranch.getId());
				if (rec != null) {
					rec.adjustedFlowMW = dclfBranch.getDclfFlow() * baseMVA;
					rec.adjustedLoadingPercent = Math.abs(rec.adjustedFlowMW / dclfBranch.getBranch().getRatingMva1())*100;
				}
			}
		});
		System.out.println("Total number of branches over limit after OptAdj: " + cnt1.getCount());
		System.out.println("Max loading percent: " + maxLoading.val);
	}
}
