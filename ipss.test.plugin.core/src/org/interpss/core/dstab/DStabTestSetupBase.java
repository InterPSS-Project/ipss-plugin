 /*
  * @(#)TestSetupBase.java   
  *
  * Copyright (C) 2006 www.interpss.org
  *
  * This program is free software; you can redistribute it and/or
  * modify it under the terms of the GNU LESSER GENERAL PUBLIC LICENSE
  * as published by the Free Software Foundation; either version 2.1
  * of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  * GNU General Public License for more details.
  *
  * @Author Mike Zhou
  * @Version 1.0
  * @Date 09/15/2006
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.core.dstab;

import java.util.logging.Level;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.numeric.NumericConstant;

import com.interpss.CoreCommonFactory;
import com.interpss.DStabObjectFactory;
import com.interpss.common.msg.IPSSMsgHub;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.acsc.fault.AcscBusFault;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.devent.DynamicSimuEvent;
import com.interpss.dstab.devent.DynamicSimuEventType;

public class DStabTestSetupBase extends CorePluginTestSetup {
	
	protected IPSSMsgHub msg;

	public DStabTestSetupBase() { 
		msg = CoreCommonFactory.getIpssMsgHub();
		IpssLogger.getLogger().setLevel(Level.WARNING);
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

		AcscBusFault fault = CoreObjectFactory.createAcscBusFault("Bus Fault 3P@0003", net);
  		fault.setBus(faultBus);
		fault.setFaultCode(SimpleFaultCode.GROUND_3P);
		fault.setZLGFault(NumericConstant.SmallScZ);
		fault.setZLLFault(new Complex(0.0, 0.0));
		event1.setBusFault(fault);		
	}	
}

