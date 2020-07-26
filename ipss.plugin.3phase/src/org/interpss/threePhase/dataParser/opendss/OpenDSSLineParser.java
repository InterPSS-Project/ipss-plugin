package org.interpss.threePhase.dataParser.opendss;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.threePhase.basic.LineConfiguration;
import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranchCode;

public class OpenDSSLineParser {

    private OpenDSSDataParser dataParser = null;
	
	public OpenDSSLineParser(OpenDSSDataParser parser){
		this.dataParser = parser;
	}
	
	public boolean parseLineData(String lineStr) throws InterpssException{
		boolean no_error = true;
    	
        final String  DOT = ".";
		
		String  lineName = "";
		String  fromBusId = "";
		String  fromBusPhases  ="1.2.3"; // by default;;
		String  toBusId = "";
		String  toBusPhases = "1.2.3"; // by default;
		String  lineCodeId = "";
		String  units = "";
		double  lineLength = 0;
		int     phaseNum = 3;    // 3 phases by default
		
		String  fromBusStr = "";
		String  toBusStr = "";
		
		DStabNetwork3Phase distNet = this.dataParser.getDistNetwork();
		
		DStab3PBus fromBus = null, toBus = null;
	
		int phaseIdx = -1, lineCodeIdx = -1;
		
		/*
		 * (6) r1 Positive sequence Resistance, ohms per unit length. See also Rmatrix.
			(7) x1 Positive sequence Reactance, ohms per unit length. See also Xmatrix
			(8) r0 Zero sequence Resistance, ohms per unit length.
			(9) x0 Zero sequence Reactance, ohms per unit length.
			(10) c1 Positive sequence capacitance, nf per unit length. See also Cmatrix.
			(11) c0 Zero sequence capacitance, nf per unit length.
		 */
		double r1= 0,r0 = 0, x1 = 0, x0 = 0, c1 = 0, c0 = 0;
		

		String[] lineStrAry = lineStr.toLowerCase().split("\\s+");
		
		for(int i = 0;i<lineStrAry.length;i++){
			if(lineStrAry[i].contains("line.")){
				lineName    = lineStrAry[i].substring(5);
			}
			else if(lineStrAry[i].contains("phases=")){
				phaseIdx = i;
				phaseNum  = Integer.valueOf(lineStrAry[i].substring(7));
			}
			
			else if(lineStrAry[i].contains("bus1=")){
				fromBusStr = lineStrAry[i].substring(5);
			}
			else if(lineStrAry[i].contains("bus2=")){
				toBusStr = lineStrAry[i].substring(5);
			}
			else if(lineStrAry[i].contains("linecode=")){
				lineCodeIdx = i;
				lineCodeId= lineStrAry[i].substring(9);
			}
			else if(lineStrAry[i].contains("length=")){
				lineLength = Double.valueOf(lineStrAry[i].substring(7));
			}
			else if(lineStrAry[i].contains("units=")){
				units = lineStrAry[i].substring(5);
			}
			else if(lineStrAry[i].contains("r1=")){
				r1 = Double.valueOf(lineStrAry[i].substring(3));
			}
			else if(lineStrAry[i].contains("r0=")){
				r0 = Double.valueOf(lineStrAry[i].substring(3));
			}
			else if(lineStrAry[i].contains("x1=")){
				x1 = Double.valueOf(lineStrAry[i].substring(3));
			}
			else if(lineStrAry[i].contains("x0=")){
				x0 = Double.valueOf(lineStrAry[i].substring(3));
			}
			else if(lineStrAry[i].contains("c1=")){
				c1 = Double.valueOf(lineStrAry[i].substring(3));
			}
			else if(lineStrAry[i].contains("c0=")){
				c0 = Double.valueOf(lineStrAry[i].substring(3));
			}
			
			
		}
		
//		if(lineName.equals("l4")){
//			System.out.println("processing line :"+lineName);
//		}
		
		//busId is the substring before the first DOT
		//phases info is defined in the substring after the first DOT
		if(fromBusStr.contains(DOT)){
			fromBusId = fromBusStr.substring(0, fromBusStr.indexOf(DOT));
		    fromBusPhases = fromBusStr.substring( fromBusStr.indexOf(DOT)+1);
		}else{
			fromBusId = fromBusStr;
			
		}
		
		if(toBusStr.contains(DOT)){
			toBusId = toBusStr.substring(0, toBusStr.indexOf(DOT));
		    toBusPhases =  toBusStr.substring( toBusStr.indexOf(DOT)+1);
		}else{
			toBusId = toBusStr;
			
		}
		
		Complex3x3 zabc = null;
		Complex3x3 yshuntabc = new Complex3x3();
		LineConfiguration config = null;
		
		if(lineCodeIdx> 0){ // line parameters defined by line code
			
			config = this.dataParser.getLineConfigTable().get(lineCodeId);
			
			if(config!=null){
				zabc = config.getZ3x3Matrix();
			}
			else{
				throw new Error("LineConfiguration definition not found, LineCodeId:"+lineCodeId);
			}
		}
		else{ // line parameters defined by raw data
			if(r1>= 0 || x1>0){
		
				Complex z1 = new Complex(r1,x1);
				Complex z0 = new Complex(r0,x0);
				
				// input as three sequence data and then converted it three-phase
				zabc = new Complex3x3(z1,z1,z0).ToAbc(); 
			}
			else{
				throw new Error("Error in Line Z, Y parameter raw data: "+lineStr);
			}
			
		}
		
		if(!fromBusPhases.equals(toBusPhases)){
			throw new Error("different phase arrangements on both terminals not support yet, from: "+fromBusPhases+ ", to: "+toBusPhases);
		}
		if(phaseNum==3){
			// no change is needed
		}
		else if(phaseNum==2){
			if(fromBusPhases.equals("1.2")){
				//no change is needed
			}
			else if (fromBusPhases.equals("1.3")){
				//no change is needed
				zabc.ac = zabc.ab;
				zabc.ab = new Complex(0.0);
				
				zabc.ca = zabc.ba;
				zabc.ba = new Complex(0.0);
				
				zabc.cc = zabc.bb;
				zabc.bb = new Complex(0.0);
			}
			else if (fromBusPhases.equals("2.3")){
				
				zabc.cc = zabc.bb;
				
				zabc.bb = zabc.aa;
				zabc.aa = new Complex(0.0);
				
				zabc.bc = zabc.ab;
				zabc.ab = new Complex(0.0);
				
				zabc.cb = zabc.ba;
				zabc.ba = new Complex(0.0);
				
			}
			else{
				throw new Error("phase arrangement not support yet : "+fromBusPhases);
			}
		}
		else if(phaseNum==1){
			
			
			if(fromBusPhases.equals("1")){
				// by default, phase = "1", no change is needed
			}
			else if(fromBusPhases.equals("2")){
				if(zabc.aa.abs()<1.0E-8 && zabc.bb.abs()>1.0E-5){
					zabc.aa = new Complex(0.0);
					zabc.cc = new Complex(0.0);
					// no change to zabc.cc is needed 
				}
				else{
					zabc.bb = zabc.aa;
					zabc.aa = new Complex(0.0);
					zabc.cc = new Complex(0.0);
				}
			}
			else if(fromBusPhases.equals("3")){
				Complex diag = null;
				if(zabc.aa.abs()<1.0E-8 && zabc.cc.abs()>1.0E-5){
					zabc.aa = new Complex(0.0);
					zabc.bb = new Complex(0.0);
					// no change to zabc.cc is needed 
				}
				else{
					zabc.cc = zabc.aa;
					zabc.aa = new Complex(0.0);
					zabc.bb = new Complex(0.0);
				}
			}
			else{
				throw new Error("phase arrangement not support yet : "+lineStr);
			}
		}
		else{
			throw new Error("phase number must be 1, 2 or 3");
		}
		
		fromBusId =this.dataParser.getBusIdPrefix()+fromBusId;
		toBusId =this.dataParser.getBusIdPrefix()+toBusId;
			
		if(distNet.getBus(fromBusId)==null)
			fromBus = ThreePhaseObjectFactory.create3PDStabBus(fromBusId, distNet);
		
		if(distNet.getBus(toBusId)==null)
			toBus = ThreePhaseObjectFactory.create3PDStabBus(toBusId, distNet);
		
		DStab3PBranch line = ThreePhaseObjectFactory.create3PBranch(fromBusId, toBusId, "1", distNet);
		
		line.setName(this.dataParser.getBusIdPrefix()+lineName);
	
		
		line.setBranchCode(AclfBranchCode.LINE);
		// the format of Zmatrix need to be consistent with the number of phases and the phases in use.
		
		
		// need to consider the line length
		//TODO consistency of the unit types
		line.setZabc(zabc.multiply(lineLength));
		
		if(line.getZabc().absMax()<1.0E-7){
			throw new Error("Line Zabc.absMax() is less than 1.0E-7. LineID, Name = "+line.getId()+", "+line.getName());
		}
		
		//TODO ShuntY is not considered for this initial implementation 
		//line.setFromShuntYabc(yshuntabc.multiply(0.5));
		//line.setToShuntYabc(yshuntabc.multiply(0.5));
		
		return no_error;
		
	}
	
//	private boolean parseLineDataWithLineCode(String lineStr) throws InterpssException{
//		boolean no_error = true;
//		
//        final String  DOT = ".";
//		
//		String  lineId = "";
//		String  fromBusId = "";
//		String  fromBusPhases  ="1.2.3"; // by default;;
//		String  toBusId = "";
//		String  toBusPhases = "1.2.3"; // by default;
//		String  lineCodeId = "";
//		String  units = "";
//		double  lineLength = 1.0; // by default is 1.0
//		int     phaseNum = 3;    // 3 phases by default
//		
//		String  fromBusStr = "";
//		String  toBusStr = "";
//		
//		
//		DStabNetwork3Phase distNet = this.dataParser.getDistNetwork();
//		
//		Bus3Phase fromBus = null, toBus = null;
//	
//		
//		int lineIdIdx = 1, phaseIdx = 2, fromBusIdx = 3, toBusIdx = 4, lineCodeIdx = 5, lengthIdx = 6;
//		
//		if (!lineStr.contains("Phases=")){
//			phaseIdx = -1;
//			fromBusIdx =2;
//			toBusIdx = 3; 
//			lineCodeIdx = 4; 
//			lengthIdx = 5;
//			
//		}
//		String[] lineStrAry = lineStr.split("\\s+");
//		
//		if(phaseIdx>0)
//		     phaseNum  = Integer.valueOf(lineStrAry[phaseIdx].substring(7));
//		
//		lineId    = lineStrAry[lineIdIdx].substring(4);
//		fromBusStr = lineStrAry[fromBusIdx].substring(4);
//		toBusStr   = lineStrAry[toBusIdx].substring(4);
//		lineCodeId = lineStrAry[lineCodeIdx].substring(9);
//		lineLength = Double.valueOf(lineStrAry[lengthIdx].substring(7));
//		
//		for(int i =0;i<lineStrAry.length;i++){
//			//TODO
//				
//				
//				
//		}
//		
//		//busId is the substring before the first DOT
//		//phases info is defined in the substring after the first DOT
//		if(fromBusStr.contains(DOT)){
//			fromBusId = fromBusStr.substring(0, fromBusStr.indexOf(DOT));
//		    fromBusPhases =  fromBusStr.substring( fromBusStr.indexOf(DOT)+1);
//		}else{
//			fromBusId = fromBusStr;
//			
//		}
//		
//		if(toBusStr.contains(DOT)){
//			toBusId = toBusStr.substring(0, toBusStr.indexOf(DOT));
//			
//		    toBusPhases = toBusStr.substring( toBusStr.indexOf(DOT)+1);
//		}else{
//			fromBusId = toBusStr;
//			
//		}
//		
//		LineConfiguration config = this.dataParser.getLineConfigTable().get(lineCodeId);
//		Complex3x3 zabc = null;
//		Complex3x3 yshuntabc = new Complex3x3();
//		
//		if(config!=null){
//			zabc = config.getZ3x3Matrix();
//			
//			if(!fromBusPhases.equals(toBusPhases)){
//				throw new Error("different phase arrangements on both terminals not support yet, from: "+fromBusPhases+ ", to: "+toBusPhases);
//			}
//			if(phaseNum==3){
//				// no change is needed
//			}
//			else if(phaseNum==2){
//				if(fromBusPhases.equals("1.2")){
//					//no change is needed
//				}
//				else if (fromBusPhases.equals("1.3")){
//					//no change is needed
//					zabc.ac = zabc.ab;
//					zabc.ab = new Complex(0.0);
//					
//					zabc.ca = zabc.ba;
//					zabc.ba = new Complex(0.0);
//					
//					zabc.cc = zabc.bb;
//					zabc.bb = new Complex(0.0);
//				}
//				else if (fromBusPhases.equals("2.3")){
//					
//					zabc.cc = zabc.bb;
//					
//					zabc.bb = zabc.aa;
//					zabc.aa = new Complex(0.0);
//					
//					zabc.bc = zabc.ab;
//					zabc.ab = new Complex(0.0);
//					
//					zabc.cb = zabc.ba;
//					zabc.ba = new Complex(0.0);
//					
//				}
//				else{
//					throw new Error("phase arrangement not support yet : "+fromBusPhases);
//				}
//			}
//			else if(phaseNum==1){
//				// by default, phase = "1"
//				
//				if(fromBusPhases.equals("2")){
//					zabc.bb = zabc.aa;
//					zabc.aa = new Complex(0.0);
//				}
//				else if(fromBusPhases.equals("3")){
//					zabc.cc = zabc.aa;
//					zabc.aa = new Complex(0.0);
//				}
//				else{
//					throw new Error("phase arrangement not support yet : "+fromBusPhases);
//				}
//			}
//			else{
//				throw new Error("phase number must be 1, 2 or 3");
//			}
//					
//		}
//		else{
//			throw new Error("LineConfiguration definition not found, LineCodeId:"+lineCodeId);
//		}
//		
//		
//		if(distNet.getBus(fromBusId)==null)
//			fromBus = ThreePhaseObjectFactory.create3PDStabBus(fromBusId, distNet);
//		
//		if(distNet.getBus(toBusId)==null)
//			toBus = ThreePhaseObjectFactory.create3PDStabBus(toBusId, distNet);
//		
//		Branch3Phase line = ThreePhaseObjectFactory.create3PBranch(fromBusId, toBusId, "1", distNet);
//		line.setBranchCode(AclfBranchCode.LINE);
//		// the format of Zmatrix need to be consistent with the number of phases and the phases in use.
//		
//		
//		line.setZabc(zabc);
//		
//		
//		return no_error;
//	}
	
	
}
