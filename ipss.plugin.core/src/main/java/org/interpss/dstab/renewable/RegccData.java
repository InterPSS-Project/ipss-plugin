package org.interpss.dstab.renewable;

/** Exact REGCC order: two selectors followed by thirteen machine-base constants. */
public record RegccData(int rateFlag, int pqFlag,
        double iqrmax, double iqrmin, double tfltr, double rrpwr, double imax,
        double te, double kip, double kii, double kppll, double kipll,
        double wmax, double wmin, double vpllfrz) {
    public RegccData {
        if ((rateFlag != 0 && rateFlag != 1) || (pqFlag != 0 && pqFlag != 1))
            throw new IllegalArgumentException("REGCC selectors must be zero or one");
        for (double value : new double[]{iqrmax,iqrmin,tfltr,rrpwr,imax,te,kip,kii,kppll,kipll,wmax,wmin,vpllfrz})
            if (!Double.isFinite(value)) throw new IllegalArgumentException("REGCC constants must be finite");
        if (iqrmax <= 0 || iqrmin >= 0 || tfltr < 0 || rrpwr <= 0 || imax <= 0
                || te <= 0 || kip < 0 || kii <= 0 || kppll < 0 || kipll < 0
                || wmax <= 0 || wmin >= 0 || vpllfrz < 0)
            throw new IllegalArgumentException("REGCC gains, limits or time constants are invalid");
    }
}
