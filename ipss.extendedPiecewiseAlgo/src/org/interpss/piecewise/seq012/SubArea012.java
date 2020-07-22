 /*
  * @(#)SubArea012.java   
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
import org.interpss.piecewise.base.BaseSubArea;

/**
 * Class for modeling the SubArea concept for representing 012 un-symmetric sub-network. 
 */
public class SubArea012 extends BaseSubArea<ISparseEqnComplex[], Complex3x1[][], Complex3x1>{
	/**
	 * default constructor
	 * 
	 * @param flag
	 */
	public SubArea012(int flag) {
		super(flag);
	}
	
	/**
	 * constructor
	 * 
	 * @param flag
	 * @param ids
	 */
	public SubArea012(int flag, String[] ids) {
		super(flag, ids);
	}
}	
