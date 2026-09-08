package org.interpss.dstab.control.exc.psse.esac3a;

import org.interpss.dstab.control.base.BaseControllerData;

/** Native PSS/E ESAC3A parameters in DYR order. */
public final class Esac3aData extends BaseControllerData {
    private double tr,tb,tc,ka,ta,vamax,vamin,te,vemin,kr,kf,tf,kn,efdn;
    private double kc,kd,ke,vfemax,e1,se1,e2,se2,spdmlt;

    public Esac3aData() {
        setRangeParameters(new String[][] {
                {"tr","0","1000"},{"tb","0","1000"},{"tc","-1000","1000"},
                {"ka","0","10000"},{"ta","0","1000"},{"vamax","-10000","10000"},
                {"vamin","-10000","10000"},{"te","0","1000"},{"vemin","-10000","10000"},
                {"kr","0","10000"},{"kf","-10000","10000"},{"tf","0","1000"},
                {"kn","-10000","10000"},{"efdn","-10000","10000"},{"kc","0","10000"},
                {"kd","0","10000"},{"ke","-10000","10000"},{"vfemax","-10000","10000"},
                {"e1","0","10000"},{"se1","0","10000"},{"e2","0","10000"},
                {"se2","0","10000"},{"spdmlt","0","1"}
        });
    }

    @Override public void setValue(String name,int value){setValue(name,(double)value);}
    @Override public void setValue(String name,double value){
        switch(name.toLowerCase()){
            case "tr"->tr=value;case "tb"->tb=value;case "tc"->tc=value;case "ka"->ka=value;
            case "ta"->ta=value;case "vamax"->vamax=value;case "vamin"->vamin=value;
            case "te"->te=value;case "vemin"->vemin=value;case "kr"->kr=value;
            case "kf"->kf=value;case "tf"->tf=value;case "kn"->kn=value;case "efdn"->efdn=value;
            case "kc"->kc=value;case "kd"->kd=value;case "ke"->ke=value;case "vfemax"->vfemax=value;
            case "e1"->e1=value;case "se1"->se1=value;case "e2"->e2=value;case "se2"->se2=value;
            case "spdmlt"->spdmlt=value;default->{ }
        }
    }

    public double getTr(){return tr;}public void setTr(double v){tr=v;}
    public double getTb(){return tb;}public void setTb(double v){tb=v;}
    public double getTc(){return tc;}public void setTc(double v){tc=v;}
    public double getKa(){return ka;}public void setKa(double v){ka=v;}
    public double getTa(){return ta;}public void setTa(double v){ta=v;}
    public double getVamax(){return vamax;}public void setVamax(double v){vamax=v;}
    public double getVamin(){return vamin;}public void setVamin(double v){vamin=v;}
    public double getTe(){return te;}public void setTe(double v){te=v;}
    public double getVemin(){return vemin;}public void setVemin(double v){vemin=v;}
    public double getKr(){return kr;}public void setKr(double v){kr=v;}
    public double getKf(){return kf;}public void setKf(double v){kf=v;}
    public double getTf(){return tf;}public void setTf(double v){tf=v;}
    public double getKn(){return kn;}public void setKn(double v){kn=v;}
    public double getEfdn(){return efdn;}public void setEfdn(double v){efdn=v;}
    public double getKc(){return kc;}public void setKc(double v){kc=v;}
    public double getKd(){return kd;}public void setKd(double v){kd=v;}
    public double getKe(){return ke;}public void setKe(double v){ke=v;}
    public double getVfemax(){return vfemax;}public void setVfemax(double v){vfemax=v;}
    public double getE1(){return e1;}public void setE1(double v){e1=v;}
    public double getSe1(){return se1;}public void setSe1(double v){se1=v;}
    public double getE2(){return e2;}public void setE2(double v){e2=v;}
    public double getSe2(){return se2;}public void setSe2(double v){se2=v;}
    public double getSpdmlt(){return spdmlt;}public void setSpdmlt(double v){spdmlt=v;}
}
