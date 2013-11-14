/*
 * @(#)ODMDistNetDataMapper.java   
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
 * @Date 02/15/2011
 * 
 *   Revision History
 *   ================
 *
 */
package org.interpss.mapper.odm.impl.dist;

import static com.interpss.common.util.IpssLogger.ipssLogger;

import org.ieee.odm.model.dist.DistModelParser;
import org.ieee.odm.schema.DistributionNetXmlType;
import org.ieee.odm.schema.NetworkCategoryEnumType;
import org.interpss.mapper.odm.AbstractODMNetDataMapper;
import org.interpss.mapper.odm.ODMDistNetMapper;
import org.interpss.mapper.odm.impl.mnet.MultiNetDistHelper;

import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

public abstract class AbstractODMDistParserMapper<T> extends AbstractODMNetDataMapper<T, SimuContext> {
	public AbstractODMDistParserMapper() {
	}
	
	/**
	 * transfer info stored in the parser object into the distNet object
	 * 
	 * @param p an ODM parser object, representing an ODM xml file
	 * @return DistNetwork object
	 */
	@Override
	public SimuContext map2Model(T p) throws InterpssException {
		final SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DISTRIBUTE_NET);
		if (map2Model(p, simuCtx))
			return simuCtx;
		else
			throw new InterpssException("Error - map ODM model to create DistNetwork object");
	}
	
	/**
	 * transfer info stored in the parser object into this distNet object
	 * 
	 * @param p an ODM parser object, representing an ODM xml file
	 * @param distNet
	 * @return
	 */
	@Override
	public boolean map2Model(T p, SimuContext simuCtx) {
		boolean noError = true;
		
		DistModelParser parser = (DistModelParser)p;
		if (parser.getStudyCase().getNetworkCategory() == NetworkCategoryEnumType.DISTRIBUTION) {
			DistributionNetXmlType xmlNet = parser.getDistNet();
			noError = new ODMDistNetMapper().map2Model(xmlNet, simuCtx.getDistNet());
			
			/*
			 * a parent dist net may contain DcSys child network(s) 
			 */
			
			if (xmlNet.isHasChildNet() != null && xmlNet.isHasChildNet()) {
				if (!new MultiNetDistHelper(simuCtx.getDistNet()).mapChildNet(xmlNet.getChildNetDef()))
					noError = false;
			}
		} else {
			ipssLogger.severe("Error: wrong network type, " + parser.getStudyCase().getNetworkCategory());
			return false;
		}
		
		return noError;
	}	
}