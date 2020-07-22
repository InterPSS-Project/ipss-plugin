 /*
  * @(#)PiecewiseAlgoDStabImpl.java   
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

package org.interpss.piecewise.seq012.algo;

import java.util.List;

import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.sparse.ISparseEqnComplex;
import org.interpss.piecewise.base.BaseSubArea;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabBranch;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabLoad;
import com.interpss.dstab.DStabilityNetwork;

/**
 * Piecewise algorithm implementation for DStabNet012 sequence network. We use
 * an array[3] to store 012 quantities in the sequence of [0, 1, 2]
 * 
 * @author Mike
 *
 */
public class PiecewiseAlgoDStabImpl<TSub extends BaseSubArea<ISparseEqnComplex[], Complex3x1[][], Complex3x1>> 
					extends PiecewiseAlgo012Impl<BaseDStabBus<DStabGen,DStabLoad>, 
								BaseDStabNetwork<BaseDStabBus<DStabGen,DStabLoad>, DStabBranch>, TSub> {
	/**
	 * Constructor
	 * 
	 * @param net DStabilityNetwork object
	 */
	public PiecewiseAlgoDStabImpl(BaseDStabNetwork net) {
		super(net);
	}

	/**
	 * Constructor
	 * 
	 * @param net DStabilityNetwork object
	 * @param subAreaList SubArea/Network object list
	 */
	public PiecewiseAlgoDStabImpl(BaseDStabNetwork net, List<TSub> subAreaNetList) {
		super(net, subAreaNetList);
	}
}

