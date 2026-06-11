package org.interpss.plugin.optadj.algo.lf;

import java.util.Set;

import org.ejml.data.DMatrixSparseCSC;
import org.interpss.plugin.optadj.algo.util.AclfNetSensHelper;
import org.interpss.plugin.optadj.algo.util.AclfNetSensSparseHelper;
import org.interpss.plugin.optadj.result.SsaResultContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.core.aclf.AclfNetwork;

/**
 * 
 * 
 */
public class AclfNetLoadFlowOptimizer extends BaseAclfNetLoadFlowOptimizer{
	private static Logger log = LoggerFactory.getLogger(AclfNetLoadFlowOptimizer.class);
	
	private boolean sparseMatrix = false;

	private float[][] senMatrix = null;

	private DMatrixSparseCSC senSparseMatrix = null;

	/* 
	public AclfNetLoadFlowOptimizer() {
		this.sparseMatrix = false;
	}
	*/

	public AclfNetLoadFlowOptimizer(boolean sparseMatrix) {
		this.sparseMatrix = sparseMatrix;
	}

	@Override
	protected int getBusSenIndex(com.interpss.core.aclf.BaseAclfBus<?, ?> bus) {
		return sparseMatrix ? bus.getSortNumber() : super.getBusSenIndex(bus);
	}

	@Override
	protected int getBranchSenIndex(com.interpss.core.aclf.AclfBranch branch) {
		return sparseMatrix ? branch.getSortNumber() : super.getBranchSenIndex(branch);
	}

	@Override
	protected void createSenMatrix(AclfNetwork net, SsaResultContainer ssaResult) {
		if (sparseMatrix) {
			AclfNetSensSparseHelper helper = new AclfNetSensSparseHelper(net);
			if (ssaResult != null) {
				Set<String> busSet = buildGenParentBusSet(net);
				Set<String> branchSet = buildSsaBranchSet(ssaResult);
				log.info("Sparse sen matrix busSet: " + busSet.size());
				log.info("Sparse sen matrix branchSet: " + branchSet.size());
				senSparseMatrix = helper.calSenSortNumber(busSet, branchSet);
			} else
				senSparseMatrix = helper.calSen();
		} else {
			AclfNetSensHelper helper = new AclfNetSensHelper(net);
			senMatrix = helper.calSen();
		}
	}
	
	@Override
	protected float getSen(int busNo, int branchNo) {
		return sparseMatrix ? (float) senSparseMatrix.get(busNo, branchNo, 0.0f) : senMatrix[busNo][branchNo];
	}
}
