
package org.interpss.optadj;

import static com.interpss.core.DclfAlgoObjectFactory.createCaOutageBranch;
import static com.interpss.core.DclfAlgoObjectFactory.createContingency;
import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;
import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.interpss.numeric.datatype.AtomicCounter;
import org.interpss.plugin.optadj.algo.AclfNetContigencyOptimizer;
import org.interpss.plugin.pssl.plugin.IpssAdapter;

import com.interpss.algo.parallel.ContingencyAnalysisMonad;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.algo.dclf.solver.IDclfSolver.CacheType;
import com.interpss.core.contingency.ContingencyBranchOutageType;
import com.interpss.core.contingency.dclf.DclfBranchOutage;
import com.interpss.core.contingency.dclf.DclfOutageBranch;

public class OptAdj_Texas2K_N1Scan_Sample {
	public static void main(String args[]) throws Exception {
		AclfNetwork aclfNet = IpssAdapter.importAclfNet("ipss.plugin.core/testData/psse/v36/Texas2k_series24_case1_2016summerPeak_v36.RAW")
				.setFormat(PSSE)
				.setPsseVersion(IpssAdapter.PsseVersion.PSSE_36) 
				.load()
				.getImportedObj();	
		  
		// define an caAlgo object and perform DCLF 
		ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(aclfNet, CacheType.SenNotCached, true);
		dclfAlgo.calculateDclf(DclfMethod.INC_LOSS);
		
		List<DclfBranchOutage> contList = new ArrayList<>();
		aclfNet.getBranchList().stream().filter(branch -> branch.isActive() && !branch.isXfr())
			.forEach(branch -> {
				// create a contingency object for the branch outage analysis
				DclfBranchOutage cont = createContingency("contBranch:"+branch.getId());
				// create an open CA outage branch object for the branch outage analysis
				DclfOutageBranch outage = createCaOutageBranch(dclfAlgo.getDclfAlgoBranch(branch.getId()), ContingencyBranchOutageType.OPEN);
				cont.setOutageEquip(outage);
				contList.add(cont);
			});
		
		AtomicCounter cnt = new AtomicCounter();
		contList.parallelStream()
			.forEach(contingency -> {
				ContingencyAnalysisMonad.of(dclfAlgo, contingency)
					.ca(resultRec -> {
						//System.out.println(resultRec.aclfBranch.getId() + 
						//		", " + resultRec.contingency.getId() +
						//		" postContFlow: " + resultRec.getPostFlowMW());
						double loading = resultRec.calLoadingPercent(resultRec.calBranchRateB());
						if (loading > 100.0) {
							cnt.increment();
							System.out.println(String.format(Locale.US,
									"OverLimit Branch: %s outage: %s postFlow: %.2f rating: %.2f loading: %.2f",
									resultRec.aclfBranch.getId(), resultRec.contingency.getId(),
									resultRec.getPostFlowMW(), resultRec.calBranchRateB(), loading));
						}
					});
			});
		System.out.println("Total number of branches over limit before OptAdj: " + cnt.getCount());
		 
		AclfNetContigencyOptimizer optimizer = new AclfNetContigencyOptimizer(dclfAlgo);
		optimizer.optimize(100);
		
		Map<String, Double> resultMap = optimizer.getResultMap();
		System.out.println("Optimization result: " + resultMap);
		
		System.out.println("Optimization gen size." + optimizer.getControlGenMap().size());
		System.out.println("Optimization gen constrain size." + optimizer.getOptimizer().getGenConstrainDataList().size());
		System.out.println("Optimization sec constrian size." + optimizer.getOptimizer().getSecConstrainDataList().size());
		
		dclfAlgo.calculateDclf();
		
		AtomicCounter cnt1 = new AtomicCounter();
		contList.parallelStream()
			.forEach(contingency -> {
				ContingencyAnalysisMonad.of(dclfAlgo, contingency)
					.ca(resultRec -> {
						//System.out.println(resultRec.aclfBranch.getId() + 
						//		", " + resultRec.contingency.getId() +
						//		" postContFlow: " + resultRec.getPostFlowMW());
						double loading = resultRec.calLoadingPercent(resultRec.calBranchRateB());
						if (loading > 100.0) {
							cnt1.increment();
							System.out.println(String.format(Locale.US,
									"Branch: %s outage: %s postFlow: %.2f rating: %.2f loading: %.2f",
									resultRec.aclfBranch.getId(), resultRec.contingency.getId(),
									resultRec.getPostFlowMW(), resultRec.calBranchRateB(),
									resultRec.calLoadingPercent()));
						}
					});
			});
		System.out.println("Total number of branches over limit after OptAdj: " + cnt1.getCount());
	}
}
