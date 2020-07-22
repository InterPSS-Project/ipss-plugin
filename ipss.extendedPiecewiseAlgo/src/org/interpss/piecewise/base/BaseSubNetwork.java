 /*
  * @(#)BaseSubNetwork.java   
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

package org.interpss.piecewise.base;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.net.Branch;
import com.interpss.core.net.Bus;
import com.interpss.core.net.Network;

/**
 * Base class for modeling the SubNetwork concept. A SubNetwork contains buses and connected branches, 
 * using Bus.SubAreaFlag = SubNetwork.flag. Therefore, Bus.network = the SubNetwork for all buses and branches 
 * in a SubNetwork. 
 * 
 * @param <TBus> bus generic type
 * @param <TBranch> branch generic type
 * @param <TNet> network generic type
 *  */
public abstract class BaseSubNetwork<TBus extends Bus, 
                            TBranch extends Branch, 
                            TNet extends Network<TBus, TBranch>, 
                            TYmatrix, TZMatrix, TVolt> 
								extends BaseSubArea<TYmatrix, TZMatrix, TVolt>{
	/** a network object representing the sub-area*/
	protected TNet subNet;
	
	/**
	 * default constructor
	 * 
	 * @param flag
	 */
	public BaseSubNetwork(int flag) {
		super(flag);
	}
	
	/**
	 * constructor
	 * 
	 * @param flag
	 * @param ids
	 */
	public BaseSubNetwork(int flag, String[] ids) {
		super(flag, ids);
	}
	
	/**
	 * @return the subNet
	 */
	public TNet getSubNet() {
		return subNet;
	}
	
	/**
	 * After the sub-area info have been defined, build the sub-net object from
	 * the parent network object. Before the SubNetwork processing, all buses/branches in SubArea
	 * are contained by the parent network (Bus/Branch.network = Parent Network). After the SubNetwork processing,
	 * buses/branches in SubNetwork are contained by the SubNetwork (Bus/Branch.network = SubNetwork), although the
	 * parent network still holds reference to all the buses and branches. <p>
	 * 
	 * In this method, only Bus and Branch objected are "moved" from the parent network to
	 * the SubNetwork. It should be override in the subclass if more objects need to be "moved".
	 * 
	 * @param parentNet
	 */
	public void buildSubNet(TNet parentNet) throws InterpssException {
		//System.out.println("Build SubNetwork for " + this.getFlag());
		
		this.subNet = createSubNetwork();
		
		for (TBus bus : parentNet.getBusList()) {
			if (bus.getSubAreaFlag() == this.getFlag()) {
				this.subNet.addBus(bus);
			}
		};
		
		for ( TBranch branch : parentNet.getBranchList()) {
			if (branch.getFromBus().getSubAreaFlag() == this.getFlag() && 
					branch.getToBus().getSubAreaFlag() == this.getFlag()) {
				this.subNet.addBranch(branch);
			}
		};
	}
	
	/**
	 * create the sub-network object
	 * 
	 * @return the sub-network object
	 */
	public abstract TNet createSubNetwork();
	
	public String toString() {
		return super.toString() + 
				this.subNet.net2String();
	}	
}	
