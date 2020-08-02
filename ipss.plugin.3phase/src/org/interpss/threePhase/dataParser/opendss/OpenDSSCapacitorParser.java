package org.interpss.threePhase.dataParser.opendss;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.basic.dstab.DStab3PLoad;
import org.interpss.threePhase.basic.dstab.impl.DStab3PLoadImpl;

import com.interpss.core.abc.LoadConnectionType;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.acsc.PhaseCode;

public class OpenDSSCapacitorParser {
	
    private OpenDSSDataParser dataParser = null;
	
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
		
		for(int i = 0;i<capStrAry.length;i++){
			if(capStrAry[i].contains("capacitor.")){
				capId   = capStrAry[i].substring(10);
			}
			else if(capStrAry[i].contains("bus1=")){
				busId   = capStrAry[i].substring(5);
				
				if(busId.contains(".")){
					String[] tempAry = busId.split("\\."); // split by dot
					busId = tempAry[0];
					if(tempAry.length>1){
						phase1 = tempAry[1];
					}
					else if(tempAry.length>2){
						phase2 = tempAry[2];
					}
					else if(tempAry.length>3){
						phase3 = tempAry[3];
					}
				}
			}
			else if(capStrAry[i].contains("phases=")){
				phaseNum   = Integer.valueOf(capStrAry[i].substring(7));
			}
			else if(capStrAry[i].contains("kvar=")){
				capKVAR   = Double.valueOf(capStrAry[i].substring(5));
			}
			else if(capStrAry[i].contains("kv=")){
				nominalKV   = Double.valueOf(capStrAry[i].substring(3));
			}
		}
		
	
		//get the bus object
		busId = this.dataParser.getBusIdPrefix()+busId;
		DStab3PBus bus = (DStab3PBus) this.dataParser.getDistNetwork().getBus(busId);
		
		if(bus==null){
			throw new Error("Bus for a capacitor cannot be found, busId, capId = "+busId+","+capId );
			
		}
		
		// tentatively modeled by constant Z Type of loads
		
		DStab3PLoad load= new DStab3PLoadImpl();
		
		load.setId(capId);
		load.setCode(AclfLoadCode.CONST_Z);
		load.setLoadConnectionType(LoadConnectionType.THREE_PHASE_WYE);
		load.setPhaseCode(PhaseCode.ABC);
		
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
			 }
			 else
				 throw new Error("Connection phase for a capacitor cannot be found! busId, capId = "+busId+","+capId );
				 
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
		
		bus.getThreePhaseLoadList().add(load);
		
		return no_error;
	}

}
