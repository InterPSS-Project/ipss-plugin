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
import com.interpss.common.util.IpssLogger;
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
	/*
	 * function type 1 = quadratic , 2 = exponential
	 */
	private int functionType =1; // 

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
         
		 if((e1-e2)*(se_e1-se_e2)<0){
			throw new InterpssException("Se(Efd) data error, E1, Se(E1), E2, Se(E2): "
					+ e1 + ", " + se_e1 + ", " + e2 + ", " + se_e2);
		}
		if(Math.abs(e2)<1.0E-3 || Math.abs(se_e2)<1.0E-3 || Math.abs(se_e2-se_e1)<1.0E-3){
			this.a=0.0;
			this.b=0.0;
		}
		else if(se_e1<1.0E-3 && se_e2>1.0E-3 && e2>1.0){
			//Use quadratic function as default
			this.a = e1;
			this.b = (se_e2*e2)/(e2-this.a)/(e2-this.a);
		}
		else{	
			this.e1 = e1;
			this.se_e1 = se_e1;
			this.e2 = e2;
			this.se_e2 = se_e2;
			
			//Use quadratic function as default
			//Se = B*(Efd-A)^2/Efd
			if(this.functionType == 1){
				double X = Math.sqrt(e2/e1*se_e2/se_e1);
				 this.a = (e2-e1*X)/(1-X);
				 this.b = se_e1/(e1-a)/(e1-a)*e1;
				 if(Double.isNaN(a)) a =0;
				 if(Double.isNaN(b)) b =0;
			}
			
			//Se = B*exp(Efd*A)
			if(this.functionType == 2){
			    this.a = Math.log(se_e1 / se_e2) / (e1 - e2);
			    this.b = se_e1 / Math.exp(this.a * e1);
			}
			
			
		}
	}

	/**
	 * evaluate function value based on the input double array. The array matches the input var rec list
	 *
	 * @param dAry contains only one value Efd
	 * @return the function value
	 */
	@Override public double eval(double[] dAry) {
		double efd = dAry[0]; // the only input to this function is Efd
		double se = 0;
		if(this.functionType == 1){
			if(efd !=0.0 && efd >1.0E-2)
			  se = this.b * Math.pow((efd-this.a), 2)/efd;
			else
			  se =0.0;
		}
		else
			se = this.b * Math.exp(this.a * efd);
		if(Double.isNaN(se)){
			System.out.print("a, b, efd ="+this.a+","+this.b+","+efd);
			IpssLogger.getLogger().severe(("Se function returns NAN!"));
		}
		return se;
	}

	@Override public String toString() {
		String str = "E1, Se(E1), E2, Se(E2): " + e1 + ", " + se_e1 + ", " + e2
				+ ", " + se_e2;
		str += "A, B: " + a + ", " + b;
		return str;
	}
}
