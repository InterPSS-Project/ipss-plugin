package org.interpss.dstab.control.exc.psse.esst2a;

import org.interpss.dstab.control.base.BaseControllerData;

/** IEEE 421.5-2005 / PSS/E ESST2A excitation-system parameters. */
public final class Esst2aData extends BaseControllerData {
    private double tr, ka, ta, vrmax, vrmin, ke, te, kf, tf, kp, ki, kc, efdmax;
    // PowerWorld exposes these extensions. PSS/E ESST2A fixes them at zero.
    private int uel;
    private double tb, tc;

    public Esst2aData() {
        setRangeParameters(new String[][] {
                {"tr", "0", "1000"}, {"ka", "-10000", "10000"},
                {"ta", "0", "1000"}, {"vrmax", "-10000", "10000"},
                {"vrmin", "-10000", "10000"}, {"ke", "-10000", "10000"},
                {"te", "0", "1000"}, {"kf", "-10000", "10000"},
                {"tf", "0", "1000"}, {"kp", "-10000", "10000"},
                {"ki", "-10000", "10000"}, {"kc", "-10000", "10000"},
                {"efdmax", "0", "10000"}, {"tb", "0", "1000"},
                {"tc", "0", "1000"}
        });
    }

    @Override public void setValue(String name, int value) {
        if ("uel".equalsIgnoreCase(name)) uel = value;
        else setValue(name, (double) value);
    }
    @Override public void setValue(String name, double value) {
        switch (name.toLowerCase()) {
            case "tr" -> tr = value; case "ka" -> ka = value;
            case "ta" -> ta = value; case "vrmax" -> vrmax = value;
            case "vrmin" -> vrmin = value; case "ke" -> ke = value;
            case "te" -> te = value; case "kf" -> kf = value;
            case "tf" -> tf = value; case "kp" -> kp = value;
            case "ki" -> ki = value; case "kc" -> kc = value;
            case "efdmax" -> efdmax = value; case "uel" -> uel = (int) value;
            case "tb" -> tb = value; case "tc" -> tc = value;
            default -> { }
        }
    }

    public double getTr(){return tr;} public void setTr(double v){tr=v;}
    public double getKa(){return ka;} public void setKa(double v){ka=v;}
    public double getTa(){return ta;} public void setTa(double v){ta=v;}
    public double getVrmax(){return vrmax;} public void setVrmax(double v){vrmax=v;}
    public double getVrmin(){return vrmin;} public void setVrmin(double v){vrmin=v;}
    public double getKe(){return ke;} public void setKe(double v){ke=v;}
    public double getTe(){return te;} public void setTe(double v){te=v;}
    public double getKf(){return kf;} public void setKf(double v){kf=v;}
    public double getTf(){return tf;} public void setTf(double v){tf=v;}
    public double getKp(){return kp;} public void setKp(double v){kp=v;}
    public double getKi(){return ki;} public void setKi(double v){ki=v;}
    public double getKc(){return kc;} public void setKc(double v){kc=v;}
    public double getEfdmax(){return efdmax;} public void setEfdmax(double v){efdmax=v;}
    public int getUel(){return uel;} public void setUel(int v){uel=v;}
    public double getTb(){return tb;} public void setTb(double v){tb=v;}
    public double getTc(){return tc;} public void setTc(double v){tc=v;}
}
