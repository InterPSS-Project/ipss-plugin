package org.interpss.optadj.ei;

import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;
import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;

import java.util.Map;
import java.util.Set;

import org.interpss.numeric.datatype.AtomicCounter;
import org.interpss.numeric.datatype.Counter;
import org.interpss.numeric.datatype.LimitType;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.PerformanceTimer;
import org.interpss.plugin.optadj.algo.AclfNetBusOptimizer;
import org.interpss.plugin.optadj.algo.AclfNetGenLoadOptimizer;
import org.interpss.plugin.optadj.algo.util.Sen2DMatrix;
import org.interpss.plugin.pssl.plugin.IpssAdapter;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.algo.dclf.solver.IDclfSolver.CacheType;

public class EInterconnect_Info_Sample {
	static class DblBuffer {
		double val;
	}
	
	static final String CASE_PATH = "ipss.plugin.core/testData/psse/v33/Base_Eastern_Interconnect_515GW.RAW";
	static final double OPT_THRESHOLD = 100.0;

    public static void main(String args[]) throws Exception {
		// load the test data V33
		AclfNetwork aclfNet = loadCase();
		
		Counter zbrCnt = new Counter();
		Counter lineCnt = new Counter();
		Counter lineXSubCnt = new Counter();
		aclfNet.getBranchList().forEach(branch -> {
			if (branch.isZeroZBranch()) {
				zbrCnt.increment();
				System.out.println("Zero Impedance Branch: " + branch.getId() + 
						" From Bus: " + branch.getFromBus().getId() + 
						" To Bus: " + branch.getToBus().getId());
			}
			
			if (branch.isLine()) {
				lineCnt.increment();
			}
			
			if (branch.isLine() && branch.getFromAclfBus().getSubstationId().equals(branch.getToAclfBus().getSubstationId())) {
				lineXSubCnt.increment();
				if (lineXSubCnt.getCount() < 10) {
					System.out.println("Line connecting buses in the same substation: " + branch.getId() + 
							" From Sus: " + branch.getFromBus().getSubstation().getId() + 
							" To Sus: " + branch.getToBus().getSubstation().getId() +
							" Substation ID: " + branch.getFromAclfBus().getSubstationId());
				}
			}
			
		});
		System.out.println("Total number of Zero Impedance Branches: " + zbrCnt.getCount());
		System.out.println("Total number of Lines: " + lineCnt.getCount());
		System.out.println("Total number of Lines connecting buses in the same substation: " + lineXSubCnt.getCount());
		
		// set the generator Pgen limit
		aclfNet.createAclfGenNameLookupTable(false).forEach((k, gen) -> {
			//System.out.println("Adj Gen: " + gen.getName());
			if (gen.getPGenLimit() == null) {
				gen.setPGenLimit(new LimitType(5, 0));
			}
		});
	}

	static AclfNetwork loadCase() throws Exception {
		AclfNetwork aclfNet = IpssAdapter.importAclfNet(CASE_PATH)
				.setFormat(PSSE)
				.setPsseVersion(IpssAdapter.PsseVersion.PSSE_33)
				.load()
				.getImportedObj();
		aclfNet.createAclfGenNameLookupTable(false).forEach((k, gen) -> {
			if (gen.getPGenLimit() == null) {
				gen.setPGenLimit(new LimitType(5, 0));
			}
		});
		return aclfNet;
	}

	static ContingencyAnalysisAlgorithm createDclfAlgo(AclfNetwork aclfNet) {
		return createContingencyAnalysisAlgorithm(aclfNet, CacheType.SenNotCached, true);
	}

	static void printOverloadSummary(ContingencyAnalysisAlgorithm dclfAlgo, double thresholdPercent) {
		Counter cnt = new Counter(0);
		DblBuffer maxLoading = new DblBuffer();
		dclfAlgo.getDclfAlgoBranchList().forEach(dclfBranch -> {
			AclfBranch branch = dclfBranch.getBranch();
			double powerFlowMW = dclfAlgo.getBranchFlow(branch, UnitType.mW);
			double ratingMVA = branch.getRatingMvaA();
			double loadingPercent = ratingMVA > 0 ? (Math.abs(powerFlowMW) / ratingMVA) * 100.0 : 0.0;
			if (loadingPercent > thresholdPercent) {
				cnt.increment();
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

	static void runBusOptimization(ContingencyAnalysisAlgorithm dclfAlgo, AclfNetwork aclfNet,
			boolean adjustGenOnly, String label) throws Exception {
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

		dclfAlgo.calculateDclf(DclfMethod.INC_LOSS);

		double baseMVA = aclfNet.getBaseMva();
		Set<String> controlBusIdSet = optimizer.getControlBusIdSet();
		Map<String, AclfNetBusOptimizer.ControlBusRole> controlBusRoleMap = optimizer.getControlBusRoleMap();
		Sen2DMatrix controlBusGfsMatrix = controlBusIdSet.isEmpty()
				? null
				: optimizer.getGFSsHelper().calGFS(controlBusIdSet);

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
		});
		System.out.println("Total number of branches over limit after OptAdj: " + cnt1.getCount());
		System.out.println("Max loading percent: " + maxLoading.val);
	}
}
