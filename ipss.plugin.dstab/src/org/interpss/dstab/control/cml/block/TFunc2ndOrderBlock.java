/*
 * @(#)TFunc2ndOrderBlock.java   
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

import static com.interpss.dstab.controller.block.ICMLStaticBlock.StaticBlockType.Limit;
import static com.interpss.dstab.controller.block.ICMLStaticBlock.StaticBlockType.NoLimit;
import static com.interpss.common.util.IpssLogger.ipssLogger;

import org.interpss.numeric.datatype.LimitType;
import org.interpss.numeric.util.Number2String;

import com.interpss.dstab.controller.block.adapt.CMLControlBlock1stOrderAdapter;

/**
 * An implementation of 2nd order transfer function block
 * 
 * <code>
 * Transfer Function:
 *  
 *       y = [ k / ( 1 + a s + b s^2 ) ] u 
 *       
 * Differential eqn      
 *       dot(x1) = |  0    1   | |x1| + | 0 | u
 *       dot(x2)   | -1/b -a/b | |x2|   |k/b|
 *       
 *        y = [ 1  0 ] |x1| = x1
 *                     |x2|
 *        x2(0) = 0
 *        x1(0) = k * u(0);
 *        y(0)  = k * u(0);             
 * </code>
 *                      
 * @author mzhou
 *
 */
public class TFunc2ndOrderBlock extends CMLControlBlock1stOrderAdapter {
	private double k = 0.0;
	private double a = 0.0;
	private double b = 0.0;
	private LimitType limit = null;

	/*
		u  :     protected double StaticBlockAdapter.u = 0.0;
		x1 :     protected double ControlBlockAdapter.stateX = 0.0;   
	 	dx1/dt : protected double ControlBlock1stOrderAdapter.dX_dt = 0.0;
	 */
	private double stateX2 = 0.0;
	private double dX2_dt = 0.0;

	/**
	 * constructor
	 * 
	 * @param k
	 * @param a
	 * @param b
	 */
	public TFunc2ndOrderBlock(double k, double a, double b) {
		setType(NoLimit);
		this.k = k;
		this.a = a;
		this.b = b;
	}

	/**
	 * constructor
	 * 
	 * @param k
	 * @param a
	 * @param b
	 * @param max
	 * @param min
	 */
	public TFunc2ndOrderBlock(double k, double a, double b, double max,
			double min) {
		this(k, a, b);
		setType(Limit);
		limit = new LimitType(max, min);
	}

	@Override public String getState() { 
		return "x2 = " + Number2String.toDebugStr(this.stateX2) + ", " + "dx2_dt = " + 
        			Number2String.toDebugStr(this.dX2_dt) + ", " + super.getState(); }

	@Override
	public boolean initStateY0(double y0) {
		//        x2(0) = 0
		//        x1(0) = k * u(0) = y0;
		if (k <= 0.0) {
			ipssLogger.severe("TFunc2ndOrderBlock.initState(), k <= 0.0");
			return false;
		} else if (b < 0.0) {
			ipssLogger.severe("TFunc2ndOrderBlock.initState() b < 0.0");
			return false;
		} else if (b == 0.0 && a != 0) {
			ipssLogger.severe("TFunc2ndOrderBlock.initState() b == 0.0 && a != 0");
			return false;
		} else if (limit != null && limit.isViolated(y0)) {
			ipssLogger.severe("TFunc2ndOrderBlock.initState() limit violation");
			return false;
		}

		setU(y0 / k);
		stateX = 0.0;
		stateX2 = y0;
		dX_dt = 0.0;
		dX2_dt = 0.0;
		return true;
	}

	@Override public boolean initStateU0(double u0) {
		setU(u0);
		double y0 = k * u0;
		return initStateY0(y0);
	}

	@Override public double getU0() {
		return getU();
	}

	@Override public void eulerStep1(double u, double dt) {
		this.u = u;
		this.dX_dt = dX_dt(u);
		this.dX2_dt = dX2_dt(u);
		this.stateX += dX_dt * dt;
		this.stateX2 += dX2_dt * dt;
	}

	@Override public void eulerStep2(double u, double dt) {
		this.u = u;
		stateX += 0.5 * (dX_dt(u) - this.dX_dt) * dt;
		stateX2 += 0.5 * (dX2_dt(u) - this.dX2_dt) * dt;
	}

	/*
	 *       dot(x1) = |  0    1   | |x1| + | 0 | u
	 *       dot(x2)   | -1/b -a/b | |x2|   |k/b|
	 */

	@Override protected double dX_dt(double u) {
		if (b > 0.0)
			return stateX2;
		else
			return 0.0;
	}

	protected double dX2_dt(double u) {
		if (b > 0.0)
			return (-stateX - a * stateX2 + k * u) / b;
		else
			return 0.0;
	}

	/*
	 *        y = [ 1  0 ] |x1| = x1
	 *                     |x2|
	 */
	@Override public double getY() {
		double y = stateX;
		if (b == 0.0)
			y = k * getU();
		if (type == Limit) {
			y = limit.limit(y);
		}
		return y;
	}

	@Override public String toString() {
		String str = "type, k, a, b: " + getType() + ", " + k + ", " + a + ", "
				+ b;
		return str;
	}
}
