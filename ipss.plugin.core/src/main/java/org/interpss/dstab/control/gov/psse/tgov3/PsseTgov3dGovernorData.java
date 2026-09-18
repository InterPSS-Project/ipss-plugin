package org.interpss.dstab.control.gov.psse.tgov3;

import org.interpss.dstab.control.base.BaseControllerData;

/** Parameter data for the PSS/E TGOV3D steam turbine-governor. */
public class PsseTgov3dGovernorData extends BaseControllerData {
    private double k, t1, t2, t3, uo, uc, pmax, pmin;
    private double t4, k1, t5, k2, t6, k3;
    private double ta, tb, tc, prmax, dbH, dbL, trate;

    public double getK() { return k; }
    public void setK(double value) { k = value; }
    public double getT1() { return t1; }
    public void setT1(double value) { t1 = value; }
    public double getT2() { return t2; }
    public void setT2(double value) { t2 = value; }
    public double getT3() { return t3; }
    public void setT3(double value) { t3 = value; }
    public double getUo() { return uo; }
    public void setUo(double value) { uo = value; }
    public double getUc() { return uc; }
    public void setUc(double value) { uc = value; }
    public double getPmax() { return pmax; }
    public void setPmax(double value) { pmax = value; }
    public double getPmin() { return pmin; }
    public void setPmin(double value) { pmin = value; }
    public double getT4() { return t4; }
    public void setT4(double value) { t4 = value; }
    public double getK1() { return k1; }
    public void setK1(double value) { k1 = value; }
    public double getT5() { return t5; }
    public void setT5(double value) { t5 = value; }
    public double getK2() { return k2; }
    public void setK2(double value) { k2 = value; }
    public double getT6() { return t6; }
    public void setT6(double value) { t6 = value; }
    public double getK3() { return k3; }
    public void setK3(double value) { k3 = value; }
    public double getTa() { return ta; }
    public void setTa(double value) { ta = value; }
    public double getTb() { return tb; }
    public void setTb(double value) { tb = value; }
    public double getTc() { return tc; }
    public void setTc(double value) { tc = value; }
    public double getPrmax() { return prmax; }
    public void setPrmax(double value) { prmax = value; }
    public double getDbH() { return dbH; }
    public void setDbH(double value) { dbH = value; }
    public double getDbL() { return dbL; }
    public void setDbL(double value) { dbL = value; }
    public double getTrate() { return trate; }
    public void setTrate(double value) { trate = value; }

    @Override public void setValue(String name, int value) { }

    @Override public void setValue(String name, double value) {
        switch (name.toLowerCase()) {
            case "k" -> k = value;
            case "t1" -> t1 = value;
            case "t2" -> t2 = value;
            case "t3" -> t3 = value;
            case "uo" -> uo = value;
            case "uc" -> uc = value;
            case "pmax" -> pmax = value;
            case "pmin" -> pmin = value;
            case "t4" -> t4 = value;
            case "k1" -> k1 = value;
            case "t5" -> t5 = value;
            case "k2" -> k2 = value;
            case "t6" -> t6 = value;
            case "k3" -> k3 = value;
            case "ta" -> ta = value;
            case "tb" -> tb = value;
            case "tc" -> tc = value;
            case "prmax" -> prmax = value;
            case "dbh" -> dbH = value;
            case "dbl" -> dbL = value;
            case "trate" -> trate = value;
            default -> { }
        }
    }
}
