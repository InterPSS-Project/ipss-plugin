 /*
  * @(#)SimpleStabilizerData.java   
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


package org.interpss.dstab.control.pss.simple;

/**
 * A JavaBean to store data for the Simple stabilizer model. It needs to follow
 * JavaBean convention so that the controller data object be serialized/
 * deserialized for grid computing.
 *
 */
public class SimpleStabilizerData {
	public SimpleStabilizerData() {}

	private double ks = 1.0;
	private double t1 = 0.05;
	private double t2 = 0.5;
	private double t3 = 0.05;
	private double t4 = 0.25;
	private double vsmax = 0.2;
	private double vsmin = -0.2;
	
	/**
	 * @return Returns the ks.
	 */
	public double getKs() {
		return ks;
	}
	/**
	 * @param ks The ks to set.
	 */
	public void setKs(final double ks) {
		this.ks = ks;
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
	/**
	 * @return Returns the t2.
	 */
	public double getT2() {
		return t2;
	}
	/**
	 * @param t2 The t2 to set.
	 */
	public void setT2(final double t2) {
		this.t2 = t2;
	}
	/**
	 * @return Returns the t3.
	 */
	public double getT3() {
		return t3;
	}
	/**
	 * @param t3 The t3 to set.
	 */
	public void setT3(final double t3) {
		this.t3 = t3;
	}
	/**
	 * @return Returns the t4.
	 */
	public double getT4() {
		return t4;
	}
	/**
	 * @param t4 The t4 to set.
	 */
	public void setT4(final double t4) {
		this.t4 = t4;
	}
	/**
	 * @return Returns the vsmax.
	 */
	public double getVsmax() {
		return vsmax;
	}
	/**
	 * @param vsmax The vsmax to set.
	 */
	public void setVsmax(final double vsmax) {
		this.vsmax = vsmax;
	}
	/**
	 * @return Returns the vsmin.
	 */
	public double getVsmin() {
		return vsmin;
	}
	/**
	 * @param vsmin The vsmin to set.
	 */
	public void setVsmin(final double vsmin) {
		this.vsmin = vsmin;
	}
} 
