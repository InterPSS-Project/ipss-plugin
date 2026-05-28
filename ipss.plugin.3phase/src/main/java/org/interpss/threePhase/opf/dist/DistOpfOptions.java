package org.interpss.threePhase.opf.dist;

public class DistOpfOptions {

	private double minVoltagePu = 0.95;
	private double maxVoltagePu = 1.05;
	private double solverTolerance = 1.0e-8;
	private boolean includeBranchThermalLimits = true;
	private boolean validateWithPowerFlow = true;
	private boolean fixedCapacitors = true;
	private boolean fixedRegulators = true;
	private int maxPowerFlowIterations = 50;
	private double powerFlowTolerance = 1.0e-6;
	private Double targetSubstationPPu;
	private Double targetSubstationQPu;

	public double getMinVoltagePu() {
		return minVoltagePu;
	}

	public DistOpfOptions setMinVoltagePu(double minVoltagePu) {
		this.minVoltagePu = minVoltagePu;
		return this;
	}

	public double getMaxVoltagePu() {
		return maxVoltagePu;
	}

	public DistOpfOptions setMaxVoltagePu(double maxVoltagePu) {
		this.maxVoltagePu = maxVoltagePu;
		return this;
	}

	public double getSolverTolerance() {
		return solverTolerance;
	}

	public DistOpfOptions setSolverTolerance(double solverTolerance) {
		this.solverTolerance = solverTolerance;
		return this;
	}

	public boolean isIncludeBranchThermalLimits() {
		return includeBranchThermalLimits;
	}

	public DistOpfOptions setIncludeBranchThermalLimits(boolean includeBranchThermalLimits) {
		this.includeBranchThermalLimits = includeBranchThermalLimits;
		return this;
	}

	public boolean isValidateWithPowerFlow() {
		return validateWithPowerFlow;
	}

	public DistOpfOptions setValidateWithPowerFlow(boolean validateWithPowerFlow) {
		this.validateWithPowerFlow = validateWithPowerFlow;
		return this;
	}

	public boolean isFixedCapacitors() {
		return fixedCapacitors;
	}

	public DistOpfOptions setFixedCapacitors(boolean fixedCapacitors) {
		this.fixedCapacitors = fixedCapacitors;
		return this;
	}

	public boolean isFixedRegulators() {
		return fixedRegulators;
	}

	public DistOpfOptions setFixedRegulators(boolean fixedRegulators) {
		this.fixedRegulators = fixedRegulators;
		return this;
	}

	public int getMaxPowerFlowIterations() {
		return maxPowerFlowIterations;
	}

	public DistOpfOptions setMaxPowerFlowIterations(int maxPowerFlowIterations) {
		this.maxPowerFlowIterations = maxPowerFlowIterations;
		return this;
	}

	public double getPowerFlowTolerance() {
		return powerFlowTolerance;
	}

	public DistOpfOptions setPowerFlowTolerance(double powerFlowTolerance) {
		this.powerFlowTolerance = powerFlowTolerance;
		return this;
	}

	public Double getTargetSubstationPPu() {
		return targetSubstationPPu;
	}

	public DistOpfOptions setTargetSubstationPPu(double targetSubstationPPu) {
		this.targetSubstationPPu = Double.valueOf(targetSubstationPPu);
		return this;
	}

	public Double getTargetSubstationQPu() {
		return targetSubstationQPu;
	}

	public DistOpfOptions setTargetSubstationQPu(double targetSubstationQPu) {
		this.targetSubstationQPu = Double.valueOf(targetSubstationQPu);
		return this;
	}
}
