package org.interpss.dstab.control.exc.psse.st4c;

import org.interpss.dstab.control.base.BaseControllerData;

/** Native PSS/E IEEE 421.5-2016 ST4C excitation-system parameters. */
public final class St4cData extends BaseControllerData {
    private int vos, oel, uel, scl, sw1;
    private double tr, kpr, kir, vrmax, vrmin, kpm, kim, vmmax, vmmin;
    private double ta, vamax, vamin, kg, tg, vgmax, kp, ki, xl, thetaP, kc, vbmax;

    public St4cData() {
        setRangeParameters(new String[][] {
                {"tr","0","1000"},{"kpr","-10000","10000"},{"kir","-10000","10000"},
                {"vrmax","-10000","10000"},{"vrmin","-10000","10000"},
                {"kpm","-10000","10000"},{"kim","-10000","10000"},
                {"vmmax","-10000","10000"},{"vmmin","-10000","10000"},
                {"ta","0","1000"},{"vamax","-10000","10000"},{"vamin","-10000","10000"},
                {"kg","-10000","10000"},{"tg","0","1000"},{"vgmax","-10000","10000"},
                {"kp","-10000","10000"},{"ki","-10000","10000"},{"xl","-10000","10000"},
                {"thetap","-360","360"},{"kc","-10000","10000"},{"vbmax","-10000","10000"}
        });
    }

    @Override public void setValue(String name, int value) { switch (name.toLowerCase()) {
        case "vos" -> vos=value; case "oel" -> oel=value; case "uel" -> uel=value;
        case "scl" -> scl=value; case "sw1" -> sw1=value; default -> setValue(name,(double)value);
    }}
    @Override public void setValue(String name, double value) { switch (name.toLowerCase()) {
        case "vos" -> vos=(int)value; case "oel" -> oel=(int)value; case "uel" -> uel=(int)value;
        case "scl" -> scl=(int)value; case "sw1" -> sw1=(int)value;
        case "tr" -> tr=value; case "kpr" -> kpr=value; case "kir" -> kir=value;
        case "vrmax" -> vrmax=value; case "vrmin" -> vrmin=value; case "kpm" -> kpm=value;
        case "kim" -> kim=value; case "vmmax" -> vmmax=value; case "vmmin" -> vmmin=value;
        case "ta" -> ta=value; case "vamax" -> vamax=value; case "vamin" -> vamin=value;
        case "kg" -> kg=value; case "tg" -> tg=value; case "vgmax" -> vgmax=value;
        case "kp" -> kp=value; case "ki" -> ki=value; case "xl" -> xl=value;
        case "thetap" -> thetaP=value; case "kc" -> kc=value; case "vbmax" -> vbmax=value;
        default -> { }
    }}

    public int getVos(){return vos;} public void setVos(int v){vos=v;}
    public int getOel(){return oel;} public void setOel(int v){oel=v;}
    public int getUel(){return uel;} public void setUel(int v){uel=v;}
    public int getScl(){return scl;} public void setScl(int v){scl=v;}
    public int getSw1(){return sw1;} public void setSw1(int v){sw1=v;}
    public double getTr(){return tr;} public void setTr(double v){tr=v;}
    public double getKpr(){return kpr;} public void setKpr(double v){kpr=v;}
    public double getKir(){return kir;} public void setKir(double v){kir=v;}
    public double getVrmax(){return vrmax;} public void setVrmax(double v){vrmax=v;}
    public double getVrmin(){return vrmin;} public void setVrmin(double v){vrmin=v;}
    public double getKpm(){return kpm;} public void setKpm(double v){kpm=v;}
    public double getKim(){return kim;} public void setKim(double v){kim=v;}
    public double getVmmax(){return vmmax;} public void setVmmax(double v){vmmax=v;}
    public double getVmmin(){return vmmin;} public void setVmmin(double v){vmmin=v;}
    public double getTa(){return ta;} public void setTa(double v){ta=v;}
    public double getVamax(){return vamax;} public void setVamax(double v){vamax=v;}
    public double getVamin(){return vamin;} public void setVamin(double v){vamin=v;}
    public double getKg(){return kg;} public void setKg(double v){kg=v;}
    public double getTg(){return tg;} public void setTg(double v){tg=v;}
    public double getVgmax(){return vgmax;} public void setVgmax(double v){vgmax=v;}
    public double getKp(){return kp;} public void setKp(double v){kp=v;}
    public double getKi(){return ki;} public void setKi(double v){ki=v;}
    public double getXl(){return xl;} public void setXl(double v){xl=v;}
    public double getThetaP(){return thetaP;} public void setThetaP(double v){thetaP=v;}
    public double getKc(){return kc;} public void setKc(double v){kc=v;}
    public double getVbmax(){return vbmax;} public void setVbmax(double v){vbmax=v;}
}
