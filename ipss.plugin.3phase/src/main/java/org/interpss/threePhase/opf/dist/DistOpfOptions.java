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
	private int branchFlowLossIterations = 0;
	private double branchFlowLossTolerance = 1.0e-7;
	private double timeStepHours = 1.0;
	private DistOpfSolverType solverType = DistOpfSolverType.OJALGO;
	private DistOpfVoltageModel voltageModel = DistOpfVoltageModel.SQUARED_VOLTAGE;
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

	public int getBranchFlowLossIterations() {
		return branchFlowLossIterations;
	}

	public DistOpfOptions setBranchFlowLossIterations(int branchFlowLossIterations) {
		this.branchFlowLossIterations = branchFlowLossIterations;
		return this;
	}

	public double getBranchFlowLossTolerance() {
		return branchFlowLossTolerance;
	}

	public DistOpfOptions setBranchFlowLossTolerance(double branchFlowLossTolerance) {
		this.branchFlowLossTolerance = branchFlowLossTolerance;
		return this;
	}

	public double getTimeStepHours() {
		return timeStepHours;
	}

	public DistOpfOptions setTimeStepHours(double timeStepHours) {
		this.timeStepHours = timeStepHours;
		return this;
	}

	public DistOpfSolverType getSolverType() {
		return solverType;
	}

	public DistOpfOptions setSolverType(DistOpfSolverType solverType) {
		this.solverType = solverType;
		return this;
	}

	public DistOpfVoltageModel getVoltageModel() {
		return voltageModel;
	}

	public DistOpfOptions setVoltageModel(DistOpfVoltageModel voltageModel) {
		this.voltageModel = voltageModel;
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
