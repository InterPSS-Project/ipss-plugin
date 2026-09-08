package org.interpss.dstab.control.exc.psse.st10c;

import org.interpss.dstab.control.base.BaseControllerData;

/** Native PSS/E IEEE 421.5-2016 ST10C excitation-system parameters. */
public final class St10cData extends BaseControllerData {
    private int pss = 1, oel = 1, uel = 1, scl = 1, sw1 = 1;
    private double tr, kr, tc1, tb1, tc2, tb2;
    private double tuc1, tub1, tuc2, tub2, toc1, tob1, toc2, tob2;
    private double vrsmax, vrsmin, vrmax, vrmin, t1;
    private double kp, kc, ki, xl, thetaP, vbmax;

    public St10cData() {
        setRangeParameters(new String[][] {
                {"tr", "0", "1000"}, {"kr", "0", "10000"},
                {"tc1", "0", "1000"}, {"tb1", "0", "1000"},
                {"tc2", "0", "1000"}, {"tb2", "0", "1000"},
                {"tuc1", "0", "1000"}, {"tub1", "0", "1000"},
                {"tuc2", "0", "1000"}, {"tub2", "0", "1000"},
                {"toc1", "0", "1000"}, {"tob1", "0", "1000"},
                {"toc2", "0", "1000"}, {"tob2", "0", "1000"},
                {"vrsmax", "-10000", "10000"}, {"vrsmin", "-10000", "10000"},
                {"vrmax", "-10000", "10000"}, {"vrmin", "-10000", "10000"},
                {"t1", "0", "1000"}, {"kp", "-10000", "10000"},
                {"kc", "0", "10000"}, {"ki", "-10000", "10000"},
                {"xl", "-10000", "10000"}, {"thetap", "-360", "360"},
                {"vbmax", "0", "10000"}
        });
    }

    @Override
    public void setValue(String name, int value) {
        switch (name.toLowerCase()) {
            case "pss", "vos" -> pss = value;
            case "oel" -> oel = value;
            case "uel" -> uel = value;
            case "scl" -> scl = value;
            case "sw1" -> sw1 = value;
            default -> setValue(name, (double) value);
        }
    }

    @Override
    public void setValue(String name, double value) {
        switch (name.toLowerCase()) {
            case "pss", "vos" -> pss = (int) value;
            case "oel" -> oel = (int) value;
            case "uel" -> uel = (int) value;
            case "scl" -> scl = (int) value;
            case "sw1" -> sw1 = (int) value;
            case "tr" -> tr = value; case "kr" -> kr = value;
            case "tc1" -> tc1 = value; case "tb1" -> tb1 = value;
            case "tc2" -> tc2 = value; case "tb2" -> tb2 = value;
            case "tuc1" -> tuc1 = value; case "tub1" -> tub1 = value;
            case "tuc2" -> tuc2 = value; case "tub2" -> tub2 = value;
            case "toc1" -> toc1 = value; case "tob1" -> tob1 = value;
            case "toc2" -> toc2 = value; case "tob2" -> tob2 = value;
            case "vrsmax" -> vrsmax = value; case "vrsmin" -> vrsmin = value;
            case "vrmax" -> vrmax = value; case "vrmin" -> vrmin = value;
            case "t1" -> t1 = value; case "kp" -> kp = value;
            case "kc" -> kc = value; case "ki" -> ki = value;
            case "xl" -> xl = value; case "thetap" -> thetaP = value;
            case "vbmax" -> vbmax = value;
            default -> { }
        }
    }

    public int getPss() { return pss; } public void setPss(int v) { pss = v; }
    public int getOel() { return oel; } public void setOel(int v) { oel = v; }
    public int getUel() { return uel; } public void setUel(int v) { uel = v; }
    public int getScl() { return scl; } public void setScl(int v) { scl = v; }
    public int getSw1() { return sw1; } public void setSw1(int v) { sw1 = v; }
    public double getTr() { return tr; } public void setTr(double v) { tr = v; }
    public double getKr() { return kr; } public void setKr(double v) { kr = v; }
    public double getTc1() { return tc1; } public void setTc1(double v) { tc1 = v; }
    public double getTb1() { return tb1; } public void setTb1(double v) { tb1 = v; }
    public double getTc2() { return tc2; } public void setTc2(double v) { tc2 = v; }
    public double getTb2() { return tb2; } public void setTb2(double v) { tb2 = v; }
    public double getTuc1() { return tuc1; } public void setTuc1(double v) { tuc1 = v; }
    public double getTub1() { return tub1; } public void setTub1(double v) { tub1 = v; }
    public double getTuc2() { return tuc2; } public void setTuc2(double v) { tuc2 = v; }
    public double getTub2() { return tub2; } public void setTub2(double v) { tub2 = v; }
    public double getToc1() { return toc1; } public void setToc1(double v) { toc1 = v; }
    public double getTob1() { return tob1; } public void setTob1(double v) { tob1 = v; }
    public double getToc2() { return toc2; } public void setToc2(double v) { toc2 = v; }
    public double getTob2() { return tob2; } public void setTob2(double v) { tob2 = v; }
    public double getVrsmax() { return vrsmax; } public void setVrsmax(double v) { vrsmax = v; }
    public double getVrsmin() { return vrsmin; } public void setVrsmin(double v) { vrsmin = v; }
    public double getVrmax() { return vrmax; } public void setVrmax(double v) { vrmax = v; }
    public double getVrmin() { return vrmin; } public void setVrmin(double v) { vrmin = v; }
    public double getT1() { return t1; } public void setT1(double v) { t1 = v; }
    public double getKp() { return kp; } public void setKp(double v) { kp = v; }
    public double getKc() { return kc; } public void setKc(double v) { kc = v; }
    public double getKi() { return ki; } public void setKi(double v) { ki = v; }
    public double getXl() { return xl; } public void setXl(double v) { xl = v; }
    public double getThetaP() { return thetaP; } public void setThetaP(double v) { thetaP = v; }
    public double getVbmax() { return vbmax; } public void setVbmax(double v) { vbmax = v; }
}
