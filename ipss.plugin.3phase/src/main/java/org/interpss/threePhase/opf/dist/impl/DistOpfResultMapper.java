package org.interpss.threePhase.opf.dist.impl;

import org.interpss.threePhase.opf.dist.DistOpfResult;
import org.interpss.threePhase.opf.dist.model.DistOpfBranchData;
import org.interpss.threePhase.opf.dist.model.DistOpfBusData;
import org.interpss.threePhase.opf.dist.model.DistOpfCapacitorData;
import org.interpss.threePhase.opf.dist.model.DistOpfDerData;
import org.interpss.threePhase.opf.dist.model.DistOpfModel;
import org.interpss.threePhase.opf.dist.model.DistOpfModelData;
import org.interpss.threePhase.opf.dist.model.DistOpfRegulatorData;
import org.interpss.threePhase.opf.dist.solver.DistOpfSolverResult;

import com.interpss.core.acsc.PhaseCode;

public final class DistOpfResultMapper {

	private DistOpfResultMapper() {
	}

	public static void map(DistOpfModel model, DistOpfSolverResult solverResult, DistOpfResult result) {
		result.addBindingConstraints(solverResult.getBindingConstraints());
		result.addDiagnostics(solverResult.getDiagnostics());
		if (!result.isSolved()) {
			result.addWarning(solverResult.getMessage());
			return;
		}
		double[] x = solverResult.getPrimalVariables();
		DistOpfModelData modelData = model.getModelData();
		for (DistOpfBusData bus : modelData.getBuses()) {
			for (PhaseCode phase : bus.getPhases()) {
				result.putBusVoltageSquared(bus.getId(), phase.name(),
						x[model.getVariableIndex().busV2(bus.getId(), phase)]);
			}
		}
		for (DistOpfBranchData branch : modelData.getBranches()) {
			for (PhaseCode phase : branch.getPhases()) {
				result.putBranchActivePower(branch.getId(), phase.name(),
						x[model.getVariableIndex().branchP(branch.getId(), phase)]);
				result.putBranchReactivePower(branch.getId(), phase.name(),
						x[model.getVariableIndex().branchQ(branch.getId(), phase)]);
			}
		}
		for (DistOpfDerData der : modelData.getDers()) {
			for (PhaseCode phase : der.getPhases()) {
				result.putDerActivePower(der.getId(), phase.name(),
						x[model.getVariableIndex().derP(der.getId(), phase)]);
				result.putDerReactivePower(der.getId(), phase.name(),
						x[model.getVariableIndex().derQ(der.getId(), phase)]);
			}
		}
		for (DistOpfCapacitorData capacitor : modelData.getCapacitors()) {
			for (PhaseCode phase : capacitor.getPhases()) {
				result.putCapacitorStatus(capacitor.getId(), phase.name(),
						x[model.getVariableIndex().capacitorStatus(capacitor.getId(), phase)]);
			}
		}
		for (DistOpfRegulatorData regulator : modelData.getRegulators()) {
			for (PhaseCode phase : regulator.getPhases()) {
				result.putRegulatorTap(regulator.getId(), phase.name(),
						x[model.getVariableIndex().regulatorTap(regulator.getId(), phase)]);
			}
		}
	}
}
