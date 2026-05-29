package org.interpss.threePhase.opf.dist.constraint;

import java.util.List;

import org.interpss.threePhase.opf.dist.DistOpfOptions;
import org.interpss.threePhase.opf.dist.DistOpfVoltageModel;
import org.interpss.threePhase.opf.dist.model.DistOpfBusData;
import org.interpss.threePhase.opf.dist.model.DistOpfModelData;
import org.interpss.threePhase.opf.dist.model.DistOpfVariableIndex;

import com.interpss.core.acsc.PhaseCode;

public class DistSwingVoltageConstraintCollector extends BaseDistOpfConstraintCollector {

	private final DistOpfOptions options;

	public DistSwingVoltageConstraintCollector(DistOpfModelData modelData,
			DistOpfVariableIndex variableIndex, List<org.interpss.plugin.opf.constraint.OpfConstraint> constraints) {
		this(modelData, variableIndex, constraints, new DistOpfOptions());
	}

	public DistSwingVoltageConstraintCollector(DistOpfModelData modelData,
			DistOpfVariableIndex variableIndex, List<org.interpss.plugin.opf.constraint.OpfConstraint> constraints,
			DistOpfOptions options) {
		super(modelData, variableIndex, constraints);
		this.options = options;
	}

	@Override
	public void collectConstraint() {
		for (DistOpfBusData bus : modelData.getBuses()) {
			if (!bus.isSwing()) {
				continue;
			}
			for (PhaseCode phase : bus.getPhases()) {
				double voltage = options.getVoltageModel() == DistOpfVoltageModel.ANGLE_COUPLED_LINDISTFLOW
						? bus.getInitialVoltageMagnitude(phase)
						: 1.0;
				addEquality("SwingV2@" + bus.getId() + "." + phase, voltage * voltage,
						new int[] { variableIndex.busV2(bus.getId(), phase) }, new double[] { 1.0 });
			}
		}
	}
}
