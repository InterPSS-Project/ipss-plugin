package org.interpss.optadj;

import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;

import java.util.Map;

import org.interpss.CorePluginFactory;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.datatype.AtomicCounter;
import org.interpss.numeric.datatype.LimitType;
import org.interpss.plugin.optadj.algo.AclfNetGenLoadOptimizer;
import org.interpss.plugin.optadj.algo.result.AclfNetSsaResultContainer;
import org.interpss.plugin.optadj.algo.result.BranchDclfResultRec;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;

public class IEEE14_OptAdj_BasecaseSSAResult_Sample {
    public static void main(String args[]) throws Exception {
		AclfNetwork net = createTestCase();
		
		// define an caAlgo object and perform DCLF 
		ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(net);
		dclfAlgo.calculateDclf();
		

		// defined a SSA result container
		AclfNetSsaResultContainer ssaResults = new AclfNetSsaResultContainer();
		
		// check the branch loading
		double baseMVA = net.getBaseMva();
		AtomicCounter cnt = new AtomicCounter();
		dclfAlgo.getDclfAlgoBranchList().stream() 
			.forEach(dclfBranch -> {
				double flowMw = dclfBranch.getDclfFlow() * baseMVA;
				double loading = Math.abs(flowMw / dclfBranch.getBranch().getRatingMva1())*100;
				if (loading > 100.0) {
					cnt.increment();
					// add the over limit branch to the SSA result container
					ssaResults.getBaseOverLimitInfo().add(new BranchDclfResultRec(dclfBranch));
					System.out.println("Over Limit Branch: " + dclfBranch.getId() + "  " + flowMw +
							" rating: " + dclfBranch.getBranch().getRatingMva1() +
							" loading: " + loading);
//						container.getBaseOverLimitInfo().add(dclfBranch);
				}
			});
		System.out.println("Total number of branches over limit before OptAdj: " + cnt.getCount());
		
		// perform the Optimization adjustment
		AclfNetGenLoadOptimizer optimizer = new AclfNetGenLoadOptimizer(dclfAlgo);
		optimizer.optimize(ssaResults, 100, true);
		
		Map<String, Double> resultMap = optimizer.getResultMap();
		System.out.println(resultMap);
		
		System.out.println("Optimization gen size." + optimizer.getOptimizer().getGenSize());
		System.out.println("Optimization gen constrain size." + optimizer.getOptimizer().getGenConstrainDataList().size());
		System.out.println("Optimization sec constrian size." + optimizer.getOptimizer().getSecConstrainDataList().size());
		
		dclfAlgo.calculateDclf();
		
		// check the branch loading after the optimization adjustment
		Map<String, BranchDclfResultRec> baseOverLimitInfoMap = ssaResults.toBaseOverLimitInfoMap();
		dclfAlgo.getDclfAlgoBranchList().stream()
			.forEach(dclfBranch -> {
				BranchDclfResultRec rec = baseOverLimitInfoMap.get(dclfBranch.getId());
				if (rec != null) {
					double originalFlowMw = rec.mwFlow;
					double originalLoading = rec.loadingPercent;
					double flowMw = dclfBranch.getDclfFlow() * baseMVA;
					double loading = Math.abs(flowMw / dclfBranch.getBranch().getRatingMva1())*100;
					System.out.println(String.format("Branch: %s flowMw(after): %.2f flowMw(original): %.2f rating: %.2f loading%%(after): %.2f loading%%(original): %.2f",
							dclfBranch.getId(), flowMw, originalFlowMw,
							dclfBranch.getBranch().getRatingMva1(), loading, originalLoading));
					}
				});
	}

	private static AclfNetwork createTestCase() throws InterpssException {
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("ipss.plugin.core/testData/adpter/ieee_format/ieee14.ieee")
				.getAclfNet();
		
		// set the branch rating.
		net.getBranchList().stream() 
			.forEach(branch -> {
				AclfBranch aclfBranch = (AclfBranch) branch;
				// Mva1 is used for basecase loading limit
				aclfBranch.setRatingMva1(100.0);
				// Mva2 is used for contingency loading limit
				aclfBranch.setRatingMva2(120.0);
			});
		
		// set the generator Pgen limit
		net.createAclfGenNameLookupTable(false).forEach((k, gen) -> {
			//System.out.println("Adj Gen: " + gen.getName());
			if (gen.getPGenLimit() == null) {
				gen.setPGenLimit(new LimitType(5, 0));
			}
		});
		
		return net;
	}
}
