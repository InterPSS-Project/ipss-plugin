package org.interpss.dstab.control.exc.psse.st5b;

import org.interpss.dstab.control.base.BaseControllerData;

/** IEEE 421.5-2005 ST5B / PSLF ESST5B excitation-system parameters. */
public class St5bData extends BaseControllerData {
    private double tr, tc1, tb1, tc2, tb2, kr, vrmax, vrmin, t1, kc;
    private double tuc1, tub1, tuc2, tub2, toc1, tob1, toc2, tob2;

    public St5bData() {
        setRangeParameters(new String[][] {
                {"tr","0","1000"},{"tc1","0","1000"},{"tb1","0","1000"},
                {"tc2","0","1000"},{"tb2","0","1000"},{"kr","0","10000"},
                {"vrmax","-10000","10000"},{"vrmin","-10000","10000"},
                {"t1","0","1000"},{"kc","-10000","10000"},
                {"tuc1","0","1000"},{"tub1","0","1000"},
                {"tuc2","0","1000"},{"tub2","0","1000"},
                {"toc1","0","1000"},{"tob1","0","1000"},
                {"toc2","0","1000"},{"tob2","0","1000"}
        });
    }

    @Override public void setValue(String name, int value){setValue(name,(double)value);}
    @Override public void setValue(String name,double value){switch(name.toLowerCase()){
        case "tr"->tr=value;case "tc1"->tc1=value;case "tb1"->tb1=value;
        case "tc2"->tc2=value;case "tb2"->tb2=value;case "kr"->kr=value;
        case "vrmax"->vrmax=value;case "vrmin"->vrmin=value;case "t1"->t1=value;
        case "kc"->kc=value;case "tuc1"->tuc1=value;case "tub1"->tub1=value;
        case "tuc2"->tuc2=value;case "tub2"->tub2=value;case "toc1"->toc1=value;
        case "tob1"->tob1=value;case "toc2"->toc2=value;case "tob2"->tob2=value;
        default->{}}
    }
    public double getTr(){return tr;}public void setTr(double v){tr=v;}
    public double getTc1(){return tc1;}public void setTc1(double v){tc1=v;}
    public double getTb1(){return tb1;}public void setTb1(double v){tb1=v;}
    public double getTc2(){return tc2;}public void setTc2(double v){tc2=v;}
    public double getTb2(){return tb2;}public void setTb2(double v){tb2=v;}
    public double getKr(){return kr;}public void setKr(double v){kr=v;}
    public double getVrmax(){return vrmax;}public void setVrmax(double v){vrmax=v;}
    public double getVrmin(){return vrmin;}public void setVrmin(double v){vrmin=v;}
    public double getT1(){return t1;}public void setT1(double v){t1=v;}
    public double getKc(){return kc;}public void setKc(double v){kc=v;}
    public double getTuc1(){return tuc1;}public void setTuc1(double v){tuc1=v;}
    public double getTub1(){return tub1;}public void setTub1(double v){tub1=v;}
    public double getTuc2(){return tuc2;}public void setTuc2(double v){tuc2=v;}
    public double getTub2(){return tub2;}public void setTub2(double v){tub2=v;}
    public double getToc1(){return toc1;}public void setToc1(double v){toc1=v;}
    public double getTob1(){return tob1;}public void setTob1(double v){tob1=v;}
    public double getToc2(){return toc2;}public void setToc2(double v){toc2=v;}
    public double getTob2(){return tob2;}public void setTob2(double v){tob2=v;}
}
