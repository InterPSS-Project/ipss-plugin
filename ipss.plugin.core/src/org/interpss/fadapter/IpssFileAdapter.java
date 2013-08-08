 /*
  * @(#)IpssFileAdapter.java   
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

package org.interpss.fadapter;

import org.ieee.odm.model.IODMModelParser;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.simu.SimuContext;

public interface IpssFileAdapter extends IpssCustomAdapter {
	public static final String
		BPA_DStab_LF_ext    = "bpa_lf",
		BPA_DStab_Dstab_ext = "bpa_dstab";
	
	public static enum FileFormat { 
			IEEECDF, PSSE, GE_PSLF, 
			UCTE, IEEE_ODM, BPA, PWD,
			IpssInternal, Custom };
	public static enum Version { 
			NotDefined, 
			PSSE_30, PSSE_29, PSSE_26 };
	
	/**
	 * get the file extension of the adapter
	 * 
	 * @return
	 */
	String getExtension();
	
	/**
	 * get the file DStab extension of the adapter
	 * 
	 * @return
	 */
	String getExtensionDstab();

	/**
	 * get the file filter string for the adapter
	 * 
	 * @return
	 */
	String getFileFilterString();
	
	/**
	 * get the adapter version list
	 * 
	 * @return
	 */
	String[] getVersionList();

	/**
	 * get the user selected version string
	 * 
	 * @return
	 */
	String getVersionSelected();
	
	/**
	 * set the user selected version string
	 * 
	 * @return
	 */
	void setVersionSelected(String v);

	/**
	 * Load the file into the a SimuNetwork object
	 *  
	 */
	void load(SimuContext simuCtx, String filepath, boolean debug, String outfile) throws InterpssException;

	/**
	 * Load the files into the a SimuNetwork object
	 *  
	 */
	void load(SimuContext simuCtx, String[] filepathAry, boolean debug, String outfile) throws InterpssException;

	/**
	 * Load the file and create a SimuNetwork object
	 *  
	 * @param filepath 
	 * @return a SimuNetwork object
	 */
	SimuContext load(String filepath) throws InterpssException;

	/**
	 * Load the file and create a SimuNetwork object
	 *  
	 * @param filepath 
	 * @return a SimuNetwork object
	 */
	SimuContext loadDebug(String filepath) throws InterpssException;
	
	/**
	 * Load the file and create a SimuNetwork object
	 *  
	 * @param filepath 
	 * @param outfile 
	 * @return a SimuNetwork object
	 */
	SimuContext loadDebug(String filepath, String outfile) throws InterpssException;

	/**
	 * Load the file and create a SimuNetwork object
	 *  
	 * @return a SimuNetwork object
	 */
	AclfNetwork loadAclfNet(String filepath) throws InterpssException;
	
	/**
     * No need to be implemented if you do not write simulaiton results back to a datafile
	 * 
	 * @see com.interpss.io.adapter.IFileAdapter#save(java.lang.String, com.interpss.core.simu.SimuContext)
	 */
	boolean save(String filepath, SimuContext net) throws InterpssException;
	
	/**
	 * get the IEEE ODMModelParser, if ODM is used for import data
	 * 
	 * @return
	 */
	IODMModelParser getODMModelParser();
}