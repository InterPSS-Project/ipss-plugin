package org.interpss.threePhase.opf.dist.constraint;

import org.interpss.plugin.opf.OpfSolverFactory;
import org.interpss.plugin.opf.constraint.OpfConstraint;

import com.interpss.opf.datatype.OpfConstraintType;

import cern.colt.list.DoubleArrayList;
import cern.colt.list.IntArrayList;

public final class DistOpfConstraintFactory {

	private static final double DEFAULT_LOWER = -1.0e20;
	private static final double DEFAULT_UPPER = 1.0e20;

	private DistOpfConstraintFactory() {
	}

	public static OpfConstraint equality(int id, String description, double rhs, int[] columns, double[] values) {
		return create(id, description, rhs, rhs, OpfConstraintType.EQUALITY, columns, values);
	}

	public static OpfConstraint lessThan(int id, String description, double upper, int[] columns, double[] values) {
		return create(id, description, upper, DEFAULT_LOWER, OpfConstraintType.LESS_THAN, columns, values);
	}

	public static OpfConstraint greaterThan(int id, String description, double lower, int[] columns, double[] values) {
		return create(id, description, DEFAULT_UPPER, lower, OpfConstraintType.LARGER_THAN, columns, values);
	}

	public static OpfConstraint bounded(int id, String description, double lower, double upper,
			int[] columns, double[] values) {
		return create(id, description, upper, lower, OpfConstraintType.LESS_THAN, columns, values);
	}

	private static OpfConstraint create(int id, String description, double upper, double lower,
			OpfConstraintType type, int[] columns, double[] values) {
		if (columns.length != values.length) {
			throw new IllegalArgumentException("Constraint column and value arrays must have the same length");
		}
		IntArrayList colNo = new IntArrayList();
		DoubleArrayList val = new DoubleArrayList();
		for (int i = 0; i < columns.length; i++) {
			colNo.add(columns[i]);
			val.add(values[i]);
		}
		return OpfSolverFactory.createOpfConstraint(id, description, upper, lower, type, colNo, val);
	}
}
