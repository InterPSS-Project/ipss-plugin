package org.interpss.threePhase.dataParser.opendss;

import java.util.ArrayList;
import java.util.List;

import org.interpss.threePhase.basic.dstab.DStab3PBranch;

import com.interpss.core.acsc.PhaseCode;

public class OpenDSSRegulatorParser {

	private final OpenDSSDataParser dataParser;
	private final List<RegControlData> regControls = new ArrayList<RegControlData>();

	public OpenDSSRegulatorParser(OpenDSSDataParser openDSSDataParser) {
		this.dataParser = openDSSDataParser;
	}

	public boolean parseRegControlData(String regControlStr) {
		String[] tokens = regControlStr.toLowerCase().trim().split("\\s+");
		String transformerName = "";
		int winding = 2;
		double vreg = 0.0;
		double ptratio = 0.0;
		for (String token : tokens) {
			if (token.startsWith("transformer=")) {
				transformerName = token.substring(12);
			}
			else if (token.startsWith("winding=")) {
				winding = Integer.valueOf(token.substring(8)).intValue();
			}
			else if (token.startsWith("vreg=")) {
				vreg = Double.valueOf(token.substring(5)).doubleValue();
			}
			else if (token.startsWith("ptratio=")) {
				ptratio = Double.valueOf(token.substring(8)).doubleValue();
			}
		}
		if (transformerName.length() == 0 || vreg <= 0.0 || ptratio <= 0.0) {
			return true;
		}
		regControls.add(new RegControlData(transformerName, winding, vreg * ptratio));
		return true;
	}

	public void applyFixedRegControlRatios() {
		for (RegControlData control : regControls) {
			DStab3PBranch transformer = dataParser.getBranchByName(dataParser.getBusIdPrefix() + control.transformerName);
			if (transformer == null) {
				transformer = dataParser.getBranchByName(control.transformerName);
			}
			if (transformer == null) {
				continue;
			}
			double controlledWindingVoltage = control.controlledWindingVoltage;
			if (transformer.getPhaseCode() == PhaseCode.ABC) {
				controlledWindingVoltage *= Math.sqrt(3.0);
			}
			if (control.winding == 1) {
				transformer.setFromTurnRatio(controlledWindingVoltage);
			}
			else {
				transformer.setToTurnRatio(controlledWindingVoltage);
			}
		}
	}

	private static class RegControlData {
		private final String transformerName;
		private final int winding;
		private final double controlledWindingVoltage;

		private RegControlData(String transformerName, int winding, double controlledWindingVoltage) {
			this.transformerName = transformerName;
			this.winding = winding;
			this.controlledWindingVoltage = controlledWindingVoltage;
		}
	}
}
