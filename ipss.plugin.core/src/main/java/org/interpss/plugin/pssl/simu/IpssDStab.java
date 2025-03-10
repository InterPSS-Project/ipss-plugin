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

package org.interpss.plugin.pssl.simu;

import org.apache.commons.math3.complex.Complex;
import org.interpss.plugin.pssl.simu.IpssAclf.LfAlgoDSL;

import com.interpss.common.CoreCommonFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.acsc.fault.AcscBusFault;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.common.IDStabSimuOutputHandler;
import com.interpss.dstab.devent.DynamicSimuEvent;
import com.interpss.dstab.devent.DynamicSimuEventType;

/**
 * DSL (domain specific language) for dynamic stability analysis
 *  
 * @author mzhou, thuang
 *
 */
public class IpssDStab {
	
	private DynamicSimuAlgorithm dstabAlgo = null;
	private BaseDStabNetwork dstabNet = null;
	
	private IDStabSimuOutputHandler outputHdler = new StateMonitor();
	
	private DynamicSimuEvent event = null;
	//private StateMonitor sm = new StateMonitor();
	
	public IpssDStab(BaseDStabNetwork net){
		this.dstabNet = net;
		this.dstabAlgo = DStabObjectFactory.createDynamicSimuAlgorithm(net, CoreCommonFactory.getIpssMsgHub());
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
    	this.dstabAlgo.setOutputPerSteps(n);
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
    
    public IpssDStab addDynamicEvent(DynamicSimuEvent event, String eventId ){
    	this.event = event;
    	this.dstabNet.addDynamicEvent(event, eventId);
    	return this;
    }
    
    public IpssDStab setRefMachine(String refMachId){
    	if(dstabNet.getMachine(refMachId)!=null)
            this.dstabAlgo.setRefMachine(dstabNet.getMachine(refMachId));
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
     * perform simulation till the end of the simulation time
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
    
    
    public DynamicSimuEvent addBusFaultEvent(String faultBusId, SimpleFaultCode code, double startTime, double durationTime, Complex Zlg, Complex Zll){
	       // define an event, set the event id and event type.
			DynamicSimuEvent event1 = DStabObjectFactory.createDEvent("BusFault_"+code+"@"+faultBusId, "Bus Fault @"+faultBusId, 
					DynamicSimuEventType.BUS_FAULT, dstabNet);
			event1.setStartTimeSec(startTime);
			event1.setDurationSec(durationTime);
			
	      // define a bus fault
			BaseDStabBus<?,?> faultBus = dstabNet.getDStabBus(faultBusId);
			AcscBusFault fault = CoreObjectFactory.createAcscBusFault("Bus Fault 3P@"+faultBusId, dstabNet, true /* cacheBusScVolt */);
	  		fault.setBus(faultBus);
			fault.setFaultCode(code);
			fault.setZLGFault(Zlg);
			fault.setZLLFault(Zll);

	      // add this fault to the event, must be consist with event type definition before.
			event1.setBusFault(fault); 
			return event1;
	}
    
    

}
