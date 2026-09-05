package org.interpss.dstab.control.gov.psse.hygov;

import org.interpss.dstab.control.base.BaseControllerData;

/** Parameters in the twelve-value PSS/E HYGOV record. */
public class PsseHygovGovernorData extends BaseControllerData {
    private double r = 0.05;
    private double rtemp = 0.5;
    private double tr = 5.0;
    private double tf = 0.05;
    private double tg = 0.2;
    private double velm = 0.2;
    private double gmax = 1.0;
    private double gmin;
    private double tw = 1.0;
    private double at = 1.0;
    private double dturb;
    private double qnl;

    public double getR() { return r; }
    public void setR(double value) { r = value; }
    public double getRtemp() { return rtemp; }
    public void setRtemp(double value) { rtemp = value; }
    public double getTr() { return tr; }
    public void setTr(double value) { tr = value; }
    public double getTf() { return tf; }
    public void setTf(double value) { tf = value; }
    public double getTg() { return tg; }
    public void setTg(double value) { tg = value; }
    public double getVelm() { return velm; }
    public void setVelm(double value) { velm = value; }
    public double getGmax() { return gmax; }
    public void setGmax(double value) { gmax = value; }
    public double getGmin() { return gmin; }
    public void setGmin(double value) { gmin = value; }
    public double getTw() { return tw; }
    public void setTw(double value) { tw = value; }
    public double getAt() { return at; }
    public void setAt(double value) { at = value; }
    public double getDturb() { return dturb; }
    public void setDturb(double value) { dturb = value; }
    public double getQnl() { return qnl; }
    public void setQnl(double value) { qnl = value; }

    @Override public void setValue(String name, int value) { }
    @Override public void setValue(String name, double value) { }
}
