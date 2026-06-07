package org.interpss.threePhase.dataParser.opendss;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.basic.dstab.DStab3PLoad;
import org.interpss.threePhase.basic.dstab.impl.DStab3PLoadImpl;
import org.interpss.threePhase.powerflow.control.CapacitorControlData;
import org.interpss.threePhase.powerflow.control.CapacitorControlData.ControlType;
import org.interpss.threePhase.powerflow.control.CapacitorControlData.PhaseSelection;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;

import com.interpss.core.threephase.LoadConnectionType;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.acsc.PhaseCode;
import com.interpss.core.threephase.IPhaseLoad;
import com.interpss.core.threephase.Static3PBus;
import com.interpss.core.threephase.Static3PLoad;

public class OpenDSSCapacitorParser {

    private OpenDSSDataParser dataParser = null;
	private final Map<String, PhaseCode> capacitorPhaseById = new HashMap<String, PhaseCode>();
	private final List<OpenDssCapControlData> capControls = new ArrayList<OpenDssCapControlData>();
	private final Map<String, OpenDssCapControlData> capControlsById = new HashMap<String, OpenDssCapControlData>();

	public OpenDSSCapacitorParser(OpenDSSDataParser parser){
		this.dataParser = parser;
	}

	public boolean parseCapDataString(String capStr){
		boolean no_error = true;

		/*
		 * New Capacitor.C83       Bus1=83      Phases=3     kVAR=600     kV=4.16
           New Capacitor.C88a      Bus1=88.1    Phases=1     kVAR=50      kV=2.402
		 */


		String capId = "";
		String busId = "";
		String phase1 = "";
		String phase2 = "";
		String phase3 = "";

		int phaseNum = 3; // 3 phases by default

		double capKVAR =  0;
		double nominalKV = 0;

        String[] capStrAry = capStr.toLowerCase().trim().split("\\s+");

		for (String element : capStrAry) {
			if(element.contains("capacitor.")){
				capId   = element.substring(10);
			}
			else if(element.contains("bus1=")){
				busId   = element.substring(5);

				if(busId.contains(".")){
					String[] tempAry = busId.split("\\."); // split by dot
					busId = tempAry[0];
					if(tempAry.length>1){
						phase1 = tempAry[1];
					}
					if(tempAry.length>2){
						phase2 = tempAry[2];
					}
					if(tempAry.length>3){
						phase3 = tempAry[3];
					}
				}
			}
			else if(element.contains("phases=")){
				phaseNum   = Integer.valueOf(element.substring(7));
			}
			else if(element.contains("kvar=")){
				capKVAR   = Double.valueOf(element.substring(5));
			}
			else if(element.contains("kv=")){
				nominalKV   = Double.valueOf(element.substring(3));
			}
		}


		//get the bus object
		busId = this.dataParser.getBusIdPrefix()+busId;
		DStab3PBus bus = this.dataParser.isStaticNetworkMode() ? null : this.dataParser.getDistNetwork().getBus(busId);
		Static3PBus staticBus = this.dataParser.isStaticNetworkMode() ? this.dataParser.getOrCreateStaticBus(busId) : null;

		if(!this.dataParser.isStaticNetworkMode() && bus==null){
			throw new Error("Bus for a capacitor cannot be found, busId, capId = "+busId+","+capId );

		}

		// tentatively modeled by constant Z Type of loads

		IPhaseLoad load = this.dataParser.isStaticNetworkMode()
				? ThreePhaseObjectFactory.createStatic3PLoad(capId)
				: createDynamicCapacitorLoad(capId);

		load.setCode(AclfLoadCode.CONST_Z);
		load.setLoadConnectionType(LoadConnectionType.THREE_PHASE_WYE);
		load.setPhaseCode(PhaseCode.ABC);
		load.setNominalKV(nominalKV);

		// single phase and three-phase capacitors are all modeled by three-phase shunts
		double kva1Phase = capKVAR/3.0;
		Complex3x1 capShunt = new Complex3x1();
		if(phaseNum==1){
			 kva1Phase = capKVAR;
			 Complex shunt1Phase = new Complex(0,kva1Phase);
			 if(phase1.equals("1")){
				 capShunt.a_0 = shunt1Phase;
				 //load.setPhaseCode(PhaseCode.A);
			 }
			 else if(phase1.equals("2")){
				 capShunt.b_1 = shunt1Phase;
				 //load.setPhaseCode(PhaseCode.B);
			 }
			 else if(phase1.equals("3")){
				 capShunt.c_2 = shunt1Phase;
				 //load.setPhaseCode(PhaseCode.C);
			 } else {
				throw new Error("Connection phase for a capacitor cannot be found! busId, capId = "+busId+","+capId );
			}

		}
		else if(phaseNum==3){

			capShunt.a_0 = new Complex(0,kva1Phase);
			capShunt.b_1 = new Complex(0,kva1Phase);
			capShunt.c_2 = new Complex(0,kva1Phase);

		}
		else if(phaseNum==2){
			throw new Error("Two phases connection for a capacitor is not supported yet! busId, capId = "+busId+","+capId );
		}

		load.set3PhaseLoad(capShunt.multiply(-1)); // capacitors are modeled as "negative" loads

		if(this.dataParser.isStaticNetworkMode()) {
			staticBus.getContributeLoadList().add((Static3PLoad) load);
		}
		else {
			bus.getThreePhaseLoadList().add((DStab3PLoad) load);
		}
		this.capacitorPhaseById.put(normalize(capId), capacitorPhaseCode(phaseNum, phase1, phase2, phase3));

		return no_error;
	}

	private static DStab3PLoad createDynamicCapacitorLoad(String capId) {
		DStab3PLoad load = new DStab3PLoadImpl();
		load.setId(capId);
		return load;
	}

	public boolean parseCapControlData(String capControlStr) {
		String[] tokens = splitDssTokens(capControlStr.toLowerCase(Locale.ROOT).trim().replaceAll("\\s*=\\s*", "="));
		String id = "";
		String capacitorId = "";
		String monitoredElementName = "";
		int terminal = 1;
		ControlType controlType = ControlType.VOLTAGE;
		double onSetting = 0.0;
		double offSetting = 0.0;
		double ptRatio = 1.0;
		double ctRatio = 1.0;
		boolean voltageOverride = false;
		double vMin = 0.0;
		double vMax = 0.0;
		double delay = 0.0;
		double delayOff = 0.0;
		PhaseSelection phaseSelection = PhaseSelection.AVG;
		PhaseCode phaseCode = null;
		List<String> propertyTokens = new ArrayList<String>();
		for(String token : tokens) {
			if(token.contains("capcontrol.")) {
				String suffix = token.substring(token.indexOf("capcontrol.") + 11);
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
		OpenDssCapControlData existing = id.length() == 0 ? null : this.capControlsById.get(id);
		for(String token : propertyTokens) {
			if(token.startsWith("capacitor=")) {
				capacitorId = token.substring(10);
			}
			else if(token.startsWith("element=")) {
				monitoredElementName = token.substring(8);
			}
			else if(token.startsWith("terminal=")) {
				terminal = Integer.valueOf(token.substring(9)).intValue();
			}
			else if(token.startsWith("type=")) {
				controlType = controlType(token.substring(5));
			}
			else if(token.startsWith("onsetting=")) {
				onSetting = Double.valueOf(token.substring(10)).doubleValue();
			}
			else if(token.startsWith("offsetting=")) {
				offSetting = Double.valueOf(token.substring(11)).doubleValue();
			}
			else if(token.startsWith("ptratio=")) {
				ptRatio = Double.valueOf(token.substring(8)).doubleValue();
			}
			else if(token.startsWith("ctratio=")) {
				ctRatio = Double.valueOf(token.substring(8)).doubleValue();
			}
			else if(token.startsWith("voltoverride=")) {
				voltageOverride = yes(token.substring(13));
			}
			else if(token.startsWith("vmin=")) {
				vMin = Double.valueOf(token.substring(5)).doubleValue();
			}
			else if(token.startsWith("vmax=")) {
				vMax = Double.valueOf(token.substring(5)).doubleValue();
			}
			else if(token.startsWith("delayoff=")) {
				delayOff = Double.valueOf(token.substring(9)).doubleValue();
			}
			else if(token.startsWith("delay=")) {
				delay = Double.valueOf(token.substring(6)).doubleValue();
			}
			else if(token.startsWith("ptphase=")) {
				PhaseToken phase = phaseToken(token.substring(8));
				phaseSelection = phase.selection;
				phaseCode = phase.phaseCode;
			}
			else if(token.startsWith("ctphase=")) {
				PhaseToken phase = phaseToken(token.substring(8));
				phaseSelection = phase.selection;
				phaseCode = phase.phaseCode;
			}
		}
		if(existing != null) {
			if(capacitorId.length() == 0) {
				capacitorId = existing.capacitorId;
			}
			if(monitoredElementName.length() == 0) {
				monitoredElementName = existing.monitoredElementName;
			}
			if(!tokenWasSpecified(propertyTokens, "terminal=")) {
				terminal = existing.terminal;
			}
			if(!tokenWasSpecified(propertyTokens, "type=")) {
				controlType = existing.controlType;
			}
			if(!tokenWasSpecified(propertyTokens, "onsetting=")) {
				onSetting = existing.onSetting;
			}
			if(!tokenWasSpecified(propertyTokens, "offsetting=")) {
				offSetting = existing.offSetting;
			}
			if(!tokenWasSpecified(propertyTokens, "ptratio=")) {
				ptRatio = existing.ptRatio;
			}
			if(!tokenWasSpecified(propertyTokens, "ctratio=")) {
				ctRatio = existing.ctRatio;
			}
			if(!tokenWasSpecified(propertyTokens, "voltoverride=")) {
				voltageOverride = existing.voltageOverride;
			}
			if(!tokenWasSpecified(propertyTokens, "vmin=")) {
				vMin = existing.vMin;
			}
			if(!tokenWasSpecified(propertyTokens, "vmax=")) {
				vMax = existing.vMax;
			}
			if(!tokenWasSpecified(propertyTokens, "delay=")) {
				delay = existing.delay;
			}
			if(!tokenWasSpecified(propertyTokens, "delayoff=")) {
				delayOff = existing.delayOff;
			}
			if(!tokenWasSpecified(propertyTokens, "ptphase=") && !tokenWasSpecified(propertyTokens, "ctphase=")) {
				phaseSelection = existing.phaseSelection;
				phaseCode = existing.phaseCode;
			}
		}
		if(capacitorId.length() == 0) {
			return true;
		}
		if(phaseCode == null) {
			phaseCode = this.capacitorPhaseById.get(normalize(capacitorId));
		}
		OpenDssCapControlData data = new OpenDssCapControlData(id, capacitorId, monitoredElementName,
				terminal, controlType, onSetting, offSetting, ptRatio, ctRatio, voltageOverride,
				vMin, vMax, delay, delayOff, phaseCode == null ? PhaseCode.ABC : phaseCode, phaseSelection);
		if(existing == null) {
			this.capControls.add(data);
		}
		else {
			int index = this.capControls.indexOf(existing);
			if(index >= 0) {
				this.capControls.set(index, data);
			}
		}
		if(id.length() > 0) {
			this.capControlsById.put(id, data);
		}
		return true;
	}

	public int getCapControlCount() {
		return this.capControls.size();
	}

	public List<CapacitorControlData> toCapacitorControlData() {
		if(this.capControls.isEmpty()) {
			return Collections.emptyList();
		}
		List<CapacitorControlData> controls = new ArrayList<CapacitorControlData>();
		for(OpenDssCapControlData control : this.capControls) {
			controls.add(new CapacitorControlData(control.id.length() == 0 ? control.capacitorId : control.id,
					control.capacitorId, control.monitoredElementName, control.terminal, control.controlType,
					control.onSetting, control.offSetting, control.ptRatio, control.ctRatio,
					control.voltageOverride, control.vMin, control.vMax, control.delay, control.delayOff,
					control.phaseCode, control.phaseSelection));
		}
		return controls;
	}

	private static PhaseCode capacitorPhaseCode(int phaseNum, String phase1, String phase2, String phase3) {
		if(phaseNum == 1) {
			return phaseCode(phase1);
		}
		if(phaseNum == 2) {
			PhaseCode first = phaseCode(phase1);
			PhaseCode second = phaseCode(phase2);
			if((first == PhaseCode.A && second == PhaseCode.B) || (first == PhaseCode.B && second == PhaseCode.A)) {
				return PhaseCode.AB;
			}
			if((first == PhaseCode.A && second == PhaseCode.C) || (first == PhaseCode.C && second == PhaseCode.A)) {
				return PhaseCode.AC;
			}
			return PhaseCode.BC;
		}
		return PhaseCode.ABC;
	}

	private static PhaseToken phaseToken(String value) {
		if("avg".equals(value) || "average".equals(value)) {
			return new PhaseToken(PhaseSelection.AVG, PhaseCode.ABC);
		}
		if("max".equals(value)) {
			return new PhaseToken(PhaseSelection.MAX, PhaseCode.ABC);
		}
		if("min".equals(value)) {
			return new PhaseToken(PhaseSelection.MIN, PhaseCode.ABC);
		}
		return new PhaseToken(PhaseSelection.PHASE, phaseCode(value));
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
		return PhaseCode.ABC;
	}

	private static ControlType controlType(String value) {
		if("current".equals(value)) {
			return ControlType.CURRENT;
		}
		if("kvar".equals(value)) {
			return ControlType.KVAR;
		}
		if("pf".equals(value)) {
			return ControlType.PF;
		}
		if("time".equals(value)) {
			return ControlType.TIME;
		}
		return ControlType.VOLTAGE;
	}

	private static boolean yes(String value) {
		return "y".equals(value) || "yes".equals(value) || "true".equals(value) || "1".equals(value);
	}

	private static boolean tokenWasSpecified(List<String> tokens, String prefix) {
		for(String token : tokens) {
			if(token.startsWith(prefix)) {
				return true;
			}
		}
		return false;
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}

	private static String[] splitDssTokens(String value) {
		List<String> tokens = new ArrayList<String>();
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

	private static class PhaseToken {
		private final PhaseSelection selection;
		private final PhaseCode phaseCode;

		private PhaseToken(PhaseSelection selection, PhaseCode phaseCode) {
			this.selection = selection;
			this.phaseCode = phaseCode;
		}
	}

	private static class OpenDssCapControlData {
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
		private final double delay;
		private final double delayOff;
		private final PhaseCode phaseCode;
		private final PhaseSelection phaseSelection;

		private OpenDssCapControlData(String id, String capacitorId, String monitoredElementName, int terminal,
				ControlType controlType, double onSetting, double offSetting, double ptRatio, double ctRatio,
				boolean voltageOverride, double vMin, double vMax, double delay, double delayOff,
				PhaseCode phaseCode, PhaseSelection phaseSelection) {
			this.id = id;
			this.capacitorId = capacitorId;
			this.monitoredElementName = monitoredElementName;
			this.terminal = terminal;
			this.controlType = controlType;
			this.onSetting = onSetting;
			this.offSetting = offSetting;
			this.ptRatio = ptRatio;
			this.ctRatio = ctRatio;
			this.voltageOverride = voltageOverride;
			this.vMin = vMin;
			this.vMax = vMax;
			this.delay = delay;
			this.delayOff = delayOff;
			this.phaseCode = phaseCode;
			this.phaseSelection = phaseSelection;
		}
	}

}
