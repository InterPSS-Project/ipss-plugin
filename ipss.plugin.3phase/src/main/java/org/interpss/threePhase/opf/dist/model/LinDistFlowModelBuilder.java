package org.interpss.threePhase.opf.dist.model;

import org.interpss.threePhase.opf.dist.DistOpfControlMode;
import org.interpss.threePhase.opf.dist.DistOpfObjective;
import org.interpss.threePhase.opf.dist.DistOpfOptions;
import org.interpss.threePhase.opf.dist.constraint.DistDerLimitConstraintCollector;
import org.interpss.threePhase.opf.dist.constraint.DistPowerBalanceConstraintCollector;
import org.interpss.threePhase.opf.dist.constraint.DistReactivePowerBalanceConstraintCollector;
import org.interpss.threePhase.opf.dist.constraint.DistSwingVoltageConstraintCollector;
import org.interpss.threePhase.opf.dist.constraint.DistVoltageDropConstraintCollector;
import org.interpss.threePhase.opf.dist.constraint.DistVoltageLimitConstraintCollector;
import org.interpss.threePhase.opf.dist.objective.CurtailmentMinObjectiveCollector;
import org.interpss.threePhase.opf.dist.objective.GenMaxObjectiveCollector;

import com.interpss.core.acsc.PhaseCode;

public class LinDistFlowModelBuilder {

	public DistOpfModel build(DistOpfModelData modelData, DistOpfOptions options) {
		return build(modelData, options, DistOpfControlMode.NONE, DistOpfObjective.CURTAILMENT_MIN);
	}

	public DistOpfModel build(DistOpfModelData modelData, DistOpfOptions options,
			DistOpfControlMode controlMode, DistOpfObjective objective) {
		DistOpfVariableIndex variableIndex = new DistOpfVariableIndex();
		for (DistOpfBranchData branch : modelData.getBranches()) {
			for (PhaseCode phase : branch.getPhases()) {
				variableIndex.branchP(branch.getId(), phase);
				variableIndex.branchQ(branch.getId(), phase);
			}
		}
		for (DistOpfBusData bus : modelData.getBuses()) {
			for (PhaseCode phase : bus.getPhases()) {
				variableIndex.busV2(bus.getId(), phase);
			}
		}
		for (DistOpfDerData der : modelData.getDers()) {
			for (PhaseCode phase : der.getPhases()) {
				variableIndex.derP(der.getId(), phase);
				variableIndex.derQ(der.getId(), phase);
				if (controlsP(controlMode)) {
					variableIndex.curtailment(der.getId(), phase);
				}
			}
		}

		DistOpfModel model = new DistOpfModel(modelData, variableIndex);
		new DistPowerBalanceConstraintCollector(modelData, variableIndex, model.getMutableConstraints()).collectConstraint();
		new DistReactivePowerBalanceConstraintCollector(modelData, variableIndex, model.getMutableConstraints()).collectConstraint();
		new DistVoltageDropConstraintCollector(modelData, variableIndex, model.getMutableConstraints()).collectConstraint();
		new DistSwingVoltageConstraintCollector(modelData, variableIndex, model.getMutableConstraints()).collectConstraint();
		new DistVoltageLimitConstraintCollector(modelData, variableIndex, model.getMutableConstraints(), options).collectConstraint();
		new DistDerLimitConstraintCollector(modelData, variableIndex, model.getMutableConstraints(), controlMode).collectConstraint();
		model.setLinearObjective(linearObjective(modelData, variableIndex, controlMode, objective));
		return model;
	}

	private static double[] linearObjective(DistOpfModelData modelData, DistOpfVariableIndex variableIndex,
			DistOpfControlMode controlMode, DistOpfObjective objective) {
		double[] objectiveVector = new double[variableIndex.size()];
		if (objective == DistOpfObjective.CURTAILMENT_MIN && controlsP(controlMode)) {
			new CurtailmentMinObjectiveCollector(modelData, variableIndex, objectiveVector).collectObjective();
		} else if (objective == DistOpfObjective.GEN_MAX) {
			new GenMaxObjectiveCollector(modelData, variableIndex, objectiveVector).collectObjective();
		}
		return objectiveVector;
	}

	private static boolean controlsP(DistOpfControlMode controlMode) {
		return controlMode == DistOpfControlMode.P || controlMode == DistOpfControlMode.PQ;
	}
}
