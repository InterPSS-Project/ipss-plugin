package org.interpss.pssl.plugin.cmd.json;

import org.ieee.odm.schema.LfMethodEnumType;
import org.interpss.pssl.plugin.IpssAdapter;

public class AclfRunConfigBean extends BaseJSONBean {
	public String aclfCaseFileName = "name";

	public String aclfOutputFileName = "name";

	public IpssAdapter.FileFormat format = IpssAdapter.FileFormat.IEEECommonFormat;
	
	public IpssAdapter.PsseVersion version = IpssAdapter.PsseVersion.PSSE_30;
	
	public LfMethodEnumType lfMethod = LfMethodEnumType.NR;
	
	public int maxIteration = 20;
	
	public double tolerance = 0.0001;
	
	public boolean nonDivergent = false;
	
	public boolean initBusVoltage = false;
	
	public double accFactor = 1.0;
}
