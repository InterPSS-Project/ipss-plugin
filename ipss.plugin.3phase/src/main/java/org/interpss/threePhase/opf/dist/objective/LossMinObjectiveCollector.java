package org.interpss.threePhase.opf.dist.objective;

import org.interpss.threePhase.opf.dist.model.DistOpfBranchData;
import org.interpss.threePhase.opf.dist.model.DistOpfModelData;
import org.interpss.threePhase.opf.dist.model.DistOpfVariableIndex;

import com.interpss.core.acsc.PhaseCode;

public class LossMinObjectiveCollector extends BaseDistOpfObjectiveCollector {

	public LossMinObjectiveCollector(DistOpfModelData modelData,
			DistOpfVariableIndex variableIndex, double[] objective) {
		super(modelData, variableIndex, objective);
	}

	@Override
	public void collectObjective() {
		for (DistOpfBranchData branch : modelData.getChildren(modelData.getSwingBusId())) {
			for (PhaseCode phase : branch.getPhases()) {
				objective[variableIndex.branchP(branch.getId(), phase)] = 1.0;
			}
		}
	}
}
