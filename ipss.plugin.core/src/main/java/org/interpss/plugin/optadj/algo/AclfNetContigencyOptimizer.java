package org.interpss.plugin.optadj.algo;

import java.util.Arrays;
import java.util.Map;

import org.apache.commons.math3.optim.linear.Relationship;
import org.interpss.plugin.optadj.algo.util.AclfNetSensHelper;
import org.interpss.plugin.optadj.optimizer.bean.SectionConstrainData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.CaBranchOutageType;
import com.interpss.core.algo.dclf.CaOutageBranch;
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
    
	/**
	 * Constructor
	 * 
	 * @param dclfAlgo
	 */
	public AclfNetContigencyOptimizer(ContingencyAnalysisAlgorithm dclfAlgo) {
		super(dclfAlgo);
	}
	
	@Override
	protected void buildSectionConstrain(double[][] gfsMatrix,
			Map<Integer, AclfGen> controlGenMap, double threshold) {
		super.buildSectionConstrain(gfsMatrix, controlGenMap, threshold);
		
		AclfNetwork aclfNet = (AclfNetwork) dclfAlgo.getNetwork();
		AclfNetSensHelper helper = new AclfNetSensHelper(aclfNet);
		double[][] lodfMatrix = helper.calLODF();
		
		double baseMva = aclfNet.getBaseMva();
		aclfNet.getBranchList().stream().filter(branch -> branch.isActive()).forEach(outBranch -> {
			int outBranchNo = outBranch.getSortNumber();
			double[] genSenArray = new double[controlGenMap.size()];
			DclfAlgoBranch outDclfBranch = dclfAlgo.getDclfAlgoBranch(outBranch.getId());

			controlGenMap.forEach((no, gen) -> {
				int busNo = gen.getParentBus().getSortNumber();
				double sen = gfsMatrix[busNo][outBranchNo];
				genSenArray[no] = sen;
			});

			if (Arrays.stream(genSenArray).anyMatch(sen -> Math.abs(sen) > SEN_THRESHOLD)) {
				//double[] lodfAry = dclfAlgo.lineOutageDFactors(caOutBranch);
				//double[] lodfAry = lodfMatrix[outBranchNo];
					
				aclfNet.getBranchList().stream()
					.filter(branch -> branch.isActive())
					.forEach(monBranch -> {
						int monBranchNo = monBranch.getSortNumber();
						double lodf = lodfMatrix[outBranchNo][monBranchNo];
						if (Arrays.stream(genSenArray)
								.anyMatch(sen -> Math.abs(sen * lodf) > SEN_THRESHOLD)) {
							DclfAlgoBranch monDclfBranch = dclfAlgo.getDclfAlgoBranch(monBranch.getId());
							double postFlow = monDclfBranch.getDclfFlow() + lodf * outDclfBranch.getDclfFlow();

							controlGenMap.forEach((no, gen) -> {
								int busNo = gen.getParentBus().getSortNumber();
								// GSFij + LODF x GSFkm
								double sen = gfsMatrix[busNo][monBranchNo] + lodf * gfsMatrix[busNo][outBranchNo];
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
