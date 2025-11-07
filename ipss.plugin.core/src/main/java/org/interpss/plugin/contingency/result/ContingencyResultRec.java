package org.interpss.plugin.contingency.result;

public class ContingencyResultRec {
	private boolean isConverged;
	
	public ContingencyResultRec(boolean isConverged) {
		this.isConverged = isConverged;
	}
	
	public boolean isConverged() {
		return isConverged;
	}
	
	public void setConverged(boolean isConverged) {
		this.isConverged = isConverged;
	}
}
