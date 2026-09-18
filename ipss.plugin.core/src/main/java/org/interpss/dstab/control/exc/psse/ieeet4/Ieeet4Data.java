package org.interpss.dstab.control.exc.psse.ieeet4;

import org.interpss.dstab.control.base.BaseControllerData;

/** PSS/E IEEET4 / WECC EXDC4 record data. */
public final class Ieeet4Data extends BaseControllerData {
    private double kr;
    private double trh;
    private double kv;
    private double vrmax;
    private double vrmin;
    private double te;
    private double ke;
    private double e1;
    private double se1;
    private double e2;
    private double se2;

    public Ieeet4Data() {
        setRangeParameters(new String[][] {
                {"kr", "-1000.0", "1000.0"}, {"trh", "0.0", "1000.0"},
                {"kv", "0.0", "1000.0"}, {"vrmax", "-1000.0", "1000.0"},
                {"vrmin", "-1000.0", "1000.0"}, {"te", "0.0", "1000.0"},
                {"ke", "-1000.0", "1000.0"}, {"e1", "0.0", "1000.0"},
                {"se1", "0.0", "1000.0"}, {"e2", "0.0", "1000.0"},
                {"se2", "0.0", "1000.0"}
        });
    }

    @Override public void setValue(String name, int value) { setValue(name, (double) value); }
    @Override public void setValue(String name, double value) {
        switch (name) {
            case "kr" -> kr = value;
            case "trh" -> trh = value;
            case "kv" -> kv = value;
            case "vrmax" -> vrmax = value;
            case "vrmin" -> vrmin = value;
            case "te" -> te = value;
            case "ke" -> ke = value;
            case "e1" -> e1 = value;
            case "se1" -> se1 = value;
            case "e2" -> e2 = value;
            case "se2" -> se2 = value;
            default -> { }
        }
    }

    public double getKr() { return kr; }
    public void setKr(double value) { kr = value; }
    public double getTrh() { return trh; }
    public void setTrh(double value) { trh = value; }
    public double getKv() { return kv; }
    public void setKv(double value) { kv = value; }
    public double getVrmax() { return vrmax; }
    public void setVrmax(double value) { vrmax = value; }
    public double getVrmin() { return vrmin; }
    public void setVrmin(double value) { vrmin = value; }
    public double getTe() { return te; }
    public void setTe(double value) { te = value; }
    public double getKe() { return ke; }
    public void setKe(double value) { ke = value; }
    public double getE1() { return e1; }
    public void setE1(double value) { e1 = value; }
    public double getSe1() { return se1; }
    public void setSe1(double value) { se1 = value; }
    public double getE2() { return e2; }
    public void setE2(double value) { e2 = value; }
    public double getSe2() { return se2; }
    public void setSe2(double value) { se2 = value; }
}
