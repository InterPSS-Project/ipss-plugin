package org.interpss.threePhase.dataParser.opendss.timeseries;

public class OpenDSSParserDiagnostic {
	public enum Severity {
		INFO,
		WARNING,
		ERROR
	}

	private final Severity severity;
	private final String message;
	private final String sourceFile;
	private final int sourceLine;

	public OpenDSSParserDiagnostic(Severity severity, String message, String sourceFile, int sourceLine) {
		this.severity = severity == null ? Severity.WARNING : severity;
		this.message = message == null ? "" : message;
		this.sourceFile = sourceFile;
		this.sourceLine = sourceLine;
	}

	public Severity getSeverity() {
		return severity;
	}

	public String getMessage() {
		return message;
	}

	public String getSourceFile() {
		return sourceFile;
	}

	public int getSourceLine() {
		return sourceLine;
	}

	@Override
	public String toString() {
		String location = sourceFile == null ? "" : sourceFile + ":" + sourceLine + " ";
		return severity + " " + location + message;
	}
}
