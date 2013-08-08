 /*
  * @(#)IpssInternalFormat.java   
  *
  * Copyright (C) 2006 www.interpss.org
  *
  * This program is free software; you can redistribute it and/or
  * modify it under the terms of the GNU LESSER GENERAL PUBLIC LICENSE
  * as published by the Free Software Foundation; either version 2.1
  * of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  * GNU General Public License for more details.
  *
  * @Author Mike Zhou
  * @Version 1.0
  * @Date 09/15/2006
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.fadapter;

import static com.interpss.common.util.IpssLogger.ipssLogger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import org.interpss.fadapter.impl.IpssFileAdapterBase;
import org.interpss.fadapter.impl.IpssInternalFormat_in;
import org.interpss.fadapter.impl.IpssInternalFormat_out;

import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.msg.IPSSMsgHub;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

public class IpssInternalFormat extends IpssFileAdapterBase {
	public IpssInternalFormat(IPSSMsgHub msgHub) {
		super(msgHub);
	}	

	/**
	 * Load the data in the data file, specified by the filepath, into the SimuContext object. An AclfAdjNetwork
	 * object will be created to hold the data for loadflow analysis.
	 * 
	 * @param simuCtx the SimuContext object
	 * @param filepath full path path of the input file
	 * @param msg the SessionMsg object
	 */
	@Override
	public void load(final SimuContext simuCtx, final String filepath, boolean debug, String outfile) throws InterpssException{
		try {
			final File file = new File(filepath);
			final InputStream stream = new FileInputStream(file);
			final BufferedReader din = new BufferedReader(new InputStreamReader(stream));
			
			// load the loadflow data into the AclfAdjNetwork object
			final AclfNetwork adjNet = IpssInternalFormat_in.loadFile(din, msgHub);
	  		// System.out.println(adjNet.net2String());

			// set the simuContext object
	  		simuCtx.setNetType(SimuCtxType.ACLF_NETWORK);
	  		simuCtx.setAclfNet(adjNet);
	  		simuCtx.setName(filepath.substring(filepath.lastIndexOf(File.separatorChar)+1));
	  		simuCtx.setDesc("This project is created by input file " + filepath);
		} catch (Exception e) {
			ipssLogger.severe(e.toString());
			throw new InterpssException(e.toString());
		}
	}
	
	/**
	 * Create a SimuContext object and Load the data in the data file, specified by the filepath, into the object. 
	 * An AclfAdjNetwork object will be created to hold the data for loadflow analysis.
	 * 
	 * @param filepath full path path of the input file
	 * @param msg the SessionMsg object
	 * @return the created SimuContext object.
	 */
	@Override
	public SimuContext load(final String filepath) throws InterpssException{
  		final SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.NOT_DEFINED);
  		load(simuCtx, filepath, false, null);
  		return simuCtx;
	}
	
	/**
	 * This method is currently not implemented, since the loadflow results are not going to write
	 * back to a data file.
	 */
	@Override
	public boolean save(final String filepath, final SimuContext simuCtx) throws InterpssException {
		try {
	        final File file = new File(filepath);
	        final OutputStream stream = new FileOutputStream(file);
	        final BufferedWriter out = new BufferedWriter(new OutputStreamWriter(stream));

	        boolean r = IpssInternalFormat_out.save(out, simuCtx, msgHub);
	        
	        out.flush();
	        out.close();
	        return r;
		} catch (Exception e) {
			ipssLogger.severe(e.toString());
			throw new InterpssException(e.toString());
		}
   }
}