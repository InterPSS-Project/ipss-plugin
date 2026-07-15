/*
  * @(#)PTIFormat.java   
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

import java.io.File;

import org.interpss.fadapter.impl.IpssFileAdapterBase;
import org.interpss.fadapter.psse.PSSEDirectParser;

import com.interpss.common.exp.InterpssException;
import com.interpss.common.msg.IPSSMsgHub;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import com.interpss.simu.SimuObjectFactory;

public class PTIFormat extends IpssFileAdapterBase {
	private int psseVersion = 30;

	public PTIFormat(IPSSMsgHub msgHub) {
		super(msgHub);
		this.psseVersion = 30;
	}

	public PTIFormat(IpssFileAdapter.Version v, IPSSMsgHub msgHub) {
		super(msgHub);
		this.psseVersion = mapVersionToInt(v);
	}
	
	@Override
	public void load(final SimuContext simuCtx, final String filepath, boolean debug, String outfile) throws InterpssException {
		PSSEDirectParser parser = new PSSEDirectParser(psseVersion);
		AclfNetwork aclfNet = parser.parse(filepath);
		simuCtx.setNetType(SimuCtxType.ACLF_NETWORK);
		simuCtx.setAclfNet(aclfNet);
		simuCtx.setName(filepath.substring(filepath.lastIndexOf(File.separatorChar) + 1));
		simuCtx.setDesc("This project is created by input file " + filepath);
	}

	@Override
	public AclfNetwork loadAclfNet(String filepath) throws InterpssException {
		PSSEDirectParser parser = new PSSEDirectParser(psseVersion);
		return parser.parse(filepath);
	}

	private static int mapVersionToInt(IpssFileAdapter.Version v) {
		switch(v) {
			case PSSE_26: return 26;
			case PSSE_29: return 29;
			case PSSE_30: return 30;
			case PSSE_31: return 31;
			case PSSE_32: return 32;
			case PSSE_33: return 33;
			case PSSE_34: return 34;
			case PSSE_35: return 35;
			case PSSE_36: return 36;
			default: return 30;
		}
	}
}