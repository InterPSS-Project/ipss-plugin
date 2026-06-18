package org.interpss.plugin.contingency;

/**
 * @deprecated Use {@link com.interpss.core.algo.dclf.DclfSensitivityResult}.
 */
@Deprecated
public record DclfSensitivityResult(
        String sourceId,
        String monitorBranchId,
        double branchRatingMva,
        double factor) {
}
