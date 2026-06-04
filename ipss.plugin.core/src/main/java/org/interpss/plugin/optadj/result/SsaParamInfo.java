package org.interpss.plugin.optadj.result;

/** 
* SSA input parameter info
*/
public class SsaParamInfo {
	// SSA analysis type [ BaseNetwork("Base Network"), NetOutage("Network Outage") ]
	private SsaType ssaType;
	
	// LODF threshold for N-1 scan calculation
	private double lodfThreshold = 0.2; 
	
	// outage branch name, for the ssaType = NetOutage scenario
	private String outBranchName;
	
	public SsaParamInfo(double lodfThreshold) {
		this.ssaType = SsaType.BaseNetwork;
		this.lodfThreshold = lodfThreshold;
	}
	
	public SsaParamInfo(SsaType ssaType, String outBranchName, double lodfThreshold) {
		this(lodfThreshold);
		this.ssaType = ssaType;
		this.outBranchName = outBranchName;
	}

	public SsaType getSsaType() {
		return ssaType;
	}
	
	public double getLodfThreshold() {
		return lodfThreshold;
	}
	
	public String getOutBranchName() {
		return outBranchName;
	}
}
