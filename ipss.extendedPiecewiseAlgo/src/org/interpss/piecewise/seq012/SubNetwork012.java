 /* @(#)SubNetwork012.java   
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

import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.sparse.ISparseEqnComplex;
import org.interpss.piecewise.base.BaseSubNetwork;

import com.interpss.core.acsc.AcscBranch;
import com.interpss.core.acsc.AcscGen;
import com.interpss.core.acsc.AcscLoad;
import com.interpss.core.acsc.BaseAcscBus;
import com.interpss.core.acsc.BaseAcscNetwork;
import com.interpss.core.acsc.SequenceCode;

/**
 * Class for modeling the SubNetwork concept for representing 012 un-symmetric sub-network. 
 * 
 */
public abstract class SubNetwork012<TBus extends BaseAcscBus<? extends AcscGen, ? extends AcscLoad>, 
 									TBranch extends AcscBranch, 
									TNet extends BaseAcscNetwork<TBus, TBranch>> 
                   extends BaseSubNetwork<TBus, TBranch, TNet, ISparseEqnComplex[], Complex3x1[][], Complex3x1>{
	
	/**
	 * default constructor
	 * 
	 * @param flag
	 */
	public SubNetwork012(int flag) {
		super(flag);
	}
	
	/**
	 * constructor
	 * 
	 * @param flag
	 * @param ids
	 */
	public SubNetwork012(int flag, String[] ids) {
		super(flag, ids);
	}
	
	
	/**
	 * form the Y-matrix for the SubNetwork
	 */
	public void formYMatrix() {
		this.setYSparseEqn(new ISparseEqnComplex[] { 
				this.subNet.formScYMatrix(SequenceCode.POSITIVE, false),
				this.subNet.formScYMatrix(SequenceCode.NEGATIVE, false),
				this.subNet.formScYMatrix(SequenceCode.ZERO, false)});
	}	
}	
