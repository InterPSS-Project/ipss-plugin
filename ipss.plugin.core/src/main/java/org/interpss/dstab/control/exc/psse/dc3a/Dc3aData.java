package org.interpss.dstab.control.exc.psse.dc3a;

import org.interpss.dstab.control.base.BaseControllerData;

/** IEEE 421.5-2005 DC3A / PSLF ESDC3A excitation-system parameters. */
public final class Dc3aData extends BaseControllerData {
    private double tr,kv,vrmax,vrmin,trh,te,ke,vemin,e1,se1,e2,se2,spdmlt;
    private int exclim;
    public Dc3aData(){setRangeParameters(new String[][]{
            {"tr","0","1000"},{"kv","0","10000"},{"vrmax","-10000","10000"},
            {"vrmin","-10000","10000"},{"trh","0","1000"},{"te","0","1000"},
            {"ke","-10000","10000"},{"vemin","-10000","10000"},
            {"e1","0","10000"},{"se1","0","10000"},{"e2","0","10000"},
            {"se2","0","10000"},{"spdmlt","0","1"}});}
    @Override public void setValue(String name,int value){if("exclim".equalsIgnoreCase(name))exclim=value;else setValue(name,(double)value);}
    @Override public void setValue(String name,double value){switch(name.toLowerCase()){
        case "tr"->tr=value;case "kv"->kv=value;case "vrmax"->vrmax=value;case "vrmin"->vrmin=value;
        case "trh"->trh=value;case "te"->te=value;case "ke"->ke=value;case "vemin"->vemin=value;
        case "e1"->e1=value;case "se1"->se1=value;case "e2"->e2=value;case "se2"->se2=value;
        case "spdmlt"->spdmlt=value;case "exclim"->exclim=(int)value;default->{}}}
    public double getTr(){return tr;}public void setTr(double v){tr=v;}public double getKv(){return kv;}public void setKv(double v){kv=v;}
    public double getVrmax(){return vrmax;}public void setVrmax(double v){vrmax=v;}public double getVrmin(){return vrmin;}public void setVrmin(double v){vrmin=v;}
    public double getTrh(){return trh;}public void setTrh(double v){trh=v;}public double getTe(){return te;}public void setTe(double v){te=v;}
    public double getKe(){return ke;}public void setKe(double v){ke=v;}public double getVemin(){return vemin;}public void setVemin(double v){vemin=v;}
    public double getE1(){return e1;}public void setE1(double v){e1=v;}public double getSe1(){return se1;}public void setSe1(double v){se1=v;}
    public double getE2(){return e2;}public void setE2(double v){e2=v;}public double getSe2(){return se2;}public void setSe2(double v){se2=v;}
    public double getSpdmlt(){return spdmlt;}public void setSpdmlt(double v){spdmlt=v;}public int getExclim(){return exclim;}public void setExclim(int v){exclim=v;}
}
