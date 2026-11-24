package org.interpss.plugin.contingency.result;

import java.util.Objects;

import com.interpss.core.algo.LoadflowTerminationReport;
import com.interpss.core.algo.contingency.aclf.AcContingencyReportStatus;

/**
 * Outcome of one AC contingency solve. The termination report carries the
 * numerical stop reason and the remaining control findings.
 *
 * <p>Report classification can admit an electrically solved case with
 * outstanding controls, or a near solution that passes separate reporting
 * thresholds. Classification leaves the original solver result intact.
 */
public class AclfContingencyResultRec {
	private boolean isConverged;
	private final LoadflowTerminationReport terminationReport;
	
	public AclfContingencyResultRec(boolean isConverged) {
		this(isConverged, LoadflowTerminationReport.notRun());
	}

	public AclfContingencyResultRec(boolean isConverged, LoadflowTerminationReport terminationReport) {
		this.isConverged = isConverged;
		this.terminationReport = Objects.requireNonNull(terminationReport);
	}

	public LoadflowTerminationReport getTerminationReport() {
		return terminationReport;
	}

	/** Classify report eligibility using the study-wide report defaults. */
	public AcContingencyReportStatus reportStatus() {
		return reportStatus(AcContingencyReportStatus.DEFAULT_BUS_TOLERANCE_MW_OR_MVAR,
				AcContingencyReportStatus.DEFAULT_SYSTEM_TOLERANCE_MVA);
	}

	/**
	 * Classifies the existing termination report under study-specific mismatch limits.
	 * These values affect report eligibility and leave the solve tolerance intact.
	 *
	 * @param busToleranceMwOrMvar maximum absolute bus P or Q mismatch in MW or Mvar
	 * @param systemToleranceMva maximum sum of bus apparent mismatches in MVA
	 * @return report classification for the recorded case
	 */
	public AcContingencyReportStatus reportStatus(double busToleranceMwOrMvar, double systemToleranceMva) {
		var status = AcContingencyReportStatus.classify(terminationReport, busToleranceMwOrMvar, systemToleranceMva);
		if (terminationReport.status() == LoadflowTerminationReport.Status.NOT_RUN && isConverged()) {
			return AcContingencyReportStatus.CONVERGED;
		}
		return status;
	}
	
	/** Whether the outcome is usable in the planning report under the default mismatch screen. */
	public boolean isReportable() {
		return switch (reportStatus()) {
			case CONVERGED, CONTROLS_UNSETTLED, TREATED_AS_CONVERGED -> true;
			default -> false;
		};
	}

	/**
	 * Returns the solve convergence flag. A converged case can still have
	 * unsettled controls. Use the termination report to check control settlement.
	 *
	 * @return whether the solve recorded convergence
	 */
	public boolean isConverged() {
		return isConverged;
	}
	
	public void setConverged(boolean isConverged) {
		this.isConverged = isConverged;
	}
}
