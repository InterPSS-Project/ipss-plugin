/*
 * @(#)PluginObjectFactory.java   
 *
 * Copyright (C) 2006-2010 www.interpss.org
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
 * @Date 12/04/2010
 * 
 *   Revision History
 *   ================
 *
 */
package org.interpss;

import org.interpss.fadapter.BPAFormat;
import org.interpss.fadapter.GEFormat;
import org.interpss.fadapter.IeeeCDFFormat;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.fadapter.IpssInternalFormat;
import org.interpss.fadapter.PTIFormat;
import org.interpss.fadapter.PWDFormat;
import org.interpss.fadapter.UCTEFormat;
import org.interpss.spring.CorePluginSpringFactory;

import com.interpss.common.exp.InterpssException;

/**
 * Core plugin object factory
 * 
 * @author mzhou
 *
 */
public class CorePluginObjFactory {
	/**
	 * get input file adapter for the file format
	 * 
	 * @param f
	 * @return
	 * @throws InterpssException
	 */
	public static IpssFileAdapter getFileAdapter(IpssFileAdapter.FileFormat f) throws InterpssException {
		return getFileAdapter(f, IpssFileAdapter.Version.NotDefined);
	}
	
	/**
	 * get input file adapter for the file format
	 * 
	 * @param f
	 * @param v
	 * @return
	 * @throws InterpssException
	 */
	public static IpssFileAdapter getFileAdapter(IpssFileAdapter.FileFormat f, IpssFileAdapter.Version v)
					throws InterpssException {
		if (f == IpssFileAdapter.FileFormat.IEEECDF) {
			return new IeeeCDFFormat(CorePluginSpringFactory.getIpssMsgHub());
		}
		else if (f == IpssFileAdapter.FileFormat.GE_PSLF) {
			return new GEFormat(CorePluginSpringFactory.getIpssMsgHub());
		} 
		else if (f == IpssFileAdapter.FileFormat.PSSE) {
			return new PTIFormat(v, CorePluginSpringFactory.getIpssMsgHub());
		} 
		else if (f == IpssFileAdapter.FileFormat.BPA) {
			return new BPAFormat(CorePluginSpringFactory.getIpssMsgHub());
		} 
		else if (f == IpssFileAdapter.FileFormat.PWD) {
			return new PWDFormat(CorePluginSpringFactory.getIpssMsgHub());
		} 
		else if (f == IpssFileAdapter.FileFormat.UCTE) {
			return new UCTEFormat(CorePluginSpringFactory.getIpssMsgHub());
		} 
		else if (f == IpssFileAdapter.FileFormat.IpssInternal) {
			return new IpssInternalFormat(CorePluginSpringFactory.getIpssMsgHub());
		} 
		throw new InterpssException("Error - File adapter format/version not implemented");
	}
}
