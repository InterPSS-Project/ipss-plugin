package org.interpss.dstab.control.exc.psse.esac8b;

import org.interpss.dstab.control.exc.psse.ac8b.Ac8bData;

/** Exact native PSS/E ESAC8B 15-CON profile for the shared AC8B PID engine. */
public final class Esac8bData extends Ac8bData {
    public Esac8bData() {
        setVpidmax(Double.MAX_VALUE);
        setVpidmin(-Double.MAX_VALUE);
        setVfemax(Double.POSITIVE_INFINITY);
        setVemin(0.0);
        setKc(0.0);
        setKd(0.0);
    }

    @Override public boolean isEsac8bPti() { return true; }
}
