package org.interpss.pssl.plugin.cmd.json;

import org.interpss.datamodel.bean.datatype.ComplexBean;

import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.acsc.fault.SimpleFaultType;

public class AcscRunConfigBean extends BaseJSONBean {
	
	public boolean runAclf = false;
	
	public AclfRunConfigBean runAclfConfig = new AclfRunConfigBean();
	
	public SimpleFaultType type = SimpleFaultType.BUS_FAULT;
	
	public SimpleFaultCode category = SimpleFaultCode.GROUND_3P;

	public ComplexBean zLG = new ComplexBean(),
			           zLL = new ComplexBean();
	
	// Bus fault only
	public String faultBusId = "id";
	
	// Branch fault only
	public String faultBranchFromId = "id";

	public String faultBranchToId = "id";

	public double distance = 0.0;
}
