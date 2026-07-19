package org.interpss.core.dstab;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.numeric.NumericConstant;

import com.interpss.common.CoreCommonFactory;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.acsc.fault.AcscBusFault;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.devent.DynamicSimuEvent;
import com.interpss.dstab.devent.DynamicSimuEventType;

public class DStabTestSetupBase extends CorePluginTestSetup {
	

	public DStabTestSetupBase() { 
 	}
/*
	public DynamicSimuAlgorithm createDStabAlgo(DStabilityNetwork net) {
		DynamicSimuAlgorithm algo = DStabObjectFactory.createDynamicSimuAlgorithm(net, 
				new DatabaseSimuOutputHandler(), msg);
		algo.setSimuStepSec(0.002);
		algo.setTotalSimuTimeSec(10.0);
		
		Machine mach = net.getMachine("Mach@0003");
		algo.setRefMachine(mach);	
		return algo;
	}
*/	
	public void addDynamicEventData(BaseDStabNetwork net) {
		// define a bus fault event
		DynamicSimuEvent event1 = DStabObjectFactory.createDEvent("BusFault3P@0003", "Bus Fault 3P@0003", 
				DynamicSimuEventType.BUS_FAULT, net);
		event1.setStartTimeSec(1.0);
		event1.setDurationSec(0.1);
		
		BaseDStabBus faultBus = net.getDStabBus("0003");

		AcscBusFault fault = CoreObjectFactory.createAcscBusFault("Bus Fault 3P@0003", net, true /* cacheBusScVolt */);
  		fault.setBus(faultBus);
		fault.setFaultCode(SimpleFaultCode.GROUND_3P);
		fault.setZLGFault(NumericConstant.SmallScZ);
		fault.setZLLFault(new Complex(0.0, 0.0));
		event1.setBusFault(fault);		
	}	
}

