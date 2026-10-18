package org.interpss.dstab.renewable;

import org.apache.commons.math3.complex.Complex;
import static org.interpss.dstab.renewable.Regfmc1Data.Parameter.*;

/** Native REGFMC1U hybrid algebraic boundary (figures 1/3, notes 12/13).
 * All currents, power commands and impedance use machine MVA base.
 * No state, simulator attachment, or dynamic initialization is implied. */
public final class Regfmc1NetworkBoundary {
    private Regfmc1NetworkBoundary(){}
    public record Gfl(double ip,double iq,double ipmax,double iqmax,double voltageInjection,Complex current){}
    public record Hybrid(Complex rawGfmCurrent,Complex rawGflCurrent,
                         Complex gfmCurrent,Complex gflCurrent,Complex totalCurrent,
                         Complex internalVoltage,double currentRatio){}

    /** Iq is positive for reactive power injection; the network frame uses -j Iq. */
    public static Gfl gridFollowing(Regfmc1Data data,double pref,double qref,double filteredVoltage,
                                    double voltageReference,double terminalAngle) {
        for(double v:new double[]{pref,qref,filteredVoltage,voltageReference,terminalAngle})finite(v);
        double divisor=Math.max(data.minimumGflVoltage(),filteredVoltage);
        double error=voltageReference-filteredVoltage;
        double deadband=error>data.value(DBVH)?error-data.value(DBVH):error<data.value(DBVL)?error-data.value(DBVL):0;
        double injection=data.value(KQV)*deadband;
        double ipref=clip(pref,data.value(PREFMIN),data.value(PREFMAX))/divisor;
        double iqref=clip(qref,data.value(QREFMIN),data.value(QREFMAX))/divisor+injection;
        double radius=data.value(IMAX),priority=data.currentPriorityFactor()*radius;
        // Note 12 uses the pre-limit reference, not the priority-clipped current.
        double ipmax=data.enabled(Regfmc1Data.Flag.PQFLAG)?priority:remaining(radius,iqref);
        double iqmax=data.enabled(Regfmc1Data.Flag.PQFLAG)?remaining(radius,ipref):priority;
        double ip=clip(ipref,-ipmax,ipmax),iq=clip(iqref,-iqmax,iqmax);
        Complex current=new Complex(ip,-iq).multiply(polar(1,terminalAngle));
        return new Gfl(ip,iq,ipmax,iqmax,injection,current);
    }

    /** Both branches share one scale when their vector sum exceeds Imax.
     * Scaling the branches separately would change the terminal current angle. */
    public static Hybrid hybrid(Regfmc1Data data,Complex internal,Complex terminal,Complex gflCurrent) {
        finite(internal);finite(terminal);finite(gflCurrent);
        Complex rawGfm=internal.subtract(terminal).divide(data.sourceImpedance());
        double ratio=rawGfm.add(gflCurrent).abs()/data.value(IMAX),scale=ratio>1?1/ratio:1;
        Complex gfm=rawGfm.multiply(scale),gfl=gflCurrent.multiply(scale);
        return new Hybrid(rawGfm,gflCurrent,gfm,gfl,gfm.add(gfl),terminal.add(data.sourceImpedance().multiply(gfm)),ratio);
    }
    private static Complex polar(double magnitude,double angle){return new Complex(Math.cos(angle),Math.sin(angle)).multiply(magnitude);}
    private static double clip(double v,double min,double max){return Math.max(min,Math.min(max,v));}
    private static double remaining(double r,double c){return Math.sqrt(Math.max(0,r*r-c*c));}
    private static void finite(double v){if(!Double.isFinite(v))throw new IllegalArgumentException("Boundary inputs must be finite");}
    private static void finite(Complex v){if(v==null)throw new IllegalArgumentException("Missing phasor");finite(v.getReal());finite(v.getImaginary());}
}
