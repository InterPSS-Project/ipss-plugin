/*
  * @(#)DblBranchValue.java   
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

import java.util.Comparator;

import org.interpss.datatype.base.BranchValueBase;
import org.interpss.numeric.util.Number2String;

import com.interpss.core.aclf.AclfBranch;

/**
 * A branch data object of type double
 * 
 * @author mzhou
 *
 */
public class DblBranchValue extends BranchValueBase {
	/**
	 * double value field
	 */
	public double value;
	
	/**
	 * constructor
	 * 
	 * @param x
	 */
	public DblBranchValue(double x) {
		this.value = x;
	}
	
	/**
	 * constructor
	 * 
	 * @param id
	 * @param x
	 */
	public DblBranchValue(String id, double x) {
		this.id = id;
		this.value = x;
	}	
	
	/**
	 * constructor
	 * 
	 * @param b
	 * @param x
	 */
	public DblBranchValue(AclfBranch b, double x) {
		this.branch = b;
		this.id = branch.getId();
		this.value = x;
	}	

	/**
	 * Branch data object absolute value comparator. For use Collections.sort() in the descending order
	 * 
	 * @return
	 */
	public static Comparator<DblBranchValue> getAbsComparator() {
		return new Comparator<DblBranchValue>() {
			@Override public int compare(DblBranchValue o1, DblBranchValue o2) {
		        return Math.abs(o1.value) < Math.abs(o2.value)? 1 : -1;
		    }
		};
	}	
	
	@Override public String toString() {
		return Number2String.toStr(value) + "@" + (branch!=null?branch.getId():"NotFound");
	}
}
