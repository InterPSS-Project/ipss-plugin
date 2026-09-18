package org.interpss.dstab.control.gov.psse.degov1;

import org.interpss.dstab.control.base.BaseControllerData;

/** PSS/E DEGOV1D Woodward diesel-governor parameters. */
public class PsseDegov1dGovernorData extends BaseControllerData {
    private int droopControl;
    private double t1 = .1, t2 = .1, t3, k = 20.0;
    private double t4, t5, t6 = .1, td;
    private double tmax = 1.0, tmin, droop = .05, te;
    private double dbH, dbL, trate;

    public int getDroopControl() { return droopControl; }
    public void setDroopControl(int value) { droopControl = value; }
    public double getT1() { return t1; }
    public void setT1(double value) { t1 = value; }
    public double getT2() { return t2; }
    public void setT2(double value) { t2 = value; }
    public double getT3() { return t3; }
    public void setT3(double value) { t3 = value; }
    public double getK() { return k; }
    public void setK(double value) { k = value; }
    public double getT4() { return t4; }
    public void setT4(double value) { t4 = value; }
    public double getT5() { return t5; }
    public void setT5(double value) { t5 = value; }
    public double getT6() { return t6; }
    public void setT6(double value) { t6 = value; }
    public double getTd() { return td; }
    public void setTd(double value) { td = value; }
    public double getTmax() { return tmax; }
    public void setTmax(double value) { tmax = value; }
    public double getTmin() { return tmin; }
    public void setTmin(double value) { tmin = value; }
    public double getDroop() { return droop; }
    public void setDroop(double value) { droop = value; }
    public double getTe() { return te; }
    public void setTe(double value) { te = value; }
    public double getDbH() { return dbH; }
    public void setDbH(double value) { dbH = value; }
    public double getDbL() { return dbL; }
    public void setDbL(double value) { dbL = value; }
    public double getTrate() { return trate; }
    public void setTrate(double value) { trate = value; }

    @Override public void setValue(String name, int value) {
        if ("droopControl".equalsIgnoreCase(name)) setDroopControl(value);
    }

    @Override public void setValue(String name, double value) {
        switch (name.toLowerCase(java.util.Locale.ROOT)) {
            case "droopcontrol" -> setDroopControl((int) value);
            case "t1" -> setT1(value);
            case "t2" -> setT2(value);
            case "t3" -> setT3(value);
            case "k" -> setK(value);
            case "t4" -> setT4(value);
            case "t5" -> setT5(value);
            case "t6" -> setT6(value);
            case "td" -> setTd(value);
            case "tmax" -> setTmax(value);
            case "tmin" -> setTmin(value);
            case "droop" -> setDroop(value);
            case "te" -> setTe(value);
            case "dbh" -> setDbH(value);
            case "dbl" -> setDbL(value);
            case "trate" -> setTrate(value);
            default -> { }
        }
    }
}
