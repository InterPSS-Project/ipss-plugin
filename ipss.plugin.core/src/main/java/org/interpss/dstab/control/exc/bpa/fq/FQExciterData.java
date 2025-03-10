 /*
  * @(#)FVkv0ExciterData.java
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
  * @Date 05/23/2011
  *
  *   Revision History
  *   ================
  *
  */

package org.interpss.dstab.control.exc.bpa.fq;

/**
 * Define controller plugin data here. The class has to following the JavaBean
 * specification with the getter/setter(s), so that it could be serialized/deserialized
 * automatically.
 *
 * @author Sherlock_Li
 */

public class FQExciterData {
    public FQExciterData() {}

    // We need to put the default values here, so that the controller could be
    // properly initialized
    private double rc        = 0;
	private double xc        = 0;
    private double tr        = 1.0;
    private double k        = 22.0;
    private double kv       = 1.0;
    private double t1       = 1.0;
    private double t2       = 4.0;
    private double va1max	= 100.0;
    private double va1min	= -100;
    private double t3       = 1.0;
    private double t4       = 1.0;
    private double ka       = 10.0;
    private double ta       = 0.01;
    private double vamax    = 10.0;
    private double vamin    = -10.0;
    private double kb		= 10.0;
    private double t5		= 0.01;
    private double vrmax    = 100.0;
    private double vrmin    = -100.0;
    private double ke		= 2.0;
    private double ve		= 2.0;
    private double e1		= 3.0;
    private double se_e1	= 1.0;
    private double se_e2	= 0.50;
    private double kd		= 10.0;
    private double kc       = 0.14;
    private double efdmax   = 5.30;
    
    
	public double getRc() {
		return rc;
	}
	public void setRc(double rc) {
		this.rc = rc;
	}
	public double getXc() {
		return xc;
	}
	public void setXc(double xc) {
		this.xc = xc;
	}
	public double getTr() {
		return tr;
	}
	public void setTr(double tr) {
		this.tr = tr;
	}
	public double getK() {
		return k;
	}
	public void setK(double k) {
		this.k = k;
	}
	public double getKv() {
		return kv;
	}
	public void setKv(double kv) {
		this.kv = kv;
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
	public double getVa1max() {
		return va1max;
	}
	public void setVa1max(double va1max) {
		this.va1max = va1max;
	}
	public double getVa1min() {
		return va1min;
	}
	public void setVa1min(double va1min) {
		this.va1min = va1min;
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
	public double getKa() {
		return ka;
	}
	public void setKa(double ka) {
		this.ka = ka;
	}
	public double getTa() {
		return ta;
	}
	public void setTa(double ta) {
		this.ta = ta;
	}
	public double getVamax() {
		return vamax;
	}
	public void setVamax(double vamax) {
		this.vamax = vamax;
	}
	public double getVamin() {
		return vamin;
	}
	public void setVamin(double vamin) {
		this.vamin = vamin;
	}
	public double getKb() {
		return kb;
	}
	public void setKb(double kb) {
		this.kb = kb;
	}
	public double getT5() {
		return t5;
	}
	public void setT5(double t5) {
		this.t5 = t5;
	}
	public double getVrmax() {
		return vrmax;
	}
	public void setVrmax(double vrmax) {
		this.vrmax = vrmax;
	}
	public double getVrmin() {
		return vrmin;
	}
	public void setVrmin(double vrmin) {
		this.vrmin = vrmin;
	}
	public double getKe() {
		return ke;
	}
	public void setKe(double ke) {
		this.ke = ke;
	}
	public double getVe() {
		return ve;
	}
	public void setVe(double ve) {
		this.ve = ve;
	}
	public double getE1() {
		return e1;
	}
	public void setE1(double e1) {
		this.e1 = e1;
	}
	public double getSe_e1() {
		return se_e1;
	}
	public void setSe_e1(double seE1) {
		se_e1 = seE1;
	}
	public double getSe_e2() {
		return se_e2;
	}
	public void setSe_e2(double seE2) {
		se_e2 = seE2;
	}
	public double getKd() {
		return kd;
	}
	public void setKd(double kd) {
		this.kd = kd;
	}
	public double getKc() {
		return kc;
	}
	public void setKc(double kc) {
		this.kc = kc;
	}
	public double getEfdmax() {
		return efdmax;
	}
	public void setEfdmax(double efdmax) {
		this.efdmax = efdmax;
	}


}
