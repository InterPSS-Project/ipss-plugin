/*
 * @(#)ExciterObjectFactory.java   
 *
 * Copyright (C) 2008-2010 www.interpss.org
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
 * @Date 08/15/2006
 * 
 *   Revision History
 *   ================
 *
 */

package org.interpss.dstab.control.exc.simple;

/**
 * A JavaBean to store data for the Simple exciter model. It needs to follow
 * JavaBean convention so that the controller data object be serialized/
 * deserialized for grid computing.
 *
 */
public class SimpleExciterData {
	public SimpleExciterData() {}

	// We need to put the default values here, so that the controller could be 
	// properly initialized. The parameters could be override by GUI editor
	private double ka = 10.0;
	private double ta = 0.05;
	private double vrmax = 10.0;
	private double vrmin = 0.0;
	
	/**
	 * @return Returns the ka.
	 */
	public double getKa() {
		return ka;
	}
	
	/**
	 * @param ka The ka to set.
	 */
	public void setKa(final double ka) {
		this.ka = ka;
	}
	
	/**
	 * @return Returns the ta.
	 */
	public double getTa() {
		return ta;
	}
	
	/**
	 * @param ta The ta to set.
	 */
	public void setTa(final double ta) {
		this.ta = ta;
	}
	
	/**
	 * @return Returns the vrmax.
	 */
	public double getVrmax() {
		return vrmax;
	}
	
	/**
	 * @param vrmax The vrmax to set.
	 */
	public void setVrmax(final double vrmax) {
		this.vrmax = vrmax;
	}
	
	/**
	 * @return Returns the vrmin.
	 */
	public double getVrmin() {
		return vrmin;
	}
	
	/**
	 * @param vrmin The vrmin to set.
	 */
	public void setVrmin(final double vrmin) {
		this.vrmin = vrmin;
	}
}

