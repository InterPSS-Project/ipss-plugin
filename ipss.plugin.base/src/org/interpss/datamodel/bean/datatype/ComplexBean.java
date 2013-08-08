/*
 * @(#)ComplexBean.java   
 *
 * Copyright (C) 2008-2013 www.interpss.org
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
 * @Date 01/10/2013
 * 
 *   Revision History
 *   ================
 *
 */

package org.interpss.datamodel.bean.datatype;

import org.apache.commons.math3.complex.Complex;


public class ComplexBean {
	public double
		re,				// real part
		im;				// imaginary part
	
	public ComplexBean() { }
	public ComplexBean(double re, double im) {this.re = re; this.im = im; }
	public ComplexBean(Complex c) {this.re = c.getReal(); this.im = c.getImaginary(); }

	public Complex toComplex() { return new Complex(this.re, this.im); }
}