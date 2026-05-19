package org.interpss.optadj.ssaResult;

import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;
import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;

import java.util.Map;
import org.interpss.numeric.datatype.AtomicCounter;
import org.interpss.numeric.datatype.Counter;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.PerformanceTimer;
import org.interpss.plugin.optadj.algo.AclfNetGenLoadOptimizer;
import org.interpss.plugin.optadj.algo.result.AclfNetSsaResultContainer;
import org.interpss.plugin.optadj.algo.result.BranchOptAdjustResultRec;
import org.interpss.plugin.pssl.plugin.IpssAdapter;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.algo.dclf.solver.IDclfSolver.CacheType;

public class OptAdjGenOnly_Texas2K_SsaResultSample {
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
		
		// set the basecase loading threshold
		double loadingThreshold = 90.0;

		// defined a SSA result container
		AclfNetSsaResultContainer ssaResults = new AclfNetSsaResultContainer(true);

		ssaResults.setBasecaseThreshold(loadingThreshold);

		dclfAlgo.getDclfAlgoBranchList().forEach(braDclf -> {
			AclfBranch branch = braDclf.getBranch();

            double powerFlowMW = dclfAlgo.getBranchFlow(branch, UnitType.mW);
            double ratingMVA = branch.getRatingMvaA();
            double loadingPercent = ratingMVA > 0 ? (Math.abs(powerFlowMW) / ratingMVA) * 100.0 : 0.0;
            if ( loadingPercent > loadingThreshold) {
				// add the basecaseover limit branch to the SSA result container
				ssaResults.getBaseOverLimitInfo().add(new BranchOptAdjustResultRec(braDclf));
            	System.out.printf("Overloaded Branch: %s, Flow(MW): %.2f, Rating(MVA): %.2f, Loading(%%): %.2f%n",
            			branch.getId(), powerFlowMW, ratingMVA, loadingPercent);
            }
		});		
		
		PerformanceTimer timer = new PerformanceTimer();
		// perform the Optimization adjustment
		AclfNetGenLoadOptimizer optimizer = new AclfNetGenLoadOptimizer(dclfAlgo);
		optimizer.optimize(ssaResults, loadingThreshold, true);
		
		timer.log("Opt");
		
		Map<String, Double> resultMap = optimizer.getResultGenMap();
		System.out.println("Optimization gen result: " + resultMap);

		// set the optimization gen/load result map to the SSA result container
		ssaResults.setOptAdjBaseResultMap(resultMap);
		
		System.out.println("Optimization gen size: " + optimizer.getOptimizer().getGenSize());
		System.out.println("Optimization gen constrain size: " + optimizer.getOptimizer().getGenConstrainDataList().size());
		System.out.println("Optimization sec constrain size: " + optimizer.getOptimizer().getSecConstrainDataList().size());
	
		// Dclf after the optimization, Dclf gen has been adjusted
		dclfAlgo.calculateDclf(DclfMethod.INC_LOSS);
		
		// update the branch loading after the optimization adjustment
		double baseMVA = aclfNet.getBaseMva();
		Map<String, BranchOptAdjustResultRec> baseOverLimitInfoMap = ssaResults.toBaseOverLimitInfoMap();
		dclfAlgo.getDclfAlgoBranchList().stream()
			.forEach(dclfBranch -> {
				// update the adjusted flow and loading percent
				BranchOptAdjustResultRec rec = baseOverLimitInfoMap.get(dclfBranch.getId());
				if (rec != null) {
					// update the adjusted flow and loading percent after the optimization adjustment
					rec.adjustedFlowMW = dclfBranch.getDclfFlow() * baseMVA;
					rec.adjustedLoadingPercent = Math.abs(rec.adjustedFlowMW / dclfBranch.getBranch().getRatingMvaA())*100;
				}
			});

		System.out.println(ssaResults.toString());
    }
}
