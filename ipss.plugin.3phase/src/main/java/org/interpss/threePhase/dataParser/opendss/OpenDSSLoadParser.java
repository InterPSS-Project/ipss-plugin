package org.interpss.threePhase.dataParser.opendss;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.threePhase.basic.dstab.DStab1PLoad;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.basic.dstab.DStab3PLoad;
import org.interpss.threePhase.basic.dstab.impl.DStab1PLoadImpl;
import org.interpss.threePhase.basic.dstab.impl.DStab3PLoadImpl;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.threephase.LoadConnectionType;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.acsc.PhaseCode;

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

			String[] loadStrAry = loadStr.toLowerCase().trim().replaceAll("\\s*=\\s*", "=").split("\\s+");

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
			DStab3PBus bus =  this.dataParser.getDistNetwork().getBus(busName);
			if(bus == null) {
				bus = ThreePhaseObjectFactory.create3PDStabBus(busName, this.dataParser.getDistNetwork());
			}

			DStab1PLoad load= null;
			if(phaseNum==3 || phaseNum==2) {
				load= new DStab3PLoadImpl();
			} else {
				load= new DStab1PLoadImpl();
			}

			//load ID
			load.setId(loadId);
			// rated KV
			load.setNominalKV(nominalKV);
			if (vminpu != null) {
				load.setVminpu(vminpu.doubleValue());
			}
			if (vmaxpu != null) {
				load.setVmaxpu(vmaxpu.doubleValue());
			}

			//load model type
			if(modelType==1){
				//TODO extend AclfLoadCode instead of introducing a new load model type
				setLoadPower(load, phaseNum, phase1, phase2, loadPQ, AclfLoadCode.CONST_P);
			}
			else if(modelType==2){
				setLoadPower(load, phaseNum, phase1, phase2, loadPQ, AclfLoadCode.CONST_Z);
			}
			else if(modelType==4){
				setLoadPower(load, phaseNum, phase1, phase2, loadPQ, AclfLoadCode.CONST_P);
				load.setOpenDssModel4(true, cvrWatts, cvrVars);
			}
			else if(modelType==5){
				setLoadPower(load, phaseNum, phase1, phase2, loadPQ, AclfLoadCode.CONST_I);
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

			if(phaseNum==1) {
				bus.getSinglePhaseLoadList().add(load);
			} else if(phaseNum==3 || phaseNum==2) {
				bus.getThreePhaseLoadList().add((DStab3PLoad) load);
			}
			registerParsedLoad(loadId, load, phaseNum, phase1, phase2, modelType,
					transformerKva, powerfactor, cvrWatts, cvrVars);

			//TODO The following setting does NOT mean all the loads are constP type, it is a known limitation with bus level loadcode setting
			bus.setLoadCode(AclfLoadCode.CONST_P);

			return no_error;

		}

		public boolean parseLoadPropertyData(String propertyStr) {
			String normalized = propertyStr.trim().toLowerCase().replaceAll("\\s*=\\s*", "=");
			if(!normalized.startsWith("load.") || !normalized.contains(".allocationfactor=")) {
				return true;
			}
			int loadStart = "load.".length();
			int propertyStart = normalized.indexOf(".allocationfactor=");
			if(propertyStart <= loadStart) {
				return true;
			}
			String loadId = normalized.substring(loadStart, propertyStart);
			double allocationFactor = parseDssDouble(normalized.substring(propertyStart + ".allocationfactor=".length()));
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
				if(parsedLoad.modelType == 4) {
					parsedLoad.load.setOpenDssModel4(true, parsedLoad.cvrWatts, parsedLoad.cvrVars);
				}
			}
			return true;
		}

		private void registerParsedLoad(String loadId, DStab1PLoad load, int phaseNum, String phase1, String phase2,
				int modelType, double transformerKva, double powerFactor, double cvrWatts, double cvrVars) {
			this.parsedLoadsById.computeIfAbsent(loadId.toLowerCase(), ignored -> new ArrayList<>())
					.add(new ParsedLoad(load, phaseNum, phase1, phase2, modelType,
							transformerKva, powerFactor, cvrWatts, cvrVars));
		}

		private void setLoadPower(DStab1PLoad load, int phaseNum, String phase1, String phase2,
				Complex loadPQ, AclfLoadCode code) {
			load.setCode(code);
			if(phaseNum==3) {
				((DStab3PLoad)load).set3PhaseLoad(new Complex3x1(loadPQ.divide(3),loadPQ.divide(3),loadPQ.divide(3)));
			} else if(phaseNum==2) {
				((DStab3PLoad)load).set3PhaseLoad(twoPhaseLoad(loadPQ, phase1, phase2));
			} else if(code == AclfLoadCode.CONST_Z) {
				load.setLoadCZ(loadPQ);
			} else if(code == AclfLoadCode.CONST_I) {
				load.setLoadCI(loadPQ);
			} else {
				load.setLoadCP(loadPQ);
			}
		}

		private static double parseDssDouble(String value) {
			String normalized = value.trim();
			if((normalized.startsWith("(") && normalized.endsWith(")"))
					|| (normalized.startsWith("[") && normalized.endsWith("]"))) {
				normalized = normalized.substring(1, normalized.length() - 1).trim();
			}
			return Double.valueOf(normalized);
		}

		private static final class ParsedLoad {
			private final DStab1PLoad load;
			private final int phaseNum;
			private final String phase1;
			private final String phase2;
			private final int modelType;
			private final double transformerKva;
			private final double powerFactor;
			private final double cvrWatts;
			private final double cvrVars;

			private ParsedLoad(DStab1PLoad load, int phaseNum, String phase1, String phase2,
					int modelType, double transformerKva, double powerFactor, double cvrWatts, double cvrVars) {
				this.load = load;
				this.phaseNum = phaseNum;
				this.phase1 = phase1;
				this.phase2 = phase2;
				this.modelType = modelType;
				this.transformerKva = transformerKva;
				this.powerFactor = powerFactor;
				this.cvrWatts = cvrWatts;
				this.cvrVars = cvrVars;
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
