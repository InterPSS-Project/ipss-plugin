package org.interpss.dstab.control.exc.psse.bbsex1;

import org.interpss.dstab.control.base.BaseControllerData;

/** Native PSS/E BBSEX1 transformer-fed static-exciter parameters. */
public final class Bbsex1Data extends BaseControllerData {
    private double tf, k, t1, t2, t3, t4;
    private double vrmax, vrmin, efdmax, efdmin;
    private int switchLocation;

    public Bbsex1Data() {
        setRangeParameters(new String[][] {
                {"tf", "0", "1000"}, {"k", "0", "10000"},
                {"t1", "0", "1000"}, {"t2", "0", "1000"},
                {"t3", "0", "1000"}, {"t4", "0", "1000"},
                {"vrmax", "-10000", "10000"}, {"vrmin", "-10000", "10000"},
                {"efdmax", "-10000", "10000"}, {"efdmin", "-10000", "10000"}
        });
    }

    @Override
    public void setValue(String name, int value) {
        if ("switch".equalsIgnoreCase(name) || "switchlocation".equalsIgnoreCase(name)) {
            switchLocation = value;
        } else {
            setValue(name, (double) value);
        }
    }

    @Override
    public void setValue(String name, double value) {
        switch (name.toLowerCase()) {
            case "tf" -> tf = value;
            case "k" -> k = value;
            case "t1" -> t1 = value;
            case "t2" -> t2 = value;
            case "t3" -> t3 = value;
            case "t4" -> t4 = value;
            case "vrmax" -> vrmax = value;
            case "vrmin" -> vrmin = value;
            case "efdmax" -> efdmax = value;
            case "efdmin" -> efdmin = value;
            case "switch", "switchlocation" -> switchLocation = (int) value;
            default -> { }
        }
    }

    public double getTf() { return tf; }
    public void setTf(double value) { tf = value; }
    public double getK() { return k; }
    public void setK(double value) { k = value; }
    public double getT1() { return t1; }
    public void setT1(double value) { t1 = value; }
    public double getT2() { return t2; }
    public void setT2(double value) { t2 = value; }
    public double getT3() { return t3; }
    public void setT3(double value) { t3 = value; }
    public double getT4() { return t4; }
    public void setT4(double value) { t4 = value; }
    public double getVrmax() { return vrmax; }
    public void setVrmax(double value) { vrmax = value; }
    public double getVrmin() { return vrmin; }
    public void setVrmin(double value) { vrmin = value; }
    public double getEfdmax() { return efdmax; }
    public void setEfdmax(double value) { efdmax = value; }
    public double getEfdmin() { return efdmin; }
    public void setEfdmin(double value) { efdmin = value; }
    public int getSwitchLocation() { return switchLocation; }
    public void setSwitchLocation(int value) { switchLocation = value; }
}
