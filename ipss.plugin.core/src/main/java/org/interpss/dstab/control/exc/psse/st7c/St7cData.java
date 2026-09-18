package org.interpss.dstab.control.exc.psse.st7c;

import org.interpss.dstab.control.exc.psse.st7b.St7bData;

/** Native PSS/E IEEE 421.5-2016 ST7C excitation-system parameters. */
public final class St7cData extends St7bData {
    private double ta;
    @Override public void setValue(String name,double value){if("ta".equalsIgnoreCase(name))ta=value;else super.setValue(name,value);}
    public double getTa(){return ta;}public void setTa(double value){ta=value;}
}
