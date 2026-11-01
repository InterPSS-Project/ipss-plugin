package org.interpss.dstab.hvdc;

/** Prepared normal-operation three-point voltage-dependent current ceiling. */
public final class Cdc6tVoltageCurrentLimit {
    private final double v1,v2,v3,c1,c2,c3,slope12,slope23;
    /** Curve voltage coordinates are kV; evaluation uses measured inverter volts. */
    public Cdc6tVoltageCurrentLimit(double v1,double c1,double v2,double c2,double v3,double c3) {
        finite(v1);finite(v2);finite(v3);finite(c1);finite(c2);finite(c3);
        if(v1<0||v2<=v1||v3<=v2||c1<0||c2<c1||c3<c2)
            throw new IllegalArgumentException("Increasing voltage and nondecreasing nonnegative current points required");
        this.v1=v1*1000;this.v2=v2*1000;this.v3=v3*1000;
        finite(this.v1);finite(this.v2);finite(this.v3);
        this.c1=c1;this.c2=c2;this.c3=c3;
        slope12=(c2-c1)/(this.v2-this.v1);slope23=(c3-c2)/(this.v3-this.v2);
        finite(slope12);finite(slope23);
    }
    public double ceilingAmperes(double measuredInverterVolts) {
        finite(measuredInverterVolts);
        if(measuredInverterVolts<=v1)return c1;
        if(measuredInverterVolts>=v3)return c3;
        return measuredInverterVolts<=v2?c1+slope12*(measuredInverterVolts-v1)
                :c2+slope23*(measuredInverterVolts-v2);
    }
    /** Selects the smaller normal order. Recovery ramps and the restart floor are separate. */
    public double limit(double desiredAmperes,double measuredInverterVolts) {
        finite(desiredAmperes);return Math.min(desiredAmperes,ceilingAmperes(measuredInverterVolts));
    }
    private static void finite(double value) {
        if(!Double.isFinite(value))throw new IllegalArgumentException("Finite current-limit curve required");
    }
}
