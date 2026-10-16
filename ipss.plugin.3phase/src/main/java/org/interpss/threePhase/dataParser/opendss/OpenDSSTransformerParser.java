package org.interpss.threePhase.dataParser.opendss;

import static com.interpss.core.funcImpl.AcscFunction.acscXfrAptr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.interpss.threePhase.basic.ThreeWindingTransformerAdmittance;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.acsc.BusGroundCode;
import com.interpss.core.acsc.AcscBranch;
import com.interpss.core.acsc.PhaseCode;
import com.interpss.core.acsc.XFormerConnectCode;
import com.interpss.core.acsc.adpter.AcscXformerAdapter;
import com.interpss.core.threephase.IBranch3Phase;

public class OpenDSSTransformerParser {

    private OpenDSSDataParser dataParser = null;
	private final Map<String, double[]> transformerTaps = new HashMap<>();
	private final Map<String, XfmrCodeData> xfmrCodes = new HashMap<>();

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
		String phase1 = "";

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
				if(terminal.nodes.length>0) {
					phase1 = terminal.nodes[0];
				}
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

		// create a transformer object
		AcscBranch xfrBranch = createTransformerBranch(fromBusId, toBusId, xfrId);
		IBranch3Phase xfr3P = (IBranch3Phase) xfrBranch;
		xfrBranch.setName(xfrId);
		xfrBranch.setBranchCode(AclfBranchCode.XFORMER);

		// use the turn ratios to tentatively store the nominalKVs, will convert both to true ratios later.
		xfrBranch.setFromTurnRatio(nominalKV1*1000.0);
		xfrBranch.setToTurnRatio(nominalKV2*1000.0);
		if(phaseNum ==3){
			xfr3P.setPhaseCode(PhaseCode.ABC);
		}
		else if(phaseNum ==1){
			if(phase1.equals("1")) {
				xfr3P.setPhaseCode(PhaseCode.A);
			} else if(phase1.equals("2")) {
				xfr3P.setPhaseCode(PhaseCode.B);
			} else if(phase1.equals("3")) {
				xfr3P.setPhaseCode(PhaseCode.C);
			} else{
				throw new Error("Transformer connection phase currently must be either 1, 2 or 3.  xfr #" +xfrId);
			}
		}

//		xfrBranch.getFromAclfBus().setBaseVoltage(normKV1, UnitType.kV);
//		xfrBranch.getToAclfBus().setBaseVoltage(normKV2, UnitType.kV);

		setTransformerSeriesImpedance(xfrBranch, xfr3P, transformerSeriesImpedanceOhm(nominalKV1,
				nominalKV2, kva1, kva2, losspercent1 + losspercent2, xhl));

		xfr3P.setXfrRatedKVA(kva1);

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

	public boolean parseTransformerTapData(String transformerTapStr) {
		String normalized = transformerTapStr.trim().toLowerCase();
		Matcher matcher = Pattern.compile("transformer\\.([^\\.\\s]+)\\.taps=\\[([^\\]]+)\\]").matcher(normalized);
		if(!matcher.find()) {
			return true;
		}
		String transformerName = matcher.group(1);
		String[] tapTokens = matcher.group(2).trim().split("\\s+");
		if(tapTokens.length < 2) {
			return true;
		}
		double fromTap = Double.valueOf(tapTokens[0]).doubleValue();
		double toTap = Double.valueOf(tapTokens[1]).doubleValue();
		this.transformerTaps.put(transformerName, new double[] {fromTap, toTap});
		this.transformerTaps.put(this.dataParser.getBusIdPrefix() + transformerName, new double[] {fromTap, toTap});
		return true;
	}

	public boolean parseXfmrCodeData(String xfrCodeStr) {
		XfmrCodeData code = new XfmrCodeData();
		String codeName = "";
		for(String element : splitOutsideLists(xfrCodeStr.trim().toLowerCase())) {
			if(element.contains("xfmrcode.")) {
				codeName = element.substring(element.indexOf("xfmrcode.") + 9);
			}
			else if(element.contains("phases=")) {
				code.phaseNum = Integer.valueOf(element.substring(7));
			}
			else if(element.contains("windings=")) {
				code.windingNum = Integer.valueOf(element.substring(9));
			}
			else if(element.contains("kvs=")) {
				code.kvs = doubleValues(listValues(element));
			}
			else if(element.startsWith("conns=")) {
				code.conns = listValues(element);
			}
			else if(element.contains("kvas=")) {
				code.kvas = doubleValues(listValues(element));
			}
			else if(element.contains("%rs=")) {
				code.rPercents = doubleValues(listValues(element));
			}
			else if(element.contains("%imag=")) {
				code.imagPercent = Double.valueOf(element.substring(6));
			}
			else if(element.contains("%noloadloss=")) {
				code.noLoadLossPercent = Double.valueOf(element.substring(12));
			}
			else if(element.contains("xhl=")) {
				code.xhl = Double.valueOf(element.substring(4));
			}
			else if(element.contains("xht=")) {
				code.xht = Double.valueOf(element.substring(4));
			}
			else if(element.contains("xlt=")) {
				code.xlt = Double.valueOf(element.substring(4));
			}
		}
		if(!codeName.equals("")) {
			this.xfmrCodes.put(codeName, code);
		}
		return true;
	}

	public void mergeParallelSinglePhaseRegulatorBranches() throws InterpssException {
		Map<String, List<AclfBranch>> branchGroups = new HashMap<>();
		for(AclfBranch branch : new ArrayList<AclfBranch>(currentBranchList())) {
			if(branch.isActive() && branch.isXfr() && branch instanceof IBranch3Phase) {
				IBranch3Phase branch3P = (IBranch3Phase) branch;
				if(branch3P.getPhaseCode() != PhaseCode.ABC) {
					String key = branch.getFromBus().getId() + "->" + branch.getToBus().getId();
					branchGroups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(branch);
				}
			}
		}
		for(List<AclfBranch> group : branchGroups.values()) {
			mergeCompleteSinglePhaseBank(group);
		}
	}

	private void mergeCompleteSinglePhaseBank(List<AclfBranch> group) throws InterpssException {
		if(group.size() != 3) {
			return;
		}
		AclfBranch phaseA = branchForPhase(group, PhaseCode.A);
		AclfBranch phaseB = branchForPhase(group, PhaseCode.B);
		AclfBranch phaseC = branchForPhase(group, PhaseCode.C);
		if(phaseA == null || phaseB == null || phaseC == null) {
			return;
		}
		if(!hasExplicitTap(phaseA) || !hasExplicitTap(phaseB) || !hasExplicitTap(phaseC)) {
			return;
		}
		if(!sameGroundedWyeConnection(phaseA, phaseB) || !sameGroundedWyeConnection(phaseA, phaseC)) {
			return;
		}

		String fromBusId = phaseA.getFromBus().getId();
		String toBusId = phaseA.getToBus().getId();
		AcscBranch merged = createTransformerBranch(fromBusId, toBusId, phaseA.getName() + "_abc");
		IBranch3Phase merged3P = (IBranch3Phase) merged;
		merged.setName(phaseA.getName() + "_abc");
		merged.setBranchCode(AclfBranchCode.XFORMER);
		merged3P.setPhaseCode(PhaseCode.ABC);
		merged.setFromTurnRatio(phaseA.getFromTurnRatio());
		merged.setToTurnRatio(phaseA.getToTurnRatio());
		merged3P.setFromTurnRatioABC(tappedTurnRatio(phaseA, true), tappedTurnRatio(phaseB, true), tappedTurnRatio(phaseC, true));
		merged3P.setToTurnRatioABC(tappedTurnRatio(phaseA, false), tappedTurnRatio(phaseB, false), tappedTurnRatio(phaseC, false));
		merged3P.setZabc(diagonalZabc(phaseA, phaseB, phaseC));
		merged3P.setXfrRatedKVA(branch3P(phaseA).getXfrRatedKVA()
				+ branch3P(phaseB).getXfrRatedKVA()
				+ branch3P(phaseC).getXfrRatedKVA());

		AcscXformerAdapter sourceGrounding = acscXfrAptr.apply((AcscBranch) phaseA);
		AcscXformerAdapter mergedGrounding = acscXfrAptr.apply(merged);
		mergedGrounding.setFromGrounding(sourceGrounding.getFromGrounding().getGroundCode(),
				sourceGrounding.getFromGrounding().getXfrConnectCode(), new Complex(0.0,0.0), UnitType.PU);
		mergedGrounding.setToGrounding(sourceGrounding.getToGrounding().getGroundCode(),
				sourceGrounding.getToGrounding().getXfrConnectCode(), new Complex(0.0,0.0), UnitType.PU);

		for(AclfBranch branch : group) {
			branch.setStatus(false);
		}
	}

	private AclfBranch branchForPhase(List<AclfBranch> branches, PhaseCode phaseCode) {
		for(AclfBranch branch : branches) {
			if(branch3P(branch).getPhaseCode() == phaseCode) {
				return branch;
			}
		}
		return null;
	}

	private boolean sameGroundedWyeConnection(AclfBranch reference, AclfBranch branch) {
		AcscXformerAdapter ref = acscXfrAptr.apply((AcscBranch) reference);
		AcscXformerAdapter other = acscXfrAptr.apply((AcscBranch) branch);
		return ref.getFromGrounding().getXfrConnectCode() == XFormerConnectCode.WYE
				&& ref.getToGrounding().getXfrConnectCode() == XFormerConnectCode.WYE
				&& other.getFromGrounding().getXfrConnectCode() == XFormerConnectCode.WYE
				&& other.getToGrounding().getXfrConnectCode() == XFormerConnectCode.WYE
				&& ref.getFromGrounding().getGroundCode() == other.getFromGrounding().getGroundCode()
				&& ref.getToGrounding().getGroundCode() == other.getToGrounding().getGroundCode();
	}

	private Complex3x3 diagonalZabc(AclfBranch phaseA, AclfBranch phaseB, AclfBranch phaseC) {
		Complex3x3 zabc = new Complex3x3();
		zabc.aa = phaseA.getAdjustedZ();
		zabc.bb = phaseB.getAdjustedZ();
		zabc.cc = phaseC.getAdjustedZ();
		return zabc;
	}

	private static void setTransformerSeriesImpedance(AcscBranch branch, IBranch3Phase branch3P,
			Complex seriesZ) {
		branch.setZ(seriesZ);
		branch3P.setZabc(diagonalZabc(branch3P.getPhaseCode(), seriesZ));
	}

	private static void addSecondaryNoLoadAdmittance(IBranch3Phase branch3P, double kv, double kva,
			double imagPercent, double noLoadLossPercent) {
		Complex admittance = noLoadAdmittance(kv, kva, imagPercent, noLoadLossPercent);
		if(admittance.equals(Complex.ZERO)) {
			return;
		}
		branch3P.setFromShuntYabc(new Complex3x3());
		branch3P.setToShuntYabc(diagonalZabc(branch3P.getPhaseCode(), admittance));
	}

	private static Complex3x3 diagonalZabc(PhaseCode phaseCode, Complex impedance) {
		Complex zero = new Complex(0.0);
		Complex3x3 zabc = new Complex3x3();
		zabc.aa = zero;
		zabc.ab = zero;
		zabc.ac = zero;
		zabc.ba = zero;
		zabc.bb = zero;
		zabc.bc = zero;
		zabc.ca = zero;
		zabc.cb = zero;
		zabc.cc = zero;
		if(phaseCode == PhaseCode.A || phaseCode == PhaseCode.AB
				|| phaseCode == PhaseCode.AC || phaseCode == PhaseCode.ABC) {
			zabc.aa = impedance;
		}
		if(phaseCode == PhaseCode.B || phaseCode == PhaseCode.AB
				|| phaseCode == PhaseCode.BC || phaseCode == PhaseCode.ABC) {
			zabc.bb = impedance;
		}
		if(phaseCode == PhaseCode.C || phaseCode == PhaseCode.AC
				|| phaseCode == PhaseCode.BC || phaseCode == PhaseCode.ABC) {
			zabc.cc = impedance;
		}
		return zabc;
	}

	private double tappedTurnRatio(AclfBranch branch, boolean fromSide) {
		double[] taps = this.transformerTaps.get(branch.getName());
		double tap = taps == null ? 1.0 : taps[fromSide ? 0 : 1];
		return (fromSide ? branch.getFromTurnRatio() : branch.getToTurnRatio()) * tap;
	}

	private boolean hasExplicitTap(AclfBranch branch) {
		return this.transformerTaps.containsKey(branch.getName());
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
		double xht = 0.0;
		double xlt = 0.0;
		double losspercent1 = 0,losspercent2;
		double kva1 = 0, kva2 = 0;
		double normKV1 = 0.0, normKV2 = 0.0;
		double[] normKVs = new double[0];
		double[] kvaRatings = new double[0];
		double[] rPercents = new double[0];
		double imagPercent = 0.0;
		double noLoadLossPercent = 0.0;
		String[] busTerminals = new String[0];
		String[] windingConnections = new String[0];
		double[] windingTaps = {1, 1, 1};
		boolean lead = false;
		String xfrId = "";
		String fromBusId = "", toBusId = "";
		String fromConnection="", toConnection = "";
		String referenceXfrName = "";
		String xfrCodeName = "";
		String phase1 = "", phase2 = "",phase3 = "";
		boolean fromWyeGrounded = true, toWyeGrounded = true;
		boolean phaseSpecified = false;
		boolean xhlSpecified = false;
		boolean lossSpecified = false;

		String[] xfrStrAry  = splitOutsideLists(normalizePropertyEquals(
				normalizeInlineRpnDivisions(xfrStr.trim().toLowerCase())));
		int windingContext = 0;
		boolean hasWindingSpecificResistance = false;


		for (String element : xfrStrAry) {
			if(element.contains("transformer.")){
				xfrId = element.substring(12);
			}
			else if(element.contains("phases=")){
				phaseNum = Integer.valueOf(element.substring(7));
				phaseSpecified = true;
			}
			else if(element.contains("windings=")){
				windingNum = Integer.valueOf(element.substring(9));
			}
			else if(element.contains("wdg=")){
				windingContext = Integer.valueOf(element.substring(4));
			}
			else if(element.contains("xhl=")){
				xhl= Double.valueOf(element.substring(4));
				xhlSpecified = true;
			}
			else if(element.contains("xht=")){
				xht= Double.valueOf(element.substring(4));
			}
			else if(element.contains("xlt=")){
				xlt= Double.valueOf(element.substring(4));
			}

			else if(element.startsWith("buses=")){
				String[] busIds = listValues(element);
				busTerminals = busIds;
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
			else if(element.startsWith("bus=")){
				TerminalBus terminal = terminalBus(element.substring(4));
				if(windingContext == 1) {
					fromBusId = terminal.busId;
					fromWyeGrounded = terminal.wyeGrounded;
					if(terminal.nodes.length>0){
						phase1 = terminal.nodes[0];
					}
					if(terminal.nodes.length>1){
						phase2 = terminal.nodes[1];
					}
					if(terminal.nodes.length>2){
						phase3 = terminal.nodes[2];
					}
				}
				else if(windingContext == 2) {
					toBusId = terminal.busId;
					toWyeGrounded = terminal.wyeGrounded;
				}
			}
			else if(element.startsWith("conns=")){
				String[] connTypes = listValues(element);
				windingConnections = connTypes;
				fromConnection = connTypes[0];
				toConnection = connTypes[1];
			}
			else if(element.startsWith("conn=")){
				if(windingContext == 1) {
					fromConnection = element.substring(5);
				}
				else if(windingContext == 2) {
					toConnection = element.substring(5);
				}
			}
			else if(element.startsWith("taps=")){
				windingTaps = doubleValues(listValues(element));
			}
			else if(element.startsWith("leadlag=")){
				lead = element.substring(8).equals("lead");
			}
			else if(element.startsWith("kvs=")){
				String[] kvs = listValues(element);
				normKVs = doubleValues(kvs);
				normKV1 = Double.valueOf(kvs[0]);
				normKV2 = Double.valueOf(kvs[1]);
			}
			else if(element.startsWith("kv=")){
				if(windingContext == 1) {
					normKV1 = Double.valueOf(element.substring(3));
				}
				else if(windingContext == 2) {
					normKV2 = Double.valueOf(element.substring(3));
				}
			}
			else if(element.startsWith("kvas=")){
				String[] kvas = listValues(element);
				kvaRatings = doubleValues(kvas);
				kva1 = Double.valueOf(kvas[0]);
				kva2 = Double.valueOf(kvas[1]);

			}
			else if(element.startsWith("kva=")){
				if(windingContext == 1) {
					kva1 = Double.valueOf(element.substring(4));
				}
				else if(windingContext == 2) {
					kva2 = Double.valueOf(element.substring(4));
				}
			}
			else if(element.contains("%rs=")){
				rPercents = doubleValues(listValues(element));
				if(rPercents.length > 1) {
					losspercent1 = rPercents[0] + rPercents[1];
				}
				lossSpecified = true;
			}
			else if(element.contains("%imag=")){
				imagPercent = Double.valueOf(element.substring(6));
			}
			else if(element.contains("%noloadloss=")){
				noLoadLossPercent = Double.valueOf(element.substring(12));
			}
			else if(element.contains("%r=")){
				if(windingContext > 0) {
					if(!hasWindingSpecificResistance) {
						losspercent1 = 0.0;
					}
					losspercent1 = losspercent1 + Double.valueOf(element.substring(3));
					hasWindingSpecificResistance = true;
				} else {
					losspercent1= Double.valueOf(element.substring(3));
				}
				lossSpecified = true;
			}
			else if (element.contains("%loadloss=")){
				losspercent1= Double.valueOf(element.substring(10));
				lossSpecified = true;
			}
			else if (element.contains("like=")){
				referenceXfrName= element.substring(5);
			}
			else if (element.contains("xfmrcode=")){
				xfrCodeName= element.substring(9);
			}


		}

		XfmrCodeData code = this.xfmrCodes.get(xfrCodeName);
		if(code != null) {
			if(!phaseSpecified) {
				phaseNum = code.phaseNum;
			}
			if(windingNum == 2) {
				windingNum = code.windingNum;
			}
			if(normKV1 == 0.0 && code.kvs.length > 0) {
				normKV1 = code.kvs[0];
			}
			if(normKV2 == 0.0 && code.kvs.length > 1) {
				normKV2 = code.kvs[1];
			}
			if(kva1 == 0.0 && code.kvas.length > 0) {
				kva1 = code.kvas[0];
			}
			if(kva2 == 0.0 && code.kvas.length > 1) {
				kva2 = code.kvas[1];
			}
			if(!lossSpecified && code.rPercents.length > 1) {
				losspercent1 = code.rPercents[0] + code.rPercents[1];
				lossSpecified = true;
			}
			if(rPercents.length == 0) {
				rPercents = code.rPercents;
			}
			if(!xhlSpecified && code.xhl != 0.0) {
				xhl = code.xhl;
				xhlSpecified = true;
			}
			if(xht == 0.0) {
				xht = code.xht;
			}
			if(xlt == 0.0) {
				xlt = code.xlt;
			}
			if(normKVs.length == 0) {
				normKVs = code.kvs;
			}
			if(kvaRatings.length == 0) {
				kvaRatings = code.kvas;
			}
			if(imagPercent == 0.0) {
				imagPercent = code.imagPercent;
			}
			if(noLoadLossPercent == 0.0) {
				noLoadLossPercent = code.noLoadLossPercent;
			}
		}
		if(windingNum == 3) {
			if (windingConnections.length == 0) windingConnections = code == null ? new String[]{"wye", "wye", "wye"} : code.conns;
			return createThreeWindingTransformer(xfrId, phaseNum, busTerminals, windingConnections,
					normKVs, kvaRatings, rPercents, windingTaps, xhl, xht, xlt, imagPercent, noLoadLossPercent, lead);
		}
		if (windingNum != 2) throw new IllegalArgumentException("Expected two or three transformer windings: " + xfrId);

		fromBusId =this.dataParser.getBusIdPrefix()+fromBusId;
		toBusId =this.dataParser.getBusIdPrefix()+toBusId;

		// create a transformer object
		AcscBranch xfrBranch = createTransformerBranch(fromBusId, toBusId, xfrId);
		IBranch3Phase xfr3P = (IBranch3Phase) xfrBranch;

		// since InterPSS uses fromBus->toBus(cirId) as the unique branchId, here the original Id is set as the name.
		xfrBranch.setName(this.dataParser.getBusIdPrefix()+xfrId);
		xfrBranch.setBranchCode(AclfBranchCode.XFORMER);

		AclfBranch likeBranch = null;

		if(!referenceXfrName.equals("")){
			likeBranch= this.dataParser.getThreePhaseBranchByName(referenceXfrName);
		}

		if(likeBranch!=null){
			if(!phaseSpecified){
				phaseNum = phaseCount(branch3P(likeBranch).getPhaseCode());
			}
			if(normKV1==0.0){
				normKV1 = likeBranch.getFromTurnRatio()/1000.0;
			}
			if(normKV2==0.0){
				normKV2 = likeBranch.getToTurnRatio()/1000.0;
			}
			if(kva1==0.0){
				kva1 = branch3P(likeBranch).getXfrRatedKVA();
			}
			if(kva2==0.0){
				kva2 = branch3P(likeBranch).getXfrRatedKVA();
			}
			AcscXformerAdapter likexfr = acscXfrAptr.apply((AcscBranch) likeBranch);

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
			xfr3P.setPhaseCode(PhaseCode.ABC);
		}
		else if(phaseNum ==1){
			if(phase1.equals("1")) {
				xfr3P.setPhaseCode(PhaseCode.A);
			} else if(phase1.equals("2")) {
				xfr3P.setPhaseCode(PhaseCode.B);
			} else if(phase1.equals("3")) {
				xfr3P.setPhaseCode(PhaseCode.C);
			} else{
				throw new Error("Transformer connection phase currently must be either 1, 2 or 3.  xfr #" +xfrId);
			}

		}

//		xfrBranch.getFromBus().setBaseVoltage(normKV1, UnitType.kV);
//		xfrBranch.getToBus().setBaseVoltage(normKV2, UnitType.kV);
//
		Complex seriesZ = transformerSeriesImpedanceOhm(normKV1, normKV2, kva1, kva2, losspercent1, xhl);
		if(likeBranch != null) {
			Complex likeZ = likeBranch.getAdjustedZ();
			if(!lossSpecified && !xhlSpecified) {
				seriesZ = likeZ;
			}
			else if(!lossSpecified) {
				seriesZ = new Complex(likeZ.getReal(), seriesZ.getImaginary());
			}
			else if(!xhlSpecified) {
				seriesZ = new Complex(seriesZ.getReal(), likeZ.getImaginary());
			}
		}
		setTransformerSeriesImpedance(xfrBranch, xfr3P, seriesZ);

		xfr3P.setXfrRatedKVA(kva1);


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

		addSecondaryNoLoadAdmittance(xfr3P, normKV2, kva2 > 0.0 ? kva2 : kva1,
				imagPercent, noLoadLossPercent);

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

	/** Stamp a three-terminal primitive using existing two-terminal explicit Y blocks.
	 * Diagonal blocks are partitioned across incident branches; off-diagonal blocks
	 * are exact. Co-located split-phase windings are collected into one bus block.
	 */
	private boolean createThreeWindingTransformer(String id, int phases, String[] buses, String[] conns,
			double[] kv, double[] kva, double[] resistance, double[] taps, double x12, double x13, double x23,
			double imag, double loss, boolean lead) throws InterpssException {
		if ((phases != 1 && phases != 3) || buses.length != 3 || conns.length != 3 || kv.length != 3
				|| kva.length != 3 || resistance.length != 3 || taps.length != 3)
			throw new IllegalArgumentException("Three-winding transformer requires three buses, connections, ratings, resistances and taps: " + id);
		TerminalBus[] terminals = new TerminalBus[3];
		List<String> nodes = new ArrayList<>();
		int[] group = new int[3];
		double[][][] connection = new double[3][3][3];
		double[] commonR = new double[3], effectiveKv = new double[3];
		for (int i = 0; i < 3; i++) {
			if (!Double.isFinite(kv[i] + kva[i] + taps[i] + resistance[i]) || kv[i] <= 0 || kva[i] <= 0 || taps[i] <= 0 || resistance[i] < 0)
				throw new IllegalArgumentException("Invalid winding rating/tap/resistance: " + id);
			terminals[i] = terminalBus(buses[i]);
			if (!nodes.contains(terminals[i].busId)) nodes.add(terminals[i].busId);
			group[i] = nodes.indexOf(terminals[i].busId);
			// OpenDSS %Rs, like XHL/XHT/XLT, use winding-one's power base.
			commonR[i] = resistance[i];
			effectiveKv[i] = kv[i] * taps[i];
			boolean delta = conns[i].equals("delta") || conns[i].equals("d");
			if (!delta && !conns[i].equals("wye") && !conns[i].equals("y"))
				throw new IllegalArgumentException("Unsupported winding connection: " + conns[i]);
			if (!delta && !terminals[i].wyeGrounded)
				throw new IllegalArgumentException("Three-winding wye neutral must be grounded: " + id);
			if (phases == 1) {
				String[] p = terminals[i].nodes;
				if (p.length == 1) connection[i][0][phaseIndex(p[0])] = 1;
				else if (p.length == 2) {
					if (p[0].equals(p[1])) throw new IllegalArgumentException("Winding terminals must differ: " + id);
					if (!p[0].equals("0")) connection[i][0][phaseIndex(p[0])] = 1;
					if (!p[1].equals("0")) connection[i][0][phaseIndex(p[1])] = -1;
				} else throw new IllegalArgumentException("Single-phase winding needs one or two conductor nodes: " + id);
			} else {
				String[] pnodes = terminals[i].nodes;
				if (pnodes.length != 0 && pnodes.length != 3 && !(pnodes.length == 4 && pnodes[3].equals("0")))
					throw new IllegalArgumentException("Three-phase winding requires three phases and optional grounded neutral: " + id);
				for (int p = 0; p < 3; p++) {
					int first = pnodes.length == 0 ? p : phaseIndex(pnodes[p]);
					connection[i][p][first] = delta ? 1 / Math.sqrt(3) : 1;
					int offset = conns[0].startsWith("d") ? (lead ? 1 : 2) : (lead ? 2 : 1);
					int second = pnodes.length == 0 ? (p + offset) % 3 : phaseIndex(pnodes[(p + offset) % 3]);
					if (delta) connection[i][p][second] = -1 / Math.sqrt(3);
				}
			}
		}
		if (nodes.size() < 2) throw new IllegalArgumentException("Transformer requires at least two distinct buses: " + id);
		Complex[][] windingY = centerTappedWindingAdmittance(effectiveKv, kva[0], commonR, x12, x13, x23);
		Complex3x3[][] blocks = new Complex3x3[nodes.size()][nodes.size()];
		for (int i = 0; i < nodes.size(); i++) for (int j = 0; j < nodes.size(); j++) blocks[i][j] = new Complex3x3();
		for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++)
			for (int a = 0; a < 3; a++) for (int b = 0; b < 3; b++) {
				double coefficient = 0;
				for (int p = 0; p < phases; p++) coefficient += connection[i][p][a] * connection[j][p][b];
				var block = blocks[group[i]][group[j]];
				setPhaseValue(block, a, b, getPhaseValue(block, a, b).add(windingY[i][j].multiply(coefficient)));
			}
		// OpenDSS places the excitation branch on winding two, on winding-one's kVA base.
		Complex ym = noLoadAdmittance(effectiveKv[1], kva[0], imag, loss);
		for (int a = 0; a < 3; a++) for (int b = 0; b < 3; b++) {
			double coefficient = 0;
			for (int p = 0; p < phases; p++) coefficient += connection[1][p][a] * connection[1][p][b];
			var block = blocks[group[1]][group[1]];
			setPhaseValue(block, a, b, getPhaseValue(block, a, b).add(ym.multiply(coefficient)));
		}
		for (int a = 0; a < nodes.size(); a++) for (int b = a + 1; b < nodes.size(); b++) {
			int i = 0, j = 0;
			while (group[i] != a) i++;
			while (group[j] != b) j++;
			String branchId = nodes.size() == 2 ? id : id + "_w" + (i + 1) + "_w" + (j + 1);
			var branch = createTransformerBranch(dataParser.getBusIdPrefix() + nodes.get(a), dataParser.getBusIdPrefix() + nodes.get(b), branchId);
			var branch3p = (IBranch3Phase) branch;
			branch.setName(dataParser.getBusIdPrefix() + branchId);
			branch.setBranchCode(AclfBranchCode.XFORMER);
			branch3p.setPhaseCode(phases == 3 ? PhaseCode.ABC : phaseCode(nonGroundNode(terminals[0])));
			branch.setFromTurnRatio(kv[i] * 1000);
			branch.setToTurnRatio(kv[j] * 1000);
			branch3p.setXfrRatedKVA(kva[0]);
			branch3p.setExplicitYabc(blocks[a][a].multiply(1.0 / (nodes.size() - 1)), blocks[a][b], blocks[b][a], blocks[b][b].multiply(1.0 / (nodes.size() - 1)));
			var adapter = acscXfrAptr.apply(branch);
			adapter.setFromGrounding(BusGroundCode.SOLID_GROUNDED, conns[i].startsWith("d") ? XFormerConnectCode.DELTA : XFormerConnectCode.WYE, Complex.ZERO, UnitType.PU);
			adapter.setToGrounding(BusGroundCode.SOLID_GROUNDED, conns[j].startsWith("d") ? XFormerConnectCode.DELTA : XFormerConnectCode.WYE, Complex.ZERO, UnitType.PU);
			if (nodes.size() == 2 && phases == 1) for (int w = 1; w < 3; w++)
				dataParser.registerBranchPowerTerminal(branch, phaseIndex(nonGroundNode(terminals[w])), w + 1);
		}
		return true;
	}

	private static Complex[][] centerTappedWindingAdmittance(double[] kvs, double kvaBase,
			double[] rPercents, double xhl, double xht, double xlt) {
		Complex z12 = new Complex((rPercents[0] + rPercents[1])/100.0, xhl/100.0);
		Complex z13 = new Complex((rPercents[0] + rPercents[2])/100.0, xht/100.0);
		Complex z23 = new Complex((rPercents[1] + rPercents[2])/100.0, xlt/100.0);
		Complex[][] ypu = ThreeWindingTransformerAdmittance.perUnit(z12, z13, z23);
		double mvaBase = kvaBase/1000.0;
		for(int i = 0; i < 3; i++) {
			for(int j = 0; j < 3; j++) {
				ypu[i][j] = ypu[i][j].multiply(mvaBase/(kvs[i]*kvs[j]));
			}
		}
		return ypu;
	}

	private static Complex noLoadAdmittance(double kv, double kva, double imagPercent, double noLoadLossPercent) {
		if(kv <= 0.0 || kva <= 0.0 || (imagPercent == 0.0 && noLoadLossPercent == 0.0)) {
			return Complex.ZERO;
		}
		double conductancePu = noLoadLossPercent/100.0;
		double susceptancePu = imagPercent/100.0;
		Complex admittancePu = new Complex(conductancePu, -susceptancePu);
		return admittancePu.multiply((kva/1000.0)/(kv*kv));
	}

	private static String nonGroundNode(TerminalBus terminal) {
		for(String node : terminal.nodes) {
			if(!"0".equals(node)) {
				return node;
			}
		}
		throw new IllegalArgumentException("No non-ground node for " + terminal.busId);
	}

	private static PhaseCode phaseCode(String node) {
		if("1".equals(node)) {
			return PhaseCode.A;
		}
		if("2".equals(node)) {
			return PhaseCode.B;
		}
		if("3".equals(node)) {
			return PhaseCode.C;
		}
		throw new Error("Transformer connection phase currently must be either 1, 2 or 3. phase=" + node);
	}

	private static int phaseIndex(String node) {
		int phase = Integer.parseInt(node) - 1;
		if (phase < 0 || phase > 2) throw new IllegalArgumentException("Phase node must be 1, 2 or 3: " + node);
		return phase;
	}

	private static Complex getPhaseValue(Complex3x3 matrix, int row, int col) {
		if(row == 0 && col == 0) return matrix.aa;
		if(row == 0 && col == 1) return matrix.ab;
		if(row == 0 && col == 2) return matrix.ac;
		if(row == 1 && col == 0) return matrix.ba;
		if(row == 1 && col == 1) return matrix.bb;
		if(row == 1 && col == 2) return matrix.bc;
		if(row == 2 && col == 0) return matrix.ca;
		if(row == 2 && col == 1) return matrix.cb;
		return matrix.cc;
	}

	private static void setPhaseValue(Complex3x3 matrix, int row, int col, Complex value) {
		if(row == 0 && col == 0) matrix.aa = value;
		else if(row == 0 && col == 1) matrix.ab = value;
		else if(row == 0 && col == 2) matrix.ac = value;
		else if(row == 1 && col == 0) matrix.ba = value;
		else if(row == 1 && col == 1) matrix.bb = value;
		else if(row == 1 && col == 2) matrix.bc = value;
		else if(row == 2 && col == 0) matrix.ca = value;
		else if(row == 2 && col == 1) matrix.cb = value;
		else matrix.cc = value;
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

	private static String normalizePropertyEquals(String value) {
		return value.replaceAll("\\s*=\\s*", "=");
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

	private static int phaseCount(PhaseCode phaseCode) {
		return phaseCode == PhaseCode.ABC ? 3 : 1;
	}

	private List<? extends AclfBranch> currentBranchList() {
		return this.dataParser.isStaticNetworkMode()
				? this.dataParser.getStaticNetwork().getBranchList()
				: this.dataParser.getDistNetwork().getBranchList();
	}

	private AcscBranch createTransformerBranch(String fromBusId, String toBusId, String cirId)
			throws InterpssException {
		if(this.dataParser.isStaticNetworkMode()) {
			this.dataParser.getOrCreateStaticBus(fromBusId);
			this.dataParser.getOrCreateStaticBus(toBusId);
			return ThreePhaseObjectFactory.createStatic3PBranch(fromBusId, toBusId, cirId,
					this.dataParser.getStaticNetwork());
		}
		if(this.dataParser.getDistNetwork().getBus(fromBusId)==null) {
			ThreePhaseObjectFactory.create3PDStabBus(fromBusId, this.dataParser.getDistNetwork());
		}
		if(this.dataParser.getDistNetwork().getBus(toBusId)==null) {
			ThreePhaseObjectFactory.create3PDStabBus(toBusId, this.dataParser.getDistNetwork());
		}
		return ThreePhaseObjectFactory.create3PBranch(fromBusId, toBusId, cirId,
				this.dataParser.getDistNetwork());
	}

	private static IBranch3Phase branch3P(AclfBranch branch) {
		return (IBranch3Phase) branch;
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

	private static String[] listValues(String token) {
		int startIdx = token.indexOf("[");
		int endIdx = token.lastIndexOf("]");
		if(startIdx < 0 || endIdx < 0) {
			startIdx = token.indexOf("(");
			endIdx = token.lastIndexOf(")");
		}
		if(startIdx < 0 || endIdx <= startIdx) {
			return new String[0];
		}
		return token.substring(startIdx + 1, endIdx)
				.trim()
				.replace(",", " ")
				.split("\\s+");
	}

	private static double[] doubleValues(String[] values) {
		double[] doubles = new double[values.length];
		for(int i = 0; i < values.length; i++) {
			doubles[i] = Double.valueOf(values[i]);
		}
		return doubles;
	}

	private static String[] splitOutsideLists(String value) {
		List<String> tokens = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		int bracketDepth = 0;
		int parenDepth = 0;
		for (int i = 0; i < value.length(); i++) {
			char ch = value.charAt(i);
			if (Character.isWhitespace(ch) && bracketDepth == 0 && parenDepth == 0) {
				if (current.length() > 0) {
					tokens.add(current.toString());
					current.setLength(0);
				}
				continue;
			}
			if (ch == '[') {
				bracketDepth++;
			}
			else if (ch == ']' && bracketDepth > 0) {
				bracketDepth--;
			}
			else if (ch == '(') {
				parenDepth++;
			}
			else if (ch == ')' && parenDepth > 0) {
				parenDepth--;
			}
			current.append(ch);
		}
		if (current.length() > 0) {
			tokens.add(current.toString());
		}
		return tokens.toArray(new String[0]);
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

	private static class XfmrCodeData {
		private String[] conns = {"wye", "wye", "wye"};
		private int phaseNum = 3;
		private int windingNum = 2;
		private double[] kvs = new double[0];
		private double[] kvas = new double[0];
		private double[] rPercents = new double[0];
		private double xhl = 0.0;
		private double xht = 0.0;
		private double xlt = 0.0;
		private double imagPercent = 0.0;
		private double noLoadLossPercent = 0.0;
	}
}
