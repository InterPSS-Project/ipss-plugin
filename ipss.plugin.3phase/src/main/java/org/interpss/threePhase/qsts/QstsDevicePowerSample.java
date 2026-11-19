package org.interpss.threePhase.qsts;

/**
 * Power for one device phase, expressed in per unit rather than kW/kvar.
 *
 * <p>Samples produced by {@code QstsStudy} retain the load and generator model
 * conventions. Let {@code B = network.getBaseKva()} be the full three-phase
 * network power base used for the study:
 * <ul>
 * <li>{@code deviceClass = "load"}: the power base is {@code B / 3} kVA.
 *     Active power in kW is {@code getP() * B / 3}; reactive power in kvar is
 *     {@code getQ() * B / 3}.</li>
 * <li>{@code deviceClass = "generator"}: the power base is {@code B} kVA.
 *     Active power in kW is {@code getP() * B}; reactive power in kvar is
 *     {@code getQ() * B}. This includes PV and storage generator samples.</li>
 * </ul>
 * <p>Each sample describes only the phase returned by {@link #getPhase()}.
 * Sum a device's phase samples and apply the corresponding base to obtain its
 * total kW/kvar. The load divisor remains three for single-phase and split-phase
 * devices; it is not the number of sampled phases. For example, on a 1000 kVA
 * network, a phase load of 15 kW is {@code p = 0.045}, while a phase generator
 * output of 15 kW is {@code p = 0.015}.
 *
 * <p>Positive load power denotes consumption; positive generator power denotes
 * injection. Storage charging therefore has negative generator active power.
 * These conventions require manually assembled network devices to use the same
 * load/generator normalization as the feeder models. This value object stores
 * neither the network base nor a conversion factor; callers must retain the
 * study's network base when converting results.
 */
public class QstsDevicePowerSample {
	private final int stepIndex;
	private final double hour;
	private final String deviceClass;
	private final String deviceId;
	private final String phase;
	private final double p;
	private final double q;

	/**
	 * Creates a phase sample with per-unit {@code p} and {@code q} on the
	 * device-class-specific power base documented by this class.
	 */
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

	/**
	 * Returns this phase's active power in per unit.
	 *
	 * @return per-unit active power; multiply by {@code networkBaseKva / 3} for
	 *         loads, or by {@code networkBaseKva} for generators, to obtain kW
	 * @see QstsDevicePowerSample
	 */
	public double getP() {
		return p;
	}

	/**
	 * Returns this phase's reactive power in per unit.
	 *
	 * @return per-unit reactive power; multiply by {@code networkBaseKva / 3} for
	 *         loads, or by {@code networkBaseKva} for generators, to obtain kvar
	 * @see QstsDevicePowerSample
	 */
	public double getQ() {
		return q;
	}
}
