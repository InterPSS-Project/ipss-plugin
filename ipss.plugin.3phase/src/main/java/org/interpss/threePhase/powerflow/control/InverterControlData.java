package org.interpss.threePhase.powerflow.control;

public class InverterControlData {
	public enum ControlMode {
		VOLTVAR,
		VOLTWATT,
		WATTPF,
		WATTVAR
	}

	private final String id;
	private final String generatorId;
	private final ControlMode controlMode;
	private final String curveId;
	private final double ratedKva;
	private final double minReactivePowerKvar;
	private final double maxReactivePowerKvar;
	private final double minPowerFactor;
	private final boolean enabled;

	public InverterControlData(String id, String generatorId, ControlMode controlMode, String curveId,
			double ratedKva, double minReactivePowerKvar, double maxReactivePowerKvar,
			double minPowerFactor, boolean enabled) {
		this.id = id;
		this.generatorId = generatorId;
		this.controlMode = controlMode == null ? ControlMode.VOLTVAR : controlMode;
		this.curveId = curveId == null ? "" : curveId;
		this.ratedKva = Math.max(0.0, ratedKva);
		this.minReactivePowerKvar = minReactivePowerKvar;
		this.maxReactivePowerKvar = maxReactivePowerKvar;
		this.minPowerFactor = minPowerFactor;
		this.enabled = enabled;
	}

	public String getId() {
		return id;
	}

	public String getGeneratorId() {
		return generatorId;
	}

	public ControlMode getControlMode() {
		return controlMode;
	}

	public String getCurveId() {
		return curveId;
	}

	public double getRatedKva() {
		return ratedKva;
	}

	public double getMinReactivePowerKvar() {
		return minReactivePowerKvar;
	}

	public double getMaxReactivePowerKvar() {
		return maxReactivePowerKvar;
	}

	public double getMinPowerFactor() {
		return minPowerFactor;
	}

	public boolean isEnabled() {
		return enabled;
	}
}
