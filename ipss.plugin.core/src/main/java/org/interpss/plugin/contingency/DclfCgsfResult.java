package org.interpss.plugin.contingency;

/**
 * One DCLF combined generator shift factor (CGSF) result row.
 */
public record DclfCgsfResult(
		String genBusId,
		String monitorBranchId,
		String outageBranchId,
		double branchRatingMva,
		double cgsf,
		double genMw,
		double genMaxMw,
		double genMinMw) {
}
