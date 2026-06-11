package org.interpss.threePhase.dataParser.opendss;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.interpss.threePhase.powerflow.control.RegulatorControlData;
import org.interpss.threePhase.powerflow.control.RegulatorControlData.PhaseSelection;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.acsc.PhaseCode;
import com.interpss.core.threephase.IBranch3Phase;

public class OpenDSSRegulatorParser {

	private final OpenDSSDataParser dataParser;
	private final List<OpenDssRegControlData> regControls = new ArrayList<OpenDssRegControlData>();
	private final Map<String, OpenDssRegControlData> regControlsById = new HashMap<>();

	public OpenDSSRegulatorParser(OpenDSSDataParser openDSSDataParser) {
		this.dataParser = openDSSDataParser;
	}

	public boolean parseRegControlData(String regControlStr) {
		String[] tokens = splitDssTokens(regControlStr.toLowerCase().trim().replaceAll("\\s*=\\s*", "="));
		String id = "";
		String likeId = "";
		String transformerName = "";
		int winding = 2;
		boolean windingSet = false;
		int tapWinding = 0;
		boolean tapWindingSet = false;
		double vreg = 0.0;
		double band = 3.0;
		boolean bandSet = false;
		double ptratio = 0.0;
		double remotePtratio = 0.0;
		double ctPrim = 300.0;
		double r = 0.0;
		double x = 0.0;
		String regulatedBus = "";
		PhaseSelection phaseSelection = PhaseSelection.PHASE;
		PhaseCode ptPhaseCode = null;
		double vLimit = 0.0;
		int maxTapChange = 16;
		double delaySeconds = 0.0;
		double tapStep = 0.00625;
		int minTap = -16;
		int maxTap = 16;
		List<String> propertyTokens = new ArrayList<>();
		for (String token : tokens) {
			if(token.contains("regcontrol.")) {
				String suffix = token.substring(token.indexOf("regcontrol.") + 11);
				int propertySeparator = suffix.indexOf('.');
				if(propertySeparator >= 0) {
					id = suffix.substring(0, propertySeparator);
					propertyTokens.add(suffix.substring(propertySeparator + 1));
				}
				else {
					id = suffix;
				}
			}
			else {
				propertyTokens.add(token);
			}
		}
		OpenDssRegControlData existing = id.length() == 0 ? null : this.regControlsById.get(id);
		OpenDssRegControlData template = likeId.length() == 0 ? null : this.regControlsById.get(likeId);
		if(existing != null) {
			template = existing;
		}
		for (String token : propertyTokens) {
			if (token.startsWith("like=")) {
				likeId = token.substring(5);
				template = this.regControlsById.get(likeId);
			}
			else if (token.startsWith("transformer=")) {
				transformerName = token.substring(12);
			}
			else if (token.startsWith("winding=")) {
				winding = Integer.valueOf(token.substring(8)).intValue();
				windingSet = true;
			}
			else if (token.startsWith("vreg=")) {
				vreg = Double.valueOf(token.substring(5)).doubleValue();
			}
			else if (token.startsWith("band=")) {
				band = Double.valueOf(token.substring(5)).doubleValue();
				bandSet = true;
			}
			else if (token.startsWith("ptratio=")) {
				ptratio = Double.valueOf(token.substring(8)).doubleValue();
			}
			else if (token.startsWith("remoteptratio=")) {
				remotePtratio = Double.valueOf(token.substring(14)).doubleValue();
			}
			else if (token.startsWith("ctprim=")) {
				ctPrim = Double.valueOf(token.substring(7)).doubleValue();
			}
			else if (token.startsWith("r=")) {
				r = Double.valueOf(token.substring(2)).doubleValue();
			}
			else if (token.startsWith("x=")) {
				x = Double.valueOf(token.substring(2)).doubleValue();
			}
			else if (token.startsWith("bus=")) {
				regulatedBus = token.substring(4);
			}
			else if (token.startsWith("ptphase=")) {
				String value = token.substring(8);
				if("max".equals(value)) {
					phaseSelection = PhaseSelection.MAX;
				}
				else if("min".equals(value)) {
					phaseSelection = PhaseSelection.MIN;
				}
				else {
					ptPhaseCode = phaseCode(value);
				}
			}
			else if (token.startsWith("tapwinding=")) {
				tapWinding = Integer.valueOf(token.substring(11)).intValue();
				tapWindingSet = true;
			}
			else if (token.startsWith("vlimit=")) {
				vLimit = Double.valueOf(token.substring(7)).doubleValue();
			}
			else if (token.startsWith("maxtapchange=")) {
				maxTapChange = Integer.valueOf(token.substring(13)).intValue();
			}
			else if (token.startsWith("delay=")) {
				delaySeconds = Double.valueOf(token.substring(6)).doubleValue();
			}
			else if (token.startsWith("mintap=")) {
				double minTapRatio = Double.valueOf(token.substring(7)).doubleValue();
				minTap = (int) Math.round((minTapRatio - 1.0) / tapStep);
			}
			else if (token.startsWith("maxtap=")) {
				double maxTapRatio = Double.valueOf(token.substring(7)).doubleValue();
				maxTap = (int) Math.round((maxTapRatio - 1.0) / tapStep);
			}
			else if (token.startsWith("numtaps=")) {
				int halfRange = Math.max(1, Integer.valueOf(token.substring(8)).intValue() / 2);
				minTap = -halfRange;
				maxTap = halfRange;
			}
		}
		if(template != null) {
			if(transformerName.length() == 0) {
				transformerName = template.transformerName;
			}
			if(!windingSet) {
				winding = template.winding;
			}
			if(!tapWindingSet) {
				tapWinding = template.tapWinding;
			}
			if(vreg <= 0.0) {
				vreg = template.vreg;
			}
			if(!bandSet) {
				band = template.band;
			}
			if(ptratio <= 0.0) {
				ptratio = template.ptratio;
			}
			if(remotePtratio <= 0.0) {
				remotePtratio = template.remotePtratio;
			}
			ctPrim = tokenWasSpecified(propertyTokens, "ctprim=") ? ctPrim : template.ctPrim;
			r = tokenWasSpecified(propertyTokens, "r=") ? r : template.r;
			x = tokenWasSpecified(propertyTokens, "x=") ? x : template.x;
			if(regulatedBus.length() == 0) {
				regulatedBus = template.regulatedBus;
			}
			if(!tokenWasSpecified(propertyTokens, "ptphase=")) {
				phaseSelection = template.phaseSelection;
				ptPhaseCode = template.ptPhaseCode;
			}
			if(vLimit <= 0.0) {
				vLimit = template.vLimit;
			}
			if(!tokenWasSpecified(propertyTokens, "maxtapchange=")) {
				maxTapChange = template.maxTapChange;
			}
			if(!tokenWasSpecified(propertyTokens, "delay=")) {
				delaySeconds = template.delaySeconds;
			}
			tapStep = template.tapStep;
			minTap = template.minTap;
			maxTap = template.maxTap;
		}
		if(tapWinding <= 0) {
			tapWinding = winding;
		}
		if(remotePtratio <= 0.0) {
			remotePtratio = ptratio;
		}
		if (transformerName.length() == 0 || vreg <= 0.0 || ptratio <= 0.0) {
			return true;
		}
		OpenDssRegControlData control = new OpenDssRegControlData(id, transformerName, winding, vreg,
				band, ptratio, remotePtratio, ctPrim, r, x, regulatedBus, phaseSelection, ptPhaseCode,
				tapWinding, vLimit, maxTapChange, delaySeconds, tapStep, minTap, maxTap);
		if(existing == null) {
			regControls.add(control);
		}
		else {
			int index = this.regControls.indexOf(existing);
			if(index >= 0) {
				this.regControls.set(index, control);
			}
		}
		if(id.length() > 0) {
			this.regControlsById.put(id, control);
		}
		return true;
	}

	public int getRegControlCount() {
		return this.regControls.size();
	}

	public List<RegulatorControlData> toRegulatorControlData() {
		if(this.regControls.isEmpty()) {
			return Collections.emptyList();
		}
		List<RegulatorControlData> controls = new ArrayList<>();
		for(OpenDssRegControlData control : this.regControls) {
			AclfBranch transformer = findTransformer(control.transformerName);
			if(transformer == null) {
				continue;
			}
			PhaseCode transformerPhaseCode = ((IBranch3Phase) transformer).getPhaseCode();
			PhaseCode controlPhaseCode = control.ptPhaseCode == null ? transformerPhaseCode : control.ptPhaseCode;
			RegulatorControlData data = new RegulatorControlData(
					control.id.length() == 0 ? control.transformerName : control.id,
					transformer.getName(),
					control.winding,
					control.tapWinding,
					controlPhaseCode,
					control.phaseSelection,
					resolveRegulatedBusId(control.regulatedBus),
					control.vreg,
					control.band,
					control.ptratio,
					control.remotePtratio,
					control.ctPrim,
					control.r,
					control.x,
					control.vLimit,
					control.tapStep,
					control.minTap,
					control.maxTap,
					control.maxTapChange,
					control.delaySeconds);
			controls.add(data);
		}
		return controls;
	}

	private AclfBranch findTransformer(String transformerName) {
		AclfBranch transformer = dataParser.getThreePhaseBranchByName(dataParser.getBusIdPrefix() + transformerName);
		if (transformer == null) {
			transformer = dataParser.getThreePhaseBranchByName(transformerName);
		}
		return transformer;
	}

	private double transformerControlBaseLnVoltage(AclfBranch transformer, int winding) {
		double baseVoltage = winding == 1
				? transformer.getFromBus().getBaseVoltage()
				: transformer.getToBus().getBaseVoltage();
		return ((IBranch3Phase) transformer).getPhaseCode() == PhaseCode.ABC
				? baseVoltage / Math.sqrt(3.0)
				: baseVoltage;
	}

	private String resolveRegulatedBusId(String regulatedBus) {
		if(regulatedBus == null || regulatedBus.length() == 0) {
			return null;
		}
		String busId = regulatedBus.split("\\.")[0];
		return this.dataParser.getBusIdPrefix() + busId;
	}

	private static boolean tokenWasSpecified(List<String> tokens, String prefix) {
		for(String token : tokens) {
			if(token.startsWith(prefix)) {
				return true;
			}
		}
		return false;
	}

	private static PhaseCode phaseCode(String value) {
		if("1".equals(value) || "a".equals(value)) {
			return PhaseCode.A;
		}
		if("2".equals(value) || "b".equals(value)) {
			return PhaseCode.B;
		}
		if("3".equals(value) || "c".equals(value)) {
			return PhaseCode.C;
		}
		return PhaseCode.A;
	}

	private static String[] splitDssTokens(String value) {
		List<String> tokens = new ArrayList<>();
		StringBuilder token = new StringBuilder();
		int bracketDepth = 0;
		for(int i = 0; i < value.length(); i++) {
			char ch = value.charAt(i);
			if(ch == '[' || ch == '(') {
				bracketDepth++;
			}
			else if(ch == ']' || ch == ')') {
				bracketDepth = Math.max(0, bracketDepth - 1);
			}
			if(Character.isWhitespace(ch) && bracketDepth == 0) {
				if(token.length() > 0) {
					tokens.add(token.toString());
					token.setLength(0);
				}
			}
			else {
				token.append(ch);
			}
		}
		if(token.length() > 0) {
			tokens.add(token.toString());
		}
		return tokens.toArray(new String[0]);
	}

	private static class OpenDssRegControlData {
		private final String id;
		private final String transformerName;
		private final int winding;
		private final int tapWinding;
		private final double vreg;
		private final double band;
		private final double ptratio;
		private final double remotePtratio;
		private final double ctPrim;
		private final double r;
		private final double x;
		private final String regulatedBus;
		private final PhaseSelection phaseSelection;
		private final PhaseCode ptPhaseCode;
		private final double vLimit;
		private final int maxTapChange;
		private final double delaySeconds;
		private final double tapStep;
		private final int minTap;
		private final int maxTap;

		private OpenDssRegControlData(String id, String transformerName, int winding, double vreg, double band,
				double ptratio, double remotePtratio, double ctPrim, double r, double x, String regulatedBus,
				PhaseSelection phaseSelection, PhaseCode ptPhaseCode, int tapWinding, double vLimit,
				int maxTapChange, double delaySeconds, double tapStep, int minTap, int maxTap) {
			this.id = id;
			this.transformerName = transformerName;
			this.winding = winding;
			this.tapWinding = tapWinding;
			this.vreg = vreg;
			this.band = band;
			this.ptratio = ptratio;
			this.remotePtratio = remotePtratio;
			this.ctPrim = ctPrim;
			this.r = r;
			this.x = x;
			this.regulatedBus = regulatedBus;
			this.phaseSelection = phaseSelection;
			this.ptPhaseCode = ptPhaseCode;
			this.vLimit = vLimit;
			this.maxTapChange = maxTapChange;
			this.delaySeconds = Math.max(0.0, delaySeconds);
			this.tapStep = tapStep;
			this.minTap = minTap;
			this.maxTap = maxTap;
		}
	}
}
