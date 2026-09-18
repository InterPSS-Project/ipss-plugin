package org.interpss.dstab.control.exc.psse.ac4c;

import org.interpss.dstab.control.base.BaseControllerData;

/** IEEE 421.5-2016 / PSS/E AC4C excitation-system parameters. */
public final class Ac4cData extends BaseControllerData {
    // PSS/E carries OEL and UEL; SCL is retained only for the typed IEEE API.
    private int oelLocation, uelLocation, sclLocation;
    private double tr,vimax,vimin,tc,tb,ka,ta,vrmax,vrmin,kc;

    public Ac4cData() {
        setRangeParameters(new String[][]{
                {"tr","0","1000"},{"vimax","-10000","10000"},
                {"vimin","-10000","10000"},{"tc","-1000","1000"},
                {"tb","0","1000"},{"ka","0","10000"},
                {"ta","0","1000"},{"vrmax","-10000","10000"},
                {"vrmin","-10000","10000"},{"kc","0","10000"}
        });
    }

    @Override public void setValue(String name,int value) {
        switch(name.toLowerCase()) {
            case "oellocation" -> oelLocation=value;
            case "uellocation" -> uelLocation=value;
            case "scllocation" -> sclLocation=value;
            default -> setValue(name,(double)value);
        }
    }
    @Override public void setValue(String name,double value) {
        switch(name.toLowerCase()) {
            case "tr" -> tr=value; case "vimax" -> vimax=value;
            case "vimin" -> vimin=value; case "tc" -> tc=value;
            case "tb" -> tb=value; case "ka" -> ka=value;
            case "ta" -> ta=value; case "vrmax" -> vrmax=value;
            case "vrmin" -> vrmin=value; case "kc" -> kc=value;
            default -> { }
        }
    }

    public int getOelLocation(){return oelLocation;} public void setOelLocation(int v){oelLocation=v;}
    public int getUelLocation(){return uelLocation;} public void setUelLocation(int v){uelLocation=v;}
    public int getSclLocation(){return sclLocation;} public void setSclLocation(int v){sclLocation=v;}
    public double getTr(){return tr;} public void setTr(double v){tr=v;}
    public double getVimax(){return vimax;} public void setVimax(double v){vimax=v;}
    public double getVimin(){return vimin;} public void setVimin(double v){vimin=v;}
    public double getTc(){return tc;} public void setTc(double v){tc=v;}
    public double getTb(){return tb;} public void setTb(double v){tb=v;}
    public double getKa(){return ka;} public void setKa(double v){ka=v;}
    public double getTa(){return ta;} public void setTa(double v){ta=v;}
    public double getVrmax(){return vrmax;} public void setVrmax(double v){vrmax=v;}
    public double getVrmin(){return vrmin;} public void setVrmin(double v){vrmin=v;}
    public double getKc(){return kc;} public void setKc(double v){kc=v;}
}
