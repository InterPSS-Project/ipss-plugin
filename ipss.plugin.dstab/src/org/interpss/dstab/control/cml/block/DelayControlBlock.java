/*
 * @(#)DelayControlBlock.java   
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

import com.interpss.dstab.controller.block.ICMLLimitExpression;
import com.interpss.dstab.controller.block.adapt.CMLControlBlock1stOrderAdapter;
import com.interpss.dstab.datatype.ExpCalculator;
import com.interpss.dstab.datatype.LimitExpression;

/**
 * An implementation of delay block
 * 
 * @author mzhou
 *
 */
public class DelayControlBlock extends CMLControlBlock1stOrderAdapter implements ICMLLimitExpression {
	protected double k = 0.0;
	protected double t = 0.0;
	protected LimitExpression limit = null;

	/**
	 * constructor
	 * 
	 * @param k
	 * @param t
	 */
	public DelayControlBlock(double k, double t) {
		setType(NoLimit);
		this.k = k;
		this.t = t;
	}

	/**
	 * constructor
	 * 
	 * @param type
	 * @param k
	 * @param t
	 * @param max
	 * @param min
	 */
	public DelayControlBlock(StaticBlockType type, double k, double t, double max,
			double min) {
		this(k, t);
		setType(type);
		limit = new LimitExpression(max, min);
	}

	/**
	 * constructor
	 * 
	 * @param type
	 * @param k
	 * @param t
	 * @param maxExp
	 * @param min
	 */
	public DelayControlBlock(StaticBlockType type, double k, double t,
			ExpCalculator maxExp, double min) {
		this(k, t);
		setType(type);
		limit = new LimitExpression(maxExp, min);
	}

	/**
	 * constructor
	 * 
	 * @param type
	 * @param k
	 * @param t
	 * @param max
	 * @param minExp
	 */
	public DelayControlBlock(StaticBlockType type, double k, double t, double max,
			ExpCalculator minExp) {
		this(k, t);
		setType(type);
		limit = new LimitExpression(max, minExp);
	}

	/**
	 * constructor
	 * 
	 * @param type
	 * @param k
	 * @param t
	 * @param maxExp
	 * @param minExp
	 */
	public DelayControlBlock(StaticBlockType type, double k, double t,
			ExpCalculator maxExp, ExpCalculator minExp) {
		this(k, t);
		setType(type);
		limit = new LimitExpression(maxExp, minExp);
	}

	@Override public boolean initStateY0(double y0, double[] maxDAry, double[] minDAry) {
		if (getK() <= 0.0) {
			ipssLogger.severe("DelayControlBlock.initState(), k <= 0.0");
			return false;
		}
		setU(y0 / getK());
		setStateX(y0);

		if (getType() == Limit
				|| getType() == NonWindup) {
			return !limit.isViolated(y0, maxDAry, minDAry);
		} else
			return true;
	}

	@Override public boolean initStateY0(double y0) {
		if (getK() <= 0.0) {
			ipssLogger.severe("DelayControlBlock.initState(), k <= 0.0");
			return false;
		}
		setU(y0 / getK());
		setStateX(y0);
		if (getType() == Limit
				|| getType() == NonWindup) {
			return !limit.isViolated(y0);
		} else
			return true;
	}

	@Override public boolean initStateU0(double u0, double[] maxDAry, double[] minDAry) {
		setU(u0);
		double y0 = u0 * getK();
		return initStateY0(y0, maxDAry, minDAry);
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
		eulerStep1(u, dt, null, null);
	}

	@Override public void eulerStep2(double u, double dt) {
		eulerStep2(u, dt, null, null);
	}

	@Override public void eulerStep1(double u, double dt, double[] maxDAry,
			double[] minDAry) {
		super.eulerStep1(u, dt);
		if (getType() == NonWindup) {
			if (limit.isViolated(getStateX(), maxDAry, minDAry)) {
				setDX_dt(0.0);
				setStateX(limit.limit(getStateX(), maxDAry, minDAry));
			}
		}
	}

	@Override public void eulerStep2(double u, double dt, double[] maxDAry,
			double[] minDAry) {
		super.eulerStep2(u, dt);
		if (getType() == NonWindup)
			setStateX(limit.limit(getStateX(), maxDAry, minDAry));
	}

	@Override public double getY() {
		//System.out.println("state " + getStateX());
		double y = getStateX();
		if (getT() <= 0.0)
			y = getK() * getU();
		if (getType() == Limit)
			return limit.limit(y);
		else
			return y;
	}

	@Override protected double dX_dt(double u) {
		if (getT() > 0.0)
			return (getK() * u - getStateX()) / getT();
		else
			return 0.0;
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
	 * get parameter limit
	 * 
	 * @return the limit
	 */
	public LimitExpression getLimit() {
		return limit;
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
		String str = "type, k, t, limit: " + getType() + ", " + k + ", " + t
				+ ", " + limit;
		return str;
	}
}
