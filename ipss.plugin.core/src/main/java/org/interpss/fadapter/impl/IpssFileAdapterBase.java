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

import org.interpss.fadapter.IpssFileAdapter;

import com.interpss.common.exp.InterpssException;
import com.interpss.common.exp.InterpssRuntimeException;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import com.interpss.simu.SimuObjectFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IpssFileAdapterBase implements IpssFileAdapter {
    private static final Logger log = LoggerFactory.getLogger(IpssFileAdapterBase.class);

	private String name;
	private String[] versionList = null;
	private String extension;
	private String extensionDstab;
	private String description;
	private String fileFilterString;
	private String versionSelected;

	@Override
	public void load(final SimuContext simuCtx, final String filepath, boolean debug, String outfile) throws InterpssException {
		throw new InterpssException("load() must be implemented by subclass: " + getClass().getName());
 	}

	/**
	 * @return the fileFilterString
	 */
	@Override
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
	@Override
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
	@Override
	public String[] getVersionList() {
		return versionList;
	}

	/**
	 * @param versionList the versionList to set
	 */
	public void setVersionList(String[] versionList) {
		this.versionList = versionList;
	}

	@Override
	public String getExtension() {
		return extension;
	}
	
	public void setExtension(String s) {
		extension = s;
	}

	@Override
	public String getExtensionDstab() {
		return extensionDstab;
	}
	
	public void setExtensionDstab(String s) {
		extensionDstab = s;
	}

	@Override
	public String getDescription() {
		return description;
	}
	
	public void setDescription(String s) {
		description = s;
	}

	@Override
	public void load(SimuContext simuCtx, String[] filepathAry, boolean debug, String outfile) throws InterpssException {
		throw new InterpssRuntimeException("Load() need to implemented");
	}

	@Override
	public SimuContext load(String filepath) throws InterpssException {
  		final SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.NOT_DEFINED);
  		load(simuCtx, filepath, false, null);
  		return simuCtx;
	}

	@Override
	public SimuContext loadDebug(String filepath) throws InterpssException {
  		final SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.NOT_DEFINED);
  		load(simuCtx, filepath, true, null);
  		return simuCtx;
	}

	@Override
	public SimuContext loadDebug(String filepath, String outfile) throws InterpssException {
  		final SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.NOT_DEFINED);
  		load(simuCtx, filepath, true, outfile);
  		return simuCtx;
	}

	@Override
	public AclfNetwork loadAclfNet(String filepath) throws InterpssException {
		return load(filepath).getAclfNet();
	}

	@Override
	public boolean save(String filepath, SimuContext net) throws InterpssException {
		throw new InterpssRuntimeException("Save need to implemented");
	}

	/**
	 * @return the versionSelected
	 */
	@Override
	public String getVersionSelected() {
		return versionSelected;
	}

	/**
	 * @param versionSelected the versionSelected to set
	 */
	@Override
	public void setVersionSelected(String versionSelected) {
		this.versionSelected = versionSelected;
	}
}