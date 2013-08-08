/*
 * @(#)PIControlBlock.java   
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

import org.interpss.numeric.datatype.LimitType;

import static com.interpss.dstab.controller.block.ICMLStaticBlock.StaticBlockType.*;
import com.interpss.dstab.controller.block.adapt.CMLControlBlock1stOrderAdapter;

/**
 * An implementation of PI controller block
 * 
 * @author mzhou
 *
 */
public class PIControlBlock extends CMLControlBlock1stOrderAdapter {
	protected double kp = 0.0;
	protected double ki = 0.0;
	protected LimitType limit = null;

	/**
	 * constructor
	 * 
	 * @param kp
	 * @param ki
	 */
	public PIControlBlock(double kp, double ki) {
		setType(NoLimit);
		this.kp = kp;
		this.ki = ki;
	}

	/**
	 * constructor
	 * 
	 * @param type
	 * @param kp
	 * @param ki
	 * @param max
	 * @param min
	 */
	public PIControlBlock(StaticBlockType type, double kp, double ki, double max,
			double min) {
		this(kp, ki);
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
			double x = getKp() * u;
			if (isLimitViolated(x)) {
				setStateX(limit.limit(getStateX() + x) - x);
				setDX_dt(0.0);
			}
		}
	}

	@Override public void eulerStep2(double u, double dt) {
		super.eulerStep2(u, dt);
		if (getType() == NonWindup) {
			double x = getKp() * u;
			if (isLimitViolated(x)) {
				setStateX(limit.limit(getStateX() + x) - x);
			}
		}
	}

	@Override public double getY() {
		double u = getU();
		if (getType() == Limit)
			return limit.limit(getStateX() + u * getKp());
		else
			return getStateX() + u * getKp();
	}

	@Override protected double dX_dt(double u) {
		return getKi() * u;
	}

	private boolean isLimitViolated(double x) {
		return (getLimit().getMin() - x) > getStateX()
				|| getStateX() > (getLimit().getMax() - x);

	}

	/**
	 * get parameter kp
	 * 
	 * @return the kp
	 */
	public double getKp() {
		return kp;
	}

	/**
	 * get the limit object
	 * 
	 * @return the limit
	 */
	public LimitType getLimit() {
		return limit;
	}

	/**
	 * get parameter t
	 * 
	 * @return the t
	 */
	public double getKi() {
		return ki;
	}
	
	@Override public String toString() {
		String str = "type, kp, ki, limit: " + getType() + ", " + kp + ", " + ki + ", " + limit;
		return str;
	}	
}
