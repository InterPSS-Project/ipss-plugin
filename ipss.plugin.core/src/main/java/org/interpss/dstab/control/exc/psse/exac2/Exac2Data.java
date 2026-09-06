package org.interpss.dstab.control.exc.psse.exac2;

import org.interpss.dstab.control.exc.psse.exac1.Exac1Data;

/** PSS/E/PowerWorld EXAC2 parameters in model units. */
public class Exac2Data extends Exac1Data {
    private double vamax, vamin, kb, kl, kh, vlr;

    public double getVamax() { return vamax; }
    public void setVamax(double value) { vamax=value; }
    public double getVamin() { return vamin; }
    public void setVamin(double value) { vamin=value; }
    public double getKb() { return kb; }
    public void setKb(double value) { kb=value; }
    public double getKl() { return kl; }
    public void setKl(double value) { kl=value; }
    public double getKh() { return kh; }
    public void setKh(double value) { kh=value; }
    public double getVlr() { return vlr; }
    public void setVlr(double value) { vlr=value; }
}
