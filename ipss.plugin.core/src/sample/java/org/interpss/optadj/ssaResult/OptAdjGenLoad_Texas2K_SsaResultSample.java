package org.interpss.optadj.ssaResult;

import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;
import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.interpss.numeric.datatype.AtomicCounter;
import org.interpss.numeric.datatype.Counter;
import org.interpss.numeric.datatype.LimitType;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.PerformanceTimer;
import org.interpss.plugin.optadj.algo.AclfNetGenLoadOptimizer;
import org.interpss.plugin.optadj.algo.util.AclfNetGFSsHelper;
import org.interpss.plugin.optadj.algo.util.Sen2DMatrix;
import org.interpss.plugin.pssl.plugin.IpssAdapter;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.algo.dclf.solver.IDclfSolver.CacheType;

public class OptAdjGenLoad_Texas2K_SsaResultSample {
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
		AclfNetGenLoadOptimizer optimizer = new AclfNetGenLoadOptimizer(dclfAlgo);
		optimizer.optimize(loadingThreshold, false);
		
		timer.log("Opt");

		System.out.println("Optimization gen result: " + optimizer.getResultGenMap());
		System.out.println("Optimization load result: " + optimizer.getResultLoadMap());
		
		System.out.println("Optimization gen/load size: " + optimizer.getOptimizer().getGenSize());
		System.out.println("Optimization gen/load constrain size: " + optimizer.getOptimizer().getGenConstrainDataList().size());
		System.out.println("Optimization sec constrain size: " + optimizer.getOptimizer().getSecConstrainDataList().size());
	
		// Dclf after the optimization, Dclf gen has been adjusted
		dclfAlgo.calculateDclf(DclfMethod.INC_LOSS);
		
		// check the branch loading after the optimization adjustment
		double baseMVA = aclfNet.getBaseMva();
		
		AtomicCounter cnt1 = new AtomicCounter();
		maxLoading.val = 0.0;
		dclfAlgo.getDclfAlgoBranchList().stream()
			.forEach(dclfBranch -> {
				double flowMw = dclfBranch.getDclfFlow() * baseMVA;
				double loading = Math.abs(flowMw / dclfBranch.getBranch().getRatingMvaA())*100;
				if (loading > loadingThreshold) {
					cnt1.increment();
					System.out.printf("Branch: %s  %.2f rating: %.2f loading: %.2f%n",
							dclfBranch.getId(), flowMw, dclfBranch.getBranch().getRatingMvaA(), loading);
				}
	            if (loading > maxLoading.val) {
					maxLoading.val = loading;
				}
			});
		System.out.println("Total number of branches over limit after OptAdj: " + cnt1.getCount());
		System.out.println("Max loading percent: " + maxLoading.val);
    }
}
