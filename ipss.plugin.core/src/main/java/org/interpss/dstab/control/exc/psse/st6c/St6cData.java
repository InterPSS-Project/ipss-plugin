package org.interpss.dstab.control.exc.psse.st6c;

import org.interpss.dstab.control.base.BaseControllerData;

/** Native PSS/E IEEE 421.5-2016 ST6C excitation-system parameters. */
public final class St6cData extends BaseControllerData {
    private int oel, uel, scl, sw1;
    private double tr,kpa,kia,kda,tda,vamax,vamin,kff,km,kci,klr,ilr;
    private double vrmax,vrmin,kg,tg,vmmax,vmmin,ta,kp,ki,xl,thetaP,kc,vbmax;

    public St6cData(){setRangeParameters(new String[][]{
            {"tr","0","1000"},{"kpa","0","10000"},{"kia","0","10000"},
            {"kda","-10000","10000"},{"tda","0","1000"},{"vamax","-10000","10000"},
            {"vamin","-10000","10000"},{"kff","-10000","10000"},{"km","-10000","10000"},
            {"kci","-10000","10000"},{"klr","-10000","10000"},{"ilr","-10000","10000"},
            {"vrmax","-10000","10000"},{"vrmin","-10000","10000"},{"kg","-10000","10000"},
            {"tg","0","1000"},{"vmmax","-10000","10000"},{"vmmin","-10000","10000"},
            {"ta","0","1000"},{"kp","-10000","10000"},{"ki","-10000","10000"},
            {"xl","-10000","10000"},{"thetap","-360","360"},{"kc","-10000","10000"},
            {"vbmax","-10000","10000"}
    });}
    @Override public void setValue(String n,int v){switch(n.toLowerCase()){
        case"oel"->oel=v;case"uel"->uel=v;case"scl"->scl=v;case"sw1"->sw1=v;default->setValue(n,(double)v);}}
    @Override public void setValue(String n,double v){switch(n.toLowerCase()){
        case"oel"->oel=(int)v;case"uel"->uel=(int)v;case"scl"->scl=(int)v;case"sw1"->sw1=(int)v;
        case"tr"->tr=v;case"kpa"->kpa=v;case"kia"->kia=v;case"kda"->kda=v;case"tda"->tda=v;
        case"vamax"->vamax=v;case"vamin"->vamin=v;case"kff"->kff=v;case"km"->km=v;
        case"kci"->kci=v;case"klr"->klr=v;case"ilr"->ilr=v;case"vrmax"->vrmax=v;
        case"vrmin"->vrmin=v;case"kg"->kg=v;case"tg"->tg=v;case"vmmax"->vmmax=v;
        case"vmmin"->vmmin=v;case"ta"->ta=v;case"kp"->kp=v;case"ki"->ki=v;
        case"xl"->xl=v;case"thetap"->thetaP=v;case"kc"->kc=v;case"vbmax"->vbmax=v;default->{}}}
    public int getOel(){return oel;} public void setOel(int v){oel=v;}
    public int getUel(){return uel;} public void setUel(int v){uel=v;}
    public int getScl(){return scl;} public void setScl(int v){scl=v;}
    public int getSw1(){return sw1;} public void setSw1(int v){sw1=v;}
    public double getTr(){return tr;} public void setTr(double v){tr=v;}
    public double getKpa(){return kpa;} public void setKpa(double v){kpa=v;}
    public double getKia(){return kia;} public void setKia(double v){kia=v;}
    public double getKda(){return kda;} public void setKda(double v){kda=v;}
    public double getTda(){return tda;} public void setTda(double v){tda=v;}
    public double getVamax(){return vamax;} public void setVamax(double v){vamax=v;}
    public double getVamin(){return vamin;} public void setVamin(double v){vamin=v;}
    public double getKff(){return kff;} public void setKff(double v){kff=v;}
    public double getKm(){return km;} public void setKm(double v){km=v;}
    public double getKci(){return kci;} public void setKci(double v){kci=v;}
    public double getKlr(){return klr;} public void setKlr(double v){klr=v;}
    public double getIlr(){return ilr;} public void setIlr(double v){ilr=v;}
    public double getVrmax(){return vrmax;} public void setVrmax(double v){vrmax=v;}
    public double getVrmin(){return vrmin;} public void setVrmin(double v){vrmin=v;}
    public double getKg(){return kg;} public void setKg(double v){kg=v;}
    public double getTg(){return tg;} public void setTg(double v){tg=v;}
    public double getVmmax(){return vmmax;} public void setVmmax(double v){vmmax=v;}
    public double getVmmin(){return vmmin;} public void setVmmin(double v){vmmin=v;}
    public double getTa(){return ta;} public void setTa(double v){ta=v;}
    public double getKp(){return kp;} public void setKp(double v){kp=v;}
    public double getKi(){return ki;} public void setKi(double v){ki=v;}
    public double getXl(){return xl;} public void setXl(double v){xl=v;}
    public double getThetaP(){return thetaP;} public void setThetaP(double v){thetaP=v;}
    public double getKc(){return kc;} public void setKc(double v){kc=v;}
    public double getVbmax(){return vbmax;} public void setVbmax(double v){vbmax=v;}
}
