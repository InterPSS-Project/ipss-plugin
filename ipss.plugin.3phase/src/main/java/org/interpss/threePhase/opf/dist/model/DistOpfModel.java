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
}
