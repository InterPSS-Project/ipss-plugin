 /*
  * @(#)QAResultContainer.java   
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
package org.interpss.plugin.QA.result;

import java.util.Hashtable;

/**
 * QA result container, normally contains base case info for comparison
 * 
 * @author mzhou
 *
 * @param <TBusRec>
 * @param <TBranchRec>
 */
@Deprecated            // use the Bean data structure instead
public class  QAResultContainer<TBusRec extends QABusRec, TBranchRec extends QABranchRec> {
	// since the comparison is at PU, we need to make sure the baseKva/Mva is consistent
	private double baseMva = 100.0;
	protected Hashtable<String, TBusRec> busResultSet;
	protected Hashtable<String, TBranchRec> branchResultSet;
	
	public QAResultContainer() {
		this(100.0);
	}
	
	public QAResultContainer(double baseMva) {
		this.busResultSet = new Hashtable<String, TBusRec>();
		this.branchResultSet = new Hashtable<String, TBranchRec>();
		this.baseMva = baseMva;
	}
	
	public double getBaseMva() {
		return this.baseMva;
	}

	public void setBaseMva(double baseMva) {
		this.baseMva = baseMva;
	}
	
	public TBusRec getBusResult(String id) {
		return this.busResultSet.get(id);
	}

	public void setBusResult(String id, TBusRec rec) {
		this.busResultSet.put(id, rec);
	}

	public TBranchRec getBranchResult(String id) {
		return this.branchResultSet.get(id);
	}

	public void setBranchResult(String id, TBranchRec rec) {
		this.branchResultSet.put(id, rec);
	}
}
