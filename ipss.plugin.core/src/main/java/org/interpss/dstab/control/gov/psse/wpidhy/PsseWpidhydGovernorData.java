package org.interpss.dstab.control.gov.psse.wpidhy;

import org.interpss.dstab.control.base.BaseControllerData;

/** Parameters in the twenty-four-value PSS/E WPIDHYD record. */
public class PsseWpidhydGovernorData extends BaseControllerData {
    private double treg, reg, kp, ki, kd, ta, tb, velmax, velmin;
    private double gmax, gmin, tw, pmax, pmin, d, g0, g1, p1, g2, p2, p3;
    private double dbH, dbL, trate;

    public double getTreg() { return treg; }
    public void setTreg(double value) { treg = value; }
    public double getReg() { return reg; }
    public void setReg(double value) { reg = value; }
    public double getKp() { return kp; }
    public void setKp(double value) { kp = value; }
    public double getKi() { return ki; }
    public void setKi(double value) { ki = value; }
    public double getKd() { return kd; }
    public void setKd(double value) { kd = value; }
    public double getTa() { return ta; }
    public void setTa(double value) { ta = value; }
    public double getTb() { return tb; }
    public void setTb(double value) { tb = value; }
    public double getVelmax() { return velmax; }
    public void setVelmax(double value) { velmax = value; }
    public double getVelmin() { return velmin; }
    public void setVelmin(double value) { velmin = value; }
    public double getGmax() { return gmax; }
    public void setGmax(double value) { gmax = value; }
    public double getGmin() { return gmin; }
    public void setGmin(double value) { gmin = value; }
    public double getTw() { return tw; }
    public void setTw(double value) { tw = value; }
    public double getPmax() { return pmax; }
    public void setPmax(double value) { pmax = value; }
    public double getPmin() { return pmin; }
    public void setPmin(double value) { pmin = value; }
    public double getD() { return d; }
    public void setD(double value) { d = value; }
    public double getG0() { return g0; }
    public void setG0(double value) { g0 = value; }
    public double getG1() { return g1; }
    public void setG1(double value) { g1 = value; }
    public double getP1() { return p1; }
    public void setP1(double value) { p1 = value; }
    public double getG2() { return g2; }
    public void setG2(double value) { g2 = value; }
    public double getP2() { return p2; }
    public void setP2(double value) { p2 = value; }
    public double getP3() { return p3; }
    public void setP3(double value) { p3 = value; }
    public double getDbH() { return dbH; }
    public void setDbH(double value) { dbH = value; }
    public double getDbL() { return dbL; }
    public void setDbL(double value) { dbL = value; }
    public double getTrate() { return trate; }
    public void setTrate(double value) { trate = value; }

    @Override public void setValue(String name, int value) { }

    @Override public void setValue(String name, double value) {
        switch (name.toLowerCase()) {
            case "treg" -> treg = value;
            case "reg" -> reg = value;
            case "kp" -> kp = value;
            case "ki" -> ki = value;
            case "kd" -> kd = value;
            case "ta" -> ta = value;
            case "tb" -> tb = value;
            case "velmax" -> velmax = value;
            case "velmin" -> velmin = value;
            case "gmax" -> gmax = value;
            case "gmin" -> gmin = value;
            case "tw" -> tw = value;
            case "pmax" -> pmax = value;
            case "pmin" -> pmin = value;
            case "d" -> d = value;
            case "g0" -> g0 = value;
            case "g1" -> g1 = value;
            case "p1" -> p1 = value;
            case "g2" -> g2 = value;
            case "p2" -> p2 = value;
            case "p3" -> p3 = value;
            case "dbh" -> dbH = value;
            case "dbl" -> dbL = value;
            case "trate" -> trate = value;
            default -> { }
        }
    }
}
