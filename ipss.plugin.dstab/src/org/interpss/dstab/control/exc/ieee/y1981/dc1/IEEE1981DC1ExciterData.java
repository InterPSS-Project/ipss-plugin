 /*
  * @(#)FAExciterData.java
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

package org.interpss.dstab.control.exc.ieee.y1981.dc1;

/**
 * Define controller plugin data here. The class has to following the JavaBean
 * specification with the getter/setter(s), so that it could be serialized/deserialized
 * automatically.
 *
 * @author Sherlock_Li
 */

public class IEEE1981DC1ExciterData {
    public IEEE1981DC1ExciterData() {}

    // We need to put the default values here, so that the controller could be
    // properly initialized
    private double tc       = 21.84;
    private double tb       = 52.73;
    private double ka       = 39.35;
    private double ta       = 0.02;
    private double vrmax    = 6.0;
    private double vrmin    = -6.0;
    private double ke       = 1.0;
    private double te       = 2.0;
    private double e1    = 6.0;
    private double e2    = 7.2;
    private double se_e1    = 0.1;
    private double se_e2    = 0.05;
    private double kf       = 0.03;
    private double tf       = 0.350;

    /**
     * @return the tc
     */
    public double getTc() {
        return tc;
    }

    /**
     * @param tc the tc to set
     */
    public void setTc(double tc) {
        this.tc = tc;
    }

    /**
     * @return the tb
     */
    public double getTb() {
        return tb;
    }

    /**
     * @param tb the tb to set
     */
    public void setTb(double tb) {
        this.tb = tb;
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
     * @return the ke
     */
    public double getKe() {
        return ke;
    }

    /**
     * @param ke the ke to set
     */
    public void setKe(double ke) {
        this.ke = ke;
    }

    /**
     * @return the te
     */
    public double getTe() {
        return te;
    }

    /**
     * @param te the te to set
     */
    public void setTe(double te) {
        this.te = te;
    }

    /**
	 * @return the e1
	 */
	public double getE1() {
		return e1;
	}

	/**
	 * @return the e2
	 */
	public double getE2() {
		return e2;
	}

	/**
	 * @param e1 the e1 to set
	 */
	public void setE1(double e1) {
		this.e1 = e1;
	}

	/**
	 * @param e2 the e2 to set
	 */
	public void setE2(double e2) {
		this.e2 = e2;
	}

	/**
     * @return the se_e1
     */
    public double getSe_e1() {
        return se_e1;
    }

    /**
     * @param se_e1 the se_e1 to set
     */
    public void setSe_e1(double se_e1) {
        this.se_e1 = se_e1;
    }

    /**
     * @return the se_e2
     */
    public double getSe_e2() {
        return se_e2;
    }

    /**
     * @param se_e2 the se_e2 to set
     */
    public void setSe_e2(double se_e2) {
        this.se_e2 = se_e2;
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
