package org.interpss.dstab.control.exc.psse.ac8b;

import org.interpss.dstab.control.base.BaseControllerData;

/**
 * IEEE 421.5-2005 / PSS/E AC8B parameters.
 *
 * <p>The order represented here is the 21-value PSS/E record used by ANDES
 * and by the supplied cases. It is intentionally distinct from PowerWorld's
 * newer, extended AC8B data form.</p>
 */
public final class Ac8bData extends BaseControllerData {
    private double tr, kpr, kir, kdr, tdr;
    private double vpidmax, vpidmin, vrmax, vrmin;
    private double vfemax, vemin, ta, ka, te, kc, kd, ke;
    private double e1, se1, e2, se2;

    public Ac8bData() {
        setRangeParameters(new String[][] {
                {"tr", "0.0", "1000.0"}, {"kpr", "-10000.0", "10000.0"},
                {"kir", "-10000.0", "10000.0"}, {"kdr", "-10000.0", "10000.0"},
                {"tdr", "0.0", "1000.0"}, {"vpidmax", "-10000.0", "10000.0"},
                {"vpidmin", "-10000.0", "10000.0"}, {"vrmax", "-10000.0", "10000.0"},
                {"vrmin", "-10000.0", "10000.0"}, {"vfemax", "-10000.0", "10000.0"},
                {"vemin", "-10000.0", "10000.0"}, {"ta", "0.0", "1000.0"},
                {"ka", "-10000.0", "10000.0"}, {"te", "0.0", "1000.0"},
                {"kc", "-10000.0", "10000.0"}, {"kd", "-10000.0", "10000.0"},
                {"ke", "-10000.0", "10000.0"}, {"e1", "0.0", "10000.0"},
                {"se1", "0.0", "10000.0"}, {"e2", "0.0", "10000.0"},
                {"se2", "0.0", "10000.0"}
        });
    }

    @Override public void setValue(String name, int value) { setValue(name, (double) value); }
    @Override public void setValue(String name, double value) {
        switch (name.toLowerCase()) {
            case "tr" -> tr=value; case "kpr" -> kpr=value; case "kir" -> kir=value;
            case "kdr" -> kdr=value; case "tdr" -> tdr=value;
            case "vpidmax" -> vpidmax=value; case "vpidmin" -> vpidmin=value;
            case "vrmax" -> vrmax=value; case "vrmin" -> vrmin=value;
            case "vfemax" -> vfemax=value; case "vemin" -> vemin=value;
            case "ta" -> ta=value; case "ka" -> ka=value; case "te" -> te=value;
            case "kc" -> kc=value; case "kd" -> kd=value; case "ke" -> ke=value;
            case "e1" -> e1=value; case "se1" -> se1=value;
            case "e2" -> e2=value; case "se2" -> se2=value;
            default -> { }
        }
    }

    public double getTr(){return tr;} public void setTr(double v){tr=v;}
    public double getKpr(){return kpr;} public void setKpr(double v){kpr=v;}
    public double getKir(){return kir;} public void setKir(double v){kir=v;}
    public double getKdr(){return kdr;} public void setKdr(double v){kdr=v;}
    public double getTdr(){return tdr;} public void setTdr(double v){tdr=v;}
    public double getVpidmax(){return vpidmax;} public void setVpidmax(double v){vpidmax=v;}
    public double getVpidmin(){return vpidmin;} public void setVpidmin(double v){vpidmin=v;}
    public double getVrmax(){return vrmax;} public void setVrmax(double v){vrmax=v;}
    public double getVrmin(){return vrmin;} public void setVrmin(double v){vrmin=v;}
    public double getVfemax(){return vfemax;} public void setVfemax(double v){vfemax=v;}
    public double getVemin(){return vemin;} public void setVemin(double v){vemin=v;}
    public double getTa(){return ta;} public void setTa(double v){ta=v;}
    public double getKa(){return ka;} public void setKa(double v){ka=v;}
    public double getTe(){return te;} public void setTe(double v){te=v;}
    public double getKc(){return kc;} public void setKc(double v){kc=v;}
    public double getKd(){return kd;} public void setKd(double v){kd=v;}
    public double getKe(){return ke;} public void setKe(double v){ke=v;}
    public double getE1(){return e1;} public void setE1(double v){e1=v;}
    public double getSe1(){return se1;} public void setSe1(double v){se1=v;}
    public double getE2(){return e2;} public void setE2(double v){e2=v;}
    public double getSe2(){return se2;} public void setSe2(double v){se2=v;}
}
