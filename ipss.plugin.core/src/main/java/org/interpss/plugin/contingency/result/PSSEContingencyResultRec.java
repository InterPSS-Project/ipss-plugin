package org.interpss.plugin.contingency.result;

public class PSSEContingencyResultRec extends ContingencyResultRec {
	private int contType = 0; // 0 - BaseCase, 1 - N-1, 2 - N-2;
	private double largestBusMismatch = 0.0;
	private int noLoadViolation = 0;
	private int noVoltageViolation = 0;
	private double largestLoading = 0.0;
	private double largestUnderVoltage = 0.0;
	
	
	public PSSEContingencyResultRec(boolean isConverged) {
		super(isConverged);
	}
	
	public int getContType() {
		return contType;
	}
	
	public void setContType(int contType) {
		this.contType = contType;
	}

	public double getLargestBusMismatch() {
		return largestBusMismatch;
	}

	public void setLargestBusMismatch(double largestBusMismatch) {
		this.largestBusMismatch = largestBusMismatch;
	}

	public int getNoLoadViolation() {
		return noLoadViolation;
	}

	public void setNoLoadViolation(int noLoadViolation) {
		this.noLoadViolation = noLoadViolation;
	}

	public int getNoVoltageViolation() {
		return noVoltageViolation;
	}

	public void setNoVoltageViolation(int noVoltageViolation) {
		this.noVoltageViolation = noVoltageViolation;
	}

	public double getLargestLoading() {
		return largestLoading;
	}

	public void setLargestLoading(double largestLoading) {
		this.largestLoading = largestLoading;
	}

	public double getLargestUnderVoltage() {
		return largestUnderVoltage;
	}

	public void setLargestUnderVoltage(double largestUnderVoltage) {
		this.largestUnderVoltage = largestUnderVoltage;
	}
	
}
