package org.interpss.threePhase.opf.dist.objective;

import org.interpss.threePhase.opf.dist.model.DistOpfDerData;
import org.interpss.threePhase.opf.dist.model.DistOpfModelData;
import org.interpss.threePhase.opf.dist.model.DistOpfVariableIndex;

import com.interpss.core.acsc.PhaseCode;

public class CurtailmentMinObjectiveCollector extends BaseDistOpfObjectiveCollector {

	public CurtailmentMinObjectiveCollector(DistOpfModelData modelData,
			DistOpfVariableIndex variableIndex, double[] objective) {
		super(modelData, variableIndex, objective);
	}

	@Override
	public void collectObjective() {
		for (DistOpfDerData der : modelData.getDers()) {
			for (PhaseCode phase : der.getPhases()) {
				objective[variableIndex.curtailment(der.getId(), phase)] = 1.0;
			}
		}
	}
}
