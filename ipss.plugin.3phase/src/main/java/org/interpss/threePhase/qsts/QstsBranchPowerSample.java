package org.interpss.threePhase.qsts;

public class QstsBranchPowerSample {
	private final int stepIndex;
	private final double hour;
	private final String elementClass;
	private final String elementId;
	private final int terminal;
	private final String busId;
	private final String phase;
	private final double activePowerKw;
	private final double reactivePowerKvar;

	public QstsBranchPowerSample(int stepIndex, double hour, String elementClass, String elementId,
			int terminal, String busId, String phase, double activePowerKw, double reactivePowerKvar) {
		this.stepIndex = stepIndex;
		this.hour = hour;
		this.elementClass = elementClass;
		this.elementId = elementId;
		this.terminal = terminal;
		this.busId = busId;
		this.phase = phase;
		this.activePowerKw = activePowerKw;
		this.reactivePowerKvar = reactivePowerKvar;
	}

	public int getStepIndex() {
		return stepIndex;
	}

	public double getHour() {
		return hour;
	}

	public String getElementClass() {
		return elementClass;
	}

	public String getElementId() {
		return elementId;
	}

	public int getTerminal() {
		return terminal;
	}

	public String getBusId() {
		return busId;
	}

	public String getPhase() {
		return phase;
	}

	public double getActivePowerKw() {
		return activePowerKw;
	}

	public double getReactivePowerKvar() {
		return reactivePowerKvar;
	}
}
