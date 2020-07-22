 /* @(#)SubAcscNetwork.java   
  *
  * Copyright (C) 2006-2016 www.interpss.org
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
  * @Date 11/15/2016
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.piecewise.seq012;

import com.interpss.CoreObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.acsc.AcscBranch;
import com.interpss.core.acsc.AcscBus;
import com.interpss.core.acsc.AcscGen;
import com.interpss.core.acsc.AcscLoad;
import com.interpss.core.acsc.AcscNetwork;

/**
 * Class for modeling the SubNetwork concept for representing a sub-network of type AcscNetwork. 
 */
public class SubAcscNetwork extends SubNetwork012<AcscBus, AcscBranch, AcscNetwork>{
	
	/**
	 * default constructor
	 * 
	 * @param flag
	 */
	public SubAcscNetwork(int flag) {
		super(flag);
	}
	
	/**
	 * constructor
	 * 
	 * @param flag
	 * @param ids
	 */
	public SubAcscNetwork(int flag, String[] ids) {
		super(flag, ids);
	}
	
	@Override public AcscNetwork createSubNetwork() {
		return CoreObjectFactory.createAcscNetwork();
	};
	
	@Override public void buildSubNet(AcscNetwork parentNet) throws InterpssException {
		super.buildSubNet(parentNet);
		
		// TODO only bus and branch objects are copied to the SubNetwork in the super class. We
		//      may need to add missing objects
	}
}	
