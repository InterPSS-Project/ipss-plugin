package org.interpss.mapper.odm.impl.dstab;

import org.ieee.odm.schema.DynamicLoadCMPLDWXmlType;
import org.interpss.dstab.load.DynLoadCMPLDW;

import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabilityNetwork;

public class DynLoadDataHelper {
	
	private DStabBus bus = null;
	private DStabilityNetwork dynNet = null;
	
	public DynLoadDataHelper() {
	
	}
	
	
	public DynLoadDataHelper(DStabilityNetwork dstabNet, DStabBus dstabBus) {
		this.dynNet = dstabNet;
		this.bus= dstabBus; 
	}
	
	
	public DynLoadCMPLDW createCMPLDWLoadModel(DynamicLoadCMPLDWXmlType cmpldw, DStabBus dstabBus, String loadId){
		
		//TODO
		return null;
		
		
	}

}
