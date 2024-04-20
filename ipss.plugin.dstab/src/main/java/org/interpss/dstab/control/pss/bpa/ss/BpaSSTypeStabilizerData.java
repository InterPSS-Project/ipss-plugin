 /*
  * @(#)SSPSSData.java
  *
  * Copyright (C) 2011 www.interpss.org
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
  * @Author Sherlock Li
  * @Version 1.0
  * @Date 06/08/2011
  *
  *   Revision History
  *   ================
  *
  */

package org.interpss.dstab.control.pss.bpa.ss;

/**
 * Define controller plugin data here. The class has to following the JavaBean
 * specification with the getter/setter(s), so that it could be serialized/deserialized
 * automatically.
 *
 * @author Sherlock_Li
 */

public class BpaSSTypeStabilizerData {
    public BpaSSTypeStabilizerData() {}

    // We need to put the default values here, so that the controller could be
    // properly initialized
    private double kqs       = 0.5;
    public double getKqs() {
		return kqs;
	}
	public void setKqs(double kqs) {
		this.kqs = kqs;
	}
	public double getTqs() {
		return tqs;
	}
	public void setTqs(double tqs) {
		this.tqs = tqs;
	}
	public double getTq() {
		return tq;
	}
	public void setTq(double tq) {
		this.tq = tq;
	}
	public double getTq11() {
		return tq11;
	}
	public void setTq11(double tq11) {
		this.tq11 = tq11;
	}
	public double getTq1() {
		return tq1;
	}
	public void setTq1(double tq1) {
		this.tq1 = tq1;
	}
	public double getTq21() {
		return tq21;
	}
	public void setTq21(double tq21) {
		this.tq21 = tq21;
	}
	public double getTq2() {
		return tq2;
	}
	public void setTq2(double tq2) {
		this.tq2 = tq2;
	}
	public double getTq31() {
		return tq31;
	}
	public void setTq31(double tq31) {
		this.tq31 = tq31;
	}
	public double getTq3() {
		return tq3;
	}
	public void setTq3(double tq3) {
		this.tq3 = tq3;
	}
	public double getVsmax() {
		return vsmax;
	}
	public void setVsmax(double vsmax) {
		this.vsmax = vsmax;
	}
	public double getVcutoff() {
		return Vcutoff;
	}
	public void setVcutoff(double vcutoff) {
		Vcutoff = vcutoff;
	}

	/**
	 * @param vsmin the vsmin to set
	 */
	public void setVsmin(double vsmin) {
		this.vsmin = vsmin;
	}
	/**
	 * @return the vsmin
	 */
	public double getVsmin() {
		return vsmin;
	}

	private double tqs       = 0.0;
    private double tq        = 10.0;
    private double tq11      = 1.3;
    private double tq1       = 0.2;
    private double tq21      = 1.3;
    private double tq2       = 0.0;
    private double tq31      = 0.0;
    private double tq3       = 5.0;
    private double vsmax     = 0.1;
    private double Vcutoff   = 1.0;
    private double vsmin     =0.0;






}
