package org.interpss.threePhase.opf.dist;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.interpss.threePhase.dynamic.DStabNetwork3Phase;

public class DistOpfResult {

	private final DistOpfStatus status;
	private final double objectiveValue;
	private final double maxConstraintResidual;
	private final List<String> warnings = new ArrayList<String>();

	public DistOpfResult(DistOpfStatus status, double objectiveValue, double maxConstraintResidual) {
		this.status = status;
		this.objectiveValue = objectiveValue;
		this.maxConstraintResidual = maxConstraintResidual;
	}

	public DistOpfStatus getStatus() {
		return status;
	}

	public boolean isSolved() {
		return status == DistOpfStatus.OPTIMAL || status == DistOpfStatus.FEASIBLE;
	}

	public double getObjectiveValue() {
		return objectiveValue;
	}

	public double getMaxConstraintResidual() {
		return maxConstraintResidual;
	}

	public List<String> getWarnings() {
		return Collections.unmodifiableList(warnings);
	}

	public DistOpfResult addWarning(String warning) {
		this.warnings.add(warning);
		return this;
	}

	public void applySetpointsToNetwork(DStabNetwork3Phase net) {
		// Setpoint mapping is added with DER extraction. Keep solve() non-mutating.
	}
}
