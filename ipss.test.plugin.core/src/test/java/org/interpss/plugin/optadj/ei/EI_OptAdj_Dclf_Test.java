package org.interpss.plugin.optadj.ei;

import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;
import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.interpss.CorePluginTestSetup;
import org.interpss.numeric.datatype.AtomicCounter;
import org.interpss.numeric.datatype.Counter;
import org.interpss.numeric.datatype.LimitType;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.PerformanceTimer;
import org.interpss.plugin.optadj.algo.AclfNetBusOptimizer;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.algo.dclf.solver.IDclfSolver.CacheType;

public class EI_OptAdj_Dclf_Test extends CorePluginTestSetup {
	static class DblBuffer {
		double val;
	}
	
	@Test
	public void test() throws InterpssException {
		// load the test data V33
		AclfNetwork aclfNet = IpssAdapter.importAclfNet("testData/psse/v33/Base_Eastern_Interconnect_515GW.RAW")
				.setFormat(PSSE)
				.setPsseVersion(IpssAdapter.PsseVersion.PSSE_33) 
				.load()
				.getImportedObj();	
		
		// set the generator Pgen limit
		aclfNet.createAclfGenNameLookupTable(false).forEach((k, gen) -> {
			//System.out.println("Adj Gen: " + gen.getName());
			if (gen.getPGenLimit() == null) {
				gen.setPGenLimit(new LimitType(5, 0));
			}
		});
		
		// define an caAlgo object and perform DCLF 
		ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(aclfNet, CacheType.SenNotCached, true);
		dclfAlgo.calculateDclf(DclfMethod.INC_LOSS);
		//System.out.println(DclfOutFunc.dclfResults(dclfAlgo, false));
		
		Counter cnt = new Counter(0);
		DblBuffer maxLoading = new DblBuffer();
		dclfAlgo.getDclfAlgoBranchList().forEach(braDclf -> {
			AclfBranch branch = braDclf.getBranch();

            double powerFlowMW = dclfAlgo.getBranchFlow(branch, UnitType.mW);
            double ratingMVA = branch.getRatingMva1();
            double loadingPercent = ratingMVA > 0 ? (Math.abs(powerFlowMW) / ratingMVA) * 100.0 : 0.0;
            if ( loadingPercent > 100.0) {
            	//System.out.println("Overloaded Branch: " + branch.getId() + ", Flow(MW): " + powerFlowMW + ", Rating(MVA): " + ratingMVA + ", Loading(%): " + loadingPercent);
            	cnt.increment();
            }
            if (loadingPercent > maxLoading.val) {
				maxLoading.val = loadingPercent;
			}
		});		
		System.out.println("Number of overloaded branches: " + cnt.getCount()); 
		System.out.println("Max loading percent: " + maxLoading.val);
		assertTrue(cnt.getCount() == 61);
		assertEquals(154.03, maxLoading.val, 0.01);
		
		PerformanceTimer timer = new PerformanceTimer();
		// perform the Optimization adjustment
		AclfNetBusOptimizer optimizer = new AclfNetBusOptimizer(dclfAlgo);
		optimizer.optimize(100);
		
		timer.log("Opt");
		
		Map<String, Double> resultMap = optimizer.getResultMap();
		//System.out.println(resultMap);
		
		//System.out.println("Optimization gen size: " + optimizer.getOptimizer().getGenSize());
		//System.out.println("Optimization gen constrain size: " + optimizer.getOptimizer().getGenConstrainDataList().size());
		//System.out.println("Optimization sec constrain size: " + optimizer.getOptimizer().getSecConstrainDataList().size());
	
		dclfAlgo.calculateDclf(DclfMethod.INC_LOSS);
		
		// check the branch loading after the optimization adjustment
		double baseMVA = aclfNet.getBaseMva();
		AtomicCounter cnt1 = new AtomicCounter();
		maxLoading.val = 0.0;
		dclfAlgo.getDclfAlgoBranchList().stream()
			.forEach(dclfBranch -> {
				double flowMw = dclfBranch.getDclfFlow() * baseMVA;
				double loading = Math.abs(flowMw / dclfBranch.getBranch().getRatingMva1())*100;
				if (loading > 100) {
					cnt1.increment();
					//System.out.println("Branch: " + dclfBranch.getId() + "  " + flowMw +
					//		" rating: " + dclfBranch.getBranch().getRatingMva1() +
					//		" loading: " + loading);
				}
	            if (loading > maxLoading.val) {
					maxLoading.val = loading;
				}
			});
		System.out.println("Total number of branches over limit after OptAdj: " + cnt1.getCount());
		System.out.println("Max loading percent: " + maxLoading.val);
		assertTrue(cnt1.getCount() == 35);
		assertEquals(124.65, maxLoading.val, 0.01);
	}
}
