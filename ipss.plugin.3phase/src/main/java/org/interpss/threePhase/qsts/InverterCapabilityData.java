package org.interpss.threePhase.qsts;

public class InverterCapabilityData {
	private final double ratedKva;
	private final double availableActivePowerKw;
	private final double minReactivePowerKvar;
	private final double maxReactivePowerKvar;
	private final double cutInPowerKw;
	private final double cutOutPowerKw;
	private final boolean enabled;

	public InverterCapabilityData(double ratedKva, double availableActivePowerKw,
			double minReactivePowerKvar, double maxReactivePowerKvar,
			double cutInPowerKw, double cutOutPowerKw, boolean enabled) {
		this.ratedKva = Math.max(0.0, ratedKva);
		this.availableActivePowerKw = availableActivePowerKw;
		this.minReactivePowerKvar = minReactivePowerKvar;
		this.maxReactivePowerKvar = maxReactivePowerKvar;
		this.cutInPowerKw = Math.max(0.0, cutInPowerKw);
		this.cutOutPowerKw = Math.max(0.0, cutOutPowerKw);
		this.enabled = enabled;
	}

	public static InverterCapabilityData none() {
		return new InverterCapabilityData(0.0, Double.NaN, Double.NaN, Double.NaN,
				0.0, 0.0, true);
	}

	public double getRatedKva() {
		return ratedKva;
	}

	public double getAvailableActivePowerKw() {
		return availableActivePowerKw;
	}

	public double getMinReactivePowerKvar() {
		return minReactivePowerKvar;
	}

	public double getMaxReactivePowerKvar() {
		return maxReactivePowerKvar;
	}

	public double getCutInPowerKw() {
		return cutInPowerKw;
	}

	public double getCutOutPowerKw() {
		return cutOutPowerKw;
	}

	public boolean isEnabled() {
		return enabled;
	}
}
