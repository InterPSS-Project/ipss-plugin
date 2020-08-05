 /*
  * @(#)SubNetworkPosProcessorImpl.java   
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

package org.interpss.piecewise.subAreaNet.seqPos.impl;

import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.interpss.piecewise.subAreaNet.base.BaseCuttingBranch;
import org.interpss.piecewise.subAreaNet.base.BaseSubArea;
import org.interpss.piecewise.subAreaNet.impl.BaseSubAreaNetProcessorImpl;
import org.interpss.piecewise.subAreaNet.seqPos.SubAreaPos;
import org.interpss.piecewise.subAreaNet.seqPos.SubNetworkPos;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;

/**
 * Class for single phase SubNetwork processing. It begins by defining a set of cutting branches.
 * It finds SubAreas in the network and SubArea interface buses. Then it "moves" the bus and branch 
 * objects to corresponding SubNetwork.
 * 
 * @author Mike
 *
 */
		
public class SubNetworkPosProcessorImpl<TSub extends BaseSubArea<?, ?, Complex>> extends BaseSubAreaNetProcessorImpl<AclfBus, AclfBranch, TSub, Complex> {
	/**
	 * Constructor
	 * 
	 * @param net AclfNetwork object
	 * @param subType SubArea/Network processing type
	 */
	public SubNetworkPosProcessorImpl(AclfNetwork net) {
		super(net);
	}

	/**
	 * Constructor
	 * 
	 * @param net AclfNetwork object
	 * @param subType SubArea/Network processing type
	 * @param cuttingBranches cutting branch set
	 */
	public SubNetworkPosProcessorImpl(AclfNetwork net, BaseCuttingBranch<Complex>[] cuttingBranches) {
		super(net, cuttingBranches);
	}	
	
	@SuppressWarnings("unchecked")
	@Override public TSub createSubAreaNet(int flag) {
		return (TSub)new SubNetworkPos(flag);
	};
	
	@Override public List<TSub> processSubAreaNet() throws InterpssException {
		List<TSub> subNetList = super.processSubAreaNet();
		
		// for each SubNetwork, we build the child/parent relationship.
		for (TSub subNet : subNetList ) {
			((SubNetworkPos)subNet).buildSubNet((AclfNetwork)this.getNetwork());
		};
		
		return subNetList;
	}
}
