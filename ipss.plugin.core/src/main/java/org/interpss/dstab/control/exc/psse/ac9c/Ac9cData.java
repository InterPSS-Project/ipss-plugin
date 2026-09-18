package org.interpss.dstab.control.exc.psse.ac9c;

import org.interpss.dstab.control.base.BaseControllerData;

/** IEEE 421.5-2016 / PSS/E AC9C excitation-system parameters. */
public final class Ac9cData extends BaseControllerData {
    // PSS/E carries OEL, UEL and SW1. SCL and Spdmlt are typed inputs.
    private int oelLocation, uelLocation, sclLocation, sw1 = 1, sct;
    private double tr, kpr, kir, kdr, tdr, vpidmax, vpidmin;
    private double kpa, kia, vamax, vamin, ka, ta, vrmax, vrmin;
    private double kf, tf, kfw, vfwmax, vfwmin;
    private double kc, kd, ke, te, vfemax, vemin, e1, se1, e2, se2;
    private double kp, ki1, ki2, kc1, kc2, xl, thetaP, vbmax1, vbmax2;
    private double vlim1, vlim2, spdmlt;

    public Ac9cData() {
        setRangeParameters(new String[][] {
                {"tr","0","1000"},{"kpr","-10000","10000"},{"kir","-10000","10000"},
                {"kdr","-10000","10000"},{"tdr","0","1000"},
                {"vpidmax","-10000","10000"},{"vpidmin","-10000","10000"},
                {"kpa","-10000","10000"},{"kia","-10000","10000"},
                {"vamax","-10000","10000"},{"vamin","-10000","10000"},
                {"ka","-10000","10000"},{"ta","0","1000"},
                {"vrmax","-10000","10000"},{"vrmin","-10000","10000"},
                {"kf","-10000","10000"},{"tf","0","1000"},{"kfw","-10000","10000"},
                {"vfwmax","-10000","10000"},{"vfwmin","-10000","10000"},
                {"kc","0","10000"},{"kd","0","10000"},{"ke","-10000","10000"},
                {"te","0","1000"},{"vfemax","-10000","10000"},{"vemin","-10000","10000"},
                {"e1","0","10000"},{"se1","0","10000"},{"e2","0","10000"},{"se2","0","10000"},
                {"kp","-10000","10000"},{"ki1","-10000","10000"},{"ki2","-10000","10000"},
                {"kc1","0","10000"},{"kc2","0","10000"},{"xl","-10000","10000"},
                {"thetap","-360","360"},{"vbmax1","0","10000"},{"vbmax2","0","10000"},
                {"vlim1","-10000","10000"},{"vlim2","-10000","10000"},
                {"spdmlt","-10000","10000"}
        });
    }

    @Override public void setValue(String name, int value) {
        switch (name.toLowerCase()) {
            case "oellocation" -> oelLocation=value; case "uellocation" -> uelLocation=value;
            case "scllocation" -> sclLocation=value; case "sw1" -> sw1=value; case "sct" -> sct=value;
            default -> setValue(name,(double)value);
        }
    }
    @Override public void setValue(String name, double value) {
        switch (name.toLowerCase()) {
            case "tr"->tr=value;case "kpr"->kpr=value;case "kir"->kir=value;case "kdr"->kdr=value;
            case "tdr"->tdr=value;case "vpidmax"->vpidmax=value;case "vpidmin"->vpidmin=value;
            case "kpa"->kpa=value;case "kia"->kia=value;case "vamax"->vamax=value;case "vamin"->vamin=value;
            case "ka"->ka=value;case "ta"->ta=value;case "vrmax"->vrmax=value;case "vrmin"->vrmin=value;
            case "kf"->kf=value;case "tf"->tf=value;case "kfw"->kfw=value;
            case "vfwmax"->vfwmax=value;case "vfwmin"->vfwmin=value;
            case "kc"->kc=value;case "kd"->kd=value;case "ke"->ke=value;case "te"->te=value;
            case "vfemax"->vfemax=value;case "vemin"->vemin=value;case "e1"->e1=value;case "se1"->se1=value;
            case "e2"->e2=value;case "se2"->se2=value;case "kp"->kp=value;case "ki1"->ki1=value;
            case "ki2"->ki2=value;case "kc1"->kc1=value;case "kc2"->kc2=value;case "xl"->xl=value;
            case "thetap"->thetaP=value;case "vbmax1"->vbmax1=value;case "vbmax2"->vbmax2=value;
            case "vlim1"->vlim1=value;case "vlim2"->vlim2=value;case "spdmlt"->spdmlt=value;
            default->{ }
        }
    }

    public int getOelLocation(){return oelLocation;} public void setOelLocation(int v){oelLocation=v;}
    public int getUelLocation(){return uelLocation;} public void setUelLocation(int v){uelLocation=v;}
    public int getSclLocation(){return sclLocation;} public void setSclLocation(int v){sclLocation=v;}
    public int getSw1(){return sw1;} public void setSw1(int v){sw1=v;}
    public int getSct(){return sct;} public void setSct(int v){sct=v;}
    public double getTr(){return tr;} public void setTr(double v){tr=v;}
    public double getKpr(){return kpr;} public void setKpr(double v){kpr=v;}
    public double getKir(){return kir;} public void setKir(double v){kir=v;}
    public double getKdr(){return kdr;} public void setKdr(double v){kdr=v;}
    public double getTdr(){return tdr;} public void setTdr(double v){tdr=v;}
    public double getVpidmax(){return vpidmax;} public void setVpidmax(double v){vpidmax=v;}
    public double getVpidmin(){return vpidmin;} public void setVpidmin(double v){vpidmin=v;}
    public double getKpa(){return kpa;} public void setKpa(double v){kpa=v;}
    public double getKia(){return kia;} public void setKia(double v){kia=v;}
    public double getVamax(){return vamax;} public void setVamax(double v){vamax=v;}
    public double getVamin(){return vamin;} public void setVamin(double v){vamin=v;}
    public double getKa(){return ka;} public void setKa(double v){ka=v;}
    public double getTa(){return ta;} public void setTa(double v){ta=v;}
    public double getVrmax(){return vrmax;} public void setVrmax(double v){vrmax=v;}
    public double getVrmin(){return vrmin;} public void setVrmin(double v){vrmin=v;}
    public double getKf(){return kf;} public void setKf(double v){kf=v;}
    public double getTf(){return tf;} public void setTf(double v){tf=v;}
    public double getKfw(){return kfw;} public void setKfw(double v){kfw=v;}
    public double getVfwmax(){return vfwmax;} public void setVfwmax(double v){vfwmax=v;}
    public double getVfwmin(){return vfwmin;} public void setVfwmin(double v){vfwmin=v;}
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
    public double getKp(){return kp;} public void setKp(double v){kp=v;}
    public double getKi1(){return ki1;} public void setKi1(double v){ki1=v;}
    public double getKi2(){return ki2;} public void setKi2(double v){ki2=v;}
    public double getKc1(){return kc1;} public void setKc1(double v){kc1=v;}
    public double getKc2(){return kc2;} public void setKc2(double v){kc2=v;}
    public double getXl(){return xl;} public void setXl(double v){xl=v;}
    public double getThetaP(){return thetaP;} public void setThetaP(double v){thetaP=v;}
    public double getVbmax1(){return vbmax1;} public void setVbmax1(double v){vbmax1=v;}
    public double getVbmax2(){return vbmax2;} public void setVbmax2(double v){vbmax2=v;}
    public double getVlim1(){return vlim1;} public void setVlim1(double v){vlim1=v;}
    public double getVlim2(){return vlim2;} public void setVlim2(double v){vlim2=v;}
    public double getSpdmlt(){return spdmlt;} public void setSpdmlt(double v){spdmlt=v;}
}
