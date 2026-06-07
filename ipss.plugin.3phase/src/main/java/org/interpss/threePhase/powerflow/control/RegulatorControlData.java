package org.interpss.threePhase.powerflow.control;

import com.interpss.core.acsc.PhaseCode;

public class RegulatorControlData {
	public enum PhaseSelection {
		PHASE,
		MIN,
		MAX
	}

	private final String id;
	private final String branchName;
	private final int winding;
	private final int tapWinding;
	private final PhaseCode phaseCode;
	private final PhaseSelection phaseSelection;
	private final String regulatedBusId;
	private final double targetVoltage;
	private final double bandwidth;
	private final double ptRatio;
	private final double remotePtRatio;
	private final double ctPrim;
	private final double lineDropR;
	private final double lineDropX;
	private final double vLimit;
	private final double tapStep;
	private final int minTapPosition;
	private final int maxTapPosition;
	private final int maxTapChange;
	private int tapPosition;

	public RegulatorControlData(String id, String branchName, int winding, PhaseCode phaseCode,
			double targetVoltagePu, double bandwidthPu, double tapStep,
			int minTapPosition, int maxTapPosition) {
		this(id, branchName, winding, winding, phaseCode, PhaseSelection.PHASE, null,
				targetVoltagePu, bandwidthPu, 1.0, 1.0, 0.0, 0.0, 0.0, 0.0,
				tapStep, minTapPosition, maxTapPosition, Integer.MAX_VALUE);
	}

	public RegulatorControlData(String id, String branchName, int winding, int tapWinding, PhaseCode phaseCode,
			PhaseSelection phaseSelection, String regulatedBusId, double targetVoltage, double bandwidth,
			double ptRatio, double remotePtRatio, double ctPrim, double lineDropR, double lineDropX,
			double vLimit, double tapStep, int minTapPosition, int maxTapPosition, int maxTapChange) {
		this.id = id;
		this.branchName = branchName;
		this.winding = winding;
		this.tapWinding = tapWinding;
		this.phaseCode = phaseCode;
		this.phaseSelection = phaseSelection == null ? PhaseSelection.PHASE : phaseSelection;
		this.regulatedBusId = regulatedBusId;
		this.targetVoltage = targetVoltage;
		this.bandwidth = bandwidth;
		this.ptRatio = ptRatio;
		this.remotePtRatio = remotePtRatio;
		this.ctPrim = ctPrim;
		this.lineDropR = lineDropR;
		this.lineDropX = lineDropX;
		this.vLimit = vLimit;
		this.tapStep = tapStep;
		this.minTapPosition = minTapPosition;
		this.maxTapPosition = maxTapPosition;
		this.maxTapChange = maxTapChange;
	}

	public String getId() {
		return id;
	}

	public String getBranchName() {
		return branchName;
	}

	public int getWinding() {
		return winding;
	}

	public int getTapWinding() {
		return tapWinding;
	}

	public PhaseCode getPhaseCode() {
		return phaseCode;
	}

	public PhaseSelection getPhaseSelection() {
		return phaseSelection;
	}

	public String getRegulatedBusId() {
		return regulatedBusId;
	}

	public boolean hasRegulatedBus() {
		return regulatedBusId != null && regulatedBusId.length() > 0;
	}

	public double getTargetVoltage() {
		return targetVoltage;
	}

	public double getBandwidth() {
		return bandwidth;
	}

	public double getPtRatio() {
		return ptRatio;
	}

	public double getRemotePtRatio() {
		return remotePtRatio;
	}

	public double getCtPrim() {
		return ctPrim;
	}

	public double getLineDropR() {
		return lineDropR;
	}

	public double getLineDropX() {
		return lineDropX;
	}

	public boolean hasLineDropCompensation() {
		return Math.abs(lineDropR) > 0.0 || Math.abs(lineDropX) > 0.0;
	}

	public double getVLimit() {
		return vLimit;
	}

	public boolean hasVLimit() {
		return vLimit > 0.0;
	}

	public double getTargetVoltagePu() {
		return targetVoltage;
	}

	public double getBandwidthPu() {
		return bandwidth;
	}

	public double getTapStep() {
		return tapStep;
	}

	public int getMinTapPosition() {
		return minTapPosition;
	}

	public int getMaxTapPosition() {
		return maxTapPosition;
	}

	public int getMaxTapChange() {
		return maxTapChange;
	}

	public int getTapPosition() {
		return tapPosition;
	}

	public void setTapPosition(int tapPosition) {
		this.tapPosition = Math.max(this.minTapPosition, Math.min(this.maxTapPosition, tapPosition));
	}

	public double getTapRatio() {
		return 1.0 + this.tapPosition * this.tapStep;
	}
}
