package org.interpss.pssl.plugin.cmd.json;

import org.ieee.odm.schema.LfMethodEnumType;

public class AclfRunConfigBean extends BaseJSONBean {
	public LfMethodEnumType lfMethod = LfMethodEnumType.NR;
	
	public int maxIteration = 20;
	
	public double tolerance = 0.0001;
	
	public boolean nonDivergent = false;
	
	public boolean initBusVoltage = false;
	
	public double accFactor = 1.0;
}
