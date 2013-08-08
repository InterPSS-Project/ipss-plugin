/*
 * @(#)SeFuncBlock.java   
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

package org.interpss.dstab.control.cml.func;

import com.interpss.common.exp.InterpssException;
import com.interpss.dstab.controller.block.adapt.CMLFunctionAdapter;

/**
 * An implementation of function y = Se(Efd) 
 * 
 * @author mzhou
 *
 */
public class SeFunction extends CMLFunctionAdapter {
	private double e1 = 1.0;
	private double se_e1 = 1.0;
	private double e2 = 1.0;
	private double se_e2 = 1.0;

	private double a = 1.0;
	private double b = 1.0;

	/**
	 * constructor
	 * 
	 * @param e1
	 * @param se_e1
	 * @param e2
	 * @param se_e2
	 * @throws Exception
	 */
	public SeFunction(double e1, double se_e1, double e2, double se_e2)
			throws InterpssException {
		if (e1 <= e2 || se_e1 <= se_e2) {
			throw new InterpssException("Se(Efd) data error, E1, Se(E1), E2, Se(E2): "
					+ e1 + ", " + se_e1 + ", " + e2 + ", " + se_e2);
		}
		this.e1 = e1;
		this.se_e1 = se_e1;
		this.e2 = e2;
		this.se_e2 = se_e2;
		this.b = Math.log(se_e1 / se_e2) / (e1 - e2);
		this.a = se_e1 / Math.exp(this.b * e1);
	}

	/**
	 * evaluate function value based on the input double array. The array matches the input var rec list
	 *
	 * @param dAry contains only one value Efd
	 * @return the function value
	 */
	@Override public double eval(double[] dAry) {
		double efd = dAry[0]; // the only input to this function is Efd
		return this.a * Math.exp(this.b * efd);
	}

	@Override public String toString() {
		String str = "E1, Se(E1), E2, Se(E2): " + e1 + ", " + se_e1 + ", " + e2
				+ ", " + se_e2;
		str += "A, B: " + a + ", " + b;
		return str;
	}
}
