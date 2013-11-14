 /*
  * @(#)AclfPoolableObjFactory.java   
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

package org.interpss.pssl.util.impl;

import org.apache.commons.pool.BasePoolableObjectFactory;
import org.interpss.pssl.plugin.IpssAdapter.FileImportDSL;
import org.interpss.pssl.util.IAclfNetPoolHelper;

import com.interpss.core.aclf.AclfNetwork;

/**
 * Poolable AclfNet object factory
 * 
 * @author mzhou
 *
 */
public class AclfPoolableObjFactory extends BasePoolableObjectFactory<AclfNetwork> {
	/**
	 * a file import DSL object, which holds an ODM parser object after loading 
	 * a case data
	 */
	private FileImportDSL fileImportDsl = null;
	
	/**
	 * default empty implementation of the IAclfNetPoolHelper interface
	 */
	private IAclfNetPoolHelper helper = new IAclfNetPoolHelper() {
		public void init(AclfNetwork net) { /* do nothing */ }
		public void reset(AclfNetwork net) { /* do nothing */ }
	};

	private int objCnt = 0;
	
	/**
	 * constructor
	 * 
	 * @param modelStr a serialized AclfNetwork object string
	 */
	public AclfPoolableObjFactory(FileImportDSL fileDsl) {
		this.fileImportDsl = fileDsl;
	}
	
	/**
	 * constructor
	 * 
	 * @param modelStr a serialized AclfNetwork object string
	 * @param helper object init and reset helper object
	 */
	public AclfPoolableObjFactory(FileImportDSL fileDsl, IAclfNetPoolHelper helper) {
		this.fileImportDsl = fileDsl;
		this.helper = helper;
	}
	
    /**
     * for makeObject we'll simply return a new object instance
     * 
     * @return created AclfNetwork object 
     */ 
    @Override public AclfNetwork makeObject() throws Exception {
    	// map the ODM parser object to an AclfNetwork object to 
    	// create the net object
    	AclfNetwork net = this.fileImportDsl.mapAclfNet();
    	net.setName("AclfNetPool created - " + ++this.objCnt);
    	this.helper.init(net);
    	return net;
    } 
     
    /**
    	when an object is returned to the pool,  
    	we'll call this method to clear it out
    	
    *@param obj	AclfNetwork object to be return to the pool
    */
    @Override public void passivateObject(AclfNetwork obj) { 
        this.helper.reset(obj); 
    } 
}
