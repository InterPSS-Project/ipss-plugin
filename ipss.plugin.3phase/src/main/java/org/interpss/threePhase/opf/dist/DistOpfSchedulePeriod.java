package org.interpss.threePhase.opf.dist;

public class DistOpfSchedulePeriod {

	private final DistOpfOptions options;
	private final DistOpfControlMode controlMode;
	private final DistOpfObjective objective;

	public DistOpfSchedulePeriod(DistOpfOptions options, DistOpfControlMode controlMode,
			DistOpfObjective objective) {
		this.options = options == null ? new DistOpfOptions() : options;
		this.controlMode = controlMode == null ? DistOpfControlMode.NONE : controlMode;
		this.objective = objective == null ? DistOpfObjective.CURTAILMENT_MIN : objective;
	}

	public DistOpfOptions getOptions() {
		return options;
	}

	public DistOpfControlMode getControlMode() {
		return controlMode;
	}

	public DistOpfObjective getObjective() {
		return objective;
	}
}
