/*
 * @(#)FilterControlBlock.java   
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

import static com.interpss.common.util.IpssLogger.ipssLogger;
import static com.interpss.dstab.controller.block.ICMLStaticBlock.StaticBlockType.Limit;
import static com.interpss.dstab.controller.block.ICMLStaticBlock.StaticBlockType.NoLimit;
import static com.interpss.dstab.controller.block.ICMLStaticBlock.StaticBlockType.NonWindup;

import org.interpss.numeric.datatype.LimitType;

import com.interpss.dstab.controller.block.adapt.CMLControlBlock1stOrderAdapter;

/**
 * A filter controller implementation
 * 
 * @author mzhou
 *
 */
public class FilterControlBlock extends CMLControlBlock1stOrderAdapter {
	protected double k = 0.0;
	protected double t1 = 0.0;
	protected double t2 = 0.0;
	protected LimitType limit = null;

	/**
	 * constructor
	 * 
	 * @param k
	 * @param t1
	 * @param t2
	 */
	public FilterControlBlock(double k, double t1, double t2) {
		setType(NoLimit);
		this.k = k;
		this.t1 = t1;
		this.t2 = t2;
	}

	/**
	 * constructor
	 * 
	 * @param type
	 * @param k
	 * @param t1
	 * @param t2
	 * @param max
	 * @param min
	 */
	public FilterControlBlock(StaticBlockType type, double k, double t1, double t2,
			double max, double min) {
		this(k, t1, t2);
		setType(type);
		limit = new LimitType(max, min);
	}

	@Override public boolean initStateY0(double y0) {
		if (getK() <= 0.0) {
			ipssLogger.severe("FilterControlBlock.initState(), k <= 0.0");
			return false;
		}
		setU(y0 / getK());
		setStateX(y0 * (1.0 - getT1() / getT2()));
		if (getType() == Limit
				|| getType() == NonWindup)
			return !limit.isViolated(y0);
		else
			return true;
	}

	@Override public boolean initStateU0(double u0) {
		setU(u0);
		double y0 = u0 * getK();
		return initStateY0(y0);
	}

	@Override public double getU0() {
		return getU();
	}

	@Override public void eulerStep1(double u, double dt) {
		super.eulerStep1(u, dt);
		if (getType() == NonWindup) {
			if (isLimitViolated(u)) {
				double u1 = getU1(u);
				setStateX(limit.limit(getStateX() + u1) - u1);
				setDX_dt(0.0);
			}
		}
	}

	@Override public void eulerStep2(double u, double dt) {
		super.eulerStep2(u, dt);
		if (getType() == NonWindup) {
			if (isLimitViolated(u)) {
				double u1 = getU1(u);
				setStateX(limit.limit(getStateX() + u1) - u1);
			}
		}
	}

	@Override public double getY() {
		double u = getU();
		double y = 0.0;
		
		if (getT2() > 0.0)
			y = getStateX() + getU1(u);
		else
			y = u * getK();
		// System.out.println("u, y " + u + ", " + y);
		if (getType() == Limit)
			return limit.limit(y);
		else
			return y;
	}

	@Override protected double dX_dt(double u) {
		if (getT2() > 0.0)
			return (getK() * (1.0 - getT1() / getT2()) * u - getStateX())
					/ getT2();
		else
			return 0.0;
	}

	private double getU1(double u) {
		if (getT2() > 0.0)
			return getK() * getT1() * u / getT2();
		else
			return 0.0;
	}

	private boolean isLimitViolated(double u) {
		return (limit.getMin() - getU1(u)) > getStateX()
				|| getStateX() > (limit.getMax() - getU1(u));
	}

	/**
	 * @return the k
	 */
	public double getK() {
		return k;
	}

	/**
	 * @return the limit
	 */
	public LimitType getLimit() {
		return limit;
	}

	/**
	 * @return the t
	 */
	public double getT1() {
		return t1;
	}

	/**
	 * @return the t
	 */
	public double getT2() {
		return t2;
	}

	@Override public String toString() {
		String str = "type, k, t1, t2, limit: " + getType() + ", " + k + ", "
				+ t1 + ", " + t2 + ", " + limit;
		return str;
	}
}
