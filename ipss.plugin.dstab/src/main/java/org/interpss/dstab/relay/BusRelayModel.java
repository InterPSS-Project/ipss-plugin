package org.interpss.dstab.relay;

import java.util.List;

import org.interpss.numeric.datatype.Triplet;

import com.interpss.dstab.device.DynamicBusDevice;
import com.interpss.dstab.relay.IRelayModel;

public interface BusRelayModel extends DynamicBusDevice, IRelayModel {
	
	List<Triplet> getRelaySetPoints();
	
    void setRelaySetPoints(List<Triplet> setPointList);
}

