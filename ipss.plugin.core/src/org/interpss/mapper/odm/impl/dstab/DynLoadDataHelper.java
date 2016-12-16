package org.interpss.mapper.odm.impl.dstab;

import org.ieee.odm.schema.DynamicLoadCMPLDWXmlType;
import org.interpss.dstab.load.DynLoadCMPLDW;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;

public class DynLoadDataHelper {
	
	private BaseDStabBus bus = null;
	private BaseDStabNetwork dynNet = null;
	
	public DynLoadDataHelper() {
	
	}
	
	
	public DynLoadDataHelper(BaseDStabNetwork dstabNet, BaseDStabBus dstabBus) {
		this.dynNet = dstabNet;
		this.bus= dstabBus; 
	}
	
	
	public DynLoadCMPLDW createCMPLDWLoadModel(DynamicLoadCMPLDWXmlType cmpldw, BaseDStabBus dstabBus, String loadId){
		
		//TODO
		return null;
		
		
	}

}
