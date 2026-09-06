package org.interpss.dstab.control.pss.ieee.y2016.pss2c;

import org.interpss.dstab.control.pss.ieee.y1992.pss2b.Ieee1992PSS2BStabilizerData;

/** Complete PSS/E PSS2C parameter set from IEEE Std 421.5-2016. */
public class Ieee2016PSS2CStabilizerData extends Ieee1992PSS2BStabilizerData {
    private double t12 = 1.0;
    private double t13 = 1.0;
    private double pssActivation;
    private double pssDeactivation;
    private double tpgfilt;
    private double xcomp;
    private double tcomp;

    public double getT12() { return t12; }
    public void setT12(double t12) { this.t12 = t12; }
    public double getT13() { return t13; }
    public void setT13(double t13) { this.t13 = t13; }
    public double getPssActivation() { return pssActivation; }
    public void setPssActivation(double pssActivation) { this.pssActivation = pssActivation; }
    public double getPssDeactivation() { return pssDeactivation; }
    public void setPssDeactivation(double pssDeactivation) { this.pssDeactivation = pssDeactivation; }
    public double getTpgfilt() { return tpgfilt; }
    public void setTpgfilt(double tpgfilt) { this.tpgfilt = tpgfilt; }
    public double getXcomp() { return xcomp; }
    public void setXcomp(double xcomp) { this.xcomp = xcomp; }
    public double getTcomp() { return tcomp; }
    public void setTcomp(double tcomp) { this.tcomp = tcomp; }
}
