/*
 * @(#)FilterNthOrderBlock.java   
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

import static com.interpss.dstab.controller.block.ICMLStaticBlock.StaticBlockType.NoLimit;
import static com.interpss.common.util.IpssLogger.ipssLogger;

import com.interpss.dstab.controller.block.adapt.CMLControlBlock1stOrderAdapter;

/**
 * Transfer Function:
 *  
 *       [ ( 1 + t1 s ) / ( 1 + t2 s )^m ] ^n 
 * 
 * This block is represented by 
 * 
 *       [ FilterControlBlock ]^n  [ DelayControlBlock ] ^ (m-1) * n
 * 
 * @author mzhou
 *
 */
public class FilterNthOrderBlock extends CMLControlBlock1stOrderAdapter {
	private double t1 = 0.0;
	private double t2 = 0.0;
	
	private int m = 1;
	private int n = 1;

	private FilterControlBlock[] filterBlockList = null;
	private DelayControlBlock[] delayBlockList = null;
	
	/**
	 * constructor
	 * 
	 * @param t1
	 * @param t2
	 * @param m
	 * @param n
	 */
	public FilterNthOrderBlock(double t1, double t2, int m, int n) {
		setType(NoLimit);
		this.t1 = t1;
		this.t2 = t2;
		
		this.m = m;
		this.n = n;
		
		if (n > 0) {
			filterBlockList = new FilterControlBlock[n];
			for (int i = 0; i < filterBlockList.length; i++)
				filterBlockList[i] = new FilterControlBlock(1.0, getT1(),
						getT2());
			if (m - 1 > 0) {
				delayBlockList = new DelayControlBlock[n * (m - 1)];
				for (int i = 0; i < delayBlockList.length; i++)
					delayBlockList[i] = new DelayControlBlock(1.0, getT2());
			}
		}
	}

	@Override public String getState() { 
		String str = "";
		if (delayBlockList != null)
			for (int i = delayBlockList.length - 1; i >= 0; i--) {
				str += delayBlockList[i].getState() + ", ";
			}
		if (filterBlockList != null)
			for (int i = filterBlockList.length - 1; i >= 0; i--) {
				str = filterBlockList[i].getState() + ", ";
			}
		return str + ", " + super.getState(); 
	}

	@Override public boolean initStateY0(double y0) {
		if (getN() < 0 || getM() <= 0) {
			ipssLogger.severe("FilterNthOrderBlock.intiState(),  < 0 || m <= 0");
			return false;
		}

		// all blocks should have the same y0
		setU(y0);
		if (filterBlockList != null)
			for (FilterControlBlock filter : filterBlockList) {
				filter.initStateY0(y0);
			}
		if (delayBlockList != null)
			for (DelayControlBlock delay : delayBlockList) {
				delay.initStateY0(y0);
			}

		return true;
	}

	@Override public boolean initStateU0(double u0) {
		setU(u0);
		double y0 = u0;
		return initStateY0(y0);
	}

	@Override public double getU0() {
		return getU();
	}

	@Override public void eulerStep1(double u, double dt) {
		setU(u);
		if (delayBlockList != null) {
			for (int i = delayBlockList.length - 1; i >= 0; i--) {
				double ui = 0.0;
				if (i == 0)
					ui = filterBlockList[filterBlockList.length - 1].getU();
				else
					ui = delayBlockList[i - 1].getU();
				delayBlockList[i].eulerStep1(ui, dt);
			}
		}

		if (filterBlockList != null) {
			for (int i = filterBlockList.length - 1; i >= 0; i--) {
				double ui = 0.0;
				if (i == 0)
					ui = u;
				else
					ui = filterBlockList[i - 1].getU();
				filterBlockList[i].eulerStep1(ui, dt);
			}
		}
	}

	@Override public void eulerStep2(double u, double dt) {
		setU(u);
		if (delayBlockList != null) {
			for (int i = delayBlockList.length - 1; i >= 0; i--) {
				double ui = 0.0;
				if (i == 0)
					ui = filterBlockList[filterBlockList.length - 1].getU();
				else
					ui = delayBlockList[i - 1].getU();
				delayBlockList[i].eulerStep2(ui, dt);
			}
		}

		if (filterBlockList != null) {
			for (int i = filterBlockList.length - 1; i >= 0; i--) {
				double ui = 0.0;
				if (i == 0)
					ui = u;
				else
					ui = filterBlockList[i - 1].getU();
				filterBlockList[i].eulerStep2(ui, dt);
			}
		}
	}

	@Override public double getY() {
		// return y of the last block
		if (delayBlockList != null)
			return delayBlockList[delayBlockList.length - 1].getY();
		else if (filterBlockList != null)
			return filterBlockList[filterBlockList.length - 1].getY();
		else
			return getU();
	}

	/**
	 * get parameter t1
	 * 
	 * @return the t1
	 */
	public double getT1() {
		return t1;
	}

	/**
	 * get parameter t2

	 * @return the t2
	 */
	public double getT2() {
		return this.t2;
	}

	/**
	 * get parameter m

	 * @return the m
	 */
	public int getM() {
		return this.m;
	}

	/**
	 * get parameter n
	 * 
	 * @return the n
	 */
	public int getN() {
		return this.n;
	}

	@Override public String toString() {
		String str = "t1, t2, n, m: " + t1 + ", " + t2 + ", " + n + ", " + m;
		return str;
	}
}
