package org.interpss.dstab.control.exc.psse.st9c;

import org.interpss.dstab.control.base.BaseControllerData;

/** Native PSS/E IEEE 421.5-2016 ST9C excitation-system parameters. */
public final class St9cData extends BaseControllerData {
    private int oel, uel, scl, sw1 = 1;
    private double tr, tcd, tbd, za, ka, ku, ta, tauel;
    private double vrmax, vrmin, kas, tas, kp, thetaP, ki, xl, kc, vbmax;

    public St9cData() {
        setRangeParameters(new String[][] {
                {"tr", "0", "1000"}, {"tcd", "0", "1000"},
                {"tbd", "0", "1000"}, {"za", "0", "10000"},
                {"ka", "0", "10000"}, {"ku", "0", "10000"},
                {"ta", "0", "1000"}, {"tauel", "0", "1000"},
                {"vrmax", "-10000", "10000"}, {"vrmin", "-10000", "10000"},
                {"kas", "0", "10000"}, {"tas", "0", "1000"},
                {"kp", "-10000", "10000"}, {"thetap", "-360", "360"},
                {"ki", "-10000", "10000"}, {"xl", "-10000", "10000"},
                {"kc", "0", "10000"}, {"vbmax", "0", "10000"}
        });
    }

    @Override
    public void setValue(String name, int value) {
        switch (name.toLowerCase()) {
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
            case "oel" -> oel = (int) value;
            case "uel" -> uel = (int) value;
            case "scl" -> scl = (int) value;
            case "sw1" -> sw1 = (int) value;
            case "tr" -> tr = value;
            case "tcd" -> tcd = value;
            case "tbd" -> tbd = value;
            case "za" -> za = value;
            case "ka" -> ka = value;
            case "ku" -> ku = value;
            case "ta" -> ta = value;
            case "tauel" -> tauel = value;
            case "vrmax" -> vrmax = value;
            case "vrmin" -> vrmin = value;
            case "kas" -> kas = value;
            case "tas" -> tas = value;
            case "kp" -> kp = value;
            case "thetap" -> thetaP = value;
            case "ki" -> ki = value;
            case "xl" -> xl = value;
            case "kc" -> kc = value;
            case "vbmax" -> vbmax = value;
            default -> { }
        }
    }

    public int getOel() { return oel; }
    public void setOel(int value) { oel = value; }
    public int getUel() { return uel; }
    public void setUel(int value) { uel = value; }
    public int getScl() { return scl; }
    public void setScl(int value) { scl = value; }
    public int getSw1() { return sw1; }
    public void setSw1(int value) { sw1 = value; }
    public double getTr() { return tr; }
    public void setTr(double value) { tr = value; }
    public double getTcd() { return tcd; }
    public void setTcd(double value) { tcd = value; }
    public double getTbd() { return tbd; }
    public void setTbd(double value) { tbd = value; }
    public double getZa() { return za; }
    public void setZa(double value) { za = value; }
    public double getKa() { return ka; }
    public void setKa(double value) { ka = value; }
    public double getKu() { return ku; }
    public void setKu(double value) { ku = value; }
    public double getTa() { return ta; }
    public void setTa(double value) { ta = value; }
    public double getTauel() { return tauel; }
    public void setTauel(double value) { tauel = value; }
    public double getVrmax() { return vrmax; }
    public void setVrmax(double value) { vrmax = value; }
    public double getVrmin() { return vrmin; }
    public void setVrmin(double value) { vrmin = value; }
    public double getKas() { return kas; }
    public void setKas(double value) { kas = value; }
    public double getTas() { return tas; }
    public void setTas(double value) { tas = value; }
    public double getKp() { return kp; }
    public void setKp(double value) { kp = value; }
    public double getThetaP() { return thetaP; }
    public void setThetaP(double value) { thetaP = value; }
    public double getKi() { return ki; }
    public void setKi(double value) { ki = value; }
    public double getXl() { return xl; }
    public void setXl(double value) { xl = value; }
    public double getKc() { return kc; }
    public void setKc(double value) { kc = value; }
    public double getVbmax() { return vbmax; }
    public void setVbmax(double value) { vbmax = value; }
}
