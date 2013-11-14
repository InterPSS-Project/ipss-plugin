 /*
  * @(#)AclfNetObjectPool.java   
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
  * @Date 12/15/2012
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.pssl.util;

import org.apache.commons.pool.impl.SoftReferenceObjectPool;
import org.interpss.pssl.plugin.IpssAdapter.FileImportDSL;
import org.interpss.pssl.util.impl.AclfPoolableObjFactory;

import com.interpss.core.aclf.AclfNetwork;

/**
 * AclfNetwork object pool
 * 
 * @author mzhou
 *
 */
public class AclfNetObjectPool extends SoftReferenceObjectPool<AclfNetwork> {

	/**
	 * constructor
	 * 
	 * @param dsl case input file DSL, which holds an ODM parser object
	 */
	public AclfNetObjectPool(FileImportDSL dsl) {
		super(new AclfPoolableObjFactory(dsl));
	}
	
	/**
	 * constructor
	 * 
	 * @param dsl case input file DSL, which holds an ODM parser object
	 * @param helper AclfNetwork object pool helper 
	 */
	public AclfNetObjectPool(FileImportDSL dsl, IAclfNetPoolHelper helper) {
		super(new AclfPoolableObjFactory(dsl, helper));
	}
}
