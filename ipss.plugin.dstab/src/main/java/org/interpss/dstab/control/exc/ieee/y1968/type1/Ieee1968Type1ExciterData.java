 /*
  * @(#)NBIeee1968Type1EditPanel.java   
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
  * @Date 04/15/2007
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.dstab.control.exc.ieee.y1968.type1;

import org.interpss.dstab.control.base.BaseControllerData;

import com.interpss.common.exp.InterpssRuntimeException;

/**
 * Ieee1968Type1 type exciter data object definition. It needs to follow
 * JavaBean convention so that the controller data object be serialized/
 * deserialized for grid computing.
 * 
 * @author mzhou
 *
 */
public class Ieee1968Type1ExciterData extends BaseControllerData {
	// define all data fields 
	private double ka = 50.0;
	private double ta = 0.06;
	private double vrmax = 2.0;
	private double vrmin = -0.9;
	private double ke = 1.0;
	private double te = 0.46;
	private double e1 = 3.1;
	private double seE1 = 0.33;
	private double e2 = 2.3;
	private double seE2 = 0.10;
	private double kf = 0.1;
	private double tf = 1.0;
	private double tr =0.3;
	
	// define data field range for GUI data editing 
	private static String[][] controllerParameters= { 
		//          min         max
		{"ka", 		"-1000.0", 	"1000.0"}, 
		{"ta", 		"-1000.0", 	"1000.0"}, 
		{"vrmax", 	"-1000.0", 	"1000.0"}, 
		{"vrmin", 	"-1000.0", 	"1000.0"}, 
		{"ke", 		"-1000.0", 	"1000.0"}, 
		{"te", 		"-1000.0", 	"1000.0"}, 
		{"e1", 		"-1000.0", 	"1000.0"}, 
		{"seE1", 	"-1000.0", 	"1000.0"}, 
		{"e2", 		"-1000.0", 	"1000.0"}, 
		{"seE2", 	"-1000.0", 	"1000.0"}, 
		{"kf", 		"-1000.0", 	"1000.0"}, 
		{"tf", 		"-1000.0", 	"1000.0"} ,
		{"tr", 		"-1000.0", 	"1000.0"} 
	};

	/**
	 * constructor
	 */
	public Ieee1968Type1ExciterData() {
		setRangeParameters(controllerParameters);
	}

	@Override public void setValue(String name, int value) {
		throw new InterpssRuntimeException("method not implementation");
	}

	/**
	 * override the setValue() method 
	 */
	@Override public void setValue(String name, double value) {
		if (name.equals("ka"))			this.ka = value;
		else if (name.equals("ta"))		this.ta = value;
		else if (name.equals("vrmax"))	this.vrmax = value;
		else if (name.equals("vrmin"))	this.vrmin = value;
		else if (name.equals("ke"))		this.ke = value;
		else if (name.equals("te"))		this.te = value;
		else if (name.equals("e1"))		this.e1 = value;
		else if (name.equals("seE1"))	this.seE1 = value;
		else if (name.equals("e2"))		this.e2 = value;
		else if (name.equals("seE2"))	this.seE2 = value;
		else if (name.equals("kf"))		this.kf = value;
		else if (name.equals("tf"))		this.tf = value;
		else if (name.equals("tr"))		this.tr = value;
	}
	
	// define data field getter
	public double getKa() {		return ka;	}
	public double getTa() {		return ta;	}
	public double getVrmax() {	return vrmax;	}
	public double getVrmin() {	return vrmin;	}
	public double getE1() {		return e1;	}
	public double getE2() {		return e2;	}
	public double getKe() {		return ke;	}
	public double getKf() {		return kf;	}
	public double getSeE1() {	return seE1;	}
	public double getSeE2() {	return seE2;	}
	public double getTe() {		return te;	}
	public double getTf() {		return tf;	}
	public double getTr() {		return tr;	}

	// define data field setter
	public void setKa(final double ka) {		this.ka = ka;	}
	public void setTa(final double ta) { 		this.ta = ta;	}
	public void setVrmax(final double vrmax) {	this.vrmax = vrmax;	}
	public void setVrmin(final double vrmin) {	this.vrmin = vrmin;	}
	public void setE1(double e1) {				this.e1 = e1;	}
	public void setE2(double e2) {				this.e2 = e2;	}
	public void setKe(double ke) {				this.ke = ke;	}
	public void setKf(double kf) {				this.kf = kf;	}
	public void setSeE1(double seE1) {			this.seE1 = seE1;	}
	public void setSeE2(double seE2) {			this.seE2 = seE2;	}
	public void setTe(double te) {				this.te = te;	}
	public void setTf(double tf) {				this.tf = tf;	}
	public void setTr(double tr) {				this.tr = tr;	}
}

