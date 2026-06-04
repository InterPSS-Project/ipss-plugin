package org.interpss.plugin.optadj.result;

public enum SsaType {
	BaseNetwork("Base Network"),    		// Run SSA on natural power flow over the base network without static adjustment
	NetOutage("Network Outage"),      		// Run SSA on natural power flow with an additional branch outage over the base network
	Net3WXfrOutage("3WXfr Outage");  	// Run SSA on natural power flow with a three-winding transformer outage over the base network
	
	private String name;
	
	private SsaType(String name) {	
		this.name = name;
	}
	
	public String getName() {
		return this.name;
	}

}
