package org.interpss.threePhase.opf.dist.objective;

import org.interpss.threePhase.opf.dist.model.DistOpfModelData;
import org.interpss.threePhase.opf.dist.model.DistOpfVariableIndex;

public abstract class BaseDistOpfObjectiveCollector implements IDistOpfObjectiveCollector {

	protected final DistOpfModelData modelData;
	protected final DistOpfVariableIndex variableIndex;
	protected final double[] objective;

	protected BaseDistOpfObjectiveCollector(DistOpfModelData modelData,
			DistOpfVariableIndex variableIndex, double[] objective) {
		this.modelData = modelData;
		this.variableIndex = variableIndex;
		this.objective = objective;
	}
}
