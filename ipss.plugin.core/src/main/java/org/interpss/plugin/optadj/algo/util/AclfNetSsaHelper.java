package org.interpss.plugin.optadj.algo.util;

import com.interpss.algo.parallel.BranchCAResultRec;
import com.interpss.algo.parallel.ContingencyAnalysisMonad;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBranch;
import com.interpss.core.contingency.dclf.DclfBranchOutage;
import com.interpss.core.contingency.dclf.DclfOutageBranch;

import org.slf4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.interpss.plugin.optadj.result.SsaBranchOverLimitInfo;
import org.interpss.plugin.optadj.result.SsaResultContainer;
import org.slf4j.LoggerFactory;

/**
 * Helper class for SSA (Steady-State Analysis) over-limit result calculation.
 */
public class AclfNetSsaHelper {
   private static Logger log = LoggerFactory.getLogger(AclfNetSsaHelper.class);

   /**
    * The DCLF algorithm object.
    */
   private ContingencyAnalysisAlgorithm dclfAlgo;

   /**
    * Constructor.
    * @param dclfAlgo The DCLF algorithm object.
    */
   public AclfNetSsaHelper(ContingencyAnalysisAlgorithm dclfAlgo) {
      this.dclfAlgo = dclfAlgo;
   }

   /**
    * Scan the base case loading and return the SSA result container.
    * @param loadingThreshold The loading threshold.
    * @return The SSA result container.
    */
   public SsaResultContainer baseCaseScan(double loadingThreshold) {
      SsaResultContainer ssaResult = new SsaResultContainer();
		ssaResult.setBaseLoadingThreshold(loadingThreshold);
				
		// check the branch loading
		double baseMVA = dclfAlgo.getNetwork().getBaseMva();
		dclfAlgo.getDclfAlgoBranchList().stream() 
			.forEach(dclfBranch -> {
				double flowMw = dclfBranch.getDclfFlow() * baseMVA;
				double loading = Math.abs(flowMw / dclfBranch.getBranch().getRatingMvaA())*100;
				if (loading > ssaResult.getBaseLoadingThreshold()) {
					ssaResult.getBaseOverLimitInfo().add(new SsaBranchOverLimitInfo(dclfBranch.getId(), dclfBranch.getBranch().getRatingMvaA(), flowMw));
					//System.out.printf("Over Limit Branch: %s  %.2f rating: %.2f loading: %.2f%n",
					//		dclfBranch.getId(),
					//		flowMw,
					//		dclfBranch.getBranch().getRatingMvaA(),
					//		loading);
					}
			});
         return ssaResult;
   }

     /**
    * calculate the base case loading for the given base over limit info and return the SSA result container.
    * @param baseOverLimitInfo The list of base over limit info.
    * @return The SSA result container.
    */
   public SsaResultContainer calBaseCaseLoading(List<SsaBranchOverLimitInfo> baseOverLimitInfo) {
      SsaResultContainer ssaResult = new SsaResultContainer();
				
      double baseMVA = dclfAlgo.getNetwork().getBaseMva();
      baseOverLimitInfo.forEach(info -> {
         DclfAlgoBranch dclfBranch = dclfAlgo.getDclfAlgoBranch(info.getOverLimitBranchId());
         double flowMw = dclfBranch.getDclfFlow() * baseMVA;
         ssaResult.getBaseOverLimitInfo().add(new SsaBranchOverLimitInfo(dclfBranch.getId(), dclfBranch.getBranch().getRatingMvaA(), flowMw));
      });

      return ssaResult;
   }

   /**
    * Scan the contingency loading and return the SSA result container.
    * @param contList The list of contingency branches.
    * @param loadingThreshold The loading threshold.
    * @return The SSA result container.
    */
   public SsaResultContainer contingencyScan(List<DclfBranchOutage> contList, double loadingThreshold) {
      return contingencyScan(contList, Collections.emptySet(), loadingThreshold);
   }

   /**
    * Scan the contingency loading and return the SSA result container.
    * @param contList The list of contingency branches.
    * @param monitoredBranchIds The set of monitored branch IDs.
    * @param loadingThreshold The loading threshold.
    * @return The SSA result container.
    */
   public SsaResultContainer contingencyScan(List<DclfBranchOutage> contList, Set<String> monitoredBranchIds, double loadingThreshold) {
      SsaResultContainer ssaResult = new SsaResultContainer();
      ssaResult.setCaLoadingThreshold(loadingThreshold);
      
      contList.parallelStream().forEach(contingency -> {
         ContingencyAnalysisMonad.of(dclfAlgo, contingency)
            .ca(resultRec -> {
               if (Math.abs(resultRec.shiftedFlowMW) > BranchCAResultRec.ContingencyShiftThreshold) {
                  // skip the contingency if the shifted flow is less than the shift threshold
                  double loading = resultRec.calLoadingPercent();
                  AclfBranch monitoredBranch = resultRec.aclfBranch;
                  if (loading > ssaResult.getCaLoadingThreshold() && 
                           (monitoredBranchIds.isEmpty() || monitoredBranchIds.contains(monitoredBranch.getId()))) {
                     // add the over limit branch CA result rec to the SSA result container
                     DclfOutageBranch outageBranch = ((DclfBranchOutage)resultRec.contingency).getOutageEquip();
                     ssaResult.getCaOverLimitInfo().add(new SsaBranchOverLimitInfo(
                           outageBranch.getBranch().getId(), monitoredBranch.getId(), 
                           monitoredBranch.getRatingMvaB(), resultRec.preFlowMW, resultRec.shiftedFlowMW));
                     //System.out.println(String.format("OverLimit Branch: %s outage: %s postFlow: %.2f rating: %.2f loading: %.2f",
                     //     monitoredBranch.getId(), outageBranch.getBranch().getId(),
                     //      resultRec.getPostFlowMW(), monitoredBranch.getRatingMvaB(), loading));
                  }
               }
            });
      });

      return ssaResult;

   }

}