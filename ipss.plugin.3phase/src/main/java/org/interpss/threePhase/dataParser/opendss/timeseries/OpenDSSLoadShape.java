package org.interpss.threePhase.dataParser.opendss.timeseries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.interpss.threePhase.qsts.QstsProfile;

public class OpenDSSLoadShape {
	private final String id;
	private final int npts;
	private final double intervalHours;
	private final double[] hour;
	private final double[] pMult;
	private final double[] qMult;
	private final String sourceFile;
	private final int sourceLine;
	private final List<OpenDSSParserDiagnostic> diagnostics;

	public OpenDSSLoadShape(String id, int npts, double intervalHours, double[] hour, double[] pMult,
			double[] qMult, String sourceFile, int sourceLine, List<OpenDSSParserDiagnostic> diagnostics) {
		if(id == null || id.trim().isEmpty()) {
			throw new IllegalArgumentException("OpenDSS LoadShape id is required");
		}
		this.id = id.trim();
		this.npts = npts;
		this.intervalHours = intervalHours;
		this.hour = hour == null ? new double[0] : hour.clone();
		this.pMult = pMult == null ? new double[0] : pMult.clone();
		this.qMult = qMult == null ? this.pMult.clone() : qMult.clone();
		this.sourceFile = sourceFile;
		this.sourceLine = sourceLine;
		this.diagnostics = diagnostics == null
				? Collections.emptyList()
				: Collections.unmodifiableList(new ArrayList<>(diagnostics));
	}

	public String getId() {
		return id;
	}

	public int getNpts() {
		return npts;
	}

	public double getIntervalHours() {
		return intervalHours;
	}

	public double[] getHour() {
		return hour.clone();
	}

	public double[] getPMult() {
		return pMult.clone();
	}

	public double[] getQMult() {
		return qMult.clone();
	}

	public int getPointCount() {
		return pMult.length;
	}

	public String getSourceFile() {
		return sourceFile;
	}

	public int getSourceLine() {
		return sourceLine;
	}

	public List<OpenDSSParserDiagnostic> getDiagnostics() {
		return diagnostics;
	}

	public QstsProfile toQstsProfile() {
		List<String> qstsDiagnostics = new ArrayList<>();
		for(OpenDSSParserDiagnostic diagnostic : diagnostics) {
			qstsDiagnostics.add(diagnostic.toString());
		}
		return new QstsProfile(id, intervalHours, hour, pMult, qMult, qstsDiagnostics);
	}
}
