/*
  * @(#)BPAFormat.java   
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
  * @Date 02/01/2008
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.fadapter;

import java.io.File;

import org.interpss.fadapter.bpa.BPADirectParser;
import org.interpss.fadapter.impl.IpssFileAdapterBase;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BPAFormat extends IpssFileAdapterBase {
    private static final Logger logger = LoggerFactory.getLogger(BPAFormat.class);

	@Override
	public void load(final SimuContext simuCtx, final String filepath, boolean debug, String outfile) throws InterpssException {
		BPADirectParser parser = new BPADirectParser();
		AclfNetwork aclfNet = parser.parse(filepath);
		simuCtx.setNetType(SimuCtxType.ACLF_NETWORK);
		simuCtx.setAclfNet(aclfNet);
		simuCtx.setName(filepath.substring(filepath.lastIndexOf(File.separatorChar) + 1));
		simuCtx.setDesc("This project is created by input file " + filepath);
	}

	@Override
	public void load(final SimuContext simuCtx, final String[] filepathAry, boolean debug, String outfile) throws InterpssException {
		// For single-file load, delegate to the main load method
		if (filepathAry.length == 1) {
			load(simuCtx, filepathAry[0], debug, outfile);
			return;
		}
		// Multi-file BPA (load flow + dynamics) uses the first file for load flow
		load(simuCtx, filepathAry[0], debug, outfile);
		logger.info("BPA multi-file load: only load flow data from first file is imported via direct parser");
	}

	@Override
	public AclfNetwork loadAclfNet(String filepath) throws InterpssException {
		BPADirectParser parser = new BPADirectParser();
		return parser.parse(filepath);
	}
}