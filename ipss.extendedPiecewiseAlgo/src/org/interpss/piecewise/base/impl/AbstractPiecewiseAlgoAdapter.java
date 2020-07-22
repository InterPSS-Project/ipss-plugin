 /*
  * @(#)AbstractPiecewiseAlgoAdapter.java   
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

package org.interpss.piecewise.base.impl;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.function.Function;

import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.piecewise.PiecewiseAlgorithm;
import org.interpss.piecewise.base.BaseCuttingBranch;
import org.interpss.piecewise.base.BaseSubArea;

import com.interpss.common.exp.InterpssException;

/**
 * Abstract Piecewise Algorithm implementation adapter.
 * 
 * @author Mike
 *
 */
public abstract class AbstractPiecewiseAlgoAdapter<TBus, TNet, TState, TSub extends BaseSubArea<?, ?, TState>> 
					implements  PiecewiseAlgorithm<TBus, TState, TSub> {
	// Parent Network object
	protected TNet parentNet;
	
	// flag to indicate if the network subarea Y-matrix needs to be formed for the 
	// calculation
	protected boolean netYmatrixDirty;
	
	// network bus voltage storage place
	//protected Hashtable<String, TState> netVoltage;
	
	// Sub-area/network list 
	protected List<TSub> subAreaNetList;
	
	/**
	 * Constructor
	 * 
	 * @param parentNet AclfNetwork object
	 */
	public AbstractPiecewiseAlgoAdapter() {
		this.netYmatrixDirty = true;
		//this.netVoltage = new Hashtable<>();
		this.subAreaNetList = new ArrayList<>();
	}
	
	/**
	 * @return the netYmatrixDirty
	 */
	public boolean isNetYmatrixDirty() {
		return netYmatrixDirty;
	}


	/**
	 * @param netYmatrixDirty the netYmatrixDirty to set
	 */
	public void setNetYmatrixDirty(boolean netYmatrixDirty) {
		this.netYmatrixDirty = netYmatrixDirty;
	}

	@Override public Hashtable<String, TState> getBusVoltage(int areaFlag) {
		return this.getSubArea(areaFlag).getBusVoltage();
	}

	/**
	 * get the subarea list
	 * 
	 * @return the netVoltage
	 */
	public List<TSub> getSubAreaNetList() {
		return this.subAreaNetList;
	}

	/**
	 * get SubArea by the area flag
	 * 
	 * @param flag the area flag
	 * @return the subarea object
	 */
	public TSub getSubArea(int flag) {
		for (TSub subarea: this.subAreaNetList) {
			if (subarea.getFlag() == flag)
				return subarea;
		}
		return null;
	}
	
	@Override
	public void calculateNetVoltage(
				List<TSub> subAreaNetList,
				BaseCuttingBranch<TState>[] cbranches, 
				Function<TBus,TState> injCurrentFunc)  throws InterpssException, IpssNumericException {
		this.subAreaNetList = subAreaNetList;
  		
		/*
		 * extension point-1: The open circuit voltage of each SubArea/Network could be calculated in
		 *                    different ways. This is the place to plug-in different open circuit solution
		 *                    methods 
		 */
  		// Solve for the open-circuit voltage and build the interface equiv bus z-matrix. The voltage results are stored in
		// the this.netVoltage.
		buildNortonEquivNet(injCurrentFunc);

		/*
		 * extension point-2: Certain SubArea/Network might be a current source, for example, an SubArea/Network
		 *                    modeled in the EMTP approach. 
		 */
  		// calculate cutting branch current. The current results are stored in the cbranches object
    	calculateCuttingBranchCurrent(cbranches);

		/*
		 * extension point-3: For linear SubArea/Network, the superposition method could be used. However, for non-linear
		 *                    SubArea/Network (EMPT, abc), custom methods might be needed.  
		 */
  		// calculate the SubArea/Network bus voltage. For linear SubArea/Network, the superposition method could 
    	// be used, superposition of the open-circuit voltage and voltage by injecting the cutting branch current 
    	// in the SubArea/Network.
  		calcuateSubAreaNetVoltage(cbranches);  		

		//return this.netVoltage;
	}
}

