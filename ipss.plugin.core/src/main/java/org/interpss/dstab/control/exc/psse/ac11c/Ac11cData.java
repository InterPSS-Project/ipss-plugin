package org.interpss.dstab.control.exc.psse.ac11c;

import org.interpss.dstab.control.base.BaseControllerData;

/** IEEE 421.5-2016 / PSS/E AC11C excitation-system parameters. */
public final class Ac11cData extends BaseControllerData {
    // PSS/E carries OEL, UEL, VOS and SW1. SCL is a typed controller input.
    private int oelLocation, uelLocation, vosLocation = 1, sclLocation, sw1 = 1;
    private double tr, kpa, tia, kpu, tiu, kb, tb, kpo, tio;
    private double vrsmax, vrsmin, vrmax, vrmin, vamax, vamin;
    private double te, kc, kd, ke, vfemax, vemin, e1, se1, e2, se2;
    private double kp, ki, xl, thetaP, kc1, vbmax1, ki2, kc2, vbmax2;
    private double kboost, vboost;

    public Ac11cData() {
        setRangeParameters(new String[][] {
                {"tr","0","1000"},{"kpa","-10000","10000"},{"tia","0","1000"},
                {"kpu","-10000","10000"},{"tiu","0","1000"},
                {"kb","-10000","10000"},{"tb","0","1000"},
                {"kpo","-10000","10000"},{"tio","0","1000"},
                {"vrsmax","-10000","10000"},{"vrsmin","-10000","10000"},
                {"vrmax","-10000","10000"},{"vrmin","-10000","10000"},
                {"vamax","-10000","10000"},{"vamin","-10000","10000"},
                {"te","0","1000"},{"kc","0","10000"},{"kd","0","10000"},
                {"ke","-10000","10000"},{"vfemax","-10000","10000"},
                {"vemin","-10000","10000"},{"e1","0","10000"},
                {"se1","0","10000"},{"e2","0","10000"},{"se2","0","10000"},
                {"kp","-10000","10000"},{"ki","-10000","10000"},
                {"xl","-10000","10000"},{"thetap","-360","360"},
                {"kc1","0","10000"},{"vbmax1","0","10000"},
                {"ki2","-10000","10000"},{"kc2","0","10000"},
                {"vbmax2","0","10000"},{"kboost","-10000","10000"},
                {"vboost","-10000","10000"}
        });
    }

    @Override public void setValue(String name, int value) {
        switch (name.toLowerCase()) {
            case "oellocation" -> oelLocation=value;
            case "uellocation" -> uelLocation=value;
            case "voslocation" -> vosLocation=value;
            case "scllocation" -> sclLocation=value;
            case "sw1" -> sw1=value;
            default -> setValue(name,(double)value);
        }
    }

    @Override public void setValue(String name, double value) {
        switch (name.toLowerCase()) {
            case "tr"->tr=value;case "kpa"->kpa=value;case "tia"->tia=value;
            case "kpu"->kpu=value;case "tiu"->tiu=value;case "kb"->kb=value;
            case "tb"->tb=value;case "kpo"->kpo=value;case "tio"->tio=value;
            case "vrsmax"->vrsmax=value;case "vrsmin"->vrsmin=value;
            case "vrmax"->vrmax=value;case "vrmin"->vrmin=value;
            case "vamax"->vamax=value;case "vamin"->vamin=value;
            case "te"->te=value;case "kc"->kc=value;case "kd"->kd=value;
            case "ke"->ke=value;case "vfemax"->vfemax=value;case "vemin"->vemin=value;
            case "e1"->e1=value;case "se1"->se1=value;case "e2"->e2=value;case "se2"->se2=value;
            case "kp"->kp=value;case "ki"->ki=value;case "xl"->xl=value;case "thetap"->thetaP=value;
            case "kc1"->kc1=value;case "vbmax1"->vbmax1=value;case "ki2"->ki2=value;
            case "kc2"->kc2=value;case "vbmax2"->vbmax2=value;
            case "kboost"->kboost=value;case "vboost"->vboost=value;
            default->{ }
        }
    }

    public int getOelLocation(){return oelLocation;} public void setOelLocation(int v){oelLocation=v;}
    public int getUelLocation(){return uelLocation;} public void setUelLocation(int v){uelLocation=v;}
    public int getVosLocation(){return vosLocation;} public void setVosLocation(int v){vosLocation=v;}
    public int getSclLocation(){return sclLocation;} public void setSclLocation(int v){sclLocation=v;}
    public int getSw1(){return sw1;} public void setSw1(int v){sw1=v;}
    public double getTr(){return tr;} public void setTr(double v){tr=v;}
    public double getKpa(){return kpa;} public void setKpa(double v){kpa=v;}
    public double getTia(){return tia;} public void setTia(double v){tia=v;}
    public double getKpu(){return kpu;} public void setKpu(double v){kpu=v;}
    public double getTiu(){return tiu;} public void setTiu(double v){tiu=v;}
    public double getKb(){return kb;} public void setKb(double v){kb=v;}
    public double getTb(){return tb;} public void setTb(double v){tb=v;}
    public double getKpo(){return kpo;} public void setKpo(double v){kpo=v;}
    public double getTio(){return tio;} public void setTio(double v){tio=v;}
    public double getVrsmax(){return vrsmax;} public void setVrsmax(double v){vrsmax=v;}
    public double getVrsmin(){return vrsmin;} public void setVrsmin(double v){vrsmin=v;}
    public double getVrmax(){return vrmax;} public void setVrmax(double v){vrmax=v;}
    public double getVrmin(){return vrmin;} public void setVrmin(double v){vrmin=v;}
    public double getVamax(){return vamax;} public void setVamax(double v){vamax=v;}
    public double getVamin(){return vamin;} public void setVamin(double v){vamin=v;}
    public double getTe(){return te;} public void setTe(double v){te=v;}
    public double getKc(){return kc;} public void setKc(double v){kc=v;}
    public double getKd(){return kd;} public void setKd(double v){kd=v;}
    public double getKe(){return ke;} public void setKe(double v){ke=v;}
    public double getVfemax(){return vfemax;} public void setVfemax(double v){vfemax=v;}
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
    public double getVbmax1(){return vbmax1;} public void setVbmax1(double v){vbmax1=v;}
    public double getKi2(){return ki2;} public void setKi2(double v){ki2=v;}
    public double getKc2(){return kc2;} public void setKc2(double v){kc2=v;}
    public double getVbmax2(){return vbmax2;} public void setVbmax2(double v){vbmax2=v;}
    public double getKboost(){return kboost;} public void setKboost(double v){kboost=v;}
    public double getVboost(){return vboost;} public void setVboost(double v){vboost=v;}
}
