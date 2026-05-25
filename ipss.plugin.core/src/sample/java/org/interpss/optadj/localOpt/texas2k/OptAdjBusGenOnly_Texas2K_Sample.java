package org.interpss.optadj.localOpt.texas2k;

import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;
import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;

import org.interpss.optadj.localOpt.AclfNetBusOptUtil;
import org.interpss.plugin.pssl.plugin.IpssAdapter;

import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.algo.dclf.solver.IDclfSolver.CacheType;

public class OptAdjBusGenOnly_Texas2K_Sample {
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

				ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(aclfNet, CacheType.SenNotCached, true);
				dclfAlgo.calculateDclf(DclfMethod.INC_LOSS);
		
				System.out.println("=== Base case overloads ===");
				AclfNetBusOptUtil.printOverloadSummary(dclfAlgo, 90.0);
		
				AclfNetBusOptUtil.runBusOptimization(dclfAlgo, aclfNet, 90.0, true, "Gen-only");
					
    }
}
