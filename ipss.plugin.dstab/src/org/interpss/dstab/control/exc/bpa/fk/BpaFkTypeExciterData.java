 /*
  * @(#)FKExciterData.java
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

package org.interpss.dstab.control.exc.bpa.fk;

/**
 * Define controller plugin data here. The class has to following the JavaBean
 * specification with the getter/setter(s), so that it could be serialized/deserialized
 * automatically.
 *
 * @author Sherlock_Li
 */

public class BpaFkTypeExciterData {
    public BpaFkTypeExciterData() {}

    // We need to put the default values here, so that the controller could be
    // properly initialized
    private double vimax        = 1.4;
    private double vimin       = 0.0;
    private double tc       = 0.16;
    private double tb       = 0.01;
    private double ka       = 1.0;
    private double ta       = 0.003;
    private double vrmax    = 5.0;
    private double vrmin    = -3.0;
    private double kc       = 0.067;
	private double kf       = 0.1;
    private double tf       = 1.0;

    /**
     * @return the vimax
     */
    public double getVimax() {
        return vimax;
    }

    /**
     * @param vimax the vimax to set
     */
    public void setVimax(double vimax) {
        this.vimax = vimax;
    }

    /**
     * @return the vimin
     */
    public double getVimin() {
        return vimin;
    }

    /**
     * @param vimin the vimin to set
     */
    public void setVimin(double vimin) {
        this.vimin = vimin;
    }

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
	 * @return the tf
	 */
	public double getTf() {
		return tf;
	}

	/**
	 * @param kf the kf to set
	 */
	public void setKf(double kf) {
		this.kf = kf;
	}

	/**
	 * @param tf the tf to set
	 */
	public void setTf(double tf) {
		this.tf = tf;
	}





}
