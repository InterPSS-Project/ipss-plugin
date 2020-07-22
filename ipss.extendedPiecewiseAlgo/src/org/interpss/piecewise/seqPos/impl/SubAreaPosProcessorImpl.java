 /*
  * @(#)SubAreaPosProcessorImpl.java   
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
  * @Date 04/15/2016
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.piecewise.seqPos.impl;

import org.apache.commons.math3.complex.Complex;
import org.interpss.piecewise.base.BaseCuttingBranch;
import org.interpss.piecewise.base.BaseSubArea;
import org.interpss.piecewise.base.impl.BaseSubAreaNetProcessorImpl;
import org.interpss.piecewise.seqPos.SubAreaPos;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;

/**
 * Class for single phase SubArea processing. It begins by defining a set of cutting branches.
 * It finds SubAreas in the network and SubArea interface buses. 
 * 
 * @author Mike
 *
 */
		
public class SubAreaPosProcessorImpl<TSub extends BaseSubArea<?, ?, Complex>> extends BaseSubAreaNetProcessorImpl<AclfBus, AclfBranch, TSub, Complex> {
	/**
	 * Constructor
	 * 
	 * @param net AclfNetwork object
	 * @param subType SubArea/Network processing type
	 */
	public SubAreaPosProcessorImpl(AclfNetwork net) {
		super(net);
	}

	/**
	 * Constructor
	 * 
	 * @param net AclfNetwork object
	 * @param subType SubArea/Network processing type
	 * @param cuttingBranches cutting branch set
	 */
	public SubAreaPosProcessorImpl(AclfNetwork net, BaseCuttingBranch<Complex>[] cuttingBranches) {
		super(net, cuttingBranches);
	}	
	
	@SuppressWarnings("unchecked")
	@Override public TSub createSubAreaNet(int flag) {
		return (TSub)new SubAreaPos(flag);
	};
}
