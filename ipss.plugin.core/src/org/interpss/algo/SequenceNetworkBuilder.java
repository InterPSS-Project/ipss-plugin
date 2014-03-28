package org.interpss.algo;

import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.acsc.Acsc3WBranch;
import com.interpss.core.acsc.AcscBranch;
import com.interpss.core.acsc.AcscBus;
import com.interpss.core.acsc.AcscGen;
import com.interpss.core.acsc.BaseAcscNetwork;
import com.interpss.core.acsc.SequenceCode;
import com.interpss.core.acsc.XfrConnectCode;
import com.interpss.core.net.Branch;

public class SequenceNetworkBuilder {
	private BaseAcscNetwork<?,?> _net = null;
	private boolean overrideSeqData = false;
	private double lineZero2PosZRatio = 2.5;
	public SequenceNetworkBuilder(BaseAcscNetwork<?,?> net, boolean overrideExistingData){
		this._net = net;
		this.overrideSeqData = overrideExistingData;
	}
	
	public boolean buildSequenceNetwork(SequenceCode seq){
		boolean ok =true;
		if(!setGenSeqData(seq))
			return ok = false;
		if(!setLoadSeqData(seq))
			return ok = false;
		if(!setLineSeqData(seq))
			return ok = false;
		if(!set2WXfrSeqData(seq))
			return ok = false;
		if(!set3WXfrSeqData(seq))
			return ok = false;
		if(!setFixedShuntSeqData(seq))
			return ok = false;
		if(!setSwitchedShuntSeqData(seq))
			return ok = false;
		
		return ok;
			
	}
	
	private boolean setGenSeqData(SequenceCode seq){
		for(AcscBus scBus: this._net.getBusList()){
			if(scBus.getGenList().size()>0){
				for(AclfGen gen:scBus.getGenList()){
					if(gen.isActive()){
						AcscGen scGen =(AcscGen) gen;
						//Assume the positive sequence data is available
						if(scGen.getPosGenZ()!=null){
							// genNegZ = genPosZ
							if(seq==SequenceCode.NEGATIVE){
								if(overrideSeqData || scGen.getNegGenZ()==null)
								  scGen.setNegGenZ(scGen.getPosGenZ());
							}
							// genZeroZ = genPosZ
							else if(seq==SequenceCode.ZERO){
								if(overrideSeqData || scGen.getZeroGenZ()==null)
								scGen.setZeroGenZ(scGen.getPosGenZ());
							}
						}
						else{
							IpssLogger.getLogger().severe("Error: Positive seq Z is NULL for Gen :"+scGen.getId() +" @ "+scBus.getId());;
							return false;
						}
					}
				}
			}
		}
		return true;
		
	}
	
	private boolean setLoadSeqData(SequenceCode seq){
		
		//AcscBus.initSeqEquivLoad(SequenceCode code) method handles this
		for(AcscBus scBus: this._net.getBusList()){
			if(scBus.isActive() && scBus.getLoadList().size()>0){
				if(!scBus.initSeqEquivLoad(seq))
					return false;
			}
		}
		
		return true;
		
	}
	/**
	 * 
	 * Setting the line sequence impedance. By default, the negative Z is same as the positive Z, while the 
	 * zero seq Z is 2.5 times of the positive Z.
	 * @param seq
	 * @return
	 */
	private boolean setLineSeqData(SequenceCode seq){
		//need to exclude those AclfBranch which are actually transformers
		for(AcscBranch bra: this._net.getBranchList()){
			if(bra.isLine()){
				//Line NegZ = PosZ
				if(seq==SequenceCode.NEGATIVE){
				  //no operation is needed, since line NegZ = PosZ is already implicitly implemented 
				 // in ipss core engine.
				}
				else if(seq==SequenceCode.ZERO){
					if(this.overrideSeqData || bra.getZ0()==null){
						bra.setZ0(bra.getZ().multiply(lineZero2PosZRatio));
						//zero sequence branch shunt B0 setting, zero by default
						bra.setHB0(0);
					}
				}
			}
		}
		return true;
	}
	
	private boolean set2WXfrSeqData(SequenceCode seq){
	    //need to check if the 2W xfr part of a 3w xfr
		for(Branch bra: this._net.getBranchList()){
			if(bra instanceof AcscBranch){
				AcscBranch scBranch = (AcscBranch) bra;
			
			if(scBranch.isXfr() && !is3WXfrBranch(scBranch)){
				//Line NegZ = PosZ
				if(seq==SequenceCode.NEGATIVE){
				  //no operation is needed, since assumption NegZ = PosZ is already implicitly implemented 
				 // in ipss core engine.
				}
				else if(seq==SequenceCode.ZERO){
					if(this.overrideSeqData || scBranch.getZ0()==null ){
						scBranch.setZ0(scBranch.getZ());
						//zero sequence branch shunt B0 setting, zero by default
						scBranch.setHB0(0);
						
						/*
						 * type -1 : ordinary 2 winding transformer, HV/LV :Yg-Yg connection 
						 * criterion: HV high than 230 kV and not a generator step up transformer
						 * 
						 * type -2: sub transmission to Load/distribution network:, HV/LV Delta-Yg
						 * criterion: HV lower than 230 kV, and not a generator step up transformer
						 * 
						 * type -3: step up transformer for generators,HV/LV: Yg/Delta 
						 * 
						 * 
						 */
						//Type-1: Yg-Yg
						if((scBranch.getFromAcscBus().getBaseVoltage()>=230000||scBranch.getToAcscBus().getBaseVoltage()>=230000)
								&& !isGenXfr(scBranch)){
							scBranch.setXfrFromConnectCode(XfrConnectCode.WYE_SOLID_GROUNDED);
							scBranch.setXfrToConnectCode(XfrConnectCode.WYE_SOLID_GROUNDED);
							
						}
						//type-2 // Delta-Yg
						else if(getHighVoltageBus(scBranch).getBaseVoltage()<230000
							&& !isGenXfr(scBranch)){
							if(scBranch.getFromBusId().equals(getHighVoltageBus(scBranch).getId())){
							  scBranch.setXfrFromConnectCode(XfrConnectCode.DELTA);
							  scBranch.setXfrToConnectCode(XfrConnectCode.WYE_SOLID_GROUNDED);
							}
							else{
								scBranch.setXfrFromConnectCode(XfrConnectCode.WYE_SOLID_GROUNDED);
								scBranch.setXfrToConnectCode(XfrConnectCode.DELTA);
							}
								
						}
						//type-3
						else if(isGenXfr(scBranch)){
							//NOTE: for step up transformer: the delta connection is on low voltage bus side .
							//
							if(scBranch.getFromAcscBus().getBaseVoltage()>=scBranch.getToAcscBus().getBaseVoltage()){
							   scBranch.setXfrFromConnectCode(XfrConnectCode.WYE_SOLID_GROUNDED);
							   scBranch.setXfrToConnectCode(XfrConnectCode.DELTA);
							}
							else{
							   scBranch.setXfrFromConnectCode(XfrConnectCode.DELTA);
							   scBranch.setXfrToConnectCode(XfrConnectCode.WYE_SOLID_GROUNDED);
							}
						}
						else {
							// error, unprocessed model
							IpssLogger.getLogger().severe("Error: transformer is not belong to predefined types"+scBranch.getId());
						    return false;
						}
						
					}
				}
			}
		 }
		}
	return true;
	}
	
	private boolean set3WXfrSeqData(SequenceCode seq){
		for(Branch bra: this._net.getBranchList()){
			if(bra instanceof Acsc3WBranch){
				Acsc3WBranch bra3w = (Acsc3WBranch) bra;
				if(seq==SequenceCode.NEGATIVE){
					  //no operation is needed, since assumption NegZ = PosZ is already implicitly implemented 
					 // in ipss core engine.
				}
				else if(seq==SequenceCode.ZERO){
					//HV/MV/LV : Wye-Wye-Delta connection
					//Based on PSS/E, it will be 1-1-3 connection
					
					AcscBranch fromBranch =(AcscBranch) bra3w.getFromBranch();
					AcscBranch toBranch =(AcscBranch) bra3w.getToBranch();
					AcscBranch terBranch =(AcscBranch) bra3w.getTertiaryBranch();
					
					fromBranch.setXfrFromConnectCode(XfrConnectCode.WYE_SOLID_GROUNDED);
					fromBranch.setXfrToConnectCode(XfrConnectCode.WYE_SOLID_GROUNDED);
					
					toBranch.setXfrFromConnectCode(XfrConnectCode.WYE_SOLID_GROUNDED);
					toBranch.setXfrToConnectCode(XfrConnectCode.WYE_SOLID_GROUNDED);
					
					terBranch.setXfrFromConnectCode(XfrConnectCode.DELTA);
					terBranch.setXfrToConnectCode(XfrConnectCode.WYE_SOLID_GROUNDED);
					
					
				}
			}
			
		}
		return true;
	}
	
	private boolean setFixedShuntSeqData(SequenceCode seq){
		//TODO
		return true;
	}
	
	private boolean setSwitchedShuntSeqData(SequenceCode seq){
		//TODO
		return true;
		
	}
	
	private boolean is3WXfrBranch(AcscBranch bra){
		if(bra.isXfr()||bra.isPSXfr()){
			if(bra.getId().contains("3WNDTR")){
				return true;
			}	
		}
		return false;
	}
	private boolean isGenXfr(AcscBranch bra){
		//base voltage less than 35 kV
		if(bra.getFromAcscBus().getBaseVoltage()<35000 && bra.getFromAcscBus().isGen())
			return true;
		else if(bra.getToAcscBus().getBaseVoltage()<35000 && bra.getToAcscBus().isGen()){
			return true;
		}
		return false;
	}
	private AcscBus getHighVoltageBus(AcscBranch bra){
		if(bra.getFromAcscBus().getBaseVoltage()>bra.getToAcscBus().getBaseVoltage())
			return bra.getFromAcscBus();
		else if(bra.getFromAcscBus().getBaseVoltage()<bra.getToAcscBus().getBaseVoltage())
			return bra.getToAcscBus();
		else {
			IpssLogger.getLogger().info("From bus and to bus base voltage is the same, Xfr:"+bra.getId());
			return bra.getFromAcscBus();
		}
		
	}
		

}
