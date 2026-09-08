package org.interpss.dstab.control.exc.psse.dc4c;

import org.interpss.dstab.control.base.BaseControllerData;

/** IEEE 421.5-2016 / native PSS/E DC4C excitation-system parameters. */
public final class Dc4cData extends BaseControllerData {
    private int oel, uel, scl, sw1 = 1;
    private double tr, kpr, kir, kdr, tdr, vrmax, vrmin, ka, ta, ke, te;
    private double kf, tf, vemin, e1, se1, e2, se2, kp, ki, xl, thetaP, kc1, vbmax;
    private double spdmlt;

    public Dc4cData() {
        setRangeParameters(new String[][] {
                {"tr","0","1000"},{"kpr","-10000","10000"},{"kir","-10000","10000"},
                {"kdr","-10000","10000"},{"tdr","0","1000"},{"vrmax","-10000","10000"},
                {"vrmin","-10000","10000"},{"ka","-10000","10000"},{"ta","0","1000"},
                {"ke","-10000","10000"},{"te","0","1000"},{"kf","-10000","10000"},
                {"tf","0","1000"},{"vemin","-10000","10000"},{"e1","0","10000"},
                {"se1","0","10000"},{"e2","0","10000"},{"se2","0","10000"},
                {"kp","-10000","10000"},{"ki","-10000","10000"},{"xl","-10000","10000"},
                {"thetap","-360","360"},{"kc1","-10000","10000"},{"vbmax","0","10000"},
                {"spdmlt","0","1"}
        });
    }

    @Override public void setValue(String name, int value) {
        switch (name.toLowerCase()) {
            case "oel" -> oel=value; case "uel" -> uel=value; case "scl" -> scl=value;
            case "sw1" -> sw1=value; default -> setValue(name,(double)value);
        }
    }
    @Override public void setValue(String name, double value) {
        switch (name.toLowerCase()) {
            case "oel" -> oel=(int)value; case "uel" -> uel=(int)value;
            case "scl" -> scl=(int)value; case "sw1" -> sw1=(int)value;
            case "tr" -> tr=value; case "kpr" -> kpr=value; case "kir" -> kir=value;
            case "kdr" -> kdr=value; case "tdr" -> tdr=value; case "vrmax" -> vrmax=value;
            case "vrmin" -> vrmin=value; case "ka" -> ka=value; case "ta" -> ta=value;
            case "ke" -> ke=value; case "te" -> te=value; case "kf" -> kf=value;
            case "tf" -> tf=value; case "vemin" -> vemin=value; case "e1" -> e1=value;
            case "se1" -> se1=value; case "e2" -> e2=value; case "se2" -> se2=value;
            case "kp" -> kp=value; case "ki" -> ki=value; case "xl" -> xl=value;
            case "thetap" -> thetaP=value; case "kc1" -> kc1=value;
            case "vbmax" -> vbmax=value; case "spdmlt" -> spdmlt=value; default -> { }
        }
    }

    public int getOel(){return oel;} public void setOel(int v){oel=v;}
    public int getUel(){return uel;} public void setUel(int v){uel=v;}
    public int getScl(){return scl;} public void setScl(int v){scl=v;}
    public int getSw1(){return sw1;} public void setSw1(int v){sw1=v;}
    public double getTr(){return tr;} public void setTr(double v){tr=v;}
    public double getKpr(){return kpr;} public void setKpr(double v){kpr=v;}
    public double getKir(){return kir;} public void setKir(double v){kir=v;}
    public double getKdr(){return kdr;} public void setKdr(double v){kdr=v;}
    public double getTdr(){return tdr;} public void setTdr(double v){tdr=v;}
    public double getVrmax(){return vrmax;} public void setVrmax(double v){vrmax=v;}
    public double getVrmin(){return vrmin;} public void setVrmin(double v){vrmin=v;}
    public double getKa(){return ka;} public void setKa(double v){ka=v;}
    public double getTa(){return ta;} public void setTa(double v){ta=v;}
    public double getKe(){return ke;} public void setKe(double v){ke=v;}
    public double getTe(){return te;} public void setTe(double v){te=v;}
    public double getKf(){return kf;} public void setKf(double v){kf=v;}
    public double getTf(){return tf;} public void setTf(double v){tf=v;}
    public double getVemin(){return vemin;} public void setVemin(double v){vemin=v;}
    public double getE1(){return e1;} public void setE1(double v){e1=v;}
    public double getSe1(){return se1;} public void setSe1(double v){se1=v;}
    public double getE2(){return e2;} public void setE2(double v){e2=v;}
    public double getSe2(){return se2;} public void setSe2(double v){se2=v;}
    public double getKp(){return kp;} public void setKp(double v){kp=v;}
    public double getKi(){return ki;} public void setKi(double v){ki=v;}
    public double getXl(){return xl;} public void setXl(double v){xl=v;}
    public double getThetaP(){return thetaP;} public void setThetaP(double v){thetaP=v;}
    public double getKc1(){return kc1;} public void setKc1(double v){kc1=v;}
    public double getVbmax(){return vbmax;} public void setVbmax(double v){vbmax=v;}
    /** Optional PowerWorld extension; it is not present in the native PSS/E record. */
    public double getSpdmlt(){return spdmlt;} public void setSpdmlt(double v){spdmlt=v;}
}
