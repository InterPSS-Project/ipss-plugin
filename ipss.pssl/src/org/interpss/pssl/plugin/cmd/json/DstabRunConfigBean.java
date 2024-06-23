package org.interpss.pssl.plugin.cmd.json;

import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.devent.DynamicSimuEventType;

public class DstabRunConfigBean extends BaseJSONBean{
	
	public DstabRunConfigBean(){
	   // set the default AclfDslRunner class name
	   this.dslRunnerClassName = "org.interpss.pssl.plugin.cmd.DStabDslRunner";
	}
    public String dynamicFileName = "";
	
	public String dstabOutputFileName = "";
	
	public AcscRunConfigBean acscConfigBean = new AcscRunConfigBean();
	
	public DynamicSimuMethod dynMethod = DynamicSimuMethod.MODIFIED_EULER;
	
	public double totalSimuTimeSec = 10.0;
	
	public double simuTimeStepSec = 0.005;
	
	public DynamicSimuEventType eventType = null;
	
	public String eventLocation ="";
	
	public double eventValue = 0.0;
	
	public double eventStartTimeSec = 1.0;
	
	public double eventDurationSec = 0.01;
	
	public String referenceGeneratorId ="";
	
	public String[] monitoringBusAry ={};
	
	public String[] monitoringGenAry ={};
	
	public int outputPerNSteps =1;
	
	
	

}
