package org.interpss.dstab.control.gov.psse.hyg3;

import java.util.Arrays;

import org.interpss.dstab.control.base.BaseControllerData;

/**
 * Data for the WECC/PSLF HYG3 governor as carried by the PSS/E HYG3U1 model.
 *
 * <p>The PSS/E user-model record contains one configuration ICON followed by
 * 36 constants. Its order is deliberately represented here rather than the
 * different display order used by PowerWorld's HYG3 parameter table.</p>
 */
public class PsseHyg3GovernorData extends BaseControllerData {
    public static final int PID_CONTROL = 0;
    public static final int DOUBLE_DERIVATIVE_CONTROL = 1;

    private int controlFlag = PID_CONTROL;
    private double rgate = 0.05;
    private double relec;
    private double tt;
    private double td = 0.05;
    private double k2;
    private double ki = 1.0;
    private double k1;
    private double tf = 0.05;
    private double kg = 1.0;
    private double tp = 0.1;
    private double velopen = 0.1;
    private double velclose = -0.1;
    private double pmax = 1.0;
    private double pmin;
    private double db2;
    private final double[] gv = {0.1, 0.25, 0.5, 0.7, 0.85, 1.0};
    private final double[] pgv = {0.0, 0.2, 0.5, 0.75, 0.9, 1.0};
    private double h0 = 1.0;
    private double qnl;
    private double tw = 1.0;
    private double at = 1.0;
    private double dturb;
    private double trate;
    private double dbH;
    private double eps;
    private double dbL;

    public int getControlFlag() { return controlFlag; }
    public void setControlFlag(int value) { controlFlag = value; }
    public double getRgate() { return rgate; }
    public void setRgate(double value) { rgate = value; }
    public double getRelec() { return relec; }
    public void setRelec(double value) { relec = value; }
    public double getTt() { return tt; }
    public void setTt(double value) { tt = value; }
    public double getTd() { return td; }
    public void setTd(double value) { td = value; }
    public double getK2() { return k2; }
    public void setK2(double value) { k2 = value; }
    public double getKi() { return ki; }
    public void setKi(double value) { ki = value; }
    public double getK1() { return k1; }
    public void setK1(double value) { k1 = value; }
    public double getTf() { return tf; }
    public void setTf(double value) { tf = value; }
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
    public double getGv(int index) { return gv[index]; }
    public void setGv(int index, double value) { gv[index] = value; }
    public double[] getGv() { return gv.clone(); }
    public void setGv(double[] values) {
        if (values.length != gv.length) throw new IllegalArgumentException("HYG3 requires six GV points");
        System.arraycopy(values, 0, gv, 0, gv.length);
    }
    public double getPgv(int index) { return pgv[index]; }
    public void setPgv(int index, double value) { pgv[index] = value; }
    public double[] getPgv() { return pgv.clone(); }
    public void setPgv(double[] values) {
        if (values.length != pgv.length) throw new IllegalArgumentException("HYG3 requires six PGV points");
        System.arraycopy(values, 0, pgv, 0, pgv.length);
    }
    public double getH0() { return h0; }
    public void setH0(double value) { h0 = value; }
    public double getQnl() { return qnl; }
    public void setQnl(double value) { qnl = value; }
    public double getTw() { return tw; }
    public void setTw(double value) { tw = value; }
    public double getAt() { return at; }
    public void setAt(double value) { at = value; }
    public double getDturb() { return dturb; }
    public void setDturb(double value) { dturb = value; }
    public double getTrate() { return trate; }
    public void setTrate(double value) { trate = value; }
    public double getDbH() { return dbH; }
    public void setDbH(double value) { dbH = value; }
    public double getEps() { return eps; }
    public void setEps(double value) { eps = value; }
    public double getDbL() { return dbL; }
    public void setDbL(double value) { dbL = value; }

    @Override public void setValue(String name, int value) {
        if ("controlFlag".equals(name) || "cflag".equalsIgnoreCase(name)) setControlFlag(value);
    }

    @Override public void setValue(String name, double value) { }

    @Override public String toString() {
        return "HYG3{flag=" + controlFlag + ", gv=" + Arrays.toString(gv)
                + ", pgv=" + Arrays.toString(pgv) + "}";
    }
}
