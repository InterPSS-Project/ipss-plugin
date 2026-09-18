package org.interpss.dstab.control.gov.psse.wesgov;

import org.interpss.dstab.control.base.BaseControllerData;

/** PSS/E WESGOVD Westinghouse digital gas-turbine governor data. */
public class PsseWesgovdGovernorData extends BaseControllerData {
    private double deltaTc;
    private double deltaTp;
    private double droop = 0.05;
    private double kp = 1.0;
    private double ti = 1.0;
    private double t1;
    private double t2;
    private double alim = 1.0;
    private double tpe;
    private double dbH;
    private double dbL;
    private double trate;

    public double getDeltaTc() { return deltaTc; }
    public void setDeltaTc(double value) { deltaTc = value; }
    public double getDeltaTp() { return deltaTp; }
    public void setDeltaTp(double value) { deltaTp = value; }
    public double getDroop() { return droop; }
    public void setDroop(double value) { droop = value; }
    public double getKp() { return kp; }
    public void setKp(double value) { kp = value; }
    public double getTi() { return ti; }
    public void setTi(double value) { ti = value; }
    public double getT1() { return t1; }
    public void setT1(double value) { t1 = value; }
    public double getT2() { return t2; }
    public void setT2(double value) { t2 = value; }
    public double getAlim() { return alim; }
    public void setAlim(double value) { alim = value; }
    public double getTpe() { return tpe; }
    public void setTpe(double value) { tpe = value; }
    public double getDbH() { return dbH; }
    public void setDbH(double value) { dbH = value; }
    public double getDbL() { return dbL; }
    public void setDbL(double value) { dbL = value; }
    public double getTrate() { return trate; }
    public void setTrate(double value) { trate = value; }

    @Override
    public void setValue(String name, int value) { }

    @Override
    public void setValue(String name, double value) {
        if ("deltaTc".equalsIgnoreCase(name)) setDeltaTc(value);
        else if ("deltaTp".equalsIgnoreCase(name)) setDeltaTp(value);
        else if ("droop".equalsIgnoreCase(name)) setDroop(value);
        else if ("kp".equalsIgnoreCase(name)) setKp(value);
        else if ("ti".equalsIgnoreCase(name)) setTi(value);
        else if ("t1".equalsIgnoreCase(name)) setT1(value);
        else if ("t2".equalsIgnoreCase(name)) setT2(value);
        else if ("alim".equalsIgnoreCase(name)) setAlim(value);
        else if ("tpe".equalsIgnoreCase(name)) setTpe(value);
        else if ("dbH".equalsIgnoreCase(name)) setDbH(value);
        else if ("dbL".equalsIgnoreCase(name)) setDbL(value);
        else if ("trate".equalsIgnoreCase(name)) setTrate(value);
    }
}
