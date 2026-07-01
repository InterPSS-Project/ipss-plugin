package org.interpss.threePhase.qsts;

public class QstsRegulatorTapSample {
	private final String caseName;
	private final int stepIndex;
	private final double hour;
	private final String controlId;
	private final String branchName;
	private final int winding;
	private final int tapWinding;
	private final String phase;
	private final int tapPosition;
	private final double tapRatio;
	private final double targetVoltage;
	private final double bandwidth;
	private final double ptRatio;
	private final double delaySeconds;
	private final String regulatedBusId;
	private final double lineDropR;
	private final double lineDropX;
	private final double voltageLimit;

	public QstsRegulatorTapSample(String caseName, int stepIndex, double hour, String controlId,
			String branchName, int winding, int tapWinding, String phase, int tapPosition,
			double tapRatio, double targetVoltage, double bandwidth, double ptRatio,
			double delaySeconds, String regulatedBusId, double lineDropR, double lineDropX,
			double voltageLimit) {
		this.caseName = caseName;
		this.stepIndex = stepIndex;
		this.hour = hour;
		this.controlId = controlId;
		this.branchName = branchName;
		this.winding = winding;
		this.tapWinding = tapWinding;
		this.phase = phase;
		this.tapPosition = tapPosition;
		this.tapRatio = tapRatio;
		this.targetVoltage = targetVoltage;
		this.bandwidth = bandwidth;
		this.ptRatio = ptRatio;
		this.delaySeconds = delaySeconds;
		this.regulatedBusId = regulatedBusId;
		this.lineDropR = lineDropR;
		this.lineDropX = lineDropX;
		this.voltageLimit = voltageLimit;
	}

	public String getCaseName() {
		return caseName;
	}

	public int getStepIndex() {
		return stepIndex;
	}

	public double getHour() {
		return hour;
	}

	public String getControlId() {
		return controlId;
	}

	public String getBranchName() {
		return branchName;
	}

	public int getWinding() {
		return winding;
	}

	public int getTapWinding() {
		return tapWinding;
	}

	public String getPhase() {
		return phase;
	}

	public int getTapPosition() {
		return tapPosition;
	}

	public double getTapRatio() {
		return tapRatio;
	}

	public double getTargetVoltage() {
		return targetVoltage;
	}

	public double getBandwidth() {
		return bandwidth;
	}

	public double getPtRatio() {
		return ptRatio;
	}

	public double getDelaySeconds() {
		return delaySeconds;
	}

	public String getRegulatedBusId() {
		return regulatedBusId;
	}

	public double getLineDropR() {
		return lineDropR;
	}

	public double getLineDropX() {
		return lineDropX;
	}

	public double getVoltageLimit() {
		return voltageLimit;
	}
}
