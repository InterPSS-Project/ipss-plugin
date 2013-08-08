/*
  * @(#)CplxBranchValue.java   
  *
  * Copyright (C) 2006-2011 www.interpss.com
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
  * @Date 04/15/2009
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.datatype;

import org.apache.commons.math3.complex.Complex;
import org.interpss.datatype.base.BranchValueBase;
import org.interpss.numeric.util.Number2String;

/**
 * A branch data object of type complex
 * 
 * @author mzhou
 *
 */
public class CplxBranchValue extends BranchValueBase {
	public Complex value;
	
	/**
	 * constructor
	 * 
	 * @param x
	 */
	public CplxBranchValue(Complex x) {
		this.value = x;
	}
	
	@Override public String toString() {
		return Number2String.toStr(value) + "@" + (branch!=null?branch.getId():"NotFound");
	}
}
