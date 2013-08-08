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

import com.interpss.dstab.controller.block.adapt.CMLFunctionAdapter;

/**
 * A Function to calculate Rectifier regulation Fex(In) per IEEE 421.5, Appendix-D
 * 
 * @author mzhou
 *
 */
public class FexFunction extends CMLFunctionAdapter {
	/**
	 * evaluate function value based on the input double array. The array matches the input var rec list
	 *
	 * @param dAry contains only one value In
	 * @return the function value
	 */
	@Override public double eval(double[] dAry) {
		double In = dAry[0]; // the only input to this function is In
		if (In <= 0.0)
			return 1.0;
		else if (In > 0.0 && In <= 0.433)
			return 1.0 - 0.5777 * In;
		else if (In > 0.433 && In < 0.75)
			return Math.sqrt(0.75 - In * In);
		else if (In >= 0.75 && In <= 1.0)
			return 1.732 * (1.0 - In);
		else
			return 0.0; // In > 1.0
	}
}
