package org.interpss.dstab.control.exc.psse.esac6a;

import org.interpss.dstab.control.base.BaseControllerData;

/** PSS/E ESAC6A / IEEE AC6A excitation-system parameters. */
public final class Esac6aData extends BaseControllerData {
    private double tr, ka, ta, tk, tb, tc, vamax, vamin, vrmax, vrmin;
    private double te, vfelim, kh, vhmax, th, tj, kc, kd, ke;
    private double e1, se1, e2, se2, spdmlt;

    public Esac6aData() {
        setRangeParameters(new String[][]{{"tr", "0", "1000"}, {"te", "0", "1000"}});
    }

    @Override public void setValue(String name, int value) { setValue(name, (double)value); }
    @Override public void setValue(String name, double value) {
        switch (name.toLowerCase()) {
            case "tr" -> tr=value; case "ka" -> ka=value; case "ta" -> ta=value;
            case "tk" -> tk=value; case "tb" -> tb=value; case "tc" -> tc=value;
            case "vamax" -> vamax=value; case "vamin" -> vamin=value;
            case "vrmax" -> vrmax=value; case "vrmin" -> vrmin=value;
            case "te" -> te=value; case "vfelim" -> vfelim=value;
            case "kh" -> kh=value; case "vhmax" -> vhmax=value;
            case "th" -> th=value; case "tj" -> tj=value;
            case "kc" -> kc=value; case "kd" -> kd=value; case "ke" -> ke=value;
            case "e1" -> e1=value; case "se1" -> se1=value;
            case "e2" -> e2=value; case "se2" -> se2=value;
            case "spdmlt" -> spdmlt=value; default -> { }
        }
    }

    public double getTr(){return tr;} public void setTr(double v){tr=v;}
    public double getKa(){return ka;} public void setKa(double v){ka=v;}
    public double getTa(){return ta;} public void setTa(double v){ta=v;}
    public double getTk(){return tk;} public void setTk(double v){tk=v;}
    public double getTb(){return tb;} public void setTb(double v){tb=v;}
    public double getTc(){return tc;} public void setTc(double v){tc=v;}
    public double getVamax(){return vamax;} public void setVamax(double v){vamax=v;}
    public double getVamin(){return vamin;} public void setVamin(double v){vamin=v;}
    public double getVrmax(){return vrmax;} public void setVrmax(double v){vrmax=v;}
    public double getVrmin(){return vrmin;} public void setVrmin(double v){vrmin=v;}
    public double getTe(){return te;} public void setTe(double v){te=v;}
    public double getVfelim(){return vfelim;} public void setVfelim(double v){vfelim=v;}
    public double getKh(){return kh;} public void setKh(double v){kh=v;}
    public double getVhmax(){return vhmax;} public void setVhmax(double v){vhmax=v;}
    public double getTh(){return th;} public void setTh(double v){th=v;}
    public double getTj(){return tj;} public void setTj(double v){tj=v;}
    public double getKc(){return kc;} public void setKc(double v){kc=v;}
    public double getKd(){return kd;} public void setKd(double v){kd=v;}
    public double getKe(){return ke;} public void setKe(double v){ke=v;}
    public double getE1(){return e1;} public void setE1(double v){e1=v;}
    public double getSe1(){return se1;} public void setSe1(double v){se1=v;}
    public double getE2(){return e2;} public void setE2(double v){e2=v;}
    public double getSe2(){return se2;} public void setSe2(double v){se2=v;}
    public double getSpdmlt(){return spdmlt;} public void setSpdmlt(double v){spdmlt=v;}
}
