package org.interpss.dstab.renewable;

/** PSS/E REPCA1 (WECC REPC_A) plant-controller parameters. */
public record Repca1Data(
        int remoteBus, int branchFromBus, int branchToBus, String branchId,
        int vcFlag, int refFlag, int fFlag,
        double tfltr, double kp, double ki, double tft, double tfv,
        double vfrz, double rc, double xc, double kc,
        double emax, double emin, double dbd1, double dbd2,
        double qmax, double qmin, double kpg, double kig, double tp,
        double fdbd1, double fdbd2, double femax, double femin,
        double pmax, double pmin, double tg, double ddn, double dup, int puFlag) {

    public Repca1Data {
        branchId = branchId == null ? "" : branchId.trim();
        checkFlag(vcFlag, "VCFlag");
        // The native negative selector takes the voltage branch, just as 1;
        // keep it intact for round-trip diagnostics rather than rewriting it.
        if(refFlag!=-1)checkFlag(refFlag, "RefFlag");
        checkFlag(fFlag, "FreqFlag");
        if (tfltr < 0 || tfv < 0 || tp < 0 || tg < 0)
            throw new IllegalArgumentException("REPCA1 time constants must be non-negative");
        // The disabled frequency controller must not validate or evaluate its
        // dormant limiter ordering. Reactive limits remain active and strict.
        if (emax < emin || qmax < qmin || fFlag==1&&(femax < femin || pmax < pmin))
            throw new IllegalArgumentException("REPCA1 upper limits must be >= lower limits");
        if (puFlag != 0 && puFlag != 1)
            throw new IllegalArgumentException("REPCA1 PUflag must be 0 (system base) or 1 (model base)");
    }

    private static void checkFlag(int value, String name) {
        if (value != 0 && value != 1) {
            throw new IllegalArgumentException("REPCA1 " + name + " must be 0 or 1");
        }
    }
}
