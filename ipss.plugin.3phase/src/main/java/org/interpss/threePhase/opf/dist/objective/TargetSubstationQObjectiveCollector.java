package org.interpss.threePhase.opf.dist.objective;

import org.interpss.threePhase.opf.dist.model.DistOpfModelData;
import org.interpss.threePhase.opf.dist.model.DistOpfVariableIndex;

public class TargetSubstationQObjectiveCollector extends BaseDistOpfObjectiveCollector {

	public TargetSubstationQObjectiveCollector(DistOpfModelData modelData,
			DistOpfVariableIndex variableIndex, double[] objective) {
		super(modelData, variableIndex, objective);
	}

	@Override
	public void collectObjective() {
		objective[variableIndex.targetQPositive(modelData.getSwingBusId())] = 1.0;
		objective[variableIndex.targetQNegative(modelData.getSwingBusId())] = 1.0;
	}
}
