package org.interpss.plugin.optadj.result;

public enum MStateSsaType {
	BaseNetwork("Base Network"),    		// Run SSA on natural power flow over the base network without static adjustment
	NetOutage("Network Outage"),      		// Run SSA on natural power flow with an additional branch outage over the base network
	Net3WXfrOutage("Three-Winding Transformer Outage");  	// Run SSA on natural power flow with a three-winding transformer outage over the base network
	
	private String name;
	
	private MStateSsaType(String name) {	
		this.name = name;
	}
	
	public String getName() {
		return this.name;
	}

}
