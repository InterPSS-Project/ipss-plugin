package org.interpss.dstab.control.gov.psse.ggov1;

import org.interpss.dstab.control.base.BaseControllerData;

/** Parameters for the PSS/E GGOV1 general-purpose governor/turbine model. */
public class PsseGgov1GovernorData extends BaseControllerData {
    private int rselect = 1;
    private int flag;
    private double r = 0.04;
    private double tpelec = 1.0;
    private double maxerr = 0.05;
    private double minerr = -0.05;
    private double kpgov = 10.0;
    private double kigov = 2.0;
    private double kdgov;
    private double tdgov = 1.0;
    private double vmax = 1.0;
    private double vmin = 0.15;
    private double tact = 0.5;
    private double kturb = 1.5;
    private double wfnl = 0.2;
    private double tb = 0.1;
    private double tc;
    private double teng;
    private double tfload = 3.0;
    private double kpload = 2.0;
    private double kiload = 0.67;
    private double ldref = 1.0;
    private double dm;
    private double ropen = 0.1;
    private double rclose = -0.1;
    private double kimw;
    private double aset = 0.01;
    private double ka = 10.0;
    private double ta = 0.1;
    private double trate;
    private double db;
    private double tsa = 4.0;
    private double tsb = 5.0;
    private double rup = 99.0;
    private double rdown = -99.0;
    private double dbH;
    private double dbL;

    public int getRselect() { return rselect; }
    public void setRselect(int value) { rselect = value; }
    public int getFlag() { return flag; }
    public void setFlag(int value) { flag = value; }
    public double getR() { return r; }
    public void setR(double value) { r = value; }
    public double getTpelec() { return tpelec; }
    public void setTpelec(double value) { tpelec = value; }
    public double getMaxerr() { return maxerr; }
    public void setMaxerr(double value) { maxerr = value; }
    public double getMinerr() { return minerr; }
    public void setMinerr(double value) { minerr = value; }
    public double getKpgov() { return kpgov; }
    public void setKpgov(double value) { kpgov = value; }
    public double getKigov() { return kigov; }
    public void setKigov(double value) { kigov = value; }
    public double getKdgov() { return kdgov; }
    public void setKdgov(double value) { kdgov = value; }
    public double getTdgov() { return tdgov; }
    public void setTdgov(double value) { tdgov = value; }
    public double getVmax() { return vmax; }
    public void setVmax(double value) { vmax = value; }
    public double getVmin() { return vmin; }
    public void setVmin(double value) { vmin = value; }
    public double getTact() { return tact; }
    public void setTact(double value) { tact = value; }
    public double getKturb() { return kturb; }
    public void setKturb(double value) { kturb = value; }
    public double getWfnl() { return wfnl; }
    public void setWfnl(double value) { wfnl = value; }
    public double getTb() { return tb; }
    public void setTb(double value) { tb = value; }
    public double getTc() { return tc; }
    public void setTc(double value) { tc = value; }
    public double getTeng() { return teng; }
    public void setTeng(double value) { teng = value; }
    public double getTfload() { return tfload; }
    public void setTfload(double value) { tfload = value; }
    public double getKpload() { return kpload; }
    public void setKpload(double value) { kpload = value; }
    public double getKiload() { return kiload; }
    public void setKiload(double value) { kiload = value; }
    public double getLdref() { return ldref; }
    public void setLdref(double value) { ldref = value; }
    public double getDm() { return dm; }
    public void setDm(double value) { dm = value; }
    public double getRopen() { return ropen; }
    public void setRopen(double value) { ropen = value; }
    public double getRclose() { return rclose; }
    public void setRclose(double value) { rclose = value; }
    public double getKimw() { return kimw; }
    public void setKimw(double value) { kimw = value; }
    public double getAset() { return aset; }
    public void setAset(double value) { aset = value; }
    public double getKa() { return ka; }
    public void setKa(double value) { ka = value; }
    public double getTa() { return ta; }
    public void setTa(double value) { ta = value; }
    public double getTrate() { return trate; }
    public void setTrate(double value) { trate = value; }
    public double getDb() { return db; }
    public void setDb(double value) { db = value; }
    public double getTsa() { return tsa; }
    public void setTsa(double value) { tsa = value; }
    public double getTsb() { return tsb; }
    public void setTsb(double value) { tsb = value; }
    public double getRup() { return rup; }
    public void setRup(double value) { rup = value; }
    public double getRdown() { return rdown; }
    public void setRdown(double value) { rdown = value; }
    public double getDbH() { return dbH; }
    public void setDbH(double value) { dbH = value; }
    public double getDbL() { return dbL; }
    public void setDbL(double value) { dbL = value; }

    @Override public void setValue(String name, int value) { }
    @Override public void setValue(String name, double value) { }
}
