package org.interpss.threePhase.opf.dist.model;

import org.interpss.threePhase.opf.dist.DistOpfOptions;
import org.interpss.threePhase.opf.dist.constraint.DistPowerBalanceConstraintCollector;
import org.interpss.threePhase.opf.dist.constraint.DistReactivePowerBalanceConstraintCollector;
import org.interpss.threePhase.opf.dist.constraint.DistSwingVoltageConstraintCollector;
import org.interpss.threePhase.opf.dist.constraint.DistVoltageDropConstraintCollector;
import org.interpss.threePhase.opf.dist.constraint.DistVoltageLimitConstraintCollector;

import com.interpss.core.acsc.PhaseCode;

public class LinDistFlowModelBuilder {

	public DistOpfModel build(DistOpfModelData modelData, DistOpfOptions options) {
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

		DistOpfModel model = new DistOpfModel(modelData, variableIndex);
		new DistPowerBalanceConstraintCollector(modelData, variableIndex, model.getMutableConstraints()).collectConstraint();
		new DistReactivePowerBalanceConstraintCollector(modelData, variableIndex, model.getMutableConstraints()).collectConstraint();
		new DistVoltageDropConstraintCollector(modelData, variableIndex, model.getMutableConstraints()).collectConstraint();
		new DistSwingVoltageConstraintCollector(modelData, variableIndex, model.getMutableConstraints()).collectConstraint();
		new DistVoltageLimitConstraintCollector(modelData, variableIndex, model.getMutableConstraints(), options).collectConstraint();
		model.setLinearObjective(new double[model.getNumberOfVariables()]);
		return model;
	}
}
