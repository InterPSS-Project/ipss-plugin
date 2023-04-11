/*
 * @(#)CorePluginSpringCtx.java   
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

package org.interpss;

import org.interpss.fadapter.BPAFormat;
import org.interpss.fadapter.GEFormat;
import org.interpss.fadapter.IeeeCDFFormat;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.fadapter.IpssInternalFormat;
import org.interpss.fadapter.PTIFormat;
import org.interpss.fadapter.PWDFormat;
import org.interpss.fadapter.UCTEFormat;
import org.interpss.mapper.odm.ODMAclfNetMapper;
import org.interpss.mapper.odm.ODMAclfParserMapper;
import org.interpss.mapper.odm.ODMAcscParserMapper;
import org.interpss.mapper.odm.ODMDStabParserMapper;
import org.interpss.mapper.odm.ODMDcSysNetMapper;
import org.interpss.mapper.odm.ODMDcSysParserMapper;
import org.interpss.mapper.odm.ODMDistNetMapper;
import org.interpss.mapper.odm.ODMDistParserMapper;
import org.interpss.mapper.odm.ODMOpfParserMapper;

import com.interpss.common.CoreCommonFactory;
import com.interpss.common.exp.InterpssException;

/**
 * Core plugin spring object factory
 * 
 * @author mzhou
 *
 */
public class CorePluginFactory extends CoreCommonFactory {
	/*
	 * 		Mapper definition Odm -> SimuCtx
	 * 		================================
	 */
	/**
	 * create a ODMAclfMapper object from the Spring container
	 * 
	 * @param xfrBranchModel
	 */
	public static ODMAclfParserMapper getOdm2AclfParserMapper(ODMAclfNetMapper.XfrBranchModel xfrBranchModel) {
		ODMAclfParserMapper mapper = new ODMAclfParserMapper();
		mapper.setXfrBranchModel(xfrBranchModel);
		return mapper;
	}	

	/**
	 * create a ODMAclfNetMapper object from the Spring container
	 * 
	 * @param xfrBranchModel
	 * @return
	 */
	public static ODMAclfNetMapper getOdm2AclfNetMapper(ODMAclfNetMapper.XfrBranchModel xfrBranchModel) {
		ODMAclfNetMapper mapper = new ODMAclfNetMapper();
		mapper.setXfrBranchModel(xfrBranchModel);
		return mapper;
	}	

	/**
	 * create a ODMAcscDataMapper object from the Spring container
	 */
	public static ODMAcscParserMapper getOdm2AcscParserMapper() {
		return new ODMAcscParserMapper();
	}	
	
	/**
	 * create a ODMDStabDataMapper object from the Spring container
	 */
	public static ODMDStabParserMapper getOdm2DStabParserMapper() {
		return new ODMDStabParserMapper(CoreCommonFactory.getIpssMsgHub());
	}	
	
	/**
	 * create a ODMDistParserMapper object from the Spring container
	 */
	public static ODMDistParserMapper getOdm2DistParserMapper() {
		return new ODMDistParserMapper();
	}	

	/**
	 * create a ODMDistNetMapper object from the Spring container
	 */	
	public static ODMDistNetMapper getOdm2DistNetMapper() {
		return new ODMDistNetMapper();
	}	
	
	/**
	 * create a ODMDcSysParserMapper object from the Spring container
	 */	
	public static ODMDcSysParserMapper getOdm2DcSysParserMapper() {
		return new ODMDcSysParserMapper();
	}		

	/**
	 * create a ODMDcSysNetMapper object from the Spring container
	 */	
	public static ODMDcSysNetMapper getOdm2DcSysNetMapper() {
		return new ODMDcSysNetMapper();
	}		

	/**
	 * create a ODMOpfParserMapper object from the Spring container
	 */
	public static ODMOpfParserMapper getOdm2OpfParserMapper() {
		return new ODMOpfParserMapper();
	}	
	
	/**
	 * get input file adapter for the file format
	 * 
	 * @param f
	 * @return
	 * @throws InterpssException
	 */
	public static IpssFileAdapter getFileAdapter(IpssFileAdapter.FileFormat f) throws InterpssException {
		IpssFileAdapter.Version version = 
				f == IpssFileAdapter.FileFormat.IEEECDF? IpssFileAdapter.Version.IEEECDF : 
						IpssFileAdapter.Version.NotDefined;
		return getFileAdapter(f, version);
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
			return new IeeeCDFFormat(CoreCommonFactory.getIpssMsgHub(), v);
		}
		else if (f == IpssFileAdapter.FileFormat.GE_PSLF) {
			return new GEFormat(CoreCommonFactory.getIpssMsgHub());
		} 
		else if (f == IpssFileAdapter.FileFormat.PSSE) {
			return new PTIFormat(v, CoreCommonFactory.getIpssMsgHub());
		} 
		else if (f == IpssFileAdapter.FileFormat.BPA) {
			return new BPAFormat(CoreCommonFactory.getIpssMsgHub());
		} 
		else if (f == IpssFileAdapter.FileFormat.PWD) {
			return new PWDFormat(CoreCommonFactory.getIpssMsgHub());
		} 
		else if (f == IpssFileAdapter.FileFormat.UCTE) {
			return new UCTEFormat(CoreCommonFactory.getIpssMsgHub());
		} 
		else if (f == IpssFileAdapter.FileFormat.IpssInternal) {
			return new IpssInternalFormat(CoreCommonFactory.getIpssMsgHub());
		} 
		throw new InterpssException("Error - File adapter format/version not implemented");
	}	
}
