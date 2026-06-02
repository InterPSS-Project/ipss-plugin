package org.interpss.threePhase.opf.dist.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.interpss.plugin.opf.constraint.OpfConstraint;

public class DistOpfModel {

	private final DistOpfVariableIndex variableIndex;
	private final DistOpfModelData modelData;
	private final List<OpfConstraint> constraints = new ArrayList<OpfConstraint>();
	private double[] linearObjective = new double[0];
	private double[] lowerBounds = new double[0];
	private double[] upperBounds = new double[0];
	private boolean[] integerVariables = new boolean[0];

	public DistOpfModel(DistOpfVariableIndex variableIndex) {
		this(null, variableIndex);
	}

	public DistOpfModel(DistOpfModelData modelData, DistOpfVariableIndex variableIndex) {
		this.modelData = modelData;
		this.variableIndex = variableIndex;
	}

	public DistOpfModelData getModelData() {
		return modelData;
	}

	public DistOpfVariableIndex getVariableIndex() {
		return variableIndex;
	}

	public int getNumberOfVariables() {
		return variableIndex.size();
	}

	public List<OpfConstraint> getConstraints() {
		return Collections.unmodifiableList(constraints);
	}

	public List<OpfConstraint> getMutableConstraints() {
		return constraints;
	}

	public void addConstraint(OpfConstraint constraint) {
		this.constraints.add(constraint);
	}

	public double[] getLinearObjective() {
		return linearObjective;
	}

	public void setLinearObjective(double[] linearObjective) {
		this.linearObjective = linearObjective;
	}

	public double[] getLowerBounds() {
		return lowerBounds;
	}

	public void setLowerBounds(double[] lowerBounds) {
		this.lowerBounds = lowerBounds;
	}

	public double[] getUpperBounds() {
		return upperBounds;
	}

	public void setUpperBounds(double[] upperBounds) {
		this.upperBounds = upperBounds;
	}

	public boolean[] getIntegerVariables() {
		return integerVariables;
	}

	public void setIntegerVariables(boolean[] integerVariables) {
		this.integerVariables = integerVariables;
	}

	public void setBinaryVariable(int index) {
		setIntegerVariableBounds(index, 0.0, 1.0);
	}

	public void setIntegerVariableBounds(int index, double lower, double upper) {
		ensureVariableMetadata();
		lowerBounds[index] = lower;
		upperBounds[index] = upper;
		integerVariables[index] = true;
	}

	private void ensureVariableMetadata() {
		int size = getNumberOfVariables();
		if (lowerBounds.length != size) {
			lowerBounds = boundedArray(size, Double.NEGATIVE_INFINITY);
		}
		if (upperBounds.length != size) {
			upperBounds = boundedArray(size, Double.POSITIVE_INFINITY);
		}
		if (integerVariables.length != size) {
			integerVariables = new boolean[size];
		}
	}

	private static double[] boundedArray(int size, double value) {
		double[] values = new double[size];
		for (int i = 0; i < size; i++) {
			values[i] = value;
		}
		return values;
	}
}
