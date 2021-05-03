package org.interpss.pssl.plugin.cmd.json;

import com.interpss.dstab.devent.DynamicSimuEventType;

public class DynamicsEventConfigBean extends BaseJSONBean{
	
	public DynamicSimuEventType eventType = null;
	
	public String eventLocation ="";
	
	public double eventValue = 0.0;
	
	public double eventStartTime = 0.0;
	
	public double eventDuration= 0.0;

}
