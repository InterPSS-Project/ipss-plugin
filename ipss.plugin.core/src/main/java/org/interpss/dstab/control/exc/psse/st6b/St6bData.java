package org.interpss.dstab.control.exc.psse.st6b;

import org.interpss.dstab.control.base.BaseControllerData;

/** IEEE 421.5 ST6B / PSLF ESST6B excitation-system parameters. */
public final class St6bData extends BaseControllerData {
    private int oel,vrmult;
    private double tr,kpa,kia,kda,tda,vamax,vamin,kff,km,kcl,klr,ilr,vrmax,vrmin,kg,tg,ts;
    public St6bData(){setRangeParameters(new String[][]{
            {"tr","0","1000"},{"kpa","0","10000"},{"kia","0","10000"},
            {"kda","-10000","10000"},{"tda","0","1000"},{"vamax","-10000","10000"},
            {"vamin","-10000","10000"},{"kff","-10000","10000"},{"km","-10000","10000"},
            {"kcl","-10000","10000"},{"klr","-10000","10000"},{"ilr","-10000","10000"},
            {"vrmax","-10000","10000"},{"vrmin","-10000","10000"},{"kg","-10000","10000"},
            {"tg","0","1000"},{"ts","0","1000"}
    });}
    @Override public void setValue(String n,int v){switch(n.toLowerCase()){case"oel"->oel=v;case"vrmult"->vrmult=v;default->setValue(n,(double)v);}}
    @Override public void setValue(String n,double v){switch(n.toLowerCase()){
        case"oel"->oel=(int)v;case"vrmult"->vrmult=(int)v;case"tr"->tr=v;case"kpa"->kpa=v;case"kia"->kia=v;
        case"kda"->kda=v;case"tda"->tda=v;case"vamax"->vamax=v;case"vamin"->vamin=v;case"kff"->kff=v;
        case"km"->km=v;case"kcl"->kcl=v;case"klr"->klr=v;case"ilr"->ilr=v;case"vrmax"->vrmax=v;
        case"vrmin"->vrmin=v;case"kg"->kg=v;case"tg"->tg=v;case"ts"->ts=v;default->{}}}
    public int getOel(){return oel;}public void setOel(int v){oel=v;}public int getVrmult(){return vrmult;}public void setVrmult(int v){vrmult=v;}
    public double getTr(){return tr;}public void setTr(double v){tr=v;}public double getKpa(){return kpa;}public void setKpa(double v){kpa=v;}
    public double getKia(){return kia;}public void setKia(double v){kia=v;}public double getKda(){return kda;}public void setKda(double v){kda=v;}
    public double getTda(){return tda;}public void setTda(double v){tda=v;}public double getVamax(){return vamax;}public void setVamax(double v){vamax=v;}
    public double getVamin(){return vamin;}public void setVamin(double v){vamin=v;}public double getKff(){return kff;}public void setKff(double v){kff=v;}
    public double getKm(){return km;}public void setKm(double v){km=v;}public double getKcl(){return kcl;}public void setKcl(double v){kcl=v;}
    public double getKlr(){return klr;}public void setKlr(double v){klr=v;}public double getIlr(){return ilr;}public void setIlr(double v){ilr=v;}
    public double getVrmax(){return vrmax;}public void setVrmax(double v){vrmax=v;}public double getVrmin(){return vrmin;}public void setVrmin(double v){vrmin=v;}
    public double getKg(){return kg;}public void setKg(double v){kg=v;}public double getTg(){return tg;}public void setTg(double v){tg=v;}
    public double getTs(){return ts;}public void setTs(double v){ts=v;}
}
