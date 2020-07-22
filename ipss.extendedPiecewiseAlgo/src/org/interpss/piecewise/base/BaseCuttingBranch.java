 /*
  * @(#)BaseCuttingBranch.java   
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

import com.interpss.core.net.BranchBusSide;

/**
 * Base class for modeling the cutting branch concept
 * 
 * @author Mike
 *
 * @param <TCur> branch current generic type
 */
public class BaseCuttingBranch<TCur> {
	// default sub area flag
	public static final int DefaultFlag = -1;
	
	// cutting branch AclfNetwork id
	private String branchId;
	
	// cutting branch from/to bus SubArea flag
	private int fromSubAreaFlag, toSubAreaFlag;
	
	// cutting bus side, only for information purpose
	private BranchBusSide splitSide;
	
	// the calculated cutting branch Norton equivalent current
	protected TCur cur;
	
	/**
	 * default constructor
	 */
	public BaseCuttingBranch() {}
	
	/**
	 * constructor
	 * 
	 * @param id branch id
	 * @param fromFlag branch from bus area flag
	 * @param toFlag branch to bus area flag
	 */
	public BaseCuttingBranch(String id, int fromFlag, int toFlag) {
		this.branchId = id;
		this.fromSubAreaFlag = fromFlag;
		this.toSubAreaFlag = toFlag;
	}

	/**
	 * constructor
	 * 
	 * @param id branch id
	 * @param fromFlag branch from bus area flag
	 * @param toFlag branch to bus area flag
	 */
	public BaseCuttingBranch(String id, int fromFlag, int toFlag, BranchBusSide cuttingSide) {
		this(id, fromFlag, toFlag);
		this.splitSide = cuttingSide;
	}
	
	/**
	 * @return the branchId
	 */
	public String getBranchId() {
		return branchId;
	}

	/**
	 * @return the fromSubAreaFlag
	 */
	public int getFromSubAreaFlag() {
		return fromSubAreaFlag;
	}

	/**
	 * @return the splitSide
	 */
	public BranchBusSide getSplitSide() {
		return this.splitSide;
	}
	
	/**
	 * @return the toSubAreaFlag
	 */
	public void setToSubAreaFlag(int flag) {
		this.toSubAreaFlag = flag;
	}

	/**
	 * @return the fromSubAreaFlag
	 */
	public void setFromSubAreaFlag(int flag) {
		this.fromSubAreaFlag = flag;
	}

	/**
	 * @return the toSubAreaFlag
	 */
	public int getToSubAreaFlag() {
		return toSubAreaFlag;
	}
	
	/**
	 * @return the cur
	 */
	public TCur getCurrent() {
		return cur;
	}

	/**
	 * @return the cur
	 */
	public void setCurrent(TCur cur) {
		this.cur = cur;
	}
	
	public String toString() {
		String str =  "Branch Id: " + this.branchId + "\n"
				+ "From, To side SubArea Flag, splitSide: " 
				+ this.fromSubAreaFlag + ", " + this.toSubAreaFlag + ", " + this.splitSide + "\n";
		return str;
	}
}
