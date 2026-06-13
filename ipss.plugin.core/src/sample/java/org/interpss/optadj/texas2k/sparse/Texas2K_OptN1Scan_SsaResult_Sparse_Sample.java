package org.interpss.optadj.texas2k.sparse;

import static com.interpss.core.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm;
import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.interpss.numeric.datatype.AtomicCounter;
import org.interpss.optadj.texas2k.Texas2K_Sample_Info;
import org.interpss.plugin.contingency.definition.BranchContingencyRecord;
import org.interpss.plugin.contingency.definition.MonitoredBranchRecord;
import org.interpss.plugin.contingency.util.ContingencyFileUtil;
import org.interpss.plugin.contingency.util.DclfContingencyHelper;
import org.interpss.plugin.optadj.algo.lf.AclfNetContigencyOptimizer;
import org.interpss.plugin.optadj.algo.util.AclfNetSsaHelper;
import org.interpss.plugin.optadj.result.OptAdjResultContainer;
import org.interpss.plugin.optadj.result.SsaBranchOverLimitInfo;
import org.interpss.plugin.optadj.result.SsaResultContainer;
import com.interpss.algo.parallel.BranchCAResultRec;
import com.interpss.algo.parallel.ContingencyAnalysisMonad;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.algo.dclf.adapter.DclfAlgoGen;
import com.interpss.core.algo.dclf.solver.IDclfSolver.CacheType;
import com.interpss.core.contingency.ContingencyBranchOutageType;
import com.interpss.core.contingency.dclf.DclfBranchOutage;
import com.interpss.core.contingency.dclf.DclfOutageBranch;

public class Texas2K_OptN1Scan_SsaResult_Sparse_Sample {
    public static void main(String args[]) throws Exception {
		// load the test data V33
		AclfNetwork aclfNet = Texas2K_Sample_Info.loadNetwork();	
		
		// define an caAlgo object and perform DCLF 
		ContingencyAnalysisAlgorithm dclfAlgo = createContingencyAnalysisAlgorithm(aclfNet, CacheType.SenNotCached, true);
		dclfAlgo.calculateDclf(DclfMethod.INC_LOSS);
		//System.out.println(DclfOutFunc.dclfResults(dclfAlgo, false));

		//import contingency definitions from CA file
		File contFile = new File("ipss.plugin.core/testData/psse/v36/2k_contingencies_115kVAbove.json");
		List<BranchContingencyRecord> contingencies = ContingencyFileUtil.importContingenciesFromJson(contFile);
				
		List<DclfBranchOutage> dclfContList = new DclfContingencyHelper(dclfAlgo)
								.createDclfContList(contingencies);
		
		//import monitored branches from JSON file
		File monFile = new File("ipss.plugin.core/testData/psse/v36/2k_monitored_branches.json");
		List<MonitoredBranchRecord> monitoredBranches = ContingencyFileUtil.importMonitoredBranchRecordsFromJson(monFile);
		
		Set<String> monitoredBranchIds = monitoredBranches.stream()
												.map(record -> record.getBranchId()).collect(Collectors.toSet());
		double loadingThreshold = 100.0;
		SsaResultContainer ssaResult = new AclfNetSsaHelper(dclfAlgo).contingencyScan(dclfContList, monitoredBranchIds, loadingThreshold);
		//ssaResult.printCaOverLimitInfo();
		System.out.println("Total number of branches over limit before OptAdj: " + ssaResult.getCaOverLimitInfo().size());
		
		OptAdjResultContainer optAdjResult = new OptAdjResultContainer(ssaResult);
		new AclfNetContigencyOptimizer(true).optimize(dclfAlgo, optAdjResult, loadingThreshold);
		optAdjResult.getOptAdjResults().forEach((genName, result) -> {
			System.out.println("GenAdjustResult: " + genName + ", " + result.toString());
		});

		dclfAlgo.calculateDclf(DclfMethod.INC_LOSS);
	
		/* 
		AtomicCounter cntAfter = new AtomicCounter();
		// perform N-1 outage scan
		dclfContList.parallelStream()
			.forEach(contingency -> {
				ContingencyAnalysisMonad.of(dclfAlgo, contingency)
					.ca(resultRec -> {
						double loading = resultRec.calLoadingPercent();
						if (loading > loadingThreshold && monitoredBranchIds.contains(resultRec.aclfBranch.getId())) {
							cntAfter.increment();
							// add the over limit branch CA result rec to the SSA result container
							DclfOutageBranch outageBranch = ((DclfBranchOutage)resultRec.contingency).getOutageEquip();
							System.out.println(String.format("OverLimit Branch: %s outage: %s postFlow: %.2f rating: %.2f loading: %.2f",
									resultRec.aclfBranch.getId(), outageBranch.getBranch().getId(),
									resultRec.getPostFlowMW(), resultRec.aclfBranch.getRatingMvaB(), loading));
						}
					});
			});
		System.out.println("Total number of branches over limit after OptAdj: " + cntAfter.getCount());
		*/
		
		SsaResultContainer ssaResultAfter = new AclfNetSsaHelper(dclfAlgo).contingencyScan(dclfContList, ssaResult.getCaOverLimitInfo());	
		System.out.println("Total number of branches over limit after OptAdj: " + ssaResultAfter.getCaOverLimitInfo().size());
		ssaResultAfter.printCaOverLimitInfo(ssaResult.getCaOverLimitInfo());

		double baseMVA = aclfNet.getBaseMva();
		String monitorBranchId = "Bus4044->Bus4185(1)";
		String outageBranchId = "Bus4044->Bus4119(1)";
		SsaBranchOverLimitInfo overLimitInfo = ssaResultAfter.getCaOverLimitInfo().stream()
			.filter(info -> info.getOverLimitBranchId().equals(monitorBranchId)
					&& info.getOutageBranchId().equals(outageBranchId))
			.findFirst()
			.orElse(null);

		if (overLimitInfo != null) {
			aclfNet.getBusList().stream()
					.filter(bus -> bus.isGenPV() || bus.isGenPQ())
					.forEach(bus -> {
						try {
							double csf = overLimitInfo.calCombinedShiftingFactor(bus.getId(), dclfAlgo);
							if (Math.abs(csf) > 0.05) {
								double genMw = dclfAlgo.getDclfAlgoBus(bus.getId()).getGenList().stream()
										.mapToDouble(DclfAlgoGen::getGenP).sum() * baseMVA;
								double genMaxMw = bus.getPGenLimit().getMax() * baseMVA;
								double genMinMw = bus.getPGenLimit().getMin() * baseMVA;
								System.out.println(String.format(
										"CSF for %s on monitored: %s outage: %s: %.2f, genP: %.2f, genMaxP: %.2f, genMinP: %.2f",
										bus.getId(), monitorBranchId, outageBranchId, csf, genMw, genMaxMw, genMinMw));
							}
						} catch (InterpssException e) {
							e.printStackTrace();
						}
					});
		}		
	}
}

