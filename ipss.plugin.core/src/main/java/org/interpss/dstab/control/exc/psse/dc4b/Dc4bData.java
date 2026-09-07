package org.interpss.dstab.control.exc.psse.dc4b;

import org.interpss.dstab.control.base.BaseControllerData;

/** IEEE 421.5 DC4B and PSS/E ESDC4B excitation-system parameters. */
public final class Dc4bData extends BaseControllerData {
    private int oel, uel;
    private double tr, kp, ki, kd, td, vrmax, vrmin, ka, ta, ke, te;
    private double kf, tf, vemin, e1, se1, e2, se2, spdmlt;

    public Dc4bData() {
        setRangeParameters(new String[][] {
                {"tr","0","1000"},{"kp","-10000","10000"},{"ki","-10000","10000"},
                {"kd","-10000","10000"},{"td","0","1000"},{"vrmax","-10000","10000"},
                {"vrmin","-10000","10000"},{"ka","-10000","10000"},{"ta","0","1000"},
                {"ke","-10000","10000"},{"te","0","1000"},{"kf","-10000","10000"},
                {"tf","0","1000"},{"vemin","-10000","10000"},{"e1","0","10000"},
                {"se1","0","10000"},{"e2","0","10000"},{"se2","0","10000"},
                {"spdmlt","0","1"}
        });
    }

    @Override public void setValue(String name, int value) {
        switch (name.toLowerCase()) { case "oel" -> oel=value; case "uel" -> uel=value;
            default -> setValue(name,(double)value); }
    }
    @Override public void setValue(String name, double value) {
        switch (name.toLowerCase()) {
            case "oel" -> oel=(int)value; case "uel" -> uel=(int)value;
            case "tr" -> tr=value; case "kp" -> kp=value; case "ki" -> ki=value;
            case "kd" -> kd=value; case "td" -> td=value; case "vrmax" -> vrmax=value;
            case "vrmin" -> vrmin=value; case "ka" -> ka=value; case "ta" -> ta=value;
            case "ke" -> ke=value; case "te" -> te=value; case "kf" -> kf=value;
            case "tf" -> tf=value; case "vemin" -> vemin=value; case "e1" -> e1=value;
            case "se1" -> se1=value; case "e2" -> e2=value; case "se2" -> se2=value;
            case "spdmlt" -> spdmlt=value; default -> { }
        }
    }

    public int getOel(){return oel;} public void setOel(int v){oel=v;}
    public int getUel(){return uel;} public void setUel(int v){uel=v;}
    public double getTr(){return tr;} public void setTr(double v){tr=v;}
    public double getKp(){return kp;} public void setKp(double v){kp=v;}
    public double getKi(){return ki;} public void setKi(double v){ki=v;}
    public double getKd(){return kd;} public void setKd(double v){kd=v;}
    public double getTd(){return td;} public void setTd(double v){td=v;}
    public double getVrmax(){return vrmax;} public void setVrmax(double v){vrmax=v;}
    public double getVrmin(){return vrmin;} public void setVrmin(double v){vrmin=v;}
    public double getKa(){return ka;} public void setKa(double v){ka=v;}
    public double getTa(){return ta;} public void setTa(double v){ta=v;}
    public double getKe(){return ke;} public void setKe(double v){ke=v;}
    public double getTe(){return te;} public void setTe(double v){te=v;}
    public double getKf(){return kf;} public void setKf(double v){kf=v;}
    public double getTf(){return tf;} public void setTf(double v){tf=v;}
    public double getVemin(){return vemin;} public void setVemin(double v){vemin=v;}
    public double getE1(){return e1;} public void setE1(double v){e1=v;}
    public double getSe1(){return se1;} public void setSe1(double v){se1=v;}
    public double getE2(){return e2;} public void setE2(double v){e2=v;}
    public double getSe2(){return se2;} public void setSe2(double v){se2=v;}
    public double getSpdmlt(){return spdmlt;} public void setSpdmlt(double v){spdmlt=v;}
}
