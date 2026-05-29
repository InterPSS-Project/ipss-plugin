package org.interpss.threePhase.opf.dist.impl;

import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.opf.dist.DistOpfAlgorithm;
import org.interpss.threePhase.opf.dist.DistOpfControlMode;
import org.interpss.threePhase.opf.dist.DistOpfObjective;
import org.interpss.threePhase.opf.dist.DistOpfOptions;
import org.interpss.threePhase.opf.dist.DistOpfResult;
import org.interpss.threePhase.opf.dist.DistOpfStatus;
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
			DistOpfModel model = new LinDistFlowModelBuilder().build(modelData, options, controlMode, objective);
			DistOpfSolverResult solverResult = DistOpfSolverFactory.create(options).solve(model, options);
			DistOpfResult result = new DistOpfResult(solverResult.getStatus(),
					solverResult.getObjectiveValue(), solverResult.getMaxConstraintResidual());
			DistOpfResultMapper.map(model, solverResult, result);
			return result;
		} catch (RuntimeException e) {
			return new DistOpfResult(DistOpfStatus.ERROR, Double.NaN, Double.NaN).addWarning(e.getMessage());
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
