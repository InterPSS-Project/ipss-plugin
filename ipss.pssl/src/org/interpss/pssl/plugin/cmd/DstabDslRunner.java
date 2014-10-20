package org.interpss.pssl.plugin.cmd;

import org.interpss.pssl.plugin.cmd.json.DstabRunConfigBean;
import org.interpss.pssl.simu.IpssDStab;

import com.interpss.core.acsc.AcscNetwork;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.common.IDStabSimuOutputHandler;
import com.interpss.dstab.devent.DynamicEvent;

public class DstabDslRunner {
	
	private DStabilityNetwork net;
	
	/**
	 * constructor
	 * 
	 * @param net DStabilityNetwork object
	 */
	public DstabDslRunner(DStabilityNetwork net) {
		this.net = net;
	}
	
	public IDStabSimuOutputHandler runDstab (DstabRunConfigBean dstabBean){
		
		IpssDStab dstabDSL = new IpssDStab(net);
		
		
		
		dstabDSL.setTotalSimuTimeSec(dstabBean.totalSimuTimeSec)
		        .setSimuTimeStep(dstabBean.simuTimeStepSec)
		        .setIntegrationMethod(dstabBean.method);
		
		
		StateMonitor sm = new StateMonitor();
		sm.addBusStdMonitor(dstabBean.monitoringBusAry);
		sm.addGeneratorStdMonitor(dstabBean.monitoringGenAry);
		
		// set the output handler
		dstabDSL.setDynSimuOutputHandler(sm)
		        .setSimuOutputPerNSteps(dstabBean.outputPerNSteps);
		
		dstabDSL.addDynamicEvent(dstabDSL.createBusFaultEvent(dstabBean.acscConfigBean.faultBusId,  
				                                              dstabBean.acscConfigBean.category, 
											                  dstabBean.eventStartTimeSec,
											                  dstabBean.eventDurationSec, 
											                  dstabBean.acscConfigBean.zLG.toComplex(), 
											                  dstabBean.acscConfigBean.zLL.toComplex()), 
				                    "BusFault@"+dstabBean.acscConfigBean.faultBusId);
		
		if(dstabDSL.initialize()){
			if( dstabDSL.runDStab())
				return dstabDSL.getOutputHandler();
		}
		
		return null;
	}
	

}
