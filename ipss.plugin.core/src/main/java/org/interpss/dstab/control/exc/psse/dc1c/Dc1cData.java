package org.interpss.dstab.control.exc.psse.dc1c;

import org.interpss.dstab.control.exc.psse.esdc2a.Esdc2aData;

/** IEEE 421.5-2016 / native PSS/E DC1C and DC2C parameters. */
public class Dc1cData extends Esdc2aData {
    private int oelLocation, uelLocation, sclLocation;
    private double vemax = 999.0, vemin = -999.0;

    public int getOelLocation() { return oelLocation; }
    public void setOelLocation(int value) { oelLocation = value; }
    public int getUelLocation() { return uelLocation; }
    public void setUelLocation(int value) { uelLocation = value; }
    /** Typed IEEE/PowerWorld extension; not present in the native PSS/E record. */
    public int getSclLocation() { return sclLocation; }
    public void setSclLocation(int value) { sclLocation = value; }
    public double getVemax() { return vemax; }
    public void setVemax(double value) { vemax = value; }
    public double getVemin() { return vemin; }
    public void setVemin(double value) { vemin = value; }
}
