package org.interpss.optadj.ssaResult;

import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;

import java.util.Map;

import org.interpss.CorePluginFactory;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.datatype.AtomicCounter;
import org.interpss.numeric.datatype.LimitType;
import org.interpss.plugin.optadj.algo.AclfNetGenLoadOptimizer;
import org.interpss.plugin.optadj.algo.result.AclfNetSsaResultContainer;
import org.interpss.plugin.optadj.algo.result.BranchDclfResultRec;
import org.interpss.plugin.optadj.algo.result.BranchOptAdjustResultRec;

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
		AclfNetSsaResultContainer ssaResults = new AclfNetSsaResultContainer(true);

		ssaResults.setBasecaseThreshold(100.0);
		
		// check the branch loading
		double baseMVA = net.getBaseMva();
		AtomicCounter cnt = new AtomicCounter();
		dclfAlgo.getDclfAlgoBranchList().stream() 
			.forEach(dclfBranch -> {
				double flowMw = dclfBranch.getDclfFlow() * baseMVA;
				double loading = Math.abs(flowMw / dclfBranch.getBranch().getRatingMva1())*100;
				if (loading > ssaResults.getBasecaseThreshold()) {
					cnt.increment();
					// add the over limit branch to the SSA result container
					ssaResults.getBaseOverLimitInfo().add(new BranchOptAdjustResultRec(dclfBranch));
					System.out.println("Over Limit Branch: " + dclfBranch.getId() + "  " + flowMw +
							" rating: " + dclfBranch.getBranch().getRatingMva1() +
							" loading: " + loading);
//						container.getBaseOverLimitInfo().add(dclfBranch);
				}
			});
		System.out.println("Total number of branches over limit before OptAdj: " + cnt.getCount());
		
		// perform the Optimization adjustment
		AclfNetGenLoadOptimizer optimizer = new AclfNetGenLoadOptimizer(dclfAlgo);
		optimizer.optimize(ssaResults, ssaResults.getBasecaseThreshold(), true);
		
		Map<String, Double> resultMap = optimizer.getResultMap();
		System.out.println(resultMap);

		ssaResults.setOptAdjBaseResultMap(resultMap);
		
		System.out.println("Optimization gen size." + optimizer.getOptimizer().getGenSize());
		System.out.println("Optimization gen constrain size." + optimizer.getOptimizer().getGenConstrainDataList().size());
		System.out.println("Optimization sec constrian size." + optimizer.getOptimizer().getSecConstrainDataList().size());
		
		dclfAlgo.calculateDclf();
		
		// check the branch loading after the optimization adjustment
		Map<String, BranchOptAdjustResultRec> baseOverLimitInfoMap = ssaResults.toBaseOverLimitInfoMap();
		dclfAlgo.getDclfAlgoBranchList().stream()
			.forEach(dclfBranch -> {
				// update the adjusted flow and loading percent
				BranchOptAdjustResultRec rec = baseOverLimitInfoMap.get(dclfBranch.getId());
				if (rec != null) {
					rec.adjustedFlowMW = dclfBranch.getDclfFlow() * baseMVA;
					rec.adjustedLoadingPercent = Math.abs(rec.adjustedFlowMW / dclfBranch.getBranch().getRatingMva1())*100;
				}
			});	

		System.out.println(ssaResults.toString());
	}

	public static AclfNetwork createTestCase() throws InterpssException {
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("ipss.plugin.core/testData/adpter/ieee_format/ieee14.ieee")
				.getAclfNet();
		
		// set the branch rating.
		net.getBranchList().stream() 
			.forEach(branch -> {
				AclfBranch aclfBranch = (AclfBranch) branch;
				// Mva1 is used for basecase loading limit
				aclfBranch.setRatingMva1(120.0);
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
