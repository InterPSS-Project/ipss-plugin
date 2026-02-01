package org.interpss.plugin.contingency.result;

public class AclfContingencyResultRec {
	private boolean isConverged;
	
	public AclfContingencyResultRec(boolean isConverged) {
		this.isConverged = isConverged;
	}
	
	public boolean isConverged() {
		return isConverged;
	}
	
	public void setConverged(boolean isConverged) {
		this.isConverged = isConverged;
	}
}
