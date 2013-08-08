 /*
  * @(#)IpssFileAdapterBase.java   
  *
  * Copyright (C) 2006-2007 www.interpss.org
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
  * @Date 09/15/2007
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.fadapter.impl;

import static com.interpss.common.util.IpssLogger.ipssLogger;

import java.io.File;

import org.ieee.odm.ODMFileFormatEnum;
import org.ieee.odm.ODMObjectFactory;
import org.ieee.odm.adapter.IODMAdapter;
import org.ieee.odm.common.ODMException;
import org.ieee.odm.model.IODMModelParser;
import org.ieee.odm.model.aclf.AclfModelParser;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.mapper.odm.ODMAclfNetMapper;
import org.interpss.spring.CorePluginSpringFactory;

import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.exp.InterpssRuntimeException;
import com.interpss.common.msg.IPSSMsgHub;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

public class IpssFileAdapterBase implements IpssFileAdapter {
	protected IPSSMsgHub msgHub;
	private ODMFileFormatEnum format;
	private ODMAclfNetMapper.XfrBranchModel xfrBranchModel = ODMAclfNetMapper.XfrBranchModel.InterPSS;

	private String name;
	private String[] versionList = null;
	private String extension;
	private String extensionDstab;
	private String description;
	private String fileFilterString;
	private String versionSelected;
	
	protected IODMModelParser parser;
	
	public IpssFileAdapterBase(IPSSMsgHub msgHub) {
		this.msgHub = msgHub;
	}
	
	public IpssFileAdapterBase(IPSSMsgHub msgHub, ODMFileFormatEnum format) {
		this.msgHub = msgHub;
		this.format = format;
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
	public void load(final SimuContext simuCtx, final String filepath, boolean debug, String outfile) throws InterpssException {
		try {
			IODMAdapter adapter = ODMObjectFactory.createODMAdapter(this.format);
			loadByODMTransformation(adapter, simuCtx, filepath, msgHub, debug, outfile);
		} catch (ODMException e) {
			ipssLogger.severe(e.toString());
			throw new InterpssException("Error while loading custom file through ODM, " + e.toString());
		}
 	}
	
	protected void loadByODMTransformation(final IODMAdapter adapter, final SimuContext simuCtx, final String filepath, 
						final IPSSMsgHub msg, boolean debug, String outfile)  throws InterpssException {		
		adapter.parseInputFile(filepath);
		this.parser = adapter.getModel();
		if (debug)
			System.out.println(adapter.getModel().toXmlDoc(outfile));
		
		if (CorePluginSpringFactory.getOdm2AclfParserMapper(this.xfrBranchModel.InterPSS)
					.map2Model((AclfModelParser)adapter.getModel(), simuCtx)) {
  	  		simuCtx.setName(filepath.substring(filepath.lastIndexOf(File.separatorChar)+1));
  	  		simuCtx.setDesc("This project is created by input file " + filepath);
		}
		else {
  			msg.sendErrorMsg("Error to load file: " + filepath);
  			ipssLogger.severe("Error to load file: " + filepath);
		}
	}
	
	@Override
	public IODMModelParser getODMModelParser() {
		return this.parser;
	}

	/**
	 * @return the fileFilterString
	 */
	public String getFileFilterString() {
		return fileFilterString;
	}

	/**
	 * @param fileFilterString the fileFilterString to set
	 */
	public void setFileFilterString(String fileFilterString) {
		this.fileFilterString = fileFilterString;
	}

	/**
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param name the name to set
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * @return the versionList
	 */
	public String[] getVersionList() {
		return versionList;
	}

	/**
	 * @param versionList the versionList to set
	 */
	public void setVersionList(String[] versionList) {
		this.versionList = versionList;
	}

	public String getExtension() {
		return extension;
	}
	
	public void setExtension(String s) {
		extension = s;
	}

	public String getExtensionDstab() {
		return extensionDstab;
	}
	
	public void setExtensionDstab(String s) {
		extensionDstab = s;
	}

	public String getDescription() {
		return description;
	}
	
	public void setDescription(String s) {
		description = s;
	}

	public void load(SimuContext simuCtx, String[] filepathAry, boolean debug, String outfile) throws InterpssException {
		throw new InterpssRuntimeException("Load() need to implemented");
	}

	public SimuContext load(String filepath) throws InterpssException {
  		final SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.NOT_DEFINED);
  		load(simuCtx, filepath, false, null);
  		return simuCtx;
	}

	public SimuContext loadDebug(String filepath) throws InterpssException {
  		final SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.NOT_DEFINED);
  		load(simuCtx, filepath, true, null);
  		return simuCtx;
	}

	public SimuContext loadDebug(String filepath, String outfile) throws InterpssException {
  		final SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.NOT_DEFINED);
  		load(simuCtx, filepath, true, outfile);
  		return simuCtx;
	}

	public AclfNetwork loadAclfNet(String filepath) throws InterpssException {
		return load(filepath).getAclfNet();
	}

	public boolean save(String filepath, SimuContext net) throws InterpssException {
		throw new InterpssRuntimeException("Save need to implemented");
	}

	/**
	 * @return the versionSelected
	 */
	public String getVersionSelected() {
		return versionSelected;
	}

	/**
	 * @param versionSelected the versionSelected to set
	 */
	public void setVersionSelected(String versionSelected) {
		this.versionSelected = versionSelected;
		
		/*
		 * Please note : the following is implementation specific for ipss editor 
		 */
		if (versionSelected.equals("PSS/E-26"))
			this.format = ODMFileFormatEnum.PsseV26;
		else if (versionSelected.equals("PSS/E-30") && versionSelected.equals("PSS/E-29"))
			this.format = ODMFileFormatEnum.PsseV30;
	}
}