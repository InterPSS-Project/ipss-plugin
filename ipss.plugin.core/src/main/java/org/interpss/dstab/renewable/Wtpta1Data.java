package org.interpss.dstab.renewable;

/** Complete ten-parameter PSS/E WTPTA1 (WECC WTPT_A) record. */
public record Wtpta1Data(
        double kiw, double kpw, double kic, double kpc, double kcc,
        double tp, double thetaMax, double thetaMin,
        double dThetaMax, double dThetaMin) {
    public Wtpta1Data {
        for(double value:new double[]{kiw,kpw,kic,kpc,kcc,tp,thetaMax,thetaMin,dThetaMax,dThetaMin})
            if(!Double.isFinite(value))throw new IllegalArgumentException("WTPTA1 parameters must be finite");
        if (tp < 0.0) throw new IllegalArgumentException("WTPTA1 Tp must be non-negative");
        if (thetaMax < thetaMin || dThetaMax < dThetaMin) {
            throw new IllegalArgumentException("WTPTA1 upper limits must be >= lower limits");
        }
    }
}
