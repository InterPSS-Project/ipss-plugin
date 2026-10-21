package org.interpss.dstab.control.exc.ieee.y2005.st4b;

import org.apache.commons.math3.complex.Complex;

/** Reuses pure bridge-input arithmetic only while all numeric arguments are unchanged. */
final class St4bBridgeInputCache {
    private Complex phasor, voltagePhasor;
    private long gainBits, angleBits, vrBits, viBits, irBits, iiBits, xlBits, kiBits;
    private boolean phasorValid, voltageValid;
    private double voltage;
    private long phasorReuses, voltageReuses;

    void invalidate() { phasorValid=false;voltageValid=false; }
    long phasorReuses() {return phasorReuses;}
    long voltageReuses() {return voltageReuses;}
    private static long bits(double value) {return Double.doubleToRawLongBits(value);}

    Complex phasor(double gain,double degrees) {
        long g=bits(gain),a=bits(degrees);
        if(phasorValid && gainBits==g && angleBits==a) {phasorReuses++;return phasor;}
        double angle=Math.toRadians(degrees);
        phasor=new Complex(gain*Math.cos(angle),gain*Math.sin(angle));
        gainBits=g;angleBits=a;
        phasorValid=Double.isFinite(gain) && Double.isFinite(degrees);
        return phasor;
    }

    double voltage(Complex kp,Complex vt,Complex it,double xl,double ki) {
        long vr=bits(vt.getReal()),vi=bits(vt.getImaginary());
        long ir=bits(it.getReal()),ii=bits(it.getImaginary()),x=bits(xl),k=bits(ki);
        if(voltageValid && voltagePhasor==kp && vrBits==vr && viBits==vi
                && irBits==ir && iiBits==ii && xlBits==x && kiBits==k) {
            voltageReuses++;return voltage;
        }
        // Keep the ordinary complex expression and arithmetic order on a miss.
        voltage=vt.multiply(kp).add(new Complex(0,1)
                .multiply((kp.multiply(xl).add(ki)).multiply(it))).abs();
        voltagePhasor=kp;vrBits=vr;viBits=vi;irBits=ir;iiBits=ii;xlBits=x;kiBits=k;
        voltageValid=Double.isFinite(voltage) && Double.isFinite(xl) && Double.isFinite(ki)
                && !kp.isNaN() && !kp.isInfinite() && !vt.isNaN() && !vt.isInfinite()
                && !it.isNaN() && !it.isInfinite();
        return voltage;
    }
}
