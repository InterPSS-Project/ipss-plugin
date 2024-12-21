package org.interpss.threePhase.dataParser.opendss;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;

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
	      	int baseFreq = 60;

			String[] lineData = null;
			String code_id ="";
			String nphaseStr ="";
			String baseFreqStr ="";
			LineConfiguration lineConfig = null;

			double[] rMatrixData = new double[6];
			double[] xMatrixData = new double[6];
			double[] cMatrixData = new double[6];
	    	do {
	          	str = reader.readLine();
	        	if (str != null && !str.trim().equals("")) {
	        		str = str.trim();
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


	        	}


	        } while(str != null);


			return true;
		} catch (Exception e) {
			ODMLogger.getLogger().severe(e.toString());

			e.printStackTrace();
			return false;
		}

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
