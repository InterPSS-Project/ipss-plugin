package org.interpss.plugin.optadj.algo;

import java.util.Arrays;
import java.util.Map;

import org.apache.commons.math3.optim.linear.Relationship;
import org.interpss.plugin.optadj.algo.util.AclfNetSensHelper;
import org.interpss.plugin.optadj.optimizer.GenStateOptimizer;
import org.interpss.plugin.optadj.optimizer.bean.SectionConstrainData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.common.exp.InterpssException;
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
	protected void buildSectionConstrain(float[][] senMatrix,
			Map<Integer, AclfGen> controlGenMap, double threshold) {
		super.buildSectionConstrain(senMatrix, controlGenMap, threshold);
		AclfNetwork aclfNet = (AclfNetwork) dclfAlgo.getNetwork();
		double baseMva = aclfNet.getBaseMva();
		aclfNet.getBranchList().stream().filter(branch -> branch.isActive()).forEach(outBranch -> {
			int outBranchNo = outBranch.getSortNumber();
			double[] genSenArray = new double[controlGenMap.size()];
			DclfAlgoBranch outDclfBranch = dclfAlgo.getDclfAlgoBranch(outBranch.getId());

			controlGenMap.forEach((no, gen) -> {
				int busNo = gen.getParentBus().getSortNumber();
				float sen = senMatrix[busNo][outBranchNo];
				genSenArray[no] = sen;
			});

			if (Arrays.stream(genSenArray).anyMatch(sen -> Math.abs(sen) > SEN_THRESHOLD)) {
				CaOutageBranch caOutBranch = DclfAlgoObjectFactory.createCaOutageBranch(outDclfBranch,
						CaBranchOutageType.OPEN);
				// lodf
				try {
					double[] lodfAry = dclfAlgo.lineOutageDFactors(caOutBranch);
					aclfNet.getBranchList().stream().filter(branch -> branch.isActive()).forEach(branch -> {
						int branchNo = branch.getSortNumber();
						if (Arrays.stream(genSenArray)
								.anyMatch(sen -> Math.abs(sen * lodfAry[branch.getSortNumber()]) > SEN_THRESHOLD)) {
							DclfAlgoBranch dclfBranch = dclfAlgo.getDclfAlgoBranch(branch.getId());
							double postFlow = dclfBranch.getDclfFlow()
									+ lodfAry[branch.getSortNumber()] * outDclfBranch.getDclfFlow();

							controlGenMap.forEach((no, gen) -> {
								int busNo = gen.getParentBus().getSortNumber();
								// GSFij + LODF x GSFkm
								float sen = (float) (senMatrix[busNo][branchNo]
										+ lodfAry[branch.getSortNumber()] * senMatrix[busNo][outBranchNo]);
								genSenArray[no] = postFlow > 0 ? sen : -sen;
							});
							double limit = dclfBranch.getBranch().getRatingMva2() * threshold / 100;
							double postFlowMw = Math.abs(postFlow * baseMva); ;
							genOptimizer.addConstraint(new SectionConstrainData(postFlowMw, Relationship.LEQ, limit, genSenArray));
						}
					});
				} catch (InterpssException e) {
					//e.printStackTrace();
					log.error(e.toString());
				}
			}
		});
	}

}
