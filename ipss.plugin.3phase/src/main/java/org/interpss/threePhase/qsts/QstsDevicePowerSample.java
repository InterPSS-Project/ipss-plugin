package org.interpss.threePhase.qsts;

public class QstsDevicePowerSample {
	private final int stepIndex;
	private final double hour;
	private final String deviceClass;
	private final String deviceId;
	private final String phase;
	private final double p;
	private final double q;

	public QstsDevicePowerSample(int stepIndex, double hour, String deviceClass, String deviceId,
			String phase, double p, double q) {
		this.stepIndex = stepIndex;
		this.hour = hour;
		this.deviceClass = deviceClass;
		this.deviceId = deviceId;
		this.phase = phase;
		this.p = p;
		this.q = q;
	}

	public int getStepIndex() {
		return stepIndex;
	}

	public double getHour() {
		return hour;
	}

	public String getDeviceClass() {
		return deviceClass;
	}

	public String getDeviceId() {
		return deviceId;
	}

	public String getPhase() {
		return phase;
	}

	public double getP() {
		return p;
	}

	public double getQ() {
		return q;
	}
}
