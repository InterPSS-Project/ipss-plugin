package org.interpss.dstab.control.exc.psse.st3c;

import org.interpss.dstab.control.base.BaseControllerData;

/** Native PSS/E IEEE 421.5-2016 ST3C excitation-system parameters. */
public final class St3cData extends BaseControllerData {
    private int oel,uel,scl,sw1;
    private double tr,vimax,vimin,kpr,kir,kdr,tdr,vpidmax,vpidmin,tc,tb;
    private double ka,ta,vrmax,vrmin,km,tm,vmmax,vmmin,kg,vgmax;
    private double kp,ki,xl,thetaP,kc,vbmax;

    public St3cData(){setRangeParameters(new String[][]{
            {"tr","0","1000"},{"vimax","-10000","10000"},{"vimin","-10000","10000"},
            {"kpr","-10000","10000"},{"kir","-10000","10000"},{"kdr","-10000","10000"},{"tdr","0","1000"},
            {"vpidmax","-10000","10000"},{"vpidmin","-10000","10000"},{"tc","-1000","1000"},{"tb","0","1000"},
            {"ka","-10000","10000"},{"ta","0","1000"},{"vrmax","-10000","10000"},{"vrmin","-10000","10000"},
            {"km","-10000","10000"},{"tm","0","1000"},{"vmmax","-10000","10000"},{"vmmin","-10000","10000"},
            {"kg","-10000","10000"},{"vgmax","-10000","10000"},{"kp","-10000","10000"},{"ki","-10000","10000"},
            {"xl","-10000","10000"},{"thetap","-360","360"},{"kc","-10000","10000"},{"vbmax","-10000","10000"}
    });}
    @Override public void setValue(String n,int v){switch(n.toLowerCase()){
        case "oel"->oel=v;case "uel"->uel=v;case "scl"->scl=v;case "sw1"->sw1=v;default->setValue(n,(double)v);}}
    @Override public void setValue(String n,double v){switch(n.toLowerCase()){
        case "oel"->oel=(int)v;case "uel"->uel=(int)v;case "scl"->scl=(int)v;case "sw1"->sw1=(int)v;
        case "tr"->tr=v;case "vimax"->vimax=v;case "vimin"->vimin=v;case "kpr"->kpr=v;case "kir"->kir=v;
        case "kdr"->kdr=v;case "tdr"->tdr=v;case "vpidmax"->vpidmax=v;case "vpidmin"->vpidmin=v;
        case "tc"->tc=v;case "tb"->tb=v;case "ka"->ka=v;case "ta"->ta=v;case "vrmax"->vrmax=v;case "vrmin"->vrmin=v;
        case "km"->km=v;case "tm"->tm=v;case "vmmax"->vmmax=v;case "vmmin"->vmmin=v;case "kg"->kg=v;case "vgmax"->vgmax=v;
        case "kp"->kp=v;case "ki"->ki=v;case "xl"->xl=v;case "thetap"->thetaP=v;case "kc"->kc=v;case "vbmax"->vbmax=v;
        default->{}}}
    public int getOel(){return oel;}public void setOel(int v){oel=v;}public int getUel(){return uel;}public void setUel(int v){uel=v;}
    public int getScl(){return scl;}public void setScl(int v){scl=v;}public int getSw1(){return sw1;}public void setSw1(int v){sw1=v;}
    public double getTr(){return tr;}public void setTr(double v){tr=v;}public double getVimax(){return vimax;}public void setVimax(double v){vimax=v;}
    public double getVimin(){return vimin;}public void setVimin(double v){vimin=v;}public double getKpr(){return kpr;}public void setKpr(double v){kpr=v;}
    public double getKir(){return kir;}public void setKir(double v){kir=v;}public double getKdr(){return kdr;}public void setKdr(double v){kdr=v;}
    public double getTdr(){return tdr;}public void setTdr(double v){tdr=v;}public double getVpidmax(){return vpidmax;}public void setVpidmax(double v){vpidmax=v;}
    public double getVpidmin(){return vpidmin;}public void setVpidmin(double v){vpidmin=v;}public double getTc(){return tc;}public void setTc(double v){tc=v;}
    public double getTb(){return tb;}public void setTb(double v){tb=v;}public double getKa(){return ka;}public void setKa(double v){ka=v;}
    public double getTa(){return ta;}public void setTa(double v){ta=v;}public double getVrmax(){return vrmax;}public void setVrmax(double v){vrmax=v;}
    public double getVrmin(){return vrmin;}public void setVrmin(double v){vrmin=v;}public double getKm(){return km;}public void setKm(double v){km=v;}
    public double getTm(){return tm;}public void setTm(double v){tm=v;}public double getVmmax(){return vmmax;}public void setVmmax(double v){vmmax=v;}
    public double getVmmin(){return vmmin;}public void setVmmin(double v){vmmin=v;}public double getKg(){return kg;}public void setKg(double v){kg=v;}
    public double getVgmax(){return vgmax;}public void setVgmax(double v){vgmax=v;}public double getKp(){return kp;}public void setKp(double v){kp=v;}
    public double getKi(){return ki;}public void setKi(double v){ki=v;}public double getXl(){return xl;}public void setXl(double v){xl=v;}
    public double getThetaP(){return thetaP;}public void setThetaP(double v){thetaP=v;}public double getKc(){return kc;}public void setKc(double v){kc=v;}
    public double getVbmax(){return vbmax;}public void setVbmax(double v){vbmax=v;}
}
