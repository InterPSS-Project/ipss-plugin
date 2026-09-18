package org.interpss.dstab.control.exc.psse.ac5c;

import org.interpss.dstab.control.base.BaseControllerData;

/** IEEE 421.5-2016 / PSS/E AC5C excitation-system parameters. */
public class Ac5cData extends BaseControllerData {
    // PSS/E carries OEL and UEL. SCL and Spdmlt remain typed PowerWorld/IEEE inputs.
    private int oelLocation,uelLocation,sclLocation;
    private double tr,ka,ta,vamax,vamin,ke,te,kf,tf1,tf2,tf3;
    private double e1,se1,e2,se2,kc,kd,vfemax,vemin,spdmlt;

    public Ac5cData(){
        setRangeParameters(new String[][]{
                {"tr","0","1000"},{"ka","0","10000"},{"ta","0","1000"},
                {"vamax","-10000","10000"},{"vamin","-10000","10000"},
                {"ke","-10000","10000"},{"te","0","1000"},{"kf","-10000","10000"},
                {"tf1","0","1000"},{"tf2","0","1000"},{"tf3","0","1000"},
                {"e1","0","10000"},{"se1","0","10000"},{"e2","0","10000"},
                {"se2","0","10000"},{"kc","0","10000"},{"kd","0","10000"},
                {"vfemax","-10000","10000"},{"vemin","-10000","10000"},
                {"spdmlt","-10000","10000"}
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
            case "tr" -> tr=value;case "ka" -> ka=value;case "ta" -> ta=value;
            case "vamax" -> vamax=value;case "vamin" -> vamin=value;case "ke" -> ke=value;
            case "te" -> te=value;case "kf" -> kf=value;case "tf1" -> tf1=value;
            case "tf2" -> tf2=value;case "tf3" -> tf3=value;case "e1" -> e1=value;
            case "se1" -> se1=value;case "e2" -> e2=value;case "se2" -> se2=value;
            case "kc" -> kc=value;case "kd" -> kd=value;case "vfemax" -> vfemax=value;
            case "vemin" -> vemin=value;case "spdmlt" -> spdmlt=value;
            default -> { }
        }
    }

    public int getOelLocation(){return oelLocation;}public void setOelLocation(int v){oelLocation=v;}
    public int getUelLocation(){return uelLocation;}public void setUelLocation(int v){uelLocation=v;}
    public int getSclLocation(){return sclLocation;}public void setSclLocation(int v){sclLocation=v;}
    public double getTr(){return tr;}public void setTr(double v){tr=v;}
    public double getKa(){return ka;}public void setKa(double v){ka=v;}
    public double getTa(){return ta;}public void setTa(double v){ta=v;}
    public double getVamax(){return vamax;}public void setVamax(double v){vamax=v;}
    public double getVamin(){return vamin;}public void setVamin(double v){vamin=v;}
    public double getKe(){return ke;}public void setKe(double v){ke=v;}
    public double getTe(){return te;}public void setTe(double v){te=v;}
    public double getKf(){return kf;}public void setKf(double v){kf=v;}
    public double getTf1(){return tf1;}public void setTf1(double v){tf1=v;}
    public double getTf2(){return tf2;}public void setTf2(double v){tf2=v;}
    public double getTf3(){return tf3;}public void setTf3(double v){tf3=v;}
    public double getE1(){return e1;}public void setE1(double v){e1=v;}
    public double getSe1(){return se1;}public void setSe1(double v){se1=v;}
    public double getE2(){return e2;}public void setE2(double v){e2=v;}
    public double getSe2(){return se2;}public void setSe2(double v){se2=v;}
    public double getKc(){return kc;}public void setKc(double v){kc=v;}
    public double getKd(){return kd;}public void setKd(double v){kd=v;}
    public double getVfemax(){return vfemax;}public void setVfemax(double v){vfemax=v;}
    public double getVemin(){return vemin;}public void setVemin(double v){vemin=v;}
    public double getSpdmlt(){return spdmlt;}public void setSpdmlt(double v){spdmlt=v;}
}
