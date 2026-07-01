package org.interpss.threePhase.dataParser.opendss.timeseries;

public class OpenDSSTemperatureShape {
	private final String id;
	private final int npts;
	private final double intervalHours;
	private final double[] hour;
	private final double[] temperature;
	private final String sourceFile;
	private final int sourceLine;

	public OpenDSSTemperatureShape(String id, int npts, double intervalHours, double[] hour,
			double[] temperature, String sourceFile, int sourceLine) {
		if(id == null || id.trim().isEmpty()) {
			throw new IllegalArgumentException("OpenDSS TShape id is required");
		}
		this.id = id.trim();
		this.npts = npts;
		this.intervalHours = intervalHours;
		this.hour = hour == null ? new double[0] : hour.clone();
		this.temperature = temperature == null ? new double[0] : temperature.clone();
		this.sourceFile = sourceFile;
		this.sourceLine = sourceLine;
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

	public double[] getTemperature() {
		return temperature.clone();
	}

	public int getPointCount() {
		return temperature.length;
	}

	public String getSourceFile() {
		return sourceFile;
	}

	public int getSourceLine() {
		return sourceLine;
	}
}
