package org.interpss.dstab.control.exc.psse.st2c;

import org.interpss.dstab.control.base.BaseControllerData;

/** Native PSS/E IEEE 421.5-2016 ST2C excitation-system parameters. */
public final class St2cData extends BaseControllerData {
    private int oel, uel, scl;
    private double tr, kpr, kir, kdr, tdr, vpidmax, vpidmin, ka, ta;
    private double vrmax, vrmin, te, efdmax, ke, kf, tf;
    private double kp, ki, xl, thetaP, kc, vbmax;

    public St2cData() {
        setRangeParameters(new String[][] {
                {"tr","0","1000"},{"kpr","-10000","10000"},{"kir","-10000","10000"},
                {"kdr","-10000","10000"},{"tdr","0","1000"},
                {"vpidmax","-10000","10000"},{"vpidmin","-10000","10000"},
                {"ka","-10000","10000"},{"ta","0","1000"},
                {"vrmax","-10000","10000"},{"vrmin","-10000","10000"},
                {"te","0","1000"},{"efdmax","-10000","10000"},{"ke","-10000","10000"},
                {"kf","-10000","10000"},{"tf","0","1000"},
                {"kp","-10000","10000"},{"ki","-10000","10000"},{"xl","-10000","10000"},
                {"thetap","-360","360"},{"kc","-10000","10000"},{"vbmax","-10000","10000"}
        });
    }

    @Override public void setValue(String name, int value) { switch (name.toLowerCase()) {
        case "oel" -> oel=value; case "uel" -> uel=value; case "scl" -> scl=value;
        default -> setValue(name,(double)value);
    }}
    @Override public void setValue(String name, double value) { switch (name.toLowerCase()) {
        case "oel" -> oel=(int)value; case "uel" -> uel=(int)value; case "scl" -> scl=(int)value;
        case "tr" -> tr=value; case "kpr" -> kpr=value; case "kir" -> kir=value;
        case "kdr" -> kdr=value; case "tdr" -> tdr=value;
        case "vpidmax" -> vpidmax=value; case "vpidmin" -> vpidmin=value;
        case "ka" -> ka=value; case "ta" -> ta=value;
        case "vrmax" -> vrmax=value; case "vrmin" -> vrmin=value;
        case "te" -> te=value; case "efdmax" -> efdmax=value; case "ke" -> ke=value;
        case "kf" -> kf=value; case "tf" -> tf=value;
        case "kp" -> kp=value; case "ki" -> ki=value; case "xl" -> xl=value;
        case "thetap" -> thetaP=value; case "kc" -> kc=value; case "vbmax" -> vbmax=value;
        default -> { }
    }}

    public int getOel(){return oel;} public void setOel(int v){oel=v;}
    public int getUel(){return uel;} public void setUel(int v){uel=v;}
    public int getScl(){return scl;} public void setScl(int v){scl=v;}
    public double getTr(){return tr;} public void setTr(double v){tr=v;}
    public double getKpr(){return kpr;} public void setKpr(double v){kpr=v;}
    public double getKir(){return kir;} public void setKir(double v){kir=v;}
    public double getKdr(){return kdr;} public void setKdr(double v){kdr=v;}
    public double getTdr(){return tdr;} public void setTdr(double v){tdr=v;}
    public double getVpidmax(){return vpidmax;} public void setVpidmax(double v){vpidmax=v;}
    public double getVpidmin(){return vpidmin;} public void setVpidmin(double v){vpidmin=v;}
    public double getKa(){return ka;} public void setKa(double v){ka=v;}
    public double getTa(){return ta;} public void setTa(double v){ta=v;}
    public double getVrmax(){return vrmax;} public void setVrmax(double v){vrmax=v;}
    public double getVrmin(){return vrmin;} public void setVrmin(double v){vrmin=v;}
    public double getTe(){return te;} public void setTe(double v){te=v;}
    public double getEfdmax(){return efdmax;} public void setEfdmax(double v){efdmax=v;}
    public double getKe(){return ke;} public void setKe(double v){ke=v;}
    public double getKf(){return kf;} public void setKf(double v){kf=v;}
    public double getTf(){return tf;} public void setTf(double v){tf=v;}
    public double getKp(){return kp;} public void setKp(double v){kp=v;}
    public double getKi(){return ki;} public void setKi(double v){ki=v;}
    public double getXl(){return xl;} public void setXl(double v){xl=v;}
    public double getThetaP(){return thetaP;} public void setThetaP(double v){thetaP=v;}
    public double getKc(){return kc;} public void setKc(double v){kc=v;}
    public double getVbmax(){return vbmax;} public void setVbmax(double v){vbmax=v;}
}
