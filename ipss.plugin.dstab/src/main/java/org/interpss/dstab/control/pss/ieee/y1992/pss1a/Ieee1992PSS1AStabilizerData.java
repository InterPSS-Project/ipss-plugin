 /*
  * @(#)Ieee1992PSS1AStabilizerData.java   
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
  * @Date 09/15/2006
  * 
  *   Revision History
  *   ================
  *
  */


package org.interpss.dstab.control.pss.ieee.y1992.pss1a;

import org.interpss.dstab.control.base.BaseControllerData;

public class Ieee1992PSS1AStabilizerData extends BaseControllerData {
	private double ks = 10.0;
	private double k1 = 10.0;
	private double t1 = 0.05;
	private double t2 = 0.5;
	private double t3 = 0.05;
	private double t4 = 0.25;
	private double t5 = 0.1;
	private double t6 = 0.05;
	private double vstmax = 0.2;
	private double vstmin = -0.2;
	private double a1 = 0.05;
	private double a2 = 0.5;
	
	private static String[][] controllerParameters= { 
		//          min         max
		{"ks", 		"-1000.0", 	"1000.0"}, 
		{"k1", 		"-1000.0", 	"1000.0"}, 
		{"t1", 		"-1000.0", 	"1000.0"}, 
		{"t2", 		"-1000.0", 	"1000.0"}, 
		{"t3", 		"-1000.0", 	"1000.0"}, 
		{"t4", 		"-1000.0", 	"1000.0"}, 
		{"t5", 		"-1000.0", 	"1000.0"}, 
		{"t6", 		"-1000.0", 	"1000.0"}, 
		{"vstmax", 	"-1000.0", 	"1000.0"}, 
		{"vstmin", 	"-1000.0", 	"1000.0"}, 
		{"a1", 		"-1000.0", 	"1000.0"}, 
		{"a2", 		"-1000.0", 	"1000.0"} 
	};

	public Ieee1992PSS1AStabilizerData() {
		setRangeParameters(controllerParameters);
	}

	@Override
	public void setValue(String name, int value) {
	}

	@Override
	public void setValue(String name, double value) {
		if (name.equals("ks"))
			this.ks = value;
		else if (name.equals("k1"))
			this.k1 = value;
		else if (name.equals("t1"))
			this.t1 = value;
		else if (name.equals("t2"))
			this.t2 = value;
		else if (name.equals("t3"))
			this.t3 = value;
		else if (name.equals("t4"))
			this.t4 = value;
		else if (name.equals("t5"))
			this.t5 = value;
		else if (name.equals("t6"))
			this.t6 = value;
		else if (name.equals("vstmax"))
			this.vstmax = value;
		else if (name.equals("vstmin"))
			this.vstmin = value;
		else if (name.equals("a1"))
			this.a1 = value;
		else if (name.equals("a2"))
			this.a2 = value;
	}
	
	public double getA1() {
		return a1;
	}

	public void setA1(double a1) {
		this.a1 = a1;
	}

	public double getA2() {
		return a2;
	}

	public void setA2(double a2) {
		this.a2 = a2;
	}

	public double getK1() {
		return k1;
	}

	public void setK1(double k1) {
		this.k1 = k1;
	}
	
	public double getKs() {
		return ks;
	}

	public void setKs(double ks) {
		this.ks = ks;
	}

	public double getT1() {
		return t1;
	}

	public void setT1(double t1) {
		this.t1 = t1;
	}

	public double getT2() {
		return t2;
	}

	public void setT2(double t2) {
		this.t2 = t2;
	}

	public double getT3() {
		return t3;
	}

	public void setT3(double t3) {
		this.t3 = t3;
	}

	public double getT4() {
		return t4;
	}

	public void setT4(double t4) {
		this.t4 = t4;
	}

	public double getT5() {
		return t5;
	}

	public void setT5(double t5) {
		this.t5 = t5;
	}

	public double getT6() {
		return t6;
	}

	public void setT6(double t6) {
		this.t6 = t6;
	}

	public double getVstmax() {
		return vstmax;
	}

	public void setVstmax(double vstmax) {
		this.vstmax = vstmax;
	}

	public double getVstmin() {
		return vstmin;
	}

	public void setVstmin(double vstmin) {
		this.vstmin = vstmin;
	}
} 
