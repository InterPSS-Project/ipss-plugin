package org.interpss.plugin.dstab.config;

import java.util.ArrayList;
import java.util.List;

/** Per-run screening values. Only model assignments with a qualified threshold contract consume them. */
public record LoadModelThresholds(double minimumMw, double minimumAbsPOverQ, double minimumVoltagePu) {
    public LoadModelThresholds {
        if (!Double.isFinite(minimumMw) || !Double.isFinite(minimumAbsPOverQ)
                || !Double.isFinite(minimumVoltagePu) || minimumMw < 0
                || minimumAbsPOverQ < 0 || minimumVoltagePu < 0)
            throw new IllegalArgumentException("Load model thresholds must be finite and nonnegative");
    }
    /** Equality is admitted. The source ratio is abs(P/Q), not signed P/abs(Q).
     * A zero-Q load does not fail the ratio test; the signed MW test remains independent. */
    public List<String> violations(double pMw, double qMvar, double voltagePu) {
        if (!Double.isFinite(pMw) || !Double.isFinite(qMvar) || !Double.isFinite(voltagePu))
            throw new IllegalArgumentException("Nonfinite load screening operating point");
        List<String> reasons = new ArrayList<>();
        if (pMw < minimumMw) reasons.add("MW_BELOW_THRESHOLD");
        if (qMvar != 0 && Math.abs(pMw / qMvar) < minimumAbsPOverQ) reasons.add("ABS_P_OVER_Q_BELOW_THRESHOLD");
        if (voltagePu < minimumVoltagePu) reasons.add("VOLTAGE_BELOW_THRESHOLD");
        return List.copyOf(reasons);
    }
}
