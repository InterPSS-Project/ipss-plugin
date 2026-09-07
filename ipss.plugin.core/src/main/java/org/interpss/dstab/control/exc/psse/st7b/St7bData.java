package org.interpss.dstab.control.exc.psse.st7b;

import org.interpss.dstab.control.base.BaseControllerData;

/** IEEE ST7B / PSLF ESST7B excitation-system parameters. */
public final class St7bData extends BaseControllerData {
    private int oel,uel;
    private double tr,tg,tf,vmax,vmin,kpa,vrmax,vrmin,kh,kl,tc,tb,kia,tia,ts;
    public St7bData(){setRangeParameters(new String[][]{
            {"tr","0","1000"},{"tg","0","1000"},{"tf","0","1000"},
            {"vmax","-10000","10000"},{"vmin","-10000","10000"},{"kpa","0","10000"},
            {"vrmax","-10000","10000"},{"vrmin","-10000","10000"},{"kh","0","10000"},
            {"kl","0","10000"},{"tc","0","1000"},{"tb","0","1000"},
            {"kia","0","10000"},{"tia","0","1000"},{"ts","0","1000"}});}
    @Override public void setValue(String name,int value){if("oel".equalsIgnoreCase(name))oel=value;else if("uel".equalsIgnoreCase(name))uel=value;else setValue(name,(double)value);}
    @Override public void setValue(String name,double value){switch(name.toLowerCase()){
        case "oel"->oel=(int)value;case "uel"->uel=(int)value;case "tr"->tr=value;case "tg"->tg=value;
        case "tf"->tf=value;case "vmax"->vmax=value;case "vmin"->vmin=value;case "kpa"->kpa=value;
        case "vrmax"->vrmax=value;case "vrmin"->vrmin=value;case "kh"->kh=value;case "kl"->kl=value;
        case "tc"->tc=value;case "tb"->tb=value;case "kia"->kia=value;case "tia"->tia=value;case "ts"->ts=value;default->{}}}
    public int getOel(){return oel;}public void setOel(int v){oel=v;}public int getUel(){return uel;}public void setUel(int v){uel=v;}
    public double getTr(){return tr;}public void setTr(double v){tr=v;}public double getTg(){return tg;}public void setTg(double v){tg=v;}
    public double getTf(){return tf;}public void setTf(double v){tf=v;}public double getVmax(){return vmax;}public void setVmax(double v){vmax=v;}
    public double getVmin(){return vmin;}public void setVmin(double v){vmin=v;}public double getKpa(){return kpa;}public void setKpa(double v){kpa=v;}
    public double getVrmax(){return vrmax;}public void setVrmax(double v){vrmax=v;}public double getVrmin(){return vrmin;}public void setVrmin(double v){vrmin=v;}
    public double getKh(){return kh;}public void setKh(double v){kh=v;}public double getKl(){return kl;}public void setKl(double v){kl=v;}
    public double getTc(){return tc;}public void setTc(double v){tc=v;}public double getTb(){return tb;}public void setTb(double v){tb=v;}
    public double getKia(){return kia;}public void setKia(double v){kia=v;}public double getTia(){return tia;}public void setTia(double v){tia=v;}
    public double getTs(){return ts;}public void setTs(double v){ts=v;}
}
