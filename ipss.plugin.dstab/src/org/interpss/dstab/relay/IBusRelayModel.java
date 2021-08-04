package org.interpss.dstab.relay;

import java.util.List;

import org.interpss.numeric.datatype.Triplet;

import com.interpss.dstab.device.DynamicBusDevice;
import com.interpss.dstab.relay.IRelayModel;

public interface IBusRelayModel extends DynamicBusDevice, IRelayModel {
	
	/**
	 * Triplet: voltage/frequency, time, fraction
	 * @return
	 */
	List<Triplet> getRelaySetPoints();
	
    void setRelaySetPoints(List<Triplet> setPointList);
    
    List<Integer> getUnderOverFlagList();
    
    
    /**
     * For keeping track of the status of the corresponding sections.
     * @return
     */
    List<Boolean> isRelayTripped();
    
    double getBreakerTime();
    
    void setBreakerTime(double TBreaker);
    
    double getTrippedFraction();
    
    double getInternalTime();
}

