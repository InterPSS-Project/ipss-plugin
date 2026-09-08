package org.interpss.dstab.control.exc.psse.esac5a;

import org.interpss.dstab.control.exc.psse.ac5c.Ac5cData;

/** PSS/E/PowerWorld ESAC5A parameters in model units. */
public final class Esac5aData extends Ac5cData {
    public double getVrmax(){return getVamax();}
    public void setVrmax(double value){setVamax(value);}
    public double getVrmin(){return getVamin();}
    public void setVrmin(double value){setVamin(value);}
}
