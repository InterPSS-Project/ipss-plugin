 /*
  * @(#)EAExciterData.java
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

package org.interpss.dstab.control.exc.bpa.ea;

/**
 * Define controller plugin data here. The class has to following the JavaBean
 * specification with the getter/setter(s), so that it could be serialized/deserialized
 * automatically.
 *
 * @author Sherlock_Li
 */

public class BpaEaTypeExciterData {
    public BpaEaTypeExciterData() {}

    // We need to put the default values here, so that the controller could be
    // properly initialized
    private double tr       = 0.04;
    private double ka       = 40.0;
    private double ta       = 0.05;
    private double ta1      = 0.02;
    private double semax    = 0.860;
    private double efdmax   = 5.5;
    private double ke      = 1.0;
    private double te      = 2.0;
    private double se_e2   = 0.50;
    private double kf      = 0.03;
    private double tf      = 0.350;

    /**
     * @return the tr
     */
    public double getTr() {
        return tr;
    }

    /**
     * @param tr the tr to set
     */
    public void setTr(double tr) {
        this.tr = tr;
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
     * @return the ta1
     */
    public double getTa1() {
        return ta1;
    }

    /**
     * @param ta1 the ta1 to set
     */
    public void setTa1(double ta1) {
        this.ta1 = ta1;
    }

    /**
     * @return the semax
     */
    public double getSemax() {
        return semax;
    }

    /**
     * @param semax the semax to set
     */
    public void setSemax(double semax) {
        this.semax = semax;
    }

    /**
     * @return the efdmax
     */
    public double getEfdmax() {
        return efdmax;
    }

    /**
     * @param efdmax the efdmax to set
     */
    public void setEfdmax(double efdmax) {
        this.efdmax = efdmax;
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
