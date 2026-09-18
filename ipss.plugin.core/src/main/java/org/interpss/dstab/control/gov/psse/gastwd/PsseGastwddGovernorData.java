package org.interpss.dstab.control.gov.psse.gastwd;

import org.interpss.dstab.control.base.BaseControllerData;

/** Parameters in the thirty-four-value PSS/E GASTWDD/GASTWDDU record. */
public class PsseGastwddGovernorData extends BaseControllerData {
    private double kdroop, kp, ki, kd, etd, tcd, trate, t, maxLimit, minLimit;
    private double ecr, k3, a, b, c, tauF, kf, k5, k4, t3, t4, tauT, t5;
    private double af1, bf1, af2, bf2, cf2, tr, k6, tc, td, dbH, dbL;

    public double getKdroop(){return kdroop;} public void setKdroop(double v){kdroop=v;}
    public double getKp(){return kp;} public void setKp(double v){kp=v;}
    public double getKi(){return ki;} public void setKi(double v){ki=v;}
    public double getKd(){return kd;} public void setKd(double v){kd=v;}
    public double getEtd(){return etd;} public void setEtd(double v){etd=v;}
    public double getTcd(){return tcd;} public void setTcd(double v){tcd=v;}
    public double getTrate(){return trate;} public void setTrate(double v){trate=v;}
    public double getT(){return t;} public void setT(double v){t=v;}
    public double getMaxLimit(){return maxLimit;} public void setMaxLimit(double v){maxLimit=v;}
    public double getMinLimit(){return minLimit;} public void setMinLimit(double v){minLimit=v;}
    public double getEcr(){return ecr;} public void setEcr(double v){ecr=v;}
    public double getK3(){return k3;} public void setK3(double v){k3=v;}
    public double getA(){return a;} public void setA(double v){a=v;}
    public double getB(){return b;} public void setB(double v){b=v;}
    public double getC(){return c;} public void setC(double v){c=v;}
    public double getTauF(){return tauF;} public void setTauF(double v){tauF=v;}
    public double getKf(){return kf;} public void setKf(double v){kf=v;}
    public double getK5(){return k5;} public void setK5(double v){k5=v;}
    public double getK4(){return k4;} public void setK4(double v){k4=v;}
    public double getT3(){return t3;} public void setT3(double v){t3=v;}
    public double getT4(){return t4;} public void setT4(double v){t4=v;}
    public double getTauT(){return tauT;} public void setTauT(double v){tauT=v;}
    public double getT5(){return t5;} public void setT5(double v){t5=v;}
    public double getAf1(){return af1;} public void setAf1(double v){af1=v;}
    public double getBf1(){return bf1;} public void setBf1(double v){bf1=v;}
    public double getAf2(){return af2;} public void setAf2(double v){af2=v;}
    public double getBf2(){return bf2;} public void setBf2(double v){bf2=v;}
    public double getCf2(){return cf2;} public void setCf2(double v){cf2=v;}
    public double getTr(){return tr;} public void setTr(double v){tr=v;}
    public double getK6(){return k6;} public void setK6(double v){k6=v;}
    public double getTc(){return tc;} public void setTc(double v){tc=v;}
    public double getTd(){return td;} public void setTd(double v){td=v;}
    public double getDbH(){return dbH;} public void setDbH(double v){dbH=v;}
    public double getDbL(){return dbL;} public void setDbL(double v){dbL=v;}

    @Override public void setValue(String name, int value) { setValue(name, (double)value); }
    @Override public void setValue(String name, double value) {
        switch(name.toLowerCase()) {
            case "kdroop"->kdroop=value; case "kp"->kp=value; case "ki"->ki=value; case "kd"->kd=value;
            case "etd"->etd=value; case "tcd"->tcd=value; case "trate"->trate=value; case "t"->t=value;
            case "maxlimit"->maxLimit=value; case "minlimit"->minLimit=value; case "ecr"->ecr=value;
            case "k3"->k3=value; case "a"->a=value; case "b"->b=value; case "c"->c=value;
            case "tauf"->tauF=value; case "kf"->kf=value; case "k5"->k5=value; case "k4"->k4=value;
            case "t3"->t3=value; case "t4"->t4=value; case "taut"->tauT=value; case "t5"->t5=value;
            case "af1"->af1=value; case "bf1"->bf1=value; case "af2"->af2=value; case "bf2"->bf2=value;
            case "cf2"->cf2=value; case "tr"->tr=value; case "k6"->k6=value; case "tc"->tc=value;
            case "td"->td=value; case "dbh"->dbH=value; case "dbl"->dbL=value; default->{ }
        }
    }
}
