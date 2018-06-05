 /*
  * @(#)SimpleGovernorData.java   
  *
  * Copyright (C) 2006 www.interpss.org
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
  * @Date 09/15/2006
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.dstab.control.gov.simple;

/**
 * A JavaBean to store data for the Simple governor model. It needs to follow
 * JavaBean convention so that the controller data object be serialized/
 * deserialized for grid computing.
 *
 */
public class SimpleGovernorData {
	public SimpleGovernorData() {}
	
	private double k = 10.0;
	private double t1 = 0.5;
	private double pmax = 1.5;
	private double pmin = 0.0;
	/**
	 * @return Returns the k.
	 */
	public double getK() {
		return k;
	}
	/**
	 * @param k The k to set.
	 */
	public void setK(final double k) {
		this.k = k;
	}
	/**
	 * @return Returns the pmax.
	 */
	public double getPmax() {
		return pmax;
	}
	/**
	 * @param pmax The pmax to set.
	 */
	public void setPmax(final double pmax) {
		this.pmax = pmax;
	}
	/**
	 * @return Returns the pmin.
	 */
	public double getPmin() {
		return pmin;
	}
	/**
	 * @param pmin The pmin to set.
	 */
	public void setPmin(final double pmin) {
		this.pmin = pmin;
	}
	/**
	 * @return Returns the t1.
	 */
	public double getT1() {
		return t1;
	}
	/**
	 * @param t1 The t1 to set.
	 */
	public void setT1(final double t1) {
		this.t1 = t1;
	}
} // SimpleExcAdapter
