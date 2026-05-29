package org.interpss.threePhase.opf.dist.impl;

import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.opf.dist.DistOpfAlgorithm;
import org.interpss.threePhase.opf.dist.DistOpfControlMode;
import org.interpss.threePhase.opf.dist.DistOpfObjective;
import org.interpss.threePhase.opf.dist.DistOpfOptions;
import org.interpss.threePhase.opf.dist.DistOpfResult;
import org.interpss.threePhase.opf.dist.DistOpfSolverType;
import org.interpss.threePhase.opf.dist.DistOpfStatus;
import org.interpss.threePhase.opf.dist.model.DistOpfBranchData;
import org.interpss.threePhase.opf.dist.model.DistOpfBusData;
import org.interpss.threePhase.opf.dist.model.DistOpfDerData;
import org.interpss.threePhase.opf.dist.model.DistOpfModelData;
import org.interpss.threePhase.opf.dist.model.DistOpfModelDataExtractor;
import org.interpss.threePhase.opf.dist.model.DistOpfModel;
import org.interpss.threePhase.opf.dist.model.LinDistFlowModelBuilder;
import org.interpss.threePhase.opf.dist.solver.DistOpfSolver;
import org.interpss.threePhase.opf.dist.solver.DistOpfSolverResult;
import org.interpss.threePhase.opf.dist.solver.ORToolsDistOpfSolver;
import org.interpss.threePhase.opf.dist.solver.OjAlgoDistOpfSolver;

import com.interpss.core.acsc.PhaseCode;

public class DistOpfAlgorithmImpl implements DistOpfAlgorithm {

	private final DStabNetwork3Phase net;
	private DistOpfObjective objective = DistOpfObjective.CURTAILMENT_MIN;
	private DistOpfControlMode controlMode = DistOpfControlMode.NONE;
	private DistOpfOptions options = new DistOpfOptions();

	public DistOpfAlgorithmImpl(DStabNetwork3Phase net) {
		this.net = net;
	}

	@Override
	public DistOpfAlgorithm setObjective(DistOpfObjective objective) {
		this.objective = objective;
		return this;
	}

	@Override
	public DistOpfAlgorithm setControlMode(DistOpfControlMode controlMode) {
		this.controlMode = controlMode;
		return this;
	}

	@Override
	public DistOpfAlgorithm setOptions(DistOpfOptions options) {
		this.options = options;
		return this;
	}

	@Override
	public DistOpfResult solve() {
		try {
			DistOpfModelData modelData = new DistOpfModelDataExtractor().extract(net);
			DistOpfModel model = new LinDistFlowModelBuilder().build(modelData, options, controlMode, objective);
			DistOpfSolverResult solverResult = solver(options).solve(model, options);
			DistOpfResult result = new DistOpfResult(solverResult.getStatus(),
					solverResult.getObjectiveValue(), solverResult.getMaxConstraintResidual());
			mapResult(model, solverResult, result);
			return result;
		} catch (RuntimeException e) {
			return new DistOpfResult(DistOpfStatus.ERROR, Double.NaN, Double.NaN).addWarning(e.getMessage());
		}
	}

	private static DistOpfSolver solver(DistOpfOptions options) {
		if (options.getSolverType() == DistOpfSolverType.ORTOOLS) {
			return new ORToolsDistOpfSolver();
		}
		return new OjAlgoDistOpfSolver();
	}

	private static void mapResult(DistOpfModel model, DistOpfSolverResult solverResult, DistOpfResult result) {
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
	}

	public DStabNetwork3Phase getNetwork() {
		return net;
	}

	public DistOpfObjective getObjective() {
		return objective;
	}

	public DistOpfControlMode getControlMode() {
		return controlMode;
	}

	public DistOpfOptions getOptions() {
		return options;
	}
}
