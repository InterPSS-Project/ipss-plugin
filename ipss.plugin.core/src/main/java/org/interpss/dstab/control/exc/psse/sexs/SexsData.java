package org.interpss.dstab.control.exc.psse.sexs;

/** Native SEXS simplified excitation-system parameters. */
public class SexsData {
    private double taOverTb;
    private double tb;
    private double k;
    private double te;
    private double emin;
    private double emax;

    public double getTaOverTb() { return taOverTb; }
    public void setTaOverTb(double value) { taOverTb = value; }
    public double getTb() { return tb; }
    public void setTb(double value) { tb = value; }
    public double getK() { return k; }
    public void setK(double value) { k = value; }
    public double getTe() { return te; }
    public void setTe(double value) { te = value; }
    public double getEmin() { return emin; }
    public void setEmin(double value) { emin = value; }
    public double getEmax() { return emax; }
    public void setEmax(double value) { emax = value; }
}
