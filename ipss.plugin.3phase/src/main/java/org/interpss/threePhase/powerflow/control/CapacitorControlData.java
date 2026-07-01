package org.interpss.threePhase.powerflow.control;

import com.interpss.core.acsc.PhaseCode;

public class CapacitorControlData {
	public enum ControlType {
		VOLTAGE,
		CURRENT,
		KVAR,
		PF,
		TIME
	}

	public enum PhaseSelection {
		PHASE,
		AVG,
		MIN,
		MAX
	}

	private final String id;
	private final String capacitorId;
	private final String monitoredElementName;
	private final int terminal;
	private final ControlType controlType;
	private final double onSetting;
	private final double offSetting;
	private final double ptRatio;
	private final double ctRatio;
	private final boolean voltageOverride;
	private final double vMin;
	private final double vMax;
	private final double onDelaySeconds;
	private final double offDelaySeconds;
	private final PhaseCode phaseCode;
	private final PhaseSelection phaseSelection;
	private boolean closed = true;

	public CapacitorControlData(String id, String capacitorId, String monitoredElementName, int terminal,
			ControlType controlType, double onSetting, double offSetting, double ptRatio, double ctRatio,
			boolean voltageOverride, double vMin, double vMax, double onDelaySeconds, double offDelaySeconds,
			PhaseCode phaseCode, PhaseSelection phaseSelection) {
		this.id = id;
		this.capacitorId = capacitorId;
		this.monitoredElementName = monitoredElementName;
		this.terminal = terminal <= 0 ? 1 : terminal;
		this.controlType = controlType == null ? ControlType.VOLTAGE : controlType;
		this.onSetting = onSetting;
		this.offSetting = offSetting;
		this.ptRatio = ptRatio <= 0.0 ? 1.0 : ptRatio;
		this.ctRatio = ctRatio <= 0.0 ? 1.0 : ctRatio;
		this.voltageOverride = voltageOverride;
		this.vMin = vMin;
		this.vMax = vMax;
		this.onDelaySeconds = Math.max(0.0, onDelaySeconds);
		this.offDelaySeconds = Math.max(0.0, offDelaySeconds);
		this.phaseCode = phaseCode == null ? PhaseCode.ABC : phaseCode;
		this.phaseSelection = phaseSelection == null ? PhaseSelection.AVG : phaseSelection;
	}

	public String getId() {
		return id;
	}

	public String getCapacitorId() {
		return capacitorId;
	}

	public String getMonitoredElementName() {
		return monitoredElementName;
	}

	public boolean hasMonitoredElement() {
		return monitoredElementName != null && monitoredElementName.length() > 0;
	}

	public int getTerminal() {
		return terminal;
	}

	public ControlType getControlType() {
		return controlType;
	}

	public double getOnSetting() {
		return onSetting;
	}

	public double getOffSetting() {
		return offSetting;
	}

	public double getPtRatio() {
		return ptRatio;
	}

	public double getCtRatio() {
		return ctRatio;
	}

	public boolean isVoltageOverride() {
		return voltageOverride;
	}

	public double getVMin() {
		return vMin;
	}

	public double getVMax() {
		return vMax;
	}

	public double getOnDelaySeconds() {
		return onDelaySeconds;
	}

	public double getOffDelaySeconds() {
		return offDelaySeconds;
	}

	public PhaseCode getPhaseCode() {
		return phaseCode;
	}

	public PhaseSelection getPhaseSelection() {
		return phaseSelection;
	}

	public boolean isClosed() {
		return closed;
	}

	public void setClosed(boolean closed) {
		this.closed = closed;
	}
}
