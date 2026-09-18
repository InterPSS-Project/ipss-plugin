package org.interpss.dstab.control.exc.psse.esac1a;

import org.interpss.dstab.control.exc.psse.exac1.Exac1Data;

/** PSS/E/PowerWorld ESAC1A parameters in model units. */
public class Esac1aData extends Exac1Data {
    private double vamax;
    private double vamin;

    public double getVamax() { return vamax; }
    public void setVamax(double value) { vamax = value; }
    public double getVamin() { return vamin; }
    public void setVamin(double value) { vamin = value; }
}
