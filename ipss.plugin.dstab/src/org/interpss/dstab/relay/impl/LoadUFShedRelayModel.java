package org.interpss.dstab.relay.impl;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Triplet;

import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.AclfLoad;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.dstab.device.impl.DynamicBusDeviceImpl;
import com.interpss.dstab.dynLoad.DynLoadModel;

public class LoadUFShedRelayModel extends BusLoadRelayModel {

	
	public LoadUFShedRelayModel(BaseDStabBus bus, String loadId){
        this.setDStabBus(bus);
		
		this.relaySetPoints = new ArrayList<>();
		this.relaySectionActionStatus = new ArrayList<>();
		this.relayTrippedStatus = new ArrayList<>();
		
		this.loadId = loadId;
		
		//check the loadId, use PSS/E naming convention
		if(loadId.equals("*")){
			this.isAllBusLoad = true;
		}
		
		this.extendedDeviceId = "UFLSRelay_"+bus.getId()+"_"+loadId;
		
		
		this.states = new Hashtable<>();
		
		this.states.put(DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID, this.extendedDeviceId);
		
		bus.getDynamicBusDeviceList().add(this);
	}
	
	
	
	@Override
	public boolean updateAttributes(boolean netChange) {

		if(this.step_flag ==1) {
		    double freq = this.getDStabBus().getFreq();
		    
			for(int i = 0; i<this.relaySetPoints.size();i++){
				
				if(!this.relayTrippedStatus.get(i)) {// only untripped or unactioned sections of the relay will be active
					// Triplet <voltage, time, fraction>
					if(this.relaySetPoints.get(i).getValue1()<freq){
						//reset timer
						timerAry[i] = 0.0;
					}
					else{
						//update the timer
						timerAry[i] = timerAry[i] +this.timeStep;
					}
				}
			}
			
			if(action(getInternalTime())) {
				reset();
			}
		}
		
		return true;
	}
	
	
	
}
