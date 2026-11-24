package org.interpss.plugin.contingency.result;

import java.util.Objects;

import com.interpss.core.algo.LoadflowTerminationReport;
import com.interpss.core.algo.contingency.aclf.AcContingencyReportStatus;

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

	public boolean isConverged() {
		return isConverged;
	}
	
	public void setConverged(boolean isConverged) {
		this.isConverged = isConverged;
	}
}
