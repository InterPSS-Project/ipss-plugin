package org.interpss.threePhase.qsts;

public class QstsInverterControlSample {
	private final int stepIndex;
	private final double hour;
	private final String controlId;
	private final String generatorId;
	private final String mode;
	private final boolean applied;
	private final double activePowerKw;
	private final double reactivePowerKvar;
	private final boolean limited;
	private final String reason;

	public QstsInverterControlSample(int stepIndex, double hour, String controlId,
			String generatorId, String mode, boolean applied, double activePowerKw,
			double reactivePowerKvar, boolean limited, String reason) {
		this.stepIndex = stepIndex;
		this.hour = hour;
		this.controlId = controlId;
		this.generatorId = generatorId;
		this.mode = mode;
		this.applied = applied;
		this.activePowerKw = activePowerKw;
		this.reactivePowerKvar = reactivePowerKvar;
		this.limited = limited;
		this.reason = reason == null ? "" : reason;
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

	public String getGeneratorId() {
		return generatorId;
	}

	public String getMode() {
		return mode;
	}

	public boolean isApplied() {
		return applied;
	}

	public double getActivePowerKw() {
		return activePowerKw;
	}

	public double getReactivePowerKvar() {
		return reactivePowerKvar;
	}

	public boolean isLimited() {
		return limited;
	}

	public String getReason() {
		return reason;
	}
}
