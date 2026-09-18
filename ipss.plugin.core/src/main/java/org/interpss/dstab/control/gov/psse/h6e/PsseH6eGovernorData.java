package org.interpss.dstab.control.gov.psse.h6e;

import org.interpss.dstab.control.base.BaseControllerData;

/** Data for the WECC H6E / PSS/E H6EU1 Kaplan hydro governor. */
public class PsseH6eGovernorData extends BaseControllerData {
    private int fd = 1;
    private double re = 0.05;
    private double rg = 0.05;
    private double tpe = 0.5;
    private double tsp = 0.025;
    private double kp = 2.0;
    private double ki = 0.5;
    private double kd;
    private double td = 0.05;
    private double velm = 0.1;
    private double gmax = 1.0;
    private double gmin;
    private double buf;
    private double buv = 0.1;
    private double kg = 1.0;
    private double tg = 0.05;
    private double blg;
    private double dbbd;
    private double tbd;
    private double blb;
    private double dbbs;
    private double tbs;
    private double bgvmin = 0.01;
    private double blv = 1.0;
    private double dturb;
    private double pgc;
    private double deff;
    private double hdam = 1.0;
    private double tw = 1.0;
    private final double[] gv = new double[10];
    private final double[] pgv = new double[10];
    private final double[] bgv = new double[10];
    private double sprate;
    private double db1;
    private double eps;
    private double trate;

    public PsseH6eGovernorData() {
        for (int i = 0; i < 10; i++) {
            gv[i] = i / 9.0;
            pgv[i] = i / 9.0;
            bgv[i] = 1.0;
        }
    }

    public int getFd() { return fd; }
    public void setFd(int value) { fd = value; }
    public double getRe() { return re; }
    public void setRe(double value) { re = value; }
    public double getRg() { return rg; }
    public void setRg(double value) { rg = value; }
    public double getTpe() { return tpe; }
    public void setTpe(double value) { tpe = value; }
    public double getTsp() { return tsp; }
    public void setTsp(double value) { tsp = value; }
    public double getKp() { return kp; }
    public void setKp(double value) { kp = value; }
    public double getKi() { return ki; }
    public void setKi(double value) { ki = value; }
    public double getKd() { return kd; }
    public void setKd(double value) { kd = value; }
    public double getTd() { return td; }
    public void setTd(double value) { td = value; }
    public double getVelm() { return velm; }
    public void setVelm(double value) { velm = value; }
    public double getGmax() { return gmax; }
    public void setGmax(double value) { gmax = value; }
    public double getGmin() { return gmin; }
    public void setGmin(double value) { gmin = value; }
    public double getBuf() { return buf; }
    public void setBuf(double value) { buf = value; }
    public double getBuv() { return buv; }
    public void setBuv(double value) { buv = value; }
    public double getKg() { return kg; }
    public void setKg(double value) { kg = value; }
    public double getTg() { return tg; }
    public void setTg(double value) { tg = value; }
    public double getBlg() { return blg; }
    public void setBlg(double value) { blg = value; }
    public double getDbbd() { return dbbd; }
    public void setDbbd(double value) { dbbd = value; }
    public double getTbd() { return tbd; }
    public void setTbd(double value) { tbd = value; }
    public double getBlb() { return blb; }
    public void setBlb(double value) { blb = value; }
    public double getDbbs() { return dbbs; }
    public void setDbbs(double value) { dbbs = value; }
    public double getTbs() { return tbs; }
    public void setTbs(double value) { tbs = value; }
    public double getBgvmin() { return bgvmin; }
    public void setBgvmin(double value) { bgvmin = value; }
    public double getBlv() { return blv; }
    public void setBlv(double value) { blv = value; }
    public double getDturb() { return dturb; }
    public void setDturb(double value) { dturb = value; }
    public double getPgc() { return pgc; }
    public void setPgc(double value) { pgc = value; }
    public double getDeff() { return deff; }
    public void setDeff(double value) { deff = value; }
    public double getHdam() { return hdam; }
    public void setHdam(double value) { hdam = value; }
    public double getTw() { return tw; }
    public void setTw(double value) { tw = value; }
    public double getGv(int index) { return gv[index]; }
    public void setGv(int index, double value) { gv[index] = value; }
    public double[] getGv() { return gv.clone(); }
    public void setGv(double[] values) { copy(values, gv, "GV"); }
    public double getPgv(int index) { return pgv[index]; }
    public void setPgv(int index, double value) { pgv[index] = value; }
    public double[] getPgv() { return pgv.clone(); }
    public void setPgv(double[] values) { copy(values, pgv, "PGV"); }
    public double getBgv(int index) { return bgv[index]; }
    public void setBgv(int index, double value) { bgv[index] = value; }
    public double[] getBgv() { return bgv.clone(); }
    public void setBgv(double[] values) { copy(values, bgv, "BGV"); }
    public double getSprate() { return sprate; }
    public void setSprate(double value) { sprate = value; }
    public double getDb1() { return db1; }
    public void setDb1(double value) { db1 = value; }
    public double getEps() { return eps; }
    public void setEps(double value) { eps = value; }
    public double getTrate() { return trate; }
    public void setTrate(double value) { trate = value; }

    private static void copy(double[] source, double[] target, String name) {
        if (source == null || source.length != 10) {
            throw new IllegalArgumentException("H6E requires ten " + name + " points");
        }
        System.arraycopy(source, 0, target, 0, 10);
    }

    @Override public void setValue(String name, int value) {
        if ("fd".equalsIgnoreCase(name)) setFd(value);
    }
    @Override public void setValue(String name, double value) { }
}
