package org.interpss.dstab.control.pss.ieee.y1992.pss2b;

import org.interpss.dstab.control.pss.ieee.y1992.pss2a.Ieee1992PSS2AStabilizerData;

/** Complete PSS/E/PowerWorld PSS2B parameter set. */
public class Ieee1992PSS2BStabilizerData extends Ieee1992PSS2AStabilizerData {
    private double t10;
    private double t11;
    private double vsi1max = 999.0;
    private double vsi1min = -999.0;
    private double vsi2max = 999.0;
    private double vsi2min = -999.0;

    public double getT10() { return t10; }
    public void setT10(double t10) { this.t10 = t10; }
    public double getT11() { return t11; }
    public void setT11(double t11) { this.t11 = t11; }
    public double getVsi1max() { return vsi1max; }
    public void setVsi1max(double vsi1max) { this.vsi1max = vsi1max; }
    public double getVsi1min() { return vsi1min; }
    public void setVsi1min(double vsi1min) { this.vsi1min = vsi1min; }
    public double getVsi2max() { return vsi2max; }
    public void setVsi2max(double vsi2max) { this.vsi2max = vsi2max; }
    public double getVsi2min() { return vsi2min; }
    public void setVsi2min(double vsi2min) { this.vsi2min = vsi2min; }
}
