package org.interpss.threePhase.qsts;

public class QstsGlobalOptions {
	private final String mode;
	private final Integer numberOfSteps;
	private final Double stepSizeHours;
	private final Double loadMult;
	private final String controlMode;
	private final Integer maxControl;
	private final Double hour;

	public QstsGlobalOptions(String mode, Integer numberOfSteps, Double stepSizeHours, Double loadMult,
			String controlMode, Integer maxControl, Double hour) {
		this.mode = mode;
		this.numberOfSteps = numberOfSteps;
		this.stepSizeHours = stepSizeHours;
		this.loadMult = loadMult;
		this.controlMode = controlMode;
		this.maxControl = maxControl;
		this.hour = hour;
	}

	public String getMode() {
		return mode;
	}

	public Integer getNumberOfSteps() {
		return numberOfSteps;
	}

	public Double getStepSizeHours() {
		return stepSizeHours;
	}

	public Double getLoadMult() {
		return loadMult;
	}

	public String getControlMode() {
		return controlMode;
	}

	public Integer getMaxControl() {
		return maxControl;
	}

	public Double getHour() {
		return hour;
	}
}
