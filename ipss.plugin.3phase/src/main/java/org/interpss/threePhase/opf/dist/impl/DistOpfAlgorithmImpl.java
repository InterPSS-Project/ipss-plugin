package org.interpss.threePhase.opf.dist.impl;

import java.util.Map;

import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.opf.dist.DistOpfAlgorithm;
import org.interpss.threePhase.opf.dist.DistOpfControlMode;
import org.interpss.threePhase.opf.dist.DistOpfObjective;
import org.interpss.threePhase.opf.dist.DistOpfOptions;
import org.interpss.threePhase.opf.dist.DistOpfResult;
import org.interpss.threePhase.opf.dist.DistOpfStatus;
import org.interpss.threePhase.opf.dist.model.DistBranchFlowLossProfile;
import org.interpss.threePhase.opf.dist.model.DistOpfModel;
import org.interpss.threePhase.opf.dist.model.DistOpfModelData;
import org.interpss.threePhase.opf.dist.model.DistOpfModelDataExtractor;
import org.interpss.threePhase.opf.dist.model.LinDistFlowModelBuilder;
import org.interpss.threePhase.opf.dist.solver.DistOpfSolverFactory;
import org.interpss.threePhase.opf.dist.solver.DistOpfSolverResult;

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
			LinDistFlowModelBuilder builder = new LinDistFlowModelBuilder();
			DistOpfResult result = solveModel(builder.build(modelData, options, controlMode, objective));
			for (int iteration = 0; iteration < options.getBranchFlowLossIterations()
					&& result.isSolved(); iteration++) {
				DistBranchFlowLossProfile lossProfile = DistBranchFlowLossProfile.fromResult(modelData, result);
				DistOpfResult nextResult = solveModel(
						builder.build(modelData, options, controlMode, objective, lossProfile));
				if (!nextResult.isSolved()) {
					return nextResult;
				}
				double delta = maxSolutionDelta(result, nextResult);
				result = nextResult.addDiagnostic("Branch-flow loss iteration " + (iteration + 1)
						+ " max delta=" + delta);
				if (delta <= options.getBranchFlowLossTolerance()) {
					break;
				}
			}
			return result;
		} catch (RuntimeException e) {
			return new DistOpfResult(DistOpfStatus.ERROR, Double.NaN, Double.NaN).addWarning(e.getMessage());
		}
	}

	private DistOpfResult solveModel(DistOpfModel model) {
		DistOpfSolverResult solverResult = DistOpfSolverFactory.create(options).solve(model, options);
		DistOpfResult result = new DistOpfResult(solverResult.getStatus(),
				solverResult.getObjectiveValue(), solverResult.getMaxConstraintResidual());
		DistOpfResultMapper.map(model, solverResult, result);
		return result;
	}

	private static double maxSolutionDelta(DistOpfResult previous, DistOpfResult current) {
		double maxDelta = 0.0;
		maxDelta = Math.max(maxDelta, maxMapDelta(previous.getBusVoltageSquared(), current.getBusVoltageSquared()));
		maxDelta = Math.max(maxDelta, maxMapDelta(previous.getBranchActivePower(), current.getBranchActivePower()));
		maxDelta = Math.max(maxDelta, maxMapDelta(previous.getBranchReactivePower(), current.getBranchReactivePower()));
		maxDelta = Math.max(maxDelta, maxMapDelta(previous.getDerActivePower(), current.getDerActivePower()));
		maxDelta = Math.max(maxDelta, maxMapDelta(previous.getDerReactivePower(), current.getDerReactivePower()));
		return maxDelta;
	}

	private static double maxMapDelta(Map<String, Double> previous, Map<String, Double> current) {
		double maxDelta = 0.0;
		for (Map.Entry<String, Double> entry : current.entrySet()) {
			Double oldValue = previous.get(entry.getKey());
			if (oldValue != null) {
				maxDelta = Math.max(maxDelta, Math.abs(entry.getValue().doubleValue() - oldValue.doubleValue()));
			}
		}
		return maxDelta;
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
