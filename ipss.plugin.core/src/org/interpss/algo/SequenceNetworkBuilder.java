package org.interpss.algo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.NumericConstant;
import org.interpss.numeric.util.Number2String;

import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.acsc.Acsc3WBranch;
import com.interpss.core.acsc.AcscBranch;
import com.interpss.core.acsc.AcscBus;
import com.interpss.core.acsc.AcscGen;
import com.interpss.core.acsc.BaseAcscBus;
import com.interpss.core.acsc.BaseAcscNetwork;
import com.interpss.core.acsc.BusGroundCode;
import com.interpss.core.acsc.BusScCode;
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
		//update the sequence data attribute
		this._net.setPositiveSeqDataOnly(false);
		return ok;
			
	}
	
	/**
	 * generate the negative and zero sequence network data.
	 * The output file is based on PSS/E v30 sequence data format.
	 *  0,  30           / PSS(R)E 30 SEQ created by RWSQVERSION  FRI, JUN 21 2013  16:16
	 0 /End of Positive sequence Generator data, Begin Negative sequence Generator data
	 0 /End of Negative sequence Generator data, Begin Zero sequence Generator data
	 0 /End of Zero sequence Generator data, Begin Negative sequence Load data
	 0 /End of Negative sequence Load data, Begin Zero sequence Load data
	 0 /End of Zero sequence Load data, Begin Zero sequence Branch data
	 0 /End of Zero sequence Branch data, Begin Zero sequence Mutual data
	 0 /End of Zero sequence Mutual data, Begin Zero sequence Transformer data
	 0 /End of Zero sequence Transformer data, Begin Zero sequence Switched shunt data
	 0 /END OF ZERO SEQ. SWITCHED SHUNT DATA, BEGIN ZERO SEQ. FIXED SHUNT DATA
	 0 /END OF ZERO SEQ. FIXED SHUNT DATA
	 * 
	 * @param seqFileName
	 * @return
	 */
	public boolean generateSeqNetworkData(String seqFileName){
		
		boolean flag = true;
		StringBuffer  dyrSB= new StringBuffer();
		String header = "0,  30           / PSS(R)E 30 SEQ";
		String createdDateTimeInfo = "";
		String posSeqGenData = "";
		String negSeqGenData = "";
		String zeroSeqGenData = "";
		String negSeqLoadData = "";
		String zeroSeqLoadData = "";
		String zeroSeqBranchData = "";
		String zeroSeqXfrData = ""; // including 2-winding and 3-winding transformers
		String END_OF_POS_SeqGenData = "0 /End of Positive sequence Generator data, Begin Negative sequence Generator data";
		String END_OF_NEG_SeqGenData = "0 /End of Negative sequence Generator data, Begin Zero sequence Generator data";
	    String END_OF_ZERO_SeqGenData = "0 /End of Zero sequence Generator data, Begin Negative sequence Load data";
	    String END_OF_NEG_SeqLoadData = "0 /End of Negative sequence Load data, Begin Zero sequence Load data";
	    String END_OF_ZERO_SeqLoadData = "0 /End of Zero sequence Load data, Begin Zero sequence Branch data";
	    String END_OF_ZERO_SeqBranchData = "0 /End of Zero sequence Branch data, Begin Zero sequence Mutual data";
	    String END_OF_ZERO_SeqMutualData = "0 /End of Zero sequence Mutual data, Begin Zero sequence Transformer data";
	    String END_OF_ZERO_SeqTransformerData = "0 /End of Zero sequence Transformer data, Begin Zero sequence Switched shunt data";
	    String END_OF_ZERO_SeqSwitchedShuntData = "0 /END OF ZERO SEQ. SWITCHED SHUNT DATA, BEGIN ZERO SEQ. FIXED SHUNT DATA";
	    String END_OF_ZERO_SeqFixedShuntData = "0 /END OF ZERO SEQ. FIXED SHUNT DATA";
	    
		/*
		 *  Format
		 *  0,  30           / PSS(R)E 30 SEQ created by RWSQVERSION  FRI, JUN 21 2013  16:16
		     30,'1 ', 0.00000E+00, 2.24000E-01
		 0 /End of Positive sequence Generator data, Begin Negative sequence Generator data
		     30,'1 ', 0.00000E+00, 2.24000E-01
		 0 /End of Negative sequence Generator data, Begin Zero sequence Generator data
		     30,'1 ', 0.00000E+00, 2.24000E-01
		 0 /End of Zero sequence Generator data, Begin Negative sequence Load data
		 0 /End of Negative sequence Load data, Begin Zero sequence Load data
		 0 /End of Zero sequence Load data, Begin Zero sequence Branch data
		      1,     2,'1 ', 8.75000E-3, 1.02750E-1,    0.69870, 0.00000E+0, 0.00000E+0, 0.00000E+0, 0.00000E+0
		 0 /End of Zero sequence Branch data, Begin Zero sequence Mutual data
		 0 /End of Zero sequence Mutual data, Begin Zero sequence Transformer data
		      2,    30,     0,'1 ',  2, 0.00000E+0, 0.00000E+0, 0.00000E+0, 1.81000E-2
		 0 /End of Zero sequence Transformer data, Begin Zero sequence Switched shunt data
		 0 /END OF ZERO SEQ. SWITCHED SHUNT DATA, BEGIN ZERO SEQ. FIXED SHUNT DATA
		 0 /END OF ZERO SEQ. FIXED SHUNT DATA
		 */
		
	    
	    // HEADER
	    
	    dyrSB.append(header+"\n");
	    
	    // generation sequence data
	    for(BaseAcscBus<?,?> scBus: this._net.getBusList()){
			if(scBus.isGen()){
				if(scBus.getContributeGenList().size()>0){
					for(AclfGen gen:scBus.getContributeGenList()){
						if(gen.isActive()){
							AcscGen scGen =(AcscGen) gen;
							long busNum = scBus.getNumber();
							String id = scGen.getId();
							if(id.length()==1) id = id+" ";
							String genZ1Str = Number2String.toStr(scGen.getPosGenZ().getReal())+", "+Number2String.toStr(scGen.getPosGenZ().getImaginary());
							String genZ2Str = Number2String.toStr(scGen.getNegGenZ().getReal())+", "+Number2String.toStr(scGen.getNegGenZ().getImaginary());
							String genZ0Str = Number2String.toStr(scGen.getZeroGenZ().getReal())+", "+Number2String.toStr(scGen.getZeroGenZ().getImaginary());
							
							
							posSeqGenData = posSeqGenData +busNum+", "+"'"+id+"', "+ genZ1Str+"\n";
							negSeqGenData = negSeqGenData +busNum+", "+"'"+id+"', "+ genZ2Str+"\n";
							zeroSeqGenData = zeroSeqGenData +busNum+", "+"'"+id+"', "+ genZ0Str+"\n";
							
						}
					}
				}
			}
	    }
		
	    dyrSB.append(posSeqGenData);
	    dyrSB.append(END_OF_POS_SeqGenData+"\n");
	    
	    dyrSB.append(negSeqGenData);
	    dyrSB.append(END_OF_NEG_SeqGenData+"\n");
	    
	    dyrSB.append(zeroSeqGenData);
	    dyrSB.append(END_OF_ZERO_SeqGenData+"\n");
	    
	    if(negSeqLoadData.length() > 0){
	    	dyrSB.append(negSeqLoadData); 
	    }
	    dyrSB.append(END_OF_NEG_SeqLoadData+"\n");
	    
	    
	    if(zeroSeqLoadData.length() > 0){
	    	dyrSB.append(zeroSeqLoadData); 
	    }
	    dyrSB.append(END_OF_ZERO_SeqLoadData+"\n");	
	    
	    
	    
	    
	    // zero seq branch data
	    for(AcscBranch scBranch: this._net.getBranchList()){
	    	
	    	if(scBranch.isLine()){
	    		// 1,     2,'1 ', 8.75000E-3, 1.02750E-1,    0.69870, 0.00000E+0, 0.00000E+0, 0.00000E+0, 0.00000E+0
	    		
	    		long fBusNum = scBranch.getFromAcscBus().getNumber();
	    		long tBusNum = scBranch.getToAcscBus().getNumber();
	    		String lineId = scBranch.getCircuitNumber();
	    		if(lineId.length() ==1) lineId +=" ";
	    		
	    		String RXBStr = Number2String.toStr(scBranch.getZ0().getReal())+", "+
	    				Number2String.toStr(scBranch.getZ0().getImaginary())+", "+
	    				Number2String.toStr(scBranch.getHB0()*2.0); // PSSE uses total zero sequence branch charging susceptance
	    	    // zero seq data of shunts connected to both ends of the line
	    		String zeroSeqGIBIGJBJ = "0.0, 0.0, 0.0, 0.0"; 
	    	    zeroSeqBranchData += fBusNum+", "+tBusNum+", '"+lineId+"', "+RXBStr+","+zeroSeqGIBIGJBJ+"\n";
	    	}
	    	
	    	
	    }
	    
	    dyrSB.append(zeroSeqBranchData);
	    dyrSB.append(END_OF_ZERO_SeqBranchData+"\n");
	    dyrSB.append(END_OF_ZERO_SeqMutualData+"\n");
	    
	    
	    // zero seq transformer data
	    //I,J,K,ICKT,CZ0,CZG,CC,RG1,XG1,R01,X01,RG2,XG2,R02,X02,RNUTRL,XNUTRL
		/*
		 * for two-winding transformers:
	       I, J, K, ICKT, CC, RG, XG, R1, X1, R2, X2
	       
	       for three-winding transformers:
	       I, J, K, ICKT, CC, RG, XG, R1, X1, R2, X2, R3, X3
		 * 
		 * 
		 * For a two-winding transformer, valid values are 1 through 9. They define the 
	       following zero sequence connections that are shown in Figure 5-18.
	        1 series path (e.g., grounded wye-grounded wye)
	        2 no series path, ground path on winding one side (e.g., grounded wye-delta)
	        3 no series path, ground path on winding two side (e.g., delta-grounded wye)
	        4 no series or ground path (e.g., delta-delta)
	        5 series path, ground path on winding two side (normally only used as part of 
	          a three-winding transformer) 
	        6 no series path, ground path on winding one side, earthing transformer on 
	          winding two side (e.g., grounded wye-delta with an earthing transformer)
	        7 no series path, earthing transformer on winding one side, ground path on 
	          winding two side (e.g., delta with an earthing transformer-grounded wye)
	        8 series path, ground path on each side (e.g., grounded wye-grounded wye 
	          core-type autotransformer with a grounding resistance)
	     ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++     
	         Transformer leakage impedance: R1, X1, R2, X2
	         Grounding: RG1, XG1, RG2, XG2
	      RG, XG  Zero sequence grounding impedance for an impedance grounded transformer ZG
                  For a three-winding transformer, ZG is modeled in the lowest numbered 
                  winding whose corresponding connection code is 2, 3, 5, 6 or 7    
	      R1, X1  Zero sequence impedance of a two-winding transformer, or the winding one zero 
                  sequence impedance of a three-winding transformer,
          R2, X2  For 2-winding transformer, they are only valid when CC = 8, for specifying the grounding impedance
                  at the winding 2.
                  For 3-winding xfr, stands for the winding 2 zero sequence impedance
          R3, X3  Winding 3 zero sequence impedance 
	     */
	    
	    int cc = 1;
	    for(AcscBranch scBranch: this._net.getBranchList()){
	    	
	    	if(scBranch.isXfr() && !is3WXfrBranch(scBranch)){

	    		long fBusNum = scBranch.getFromAcscBus().getNumber();
	    		long tBusNum = scBranch.getToAcscBus().getNumber();
	    		String lineId = scBranch.getCircuitNumber();
	    		if(lineId.length() ==1) lineId +=" ";
	    		
	    		if(scBranch.getXfrFromConnectCode() == XfrConnectCode.WYE_SOLID_GROUNDED){
	    			if(scBranch.getXfrToConnectCode() == XfrConnectCode.WYE_SOLID_GROUNDED){
	    				cc =1;
	    			}
	    			else if(scBranch.getXfrToConnectCode() == XfrConnectCode.DELTA){
	    				cc = 2;
	    			}
	    			else{
	    				throw new UnsupportedOperationException("The transformer connection type is not supported yet! Connection @ from, to:"+
		    					scBranch.getXfrFromConnectCode()+"," +scBranch.getXfrToConnectCode());
	    			}
	    		}
	    		else if(scBranch.getXfrFromConnectCode() == XfrConnectCode.DELTA){
	                if(scBranch.getXfrToConnectCode() == XfrConnectCode.WYE_SOLID_GROUNDED){
	    				cc = 3;
	    			}
	    			else if(scBranch.getXfrToConnectCode() == XfrConnectCode.DELTA){
	    				cc = 4;
	    			}
	    			else{
	    				throw new UnsupportedOperationException("The transformer connection type is not supported yet! Connection @ from, to:"+
		    					scBranch.getXfrFromConnectCode()+"," +scBranch.getXfrToConnectCode());
	    			}
	    		}
	    		else{
	    			throw new UnsupportedOperationException("The transformer connection type is not supported yet! Connection @ from, to:"+
	    					scBranch.getXfrFromConnectCode()+"," +scBranch.getXfrToConnectCode());
	    		}
	    		
	    		
	    	 Complex z0 = scBranch.getZ0();
	    	 Complex zg1 = scBranch.getXfrFromGroundZ();  // if cc =1 or 2 or 6
	    	 Complex zg2 = scBranch.getXfrToGroundZ();    // if cc =1 or 3
	    	 
	    	 String z0Str =  Number2String.toStr(z0.getReal())+", "+Number2String.toStr(z0.getImaginary());
	    	 String zg1Str ="0.0, 0.0";
	    	 String rx2Str ="0.0, 0.0";
	    	 // I, J, K, ICKT, CC, RG, XG, R1, X1, R2, X2
	    	 if(cc ==1 || cc ==2 || cc ==6){
	    		 if(zg1 != null){
	    			 zg1Str = Number2String.toStr(zg1.getReal())+", "+Number2String.toStr(zg1.getImaginary());
	    		 }
	    	 }
	    	 
	    	 if(cc ==1 || cc ==3){
	    		 if(zg2 != null){
	    			 rx2Str = Number2String.toStr(zg2.getReal())+", "+Number2String.toStr(zg2.getImaginary());
	    		 }
	    	 }
	    	 int thirdBusNum  = 0;
	    	 // add the the zeroSeqXfrData
	    	 zeroSeqXfrData += fBusNum+", "+tBusNum+", "+ thirdBusNum+", '"+lineId+"', "+cc+", "+zg1Str+", "+z0Str+", "+rx2Str+"\n";
	    	}
	    	
	    }
	    
	    if(zeroSeqXfrData.length()>0){
	    	dyrSB.append(zeroSeqXfrData);
	    }
	    dyrSB.append(END_OF_ZERO_SeqTransformerData+"\n");
	    
	    
	    
	    // zero sequence fixed shunt data
	    dyrSB.append(END_OF_ZERO_SeqSwitchedShuntData+"\n");
	    dyrSB.append(END_OF_ZERO_SeqFixedShuntData+"\n");
	    
	    
	    try {
			Files.write(Paths.get(seqFileName), dyrSB.toString().getBytes());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return flag = false;
		}
	    
	    IpssLogger.getLogger().info("The Sequence network data is saved to :"+ Paths.get(seqFileName));
	    
		return flag;
	}
	
	
	
	
	private boolean setGenSeqData(SequenceCode seq){
		for(BaseAcscBus<?,?> scBus: this._net.getBusList()){
			if(!scBus.isGen()){
				scBus.setScCode(BusScCode.NON_CONTRI);
				scBus.setScGenZ(NumericConstant.LargeBusZ, SequenceCode.POSITIVE);
				scBus.setScGenZ(NumericConstant.LargeBusZ, SequenceCode.NEGATIVE);
				scBus.setScGenZ(NumericConstant.LargeBusZ, SequenceCode.ZERO);
				scBus.getGrounding().setCode(BusGroundCode.UNGROUNDED);
				scBus.getGrounding().setZ(NumericConstant.LargeBusZ);
				continue;
			}
			
			// generation bus contributes to short circuit;
			scBus.setScCode(BusScCode.CONTRIBUTE);
			
			// grounding information affects the zero sequence
			scBus.getGrounding().setCode(BusGroundCode.UNGROUNDED);
			scBus.getGrounding().setZ(NumericConstant.LargeBusZ);
			
			if(scBus.getContributeGenList().size()>0){
				for(AclfGen gen:scBus.getContributeGenList()){
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
		for(BaseAcscBus<?,?> scBus: this._net.getBusList()){
			if(scBus.isActive() && scBus.getContributeLoadList().size()>0){
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
				
				if(bra.getId().equals("Bus198->Bus202(1)")){
					System.out.print("proc Bus198-202");
				}
				
			if(scBranch.isXfr() && !is3WXfrBranch(scBranch)){
				//Line NegZ = PosZ
				if(seq==SequenceCode.NEGATIVE){
				  //no operation is needed, since assumption NegZ = PosZ is already implicitly implemented 
				 // in ipss core engine.
				}
				else if(seq==SequenceCode.ZERO){
					if(this.overrideSeqData || scBranch.getZ0().abs()==0.0){
						scBranch.setZ0(scBranch.getZ());
						//zero sequence branch shunt B0 setting, zero by default
						scBranch.setHB0(0);
						
						
						if(scBranch.getId().equals("Bus7003->Bus3(1)")){
							System.out.println("processing: Bus7003->Bus3(1)");
						}
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
						//Type-1: EHV Yg-Yg
						if(getLowVoltageBus(scBranch).getBaseVoltage()>=115000.0){
							scBranch.setXfrFromConnectCode(XfrConnectCode.WYE_SOLID_GROUNDED);
							scBranch.setXfrToConnectCode(XfrConnectCode.WYE_SOLID_GROUNDED);
							
						}
						//type-2 // Delta-Yg,  mainly for subtransmission step-down transformer
						else if(getHighVoltageBus(scBranch).getBaseVoltage()<=115000 && getLowVoltageBus(scBranch).getBaseVoltage()<69000.0 
							&& !isGenXfr(scBranch) && getLowVoltageBus(scBranch).isLoad()){
							if(scBranch.getFromAcscBus().getBaseVoltage() == scBranch.getToAcscBus().getBaseVoltage()){
								scBranch.setXfrFromConnectCode(XfrConnectCode.WYE_SOLID_GROUNDED);
								scBranch.setXfrToConnectCode(XfrConnectCode.WYE_SOLID_GROUNDED);
							}
							else if(scBranch.getFromPhysicalBusId().equals(getHighVoltageBus(scBranch).getId())){
							  scBranch.setXfrFromConnectCode(XfrConnectCode.DELTA);
							  scBranch.setXfrToConnectCode(XfrConnectCode.WYE_SOLID_GROUNDED);
							}
							else{
								scBranch.setXfrFromConnectCode(XfrConnectCode.WYE_SOLID_GROUNDED);
								scBranch.setXfrToConnectCode(XfrConnectCode.DELTA);
							}
								
						}
						//type-3  generation step up transformer, high side Yg, low side Delta
						else if(isGenXfr(scBranch) && getLowVoltageBus(scBranch).getBaseVoltage()<69000.0){
							//NOTE: for step up transformer: the delta connection is on the generator (low voltage) bus side .
							//
							if(scBranch.getToAcscBus().isGen()){ 
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
							IpssLogger.getLogger().severe("Error: transformer is not belong to predefined types, set to Yg/Yg connection: "+scBranch.getId());
							
							scBranch.setXfrFromConnectCode(XfrConnectCode.WYE_SOLID_GROUNDED);
							scBranch.setXfrToConnectCode(XfrConnectCode.WYE_SOLID_GROUNDED);
						}
						
					}
				}
			}
		 }
		}
	return true;
	}
	
	private boolean set3WXfrSeqData(SequenceCode seq){
		for(Branch bra: this._net.getSpecialBranchList()){

			if(bra instanceof Acsc3WBranch){
				Acsc3WBranch bra3w = (Acsc3WBranch) bra;
				if(seq==SequenceCode.NEGATIVE){
					  //no operation is needed, since assumption NegZ = PosZ is already implicitly implemented 
					 // in ipss core engine.
				}
				else if(seq==SequenceCode.ZERO){
					
					
					//Type-1: HV/MV/LV : Wye-Wye-Delta connection
					//Based on PSS/E, it will be 1-1-3 connection

					AcscBranch fromBranch =(AcscBranch) bra3w.getFromBranch();
					AcscBranch toBranch =(AcscBranch) bra3w.getToBranch();
					AcscBranch terBranch =(AcscBranch) bra3w.getTertiaryBranch();
					
					fromBranch.setXfrFromConnectCode(XfrConnectCode.WYE_SOLID_GROUNDED);
					fromBranch.setXfrToConnectCode(XfrConnectCode.WYE_SOLID_GROUNDED);
					fromBranch.setZ0(fromBranch.getZ());
					fromBranch.setHB0(0);
					
					toBranch.setXfrFromConnectCode(XfrConnectCode.WYE_SOLID_GROUNDED);
					toBranch.setXfrToConnectCode(XfrConnectCode.WYE_SOLID_GROUNDED);
					toBranch.setZ0(toBranch.getZ());
					toBranch.setHB0(0);
					
					
					terBranch.setXfrFromConnectCode(XfrConnectCode.DELTA);
					terBranch.setXfrToConnectCode(XfrConnectCode.WYE_SOLID_GROUNDED);
					terBranch.setZ0(terBranch.getZ());
					terBranch.setHB0(0);
					
					//Type 2: Two generators connecting to one HV bus with 3w step up transformer
					/*
					 *  19410, 14990, 14991,'1 ',2,2,1,   0.00000,   0.00000,2,'SUN G1G2    ',1,   4,1.0000
                        0.00000,   0.10000,    34.50,   0.00000,   0.15000,    34.50,   0.00000,   0.10000,    34.50,1.01847,  31.9478
                        230.000, 230.000,   0.000,   115.00,   138.00,   115.00, 0,      0, 0.00000, 0.00000, 0.00000, 0.00000, 999, 0, 0.00000, 0.00000
                        13.8000,  13.800,   0.000,   115.00,   138.00,   115.00, 0,      0, 1.10000, 0.90000, 1.10000, 0.90000,  33, 0, 0.00000, 0.00000
                        13.8000,  13.800,   0.000,   115.00,   138.00,   115.00, 0,      0, 1.10000, 0.90000, 1.10000, 0.90000,  33, 0, 0.00000, 0.00000
					 */
					
					int highVoltSide = 1;
					if(bra3w.getFromBus().getBaseVoltage()>bra3w.getToBus().getBaseVoltage() &&
							bra3w.getFromBus().getBaseVoltage()>bra3w.getTertiaryBus().getBaseVoltage()){
						highVoltSide =1;
					}
					else if(bra3w.getToBus().getBaseVoltage()>bra3w.getFromBus().getBaseVoltage() &&
							bra3w.getToBus().getBaseVoltage()>bra3w.getTertiaryBus().getBaseVoltage()){
						highVoltSide =2;
					}
					else if(bra3w.getTertiaryBus().getBaseVoltage()>bra3w.getFromBus().getBaseVoltage() &&
							bra3w.getTertiaryBus().getBaseVoltage()>bra3w.getToBus().getBaseVoltage())
						highVoltSide =3;
					
					/*
					 * Update the 3w transformer connection if it is not an ordinary transformer
					 * if two bus voltages are less than 35 kV and they are equal 
					 */
					switch(highVoltSide){
					case 1:
						if(bra3w.getToBus().getBaseVoltage()<bra3w.getTertiaryBus().getBaseVoltage()){
							terBranch.setXfrFromConnectCode(XfrConnectCode.WYE_SOLID_GROUNDED);
							toBranch.setXfrFromConnectCode(XfrConnectCode.DELTA);
						}
						// fromBus is HV, toBus and terBus are generator bus, thus both are Delta connection
					   else if(bra3w.getToBus().getBaseVoltage()<35000.0 && bra3w.getToBus().getBaseVoltage()==bra3w.getTertiaryBus().getBaseVoltage()
						&& ((AclfBus)bra3w.getToBus()).isGen() && ((AclfBus)bra3w.getTertiaryBus()).isGen()){
							
							toBranch.setXfrFromConnectCode(XfrConnectCode.DELTA);
						}
						break;
					case 2:
						if(bra3w.getFromBus().getBaseVoltage()<bra3w.getTertiaryBus().getBaseVoltage()){
								terBranch.setXfrFromConnectCode(XfrConnectCode.WYE_SOLID_GROUNDED);
								fromBranch.setXfrFromConnectCode(XfrConnectCode.DELTA);
								
						 }
						// ToBus is HV, fromBus and terBus are generator bus, thus both are Delta connection
						else if(bra3w.getFromBus().getBaseVoltage()<35000.0 && bra3w.getFromBus().getBaseVoltage()==bra3w.getTertiaryBus().getBaseVoltage()
								&& ((AclfBus)bra3w.getFromBus()).isGen() && ((AclfBus)bra3w.getTertiaryBus()).isGen()){
							
							fromBranch.setXfrFromConnectCode(XfrConnectCode.DELTA);
	
						}
						break;
					case 3: //terBus is the HV
						terBranch.setXfrFromConnectCode(XfrConnectCode.WYE_SOLID_GROUNDED);
						
						if(bra3w.getFromBus().getBaseVoltage()>bra3w.getToBus().getBaseVoltage())
							toBranch.setXfrFromConnectCode(XfrConnectCode.DELTA);
							
						else if(bra3w.getFromBus().getBaseVoltage()<bra3w.getToBus().getBaseVoltage())
							fromBranch.setXfrFromConnectCode(XfrConnectCode.DELTA);
						
						// TerBus is HV, fromBus and toBus are generator bus, thus both are Delta connection
						else if(bra3w.getToBus().getBaseVoltage()<35000.0 && bra3w.getFromBus().getBaseVoltage()==bra3w.getToBus().getBaseVoltage()
								&& ((AclfBus)bra3w.getFromBus()).isGen() && ((AclfBus)bra3w.getToBus()).isGen()){
							fromBranch.setXfrFromConnectCode(XfrConnectCode.DELTA);
							toBranch.setXfrFromConnectCode(XfrConnectCode.DELTA);
							terBranch.setXfrFromConnectCode(XfrConnectCode.WYE_SOLID_GROUNDED);
						}
						break;
					}
					
					
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
		if(bra.getId().contains("3WNDTR")){
				return true;
		}	
		return false;
	}
	private boolean isGenXfr(AcscBranch bra){
		//base voltage less than 35 kV
		if(bra.getFromAcscBus().getBaseVoltage()<35000.0 && bra.getFromAcscBus().isGen())
			return true;
		else if(bra.getToAcscBus().getBaseVoltage()<35000.0 && bra.getToAcscBus().isGen()){
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
			IpssLogger.getLogger().info(" The base voltages of from bus and to bus are the same, Xfr:"+bra.getId());
			return bra.getFromAcscBus();
		}
		
	}
	
	private AcscBus getLowVoltageBus(AcscBranch bra){
		if(bra.getFromAcscBus().getBaseVoltage()>bra.getToAcscBus().getBaseVoltage())
			return bra.getToAcscBus();
		else if(bra.getFromAcscBus().getBaseVoltage()<bra.getToAcscBus().getBaseVoltage())
			return bra.getFromAcscBus();
		else {
			IpssLogger.getLogger().info("From bus and to bus base voltage is the same, Xfr:"+bra.getId());
			return bra.getToAcscBus();
		}
		
	}	

}
