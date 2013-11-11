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

package org.interpss.spring;

import org.interpss.mapper.odm.ODMAclfNetMapper;
import org.interpss.mapper.odm.ODMAclfParserMapper;
import org.interpss.mapper.odm.ODMAcscParserMapper;
import org.interpss.mapper.odm.ODMDStabParserMapper;
import org.interpss.mapper.odm.ODMOpfParserMapper;

import com.interpss.dc.mapper.ODMDcSysNetMapper;
import com.interpss.dc.mapper.ODMDcSysParserMapper;
import com.interpss.dist.mapper.ODMDistNetMapper;
import com.interpss.dist.mapper.ODMDistParserMapper;
import com.interpss.spring.CoreSimuSpringFactory;

/**
 * Core plugin spring object factory
 * 
 * @author mzhou
 *
 */
public class CorePluginSpringFactory extends CoreSimuSpringFactory {
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
		ODMAclfParserMapper mapper = (ODMAclfParserMapper) springAppCtx.getBean("odm2AclfParserMapper");
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
		ODMAclfNetMapper mapper = (ODMAclfNetMapper) springAppCtx.getBean("odm2AclfNetMapper");
		mapper.setXfrBranchModel(xfrBranchModel);
		return mapper;
	}	

	/**
	 * create a ODMAcscDataMapper object from the Spring container
	 */
	public static ODMAcscParserMapper getOdm2AcscParserMapper() {
		return (ODMAcscParserMapper) springAppCtx.getBean("odm2AcscParserMapper");
	}	
	
	/**
	 * create a ODMDStabDataMapper object from the Spring container
	 */
	public static ODMDStabParserMapper getOdm2DStabParserMapper() {
		return (ODMDStabParserMapper) springAppCtx.getBean("odm2DStabParserMapper");
	}	
	
	/**
	 * create a ODMDistParserMapper object from the Spring container
	 */
	public static ODMDistParserMapper getOdm2DistParserMapper() {
		return (ODMDistParserMapper) springAppCtx.getBean("odm2DistParserMapper");
	}	

	/**
	 * create a ODMDistNetMapper object from the Spring container
	 */	
	public static ODMDistNetMapper getOdm2DistNetMapper() {
		return (ODMDistNetMapper) springAppCtx.getBean("odm2DistNetMapper");
	}	
	
	/**
	 * create a ODMDcSysParserMapper object from the Spring container
	 */	
	public static ODMDcSysParserMapper getOdm2DcSysParserMapper() {
		return (ODMDcSysParserMapper) springAppCtx.getBean("odm2DcSysParserMapper");
	}		

	/**
	 * create a ODMDcSysNetMapper object from the Spring container
	 */	
	public static ODMDcSysNetMapper getOdm2DcSysNetMapper() {
		return (ODMDcSysNetMapper) springAppCtx.getBean("odm2DcSysNetMapper");
	}		

	/**
	 * create a ODMOpfParserMapper object from the Spring container
	 */
	public static ODMOpfParserMapper getOdm2OpfParserMapper() {
		return (ODMOpfParserMapper) springAppCtx.getBean("odm2OpfParserMapper");
	}		
}
