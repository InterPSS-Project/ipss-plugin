package org.interpss.dstab.control.exc.psse.exst3;

import org.interpss.dstab.control.base.BaseControllerData;

/** Parameters in the native eighteen-CON PSS/E EXST3 record. */
public final class Exst3Data extends BaseControllerData {
    private double tr, vimax, vimin, kj, tc, tb, ka, ta, vrmax, vrmin;
    private double kg, kp, ki, efdmax, kc, xl, vgmax, thetaP;

    public Exst3Data() {
        setRangeParameters(new String[][] {
                {"tr", "0", "1000"}, {"vimax", "-10000", "10000"},
                {"vimin", "-10000", "10000"}, {"kj", "-10000", "10000"},
                {"tc", "0", "1000"}, {"tb", "0", "1000"},
                {"ka", "-10000", "10000"}, {"ta", "0", "1000"},
                {"vrmax", "-10000", "10000"}, {"vrmin", "-10000", "10000"},
                {"kg", "-10000", "10000"}, {"kp", "-10000", "10000"},
                {"ki", "-10000", "10000"}, {"efdmax", "-10000", "10000"},
                {"kc", "-10000", "10000"}, {"xl", "-10000", "10000"},
                {"vgmax", "-10000", "10000"}, {"thetaP", "-360", "360"}
        });
    }

    @Override public void setValue(String name, int value) { setValue(name, (double) value); }
    @Override public void setValue(String name, double value) {
        switch (name.toLowerCase()) {
            case "tr" -> tr=value; case "vimax" -> vimax=value; case "vimin" -> vimin=value;
            case "kj" -> kj=value; case "tc" -> tc=value; case "tb" -> tb=value;
            case "ka" -> ka=value; case "ta" -> ta=value; case "vrmax" -> vrmax=value;
            case "vrmin" -> vrmin=value; case "kg" -> kg=value; case "kp" -> kp=value;
            case "ki" -> ki=value; case "efdmax" -> efdmax=value; case "kc" -> kc=value;
            case "xl" -> xl=value; case "vgmax" -> vgmax=value; case "thetap" -> thetaP=value;
            default -> { }
        }
    }

    public double getTr(){return tr;} public void setTr(double v){tr=v;}
    public double getVimax(){return vimax;} public void setVimax(double v){vimax=v;}
    public double getVimin(){return vimin;} public void setVimin(double v){vimin=v;}
    public double getKj(){return kj;} public void setKj(double v){kj=v;}
    public double getTc(){return tc;} public void setTc(double v){tc=v;}
    public double getTb(){return tb;} public void setTb(double v){tb=v;}
    public double getKa(){return ka;} public void setKa(double v){ka=v;}
    public double getTa(){return ta;} public void setTa(double v){ta=v;}
    public double getVrmax(){return vrmax;} public void setVrmax(double v){vrmax=v;}
    public double getVrmin(){return vrmin;} public void setVrmin(double v){vrmin=v;}
    public double getKg(){return kg;} public void setKg(double v){kg=v;}
    public double getKp(){return kp;} public void setKp(double v){kp=v;}
    public double getKi(){return ki;} public void setKi(double v){ki=v;}
    public double getEfdmax(){return efdmax;} public void setEfdmax(double v){efdmax=v;}
    public double getKc(){return kc;} public void setKc(double v){kc=v;}
    public double getXl(){return xl;} public void setXl(double v){xl=v;}
    public double getVgmax(){return vgmax;} public void setVgmax(double v){vgmax=v;}
    public double getThetaP(){return thetaP;} public void setThetaP(double v){thetaP=v;}
}
