 /*
  * @(#)PiecewiseAlgorithm.java   
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

package org.interpss.piecewise;

import java.util.Hashtable;
import java.util.List;
import java.util.function.Function;

import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.piecewise.base.BaseCuttingBranch;

import com.interpss.common.exp.InterpssException;

/**
 * Interface for the Piecewise Algorithm implementation. 
 * A Network could be divided into a set of SubArea or SubNetwork connected by a set of cutting branches. 
 * Each SubArea/Network is identified by an unique flag(int). The Bus.SubAreaFlag is used to indicate where the 
 * bus is located in terms of SubArea/Network. A set of interface buses are defined in a SubArea/Network to keep 
 * track of cutting branch connection relationship.
 * 
 * @author Mike
 *
 * @param <TBus> Bus object generic type
 * @param <TState> Network state (current, voltage) generic type, for example, Complex for single phase analysis
 * @param <TSub> SubArea/Network generic type
 */
public interface PiecewiseAlgorithm<TBus, TState, TSub> {
	/**
	 * Flag to indicate if the Y-matrix of the SubArea/Network is dirty
	 * 
	 * @return the netYmatrixDirty boolean field
	 */
	boolean isNetYmatrixDirty();


	/**
	 * Set the netYmatrixDirty flag
	 * 
	 * @param netYmatrixDirty the netYmatrixDirty to set
	 */
	void setNetYmatrixDirty(boolean netYmatrixDirty);

	/**
	 * During the calculation process, the network bus voltage is cached in a subArea/Network hashtable.
	 * 
	 * @param areaFlag SubArea/Network flag
	 * @return the netVoltage
	 */
	Hashtable<String, TState> getBusVoltage(int areaFlag);

	/**
	 * get the SubArea/Network list
	 * 
	 * @return the SubArea/Network list
	 */
	List<TSub> getSubAreaNetList();

	/**
	 * get SubArea/Network by the area flag
	 * 
	 * @param flag the area flag
	 * @return the SubArea/Network object
	 */
	TSub getSubArea(int flag);
	
	/**
	 * Build Norton equivalent network, including: 1) solve for SubArea/Network open circuit bus voltage based on the 
	 * bus inject current function; 2) calculate the SubArea/Network interface equivalent bus Z-matrix. The 
	 * bus voltage results are stored in the netVoltage hashtable.
	 * 
	 * @param injCurrentFunc bus inject current calculation function
	 * @throws IpssNumericException
	 */
	void buildNortonEquivNet(Function<TBus, TState> injCurrentFunc)  throws IpssNumericException;
	
	/**
	 * calculate the SubArea/Network bus voltage based on 1) the cutting branch current; and  
	 * 2) the bus open circuit voltage stored in the netVoltage hashtable.
	 * 
	 * @param cuttingBranches cutting branch where the branch current is stored
	 * @throws IpssNumericException
	 */
	void calcuateSubAreaNetVoltage(BaseCuttingBranch<TState>[] cuttingBranches)  throws IpssNumericException;
	
	/**
	 * Calculate cutting branch current, based on the the bus open circuit voltage stored in the netVoltage hashtable
	 * and the cutting branch Z-matrix. The results are stored in the cuttingBranches object
	 * 
	 * @param cuttingBranches cutting branches
	 */
	void calculateCuttingBranchCurrent(BaseCuttingBranch<TState>[] cuttingBranches) throws IpssNumericException;
	
	/**
	 * Calculate network bus voltage based on a set of cutting branches and a bus injection current calculate function. 
	 * It includes 1) Solve for the open-circuit voltage; 2) calculate cutting branch current; and 3) calculate 
	 * SubArea/Network bus voltage.
	 * 
	 * @param subAreaNetList SubArea/Network list
	 * @param cbranches cutting branch set
	 * @param injCurrentFunc function for calculating bus injection current
	 * @throws InterpssException, IpssNumericException
	 */
	void calculateNetVoltage(
			List<TSub> subAreaNetList,
			BaseCuttingBranch<TState>[] cbranches, 
			Function<TBus, TState> injCurrentFunc) throws InterpssException, IpssNumericException;
}

