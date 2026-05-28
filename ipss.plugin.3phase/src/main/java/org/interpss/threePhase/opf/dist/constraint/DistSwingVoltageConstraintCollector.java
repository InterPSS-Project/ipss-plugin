package org.interpss.threePhase.opf.dist.constraint;

import java.util.List;

import org.interpss.threePhase.opf.dist.model.DistOpfBusData;
import org.interpss.threePhase.opf.dist.model.DistOpfModelData;
import org.interpss.threePhase.opf.dist.model.DistOpfVariableIndex;

import com.interpss.core.acsc.PhaseCode;

public class DistSwingVoltageConstraintCollector extends BaseDistOpfConstraintCollector {

	public DistSwingVoltageConstraintCollector(DistOpfModelData modelData,
			DistOpfVariableIndex variableIndex, List<org.interpss.plugin.opf.constraint.OpfConstraint> constraints) {
		super(modelData, variableIndex, constraints);
	}

	@Override
	public void collectConstraint() {
		for (DistOpfBusData bus : modelData.getBuses()) {
			if (!bus.isSwing()) {
				continue;
			}
			for (PhaseCode phase : bus.getPhases()) {
				addEquality("SwingV2@" + bus.getId() + "." + phase, 1.0,
						new int[] { variableIndex.busV2(bus.getId(), phase) }, new double[] { 1.0 });
			}
		}
	}
}
