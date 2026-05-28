package org.interpss.threePhase.opf.dist.objective;

import org.interpss.threePhase.opf.dist.model.DistOpfModelData;
import org.interpss.threePhase.opf.dist.model.DistOpfVariableIndex;

public class TargetSubstationPObjectiveCollector extends BaseDistOpfObjectiveCollector {

	public TargetSubstationPObjectiveCollector(DistOpfModelData modelData,
			DistOpfVariableIndex variableIndex, double[] objective) {
		super(modelData, variableIndex, objective);
	}

	@Override
	public void collectObjective() {
		objective[variableIndex.targetPPositive(modelData.getSwingBusId())] = 1.0;
		objective[variableIndex.targetPNegative(modelData.getSwingBusId())] = 1.0;
	}
}
