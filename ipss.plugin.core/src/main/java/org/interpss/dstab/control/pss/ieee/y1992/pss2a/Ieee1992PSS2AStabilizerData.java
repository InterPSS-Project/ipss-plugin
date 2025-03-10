 /*
  * @(#)Ieee1992PSS2AStabilizerData.java   
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


package org.interpss.dstab.control.pss.ieee.y1992.pss2a;

import org.interpss.dstab.control.base.BaseControllerData;

public class Ieee1992PSS2AStabilizerData extends BaseControllerData {
	private double ks1 = 10.0;
	private double t1 = 0.05;
	private double t2 = 0.5;
	private double t3 = 0.05;
	private double t4 = 0.25;
	private double t6 = 0.05;
	private double t7 = 0.05;
	private double t8 = 0.1;
	private double t9 = 0.05;
	private int n = 1;
	private int m = 1;
	private double vstmax = 0.2;
	private double vstmin = -0.2;
	private double ks2 = 1.0;
	private double ks3 = 1.0;
	private double tw1 = 0.1;
	private double tw2 = 0.05;
	private double tw3 = 0.1;
	private double tw4 = 0.05;
	
	private static String[][] controllerParameters= { 
		//          min         max
		{"ks1",		"-1000.0", 	"1000.0"}, 
		{"t1", 		"-1000.0", 	"1000.0"}, 
		{"t2", 		"-1000.0", 	"1000.0"}, 
		{"t3", 		"-1000.0", 	"1000.0"}, 
		{"t4", 		"-1000.0", 	"1000.0"}, 
		{"t6", 		"-1000.0", 	"1000.0"}, 
		{"t7", 		"-1000.0", 	"1000.0"}, 
		{"t8", 		"-1000.0", 	"1000.0"}, 
		{"t9", 		"-1000.0", 	"1000.0"}, 
		{"n", 		"0", 		"100"}, 
		{"m", 		"0", 		"100"}, 
		{"vstmax", 	"-1000.0", 	"1000.0"}, 
		{"vstmin", 	"-1000.0", 	"1000.0"}, 
		{"ks2",		"-1000.0", 	"1000.0"}, 
		{"ks3",		"-1000.0", 	"1000.0"}, 
		{"tw1",		"-1000.0", 	"1000.0"}, 
		{"tw2",		"-1000.0", 	"1000.0"}, 
		{"tw3",		"-1000.0", 	"1000.0"}, 
		{"tw4",		"-1000.0", 	"1000.0"} 
	};

	public Ieee1992PSS2AStabilizerData() {
		setRangeParameters(controllerParameters);
	}

	@Override
	public void setValue(String name, int value) {
		if (name.equals("n"))
			this.n = value;
		else if (name.equals("m"))
			this.m = value;
	}
	
	@Override
	public void setValue(String name, double value) {
		if (name.equals("ks1"))
			this.ks1 = value;
		else if (name.equals("t1"))
			this.t1 = value;
		else if (name.equals("t2"))
			this.t2 = value;
		else if (name.equals("t3"))
			this.t3 = value;
		else if (name.equals("t4"))
			this.t4 = value;
		else if (name.equals("t6"))
			this.t6 = value;
		else if (name.equals("t7"))
			this.t7 = value;
		else if (name.equals("t8"))
			this.t8 = value;
		else if (name.equals("t9"))
			this.t9 = value;
		else if (name.equals("vstmax"))
			this.vstmax = value;
		else if (name.equals("vstmin"))
			this.vstmin = value;
		else if (name.equals("ks2"))
			this.ks2 = value;
		else if (name.equals("ks3"))
			this.ks3 = value;
		else if (name.equals("tw1"))
			this.tw1 = value;
		else if (name.equals("tw2"))
			this.tw2 = value;
		else if (name.equals("tw3"))
			this.tw3 = value;
		else if (name.equals("tw4"))
			this.tw4 = value;
	}
	
	public double getKs1() {
		return ks1;
	}

	public void setKs1(double ks1) {
		this.ks1 = ks1;
	}

	public double getKs2() {
		return ks2;
	}

	public void setKs2(double ks2) {
		this.ks2 = ks2;
	}

	public double getKs3() {
		return ks3;
	}

	public void setKs3(double ks3) {
		this.ks3 = ks3;
	}


	public double getTw1() {
		return tw1;
	}

	public void setTw1(double tw1) {
		this.tw1 = tw1;
	}

	public double getTw2() {
		return tw2;
	}

	public void setTw2(double tw2) {
		this.tw2 = tw2;
	}

	public double getTw3() {
		return tw3;
	}

	public void setTw3(double tw3) {
		this.tw3 = tw3;
	}

	public double getTw4() {
		return tw4;
	}

	public void setTw4(double tw4) {
		this.tw4 = tw4;
	}

	public int getM() {
		return m;
	}

	public void setM(int m) {
		this.m = m;
	}

	public int getN() {
		return n;
	}

	public void setN(int n) {
		this.n = n;
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

	public double getT6() {
		return t6;
	}

	public void setT6(double t6) {
		this.t6 = t6;
	}

	public double getT7() {
		return t7;
	}

	public void setT7(double t7) {
		this.t7 = t7;
	}

	public double getT8() {
		return t8;
	}

	public void setT8(double t8) {
		this.t8 = t8;
	}

	public double getT9() {
		return t9;
	}

	public void setT9(double t9) {
		this.t9 = t9;
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
