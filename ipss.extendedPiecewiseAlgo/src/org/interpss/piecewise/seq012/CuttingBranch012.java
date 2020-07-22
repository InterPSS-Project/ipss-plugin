 /*
  * @(#)CuttingBranch012.java   
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
import org.interpss.piecewise.base.BaseCuttingBranch;


/**
 * Class for modeling the cutting branch concept with current of Complex[3] for 012 sub-network
 * 
 * @author Mike
 *
 */
public class CuttingBranch012 extends BaseCuttingBranch<Complex3x1> {
	/**
	 * constructor
	 * 
	 * @param id branch id
	 * @param fromFlag branch from bus area flag
	 * @param toFlag branch to bus area flag
	 */
	public CuttingBranch012(String id, int fromFlag, int toFlag) {
		super(id, fromFlag, toFlag);
	}
	
	/**
	 * constructor
	 * 
	 * @param id branch id
	 */
	public CuttingBranch012(String id) {
		super(id, BaseCuttingBranch.DefaultFlag, BaseCuttingBranch.DefaultFlag);
	}
	
	public String toString() {
		String str =  super.toString();
		if (this.cur != null)
			str += "Branch current (from->to): " + this.cur.toString() + "\n";
		return str;
	}
}