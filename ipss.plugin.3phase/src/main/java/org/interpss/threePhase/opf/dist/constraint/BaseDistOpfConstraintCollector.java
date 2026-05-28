package org.interpss.threePhase.opf.dist.constraint;

import java.util.List;

import org.interpss.plugin.opf.constraint.OpfConstraint;
import org.interpss.threePhase.opf.dist.model.DistOpfVariableIndex;

public abstract class BaseDistOpfConstraintCollector implements IDistOpfConstraintCollector {

	protected final DistOpfVariableIndex variableIndex;
	protected final List<OpfConstraint> constraints;

	protected BaseDistOpfConstraintCollector(DistOpfVariableIndex variableIndex,
			List<OpfConstraint> constraints) {
		this.variableIndex = variableIndex;
		this.constraints = constraints;
	}

	protected void addEquality(String description, double rhs, int[] columns, double[] values) {
		constraints.add(DistOpfConstraintFactory.equality(constraints.size(), description, rhs, columns, values));
	}

	protected void addLessThan(String description, double upper, int[] columns, double[] values) {
		constraints.add(DistOpfConstraintFactory.lessThan(constraints.size(), description, upper, columns, values));
	}

	protected void addGreaterThan(String description, double lower, int[] columns, double[] values) {
		constraints.add(DistOpfConstraintFactory.greaterThan(constraints.size(), description, lower, columns, values));
	}

	protected void addBounded(String description, double lower, double upper, int[] columns, double[] values) {
		constraints.add(DistOpfConstraintFactory.bounded(constraints.size(), description, lower, upper, columns, values));
	}
}
