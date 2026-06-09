package org.interpss.threePhase.qsts;

public class QstsBusVoltageSample {
	private final int stepIndex;
	private final double hour;
	private final String busId;
	private final String phase;
	private final double magnitude;
	private final double angleDegrees;

	public QstsBusVoltageSample(int stepIndex, double hour, String busId, String phase,
			double magnitude, double angleDegrees) {
		this.stepIndex = stepIndex;
		this.hour = hour;
		this.busId = busId;
		this.phase = phase;
		this.magnitude = magnitude;
		this.angleDegrees = angleDegrees;
	}

	public int getStepIndex() {
		return stepIndex;
	}

	public double getHour() {
		return hour;
	}

	public String getBusId() {
		return busId;
	}

	public String getPhase() {
		return phase;
	}

	public double getMagnitude() {
		return magnitude;
	}

	public double getAngleDegrees() {
		return angleDegrees;
	}
}
