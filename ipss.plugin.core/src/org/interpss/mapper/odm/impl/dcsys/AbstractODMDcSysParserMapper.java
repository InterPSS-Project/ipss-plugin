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

package org.interpss.mapper.odm.impl.dcsys;

import static com.interpss.common.util.IpssLogger.ipssLogger;

import org.ieee.odm.model.dc.DcSystemModelParser;
import org.ieee.odm.schema.DcNetworkXmlType;
import org.ieee.odm.schema.NetworkCategoryEnumType;
import org.interpss.mapper.odm.AbstractODMNetDataMapper;
import org.interpss.mapper.odm.ODMDcSysNetMapper;

import com.interpss.dc.common.IpssDcSysException;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import com.interpss.simu.SimuObjectFactory;

public abstract class AbstractODMDcSysParserMapper<T> extends AbstractODMNetDataMapper<T, SimuContext> {
	public AbstractODMDcSysParserMapper() {
	}
	
	/**
	 * transfer info stored in the parser object into simuCtx object
	 * 
	 * @param p an ODM parser object, representing an ODM xml file
	 * @return DcNetwork object
	 */
	@Override
	public SimuContext map2Model(T p) throws IpssDcSysException {
		
//		if (!License.getInstance().isValid()) {
//			throw new IpssDcSysException("Invalid license");
//		}
		
		final SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DC_SYSTEM_NET);
		//DcNetwork dcNet = DcSysObjectFactory.createDcNetwork();
		if (map2Model(p, simuCtx))
			return simuCtx;
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
	public boolean map2Model(T from, SimuContext simuCtx) {
		DcSystemModelParser parser = (DcSystemModelParser)from;
		boolean noError = true;
		
		if (parser.getStudyCase().getNetworkCategory() == NetworkCategoryEnumType.DC_SYSTEM ) {
			DcNetworkXmlType xmlNet = parser.getDcNet();
			noError = new ODMDcSysNetMapper().map2Model(xmlNet, simuCtx.getDcSysNet());
			
			/*
			 * a parent dc sys net cannot contain any child network 
			 */
		} else {
			ipssLogger.severe("Error: wrong network category type for DC system analysis");
			return false;
		}
		
		return noError;
	}
}