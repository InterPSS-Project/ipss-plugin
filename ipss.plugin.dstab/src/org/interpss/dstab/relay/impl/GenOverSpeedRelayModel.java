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

public class GenOverSpeedRelayModel extends BusRelayModel {

	
	private String genId = "";
	
	
	
	public GenOverSpeedRelayModel() {
		
	}
	
	public GenOverSpeedRelayModel(DStabBus bus, String genId){
		super(bus);
		this.setDStabBus(bus);
		this.genId = genId;
		
	  
		if(bus.getContributeGen(genId)==null) {
				throw new Error("The genId for bus is not valid: "+bus.getId()+","+genId);
			
		}
		
		this.relaySetPoints = new ArrayList<>();
		this.relaySectionActionStatus = new ArrayList<>();
		this.relayTrippedStatus = new ArrayList<>();
		
		
		//add it to the associated bus dynamic device list
		bus.getDynamicBusDeviceList().add(this);
		
		this.extendedDeviceId = "GenOverSpeedRelay_"+bus.getId()+"_"+genId;
		
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
		
		double spd = 1.0;
		if(this.step_flag ==1) {

			 Machine mach= (Machine) ((DStabGen)this.getDStabBus().getContributeGen(genId)).getDynamicGenDevice();
			 if(mach!=null) {
				    //get the speed and compare it with the threshold
			        spd = mach.getSpeed();
		
					for(int i = 0; i<this.relaySetPoints.size();i++){
						
						if(!this.relayTrippedStatus.get(i)) {// only untripped or unactioned sections of the relay will be active
							// Triplet <speed, time, fraction>
							if(this.relaySetPoints.get(i).getValue1()>spd){
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
			 else {
				 throw new Error("The machine of the generator cannot be identified: " +genId );
			 }
				 
			
			if(action(getInternalTime())) {
				reset();
			}
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

		
	

	
}
