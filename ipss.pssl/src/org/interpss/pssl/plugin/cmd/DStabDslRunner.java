package org.interpss.pssl.plugin.cmd;

import org.interpss.pssl.plugin.cmd.json.DstabRunConfigBean;
import org.interpss.pssl.simu.IpssDStab;

import com.interpss.core.acsc.AcscNetwork;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.common.IDStabSimuOutputHandler;
import com.interpss.dstab.devent.DynamicEvent;

public class DStabDslRunner extends BaseDStabDslRunner{
	
    /**
     * default constructor
     */
	public DStabDslRunner(){
		
	}
	
	/**
	 * constructor
	 * 
	 * @param net DStabilityNetwork object
	 */
	public DStabDslRunner(DStabilityNetwork net) {
		this.net = net;
	}
	
	protected IDStabSimuOutputHandler runDstab (DstabRunConfigBean dstabBean){
		
		IpssDStab dstabDSL = new IpssDStab(net);
		
		dstabDSL.setTotalSimuTimeSec(dstabBean.totalSimuTimeSec)
		        .setSimuTimeStep(dstabBean.simuTimeStepSec)
		        .setIntegrationMethod(dstabBean.dynMethod)
		        .setRefMachine(dstabBean.referenceGeneratorId);
		
		
		StateMonitor sm = new StateMonitor();
		sm.addBusStdMonitor(dstabBean.monitoringBusAry);
		sm.addGeneratorStdMonitor(dstabBean.monitoringGenAry);
		
		// set the output handler
		dstabDSL.setDynSimuOutputHandler(sm)
		        .setSimuOutputPerNSteps(dstabBean.outputPerNSteps);
		
		dstabDSL.addBusFaultEvent(dstabBean.acscConfigBean.faultBusId,  
				                                              dstabBean.acscConfigBean.category, 
											                  dstabBean.eventStartTimeSec,
											                  dstabBean.eventDurationSec, 
											                  dstabBean.acscConfigBean.zLG.toComplex(), 
											                  dstabBean.acscConfigBean.zLL.toComplex()); 
				                   
		
		if(dstabDSL.initialize()){
			if( dstabDSL.runDStab())
				return dstabDSL.getOutputHandler();
		}
		
		return null;
	}
	

}
