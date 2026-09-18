package org.interpss.dstab.control.exc.psse.st1c;

import org.interpss.dstab.control.base.BaseControllerData;

/** Exact PSS/E IEEE 421.5-2016 ST1C excitation-system parameters. */
public final class St1cData extends BaseControllerData {
    private int uel = 1, vos = 1, oel = 1;
    private double tr, vimax, vimin, tc, tb, tc1, tb1, ka, ta;
    private double vamax, vamin, vrmax, vrmin, kc, kf, tf, klr, ilr;

    public St1cData() {
        setRangeParameters(new String[][] {
                {"tr", "0", "1000"}, {"vimax", "-10000", "10000"},
                {"vimin", "-10000", "10000"}, {"tc", "0", "1000"},
                {"tb", "0", "1000"}, {"tc1", "0", "1000"},
                {"tb1", "0", "1000"}, {"ka", "-10000", "10000"},
                {"ta", "0", "1000"}, {"vamax", "-10000", "10000"},
                {"vamin", "-10000", "10000"}, {"vrmax", "-10000", "10000"},
                {"vrmin", "-10000", "10000"}, {"kc", "-10000", "10000"},
                {"kf", "-10000", "10000"}, {"tf", "0", "1000"},
                {"klr", "-10000", "10000"}, {"ilr", "-10000", "10000"}
        });
    }

    @Override public void setValue(String name, int value) {
        switch (name.toLowerCase()) {
            case "uel" -> uel = value;
            case "vos" -> vos = value;
            case "oel" -> oel = value;
            default -> setValue(name, (double) value);
        }
    }

    @Override public void setValue(String name, double value) {
        switch (name.toLowerCase()) {
            case "tr" -> tr = value; case "vimax" -> vimax = value;
            case "vimin" -> vimin = value; case "tc" -> tc = value;
            case "tb" -> tb = value; case "tc1" -> tc1 = value;
            case "tb1" -> tb1 = value; case "ka" -> ka = value;
            case "ta" -> ta = value; case "vamax" -> vamax = value;
            case "vamin" -> vamin = value; case "vrmax" -> vrmax = value;
            case "vrmin" -> vrmin = value; case "kc" -> kc = value;
            case "kf" -> kf = value; case "tf" -> tf = value;
            case "klr" -> klr = value; case "ilr" -> ilr = value;
            default -> { }
        }
    }

    public int getUel(){return uel;} public void setUel(int v){uel=v;}
    public int getVos(){return vos;} public void setVos(int v){vos=v;}
    public int getOel(){return oel;} public void setOel(int v){oel=v;}
    public double getTr(){return tr;} public void setTr(double v){tr=v;}
    public double getVimax(){return vimax;} public void setVimax(double v){vimax=v;}
    public double getVimin(){return vimin;} public void setVimin(double v){vimin=v;}
    public double getTc(){return tc;} public void setTc(double v){tc=v;}
    public double getTb(){return tb;} public void setTb(double v){tb=v;}
    public double getTc1(){return tc1;} public void setTc1(double v){tc1=v;}
    public double getTb1(){return tb1;} public void setTb1(double v){tb1=v;}
    public double getKa(){return ka;} public void setKa(double v){ka=v;}
    public double getTa(){return ta;} public void setTa(double v){ta=v;}
    public double getVamax(){return vamax;} public void setVamax(double v){vamax=v;}
    public double getVamin(){return vamin;} public void setVamin(double v){vamin=v;}
    public double getVrmax(){return vrmax;} public void setVrmax(double v){vrmax=v;}
    public double getVrmin(){return vrmin;} public void setVrmin(double v){vrmin=v;}
    public double getKc(){return kc;} public void setKc(double v){kc=v;}
    public double getKf(){return kf;} public void setKf(double v){kf=v;}
    public double getTf(){return tf;} public void setTf(double v){tf=v;}
    public double getKlr(){return klr;} public void setKlr(double v){klr=v;}
    public double getIlr(){return ilr;} public void setIlr(double v){ilr=v;}
}
