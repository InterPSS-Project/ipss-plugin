/*
 * @(#)IntegrationControlBlock.java   
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
import static com.interpss.dstab.controller.block.ICMLStaticBlock.StaticBlockType.NonWindup;

import org.interpss.numeric.datatype.LimitType;

import com.interpss.dstab.controller.block.adapt.CMLControlBlock1stOrderAdapter;

/**
 * An implementation of integration block
 * 
 * @author mzhou
 *
 */
public class IntegrationControlBlock extends CMLControlBlock1stOrderAdapter {
	protected double k = 0.0;
	protected LimitType limit = null;

	/**
	 * constructor
	 * 
	 * @param k
	 */
	public IntegrationControlBlock(double k) {
		setType(NoLimit);
		this.k = k;
	}

	/**
	 * constructor
	 * 
	 * @param type
	 * @param k
	 * @param max
	 * @param min
	 */
	public IntegrationControlBlock(StaticBlockType type, double k, double max, double min) {
		this(k);
		setType(type);
		limit = new LimitType(max, min);
	}

	@Override public boolean initStateY0(double y0) {
		setStateX(y0);
		if (getType() == Limit
				|| getType() == NonWindup)
			return !limit.isViolated(y0);
		else
			return true;
	}

	@Override public double getU0() {
		return 0.0;
	}

	@Override public void eulerStep1(double u, double dt) {
		super.eulerStep1(u, dt);
		if (getType() == NonWindup) {
			if (limit.isViolated(getStateX())) {
				setStateX(limit.limit(getStateX()));
				setDX_dt(0.0);
			}
		}
	}

	@Override public void eulerStep2(double u, double dt) {
		super.eulerStep2(u, dt);
		if (getType() == NonWindup)
			setStateX(limit.limit(getStateX()));
	}

	@Override public double getY() {
		if (getType() == Limit)
			return limit.limit(getStateX());
		else
			return getStateX();
	}

	@Override protected double dX_dt(double u) {
		return getK() * u;
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
	 * get the limit object
	 * 
	 * @return the limit
	 */
	public LimitType getLimit() {
		return limit;
	}
	
	@Override public String toString() {
		String str = "type, k, limit: " + getType() + ", " + k + ", " + limit;
		return str;
	}	
}
