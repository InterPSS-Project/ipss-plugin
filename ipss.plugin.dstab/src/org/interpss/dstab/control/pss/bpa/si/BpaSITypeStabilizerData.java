 /*
  * @(#)SIPSSData.java
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

package org.interpss.dstab.control.pss.bpa.si;

/**
 * Define controller plugin data here. The class has to following the JavaBean
 * specification with the getter/setter(s), so that it could be serialized/deserialized
 * automatically.
 *
 * @author Sherlock_Li
 */

public class BpaSITypeStabilizerData {
    public BpaSITypeStabilizerData() {}

    // We need to put the default values here, so that the controller could be
    // properly initialized
    private double trw       = 0.0;
    private double t5        = 5.0;
    private double t6        = 5.0;
    private double t7        = 5.0;
    private double kr        = 1.0;
    private double trp       = 0.0;
    private double tw        = 0.652;
    private double tw1       = 5.0;
    private double tw2       = 5.0;
    private double t10       = 0.1;
    private double ks        = 1.0;
    private double t9        = 0.2;
    private double t12       = 0.1;
    private double kp        = 3.0;
    private double t1        = 0.2;
    private double t2        = 0.03;
    private double t13       = 0.0;
    private double t14       = 0.0;
    private double t3        = 0.32;
    private double t4        = 0.03;
    private double vsMax      = 0.1;
    private double vsMin      = -0.1;

    /**
     * @return the trw
     */
    public double getTrw() {
        return trw;
    }

    /**
     * @param trw the trw to set
     */
    public void setTrw(double trw) {
        this.trw = trw;
    }

    /**
     * @return the t5
     */
    public double getT5() {
        return t5;
    }

    /**
     * @param t5 the t5 to set
     */
    public void setT5(double t5) {
        this.t5 = t5;
    }

    /**
     * @return the t6
     */
    public double getT6() {
        return t6;
    }

    /**
     * @param t6 the t6 to set
     */
    public void setT6(double t6) {
        this.t6 = t6;
    }

    /**
     * @return the t7
     */
    public double getT7() {
        return t7;
    }

    /**
     * @param t7 the t7 to set
     */
    public void setT7(double t7) {
        this.t7 = t7;
    }

    /**
     * @return the kr
     */
    public double getKr() {
        return kr;
    }

    /**
     * @param kr the kr to set
     */
    public void setKr(double kr) {
        this.kr = kr;
    }

    /**
     * @return the trp
     */
    public double getTrp() {
        return trp;
    }

    /**
     * @param trp the trp to set
     */
    public void setTrp(double trp) {
        this.trp = trp;
    }

    /**
     * @return the tw
     */
    public double getTw() {
        return tw;
    }

    /**
     * @param tw the tw to set
     */
    public void setTw(double tw) {
        this.tw = tw;
    }

    /**
     * @return the tw1
     */
    public double getTw1() {
        return tw1;
    }

    /**
     * @param tw1 the tw1 to set
     */
    public void setTw1(double tw1) {
        this.tw1 = tw1;
    }

    /**
     * @return the tw2
     */
    public double getTw2() {
        return tw2;
    }

    /**
     * @param tw2 the tw2 to set
     */
    public void setTw2(double tw2) {
        this.tw2 = tw2;
    }

    /**
     * @return the t10
     */
    public double getT10() {
        return t10;
    }

    /**
     * @param t10 the t10 to set
     */
    public void setT10(double t10) {
        this.t10 = t10;
    }

    /**
     * @return the ks
     */
    public double getKs() {
        return ks;
    }

    /**
     * @param ks the ks to set
     */
    public void setKs(double ks) {
        this.ks = ks;
    }

    /**
     * @return the t9
     */
    public double getT9() {
        return t9;
    }

    /**
     * @param t9 the t9 to set
     */
    public void setT9(double t9) {
        this.t9 = t9;
    }

    /**
     * @return the t12
     */
    public double getT12() {
        return t12;
    }

    /**
     * @param t12 the t12 to set
     */
    public void setT12(double t12) {
        this.t12 = t12;
    }

    /**
     * @return the kp
     */
    public double getKp() {
        return kp;
    }

    /**
     * @param kp the kp to set
     */
    public void setKp(double kp) {
        this.kp = kp;
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
     * @return the t13
     */
    public double getT13() {
        return t13;
    }

    /**
     * @param t13 the t13 to set
     */
    public void setT13(double t13) {
        this.t13 = t13;
    }

    /**
     * @return the t14
     */
    public double getT14() {
        return t14;
    }

    /**
     * @param t14 the t14 to set
     */
    public void setT14(double t14) {
        this.t14 = t14;
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
	 * @return the vsMax
	 */
	public double getVsMax() {
		return vsMax;
	}

	/**
	 * @return the vsMin
	 */
	public double getVsMin() {
		return vsMin;
	}

	/**
	 * @param vsMax the vsMax to set
	 */
	public void setVsMax(double vsMax) {
		this.vsMax = vsMax;
	}

	/**
	 * @param vsMin the vsMin to set
	 */
	public void setVsMin(double vsMin) {
		this.vsMin = vsMin;
	}




}
