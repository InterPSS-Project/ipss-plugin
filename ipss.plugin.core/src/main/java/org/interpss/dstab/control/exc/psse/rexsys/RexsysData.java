package org.interpss.dstab.control.exc.psse.rexsys;

import org.interpss.dstab.control.base.BaseControllerData;

/** Parameters for the 31-value PSS/E REXSYS excitation-system record. */
public final class RexsysData extends BaseControllerData {
    private double tr, kvp, kvi, vimax, ta, tb1, tc1, tb2, tc2;
    private double vrmax, vrmin, kf, tf, tf1, tf2;
    private int fbf;
    private double kip, kii, tp, vfmax, vfmin, kh, ke, te, kc, kd;
    private double e1, se1, e2, se2;
    private int flimf;

    public RexsysData() {
        setRangeParameters(new String[][]{{"tr","0","1000"},{"te","0","1000"}});
    }

    @Override public void setValue(String name, int value) {
        switch (name.toLowerCase()) {
            case "fbf" -> fbf=value; case "flimf" -> flimf=value;
            default -> setValue(name,(double)value);
        }
    }
    @Override public void setValue(String name, double value) {
        switch (name.toLowerCase()) {
            case "tr"->tr=value; case "kvp"->kvp=value; case "kvi"->kvi=value;
            case "vimax"->vimax=value; case "ta"->ta=value; case "tb1"->tb1=value;
            case "tc1"->tc1=value; case "tb2"->tb2=value; case "tc2"->tc2=value;
            case "vrmax"->vrmax=value; case "vrmin"->vrmin=value; case "kf"->kf=value;
            case "tf"->tf=value; case "tf1"->tf1=value; case "tf2"->tf2=value;
            case "fbf"->fbf=(int)value; case "kip"->kip=value; case "kii"->kii=value;
            case "tp"->tp=value; case "vfmax"->vfmax=value; case "vfmin"->vfmin=value;
            case "kh"->kh=value; case "ke"->ke=value; case "te"->te=value;
            case "kc"->kc=value; case "kd"->kd=value; case "e1"->e1=value;
            case "se1"->se1=value; case "e2"->e2=value; case "se2"->se2=value;
            case "flimf"->flimf=(int)value; default -> { }
        }
    }

    public double getTr(){return tr;} public void setTr(double v){tr=v;}
    public double getKvp(){return kvp;} public void setKvp(double v){kvp=v;}
    public double getKvi(){return kvi;} public void setKvi(double v){kvi=v;}
    public double getVimax(){return vimax;} public void setVimax(double v){vimax=v;}
    public double getTa(){return ta;} public void setTa(double v){ta=v;}
    public double getTb1(){return tb1;} public void setTb1(double v){tb1=v;}
    public double getTc1(){return tc1;} public void setTc1(double v){tc1=v;}
    public double getTb2(){return tb2;} public void setTb2(double v){tb2=v;}
    public double getTc2(){return tc2;} public void setTc2(double v){tc2=v;}
    public double getVrmax(){return vrmax;} public void setVrmax(double v){vrmax=v;}
    public double getVrmin(){return vrmin;} public void setVrmin(double v){vrmin=v;}
    public double getKf(){return kf;} public void setKf(double v){kf=v;}
    public double getTf(){return tf;} public void setTf(double v){tf=v;}
    public double getTf1(){return tf1;} public void setTf1(double v){tf1=v;}
    public double getTf2(){return tf2;} public void setTf2(double v){tf2=v;}
    public int getFbf(){return fbf;} public void setFbf(int v){fbf=v;}
    public double getKip(){return kip;} public void setKip(double v){kip=v;}
    public double getKii(){return kii;} public void setKii(double v){kii=v;}
    public double getTp(){return tp;} public void setTp(double v){tp=v;}
    public double getVfmax(){return vfmax;} public void setVfmax(double v){vfmax=v;}
    public double getVfmin(){return vfmin;} public void setVfmin(double v){vfmin=v;}
    public double getKh(){return kh;} public void setKh(double v){kh=v;}
    public double getKe(){return ke;} public void setKe(double v){ke=v;}
    public double getTe(){return te;} public void setTe(double v){te=v;}
    public double getKc(){return kc;} public void setKc(double v){kc=v;}
    public double getKd(){return kd;} public void setKd(double v){kd=v;}
    public double getE1(){return e1;} public void setE1(double v){e1=v;}
    public double getSe1(){return se1;} public void setSe1(double v){se1=v;}
    public double getE2(){return e2;} public void setE2(double v){e2=v;}
    public double getSe2(){return se2;} public void setSe2(double v){se2=v;}
    public int getFlimf(){return flimf;} public void setFlimf(int v){flimf=v;}
}
