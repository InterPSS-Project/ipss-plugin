package org.interpss.dstab.control.exc.psse.ac7c;

import org.interpss.dstab.control.base.BaseControllerData;

/** IEEE 421.5-2016 / PSS/E AC7C excitation-system parameters. */
public final class Ac7cData extends BaseControllerData {
    // PSS/E carries OEL, UEL, VOS, SW1 and SW2. SCL and Spdmlt are typed inputs.
    private int oelLocation, uelLocation, sclLocation, vosLocation = 1, sw1 = 1, sw2 = 1;
    private double tr, kpr, kir, kdr, tdr, vrmax, vrmin;
    private double kpa, kia, vamax, vamin, kp, kl;
    private double kf1, kf2, kf3, tf, kc, kd, ke, te;
    private double vfemax, vemin, e1, se1, e2, se2;
    private double ki, xl, thetaP, kc1, vbmax, kr, spdmlt;

    public Ac7cData() {
        setRangeParameters(new String[][] {
                {"tr","0","1000"},{"kpr","-10000","10000"},{"kir","-10000","10000"},
                {"kdr","-10000","10000"},{"tdr","0","1000"},{"vrmax","-10000","10000"},
                {"vrmin","-10000","10000"},{"kpa","-10000","10000"},{"kia","-10000","10000"},
                {"vamax","-10000","10000"},{"vamin","-10000","10000"},{"kp","-10000","10000"},
                {"kl","-10000","10000"},{"kf1","-10000","10000"},{"kf2","-10000","10000"},
                {"kf3","-10000","10000"},{"tf","0","1000"},{"kc","0","10000"},
                {"kd","0","10000"},{"ke","-10000","10000"},{"te","0","1000"},
                {"vfemax","-10000","10000"},{"vemin","-10000","10000"},{"e1","0","10000"},
                {"se1","0","10000"},{"e2","0","10000"},{"se2","0","10000"},
                {"ki","-10000","10000"},{"xl","-10000","10000"},{"thetap","-360","360"},
                {"kc1","0","10000"},{"vbmax","0","10000"},{"kr","-10000","10000"},
                {"spdmlt","-10000","10000"}
        });
    }

    @Override public void setValue(String name, int value) {
        switch (name.toLowerCase()) {
            case "oellocation" -> oelLocation=value; case "uellocation" -> uelLocation=value;
            case "scllocation" -> sclLocation=value; case "voslocation" -> vosLocation=value;
            case "sw1" -> sw1=value; case "sw2" -> sw2=value;
            default -> setValue(name,(double)value);
        }
    }
    @Override public void setValue(String name, double value) {
        switch (name.toLowerCase()) {
            case "tr"->tr=value;case "kpr"->kpr=value;case "kir"->kir=value;case "kdr"->kdr=value;
            case "tdr"->tdr=value;case "vrmax"->vrmax=value;case "vrmin"->vrmin=value;
            case "kpa"->kpa=value;case "kia"->kia=value;case "vamax"->vamax=value;case "vamin"->vamin=value;
            case "kp"->kp=value;case "kl"->kl=value;case "kf1"->kf1=value;case "kf2"->kf2=value;
            case "kf3"->kf3=value;case "tf"->tf=value;case "kc"->kc=value;case "kd"->kd=value;
            case "ke"->ke=value;case "te"->te=value;case "vfemax"->vfemax=value;case "vemin"->vemin=value;
            case "e1"->e1=value;case "se1"->se1=value;case "e2"->e2=value;case "se2"->se2=value;
            case "ki"->ki=value;case "xl"->xl=value;case "thetap"->thetaP=value;case "kc1"->kc1=value;
            case "vbmax"->vbmax=value;case "kr"->kr=value;case "spdmlt"->spdmlt=value;
            default->{ }
        }
    }

    public int getOelLocation(){return oelLocation;} public void setOelLocation(int v){oelLocation=v;}
    public int getUelLocation(){return uelLocation;} public void setUelLocation(int v){uelLocation=v;}
    public int getSclLocation(){return sclLocation;} public void setSclLocation(int v){sclLocation=v;}
    public int getVosLocation(){return vosLocation;} public void setVosLocation(int v){vosLocation=v;}
    public int getSw1(){return sw1;} public void setSw1(int v){sw1=v;}
    public int getSw2(){return sw2;} public void setSw2(int v){sw2=v;}
    public double getTr(){return tr;} public void setTr(double v){tr=v;}
    public double getKpr(){return kpr;} public void setKpr(double v){kpr=v;}
    public double getKir(){return kir;} public void setKir(double v){kir=v;}
    public double getKdr(){return kdr;} public void setKdr(double v){kdr=v;}
    public double getTdr(){return tdr;} public void setTdr(double v){tdr=v;}
    public double getVrmax(){return vrmax;} public void setVrmax(double v){vrmax=v;}
    public double getVrmin(){return vrmin;} public void setVrmin(double v){vrmin=v;}
    public double getKpa(){return kpa;} public void setKpa(double v){kpa=v;}
    public double getKia(){return kia;} public void setKia(double v){kia=v;}
    public double getVamax(){return vamax;} public void setVamax(double v){vamax=v;}
    public double getVamin(){return vamin;} public void setVamin(double v){vamin=v;}
    public double getKp(){return kp;} public void setKp(double v){kp=v;}
    public double getKl(){return kl;} public void setKl(double v){kl=v;}
    public double getKf1(){return kf1;} public void setKf1(double v){kf1=v;}
    public double getKf2(){return kf2;} public void setKf2(double v){kf2=v;}
    public double getKf3(){return kf3;} public void setKf3(double v){kf3=v;}
    public double getTf(){return tf;} public void setTf(double v){tf=v;}
    public double getKc(){return kc;} public void setKc(double v){kc=v;}
    public double getKd(){return kd;} public void setKd(double v){kd=v;}
    public double getKe(){return ke;} public void setKe(double v){ke=v;}
    public double getTe(){return te;} public void setTe(double v){te=v;}
    public double getVfemax(){return vfemax;} public void setVfemax(double v){vfemax=v;}
    public double getVemin(){return vemin;} public void setVemin(double v){vemin=v;}
    public double getE1(){return e1;} public void setE1(double v){e1=v;}
    public double getSe1(){return se1;} public void setSe1(double v){se1=v;}
    public double getE2(){return e2;} public void setE2(double v){e2=v;}
    public double getSe2(){return se2;} public void setSe2(double v){se2=v;}
    public double getKi(){return ki;} public void setKi(double v){ki=v;}
    public double getXl(){return xl;} public void setXl(double v){xl=v;}
    public double getThetaP(){return thetaP;} public void setThetaP(double v){thetaP=v;}
    public double getKc1(){return kc1;} public void setKc1(double v){kc1=v;}
    public double getVbmax(){return vbmax;} public void setVbmax(double v){vbmax=v;}
    public double getKr(){return kr;} public void setKr(double v){kr=v;}
    public double getSpdmlt(){return spdmlt;} public void setSpdmlt(double v){spdmlt=v;}
}
