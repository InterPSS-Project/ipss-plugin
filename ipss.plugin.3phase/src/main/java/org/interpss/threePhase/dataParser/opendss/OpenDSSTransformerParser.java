package org.interpss.threePhase.dataParser.opendss;

import static com.interpss.core.funcImpl.AcscFunction.acscXfrAptr;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
		double losspercent1 = 0,losspercent2 = 0;
		double kva1 = 0, kva2 = 0;
		double nominalKV1 = 0, nominalKV2 = 0;
		String xfrId = "";
		String fromBusId = "", toBusId = "";
		String fromConnection="", toConnection = "";
		boolean fromWyeGrounded = true, toWyeGrounded = true;

		String defStr = normalizeInlineRpnDivisions(xfrStr[0].trim().toLowerCase());
		String wdg1Str = normalizeInlineRpnDivisions(xfrStr[1].trim().toLowerCase());
		String wdg2Str = normalizeInlineRpnDivisions(xfrStr[2].trim().toLowerCase());

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
				TerminalBus terminal = terminalBus(element.substring(4));
				fromBusId = terminal.busId;
				fromWyeGrounded = terminal.wyeGrounded;
			}
			else if(element.contains("conn=")){
				fromConnection = element.substring(5);
			}
			else if(element.contains("kv=")){
				nominalKV1 = Double.valueOf(element.substring(3));
			}
			else if(element.contains("kva=")){
				kva1 = Double.valueOf(element.substring(4));
			}
			else if(element.contains("%r=")){
				losspercent1= Double.valueOf(element.substring(3));
			}
		}

		for (String element : wdg2StrAry) {

			if(element.contains("bus=")){
				TerminalBus terminal = terminalBus(element.substring(4));
				toBusId = terminal.busId;
				toWyeGrounded = terminal.wyeGrounded;
			}
			else if(element.contains("conn=")){
				toConnection = element.substring(5);
			}
			else if(element.contains("kv=")){
				nominalKV2 = Double.valueOf(element.substring(3));
			}
			else if(element.contains("kva=")){
				kva2 = Double.valueOf(element.substring(4));
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
		DStab3PBranch xfrBranch = ThreePhaseObjectFactory.create3PBranch(fromBusId, toBusId, xfrId,
				this.dataParser.getDistNetwork());
		xfrBranch.setName(xfrId);
		xfrBranch.setBranchCode(AclfBranchCode.XFORMER);

		// use the turn ratios to tentatively store the nominalKVs, will convert both to true ratios later.
		xfrBranch.setFromTurnRatio(nominalKV1*1000.0);
		xfrBranch.setToTurnRatio(nominalKV2*1000.0);

//		xfrBranch.getFromAclfBus().setBaseVoltage(normKV1, UnitType.kV);
//		xfrBranch.getToAclfBus().setBaseVoltage(normKV2, UnitType.kV);

		xfrBranch.setZ(transformerSeriesImpedanceOhm(nominalKV1, nominalKV2, kva1, kva2,
				losspercent1 + losspercent2, xhl));

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
	    	xfr0.setFromGrounding(fromWyeGrounded ? BusGroundCode.SOLID_GROUNDED : BusGroundCode.UNGROUNDED,
					XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);
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
	    	xfr0.setToGrounding(toWyeGrounded ? BusGroundCode.SOLID_GROUNDED : BusGroundCode.UNGROUNDED,
					XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);
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
		boolean fromWyeGrounded = true, toWyeGrounded = true;

		String[] xfrStrAry  = normalizeInlineRpnDivisions(xfrStr.trim().toLowerCase()).split("\\s+(?![^\\[]*\\])");


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
				TerminalBus fromTerminal = terminalBus(busIds[0]);
				fromBusId = fromTerminal.busId;
				fromWyeGrounded = fromTerminal.wyeGrounded;
				if(fromTerminal.nodes.length>0){
					phase1 = fromTerminal.nodes[0];
				}
				if(fromTerminal.nodes.length>1){
					phase2 = fromTerminal.nodes[1];
				}
				if(fromTerminal.nodes.length>2){
					phase3 = fromTerminal.nodes[2];
				}
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
				TerminalBus toTerminal = terminalBus(busIds[1]);
				toBusId = toTerminal.busId;
				toWyeGrounded = toTerminal.wyeGrounded;

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
		DStab3PBranch xfrBranch = ThreePhaseObjectFactory.create3PBranch(fromBusId, toBusId, xfrId,
				this.dataParser.getDistNetwork());

		// since InterPSS uses fromBus->toBus(cirId) as the unique branchId, here the original Id is set as the name.
		xfrBranch.setName(this.dataParser.getBusIdPrefix()+xfrId);
		xfrBranch.setBranchCode(AclfBranchCode.XFORMER);

		DStab3PBranch likeBranch = null;

		if(!referenceXfrName.equals("")){
			likeBranch= this.dataParser.getBranchByName(referenceXfrName);
		}

		if(likeBranch!=null){
			if(xhl==0.0){
				xhl = likeBranch.getAdjustedZ().getImaginary();

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
		if(fromConnection.equals("")){
			fromConnection = "wye";
		}
		if(toConnection.equals("")){
			toConnection = "wye";
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
		xfrBranch.setZ(transformerSeriesImpedanceOhm(normKV1, normKV2, kva1, kva2, losspercent1, xhl));

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
	    	xfr0.setFromGrounding(fromWyeGrounded ? BusGroundCode.SOLID_GROUNDED : BusGroundCode.UNGROUNDED,
					XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);
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
	    	xfr0.setToGrounding(toWyeGrounded ? BusGroundCode.SOLID_GROUNDED : BusGroundCode.UNGROUNDED,
					XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);
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

	private static String normalizeInlineRpnDivisions(String value) {
		Pattern pattern = Pattern.compile("\\(([-+0-9.Ee]+)\\s+([-+0-9.Ee]+)\\s+/\\)");
		Matcher matcher = pattern.matcher(value);
		StringBuffer buffer = new StringBuffer();
		while (matcher.find()) {
			double numerator = Double.valueOf(matcher.group(1)).doubleValue();
			double denominator = Double.valueOf(matcher.group(2)).doubleValue();
			matcher.appendReplacement(buffer, Double.toString(numerator / denominator));
		}
		matcher.appendTail(buffer);
		return buffer.toString();
	}

	private static Complex transformerSeriesImpedanceOhm(double kv1, double kv2,
			double kva1, double kva2, double rPercent, double xPercent) {
		double highKv = Math.max(kv1, kv2);
		double ratedKva = kva1 > 0.0 ? kva1 : kva2;
		if (highKv <= 0.0 || ratedKva <= 0.0) {
			return new Complex(0.0, xPercent);
		}
		double zBaseOhm = highKv * highKv / (ratedKva / 1000.0);
		return new Complex(rPercent / 100.0, xPercent / 100.0).multiply(zBaseOhm);
	}



	private static TerminalBus terminalBus(String openDssBusId) {
		String[] parts = openDssBusId.split("\\.");
		if (parts.length == 1) {
			return new TerminalBus(openDssBusId, true, new String[0]);
		}
		String[] nodes = new String[parts.length - 1];
		boolean grounded = true;
		for (int i = 1; i < parts.length; i++) {
			nodes[i - 1] = parts[i];
			if ("4".equals(parts[i])) {
				grounded = false;
			}
			else if ("0".equals(parts[i])) {
				grounded = true;
			}
		}
		return new TerminalBus(parts[0], grounded, nodes);
	}

	private static class TerminalBus {
		private final String busId;
		private final boolean wyeGrounded;
		private final String[] nodes;

		private TerminalBus(String busId, boolean wyeGrounded, String[] nodes) {
			this.busId = busId;
			this.wyeGrounded = wyeGrounded;
			this.nodes = nodes;
		}
	}
}
