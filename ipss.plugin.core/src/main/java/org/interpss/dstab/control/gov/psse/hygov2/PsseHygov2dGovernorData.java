package org.interpss.dstab.control.gov.psse.hygov2;

import org.interpss.dstab.control.base.BaseControllerData;

/** Parameters in the nineteen-value PSS/E HYGOV2D record. */
public class PsseHygov2dGovernorData extends BaseControllerData {
    private double kp, ki, ka, t1, t2, t3, t4, t5, t6, tr;
    private double rtemp, r, vgmax, gmax, gmin, pmax, dbH, dbL, trate;

    public double getKp() { return kp; }
    public void setKp(double value) { kp = value; }
    public double getKi() { return ki; }
    public void setKi(double value) { ki = value; }
    public double getKa() { return ka; }
    public void setKa(double value) { ka = value; }
    public double getT1() { return t1; }
    public void setT1(double value) { t1 = value; }
    public double getT2() { return t2; }
    public void setT2(double value) { t2 = value; }
    public double getT3() { return t3; }
    public void setT3(double value) { t3 = value; }
    public double getT4() { return t4; }
    public void setT4(double value) { t4 = value; }
    public double getT5() { return t5; }
    public void setT5(double value) { t5 = value; }
    public double getT6() { return t6; }
    public void setT6(double value) { t6 = value; }
    public double getTr() { return tr; }
    public void setTr(double value) { tr = value; }
    public double getRtemp() { return rtemp; }
    public void setRtemp(double value) { rtemp = value; }
    public double getR() { return r; }
    public void setR(double value) { r = value; }
    public double getVgmax() { return vgmax; }
    public void setVgmax(double value) { vgmax = value; }
    public double getGmax() { return gmax; }
    public void setGmax(double value) { gmax = value; }
    public double getGmin() { return gmin; }
    public void setGmin(double value) { gmin = value; }
    public double getPmax() { return pmax; }
    public void setPmax(double value) { pmax = value; }
    public double getDbH() { return dbH; }
    public void setDbH(double value) { dbH = value; }
    public double getDbL() { return dbL; }
    public void setDbL(double value) { dbL = value; }
    public double getTrate() { return trate; }
    public void setTrate(double value) { trate = value; }

    @Override public void setValue(String name, int value) { }

    @Override public void setValue(String name, double value) {
        switch (name.toLowerCase()) {
            case "kp" -> kp = value;
            case "ki" -> ki = value;
            case "ka" -> ka = value;
            case "t1" -> t1 = value;
            case "t2" -> t2 = value;
            case "t3" -> t3 = value;
            case "t4" -> t4 = value;
            case "t5" -> t5 = value;
            case "t6" -> t6 = value;
            case "tr" -> tr = value;
            case "rtemp" -> rtemp = value;
            case "r" -> r = value;
            case "vgmax" -> vgmax = value;
            case "gmax" -> gmax = value;
            case "gmin" -> gmin = value;
            case "pmax" -> pmax = value;
            case "dbh" -> dbH = value;
            case "dbl" -> dbL = value;
            case "trate" -> trate = value;
            default -> { }
        }
    }
}
