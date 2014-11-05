package org.interpss.pssl.plugin.cmd.json;

import org.interpss.datamodel.bean.datatype.ComplexBean;

import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.acsc.fault.SimpleFaultType;

/**
 * TODO
 * 
 * @author Mike
 *
 */
public class AcscRunConfigBean extends BaseJSONBean {
	/**
	 * default constructor
	 */
	public AcscRunConfigBean() {
		// set the default AclfDslRunner class name
		this.dslRunnerClassName = "org.interpss.pssl.plugin.cmd.AcscDslRunner";
	}
	
	public String seqFilename = "name";
	
	public String acscOutputFilename = "name";
	
	// flag to indicate whether or not the pre-fault bus voltage is based on load flow result?
	public boolean runAclf = false;
	
	public String aclfConfigFilename = "name";
	
	public AclfRunConfigBean runAclfConfig = new AclfRunConfigBean();
	
	public SimpleFaultType type = SimpleFaultType.BUS_FAULT;
	
	public SimpleFaultCode category = SimpleFaultCode.GROUND_3P;

	public ComplexBean zLG = new ComplexBean(),
			           zLL = new ComplexBean();
	
	// Bus fault only
	public String faultBusId = "id";
	
	// Branch fault only
	public String faultBranchFromId = "fBusId";

	public String faultBranchToId = "tBusId";
	
	public String faultBranchCirId = "cirId";
	
	// Distrance is measured starting from the fromBus side
	public double distance = 0.0;
}
