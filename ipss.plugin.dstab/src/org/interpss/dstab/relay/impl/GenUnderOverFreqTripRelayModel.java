package org.interpss.dstab.relay.impl;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.relay.IBusRelayModel;
import org.interpss.numeric.datatype.Triplet;

import com.interpss.DStabObjectFactory;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfLoad;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.dstab.device.impl.DynamicBusDeviceImpl;
import com.interpss.dstab.dynLoad.DynLoadModel;
import com.interpss.dstab.mach.Machine;

public class GenUnderOverFreqTripRelayModel extends BusRelayModel {

	
	private String genId = "";
	private String freqBusId="";
	private BaseDStabBus freqMonitorBus = null;
	
	
	
	public GenUnderOverFreqTripRelayModel() {
		
	}
	
	public GenUnderOverFreqTripRelayModel(BaseDStabBus bus, String genId, String freqBusId){
		super(bus);
		this.setDStabBus(bus);
		this.genId = genId;
		
		if(freqBusId!=null) {
			freqMonitorBus = (BaseDStabBus) this.getDStabBus().getNetwork().getBus(freqBusId);
		}
		
	  
		if(bus.getContributeGen(genId)==null) {
				throw new Error("The genId for bus is not valid: "+bus.getId()+","+genId);
		}
		
		this.relaySetPoints = new ArrayList<>();
		this.relaySectionActionStatus = new ArrayList<>();
		this.relayTrippedStatus = new ArrayList<>();
		this.underOverFlagList = new ArrayList<>();
		
		
		//add it to the associated bus dynamic device list
		bus.getDynamicBusDeviceList().add(this);
		
		this.extendedDeviceId = "GenUFTripRelay_"+bus.getId()+"_"+genId;
		
		this.states = new Hashtable<>();
		
		this.states.put(DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID, this.extendedDeviceId);
	}


	@Override
	public boolean action(double time) {
		if(isActionTime(time)){ 
		    applyGenTrippingAction();
		    IpssLogger.getLogger().info(this.extendedDeviceId+ " is activated at time = "+time);
		    this.isRelayTripped = false;
		    return true;
		}
		return false;
	}
	
	
	@Override
	public boolean initStates(BaseDStabBus<?,?> abus){
		
		// check the relaySetPoints 
		if (this.relaySetPoints.size()==0){
			IpssLogger.getLogger().severe("No relay SetPoint is defined");
			return false;
		}
	
		
		
		//initialize the timers
		this.timerAry = new double[this.relaySetPoints.size()];
		for(int i = 0; i<this.relaySetPoints.size();i++){
			timerAry[i] = 0.0;
			this.relaySectionActionStatus.add(false);
			this.relayTrippedStatus.add(false);
		}
		
		return true;
	}
	
	@Override
	public boolean updateAttributes(boolean netChange) {
		
		double freq = 1.0;
		double baseFreq = this.getDStabBus().getNetwork().getFrequency();
		if(this.step_flag ==1) {
			
			   if(this.freqMonitorBus!=null) 
				   freq = this.freqMonitorBus.getFreq();
			   else
				   freq = this.getDStabBus().getFreq();
			
			   freq = freq*baseFreq;
				 
					for(int i = 0; i<this.relaySetPoints.size();i++){
						
						if(!this.relayTrippedStatus.get(i)) {// only untripped or unactioned sections of the relay will be active
							// Triplet <speed, time, fraction>
							if(this.underOverFlagList.get(i)<=0) { // this setting point is for under-freq
								if(this.relaySetPoints.get(i).getValue1()<freq){
									//reset timer
									timerAry[i] = 0.0;
								}
								else{
									//update the timer
									timerAry[i] = timerAry[i] +this.timeStep;
								}
							}
							else if(this.underOverFlagList.get(i)>0) { // this setting point is for over-freq
								if(this.relaySetPoints.get(i).getValue1()>freq){
									//reset timer
									timerAry[i] = 0.0;
								}
								else{
									//update the timer
									timerAry[i] = timerAry[i] +this.timeStep;
								}
							}
						}
					}
	        }
				 
			
			if(action(getInternalTime())) {
				reset();
			}
		
		
		return true;
	}
	
	public Hashtable<String, Object> getStates(Object ref) {
		states.put(OUT_SYMBOL_STATUS, this.isRelayTripped);
		return states;
	}
	
	private void applyGenTrippingAction() {
		
		    BaseDStabNetwork dsNet =  (BaseDStabNetwork) this.getDStabBus().getNetwork();
		   
			for(int i = 0; i<this.relaySetPoints.size();i++){
					if(this.relaySectionActionStatus.get(i)){ // check the trip action status
						
			             String eventId = String.format("%s is tripped at time = %f", this.extendedDeviceId,this.getInternalTime());
					     dsNet.addDynamicEvent(DStabObjectFactory.createGeneratorTripEvent(this.getDStabBus().getId(), this.genId, dsNet, this.getInternalTime()),eventId);
				    
						 this.trippedFraction = 1.0; // trip the whole unit
						 
						 this.relayTrippedStatus.set(i, true);
						 
						 //reset the timer
						 this.timerAry[i] = 0.0;
						
						 
				}
		    }
		   
			
	}
	
	public void setFreqMonitorBusId(String freqMonBusId) {
		this.freqBusId = freqMonBusId;
	}

		
	

	
}
