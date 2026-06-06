package org.interpss.plugin.optadj.result;

import org.interpss.numeric.datatype.LimitType;
/** 

*/
public class OptAdjResultContainer extends SsaResultContainer {	

	/** 
	 * Generator dispatch adjustment applied to the DCLF model (MW and per-unit). 
	 */
	public record GenAdjustResult(double genP, double adjP, LimitType genLimit) {
		public String toString() {
			return String.format("genP: %.2f, asjP: %.2f, tolP: %.2f, genLimit: %s", genP, adjP, genP+adjP, genLimit);
		}
	}
	
	public OptAdjResultContainer() {
		super();
	}
}
