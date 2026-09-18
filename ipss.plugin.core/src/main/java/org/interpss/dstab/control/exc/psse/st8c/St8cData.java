package org.interpss.dstab.control.exc.psse.st8c;

import org.interpss.dstab.control.base.BaseControllerData;

/** Native PSS/E IEEE 421.5-2016 ST8C excitation-system parameters. */
public final class St8cData extends BaseControllerData {
    private int oel,uel,scl,sw1=1;
    private double tr,kpr,kir,vpimax,vpimin,kpa,kia,vamax,vamin,ka,ta,vrmax,vrmin,kf,tf;
    private double kc1,kp,ki1,xl,thetaP,vb1max,kc2,ki2,vb2max;

    public St8cData(){setRangeParameters(new String[][]{
            {"tr","0","1000"},{"kpr","0","10000"},{"kir","0","10000"},
            {"vpimax","-10000","10000"},{"vpimin","-10000","10000"},
            {"kpa","0","10000"},{"kia","0","10000"},{"vamax","-10000","10000"},{"vamin","-10000","10000"},
            {"ka","0","10000"},{"ta","0","1000"},{"vrmax","-10000","10000"},{"vrmin","-10000","10000"},
            {"kf","0","10000"},{"tf","0","1000"},{"kc1","0","10000"},{"kp","-10000","10000"},
            {"ki1","-10000","10000"},{"xl","-10000","10000"},{"thetap","-360","360"},
            {"vb1max","0","10000"},{"kc2","0","10000"},{"ki2","-10000","10000"},{"vb2max","0","10000"}});}

    @Override public void setValue(String name,int value){switch(name.toLowerCase()){
        case "oel"->oel=value;case "uel"->uel=value;case "scl"->scl=value;case "sw1"->sw1=value;default->setValue(name,(double)value);}}
    @Override public void setValue(String name,double value){switch(name.toLowerCase()){
        case "oel"->oel=(int)value;case "uel"->uel=(int)value;case "scl"->scl=(int)value;case "sw1"->sw1=(int)value;
        case "tr"->tr=value;case "kpr"->kpr=value;case "kir"->kir=value;case "vpimax"->vpimax=value;case "vpimin"->vpimin=value;
        case "kpa"->kpa=value;case "kia"->kia=value;case "vamax"->vamax=value;case "vamin"->vamin=value;
        case "ka"->ka=value;case "ta"->ta=value;case "vrmax"->vrmax=value;case "vrmin"->vrmin=value;
        case "kf"->kf=value;case "tf"->tf=value;case "kc1"->kc1=value;case "kp"->kp=value;case "ki1"->ki1=value;
        case "xl"->xl=value;case "thetap"->thetaP=value;case "vb1max"->vb1max=value;case "kc2"->kc2=value;
        case "ki2"->ki2=value;case "vb2max"->vb2max=value;default->{}}}

    public int getOel(){return oel;}public void setOel(int v){oel=v;}public int getUel(){return uel;}public void setUel(int v){uel=v;}
    public int getScl(){return scl;}public void setScl(int v){scl=v;}public int getSw1(){return sw1;}public void setSw1(int v){sw1=v;}
    public double getTr(){return tr;}public void setTr(double v){tr=v;}public double getKpr(){return kpr;}public void setKpr(double v){kpr=v;}
    public double getKir(){return kir;}public void setKir(double v){kir=v;}public double getVpimax(){return vpimax;}public void setVpimax(double v){vpimax=v;}
    public double getVpimin(){return vpimin;}public void setVpimin(double v){vpimin=v;}public double getKpa(){return kpa;}public void setKpa(double v){kpa=v;}
    public double getKia(){return kia;}public void setKia(double v){kia=v;}public double getVamax(){return vamax;}public void setVamax(double v){vamax=v;}
    public double getVamin(){return vamin;}public void setVamin(double v){vamin=v;}public double getKa(){return ka;}public void setKa(double v){ka=v;}
    public double getTa(){return ta;}public void setTa(double v){ta=v;}public double getVrmax(){return vrmax;}public void setVrmax(double v){vrmax=v;}
    public double getVrmin(){return vrmin;}public void setVrmin(double v){vrmin=v;}public double getKf(){return kf;}public void setKf(double v){kf=v;}
    public double getTf(){return tf;}public void setTf(double v){tf=v;}public double getKc1(){return kc1;}public void setKc1(double v){kc1=v;}
    public double getKp(){return kp;}public void setKp(double v){kp=v;}public double getKi1(){return ki1;}public void setKi1(double v){ki1=v;}
    public double getXl(){return xl;}public void setXl(double v){xl=v;}public double getThetaP(){return thetaP;}public void setThetaP(double v){thetaP=v;}
    public double getVb1max(){return vb1max;}public void setVb1max(double v){vb1max=v;}public double getKc2(){return kc2;}public void setKc2(double v){kc2=v;}
    public double getKi2(){return ki2;}public void setKi2(double v){ki2=v;}public double getVb2max(){return vb2max;}public void setVb2max(double v){vb2max=v;}
}
