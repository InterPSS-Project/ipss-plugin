package org.interpss.plugin.contingency;

/**
 * One DCLF sensitivity result row.
 */
public record DclfSensitivityResult(
		String sourceId,
		String monitorBranchId,
		double branchRatingMva,
		double factor) {
}
