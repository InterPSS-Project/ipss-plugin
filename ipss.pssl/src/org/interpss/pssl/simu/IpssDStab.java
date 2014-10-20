 /*
  * @(#)IpssDStab.java   
  *
  * Copyright (C) 2006-2011 www.interpss.com
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
  * @Date 04/15/2009
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.pssl.simu;

import org.apache.commons.math3.complex.Complex;
import org.interpss.pssl.simu.IpssAclf.LfAlgoDSL;

import com.interpss.CoreObjectFactory;
import com.interpss.DStabObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.acsc.fault.AcscBusFault;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.common.IDStabSimuOutputHandler;
import com.interpss.dstab.devent.DynamicEvent;
import com.interpss.dstab.devent.DynamicEventType;
import com.interpss.spring.CoreCommonSpringFactory;

/**
 * DSL (domain specific language) for dynamic stability analysis
 *  
 * @author mzhou, thuang
 *
 */
public class IpssDStab {
	
	private DynamicSimuAlgorithm dstabAlgo = null;
	private DStabilityNetwork dstabNet = null;
	
	private IDStabSimuOutputHandler outputHdler = new StateMonitor();
	
	private DynamicEvent event = null;
	//private StateMonitor sm = new StateMonitor();
	
	public IpssDStab(DStabilityNetwork net){
		this.dstabNet = net;
		this.dstabAlgo = DStabObjectFactory.createDynamicSimuAlgorithm(net, CoreCommonSpringFactory.getIpssMsgHub());
		this.dstabAlgo.setSimuOutputHandler(outputHdler);
	}
	
    public 	IpssDStab setDynSimuOutputHandler(IDStabSimuOutputHandler outputHandler){
    	this.outputHdler = outputHandler;
    	this.dstabAlgo.setSimuOutputHandler(outputHdler);
    	return this;
    }
    
    public IDStabSimuOutputHandler getOutputHandler(){
    	return this.outputHdler;
    }
    
    public 	IpssDStab setSimuOutputPerNSteps(int n){
    	this.dstabAlgo.setOutPutPerSteps(n);
    	return this;
    }
	
    /**
     * 
     * @param totalTime total simulation time in second
     * @return
     */
    public IpssDStab setTotalSimuTimeSec(double totalTime){
    	this.dstabAlgo.setTotalSimuTimeSec(totalTime);
    	return this;
    }
    
    /**
     * 
     * @param timeStep simulation time step in second
     * @return
     */
    public IpssDStab setSimuTimeStep(double timeStep){
    	this.dstabAlgo.setSimuStepSec(timeStep);
    	return this;
    }
    
    public IpssDStab setIntegrationMethod(DynamicSimuMethod method){
    	this.dstabAlgo.setSimuMethod(method);
    	return this;
    }
    
    public IpssDStab addDynamicEvent(DynamicEvent event, String eventId ){
    	this.event = event;
    	this.dstabNet.addDynamicEvent(event, eventId);
    	return this;
    }
    
    public IpssDStab setRefMachine(String refMachId){
    	if(dstabNet.getMachine("Bus1-mach1")!=null)
            this.dstabAlgo.setRefMachine(dstabNet.getMachine("Bus1-mach1"));
    	else{
    		IpssLogger.getLogger().severe("No machine is found for the input "+ refMachId + ", please check!");
    	}
        return this;
    }
    public   DynamicSimuAlgorithm getDstabAlgo(){
            return  this.dstabAlgo;
    }
    
   
    
    public boolean initialize(){
    	if(!dstabNet.isLfConverged()){
    	   LfAlgoDSL aclfDsl = IpssAclf.createAclfAlgo(dstabNet);
    	   try {
			if(!aclfDsl.runLoadflow()){
				IpssLogger.getLogger().severe("Load flow is not converged, the first stage of Dstabnetwork initializaiton failed!");
				return false;
			}
				
		    } catch (InterpssException e) {
			
			  e.printStackTrace();
		    }
    	}
    	return this.dstabAlgo.initialization();
    	
    }
    
    /**
     * perform simulation till the end of the simulaiton time
     * @return
     */
    public boolean runDStab(){
    	return this.dstabAlgo.performSimulation();
    }
    
    /**
     * perform one step simulation, including network solution and integration.
     * @param updateTime, true for updating the time T_new = T_old +dT (time step)
     * @return
     */
    public boolean runOneStepDStab(boolean updateTime){
    	return this.dstabAlgo.solveDEqnStep(updateTime);
    }
    
    
    public DynamicEvent addBusFaultEvent(String faultBusId, SimpleFaultCode code, double startTime, double durationTime, Complex Zlg, Complex Zll){
	       // define an event, set the event id and event type.
			DynamicEvent event1 = DStabObjectFactory.createDEvent("BusFault_"+code+"@"+faultBusId, "Bus Fault @"+faultBusId, 
					DynamicEventType.BUS_FAULT, dstabNet);
			event1.setStartTimeSec(startTime);
			event1.setDurationSec(durationTime);
			
	      // define a bus fault
			DStabBus faultBus = dstabNet.getDStabBus(faultBusId);
			AcscBusFault fault = CoreObjectFactory.createAcscBusFault("Bus Fault 3P@"+faultBusId, dstabNet);
	  		fault.setBus(faultBus);
			fault.setFaultCode(code);
			fault.setZLGFault(Zlg);
			fault.setZLLFault(Zll);

	      // add this fault to the event, must be consist with event type definition before.
			event1.setBusFault(fault); 
			return event1;
	}
    
    

}
