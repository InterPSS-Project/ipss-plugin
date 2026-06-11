package org.interpss.threePhase.dataParser.opendss;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.threePhase.basic.LineConfiguration;
import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.acsc.PhaseCode;
import com.interpss.core.threephase.IBranch3Phase;
import com.interpss.core.threephase.Static3PBranch;

public class OpenDSSLineParser {
	private static final double MIN_CONFIG_SERIES_ZABC_ABS = 1.0E-6;
	private static final double MIN_RAW_SERIES_ZABC_ABS = 1.0E-4;

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
		String  geometryId = "";
		String  units = "";
		double  lineLength = 0;
		int     phaseNum = 3;    // 3 phases by default

		String  fromBusStr = "";
		String  toBusStr = "";

		DStabNetwork3Phase distNet = this.dataParser.isStaticNetworkMode() ? null : this.dataParser.getDistNetwork();

		DStab3PBus fromBus = null, toBus = null;

		int phaseIdx = -1, lineConfigIdx = -1;

		/*
		 * (6) r1 Positive sequence Resistance, ohms per unit length. See also Rmatrix.
			(7) x1 Positive sequence Reactance, ohms per unit length. See also Xmatrix
			(8) r0 Zero sequence Resistance, ohms per unit length.
			(9) x0 Zero sequence Reactance, ohms per unit length.
			(10) c1 Positive sequence capacitance, nf per unit length. See also Cmatrix.
			(11) c0 Zero sequence capacitance, nf per unit length.
		 */
		double r1= 0,r0 = 0, x1 = 0, x0 = 0, c1 = 0, c0 = 0;
		boolean phaseSpecified = false;
		boolean enabled = true;


		String[] lineStrAry = normalizePropertyEquals(lineStr.toLowerCase()).split("\\s+");

		for(int i = 0;i<lineStrAry.length;i++){
			if(lineStrAry[i].contains("line.")){
				lineName    = lineStrAry[i].substring(5);
			}
			else if(lineStrAry[i].contains("phases=")){
				phaseIdx = i;
				phaseNum  = Integer.valueOf(lineStrAry[i].substring(7));
				phaseSpecified = true;
			}

			else if(lineStrAry[i].contains("bus1=")){
				fromBusStr = lineStrAry[i].substring(5);
			}
			else if(lineStrAry[i].contains("bus2=")){
				toBusStr = lineStrAry[i].substring(5);
			}
			else if(lineStrAry[i].contains("linecode=")){
				lineConfigIdx = i;
				lineCodeId= lineStrAry[i].substring(9).toLowerCase();
			}
			else if(lineStrAry[i].contains("geometry=")){
				lineConfigIdx = i;
				geometryId= lineStrAry[i].substring(9).toLowerCase();
			}
			else if(lineStrAry[i].contains("length=")){
				lineLength = Double.valueOf(lineStrAry[i].substring(7));
			}
			else if(lineStrAry[i].contains("units=")){
				units = lineStrAry[i].substring(6);
			}
			else if(lineStrAry[i].contains("r1=")){
				r1 = Double.valueOf(lineStrAry[i].substring(3));
				if(i + 3 < lineStrAry.length
						&& isNumeric(lineStrAry[i + 1])
						&& isNumeric(lineStrAry[i + 2])
						&& isNumeric(lineStrAry[i + 3])) {
					x1 = Double.valueOf(lineStrAry[i + 1]);
					r0 = Double.valueOf(lineStrAry[i + 2]);
					x0 = Double.valueOf(lineStrAry[i + 3]);
				}
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
			else if(lineStrAry[i].contains("enabled=")){
				enabled = isEnabled(lineStrAry[i].substring(8));
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
		if(!phaseSpecified && !fromBusPhases.equals("1.2.3")) {
			phaseNum = fromBusPhases.split("\\.").length;
		}

		Complex3x3 zabc = null;
		Complex3x3 yshuntabc = new Complex3x3();
		LineConfiguration config = null;

		if(lineConfigIdx> 0){ // line parameters defined by line code or geometry

			String configId = (lineCodeId.equals("") ? geometryId : lineCodeId).toLowerCase();
			config = this.dataParser.getLineConfigTable().get(configId);

			if(config!=null){
				zabc = copyComplex3x3(config.getZ3x3Matrix());
				lineLength = lineLength * OpenDSSUnitConverter.lengthFactor(units, config.getLengthUnit());
				if(config.getShuntY3x3Matrix() != null) {
					yshuntabc = capacitanceNfToSiemens(config.getShuntY3x3Matrix(), lineLength);
				}
			}
			else{
				throw new Error("LineConfiguration definition not found, id:"+configId);
			}
		}
		else{ // line parameters defined by raw data
			if(r1>= 0 || x1>0){

				Complex z1 = new Complex(r1,x1);
				Complex z0 = new Complex(r0,x0);

				// input as three sequence data and then converted it three-phase
				zabc = new Complex3x3(z1,z1,z0).ToAbc();
				if(c1 != 0.0 || c0 != 0.0) {
					Complex3x3 cabc = new Complex3x3(new Complex(0.0, c1),
							new Complex(0.0, c1), new Complex(0.0, c0)).ToAbc();
					yshuntabc = capacitanceNfToSiemens(cabc, lineLength);
				}
			}
			else{
				throw new Error("Error in Line Z, Y parameter raw data: "+lineStr);
			}

		}

		if(!fromBusPhases.equals(toBusPhases)){
			throw new Error("different phase arrangements on both terminals not support yet, from: "+fromBusPhases+ ", to: "+toBusPhases);
		}
		if(config != null && config.getNphases() == 1 && phaseNum > 1) {
			zabc = multiPhaseDiagonalFromSinglePhase(zabc.aa, fromBusPhases);
			yshuntabc = multiPhaseDiagonalFromSinglePhase(yshuntabc.aa, fromBusPhases);
		}
		else if(phaseNum==3){
			// no change is needed
		}
		else if(phaseNum==2){
			zabc = twoPhaseMatrix(zabc, fromBusPhases);
			yshuntabc = twoPhaseMatrix(yshuntabc, fromBusPhases);
		}
		else if(phaseNum==1){
			if(config != null && config.isKronReductionEnabled() && config.getNeutralConductor() > 0
					&& config.getNphases() > 1) {
				zabc = singlePhaseServiceLoopMatrix(zabc, config.getNeutralConductor(),
						config.getKronReductionCount(), fromBusPhases);
				yshuntabc = singlePhaseMatrix(yshuntabc, Integer.valueOf(fromBusPhases));
			}
			else
			if(fromBusPhases.equals("1")){
				zabc = singlePhaseMatrix(zabc, 1);
				yshuntabc = singlePhaseMatrix(yshuntabc, 1);
			}
			else if(fromBusPhases.equals("2")){
				zabc = singlePhaseMatrix(zabc, 2);
				yshuntabc = singlePhaseMatrix(yshuntabc, 2);
			}
			else if(fromBusPhases.equals("3")){
				zabc = singlePhaseMatrix(zabc, 3);
				yshuntabc = singlePhaseMatrix(yshuntabc, 3);
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

		IBranch3Phase line3Phase = null;
		AclfBranch line = null;
		if(this.dataParser.isStaticNetworkMode()) {
			this.dataParser.getOrCreateStaticBus(fromBusId);
			this.dataParser.getOrCreateStaticBus(toBusId);
			Static3PBranch staticLine = ThreePhaseObjectFactory.createStatic3PBranch(fromBusId, toBusId, "1",
					this.dataParser.getStaticNetwork());
			line3Phase = staticLine;
			line = staticLine;
		}
		else {
			if(distNet.getBus(fromBusId)==null) {
				fromBus = ThreePhaseObjectFactory.create3PDStabBus(fromBusId, distNet);
			}

			if(distNet.getBus(toBusId)==null) {
				toBus = ThreePhaseObjectFactory.create3PDStabBus(toBusId, distNet);
			}

			DStab3PBranch dynamicLine = ThreePhaseObjectFactory.create3PBranch(fromBusId, toBusId, "1", distNet);
			line3Phase = dynamicLine;
			line = dynamicLine;
		}

		line.setName(this.dataParser.getBusIdPrefix()+lineName);


		line.setBranchCode(AclfBranchCode.LINE);
		line3Phase.setPhaseCode(phaseCode(fromBusPhases));
		line.setStatus(enabled);
		// the format of Zmatrix need to be consistent with the number of phases and the phases in use.


		// need to consider the line length
		//TODO consistency of the unit types
		if(lineLength == 0.0 && lineConfigIdx < 0) {
			lineLength = 1.0;
		}
		Complex3x3 lineZabc = zabc.multiply(lineLength);
		double minSeriesZabcAbs = lineConfigIdx < 0 ? MIN_RAW_SERIES_ZABC_ABS : MIN_CONFIG_SERIES_ZABC_ABS;
		lineZabc = applyMinimumSeriesImpedance(lineZabc, fromBusPhases, minSeriesZabcAbs);
		line3Phase.setZabc(lineZabc);

		if(line3Phase.getZabc().absMax()<1.0E-7){
			throw new Error("Line Zabc.absMax() is less than 1.0E-7. LineID, Name = "+line.getId()+", "+line.getName());
		}

		if(yshuntabc != null && yshuntabc.absMax() > 0.0) {
			line3Phase.setFromShuntYabc(yshuntabc.multiply(0.5));
			line3Phase.setToShuntYabc(yshuntabc.multiply(0.5));
		}

		return no_error;

	}

	private static Complex3x3 applyMinimumSeriesImpedance(Complex3x3 zabc, String busPhases, double minSeriesZabcAbs) {
		double absMax = zabc.absMax();
		if(absMax >= minSeriesZabcAbs) {
			return zabc;
		}
		if(absMax > 0.0) {
			return zabc.multiply(minSeriesZabcAbs / absMax);
		}
		Complex3x3 floor = new Complex3x3();
		for(String phase : busPhases.split("\\.")) {
			if("1".equals(phase)) {
				floor.aa = new Complex(minSeriesZabcAbs, 0.0);
			}
			else if("2".equals(phase)) {
				floor.bb = new Complex(minSeriesZabcAbs, 0.0);
			}
			else if("3".equals(phase)) {
				floor.cc = new Complex(minSeriesZabcAbs, 0.0);
			}
			else {
				throw new Error("phase arrangement not support yet : " + busPhases);
			}
		}
		return floor;
	}

	private static String normalizePropertyEquals(String value) {
		return value.replaceAll("\\s*=\\s*", "=");
	}

	private static Complex3x3 capacitanceNfToSiemens(Complex3x3 capacitanceNf, double lineLength) {
		return capacitanceNf.multiply(2.0 * Math.PI * 60.0 * 1.0e-9 * lineLength);
	}

	private static PhaseCode phaseCode(String busPhases) {
		if("1".equals(busPhases)) {
			return PhaseCode.A;
		}
		if("2".equals(busPhases)) {
			return PhaseCode.B;
		}
		if("3".equals(busPhases)) {
			return PhaseCode.C;
		}
		if("1.2".equals(busPhases) || "2.1".equals(busPhases)) {
			return PhaseCode.AB;
		}
		if("1.3".equals(busPhases) || "3.1".equals(busPhases)) {
			return PhaseCode.AC;
		}
		if("2.3".equals(busPhases) || "3.2".equals(busPhases)) {
			return PhaseCode.BC;
		}
		if("1.2.3".equals(busPhases)) {
			return PhaseCode.ABC;
		}
		throw new Error("phase arrangement not support yet : "+busPhases);
	}

	private static Complex3x3 copyComplex3x3(Complex3x3 source) {
		Complex3x3 copy = new Complex3x3();
		copy.aa = source.aa;
		copy.ab = source.ab;
		copy.ac = source.ac;
		copy.ba = source.ba;
		copy.bb = source.bb;
		copy.bc = source.bc;
		copy.ca = source.ca;
		copy.cb = source.cb;
		copy.cc = source.cc;
		return copy;
	}

	private static Complex3x3 singlePhaseMatrix(Complex3x3 source, int phase) {
		Complex3x3 matrix = new Complex3x3();
		Complex phaseValue = source.aa;
		if(phase == 2 && source.aa.abs() < 1.0E-8 && source.bb.abs() > 1.0E-8) {
			phaseValue = source.bb;
		}
		else if(phase == 3 && source.aa.abs() < 1.0E-8 && source.cc.abs() > 1.0E-8) {
			phaseValue = source.cc;
		}
		if(phase == 1) {
			matrix.aa = phaseValue;
		}
		else if(phase == 2) {
			matrix.bb = phaseValue;
		}
		else if(phase == 3) {
			matrix.cc = phaseValue;
		}
		return matrix;
	}

	private static Complex3x3 twoPhaseMatrix(Complex3x3 source, String busPhases) {
		String[] phases = busPhases.split("\\.");
		if(phases.length != 2) {
			throw new Error("phase arrangement not support yet : " + busPhases);
		}
		Complex3x3 matrix = new Complex3x3();
		for(int row = 0; row < phases.length; row++) {
			int targetRow = Integer.valueOf(phases[row]) - 1;
			for(int col = 0; col < phases.length; col++) {
				int targetCol = Integer.valueOf(phases[col]) - 1;
				setMatrixValue(matrix, targetRow, targetCol, matrixValue(source, row, col));
			}
		}
		return matrix;
	}

	private static Complex matrixValue(Complex3x3 matrix, int row, int col) {
		if(row == 0 && col == 0) {
			return matrix.aa;
		}
		if(row == 0 && col == 1) {
			return matrix.ab;
		}
		if(row == 0 && col == 2) {
			return matrix.ac;
		}
		if(row == 1 && col == 0) {
			return matrix.ba;
		}
		if(row == 1 && col == 1) {
			return matrix.bb;
		}
		if(row == 1 && col == 2) {
			return matrix.bc;
		}
		if(row == 2 && col == 0) {
			return matrix.ca;
		}
		if(row == 2 && col == 1) {
			return matrix.cb;
		}
		if(row == 2 && col == 2) {
			return matrix.cc;
		}
		throw new Error("phase index not supported: row=" + row + ", col=" + col);
	}

	private static void setMatrixValue(Complex3x3 matrix, int row, int col, Complex value) {
		if(row == 0 && col == 0) {
			matrix.aa = value;
		}
		else if(row == 0 && col == 1) {
			matrix.ab = value;
		}
		else if(row == 0 && col == 2) {
			matrix.ac = value;
		}
		else if(row == 1 && col == 0) {
			matrix.ba = value;
		}
		else if(row == 1 && col == 1) {
			matrix.bb = value;
		}
		else if(row == 1 && col == 2) {
			matrix.bc = value;
		}
		else if(row == 2 && col == 0) {
			matrix.ca = value;
		}
		else if(row == 2 && col == 1) {
			matrix.cb = value;
		}
		else if(row == 2 && col == 2) {
			matrix.cc = value;
		}
		else {
			throw new Error("phase index not supported: row=" + row + ", col=" + col);
		}
	}

	private static Complex3x3 singlePhaseServiceLoopMatrix(Complex3x3 source, int neutralConductor,
			int kronReductionCount, String busPhase) {
		Complex[][] reduced = toArray(source, 3);
		for(int i = 0; i < kronReductionCount && reduced.length > 1; i++) {
			int neutral = Math.min(neutralConductor - 1, reduced.length - 1);
			reduced = kronReduce(reduced, neutral);
		}
		Complex loopImpedance = reduced[0][0];
		Complex3x3 loopMatrix = new Complex3x3();
		int systemPhase = Integer.valueOf(busPhase);
		if(systemPhase == 1) {
			loopMatrix.aa = loopImpedance;
		}
		else if(systemPhase == 2) {
			loopMatrix.bb = loopImpedance;
		}
		else if(systemPhase == 3) {
			loopMatrix.cc = loopImpedance;
		}
		else {
			throw new Error("phase arrangement not support yet : " + busPhase);
		}
		return loopMatrix;
	}

	private static Complex[][] kronReduce(Complex[][] source, int neutral) {
		int n = source.length;
		Complex[][] reduced = new Complex[n - 1][n - 1];
		Complex neutralSelfInv = new Complex(1.0).divide(source[neutral][neutral]);
		int outRow = 0;
		for(int row = 0; row < n; row++) {
			if(row == neutral) {
				continue;
			}
			int outCol = 0;
			for(int col = 0; col < n; col++) {
				if(col == neutral) {
					continue;
				}
				reduced[outRow][outCol] = source[row][col]
						.subtract(source[row][neutral].multiply(neutralSelfInv).multiply(source[neutral][col]));
				outCol++;
			}
			outRow++;
		}
		return reduced;
	}

	private static Complex[][] toArray(Complex3x3 source, int dimension) {
		Complex[][] values = new Complex[dimension][dimension];
		for(int row = 0; row < dimension; row++) {
			for(int col = 0; col < dimension; col++) {
				values[row][col] = matrixValue(source, row, col);
			}
		}
		return values;
	}

	private static Complex3x3 multiPhaseDiagonalFromSinglePhase(Complex phaseValue, String busPhases) {
		Complex3x3 matrix = new Complex3x3();
		for(String phase : busPhases.split("\\.")) {
			if("1".equals(phase)) {
				matrix.aa = phaseValue;
			}
			else if("2".equals(phase)) {
				matrix.bb = phaseValue;
			}
			else if("3".equals(phase)) {
				matrix.cc = phaseValue;
			}
			else {
				throw new Error("phase arrangement not support yet : " + busPhases);
			}
		}
		return matrix;
	}

	private static boolean isNumeric(String token) {
		try {
			Double.valueOf(token);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	private static boolean isEnabled(String value) {
		String normalized = value.trim();
		return !("false".equals(normalized)
				|| "no".equals(normalized)
				|| "0".equals(normalized));
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
