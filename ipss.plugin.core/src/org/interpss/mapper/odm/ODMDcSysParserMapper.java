/*
 * @(#)ODMDcNetParserMapper.java   
 *
 * Copyright (C) 2008 www.interpss.org
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
 * @Date 11/15/2010
 * 
 *   Revision History
 *   ================
 *
 */

package org.interpss.mapper.odm;

import static com.interpss.common.util.IpssLogger.ipssLogger;

import org.ieee.odm.model.dc.DcSystemModelParser;
import org.ieee.odm.schema.DcNetworkXmlType;
import org.ieee.odm.schema.NetworkCategoryEnumType;
import org.interpss.mapper.odm.AbstractODMNetDataMapper;

import com.interpss.DcSysObjectFactory;
import com.interpss.dc.DcNetwork;
import com.interpss.dc.common.IpssDcSysException;

public class ODMDcSysParserMapper extends AbstractODMNetDataMapper<DcSystemModelParser, DcNetwork> {
	public ODMDcSysParserMapper() {
	}
	
	/**
	 * transfer info stored in the parser object into simuCtx object
	 * 
	 * @param p an ODM parser object, representing an ODM xml file
	 * @return DcNetwork object
	 */
	@Override
	public DcNetwork map2Model(DcSystemModelParser p) throws IpssDcSysException {
		
//		if (!License.getInstance().isValid()) {
//			throw new IpssDcSysException("Invalid license");
//		}
		
		DcNetwork dcNet = DcSysObjectFactory.createDcNetwork();
		if (map2Model(p, dcNet))
			return dcNet;
		else
			throw new IpssDcSysException("Error - map ODM model to create DcNetwork object");
	}
	
	/**
	 * transfer info stored in the parser object into simuCtx object
	 * 
	 * @param p an ODM parser object, representing an ODM xml file
	 * @param dcNet
	 * @return
	 */
	@Override
	public boolean map2Model(DcSystemModelParser parser, DcNetwork dcNet) {
		boolean noError = true;
		
		if (parser.getStudyCase().getNetworkCategory() == NetworkCategoryEnumType.DC_SYSTEM ) {
			DcNetworkXmlType xmlNet = parser.getDcNet();
			noError = new ODMDcSysNetMapper().map2Model(xmlNet, dcNet);
		} else {
			ipssLogger.severe("Error: wrong network category type for DC system analysis");
			return false;
		}
		
		return noError;
	}
}