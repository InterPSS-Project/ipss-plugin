package org.interpss.dstab.mach;

/** WT3E1 electrical-control parameters in published DYR order. */
public record Wt3e1Data(
        int remoteBus, int varFlag, int voltageLimitFlag,
        int transformerFromBus, int transformerToBus, int transformerCircuit,
        double tfv, double kpv, double kiv, double xcc,
        double tfp, double kpp, double kip, double pmax, double pmin,
        double qmax, double qmin, double ipmax, double trv,
        double rpmax, double rpmin, double powerFilterTime, double kqi,
        double vmincl, double vmaxcl, double kqv, double xiqmin, double xiqmax,
        double tvr, double tpp, double onlineFraction,
        double omegaPmin, double omegaP20, double omegaP40, double omegaP60,
        double minimumPowerAtFullSpeed, double omegaP100) {

    public Wt3e1Data {
        if (varFlag < -1 || varFlag > 1) fail("VARFLAG must be -1, 0, or 1");
        if (voltageLimitFlag < 0 || voltageLimitFlag > 2) fail("VLTFLAG must be 0, 1, or 2");
        double[] values = {tfv, kpv, kiv, xcc, tfp, kpp, kip, pmax, pmin,
                qmax, qmin, ipmax, trv, rpmax, rpmin, powerFilterTime, kqi,
                vmincl, vmaxcl, kqv, xiqmin, xiqmax, tvr, tpp, onlineFraction,
                omegaPmin, omegaP20, omegaP40, omegaP60,
                minimumPowerAtFullSpeed, omegaP100};
        for (double value : values) if (!Double.isFinite(value)) fail("parameters must be finite");
        if (tfv < 0 || tfp < 0 || trv < 0 || powerFilterTime < 0 || tvr < 0 || tpp < 0) {
            fail("time constants must be nonnegative");
        }
        if (pmin > pmax || qmin > qmax || rpmin > rpmax
                || vmincl > vmaxcl || xiqmin > xiqmax) fail("minimum exceeds maximum");
        if (ipmax <= 0 || onlineFraction <= 0) fail("current limit and online fraction must be positive");
        if (!(minimumPowerAtFullSpeed > .6 && minimumPowerAtFullSpeed <= 1.0)) {
            fail("full-speed power breakpoint must be in (0.6, 1.0]");
        }
    }

    private static void fail(String message) { throw new IllegalArgumentException("WT3E1 " + message); }
}
