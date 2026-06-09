package org.interpss.threePhase.dataParser.opendss;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.threePhase.basic.dstab.DStab1PLoad;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.basic.dstab.DStab3PLoad;
import org.interpss.threePhase.dataParser.opendss.timeseries.OpenDSSProfileBinding;
import org.interpss.threePhase.dataParser.opendss.timeseries.OpenDSSProfileType;
import org.interpss.threePhase.basic.dstab.impl.DStab1PLoadImpl;
import org.interpss.threePhase.basic.dstab.impl.DStab3PLoadImpl;
import org.interpss.threePhase.qsts.QstsDeviceStatus;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.threephase.LoadConnectionType;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.acsc.PhaseCode;
import com.interpss.core.threephase.AclfLoad3Phase;
import com.interpss.core.threephase.Static3PBus;
import com.interpss.core.threephase.Static3PLoad;

public class OpenDSSLoadParser {

	/*
	 *  load model code:
	 *  1:Standard constant P+jQ load. (Default)
		2:Constant impedance load.
		3:Const P, Quadratic Q (like a motor).
		4:Nominal Linear P, Quadratic Q (feeder mix). Use this with CVRfactor.
		5:Constant Current Magnitude
		6:Const P, Fixed Q
		7:Const P, Fixed Impedance Q
		8:ZIPV (7 values)
	 */

	/*
	 *  !
		! LOAD DEFINITIONS
		!
		! Note that 1-phase loads have a voltage rating = to actual voltage across terminals
		! This could be either 2.4kV for Wye connectoin or 4.16 kV for Delta or Line-Line connection.
		! 3-phase loads are rated Line-Line (as are 2-phase loads, but there are none in this case).
		! Only the balanced 3-phase loads are declared as 3-phase; unbalanced 3-phase loads are declared
		! as three 1-phase loads.
	 */
	    private OpenDSSDataParser dataParser = null;
	    private final Map<String, List<ParsedLoad>> parsedLoadsById = new HashMap<>();

		public OpenDSSLoadParser(OpenDSSDataParser parser){
			this.dataParser = parser;
		}

		public boolean parseLoadData(String loadStr) throws InterpssException{
			boolean no_error = true;

			/*
			 * New Load.S1a   Bus1=1.1    Phases=1 Conn=Wye   Model=1 kV=2.4   kW=40.0  kvar=20.0
			 *
			 * NOTE:
			 * 1) if kvar is not used, need to use PF
			 * 2) for loads connected between two phases, Bus1=BusName.1.2, Phases=1
			 * To model loads connected between phases, use a pair of <-Iload, Iload> for the two phases.
			 * For the load modeling, need to add connection type, phase1, phase2 attributes
			 */
			final String DOT = ".";
			String loadId ="";
			String loadId_phases ="";
			String busName ="";
			String phase1 ="";
			String phase2 ="";
			String phase3 ="";
			String connectionType = "";
			int phaseNum = 3;  // three phases by default
			int modelType = 1; // constant power load by default
			double nominalKV = 0;
			double loadP = 0.0, loadQ  = 0.0;
			double loadKva = 0.0;
			double powerfactor = 0.0;
			double transformerKva = 0.0;
			double allocationFactor = 1.0;
			double cvrWatts = 1.0;
			double cvrVars = 2.0;
			Double vminpu = null;
			Double vmaxpu = null;
			boolean kwSpecified = false;
			double[] zipv = null;
			String dailyShapeId = "";
			String yearlyShapeId = "";
			String dutyShapeId = "";
			String status = "";

			String[] loadStrAry = splitDssTokens(loadStr.toLowerCase().trim().replaceAll("\\s*=\\s*", "="));

			for (String element : loadStrAry) {
				if(element.startsWith("Load.")||element.startsWith("load.")){
					loadId =element.substring(5);
				}
				else if(element.startsWith("Bus1=")||element.startsWith("bus1=")){
					loadId_phases =element.substring(5);
				}
				else if(element.startsWith("Bus=")||element.startsWith("bus=")){
					loadId_phases =element.substring(4);
				}
				else if(element.startsWith("Phases=")||element.startsWith("phases=")){
					phaseNum = Integer.valueOf(element.substring(7));
				}
				else if(element.startsWith("Conn=")||element.startsWith("conn=")){
					connectionType =element.substring(5);
				}
				else if(element.startsWith("Model=")||element.startsWith("model=")){
					 modelType =Integer.valueOf(element.substring(6));
				}
				else if(element.startsWith("kW=")||element.startsWith("kw=")){
					 loadP=parseDssDouble(element.substring(3));
					 kwSpecified = true;
				}
				else if(element.startsWith("kVar=")||element.startsWith("kvar=")){
					 loadQ=parseDssDouble(element.substring(5));
				}
				else if(element.startsWith("kva=")){
					 loadKva=parseDssDouble(element.substring(4));
				}
				else if(element.startsWith("PF=")||element.startsWith("pf=")){
					 powerfactor=parseDssDouble(element.substring(3));
				}
				else if(element.startsWith("xfkva=")){
					transformerKva = parseDssDouble(element.substring(6));
				}
				else if(element.startsWith("allocationfactor=")){
					allocationFactor = parseDssDouble(element.substring(17));
				}
				else if(element.startsWith("kv=")){
					nominalKV = parseDssDouble(element.substring(3));
				}
				else if(element.startsWith("vminpu=")){
					vminpu = parseDssDouble(element.substring(7));
				}
				else if(element.startsWith("vmaxpu=")){
					vmaxpu = parseDssDouble(element.substring(7));
				}
				else if(element.startsWith("cvrwatts=")){
					cvrWatts = parseDssDouble(element.substring(9));
				}
				else if(element.startsWith("cvrvars=")){
					cvrVars = parseDssDouble(element.substring(8));
				}
				else if(element.startsWith("zipv=")){
					zipv = parseDssDoubleArray(element.substring(5));
				}
				else if(element.startsWith("daily=")){
					dailyShapeId = stripDssValue(element.substring(6));
				}
				else if(element.startsWith("yearly=")){
					yearlyShapeId = stripDssValue(element.substring(7));
				}
				else if(element.startsWith("duty=")){
					dutyShapeId = stripDssValue(element.substring(5));
				}
				else if(element.startsWith("status=")){
					status = stripDssValue(element.substring(7));
				}


			}

			if(loadId_phases.contains(DOT)){
				String[] idPhasesAry = loadId_phases.split("\\.");
				busName = idPhasesAry[0];
				if(idPhasesAry.length>1){
					phase1 = idPhasesAry[1];
				}
				if(idPhasesAry.length>2){
					phase2 = idPhasesAry[2];
				}
				if(idPhasesAry.length>3){
					phase3 = idPhasesAry[3];
				}

			}
			else{
				busName = loadId_phases;
				if(phaseNum==3){
					//TODO need to set phase1,2,3???
				}
			}

			if(loadP == 0.0 && loadKva > 0.0 && powerfactor != 0.0) {
				loadP = loadKva * Math.abs(powerfactor);
			}
			if(powerfactor!=0.0 && loadQ==0.0){
				if((loadP == 0.0 || (kwSpecified && Math.abs(loadP) <= 1.0e-3)) && transformerKva > 0.0) {
					loadP = transformerKva * allocationFactor * Math.abs(powerfactor);
				}
				else if(loadP == 0.0 && !kwSpecified) {
					loadP = 10.0;
				}
				loadQ = loadP*Math.tan(Math.acos(Math.abs(powerfactor)));
				if(powerfactor < 0.0) {
					loadQ = -loadQ;
				}
			}

			Complex loadPQ = new Complex(loadP,loadQ);
			if(connectionType.equals("")) {
				connectionType = "wye";
			}

			//get the bus object
			busName =this.dataParser.getBusIdPrefix()+busName;
			DStab3PBus bus = null;
			Static3PBus staticBus = null;
			if(this.dataParser.isStaticNetworkMode()) {
				staticBus = this.dataParser.getOrCreateStaticBus(busName);
			}
			else {
				bus =  this.dataParser.getDistNetwork().getBus(busName);
				if(bus == null) {
					bus = ThreePhaseObjectFactory.create3PDStabBus(busName, this.dataParser.getDistNetwork());
				}
			}

			AclfLoad3Phase load = this.dataParser.isStaticNetworkMode()
					? ThreePhaseObjectFactory.createStatic3PLoad(loadId)
					: createDynamicLoad(loadId, phaseNum);

			// rated KV
			load.setNominalKV(nominalKV);
			if (vminpu != null) {
				setVminpu(load, vminpu.doubleValue());
			}
			if (vmaxpu != null) {
				setVmaxpu(load, vmaxpu.doubleValue());
			}

			//load model type
			if(modelType==1){
				//TODO extend AclfLoadCode instead of introducing a new load model type
				setLoadPower(load, phaseNum, phase1, phase2, loadPQ, AclfLoadCode.CONST_P);
			}
			else if(modelType==2){
				setLoadPower(load, phaseNum, phase1, phase2, loadPQ, AclfLoadCode.CONST_Z);
			}
			else if(modelType==3){
				setLoadPower(load, phaseNum, phase1, phase2, loadPQ, AclfLoadCode.CONST_P);
				setOpenDssLoadModel(load, modelType, cvrWatts, cvrVars, zipv);
			}
			else if(modelType==4){
				setLoadPower(load, phaseNum, phase1, phase2, loadPQ, AclfLoadCode.CONST_P);
				setOpenDssLoadModel(load, modelType, cvrWatts, cvrVars, zipv);
			}
			else if(modelType==5){
				setLoadPower(load, phaseNum, phase1, phase2, loadPQ, AclfLoadCode.CONST_I);
			}
			else if(modelType==6 || modelType==7){
				setLoadPower(load, phaseNum, phase1, phase2, loadPQ, AclfLoadCode.CONST_P);
				setOpenDssLoadModel(load, modelType, cvrWatts, cvrVars, zipv);
			}
			else if(modelType==8){
				if(zipv == null || zipv.length < 7) {
					no_error = false;
					throw new Error("OpenDSS load model=8 requires 7 ZIPV coefficients! # "+loadStr);
				}
				setLoadPower(load, phaseNum, phase1, phase2, loadPQ, AclfLoadCode.CONST_P);
				setOpenDssLoadModel(load, modelType, cvrWatts, cvrVars, zipv);
			}
			else{
				no_error = false;
				throw new Error("Load model type is not supported yet! # "+loadStr);
			}

			//load connection type
			if(phaseNum==3){
				if(connectionType.equalsIgnoreCase("delta")){
					load.setLoadConnectionType(LoadConnectionType.THREE_PHASE_DELTA);
				}
				else if(connectionType.equalsIgnoreCase("wye")){
					load.setLoadConnectionType(LoadConnectionType.THREE_PHASE_WYE);
				}
				else{
					no_error = false;
					throw new Error("Load connection type is not supported yet! # "+connectionType);
				}
			}
			else if(phaseNum==2){
				if(connectionType.equalsIgnoreCase("wye")){
					load.setLoadConnectionType(LoadConnectionType.THREE_PHASE_WYE);
				}
				else if(connectionType.equalsIgnoreCase("delta")){
					load.setLoadConnectionType(LoadConnectionType.THREE_PHASE_DELTA);
				}
				else{
					no_error = false;
					throw new Error("Load connection type for two phases is not supported yet! # "+connectionType);
				}
			}
			else if(phaseNum==1){
				if(connectionType.equalsIgnoreCase("wye")){
					load.setLoadConnectionType(LoadConnectionType.SINGLE_PHASE_WYE);

				}
				else if(connectionType.equalsIgnoreCase("delta")){
					if(phase1.length()>0 && phase2.length()>0) {
						load.setLoadConnectionType(LoadConnectionType.SINGLE_PHASE_DELTA);
					} else{
						no_error = false;
						throw new Error("Connection phases info is not consistent with the connection type # "+loadStr);
					}
				}
				else{
					no_error = false;
					throw new Error("Load connection type not supported yet! # "+connectionType);
				}
			}


			// process phase code

			if(phaseNum==3) {
				load.setPhaseCode(PhaseCode.ABC);
			} else if(phaseNum==2 || phaseNum==1){
				if(phase2.equals("")){
					if(phase1.equals("1")){
						load.setPhaseCode(PhaseCode.A);
					}
					else if(phase1.equals("2")){
						load.setPhaseCode(PhaseCode.B);
					}
					else if(phase1.equals("3")){
						load.setPhaseCode(PhaseCode.C);
					}
					else{
						no_error = false;
						throw new Error("Connection phases not supported yet! # "+ phase1);
					}
				}
				else{
					if((phase1.equals("1") && phase2.equals("2"))  || (phase1.equals("2") && phase2.equals("1"))) {
						load.setPhaseCode(PhaseCode.AB);
					} else if((phase1.equals("1") && phase2.equals("3"))||(phase1.equals("3") && phase2.equals("1"))) {
						load.setPhaseCode(PhaseCode.AC);
					} else if((phase1.equals("2") && phase2.equals("3"))||(phase1.equals("3") && phase2.equals("2"))) {
						load.setPhaseCode(PhaseCode.BC);
					} else{
						no_error = false;
						throw new Error("Connection phases not supported yet!  phase1, 2 = "+ phase1+","+ phase2);
					}
				}
			}

			if(this.dataParser.isStaticNetworkMode()) {
				staticBus.getContributeLoadList().add((Static3PLoad) load);
			}
			else if(phaseNum==1) {
				bus.getSinglePhaseLoadList().add((DStab1PLoad) load);
			} else if(phaseNum==3 || phaseNum==2) {
				bus.getThreePhaseLoadList().add((DStab3PLoad) load);
			}
			registerParsedLoad(loadId, load, phaseNum, phase1, phase2, modelType,
					transformerKva, powerfactor, cvrWatts, cvrVars, zipv);
			this.dataParser.getTimeSeriesData().getLoadStateStore().register(load);
			registerProfileBinding(loadId, dailyShapeId, yearlyShapeId, dutyShapeId, status);

			//TODO The following setting does NOT mean all the loads are constP type, it is a known limitation with bus level loadcode setting
			if(this.dataParser.isStaticNetworkMode()) {
				staticBus.setLoadCode(AclfLoadCode.CONST_P);
			}
			else {
				bus.setLoadCode(AclfLoadCode.CONST_P);
			}

			return no_error;

		}

		public boolean parseLoadPropertyData(String propertyStr) {
			String normalized = propertyStr.trim().toLowerCase().replaceAll("\\s*=\\s*", "=");
			if(!normalized.startsWith("load.")) {
				return true;
			}
			int loadStart = "load.".length();
			int propertyStart = normalized.indexOf('.', loadStart);
			if(propertyStart <= loadStart || !normalized.contains("=")) {
				return true;
			}
			String loadId = normalized.substring(loadStart, propertyStart);
			String propertyName = normalized.substring(propertyStart + 1, normalized.indexOf('=', propertyStart));
			String propertyValue = stripDssValue(normalized.substring(normalized.indexOf('=', propertyStart) + 1));
			if(propertyName.equals("daily")) {
				this.dataParser.getTimeSeriesData().getOrCreateLoadBinding(loadId)
						.setShapeId(OpenDSSProfileType.DAILY, propertyValue);
				return true;
			}
			if(propertyName.equals("yearly")) {
				this.dataParser.getTimeSeriesData().getOrCreateLoadBinding(loadId)
						.setShapeId(OpenDSSProfileType.YEARLY, propertyValue);
				return true;
			}
			if(propertyName.equals("duty")) {
				this.dataParser.getTimeSeriesData().getOrCreateLoadBinding(loadId)
						.setShapeId(OpenDSSProfileType.DUTY, propertyValue);
				return true;
			}
			if(propertyName.equals("status")) {
				this.dataParser.getTimeSeriesData().getOrCreateLoadBinding(loadId)
						.setStatus(parseStatus(propertyValue));
				return true;
			}
			if(!propertyName.equals("allocationfactor")) {
				return true;
			}
			double allocationFactor = parseDssDouble(propertyValue);
			List<ParsedLoad> loads = this.parsedLoadsById.get(loadId);
			if(loads == null) {
				return true;
			}
			for(ParsedLoad parsedLoad : loads) {
				if(parsedLoad.transformerKva <= 0.0 || parsedLoad.powerFactor == 0.0) {
					continue;
				}
				double loadP = parsedLoad.transformerKva * allocationFactor * Math.abs(parsedLoad.powerFactor);
				double loadQ = loadP*Math.tan(Math.acos(Math.abs(parsedLoad.powerFactor)));
				if(parsedLoad.powerFactor < 0.0) {
					loadQ = -loadQ;
				}
				AclfLoadCode code = parsedLoad.modelType == 2 ? AclfLoadCode.CONST_Z
						: parsedLoad.modelType == 5 ? AclfLoadCode.CONST_I : AclfLoadCode.CONST_P;
				setLoadPower(parsedLoad.load, parsedLoad.phaseNum, parsedLoad.phase1, parsedLoad.phase2,
						new Complex(loadP, loadQ), code);
				if(parsedLoad.modelType == 3 || parsedLoad.modelType == 4 || parsedLoad.modelType == 6
						|| parsedLoad.modelType == 7 || parsedLoad.modelType == 8) {
					setOpenDssLoadModel(parsedLoad.load, parsedLoad.modelType, parsedLoad.cvrWatts,
							parsedLoad.cvrVars, parsedLoad.zipv);
				}
			}
			return true;
		}

		private void registerProfileBinding(String loadId, String dailyShapeId, String yearlyShapeId,
				String dutyShapeId, String status) {
			if((dailyShapeId == null || dailyShapeId.isEmpty())
					&& (yearlyShapeId == null || yearlyShapeId.isEmpty())
					&& (dutyShapeId == null || dutyShapeId.isEmpty())
					&& (status == null || status.isEmpty())) {
				return;
			}
			OpenDSSProfileBinding binding = this.dataParser.getTimeSeriesData().getOrCreateLoadBinding(loadId);
			binding.setShapeId(OpenDSSProfileType.DAILY, dailyShapeId);
			binding.setShapeId(OpenDSSProfileType.YEARLY, yearlyShapeId);
			binding.setShapeId(OpenDSSProfileType.DUTY, dutyShapeId);
			binding.setStatus(parseStatus(status));
		}

		private static QstsDeviceStatus parseStatus(String status) {
			if(status == null || status.trim().isEmpty()) {
				return QstsDeviceStatus.DEFAULT;
			}
			String normalized = stripDssValue(status).toLowerCase();
			if(normalized.equals("fixed")) {
				return QstsDeviceStatus.FIXED;
			}
			if(normalized.equals("variable")) {
				return QstsDeviceStatus.VARIABLE;
			}
			if(normalized.equals("exempt")) {
				return QstsDeviceStatus.EXEMPT;
			}
			return QstsDeviceStatus.DEFAULT;
		}

		private void registerParsedLoad(String loadId, AclfLoad3Phase load, int phaseNum, String phase1, String phase2,
				int modelType, double transformerKva, double powerFactor, double cvrWatts, double cvrVars,
				double[] zipv) {
			this.parsedLoadsById.computeIfAbsent(loadId.toLowerCase(), ignored -> new ArrayList<>())
					.add(new ParsedLoad(load, phaseNum, phase1, phase2, modelType,
							transformerKva, powerFactor, cvrWatts, cvrVars, zipv));
		}

		private void setLoadPower(AclfLoad3Phase load, int phaseNum, String phase1, String phase2,
				Complex loadPQ, AclfLoadCode code) {
			load.setCode(code);
			if(phaseNum==3) {
				load.set3PhaseLoad(new Complex3x1(loadPQ.divide(3),loadPQ.divide(3),loadPQ.divide(3)));
			} else if(phaseNum==2) {
				load.set3PhaseLoad(twoPhaseLoad(loadPQ, phase1, phase2));
			} else if(code == AclfLoadCode.CONST_Z) {
				load.setLoadCZ(loadPQ);
			} else if(code == AclfLoadCode.CONST_I) {
				load.setLoadCI(loadPQ);
			} else {
				load.setLoadCP(loadPQ);
			}
		}

		private static AclfLoad3Phase createDynamicLoad(String loadId, int phaseNum) {
			DStab1PLoad load = phaseNum == 3 || phaseNum == 2 ? new DStab3PLoadImpl() : new DStab1PLoadImpl();
			load.setId(loadId);
			return load;
		}

		private static void setVminpu(AclfLoad3Phase load, double vminpu) {
			load.setVminpu(vminpu);
		}

		private static void setVmaxpu(AclfLoad3Phase load, double vmaxpu) {
			load.setVmaxpu(vmaxpu);
		}

		private static void setOpenDssLoadModel(AclfLoad3Phase load, int modelType, double cvrWatts,
				double cvrVars, double[] zipv) {
			load.setOpenDssLoadModel(modelType, cvrWatts, cvrVars, zipv);
		}

		private static double parseDssDouble(String value) {
			String normalized = stripDssValue(value);
			if((normalized.startsWith("(") && normalized.endsWith(")"))
					|| (normalized.startsWith("[") && normalized.endsWith("]"))) {
				normalized = normalized.substring(1, normalized.length() - 1).trim();
			}
			return Double.valueOf(normalized);
		}

		private static String[] splitDssTokens(String text) {
			List<String> tokens = new ArrayList<>();
			StringBuilder token = new StringBuilder();
			int bracketDepth = 0;
			for(int i = 0; i < text.length(); i++) {
				char ch = text.charAt(i);
				if(ch == '(' || ch == '[') {
					bracketDepth++;
				}
				else if(ch == ')' || ch == ']') {
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

		private static double[] parseDssDoubleArray(String value) {
			String normalized = stripDssValue(value);
			if((normalized.startsWith("(") && normalized.endsWith(")"))
					|| (normalized.startsWith("[") && normalized.endsWith("]"))) {
				normalized = normalized.substring(1, normalized.length() - 1).trim();
			}
			if(normalized.isEmpty()) {
				return new double[0];
			}
			String[] parts = normalized.split("[,\\s]+");
			double[] values = new double[parts.length];
			for(int i = 0; i < parts.length; i++) {
				values[i] = parseDssDouble(parts[i]);
			}
			return values;
		}

		private static String stripDssValue(String value) {
			String normalized = value == null ? "" : value.trim();
			while(normalized.endsWith(",")) {
				normalized = normalized.substring(0, normalized.length() - 1).trim();
			}
			if((normalized.startsWith("\"") && normalized.endsWith("\""))
					|| (normalized.startsWith("'") && normalized.endsWith("'"))) {
				normalized = normalized.substring(1, normalized.length() - 1);
			}
			return normalized;
		}

		private static final class ParsedLoad {
			private final AclfLoad3Phase load;
			private final int phaseNum;
			private final String phase1;
			private final String phase2;
			private final int modelType;
			private final double transformerKva;
			private final double powerFactor;
			private final double cvrWatts;
			private final double cvrVars;
			private final double[] zipv;

			private ParsedLoad(AclfLoad3Phase load, int phaseNum, String phase1, String phase2,
					int modelType, double transformerKva, double powerFactor, double cvrWatts, double cvrVars,
					double[] zipv) {
				this.load = load;
				this.phaseNum = phaseNum;
				this.phase1 = phase1;
				this.phase2 = phase2;
				this.modelType = modelType;
				this.transformerKva = transformerKva;
				this.powerFactor = powerFactor;
				this.cvrWatts = cvrWatts;
				this.cvrVars = cvrVars;
				this.zipv = zipv == null ? null : zipv.clone();
			}
		}

		private Complex3x1 twoPhaseLoad(Complex totalLoad, String phase1, String phase2) {
			Complex phaseLoad = totalLoad.divide(2.0);
			Complex zero = new Complex(0.0, 0.0);
			if((phase1.equals("1") && phase2.equals("2")) || (phase1.equals("2") && phase2.equals("1"))) {
				return new Complex3x1(phaseLoad, phaseLoad, zero);
			}
			if((phase1.equals("1") && phase2.equals("3")) || (phase1.equals("3") && phase2.equals("1"))) {
				return new Complex3x1(phaseLoad, zero, phaseLoad);
			}
			if((phase1.equals("2") && phase2.equals("3")) || (phase1.equals("3") && phase2.equals("2"))) {
				return new Complex3x1(zero, phaseLoad, phaseLoad);
			}
			throw new Error("Connection phases not supported yet!  phase1, 2 = "+ phase1+","+ phase2);
		}
}
