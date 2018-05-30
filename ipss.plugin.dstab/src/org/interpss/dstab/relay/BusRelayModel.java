package org.interpss.dstab.relay;

import java.util.List;

import com.interpss.dstab.device.DynamicBusDevice;
import com.interpss.dstab.relay.RelayModel;

public interface BusRelayModel extends DynamicBusDevice, RelayModel {
	
	//List<Triplet> relaySetPoints = null;
	
	List<Triplet> getRelaySetPoints();
	
    void setRelaySetPoints(List<Triplet> setPointList);
    
   
}

