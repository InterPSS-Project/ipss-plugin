package org.interpss.dstab.svc;

/** PSS/E CSVGN5 static-var-compensator parameters on the generator MVA base. */
public record Csvgn5Data(int remoteBusNumber, double ts1, double vemax,
        double ts2, double ts3, double ts4, double ts5, double ksvs,
        double ksd, double bmax, double bPrimeMax, double bPrimeMin,
        double bmin, double ts6, double dv) {

    public Csvgn5Data {
        if (remoteBusNumber == 0) {
            throw new IllegalArgumentException("CSVGN5 remote bus number must be nonzero");
        }
        if (ts1 < 0.0 || ts2 < 0.0 || ts3 <= 0.0 || ts4 < 0.0
                || ts5 < 0.0 || ts6 <= 0.0) {
            throw new IllegalArgumentException(
                    "CSVGN5 requires Ts1, Ts2, Ts4, Ts5 >= 0 and Ts3, Ts6 > 0");
        }
        if (vemax < 0.0 || ksvs == 0.0 || ksd < 0.0 || dv < 0.0) {
            throw new IllegalArgumentException(
                    "CSVGN5 requires Vemax, Ksd, DV >= 0 and nonzero Ksvs");
        }
        if (bmax < bmin || bPrimeMax < bPrimeMin) {
            throw new IllegalArgumentException("CSVGN5 susceptance limits are inverted");
        }
        if (ts5 == 0.0 && ts4 != 0.0) {
            throw new IllegalArgumentException(
                    "CSVGN5 Ts5 may be zero only when its lead numerator Ts4 is also zero");
        }
    }
}
