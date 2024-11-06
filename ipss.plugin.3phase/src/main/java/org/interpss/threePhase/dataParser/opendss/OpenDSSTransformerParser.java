package org.interpss.threePhase.dataParser.opendss;

import static com.interpss.core.funcImpl.AcscFunction.acscXfrAptr;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.acsc.BusGroundCode;
import com.interpss.core.acsc.PhaseCode;
import com.interpss.core.acsc.XFormerConnectCode;
import com.interpss.core.acsc.adpter.AcscXformerAdapter;

public class OpenDSSTransformerParser {

    private OpenDSSDataParser dataParser = null;

	public OpenDSSTransformerParser(OpenDSSDataParser parser){
		this.dataParser = parser;
	}


	public boolean parseTransformerDataMultiLines(String[] xfrStr) throws InterpssException{


		/*
		 * ! LOAD TRANSFORMER AT 61s/610
			! This is a 150 kVA Delta-Delta stepdown from 4160V to 480V.

			New Transformer.XFM1  Phases=3   Windings=2 Xhl=2.72
			~ wdg=1 bus=61s       conn=Delta kv=4.16    kva=150    %r=0.635
			~ wdg=2 bus=610       conn=Delta kv=0.48    kva=150    %r=0.635

		 */

		boolean no_error = true;



		int phaseNum = 3;
		int windingNum = 2;
		double xhl = 0;
		double losspercent1 = 0,losspercent2;
		double kva1 = 0, kva2 = 0;
		double nominalKV1 = 0, nominalKV2 = 0;
		String xfrId = "";
		String fromBusId = "", toBusId = "";
		String fromConnection="", toConnection = "";

		String defStr = xfrStr[0].trim().toLowerCase();
		String wdg1Str = xfrStr[1].trim().toLowerCase();
		String wdg2Str = xfrStr[2].trim().toLowerCase();

		String[] defStrAry  = defStr.split("\\s+");
		String[] wdg1StrAry = wdg1Str.split("\\s+");
		String[] wdg2StrAry = wdg2Str.split("\\s+");

		for (String element : defStrAry) {
			if(element.contains("transformer.")){
				xfrId = element.substring(12);
			}
			else if(element.contains("phases=")){
				phaseNum = Integer.valueOf(element.substring(7));
			}
			else if(element.contains("windings=")){
				windingNum = Integer.valueOf(element.substring(9));
			}
			else if(element.contains("xhl=")){
				xhl= Double.valueOf(element.substring(4));
			}

		}

		for (String element : wdg1StrAry) {
			if(element.contains("bus=")){
				fromBusId = element.substring(4);
			}
			else if(element.contains("conn=")){
				fromConnection = element.substring(5);
			}
			else if(element.contains("kv=")){
				nominalKV1 = Double.valueOf(element.substring(3));
			}
			else if(element.contains("kva=")){
				kva1 = Double.valueOf(element.substring(5));
			}
			else if(element.contains("%r=")){
				losspercent1= Double.valueOf(element.substring(3));
			}
		}

		for (String element : wdg2StrAry) {

			if(element.contains("bus=")){
				toBusId = element.substring(4);
			}
			else if(element.contains("conn=")){
				toConnection = element.substring(5);
			}
			else if(element.contains("kv=")){
				nominalKV2 = Double.valueOf(element.substring(3));
			}
			else if(element.contains("kva=")){
				kva1 = Double.valueOf(element.substring(4));
			}
			else if(element.contains("%r=")){
				losspercent2= Double.valueOf(element.substring(3));
			}
		}

		if(this.dataParser.getDistNetwork().getBus(fromBusId)==null) {
			ThreePhaseObjectFactory.create3PDStabBus(fromBusId, this.dataParser.getDistNetwork());
		}

		if(this.dataParser.getDistNetwork().getBus(toBusId)==null) {
			ThreePhaseObjectFactory.create3PDStabBus(toBusId, this.dataParser.getDistNetwork());
		}


		// create a transformer object
		DStab3PBranch xfrBranch = ThreePhaseObjectFactory.create3PBranch(fromBusId, toBusId, "0", this.dataParser.getDistNetwork());
		xfrBranch.setName(xfrId);
		xfrBranch.setBranchCode(AclfBranchCode.XFORMER);

		// use the turn ratios to tentatively store the nominalKVs, will convert both to true ratios later.
		xfrBranch.setFromTurnRatio(nominalKV1*1000.0);
		xfrBranch.setToTurnRatio(nominalKV2*1000.0);

//		xfrBranch.getFromAclfBus().setBaseVoltage(normKV1, UnitType.kV);
//		xfrBranch.getToAclfBus().setBaseVoltage(normKV2, UnitType.kV);

		//TODO calculate r based on loss percent
		xfrBranch.setZ( new Complex( 0.0, xhl ));

		xfrBranch.setXfrRatedKVA(kva1);

		//TODO to add the phase info to the Branch3Phase


	    AcscXformerAdapter xfr0 = acscXfrAptr.apply(xfrBranch);

	    if(fromConnection.equalsIgnoreCase("Delta")){
	    	if(toConnection.equalsIgnoreCase("Delta")) {
				xfr0.setFromGrounding(BusGroundCode.UNGROUNDED, XFormerConnectCode.DELTA, new Complex(0.0,0.0), UnitType.PU);
			} else {
				xfr0.setFromGrounding(BusGroundCode.UNGROUNDED, XFormerConnectCode.DELTA11, new Complex(0.0,0.0), UnitType.PU);
			}
	    }
	    else if(fromConnection.equalsIgnoreCase("Wye")){
	    	xfr0.setFromGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);
	    }
	    else{
	    	throw new Error("Transformer connection type at winding 1 is not supported yet #"+fromConnection);
	    }

	    if(toConnection.equalsIgnoreCase("Delta")){
	    	if(fromConnection.equalsIgnoreCase("Wye")) {
				xfr0.setToGrounding(BusGroundCode.UNGROUNDED, XFormerConnectCode.DELTA11, new Complex(0.0,0.0), UnitType.PU);
			} else {
				xfr0.setToGrounding(BusGroundCode.UNGROUNDED, XFormerConnectCode.DELTA, new Complex(0.0,0.0), UnitType.PU);
			}
	    }
	    else if(toConnection.equalsIgnoreCase("Wye")){
	    	xfr0.setToGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);
	    }
	    else{
	    	throw new Error("Transformer connection type at winding 2 is not supported yet #"+toConnection);
	    }



		return no_error;
	}

public boolean parseTransformerDataOneLine(String xfrStr) throws InterpssException{


		/*
			Another type of input format:
			* new transformer.reg1a phases=3 windings=2 buses=[150 150r] conns=[wye wye] kvs=[4.16 4.16] kvas=[5000 5000] XHL=.001 %LoadLoss=0.00001 ppm=0.0

		 */

		boolean no_error = true;


		int phaseNum = 3;
		int windingNum = 2;
		double xhl = 0.0;
		double losspercent1 = 0,losspercent2;
		double kva1 = 0, kva2 = 0;
		double normKV1 = 0.0, normKV2 = 0.0;
		String xfrId = "";
		String fromBusId = "", toBusId = "";
		String fromConnection="", toConnection = "";
		String referenceXfrName = "";
		String phase1 = "", phase2 = "",phase3 = "";

		String[] xfrStrAry  = xfrStr.trim().toLowerCase().split("\\s+(?![^\\[]*\\])");


		for (String element : xfrStrAry) {
			if(element.contains("transformer.")){
				xfrId = element.substring(12);
			}
			else if(element.contains("phases=")){
				phaseNum = Integer.valueOf(element.substring(7));
			}
			else if(element.contains("windings=")){
				windingNum = Integer.valueOf(element.substring(9));
			}
			else if(element.contains("xhl=")){
				xhl= Double.valueOf(element.substring(4));
			}

			else if(element.contains("buses=")){
				int startIdx =  element.indexOf("[")+1;
				int endIdx =  element.indexOf("]");
				String[] busIds = element.substring(startIdx,endIdx).trim().split("\\s+");
				fromBusId = busIds[0];
				if(fromBusId.contains(".")){

//					int dotIdx = fromBusId.indexOf(".");
//					fromBusId = fromBusId.substring(0, dotIdx);

					String[] tempAry = fromBusId.split("\\.");
					fromBusId = tempAry[0];

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
				toBusId = busIds[1];

				if(toBusId.contains(".")){
//					int dotIdx = toBusId.indexOf(".");
//					toBusId = toBusId.substring(0, dotIdx);
					String[] tempAry = toBusId.split("\\.");
					toBusId = tempAry[0];

				}

			}
			else if(element.contains("conns=")){
				int startIdx =  element.indexOf("[")+1;
				int endIdx =  element.indexOf("]");
				String[] connTypes = element.substring(startIdx,endIdx).trim().split("\\s+");
				fromConnection = connTypes[0];
				toConnection = connTypes[1];
			}
			else if(element.contains("kvs=")){
				int startIdx =  element.indexOf("[")+1;
				int endIdx =  element.indexOf("]");
				String[] kvs = element.substring(startIdx,endIdx).trim().split("\\s+");
				normKV1 = Double.valueOf(kvs[0]);
				normKV2 = Double.valueOf(kvs[1]);
			}
			else if(element.contains("kvas=")){
				int startIdx =  element.indexOf("[")+1;
				int endIdx =  element.indexOf("]");
				String[] kvas = element.substring(startIdx,endIdx).trim().split("\\s+");
				kva1 = Double.valueOf(kvas[0]);
				kva2 = Double.valueOf(kvas[1]);

			}
			else if(element.contains("%r=")){
				losspercent1= Double.valueOf(element.substring(3));
			}
			else if (element.contains("%loadloss=")){
				losspercent1= Double.valueOf(element.substring(10));
			}
			else if (element.contains("like=")){
				referenceXfrName= element.substring(5);
			}


		}

		fromBusId =this.dataParser.getBusIdPrefix()+fromBusId;
		toBusId =this.dataParser.getBusIdPrefix()+toBusId;

		if(this.dataParser.getDistNetwork().getBus(fromBusId)==null) {
			ThreePhaseObjectFactory.create3PDStabBus(fromBusId, this.dataParser.getDistNetwork());
		}

		if(this.dataParser.getDistNetwork().getBus(toBusId)==null) {
			ThreePhaseObjectFactory.create3PDStabBus(toBusId, this.dataParser.getDistNetwork());
		}


		// create a transformer object
		DStab3PBranch xfrBranch = ThreePhaseObjectFactory.create3PBranch(fromBusId, toBusId, "0", this.dataParser.getDistNetwork());

		// since InterPSS uses fromBus->toBus(cirId) as the unique branchId, here the original Id is set as the name.
		xfrBranch.setName(this.dataParser.getBusIdPrefix()+xfrId);
		xfrBranch.setBranchCode(AclfBranchCode.XFORMER);

		DStab3PBranch likeBranch = null;

		if(!referenceXfrName.equals("")){
			likeBranch= this.dataParser.getBranchByName(referenceXfrName);
		}

		if(likeBranch!=null){
			if(xhl==0.0){
				xhl = likeBranch.getZ().getImaginary();

			}
			if(normKV1==0.0){
				normKV1 = likeBranch.getFromBus().getBaseVoltage()/1000.0;
			}
			if(normKV2==0.0){
				normKV2 = likeBranch.getToBus().getBaseVoltage()/1000.0;
			}
			if(kva1==0.0){
				kva1 = likeBranch.getXfrRatedKVA();
			}
			if(kva2==0.0){
				kva2 = likeBranch.getXfrRatedKVA();
			}
			AcscXformerAdapter likexfr = acscXfrAptr.apply(likeBranch);

			if(fromConnection.equals("")){
				if(likexfr.getFromGrounding().getXfrConnectCode() == XFormerConnectCode.DELTA ||
						likexfr.getFromGrounding().getXfrConnectCode() == XFormerConnectCode.DELTA11){
					fromConnection ="delta";
				}
				else{
					fromConnection ="wye";
				}
			}
            if(toConnection.equals("")){
            	if(likexfr.getToGrounding().getXfrConnectCode() == XFormerConnectCode.DELTA ||
            			likexfr.getToGrounding().getXfrConnectCode() == XFormerConnectCode.DELTA11){
					toConnection ="delta";
				}
				else{
					toConnection ="wye";
				}
			}
		}

		xfrBranch.setFromTurnRatio(normKV1*1000.0);
		xfrBranch.setToTurnRatio(normKV2*1000.0);

		//phase info

		if(phaseNum ==3){
			xfrBranch.setPhaseCode(PhaseCode.ABC);
		}
		else if(phaseNum ==1){
			if(phase1.equals("1")) {
				xfrBranch.setPhaseCode(PhaseCode.A);
			} else if(phase1.equals("2")) {
				xfrBranch.setPhaseCode(PhaseCode.B);
			} else if(phase1.equals("3")) {
				xfrBranch.setPhaseCode(PhaseCode.C);
			} else{
				throw new Error("Transformer connection phase currently must be either 1, 2 or 3.  xfr #" +xfrId);
			}

		}

//		xfrBranch.getFromBus().setBaseVoltage(normKV1, UnitType.kV);
//		xfrBranch.getToBus().setBaseVoltage(normKV2, UnitType.kV);
//
		//TODO calculate r based on loss percent
		xfrBranch.setZ( new Complex( 0.0, xhl ));

		xfrBranch.setXfrRatedKVA(kva1);


	    AcscXformerAdapter xfr0 = acscXfrAptr.apply(xfrBranch);

	    if(fromConnection.equalsIgnoreCase("Delta")){
	    	if(toConnection.equalsIgnoreCase("Delta")) {
				xfr0.setFromGrounding(BusGroundCode.UNGROUNDED, XFormerConnectCode.DELTA, new Complex(0.0,0.0), UnitType.PU);
			} else {
				xfr0.setFromGrounding(BusGroundCode.UNGROUNDED, XFormerConnectCode.DELTA11, new Complex(0.0,0.0), UnitType.PU);
			}
	    }
	    else if(fromConnection.equalsIgnoreCase("Wye")){
	    	xfr0.setFromGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);
	    }
	    else{
	    	throw new Error("Transformer connection type at winding 1 is not supported yet #"+fromConnection);
	    }

	    if(toConnection.equalsIgnoreCase("Delta")){
	    	if(fromConnection.equalsIgnoreCase("Wye")) {
				xfr0.setToGrounding(BusGroundCode.UNGROUNDED, XFormerConnectCode.DELTA11, new Complex(0.0,0.0), UnitType.PU);
			} else {
				xfr0.setToGrounding(BusGroundCode.UNGROUNDED, XFormerConnectCode.DELTA, new Complex(0.0,0.0), UnitType.PU);
			}
	    }
	    else if(toConnection.equalsIgnoreCase("Wye")){
	    	xfr0.setToGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);
	    }
	    else{
	    	throw new Error("Transformer connection type at winding 2 is not supported yet #"+toConnection);
	    }




		return no_error;
	}

	public boolean parseXfrControlData(String regulateStr){

		/*
		 * new transformer.reg1a phases=3 windings=2 buses=[150 150r] conns=[wye wye] kvs=[4.16 4.16] kvas=[5000 5000] XHL=.001 %LoadLoss=0.00001 ppm=0.0
           new regcontrol.creg1a transformer=reg1a winding=2 vreg=120 band=2 ptratio=20 ctprim=700 R=3 X=7.5

		 */

		boolean no_error = true;

		return no_error;
	}



}
