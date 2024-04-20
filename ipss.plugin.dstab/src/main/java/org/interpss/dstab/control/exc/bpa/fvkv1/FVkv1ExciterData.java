 /*
  * @(#)FVkv1ExciterData.java
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

package org.interpss.dstab.control.exc.bpa.fvkv1;

/**
 * Define controller plugin data here. The class has to following the JavaBean
 * specification with the getter/setter(s), so that it could be serialized/deserialized
 * automatically.
 *
 * @author Sherlock_Li
 */

public class FVkv1ExciterData {
    public FVkv1ExciterData() {}

    // We need to put the default values here, so that the controller could be
    // properly initialized
    private double rc        = 0;
    private double xc        = 0;
    private double tr        = 1.0;
    private double k        = 22;
    private double kv       = 1.0;
    private double t1       = 1.0;
    private double t2       = 4.0;
    private double t3       = 1.0;
    private double t4       = 1.0;
    private double ka       = 10.0;
    private double ta       = 0.01;
    private double vamax    = 10.0;
    private double vamin    = -10.0;
    private double vrmax    = 6.25;
    private double vrmin    = -5.14;
    private double kc       = 0.065;
    private double kf       = 0.0;
    private double tf       = 100.0;

    /**
	 * @return the rc
	 */
	public double getRc() {
		return rc;
	}

	/**
	 * @return the xc
	 */
	public double getXc() {
		return xc;
	}

	/**
	 * @return the tr
	 */
	public double getTr() {
		return tr;
	}

	/**
	 * @param rc the rc to set
	 */
	public void setRc(double rc) {
		this.rc = rc;
	}

	/**
	 * @param xc the xc to set
	 */
	public void setXc(double xc) {
		this.xc = xc;
	}

	/**
	 * @param tr the tr to set
	 */
	public void setTr(double tr) {
		this.tr = tr;
	}

	/**
     * @return the k
     */
    public double getK() {
        return k;
    }

    /**
     * @param k the k to set
     */
    public void setK(double k) {
        this.k = k;
    }

    /**
     * @return the kv
     */
    public double getKv() {
        return kv;
    }

    /**
     * @param kv the kv to set
     */
    public void setKv(double kv) {
        this.kv = kv;
    }

    /**
     * @return the t1
     */
    public double getT1() {
        return t1;
    }

    /**
     * @param t1 the t1 to set
     */
    public void setT1(double t1) {
        this.t1 = t1;
    }

    /**
     * @return the t2
     */
    public double getT2() {
        return t2;
    }

    /**
     * @param t2 the t2 to set
     */
    public void setT2(double t2) {
        this.t2 = t2;
    }

    /**
     * @return the t3
     */
    public double getT3() {
        return t3;
    }

    /**
     * @param t3 the t3 to set
     */
    public void setT3(double t3) {
        this.t3 = t3;
    }

    /**
     * @return the t4
     */
    public double getT4() {
        return t4;
    }

    /**
     * @param t4 the t4 to set
     */
    public void setT4(double t4) {
        this.t4 = t4;
    }

    /**
     * @return the ka
     */
    public double getKa() {
        return ka;
    }

    /**
     * @param ka the ka to set
     */
    public void setKa(double ka) {
        this.ka = ka;
    }

    /**
     * @return the ta
     */
    public double getTa() {
        return ta;
    }

    /**
     * @param ta the ta to set
     */
    public void setTa(double ta) {
        this.ta = ta;
    }

    /**
     * @return the vamax
     */
    public double getVamax() {
        return vamax;
    }

    /**
     * @param vamax the vamax to set
     */
    public void setVamax(double vamax) {
        this.vamax = vamax;
    }

    /**
     * @return the vamin
     */
    public double getVamin() {
        return vamin;
    }

    /**
     * @param vamin the vamin to set
     */
    public void setVamin(double vamin) {
        this.vamin = vamin;
    }

    /**
     * @return the vrmax
     */
    public double getVrmax() {
        return vrmax;
    }

    /**
     * @param vrmax the vrmax to set
     */
    public void setVrmax(double vrmax) {
        this.vrmax = vrmax;
    }

    /**
     * @return the vrmin
     */
    public double getVrmin() {
        return vrmin;
    }

    /**
     * @param vrmin the vrmin to set
     */
    public void setVrmin(double vrmin) {
        this.vrmin = vrmin;
    }

    /**
     * @return the kc
     */
    public double getKc() {
        return kc;
    }

    /**
     * @param kc the kc to set
     */
    public void setKc(double kc) {
        this.kc = kc;
    }

    /**
     * @return the kf
     */
    public double getKf() {
        return kf;
    }

    /**
     * @param kf the kf to set
     */
    public void setKf(double kf) {
        this.kf = kf;
    }

    /**
     * @return the tf
     */
    public double getTf() {
        return tf;
    }

    /**
     * @param tf the tf to set
     */
    public void setTf(double tf) {
        this.tf = tf;
    }



}
