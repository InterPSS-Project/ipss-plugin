package org.interpss.threePhase.qsts;

public class QstsCapacitorStateSample {
	private final int stepIndex;
	private final double hour;
	private final String capacitorId;
	private final boolean closed;
	private final double totalReactivePowerPu;
	private final double totalReactivePowerKvar;
	private final int operationCount;

	public QstsCapacitorStateSample(int stepIndex, double hour, String capacitorId, boolean closed,
			double totalReactivePowerPu, double totalReactivePowerKvar, int operationCount) {
		this.stepIndex = stepIndex;
		this.hour = hour;
		this.capacitorId = capacitorId;
		this.closed = closed;
		this.totalReactivePowerPu = totalReactivePowerPu;
		this.totalReactivePowerKvar = totalReactivePowerKvar;
		this.operationCount = Math.max(0, operationCount);
	}

	public int getStepIndex() {
		return stepIndex;
	}

	public double getHour() {
		return hour;
	}

	public String getCapacitorId() {
		return capacitorId;
	}

	public boolean isClosed() {
		return closed;
	}

	public double getTotalReactivePowerPu() {
		return totalReactivePowerPu;
	}

	public double getTotalReactivePowerKvar() {
		return totalReactivePowerKvar;
	}

	public int getOperationCount() {
		return operationCount;
	}
}
