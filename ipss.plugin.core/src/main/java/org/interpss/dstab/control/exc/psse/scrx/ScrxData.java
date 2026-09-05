package org.interpss.dstab.control.exc.psse.scrx;

/** PSS/E SCRX excitation-system parameters in model units. */
public class ScrxData {
    private double taOverTb;
    private double tb;
    private double k = 1.0;
    private double te;
    private double efdmin;
    private double efdmax;
    private int cswitch;
    private double rcOverRfd;

    public double getTaOverTb() { return taOverTb; }
    public void setTaOverTb(double value) { taOverTb = value; }
    public double getTb() { return tb; }
    public void setTb(double value) { tb = value; }
    public double getK() { return k; }
    public void setK(double value) { k = value; }
    public double getTe() { return te; }
    public void setTe(double value) { te = value; }
    public double getEfdmin() { return efdmin; }
    public void setEfdmin(double value) { efdmin = value; }
    public double getEfdmax() { return efdmax; }
    public void setEfdmax(double value) { efdmax = value; }
    public int getCswitch() { return cswitch; }
    public void setCswitch(int value) { cswitch = value; }
    public double getRcOverRfd() { return rcOverRfd; }
    public void setRcOverRfd(double value) { rcOverRfd = value; }
}
