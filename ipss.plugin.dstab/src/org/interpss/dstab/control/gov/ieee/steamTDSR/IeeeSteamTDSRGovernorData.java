 /*
  * @(#)IeeeSteamTDSRGovernorData.java   
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

package org.interpss.dstab.control.gov.ieee.steamTDSR;

import org.interpss.dstab.control.base.BaseControllerData;

public class IeeeSteamTDSRGovernorData extends BaseControllerData {
	private double k = 10.0;
	private double t1 = 0.5;
	private double t2 = 0.1;
	private double t3 = 1.0;
	private double pmax = 2.0;
	private double pmin = 0.0;
	private double pup = 1.2;
	private double pdown = 0.0;
	private double tch = 1.2;
	private double trh1 = 1.2;
	private double trh2 = 1.2;
	private double tco = 1.2;
	private double fvhp = 0.1;
	private double fhp = 0.1;
	private double fip = 0.3;
	private double flp = 0.5;
	
	private static String[][] controllerParameters= { 
		//          min         max
		{"k", 		"-1000.0", 	"1000.0"}, 
		{"t1", 		"-1000.0", 	"1000.0"}, 
		{"t2", 		"-1000.0", 	"1000.0"}, 
		{"t3", 		"-1000.0", 	"1000.0"}, 
		{"pmax", 	"-1000.0", 	"1000.0"}, 
		{"pmin", 	"-1000.0", 	"1000.0"}, 
		{"pup", 	"-1000.0", 	"1000.0"}, 
		{"pdown", 	"-1000.0", 	"1000.0"}, 
		{"tch",		"-1000.0", 	"1000.0"}, 
		{"trh1", 	"-1000.0", 	"1000.0"}, 
		{"trh2", 	"-1000.0", 	"1000.0"}, 
		{"tco", 	"-1000.0", 	"1000.0"}, 
		{"fvhp", 	"-1000.0", 	"1000.0"}, 
		{"fhp", 	"-1000.0", 	"1000.0"}, 
		{"fip", 	"-1000.0", 	"1000.0"}, 
		{"flp",		"-1000.0", 	"1000.0"} 
	};

	public IeeeSteamTDSRGovernorData() {
		setRangeParameters(controllerParameters);
	}

	@Override
	public void setValue(String name, int value) {
	}
	
	@Override
	public void setValue(String name, double value) {
		if (name.equals("k"))
			this.k = value;
		else if (name.equals("t1"))
			this.t1 = value;
		else if (name.equals("t2"))
			this.t2 = value;
		else if (name.equals("t3"))
			this.t3 = value;
		else if (name.equals("pmax"))
			this.pmax = value;
		else if (name.equals("pmin"))
			this.pmin = value;
		else if (name.equals("pup"))
			this.pup = value;
		else if (name.equals("pdown"))
			this.pdown = value;
		else if (name.equals("tch"))
			this.tch = value;
		else if (name.equals("trh1"))
			this.trh1 = value;
		else if (name.equals("trh2"))
			this.trh2 = value;
		else if (name.equals("tco"))
			this.tco = value;
		else if (name.equals("fvhp"))
			this.fvhp = value;
		else if (name.equals("fhp"))
			this.fhp = value;
		else if (name.equals("fip"))
			this.fip = value;
		else if (name.equals("flp"))
			this.flp = value;
	}
	
	public double getK() {
		return k;
	}

	public void setK(double k) {
		this.k = k;
	}

	public double getPdown() {
		return pdown;
	}

	public void setPdown(double pdown) {
		this.pdown = pdown;
	}

	public double getPmax() {
		return pmax;
	}

	public void setPmax(double pmax) {
		this.pmax = pmax;
	}

	public double getPmin() {
		return pmin;
	}

	public void setPmin(double pmin) {
		this.pmin = pmin;
	}

	public double getPup() {
		return pup;
	}

	public void setPup(double pup) {
		this.pup = pup;
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

	public double getTch() {
		return tch;
	}

	public void setTch(double tch) {
		this.tch = tch;
	}

	public double getFip() {
		return fip;
	}

	public void setFip(double fip) {
		this.fip = fip;
	}

	public double getFlp() {
		return flp;
	}

	public void setFlp(double flp) {
		this.flp = flp;
	}

	public double getTco() {
		return tco;
	}

	public void setTco(double tco) {
		this.tco = tco;
	}

	public double getFhp() {
		return fhp;
	}

	public void setFhp(double fhp) {
		this.fhp = fhp;
	}

	public double getFvhp() {
		return fvhp;
	}

	public void setFvhp(double fvhp) {
		this.fvhp = fvhp;
	}

	public double getTrh1() {
		return trh1;
	}

	public void setTrh1(double trh1) {
		this.trh1 = trh1;
	}

	public double getTrh2() {
		return trh2;
	}

	public void setTrh2(double trh2) {
		this.trh2 = trh2;
	}
} 
