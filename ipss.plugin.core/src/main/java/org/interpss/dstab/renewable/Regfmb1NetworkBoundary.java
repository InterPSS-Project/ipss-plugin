package org.interpss.dstab.renewable;

import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.renewable.Regfmb1Data.Flag;
import org.interpss.dstab.renewable.Regfmb1Data.Parameter;

/** Native REGFMB1U algebraic boundary primitives. No dynamic state or attachment.
 * Currents and impedances here are exclusively on machine MVA base. */
public final class Regfmb1NetworkBoundary {
    private Regfmb1NetworkBoundary(){}
    public record Limits(double idmax,double iqmax,double emax,double emin){}
    public static Complex current(Regfmb1Data data,Complex internal,Complex terminal) {
        Complex unlimited=internal.subtract(terminal).divide(data.sourceImpedance());
        double magnitude=unlimited.abs(),limit=data.faultCurrentLimit();
        return magnitude>limit?unlimited.multiply(limit/magnitude):unlimited;
    }
    /** Reconstructed source cancels the unchanged Norton impedance at the boundary. */
    public static Complex limitedInternalVoltage(Regfmb1Data data,Complex internal,Complex terminal) {
        return terminal.add(data.sourceImpedance().multiply(current(data,internal,terminal)));
    }
    public static Complex toDq(Complex network,double pllAngleRadians) {
        return network.multiply(new Complex(Math.cos(pllAngleRadians),-Math.sin(pllAngleRadians)));
    }
    public static Complex initialInternalVoltage(Regfmb1Data data,Complex terminal,Complex machinePower) {
        if(terminal.abs()==0)throw new IllegalArgumentException("REGFMB1U initial voltage must be nonzero");
        return terminal.add(data.sourceImpedance().multiply(machinePower.divide(terminal).conjugate()));
    }
    /** Native notes 15-16; the circle's remaining radius is nonnegative. */
    public static Limits limits(Regfmb1Data data,double filteredId,double filteredIq,double filteredVoltage) {
        double iss=data.steadyCurrentLimit(),priority=data.value(Parameter.KF)*iss;
        double idmax=data.enabled(Flag.PQFLAG)?priority:remaining(iss,filteredIq);
        double iqmax=data.enabled(Flag.PQFLAG)?remaining(iss,filteredId):priority;
        double x=data.sourceImpedance().getImaginary(),active=filteredId*x;
        return new Limits(idmax,iqmax,Math.hypot(filteredVoltage+iqmax*x,active),
                Math.hypot(filteredVoltage-iqmax*x,active));
    }
    private static double remaining(double radius,double component){return Math.sqrt(Math.max(0,radius*radius-component*component));}
}
