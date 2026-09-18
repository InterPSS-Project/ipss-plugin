package org.interpss.dstab.control.gov.psse.hygovr;

import org.interpss.dstab.control.base.BaseControllerData;

/**
 * PSS/E HYGOVR1 fourth-order lead-lag hydro governor data.
 *
 * <p>The field order is the native PSS/E 34 HYGOVR1 order: DB1, ERR, TD,
 * T1..T8, KP, R, TT, KG, TP, VELOPEN, VELCLOSE, PMAX, PMIN, DB2, TW, AT,
 * DTURB, QNL, TRATE. PowerWorld imports the older HYGOVRU spelling as
 * HYGOVR1.</p>
 */
public class PsseHygovrGovernorData extends BaseControllerData {
    private double db1;
    private double err;
    private double td;
    private double t1;
    private double t2;
    private double t3;
    private double t4;
    private double t5;
    private double t6;
    private double t7;
    private double t8;
    private double kp = 1.0;
    private double r = 0.05;
    private double tt;
    private double kg = 1.0;
    private double tp;
    private double velopen = 1.0;
    private double velclose = -1.0;
    private double pmax = 1.0;
    private double pmin;
    private double db2;
    private double tw = 1.0;
    private double at = 1.0;
    private double dturb;
    private double qnl;
    private double trate;

    public double getDb1() { return db1; }
    public void setDb1(double value) { db1 = value; }
    public double getErr() { return err; }
    public void setErr(double value) { err = value; }
    public double getTd() { return td; }
    public void setTd(double value) { td = value; }
    public double getT1() { return t1; }
    public void setT1(double value) { t1 = value; }
    public double getT2() { return t2; }
    public void setT2(double value) { t2 = value; }
    public double getT3() { return t3; }
    public void setT3(double value) { t3 = value; }
    public double getT4() { return t4; }
    public void setT4(double value) { t4 = value; }
    public double getT5() { return t5; }
    public void setT5(double value) { t5 = value; }
    public double getT6() { return t6; }
    public void setT6(double value) { t6 = value; }
    public double getT7() { return t7; }
    public void setT7(double value) { t7 = value; }
    public double getT8() { return t8; }
    public void setT8(double value) { t8 = value; }
    public double getKp() { return kp; }
    public void setKp(double value) { kp = value; }
    public double getR() { return r; }
    public void setR(double value) { r = value; }
    public double getTt() { return tt; }
    public void setTt(double value) { tt = value; }
    public double getKg() { return kg; }
    public void setKg(double value) { kg = value; }
    public double getTp() { return tp; }
    public void setTp(double value) { tp = value; }
    public double getVelopen() { return velopen; }
    public void setVelopen(double value) { velopen = value; }
    public double getVelclose() { return velclose; }
    public void setVelclose(double value) { velclose = value; }
    public double getPmax() { return pmax; }
    public void setPmax(double value) { pmax = value; }
    public double getPmin() { return pmin; }
    public void setPmin(double value) { pmin = value; }
    public double getDb2() { return db2; }
    public void setDb2(double value) { db2 = value; }
    public double getTw() { return tw; }
    public void setTw(double value) { tw = value; }
    public double getAt() { return at; }
    public void setAt(double value) { at = value; }
    public double getDturb() { return dturb; }
    public void setDturb(double value) { dturb = value; }
    public double getQnl() { return qnl; }
    public void setQnl(double value) { qnl = value; }
    public double getTrate() { return trate; }
    public void setTrate(double value) { trate = value; }

    @Override public void setValue(String name, int value) { }
    @Override public void setValue(String name, double value) { }
}
