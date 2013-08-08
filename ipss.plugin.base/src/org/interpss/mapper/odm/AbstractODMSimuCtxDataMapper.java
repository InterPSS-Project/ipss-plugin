/*
 * @(#)AbstractODMSimuCtxDataMapper.java   
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
 * @Date 02/15/2008
 * 
 *   Revision History
 *   ================
 *
 */

package org.interpss.mapper.odm;

import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

/**
 * Base class for implementing mapper from ODM model to SimuContext object 
 * 
 * @author mzhou
 *
 * @param <Tfrom> a ODM parser object
 */

public abstract class AbstractODMSimuCtxDataMapper<Tfrom> extends AbstractODMNetDataMapper<Tfrom, SimuContext> {
	/**
	 * constructor
	 */
	public AbstractODMSimuCtxDataMapper() {
	}
	
	/**
	 * map into store in the ODM parser into simuCtx object
	 * 
	 * @param p ODM parser object, representing a ODM xml file
	 * @param simuCtx
	 */
	@Override
	public SimuContext map2Model(Tfrom p) throws InterpssException {
		final SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.NOT_DEFINED);
		if (this.map2Model(p, simuCtx)) {
  	  		simuCtx.setId("InterPSS_SimuCtx");
  	  		simuCtx.setName("InterPSS SimuContext Object");
  	  		simuCtx.setDesc("InterPSS SimuContext Object - created from ODM model");
  			return simuCtx;
		}	
		throw new InterpssException("Error in map ODM model to SimuContext object");
	}	
}