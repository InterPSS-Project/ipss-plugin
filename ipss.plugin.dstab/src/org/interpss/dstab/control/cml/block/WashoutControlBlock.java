/*
 * @(#)WashoutControlBlock.java   
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
 * @Date 10/30/2006
 * 
 *   Revision History
 *   ================
 *
 */

package org.interpss.dstab.control.cml.block;

import com.interpss.common.exp.InterpssRuntimeException;
import com.interpss.dstab.controller.block.adapt.CMLControlBlock1stOrderAdapter;

/**
 * An implementation of washout block
 * 
 * Transfer function  K * ( T * s ) / ( 1 + T * s )
 */
public class WashoutControlBlock extends CMLControlBlock1stOrderAdapter {
	protected double k = 0.0;
	protected double t = 0.0;

	/**
	 * constructor
	 * 
	 * @param k
	 * @param t
	 */
	public WashoutControlBlock(double k, double t) {
		this.k = k;
		this.t = t;
	}

	@Override public boolean initStateU0(double u0) {
		setU(u0);
		setStateX(getK() * u0);
		return true;
	}

	@Override public boolean initStateY0(double y0) {
		if (y0 != 0.0) {
			throw new InterpssRuntimeException(
					"Washout block, initStateY0(), y0 should = 0.0");
		}
		setU(0.0);
		setStateX(0.0);
		return true;
	}

	@Override public double getU0() {
		return getU();
	}

	@Override public double getY() {
		double u = getU();
		double y = getK() * u - getStateX();
		return y;
	}

	@Override protected double dX_dt(double u) {
		return (getK() * u - getStateX()) / getT();
	}

	/**
	 * get parameter k
	 * 
	 * @return the k
	 */
	public double getK() {
		return k;
	}

	/**
	 * get parameter t
	 * 
	 * @return the t
	 */
	public double getT() {
		return t;
	}
	
	@Override public String toString() {
		String str = "type, k, t: " + getType() + ", " + k + ", " + t;
		return str;
	}	
}
