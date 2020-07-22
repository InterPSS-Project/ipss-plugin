 /*
  * @(#)SubAreaAcscProcessorImpl.java   
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

package org.interpss.piecewise.seq012.impl;

import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.piecewise.base.BaseCuttingBranch;
import org.interpss.piecewise.base.BaseSubArea;
import org.interpss.piecewise.base.impl.BaseSubAreaNetProcessorImpl;
import org.interpss.piecewise.seq012.SubArea012;

import com.interpss.core.acsc.AcscBranch;
import com.interpss.core.acsc.AcscBus;
import com.interpss.core.acsc.AcscNetwork;

/**
 * Class for 012 SubArea processing. It begins by defining a set of cutting branches.
 * It finds SubAreas in the network and SubArea interface buses. 
 * 
 * @author Mike
 *
 */
		
public class SubAreaAcscProcessorImpl<TSub extends BaseSubArea<?, ?, Complex3x1>> extends BaseSubAreaNetProcessorImpl<AcscBus, AcscBranch, TSub, Complex3x1> {
	/**
	 * Constructor
	 * 
	 * @param net AclfNetwork object
	 * @param subType SubArea/Network processing type
	 */
	public SubAreaAcscProcessorImpl(AcscNetwork net) {
		super(net);
	}

	/**
	 * Constructor
	 * 
	 * @param net AclfNetwork object
	 * @param subType SubArea/Network processing type
	 * @param cuttingBranches cutting branch set
	 */
	public SubAreaAcscProcessorImpl(AcscNetwork net, BaseCuttingBranch<Complex3x1>[] cuttingBranches) {
		super(net, cuttingBranches);
	}	
	
	@SuppressWarnings("unchecked")
	@Override public TSub createSubAreaNet(int flag) {
		return (TSub)new SubArea012(flag);
	};
}
