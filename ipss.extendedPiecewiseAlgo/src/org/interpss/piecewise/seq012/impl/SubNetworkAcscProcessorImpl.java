 /*
  * @(#)SubNetworkAcscProcessorImpl.java   
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

import java.util.List;

import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.piecewise.base.BaseCuttingBranch;
import org.interpss.piecewise.base.BaseSubArea;
import org.interpss.piecewise.base.impl.BaseSubAreaNetProcessorImpl;
import org.interpss.piecewise.seq012.SubAcscNetwork;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.acsc.AcscBranch;
import com.interpss.core.acsc.AcscBus;
import com.interpss.core.acsc.AcscNetwork;

/**
 * Class for AcscNetwork SubNetwork processing. It begins by defining a set of cutting branches.
 * It finds SubAreas in the network and SubArea interface buses. Then it "moves" the bus and branch 
 * objects to corresponding SubNetwork.
 * 
 * @author Mike
 *
 */
		
public class SubNetworkAcscProcessorImpl<TSub extends BaseSubArea<?, ?, Complex3x1>> extends BaseSubAreaNetProcessorImpl<AcscBus, AcscBranch, TSub, Complex3x1> {
	/**
	 * Constructor
	 * 
	 * @param net AclfNetwork object
	 */
	public SubNetworkAcscProcessorImpl(AcscNetwork net) {
		super(net);
	}

	/**
	 * Constructor
	 * 
	 * @param net AclfNetwork object
	 * @param cuttingBranches cutting branch set
	 */
	public SubNetworkAcscProcessorImpl(AcscNetwork net, BaseCuttingBranch<Complex3x1>[] cuttingBranches) {
		super(net, cuttingBranches);
	}	
	
	@SuppressWarnings("unchecked")
	@Override public TSub createSubAreaNet(int flag) {
		return (TSub)new SubAcscNetwork(flag);
	};
	
	@Override public List<TSub> processSubAreaNet() throws InterpssException {
		List<TSub> subNetList = super.processSubAreaNet();
		
		// for each SubNetwork, we build the child/parent relationship.
		for (TSub subNet : subNetList ) {
			((SubAcscNetwork)subNet).buildSubNet((AcscNetwork)this.getNetwork());
		};
		
		return subNetList;
	}
}
