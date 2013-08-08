 /*
  * @(#)Ieee1968Type1sExciterData.java   
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

package org.interpss.dstab.control.exc.ieee.y1968.type1s;

import org.interpss.dstab.control.base.BaseControllerData;

public class Ieee1968Type1sExciterData extends BaseControllerData {
	private double ka = 50.0;
	private double ta = 0.05;
	private double kp = 10.0;
	private double vrmin = 0.0;
	private double kf = 0.1;
	private double tf = 0.5;
	
	private static String[][] controllerParameters= { 
		//          min         max
		{"ka", 		"-1000.0", 	"1000.0"}, 
		{"ta", 		"-1000.0", 	"1000.0"}, 
		{"kp", 		"-1000.0", 	"1000.0"}, 
		{"vrmin", 	"-1000.0", 	"1000.0"}, 
		{"kf", 		"-1000.0", 	"1000.0"}, 
		{"tf", 		"-1000.0", 	"1000.0"} 
	};

	public Ieee1968Type1sExciterData() {
		setRangeParameters(controllerParameters);
	}

	@Override
	public void setValue(String name, int value) {
	}

	@Override
	public void setValue(String name, double value) {
		if (name.equals("ka"))
			this.ka = value;
		else if (name.equals("ta"))
			this.ta = value;
		else if (name.equals("kp"))
			this.kp = value;
		else if (name.equals("vrmin"))
			this.vrmin = value;
		else if (name.equals("kf"))
			this.kf = value;
		else if (name.equals("tf"))
			this.tf = value;
	}
	
	public double getKa() {
		return ka;
	}
	public void setKa(final double ka) {
		this.ka = ka;
	}
	public double getTa() {
		return ta;
	}
	public void setTa(final double ta) {
		this.ta = ta;
	}
	public double getVrmin() {
		return vrmin;
	}
	public void setVrmin(final double vrmin) {
		this.vrmin = vrmin;
	}
	public double getKf() {
		return kf;
	}
	public void setKf(double kf) {
		this.kf = kf;
	}
	public double getTf() {
		return tf;
	}
	public void setTf(double tf) {
		this.tf = tf;
	}

	public double getKp() {
		return kp;
	}

	public void setKp(double kp) {
		this.kp = kp;
	}
}

