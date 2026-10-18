package org.interpss.dstab.renewable;

/** PSS/E 36.7 WTPTB1 fourteen-CON order; angles and rates use degrees. */
public record Wtptb1Data(double kiw, double kpw, double kic, double kpc,
        double kcc, double tp, double thetaMax, double thetaMin,
        double rateMax, double rateMin, double compensationMax,
        double compensationMin, double speedMax, double speedMin) {
    public Wtptb1Data {
        for (double value : new double[] {kiw, kpw, kic, kpc, kcc, tp,
                thetaMax, thetaMin, rateMax, rateMin, compensationMax,
                compensationMin, speedMax, speedMin}) {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("WTPTB1 parameters must be finite");
        }
        if (tp < 0) throw new IllegalArgumentException("WTPTB1 requires Tp >= 0");
        if (thetaMax < thetaMin || compensationMax < compensationMin || speedMax < speedMin)
            throw new IllegalArgumentException("WTPTB1 upper limits must be >= lower limits");
        if (rateMax <= 0 || rateMin >= 0)
            throw new IllegalArgumentException("WTPTB1 pitch-rate limits must straddle zero");
    }
}
