package org.interpss.threePhase.qsts;

public class StorageControlData {
	public enum DispatchMode {
		FOLLOW,
		PEAK_SHAVE,
		SUPPORT,
		TIME,
		SCHEDULE
	}

	public enum ChargeMode {
		OFF,
		TIME,
		LOW_LOAD,
		SCHEDULE
	}

	private final String id;
	private final String storageId;
	private final DispatchMode dispatchMode;
	private final ChargeMode chargeMode;
	private final String monitoredElementId;
	private final int monitoredTerminal;
	private final double dischargeTargetKw;
	private final double chargeTargetKw;
	private final double dischargeDeadbandKw;
	private final double chargeDeadbandKw;
	private final double maxDischargePowerKw;
	private final double maxChargePowerKw;
	private final double reserveKwh;
	private final boolean enabled;

	public StorageControlData(String id, String storageId, DispatchMode dispatchMode,
			ChargeMode chargeMode, String monitoredElementId, int monitoredTerminal,
			double dischargeTargetKw, double chargeTargetKw, double dischargeDeadbandKw,
			double chargeDeadbandKw, double maxDischargePowerKw, double maxChargePowerKw,
			double reserveKwh, boolean enabled) {
		this.id = id == null ? "" : id.trim();
		this.storageId = storageId == null ? "" : storageId.trim();
		this.dispatchMode = dispatchMode == null ? DispatchMode.FOLLOW : dispatchMode;
		this.chargeMode = chargeMode == null ? ChargeMode.OFF : chargeMode;
		this.monitoredElementId = monitoredElementId == null ? "" : monitoredElementId.trim();
		this.monitoredTerminal = Math.max(1, monitoredTerminal);
		this.dischargeTargetKw = dischargeTargetKw;
		this.chargeTargetKw = chargeTargetKw;
		this.dischargeDeadbandKw = nonNegativeOrNaN(dischargeDeadbandKw);
		this.chargeDeadbandKw = nonNegativeOrNaN(chargeDeadbandKw);
		this.maxDischargePowerKw = nonNegativeOrNaN(maxDischargePowerKw);
		this.maxChargePowerKw = nonNegativeOrNaN(maxChargePowerKw);
		this.reserveKwh = nonNegativeOrNaN(reserveKwh);
		this.enabled = enabled;
	}

	public String getId() {
		return id;
	}

	public String getStorageId() {
		return storageId;
	}

	public DispatchMode getDispatchMode() {
		return dispatchMode;
	}

	public ChargeMode getChargeMode() {
		return chargeMode;
	}

	public String getMonitoredElementId() {
		return monitoredElementId;
	}

	public int getMonitoredTerminal() {
		return monitoredTerminal;
	}

	public double getDischargeTargetKw() {
		return dischargeTargetKw;
	}

	public double getChargeTargetKw() {
		return chargeTargetKw;
	}

	public double getDischargeDeadbandKw() {
		return dischargeDeadbandKw;
	}

	public double getChargeDeadbandKw() {
		return chargeDeadbandKw;
	}

	public double getMaxDischargePowerKw() {
		return maxDischargePowerKw;
	}

	public double getMaxChargePowerKw() {
		return maxChargePowerKw;
	}

	public double getReserveKwh() {
		return reserveKwh;
	}

	public boolean isEnabled() {
		return enabled;
	}

	private static double nonNegativeOrNaN(double value) {
		if(!Double.isFinite(value)) {
			return Double.NaN;
		}
		return Math.max(0.0, value);
	}
}
