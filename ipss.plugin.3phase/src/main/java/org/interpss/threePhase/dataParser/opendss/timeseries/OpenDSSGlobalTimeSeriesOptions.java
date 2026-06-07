package org.interpss.threePhase.dataParser.opendss.timeseries;

import org.interpss.threePhase.qsts.QstsGlobalOptions;

public class OpenDSSGlobalTimeSeriesOptions {
	private String mode;
	private Integer numberOfSteps;
	private Double stepSizeHours;
	private Double loadMult;
	private String controlMode;
	private Integer maxControl;
	private Double hour;

	public String getMode() {
		return mode;
	}

	public void setMode(String mode) {
		this.mode = mode;
	}

	public Integer getNumberOfSteps() {
		return numberOfSteps;
	}

	public void setNumberOfSteps(Integer numberOfSteps) {
		this.numberOfSteps = numberOfSteps;
	}

	public Double getStepSizeHours() {
		return stepSizeHours;
	}

	public void setStepSizeHours(Double stepSizeHours) {
		this.stepSizeHours = stepSizeHours;
	}

	public Double getLoadMult() {
		return loadMult;
	}

	public void setLoadMult(Double loadMult) {
		this.loadMult = loadMult;
	}

	public String getControlMode() {
		return controlMode;
	}

	public void setControlMode(String controlMode) {
		this.controlMode = controlMode;
	}

	public Integer getMaxControl() {
		return maxControl;
	}

	public void setMaxControl(Integer maxControl) {
		this.maxControl = maxControl;
	}

	public Double getHour() {
		return hour;
	}

	public void setHour(Double hour) {
		this.hour = hour;
	}

	public QstsGlobalOptions toQstsGlobalOptions() {
		return new QstsGlobalOptions(mode, numberOfSteps, stepSizeHours, loadMult, controlMode, maxControl, hour);
	}
}
