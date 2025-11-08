package org.interpss.plugin.optadj.algo;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import org.apache.commons.math3.optim.linear.Relationship;
import org.interpss.plugin.optadj.algo.util.AclfNetLODFsHelper;
import org.interpss.plugin.optadj.algo.util.Sen2DMatrix;
import org.interpss.plugin.optadj.optimizer.bean.SectionConstrainData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBranch;

/**
 * 
 * @author Donghao.F
 * 
 * @date 2025��4��17�� ����11:20:49
 * 
 * 
 * 
 */
public class AclfNetContigencyOptimizer extends AclfNetLoadFlowOptimizer {
    private static final Logger log = LoggerFactory.getLogger(AclfNetContigencyOptimizer.class);
    
    private Set<String> outBranchIdSet;
    
	/**
	 * Constructor
	 * 
	 * @param dclfAlgo
	 */
	public AclfNetContigencyOptimizer(ContingencyAnalysisAlgorithm dclfAlgo) {
		super(dclfAlgo);
	}

	public void optimize(double threshold, Set<String> outBranchIdSet) {
		this.outBranchIdSet = outBranchIdSet;
		
		super.optimize(threshold);
	}
	
	@Override
	protected void buildSectionConstrain(Sen2DMatrix gfsMatrix, double threshold) {
		super.buildSectionConstrain(gfsMatrix, threshold);
		
		AclfNetwork aclfNet = (AclfNetwork) dclfAlgo.getNetwork();
		AclfNetLODFsHelper helper = new AclfNetLODFsHelper(aclfNet);
		Sen2DMatrix lodfMatrix = this.outBranchIdSet == null?
				helper.calLODF() : helper.calLODF(this.outBranchIdSet);
		
		double baseMva = aclfNet.getBaseMva();
		aclfNet.getBranchList().stream()
			.filter(branch -> branch.isActive() && 
					(this.outBranchIdSet == null || this.outBranchIdSet.contains(branch.getId())))
			.forEach(outBranch -> {
				int outBranchNo = outBranch.getSortNumber();
				double[] genSenArray = new double[controlGenMap.size()];
				DclfAlgoBranch outDclfBranch = dclfAlgo.getDclfAlgoBranch(outBranch.getId());
	
				controlGenMap.forEach((no, gen) -> {
					int busNo = gen.getParentBus().getSortNumber();
					double sen = gfsMatrix.get(busNo, outBranchNo);
					genSenArray[no] = sen;
				});
	
				if (Arrays.stream(genSenArray).anyMatch(sen -> Math.abs(sen) > SEN_THRESHOLD)) {
					aclfNet.getBranchList().stream()
						.filter(branch -> branch.isActive())
						.forEach(monBranch -> {
							int monBranchNo = monBranch.getSortNumber();
							double lodf = lodfMatrix.get(outBranchNo, monBranchNo);
							if (Arrays.stream(genSenArray)
									.anyMatch(sen -> Math.abs(sen * lodf) > SEN_THRESHOLD)) {
								DclfAlgoBranch monDclfBranch = dclfAlgo.getDclfAlgoBranch(monBranch.getId());
								double postFlow = monDclfBranch.getDclfFlow() + lodf * outDclfBranch.getDclfFlow();
	
								controlGenMap.forEach((no, gen) -> {
									int busNo = gen.getParentBus().getSortNumber();
									// GSFij + LODF x GSFkm
									double sen = gfsMatrix.get(busNo, monBranchNo) + lodf * gfsMatrix.get(busNo, outBranchNo);
									genSenArray[no] = postFlow > 0 ? sen : -sen;
								});
								
								double limit = monDclfBranch.getBranch().getRatingMva2() * threshold / 100;
								double postFlowMw = Math.abs(postFlow * baseMva); ;
								genOptimizer.addConstraint(new SectionConstrainData(postFlowMw, Relationship.LEQ, limit, genSenArray));
							}
						});
				}
			});
	}

}
