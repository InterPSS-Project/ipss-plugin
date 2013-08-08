/*
 * @(#)GainBlock.java   
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

import org.interpss.numeric.datatype.LimitType;

import com.interpss.dstab.controller.block.adapt.CMLStaticBlockAdapter;

/**
 * An implementation of gain block
 * 
 * @author mzhou
 *
 */
public class GainBlock extends CMLStaticBlockAdapter {
	protected double k = 0.0;
	protected LimitType limit = null;

	/**
	 * constructor
	 */
	public GainBlock() {
		this(1.0);
	}
	
	/**
	 * constructor
	 * 
	 * @param k
	 */
	public GainBlock(double k) {
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
	public GainBlock(StaticBlockType type, double k, double max, double min) {
		this(k);
		setType(type);
		limit = new LimitType(max, min);
	}

	@Override public boolean initStateY0(double y0) {
		u = y0 / getK();
		if (getType() == Limit)
			return !limit.isViolated(y0);
		else {
			return true;
		}
	}

	@Override public boolean initStateU0(double u0) {
		double y0 = u0 * getK();
		return initStateY0(y0);
	}

	@Override public double getU0(double y0) {
		return y0 / getK();
	}

	@Override public double getU0() {
		return u;
	}

	@Override public void eulerStep1(double u, double dt) {
		this.u = u;
	}

	@Override public void eulerStep2(double u, double dt) {
		this.u = u;
	}

	@Override public double getY() {
		double u = getU();
		if (getType() == Limit)
			return limit.limit(u * getK());
		else
			return u * getK();
	}

	/**
	 * get parameter kp
	 * 
	 * @return the kp
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
