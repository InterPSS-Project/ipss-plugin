 /*
  * @(#)IeeeCommonFormat.java   
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

import org.ieee.odm.ODMFileFormatEnum;
import org.interpss.fadapter.impl.IpssFileAdapterBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.common.exp.InterpssException;
import com.interpss.common.msg.IPSSMsgHub;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoad;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.simu.SimuContext;

/**
 *  Custom input file adapter for IEEE Common Format. It loads a data file in the format and create an
 *  AclfAdjNetwork object. The data fields could be positional or separeted by comma  
 */

public class IeeeCDFFormat extends IpssFileAdapterBase {
    private static final Logger log = LoggerFactory.getLogger(IeeeCDFFormat.class);
	
	public IeeeCDFFormat(IPSSMsgHub msgHub) {
		super(msgHub, ODMFileFormatEnum.IeeeCDF);
	}
	
	public IeeeCDFFormat(IPSSMsgHub msgHub, IpssFileAdapter.Version v) {
		super(msgHub, v == IpssFileAdapter.Version.IEEECDF? ODMFileFormatEnum.IeeeCDF : ODMFileFormatEnum.IeeeCDFExt1);
	}
	
	@Override
	public void load(final SimuContext simuCtx, final String filepath, boolean debug, String outfile) throws InterpssException {
		super.load(simuCtx, filepath, debug, outfile);	
		log.debug("IEEECDF Format file " + filepath + " loaded successfully.");
		
		// since the IEEE CDF format is not a contributionGen/Load model, we need to 
		// remove the empty contributionGen/Load objects 
		simuCtx.getAclfNet().getBusList().forEach(bus -> {
			if (bus.getContributeGenList().size() > 0) {
				AclfGen gen = bus.getContributeGenList().get(0);
				if (bus.getGenCode() == AclfGenCode.GEN_PQ) {
					if (gen.getGen().abs() == 0.0) {
						bus.getContributeGenList().remove(0);
						log.debug("Removed empty contributionGen " + gen.getId());
					}
				}
			}
			
			if (bus.getContributeLoadList().size() > 0) {
				AclfLoad load = bus.getContributeLoadList().get(0);
				if (load.getCode() == AclfLoadCode.CONST_P) {
					if (load.getLoadCP().abs() == 0.0) {
						bus.getContributeLoadList().remove(0);
						log.debug("Removed empty contributionLoad " + load.getId());
					}
				}				
			}
		});
	}
}