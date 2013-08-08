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

import static com.interpss.common.util.IpssLogger.ipssLogger;

import java.io.File;

import org.ieee.odm.ODMFileFormatEnum;
import org.ieee.odm.ODMObjectFactory;
import org.ieee.odm.adapter.IODMAdapter;
import org.ieee.odm.common.ODMException;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.fadapter.impl.IpssFileAdapterBase;
import org.interpss.spring.CorePluginSpringFactory;

import com.interpss.common.exp.InterpssException;
import com.interpss.common.msg.IPSSMsgHub;
import com.interpss.simu.SimuContext;

public class BPAFormat extends IpssFileAdapterBase {
	public BPAFormat(IPSSMsgHub msgHub) {
		super(msgHub, ODMFileFormatEnum.BPA);
	}
	
	@Override
	public void load(final SimuContext simuCtx, final String[] filepathAry, boolean debug, String outfile) throws InterpssException {
		try {
			IODMAdapter adapter = ODMObjectFactory.createODMAdapter(ODMFileFormatEnum.BPA);
			adapter.parseInputFile(IODMAdapter.NetType.DStabNet, filepathAry);
			this.parser = adapter.getModel();
			if (debug)
				System.out.println(adapter.getModel().toXmlDoc(outfile));

			String filepath = filepathAry[0];
			if (CorePluginSpringFactory.getOdm2DStabMapper().map2Model((DStabModelParser)adapter.getModel(), simuCtx)) {
	  	  		simuCtx.setName(filepath.substring(filepath.lastIndexOf(File.separatorChar)+1));
	  	  		simuCtx.setDesc("This project is created by input file " + filepath);
			}
			else {
				msgHub.sendErrorMsg("Error to load file: " + filepath);
	  			ipssLogger.severe("Error to load file: " + filepath);
			}		
		} catch (ODMException e) {
			ipssLogger.severe(e.toString());
			throw new InterpssException("Error while loading custom file through ODM, " + e.toString());
		}
 	}
}
