package org.interpss.threePhase.dataParser.opendss;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.threePhase.basic.dstab.DStab1PLoad;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.basic.dstab.DStab3PLoad;
import org.interpss.threePhase.basic.dstab.impl.DStab1PLoadImpl;
import org.interpss.threePhase.basic.dstab.impl.DStab3PLoadImpl;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.abc.LoadConnectionType;
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
			double powerfactor = 0.0;

			String[] loadStrAry = loadStr.toLowerCase().trim().split("\\s+");

			for (String element : loadStrAry) {
				if(element.startsWith("Load.")||element.startsWith("load.")){
					loadId =element.substring(5);
				}
				else if(element.startsWith("Bus1=")||element.startsWith("bus1=")){
					loadId_phases =element.substring(5);
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
					 loadP=Double.valueOf(element.substring(3));
				}
				else if(element.startsWith("kVar=")||element.startsWith("kvar=")){
					 loadQ=Double.valueOf(element.substring(5));
				}
				else if(element.startsWith("PF=")||element.startsWith("pf=")){
					 powerfactor=Double.valueOf(element.substring(3));
				}
				else if(element.startsWith("kv=")){
					nominalKV = Double.valueOf(element.substring(3));
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

			if(powerfactor!=0.0 && loadQ==0.0){
				loadQ = loadP*Math.tan(Math.acos(powerfactor));
			}

			Complex loadPQ = new Complex(loadP,loadQ);

			//get the bus object
			busName =this.dataParser.getBusIdPrefix()+busName;
			DStab3PBus bus =  this.dataParser.getDistNetwork().getBus(busName);

			DStab1PLoad load= null;
			if(phaseNum==3) {
				load= new DStab3PLoadImpl();
			} else {
				load= new DStab1PLoadImpl();
			}

			//load ID
			load.setId(loadId);
			// rated KV
			load.setNominalKV(nominalKV);

			//load model type
			if(modelType==1){
				//TODO extend AclfLoadCode instead of introducing a new load model type
				load.setCode(AclfLoadCode.CONST_P);

				if(phaseNum==3) {
					((DStab3PLoad)load).set3PhaseLoad(new Complex3x1(loadPQ.divide(3),loadPQ.divide(3),loadPQ.divide(3)));
				} else {
					load.setLoadCP(loadPQ);
				}
			}
			else if(modelType==2){
				load.setCode(AclfLoadCode.CONST_Z);

				if(phaseNum==3) {
					((DStab3PLoad)load).set3PhaseLoad(new Complex3x1(loadPQ.divide(3),loadPQ.divide(3),loadPQ.divide(3)));
				} else {
					load.setLoadCZ(loadPQ);
				}
			}
			else if(modelType==5){
				load.setCode(AclfLoadCode.CONST_I);
				if(phaseNum==3) {
					((DStab3PLoad)load).set3PhaseLoad(new Complex3x1(loadPQ.divide(3),loadPQ.divide(3),loadPQ.divide(3)));
				} else {
					load.setLoadCI(loadPQ);
				}
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
				no_error = false;
			    throw new Error("Load connection type for two phases is not supported yet! # "+connectionType);

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
			} else if(phaseNum==1){
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
			} else if(phaseNum==3) {
				bus.getThreePhaseLoadList().add((DStab3PLoad) load);
			}

			//TODO The following setting does NOT mean all the loads are constP type, it is a known limitation with bus level loadcode setting
			bus.setLoadCode(AclfLoadCode.CONST_P);

			return no_error;

		}
}
