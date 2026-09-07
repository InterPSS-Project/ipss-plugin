package org.interpss.dstab.control.exc.psse.ac6c;

import org.interpss.dstab.control.base.BaseControllerData;

/** IEEE 421.5-2016 / PSS/E AC6C excitation-system parameters. */
public final class Ac6cData extends BaseControllerData {
    // PSS/E carries OEL and UEL. SCL and Spdmlt remain typed PowerWorld/IEEE inputs.
    private int oelLocation,uelLocation,sclLocation;
    private double tr,ka,ta,tk,tb,tc,vamax,vamin,efemax,efemin,te;
    private double vfelim,kh,vhmax,th,tj,kc,kd,ke,e1,se1,e2,se2,vfemax,vemin,spdmlt;

    public Ac6cData(){
        setRangeParameters(new String[][]{
                {"tr","0","1000"},{"ka","0","10000"},{"ta","0","1000"},
                {"tk","0","1000"},{"tb","0","1000"},{"tc","0","1000"},
                {"vamax","-10000","10000"},{"vamin","-10000","10000"},
                {"efemax","-10000","10000"},{"efemin","-10000","10000"},
                {"te","0","1000"},{"vfelim","-10000","10000"},
                {"kh","-10000","10000"},{"vhmax","0","10000"},
                {"th","0","1000"},{"tj","0","1000"},{"kc","0","10000"},
                {"kd","0","10000"},{"ke","-10000","10000"},
                {"e1","0","10000"},{"se1","0","10000"},{"e2","0","10000"},
                {"se2","0","10000"},{"vfemax","-10000","10000"},
                {"vemin","-10000","10000"},{"spdmlt","-10000","10000"}
        });
    }

    @Override public void setValue(String name,int value){
        switch(name.toLowerCase()){
            case "oellocation" -> oelLocation=value;
            case "uellocation" -> uelLocation=value;
            case "scllocation" -> sclLocation=value;
            default -> setValue(name,(double)value);
        }
    }
    @Override public void setValue(String name,double value){
        switch(name.toLowerCase()){
            case "tr"->tr=value;case "ka"->ka=value;case "ta"->ta=value;
            case "tk"->tk=value;case "tb"->tb=value;case "tc"->tc=value;
            case "vamax"->vamax=value;case "vamin"->vamin=value;
            case "efemax"->efemax=value;case "efemin"->efemin=value;case "te"->te=value;
            case "vfelim"->vfelim=value;case "kh"->kh=value;case "vhmax"->vhmax=value;
            case "th"->th=value;case "tj"->tj=value;case "kc"->kc=value;
            case "kd"->kd=value;case "ke"->ke=value;case "e1"->e1=value;
            case "se1"->se1=value;case "e2"->e2=value;case "se2"->se2=value;
            case "vfemax"->vfemax=value;case "vemin"->vemin=value;case "spdmlt"->spdmlt=value;
            default->{ }
        }
    }

    public int getOelLocation(){return oelLocation;}public void setOelLocation(int v){oelLocation=v;}
    public int getUelLocation(){return uelLocation;}public void setUelLocation(int v){uelLocation=v;}
    public int getSclLocation(){return sclLocation;}public void setSclLocation(int v){sclLocation=v;}
    public double getTr(){return tr;}public void setTr(double v){tr=v;}
    public double getKa(){return ka;}public void setKa(double v){ka=v;}
    public double getTa(){return ta;}public void setTa(double v){ta=v;}
    public double getTk(){return tk;}public void setTk(double v){tk=v;}
    public double getTb(){return tb;}public void setTb(double v){tb=v;}
    public double getTc(){return tc;}public void setTc(double v){tc=v;}
    public double getVamax(){return vamax;}public void setVamax(double v){vamax=v;}
    public double getVamin(){return vamin;}public void setVamin(double v){vamin=v;}
    public double getEfemax(){return efemax;}public void setEfemax(double v){efemax=v;}
    public double getEfemin(){return efemin;}public void setEfemin(double v){efemin=v;}
    public double getTe(){return te;}public void setTe(double v){te=v;}
    public double getVfelim(){return vfelim;}public void setVfelim(double v){vfelim=v;}
    public double getKh(){return kh;}public void setKh(double v){kh=v;}
    public double getVhmax(){return vhmax;}public void setVhmax(double v){vhmax=v;}
    public double getTh(){return th;}public void setTh(double v){th=v;}
    public double getTj(){return tj;}public void setTj(double v){tj=v;}
    public double getKc(){return kc;}public void setKc(double v){kc=v;}
    public double getKd(){return kd;}public void setKd(double v){kd=v;}
    public double getKe(){return ke;}public void setKe(double v){ke=v;}
    public double getE1(){return e1;}public void setE1(double v){e1=v;}
    public double getSe1(){return se1;}public void setSe1(double v){se1=v;}
    public double getE2(){return e2;}public void setE2(double v){e2=v;}
    public double getSe2(){return se2;}public void setSe2(double v){se2=v;}
    public double getVfemax(){return vfemax;}public void setVfemax(double v){vfemax=v;}
    public double getVemin(){return vemin;}public void setVemin(double v){vemin=v;}
    public double getSpdmlt(){return spdmlt;}public void setSpdmlt(double v){spdmlt=v;}
}
