package org.interpss.dstab.control.exc.psse.ac2c;

import org.interpss.dstab.control.base.BaseControllerData;

/** IEEE 421.5-2016 / PSS/E AC2C excitation-system parameters. */
public final class Ac2cData extends BaseControllerData {
    // The PSS/E record carries OEL and UEL. SCL is a typed IEEE/PowerWorld input.
    private int oelLocation, uelLocation, sclLocation;
    private double tr, tb, tc, ka, ta, vamax, vamin, kb, efemax, efemin;
    private double te, vfemax, kh, kf, tf, kc, kd, ke, e1, se1, e2, se2, vemin;

    public Ac2cData() {
        setRangeParameters(new String[][] {
                {"tr", "0.0", "1000.0"}, {"tb", "0.0", "1000.0"},
                {"tc", "-1000.0", "1000.0"}, {"ka", "-10000.0", "10000.0"},
                {"ta", "0.0", "1000.0"}, {"vamax", "-10000.0", "10000.0"},
                {"vamin", "-10000.0", "10000.0"}, {"kb", "-10000.0", "10000.0"},
                {"efemax", "-10000.0", "10000.0"}, {"efemin", "-10000.0", "10000.0"},
                {"te", "0.0", "1000.0"}, {"vfemax", "-10000.0", "10000.0"},
                {"kh", "-10000.0", "10000.0"}, {"kf", "-10000.0", "10000.0"},
                {"tf", "0.0", "1000.0"}, {"kc", "0.0", "10000.0"},
                {"kd", "0.0", "10000.0"}, {"ke", "-10000.0", "10000.0"},
                {"e1", "0.0", "10000.0"}, {"se1", "0.0", "10000.0"},
                {"e2", "0.0", "10000.0"}, {"se2", "0.0", "10000.0"},
                {"vemin", "-10000.0", "10000.0"}
        });
    }

    @Override public void setValue(String name, int value) {
        switch (name.toLowerCase()) {
            case "oellocation" -> oelLocation=value;
            case "uellocation" -> uelLocation=value;
            case "scllocation" -> sclLocation=value;
            default -> setValue(name,(double)value);
        }
    }

    @Override public void setValue(String name, double value) {
        switch (name.toLowerCase()) {
            case "tr" -> tr=value; case "tb" -> tb=value; case "tc" -> tc=value;
            case "ka" -> ka=value; case "ta" -> ta=value;
            case "vamax" -> vamax=value; case "vamin" -> vamin=value;
            case "kb" -> kb=value; case "efemax" -> efemax=value; case "efemin" -> efemin=value;
            case "te" -> te=value; case "vfemax" -> vfemax=value; case "kh" -> kh=value;
            case "kf" -> kf=value; case "tf" -> tf=value; case "kc" -> kc=value;
            case "kd" -> kd=value; case "ke" -> ke=value;
            case "e1" -> e1=value; case "se1" -> se1=value;
            case "e2" -> e2=value; case "se2" -> se2=value; case "vemin" -> vemin=value;
            default -> { }
        }
    }

    public int getOelLocation(){return oelLocation;} public void setOelLocation(int v){oelLocation=v;}
    public int getUelLocation(){return uelLocation;} public void setUelLocation(int v){uelLocation=v;}
    public int getSclLocation(){return sclLocation;} public void setSclLocation(int v){sclLocation=v;}
    public double getTr(){return tr;} public void setTr(double v){tr=v;}
    public double getTb(){return tb;} public void setTb(double v){tb=v;}
    public double getTc(){return tc;} public void setTc(double v){tc=v;}
    public double getKa(){return ka;} public void setKa(double v){ka=v;}
    public double getTa(){return ta;} public void setTa(double v){ta=v;}
    public double getVamax(){return vamax;} public void setVamax(double v){vamax=v;}
    public double getVamin(){return vamin;} public void setVamin(double v){vamin=v;}
    public double getKb(){return kb;} public void setKb(double v){kb=v;}
    public double getEfemax(){return efemax;} public void setEfemax(double v){efemax=v;}
    public double getEfemin(){return efemin;} public void setEfemin(double v){efemin=v;}
    public double getTe(){return te;} public void setTe(double v){te=v;}
    public double getVfemax(){return vfemax;} public void setVfemax(double v){vfemax=v;}
    public double getKh(){return kh;} public void setKh(double v){kh=v;}
    public double getKf(){return kf;} public void setKf(double v){kf=v;}
    public double getTf(){return tf;} public void setTf(double v){tf=v;}
    public double getKc(){return kc;} public void setKc(double v){kc=v;}
    public double getKd(){return kd;} public void setKd(double v){kd=v;}
    public double getKe(){return ke;} public void setKe(double v){ke=v;}
    public double getE1(){return e1;} public void setE1(double v){e1=v;}
    public double getSe1(){return se1;} public void setSe1(double v){se1=v;}
    public double getE2(){return e2;} public void setE2(double v){e2=v;}
    public double getSe2(){return se2;} public void setSe2(double v){se2=v;}
    public double getVemin(){return vemin;} public void setVemin(double v){vemin=v;}
}
