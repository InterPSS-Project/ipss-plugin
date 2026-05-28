package org.interpss.threePhase.opf.dist.solver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.interpss.threePhase.opf.dist.DistOpfStatus;

public class DistOpfSolverResult {

	private final DistOpfStatus status;
	private final double objectiveValue;
	private final double maxConstraintResidual;
	private final double[] primalVariables;
	private final String message;
	private final List<String> bindingConstraints;
	private final List<String> diagnostics;

	public DistOpfSolverResult(DistOpfStatus status, double objectiveValue,
			double maxConstraintResidual, double[] primalVariables, String message) {
		this(status, objectiveValue, maxConstraintResidual, primalVariables, message,
				Collections.<String>emptyList(), Collections.<String>emptyList());
	}

	public DistOpfSolverResult(DistOpfStatus status, double objectiveValue,
			double maxConstraintResidual, double[] primalVariables, String message,
			List<String> bindingConstraints, List<String> diagnostics) {
		this.status = status;
		this.objectiveValue = objectiveValue;
		this.maxConstraintResidual = maxConstraintResidual;
		this.primalVariables = primalVariables;
		this.message = message;
		this.bindingConstraints = new ArrayList<String>(bindingConstraints);
		this.diagnostics = new ArrayList<String>(diagnostics);
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

	public List<String> getBindingConstraints() {
		return Collections.unmodifiableList(bindingConstraints);
	}

	public List<String> getDiagnostics() {
		return Collections.unmodifiableList(diagnostics);
	}
}
