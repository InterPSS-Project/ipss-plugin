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

import org.interpss.plugin.optadj.algo.util.Sen2DMatrix;

import org.interpss.plugin.pssl.plugin.IpssAdapter;



import com.interpss.core.aclf.AclfBranch;

import com.interpss.core.aclf.AclfNetwork;

import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;

import com.interpss.core.algo.dclf.DclfMethod;

import com.interpss.core.algo.dclf.solver.IDclfSolver.CacheType;



public class OptAdj_EInterconnect_Sample {

	static class DblBuffer {

		double val;

	}



	private static final String CASE_PATH = "ipss.plugin.core/testData/psse/v33/Base_Eastern_Interconnect_515GW.RAW";

	private static final double OPT_THRESHOLD = 100.0;



    public static void main(String args[]) throws Exception {

		AclfNetwork aclfNet = loadCase();

		ContingencyAnalysisAlgorithm dclfAlgo = createDclfAlgo(aclfNet);

		dclfAlgo.calculateDclf(DclfMethod.INC_LOSS);



		System.out.println("=== Base case overloads ===");

		printOverloadSummary(dclfAlgo, 100.0);



		runBusOptimization(dclfAlgo, aclfNet, true, "Gen-only");

		AclfNetwork aclfNetGenLoad = loadCase();
		ContingencyAnalysisAlgorithm dclfAlgoGenLoad = createDclfAlgo(aclfNetGenLoad);
		dclfAlgoGenLoad.calculateDclf(DclfMethod.INC_LOSS);
		runBusOptimization(dclfAlgoGenLoad, aclfNetGenLoad, false, "Gen+Load");

    }



	private static AclfNetwork loadCase() throws Exception {

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



	private static ContingencyAnalysisAlgorithm createDclfAlgo(AclfNetwork aclfNet) {

		return createContingencyAnalysisAlgorithm(aclfNet, CacheType.SenNotCached, true);

	}



	private static void printOverloadSummary(ContingencyAnalysisAlgorithm dclfAlgo, double thresholdPercent) {

		Counter cnt = new Counter(0);

		DblBuffer maxLoading = new DblBuffer();

		dclfAlgo.getDclfAlgoBranchList().forEach(braDclf -> {

			AclfBranch branch = braDclf.getBranch();

			double powerFlowMW = dclfAlgo.getBranchFlow(branch, UnitType.mW);

			double ratingMVA = branch.getRatingMvaA();

			double loadingPercent = ratingMVA > 0 ? (Math.abs(powerFlowMW) / ratingMVA) * 100.0 : 0.0;

			if (loadingPercent > thresholdPercent) {

				cnt.increment();

			}

			if (loadingPercent > maxLoading.val) {

				maxLoading.val = loadingPercent;

			}

		});

		System.out.println("Number of overloaded branches: " + cnt.getCount());

		System.out.println("Max loading percent: " + maxLoading.val);

	}



	private static void runBusOptimization(ContingencyAnalysisAlgorithm dclfAlgo, AclfNetwork aclfNet,

			boolean adjustGenOnly, String label) throws Exception {

		System.out.println();

		System.out.println("=== " + label + " bus optimization ===");

		dclfAlgo.calculateDclf(DclfMethod.INC_LOSS);



		PerformanceTimer timer = new PerformanceTimer();

		AclfNetBusOptimizer optimizer = new AclfNetBusOptimizer(dclfAlgo);

		optimizer.optimize(OPT_THRESHOLD, adjustGenOnly);

		timer.log("Opt-" + label);



		System.out.println("Control bus count: " + optimizer.getControlBusIdSet().size());

		System.out.println("Control load bus count: " + optimizer.getControlLoadBusIdSet().size());

		if (!optimizer.getControlLoadBusIdSet().isEmpty()) {

			//System.out.println("Control load bus ids: " + optimizer.getControlLoadBusIdSet());

		}



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

		Sen2DMatrix controlBusGfsMatrix = controlBusIdSet.isEmpty()

				? null

				: optimizer.getGFSsHelper().calGFS(controlBusIdSet);



		AtomicCounter cnt1 = new AtomicCounter();

		DblBuffer maxLoading = new DblBuffer();

		dclfAlgo.getDclfAlgoBranchList().forEach(dclfBranch -> {

			double flowMw = dclfBranch.getDclfFlow() * baseMVA;

			double rating = dclfBranch.getBranch().getRatingMvaA();

			double loading = rating > 0 ? Math.abs(flowMw / rating) * 100 : 0.0;

			if (loading > 100) {

				cnt1.increment();

				int branchNo = dclfBranch.getBranch().getSortNumber();

				double maxAbsGfs = controlBusGfsMatrix == null ? 0.0

						: controlBusIdSet.stream()

								.mapToDouble(busId -> {

									int busNo = aclfNet.getBus(busId).getSortNumber();

									return Math.abs(controlBusGfsMatrix.get(busNo, branchNo));

								})

								.max()

								.orElse(0.0);

				System.out.printf("Branch: %s  %.2f rating: %.2f loading: %.2f  max |GFS|: %.2f%n",

						dclfBranch.getId(), flowMw, rating, loading, maxAbsGfs);

			}

			if (loading > maxLoading.val) {

				maxLoading.val = loading;

			}

		});

		System.out.println("Total number of branches over limit after OptAdj: " + cnt1.getCount());

		System.out.println("Max loading percent: " + maxLoading.val);

	}

}

