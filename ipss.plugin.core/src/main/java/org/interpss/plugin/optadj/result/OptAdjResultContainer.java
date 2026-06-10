package org.interpss.plugin.optadj.result;

import java.util.Map;

import org.interpss.numeric.datatype.LimitType;
/** 

*/
public class OptAdjResultContainer extends SsaResultContainer {	
	private double optAdjThreshold;

	private Map<String, GenAdjustResult> optAdjResults;

	/** 
	 * Generator dispatch adjustment applied to the DCLF model (MW and per-unit). 
	 */
	public record GenAdjustResult(double genP, double adjP, LimitType genLimit) {
		public String toString() {
			return String.format("genP: %.2f, asjP: %.2f, tolP: %.2f, genLimit: %s", genP, adjP, genP+adjP, genLimit);
		}
	}
	
	public OptAdjResultContainer(double optAdjThreshold) {
		super();
		this.optAdjThreshold = optAdjThreshold;
	}

	public OptAdjResultContainer(SsaResultContainer ssaResult) {
		this(100.0	);
		this.setBaseLoadingThreshold(ssaResult.getBaseLoadingThreshold());
		this.setCaLoadingThreshold(ssaResult.getCaLoadingThreshold());
		this.baseOverLimitInfo = ssaResult.getBaseOverLimitInfo();
		this.caOverLimitInfo = ssaResult.getCaOverLimitInfo();
	}

	public double getOptAdjThreshold() {
		return optAdjThreshold;
	}

	public void setOptAdjThreshold(double optAdjThreshold) {
		this.optAdjThreshold = optAdjThreshold;
	}

	public Map<String, GenAdjustResult> getOptAdjResults() {
		return optAdjResults;
	}

	public void setOptAdjResults(Map<String, GenAdjustResult> results) {
		this.optAdjResults = results;
	}
}
