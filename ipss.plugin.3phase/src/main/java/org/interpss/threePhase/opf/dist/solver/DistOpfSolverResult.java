package org.interpss.threePhase.opf.dist.solver;

import org.interpss.threePhase.opf.dist.DistOpfStatus;

public class DistOpfSolverResult {

	private final DistOpfStatus status;
	private final double objectiveValue;
	private final double maxConstraintResidual;
	private final double[] primalVariables;
	private final String message;

	public DistOpfSolverResult(DistOpfStatus status, double objectiveValue,
			double maxConstraintResidual, double[] primalVariables, String message) {
		this.status = status;
		this.objectiveValue = objectiveValue;
		this.maxConstraintResidual = maxConstraintResidual;
		this.primalVariables = primalVariables;
		this.message = message;
	}

	public DistOpfStatus getStatus() {
		return status;
	}

	public double getObjectiveValue() {
		return objectiveValue;
	}

	public double getMaxConstraintResidual() {
		return maxConstraintResidual;
	}

	public double[] getPrimalVariables() {
		return primalVariables;
	}

	public String getMessage() {
		return message;
	}
}
