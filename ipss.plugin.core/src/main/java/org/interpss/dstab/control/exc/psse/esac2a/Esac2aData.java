package org.interpss.dstab.control.exc.psse.esac2a;

import org.interpss.dstab.control.exc.psse.exac1.Exac1Data;

/** PSS/E/PowerWorld ESAC2A parameters in model units. */
public class Esac2aData extends Exac1Data {
    private double vamax;
    private double vamin;
    private double kb;
    private double vfemax;
    private double kh;

    public double getVamax() { return vamax; }
    public void setVamax(double value) { vamax = value; }
    public double getVamin() { return vamin; }
    public void setVamin(double value) { vamin = value; }
    public double getKb() { return kb; }
    public void setKb(double value) { kb = value; }
    public double getVfemax() { return vfemax; }
    public void setVfemax(double value) { vfemax = value; }
    public double getKh() { return kh; }
    public void setKh(double value) { kh = value; }
}
