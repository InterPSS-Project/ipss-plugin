package org.interpss.optadj;

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
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.algo.dclf.solver.IDclfSolver.CacheType;

public class OptAdj_Texas2K_Sample {
	static class DblBuffer {
		double val;
	}
    public static void main(String args[]) throws Exception {
		// load the test data V33
		AclfNetwork aclfNet = IpssAdapter.importAclfNet("ipss.plugin.core/testData/psse/v36/Texas2k_series24_case1_2016summerPeak_v36.RAW")
				.setFormat(PSSE)
				.setPsseVersion(IpssAdapter.PsseVersion.PSSE_36) 
				.load()
				.getImportedObj();	
		
		// define an caAlgo object and perform DCLF 
		ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(aclfNet, CacheType.SenNotCached, true);
		dclfAlgo.calculateDclf(DclfMethod.INC_LOSS);
		//System.out.println(DclfOutFunc.dclfResults(dclfAlgo, false));
		
		double loadingThreshold = 90.0;

		Counter cnt = new Counter(0);
		DblBuffer maxLoading = new DblBuffer();
		dclfAlgo.getDclfAlgoBranchList().forEach(braDclf -> {
			AclfBranch branch = braDclf.getBranch();

            double powerFlowMW = dclfAlgo.getBranchFlow(branch, UnitType.mW);
            double ratingMVA = branch.getRatingMvaA();
            double loadingPercent = ratingMVA > 0 ? (Math.abs(powerFlowMW) / ratingMVA) * 100.0 : 0.0;
            if ( loadingPercent > loadingThreshold) {
            	System.out.printf("Overloaded Branch: %s, Flow(MW): %.2f, Rating(MVA): %.2f, Loading(%%): %.2f%n",
            			branch.getId(), powerFlowMW, ratingMVA, loadingPercent);
            	cnt.increment();
            }
            if (loadingPercent > maxLoading.val) {
				maxLoading.val = loadingPercent;
			}
		});		
		System.out.println("Number of overloaded branches: " + cnt.getCount()); 
		System.out.println("Max loading percent: " + maxLoading.val);
		
		PerformanceTimer timer = new PerformanceTimer();
		// perform the Optimization adjustment
		AclfNetBusOptimizer optimizer = new AclfNetBusOptimizer(dclfAlgo);
		optimizer.optimize(loadingThreshold);
		
		timer.log("Opt");
		
		Map<String, Double> resultMap = optimizer.getResultMap();
		System.out.println("Optimization result: " + resultMap);
		
		System.out.println("Optimization gen size: " + optimizer.getOptimizer().getGenSize());
		System.out.println("Optimization gen constrain size: " + optimizer.getOptimizer().getGenConstrainDataList().size());
		System.out.println("Optimization sec constrain size: " + optimizer.getOptimizer().getSecConstrainDataList().size());
	
		// Dclf after the optimization, Dclf gen has been adjusted
		dclfAlgo.calculateDclf(DclfMethod.INC_LOSS);
		
		// check the branch loading after the optimization adjustment
		double baseMVA = aclfNet.getBaseMva();
		Set<String> controlBusIdSet = optimizer.getControlBusIdSet();
		Sen2DMatrix controlBusGfsMatrix = controlBusIdSet.isEmpty() ? null : optimizer.getGFSsHelper().calGFS(controlBusIdSet);
		
		AtomicCounter cnt1 = new AtomicCounter();
		maxLoading.val = 0.0;
		dclfAlgo.getDclfAlgoBranchList().stream()
			.forEach(dclfBranch -> {
				double flowMw = dclfBranch.getDclfFlow() * baseMVA;
				double loading = Math.abs(flowMw / dclfBranch.getBranch().getRatingMvaA())*100;
				if (loading > loadingThreshold) {
					cnt1.increment();
					int branchNo = dclfBranch.getBranch().getSortNumber();
					double maxAdjustableGenP = controlBusGfsMatrix == null ? 0.0
							: controlBusIdSet.stream()
									.mapToDouble(busId -> {
										AclfBus bus = (AclfBus) aclfNet.getBus(busId);
										int busNo = bus.getSortNumber();
										double genP = bus.getGenP() * baseMVA;
										LimitType genLimit = bus.getPGenLimit();
										double gfs = controlBusGfsMatrix.get(busNo, branchNo);
										/*
										 * Adjustable Gen P = GFS * (Gen Limit - Gen P)
										 * if GFS > 0, we can increase Gen P
										 * if GFS < 0, we can decrease Gen P
										 */
										double adjustableGenP = gfs > 0 ? genLimit.getMax() - genP : genP - genLimit.getMin();
										return Math.abs(gfs * (adjustableGenP));
									})
									.max()
									.orElse(0.0);
					System.out.printf("Branch: %s  %.2f rating: %.2f loading: %.2f  max |Adj|: %.2f%n",
							dclfBranch.getId(), flowMw, dclfBranch.getBranch().getRatingMvaA(), loading, maxAdjustableGenP);
				}
	            if (loading > maxLoading.val) {
					maxLoading.val = loading;
				}
			});
		System.out.println("Total number of branches over limit after OptAdj: " + cnt1.getCount());
		System.out.println("Max loading percent: " + maxLoading.val);
    }
}
