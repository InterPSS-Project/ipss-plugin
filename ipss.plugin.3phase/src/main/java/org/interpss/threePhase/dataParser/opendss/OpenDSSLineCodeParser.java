package org.interpss.threePhase.dataParser.opendss;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.common.IFileReader;
import org.ieee.odm.common.ODMLogger;
import org.ieee.odm.common.ODMTextFileReader;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.threePhase.basic.LineConfiguration;

public class OpenDSSLineCodeParser {

	private OpenDSSDataParser dataParser = null;

	public OpenDSSLineCodeParser(OpenDSSDataParser parser){
		this.dataParser = parser;
	}

	public boolean  parseLineCodeFile(String fileName) {


		try {
			final File file = new File(fileName);
			final InputStream stream = new FileInputStream(file);
			ODMLogger.getLogger().info("Parse input file and create the parser object, " + fileName);

			final BufferedReader din = new BufferedReader(new InputStreamReader(stream));
			IFileReader reader = new ODMTextFileReader(din);



			String str;
	      	int busCnt = 0;
	      	String lineCodeId = "";
	      	int nphases = 0;

			String[] lineData = null;
			String code_id ="";
			LineConfiguration lineConfig = null;

			double[] rMatrixData = new double[6];
			double[] xMatrixData = new double[6];
			double[] cMatrixData = new double[6];
	    	do {
	          	str = reader.readLine();
	        	if (str != null && !str.trim().equals("")) {
	        		str = str.trim();
	        		String lowerStr = str.toLowerCase();
	        		if(str.startsWith("!") || str.startsWith("//")){
	        			//bypass the comment
	        		}
	        		else if(str.startsWith("New")||str.startsWith("new")){
	        			//tokenizer by blank
	        			//StringTokenizer st = new StringTokenizer(str);
	        			//while (st.hasMoreTokens()) {

	        			lineData = str.split("\\s+");
	        			code_id =lineData[1];


	        			// set lineCodeId, nphases and baseFreq
	        			lineCodeId = code_id.substring(code_id.indexOf(".")+1).toLowerCase();

	        			nphases = getIntValue(lineData, "nphases=", 3);

	        			lineConfig = new LineConfiguration();
	        			lineConfig.setId(lineCodeId);
	        			lineConfig.setNphases(nphases);
	        			lineConfig.setLengthUnit(getStringValue(lineData, "units=", ""));
	        			applySequenceParameters(lineConfig, lineData, nphases);
	        			this.dataParser.getLineConfigTable().put(lineCodeId, lineConfig);


	        		}

	        		/*
	        		 * rmatrix Resistance matrix, lower triangle, ohms per unit length.
	        		 */

	        		if(lowerStr.contains("rmatrix")){
	        			parseLowerTriangleMatrix(str, "rmatrix", rMatrixData);
	        		}
	        		/*
	        		 * xmatrix: Reactance matrix, lower triangle, ohms per unit length.
	        		 */
                    if(lowerStr.contains("xmatrix")){
	        			parseLowerTriangleMatrix(str, "xmatrix", xMatrixData);
	        			lineConfig.setZ3x3Matrix(toComplex3x3(nphases, rMatrixData, xMatrixData));

	        		}

	        		/*
	        		 *
	        		 * Nodal Capacitance matrix, lower triangle, nf per unit length.Order of the matrix is the number of phases. May be used to specify the shunt capacitance of any line configuration
	        		 */

                    if(lowerStr.contains("cmatrix")){
	        			parseLowerTriangleMatrix(str, "cmatrix", cMatrixData);
	        			lineConfig.setShuntY3x3Matrix(toImaginary3x3(nphases, cMatrixData));

	        		}
                    if(lowerStr.contains("units")){
                    	if(lineConfig != null) {
                    		lineConfig.setLengthUnit(getStringValue(str.split("\\s+"), "units=", lineConfig.getLengthUnit()));
                    	}
                    }
                    if(lineConfig != null && (lowerStr.contains("neutral=") || lowerStr.contains("kron="))) {
                    	applyNeutralKronMetadata(lineConfig, str.split("\\s+"));
                    }


	        	}


	        } while(str != null);


			return true;
		} catch (Exception e) {
			ODMLogger.getLogger().severe(e.toString());

			e.printStackTrace();
			return false;
		}

	}

	public boolean parseLineCodeBlock(List<String> lineCodeLines) {
		LineConfiguration lineConfig = null;
		int nphases = 0;
		double[] rMatrixData = new double[6];
		double[] xMatrixData = new double[6];
		double[] cMatrixData = new double[6];

		try {
			for (String rawLine : lineCodeLines) {
				if (rawLine == null) {
					continue;
				}
				String str = stripInlineComment(rawLine.trim());
				if (str.equals("") || str.startsWith("!") || str.startsWith("//")) {
					continue;
				}
				if (str.startsWith("~")) {
					str = str.substring(1).trim();
				}
				String lowerStr = str.toLowerCase();

				if (lowerStr.startsWith("new")) {
					String[] lineData = str.split("\\s+");
					String codeId = lineData[1];
					String lineCodeId = codeId.substring(codeId.indexOf(".") + 1).toLowerCase();
					nphases = getIntValue(lineData, "nphases=", 3);

					lineConfig = new LineConfiguration();
					lineConfig.setId(lineCodeId);
					lineConfig.setNphases(nphases);
					applySequenceParameters(lineConfig, lineData, nphases);
					this.dataParser.getLineConfigTable().put(lineCodeId, lineConfig);
				}
				if (lowerStr.contains("rmatrix")) {
					parseLowerTriangleMatrix(str, "rmatrix", rMatrixData);
				}
				if (lowerStr.contains("xmatrix")) {
					parseLowerTriangleMatrix(str, "xmatrix", xMatrixData);
					if (lineConfig == null) {
						throw new Error("line code xmatrix is defined before linecode header: " + str);
					}
					lineConfig.setZ3x3Matrix(toComplex3x3(nphases, rMatrixData, xMatrixData));
				}
				if (lowerStr.contains("cmatrix")) {
					parseLowerTriangleMatrix(str, "cmatrix", cMatrixData);
					if (lineConfig == null) {
						throw new Error("line code cmatrix is defined before linecode header: " + str);
					}
					lineConfig.setShuntY3x3Matrix(toImaginary3x3(nphases, cMatrixData));
				}
				if (lowerStr.contains("units=")) {
					if (lineConfig == null) {
						throw new Error("line code units are defined before linecode header: " + str);
					}
					lineConfig.setLengthUnit(getStringValue(str.split("\\s+"), "units=", ""));
				}
				if (lineConfig != null && (lowerStr.contains("neutral=") || lowerStr.contains("kron="))) {
					applyNeutralKronMetadata(lineConfig, str.split("\\s+"));
				}
			}
			return true;
		} catch (Exception e) {
			ODMLogger.getLogger().severe(e.toString());
			e.printStackTrace();
			return false;
		}
	}

	private static String stripInlineComment(String value) {
		int commentIdx = value.indexOf("!");
		return commentIdx > 0 ? value.substring(0, commentIdx).trim() : value;
	}

	private static int getIntValue(String[] tokens, String key, int defaultValue) {
		for (String token : tokens) {
			String lowerToken = token.toLowerCase();
			if (lowerToken.startsWith(key)) {
				return Integer.valueOf(token.substring(token.indexOf("=") + 1));
			}
		}
		return defaultValue;
	}

	private static String getStringValue(String[] tokens, String key, String defaultValue) {
		for (String token : tokens) {
			String lowerToken = token.toLowerCase();
			if (lowerToken.startsWith(key)) {
				return token.substring(token.indexOf("=") + 1);
			}
		}
		return defaultValue;
	}

	private static double getDoubleValue(String[] tokens, String key, double defaultValue) {
		for (String token : tokens) {
			String lowerToken = token.toLowerCase();
			if (lowerToken.startsWith(key)) {
				return Double.valueOf(token.substring(token.indexOf("=") + 1));
			}
		}
		return defaultValue;
	}

	private static void applyNeutralKronMetadata(LineConfiguration lineConfig, String[] tokens) {
		for (String token : tokens) {
			String lowerToken = token.toLowerCase();
			if (lowerToken.startsWith("neutral=")) {
				lineConfig.setNeutralConductor(Integer.valueOf(token.substring(token.indexOf("=") + 1)));
			}
			else if (lowerToken.startsWith("kron=")) {
				if(isTrueValue(token.substring(token.indexOf("=") + 1))) {
					lineConfig.addKronReduction();
				}
				else {
					lineConfig.setKronReductionEnabled(false);
				}
			}
		}
	}

	private static boolean isTrueValue(String value) {
		String normalized = value.trim().toLowerCase();
		return "yes".equals(normalized) || "true".equals(normalized) || "1".equals(normalized);
	}

	private static void applySequenceParameters(LineConfiguration lineConfig, String[] tokens, int nphases) {
		double r1 = getDoubleValue(tokens, "r1=", Double.NaN);
		double x1 = getDoubleValue(tokens, "x1=", Double.NaN);
		if (Double.isNaN(r1) && Double.isNaN(x1)) {
			return;
		}
		double r0 = getDoubleValue(tokens, "r0=", Double.isNaN(r1) ? 0.0 : r1);
		double x0 = getDoubleValue(tokens, "x0=", Double.isNaN(x1) ? 0.0 : x1);
		Complex z1 = new Complex(Double.isNaN(r1) ? 0.0 : r1, Double.isNaN(x1) ? 0.0 : x1);
		Complex z0 = new Complex(r0, x0);
		if (nphases == 1) {
			Complex3x3 zMatrix = new Complex3x3();
			zMatrix.aa = z1;
			lineConfig.setZ3x3Matrix(zMatrix);
		}
		else {
			lineConfig.setZ3x3Matrix(new Complex3x3(z1, z1, z0).ToAbc());
		}

		double c1 = getDoubleValue(tokens, "c1=", 0.0);
		double c0 = getDoubleValue(tokens, "c0=", c1);
		if (c1 != 0.0 || c0 != 0.0) {
			if (nphases == 1) {
				Complex3x3 cMatrix = new Complex3x3();
				cMatrix.aa = new Complex(0.0, c1);
				lineConfig.setShuntY3x3Matrix(cMatrix);
			}
			else {
				lineConfig.setShuntY3x3Matrix(new Complex3x3(
						new Complex(0.0, c1), new Complex(0.0, c1), new Complex(0.0, c0)).ToAbc());
			}
		}
	}

	private static void parseLowerTriangleMatrix(String str, String key, double[] matrixData) {
		String dataStr = matrixDataString(str, key);
		if (dataStr == null || dataStr.equals("")) {
			throw new Error("line code format error: " + str);
		}
		dataStr = dataStr.trim();
		if ((dataStr.startsWith("[") && dataStr.endsWith("]"))
				|| (dataStr.startsWith("(") && dataStr.endsWith(")"))) {
			dataStr = dataStr.substring(1, dataStr.length() - 1).trim();
		}
		double[] values = new double[9];
		int idx = 0;
		if (dataStr.contains("|")) {
			String[] sections = dataStr.split("\\|");
			for (String section : sections) {
				String[] sectionValues = section.trim().split("\\s+");
				for (String value : sectionValues) {
					if (!value.equals("")) {
						values[idx++] = Double.valueOf(value);
					}
				}
			}
		}
		else {
			for(String value : dataStr.trim().split("\\s+")) {
				if(!value.equals("")) {
					values[idx++] = Double.valueOf(value);
				}
			}
		}
		if (idx == 9) {
			matrixData[0] = values[0];
			matrixData[1] = values[1];
			matrixData[2] = values[4];
			matrixData[3] = values[2];
			matrixData[4] = values[5];
			matrixData[5] = values[8];
			return;
		}
		if (idx > matrixData.length) {
			throw new Error("line code matrix has unsupported value count " + idx + ": " + str);
		}
		for (int i = 0; i < idx; i++) {
			matrixData[i] = values[i];
		}
	}

	private static String matrixDataString(String str, String key) {
		String lower = str.toLowerCase();
		if(!lower.contains(key)) {
			return null;
		}
		int keyIdx = lower.indexOf(key);
		int eqIdx = lower.indexOf("=", keyIdx);
		if(eqIdx < 0) {
			return null;
		}
		int valueStart = eqIdx + 1;
		while(valueStart < str.length() && Character.isWhitespace(str.charAt(valueStart))) {
			valueStart++;
		}
		if(valueStart >= str.length()) {
			return null;
		}
		char first = str.charAt(valueStart);
		if(first == '[' || first == '(') {
			char end = first == '[' ? ']' : ')';
			int endIdx = str.indexOf(end, valueStart + 1);
			if(endIdx < 0) {
				throw new Error("line code format error: " + str);
			}
			return str.substring(valueStart, endIdx + 1);
		}
		int valueEnd = valueStart;
		while(valueEnd < str.length() && !Character.isWhitespace(str.charAt(valueEnd))) {
			valueEnd++;
		}
		return str.substring(valueStart, valueEnd);
	}

	private static Complex3x3 toComplex3x3(int nphases, double[] rMatrixData, double[] xMatrixData) throws Exception {
		Complex3x3 zMatrix = new Complex3x3();
		if (nphases >= 1) {
			zMatrix.aa = new Complex(rMatrixData[0], xMatrixData[0]);
		}
		if (nphases >= 2) {
			zMatrix.ab = new Complex(rMatrixData[1], xMatrixData[1]);
			zMatrix.ba = new Complex(rMatrixData[1], xMatrixData[1]);
			zMatrix.bb = new Complex(rMatrixData[2], xMatrixData[2]);
		}
		if (nphases == 3) {
			zMatrix.ac = new Complex(rMatrixData[3], xMatrixData[3]);
			zMatrix.ca = new Complex(rMatrixData[3], xMatrixData[3]);
			zMatrix.bc = new Complex(rMatrixData[4], xMatrixData[4]);
			zMatrix.cb = new Complex(rMatrixData[4], xMatrixData[4]);
			zMatrix.cc = new Complex(rMatrixData[5], xMatrixData[5]);
		}
		else if (nphases > 3) {
			throw new Exception("nphases > 3 not supported yet");
		}
		return zMatrix;
	}

	private static Complex3x3 toImaginary3x3(int nphases, double[] cMatrixData) throws Exception {
		Complex3x3 yMatrix = new Complex3x3();
		if (nphases >= 1) {
			yMatrix.aa = new Complex(0.0, cMatrixData[0]);
		}
		if (nphases >= 2) {
			yMatrix.ab = new Complex(0.0, cMatrixData[1]);
			yMatrix.ba = new Complex(0.0, cMatrixData[1]);
			yMatrix.bb = new Complex(0.0, cMatrixData[2]);
		}
		if (nphases == 3) {
			yMatrix.ac = new Complex(0.0, cMatrixData[3]);
			yMatrix.ca = new Complex(0.0, cMatrixData[3]);
			yMatrix.bc = new Complex(0.0, cMatrixData[4]);
			yMatrix.cb = new Complex(0.0, cMatrixData[4]);
			yMatrix.cc = new Complex(0.0, cMatrixData[5]);
		}
		else if (nphases > 3) {
			throw new Exception("nphases > 3 not supported yet");
		}
		return yMatrix;
	}

	public LineConfiguration parseLineCodeString(String linecodeStr) throws Exception{
      	int busCnt = 0;
      	String lineCodeId = "";
      	int nphases = 0;
      	int baseFreq = 60;

		String[] lineData = null;
		String code_id ="";
		String nphaseStr ="";
		String baseFreqStr ="";
		LineConfiguration lineConfig = null;

		double[] rMatrixData = new double[6];
		double[] xMatrixData = new double[6];
		double[] cMatrixData = new double[6];


		String str = linecodeStr.trim();
		if(str.startsWith("!") || str.startsWith("//")){
			//bypass the comment
		}
		else if(str.startsWith("New")||str.startsWith("new")){
			//tokenizer by blank
			//StringTokenizer st = new StringTokenizer(str);
			//while (st.hasMoreTokens()) {

			lineData = str.split("\\s+");
			code_id =lineData[1];
			nphaseStr =lineData[2];
			baseFreqStr =lineData[3];


			// set lineCodeId, nphases and baseFreq
			lineCodeId = code_id.substring(code_id.indexOf(".")+1);

			nphases = Integer.valueOf(nphaseStr.substring(nphaseStr.indexOf("=")+1));

			baseFreq = Integer.valueOf(baseFreqStr.substring(baseFreqStr.indexOf("=")+1));

			lineConfig = new LineConfiguration();
			lineConfig.setId(lineCodeId);
			lineConfig.setNphases(nphases);
			this.dataParser.getLineConfigTable().put(lineCodeId, lineConfig);


		}

		/*
		 * rmatrix Resistance matrix, lower triangle, ohms per unit length.
		 */

		else if(str.contains("rmatrix")){
			// get the matrix data within the brackets,
			int startIdx = str.indexOf("[");
			int lastIdx = str.indexOf("]");
			if( startIdx <0 ){
				startIdx= str.indexOf("(");
				if(startIdx<0){
					throw new Error("line code format error: "+ str);
				} else {
					lastIdx=str.indexOf(")");
				}
			}
			// if it has "|", tokenizer by "|", otherwise it is only one phase data, need to check <nphases>
			String dataStr = str.substring(startIdx+1, lastIdx).trim();
			String[] rDataStr = null;

			// Symmetrical matrices can be entered in a lower triangle form, for example
		    // Xmatrix=[ 1.2 | .3 1.2 | .3 .3 1.2 ] ! (3x3 matrix lower triangle)
			if (dataStr.contains("|")){
				 rDataStr = dataStr.split("\\|");

					int idx = 0;
        			for(int i = 0; i< rDataStr.length;i++){
        				if(i==0){
        				   rMatrixData[idx] = Double.valueOf(rDataStr[i]);
        				   idx +=1;
        				}
        				else if(i==1){
        					String[] rDataStr2 = rDataStr[i].trim().split("\\s+");
        					for (String element : rDataStr2) {
        						rMatrixData[idx] = Double.valueOf(element);
        								 idx +=1;
        					}
        				}
        				else if(i==2){
        					String[] rDataStr3 = rDataStr[i].trim().split("\\s+");
        					for (String element : rDataStr3) {
        						rMatrixData[idx] = Double.valueOf(element);
        						idx +=1;
        					}

        				}

        			}
			}
			else{
				///rDataStr[0]= dataStr;
				rMatrixData[0] = Double.valueOf(dataStr);
			}
		}
		/*
		 * xmatrix: Reactance matrix, lower triangle, ohms per unit length.
		 */
        else if(str.contains("xmatrix")){

            // get the matrix data within the brackets,

   			int startIdx = str.indexOf("[");
			int lastIdx = str.indexOf("]");
			if( startIdx <0 ){
				startIdx= str.indexOf("(");
				if(startIdx<0){
					throw new Error("line code format error: "+ str);
				} else {
					lastIdx=str.indexOf(")");
				}
			}
			// if it has "|", tokenizer by "|", otherwise it is only one phase data, need to check <nphases>
			String dataStr = str.substring(startIdx+1, lastIdx).trim();

			String[] xDataStr = null;
			if (dataStr.contains("|")){
				 xDataStr = dataStr.split("\\|");

					int idx = 0;
        			for(int i = 0; i< xDataStr.length;i++){
        				if(i==0){
        				   xMatrixData[idx] = Double.valueOf(xDataStr[i]);
        				   idx +=1;
        				}
        				else if(i==1){
        					String[] xDataStr2 = xDataStr[i].trim().split("\\s+");
        					for (String element : xDataStr2) {
        						xMatrixData[idx] = Double.valueOf(element);
        								 idx +=1;
        					}
        				}
        				else if(i==2){
        					String[] xDataStr3 = xDataStr[i].trim().split("\\s+");
        					for (String element : xDataStr3) {
        						xMatrixData[idx] = Double.valueOf(element);
        						idx +=1;
        					}

        				}

        			}
			}
			else{
				//xDataStr[0]= dataStr;
				xMatrixData[0] = Double.valueOf(dataStr);
			}




        	//since the Zmatrix will be created when processing the rmatrix, so here just update the imaginary part of the Zmatrix
			Complex3x3 zMatrix = new Complex3x3();


			if(nphases >=1){
				zMatrix.aa = new Complex (rMatrixData[0], xMatrixData[0]);
			}
			if(nphases>=2){

				zMatrix.ab = new Complex (rMatrixData[1], xMatrixData[1]);
				zMatrix.ba = new Complex (rMatrixData[1], xMatrixData[1]);
				zMatrix.bb = new Complex (rMatrixData[2], xMatrixData[2]);

			}
			if(nphases==3){
				zMatrix.ac = new Complex (rMatrixData[3], xMatrixData[3]);
				zMatrix.ca = new Complex (rMatrixData[3], xMatrixData[3]);

				zMatrix.bc = new Complex (rMatrixData[4], xMatrixData[4]);
				zMatrix.cb = new Complex (rMatrixData[4], xMatrixData[4]);

				zMatrix.cc = new Complex (rMatrixData[5], xMatrixData[5]);

			}
			else if(nphases>3){
				throw new Exception("nphases > 3 not supported yet");
			}

			lineConfig.setZ3x3Matrix(zMatrix);

		}

		/*
		 *
		 * Nodal Capacitance matrix, lower triangle, nf per unit length.Order of the matrix is the number of phases. May be used to specify the shunt capacitance of any line configuration
		 */

        else if(str.contains("cmatrix")){

//get the matrix data within the brackets,

   			int startIdx = str.indexOf("[");
			int lastIdx = str.indexOf("]");

			if( startIdx <0 ){
				startIdx= str.indexOf("(");
				if(startIdx<0){
					throw new Error("line code format error: "+ str);
				} else {
					lastIdx=str.indexOf(")");
				}
			}
			// if it has "|", tokenizer by "|", otherwise it is only one phase data, need to check <nphases>
			String dataStr = str.substring(startIdx+1, lastIdx).trim();

			String[] cDataStr = null;
			if (dataStr.contains("|")){
				 cDataStr = dataStr.split("\\|");

					int idx = 0;
        			for(int i = 0; i< cDataStr.length;i++){
        				if(i==0){
        				   cMatrixData[idx] = Double.valueOf(cDataStr[i]);
        				   idx +=1;
        				}
        				else if(i==1){
        					String[] cDataStr2 = cDataStr[i].trim().split("\\s+");
        					for (String element : cDataStr2) {
        						cMatrixData[idx] = Double.valueOf(element);
        								 idx +=1;
        					}
        				}
        				else if(i==2){
        					String[] cDataStr3 = cDataStr[i].trim().split("\\s+");
        					for (String element : cDataStr3) {
        						cMatrixData[idx] = Double.valueOf(element);
        						idx +=1;
        					}

        				}

        			}
			}
			else{ // only one data input
				//cDataStr[0]= dataStr;
				cMatrixData[0] = Double.valueOf(dataStr);
			}




        	//since the Zmatrix will be created when processing the rmatrix, so here just update the imaginary part of the Zmatrix
			Complex3x3 yMatrix = new Complex3x3();


			if(nphases >=1){
				yMatrix.aa = new Complex (0.0, cMatrixData[0]);
			}
			if(nphases>=2){

				yMatrix.ab = new Complex (0.0, cMatrixData[1]);
				yMatrix.ba = new Complex (0.0, cMatrixData[1]);
				yMatrix.bb = new Complex (0.0, cMatrixData[2]);

			}
			if(nphases==3){
				yMatrix.ac = new Complex (0.0, cMatrixData[3]);
				yMatrix.ca = new Complex (0.0, cMatrixData[3]);

				yMatrix.bc = new Complex (0.0, cMatrixData[4]);
				yMatrix.cb = new Complex (0.0, cMatrixData[4]);

				yMatrix.cc = new Complex (0.0, cMatrixData[5]);

			}
			else if(nphases>3){
				throw new Exception("nphases > 3 not supported yet");
			}

			lineConfig.setShuntY3x3Matrix(yMatrix);

		}
        else if( str.contains("units")){
        	//TODO
        }


		return lineConfig;



	}

}
