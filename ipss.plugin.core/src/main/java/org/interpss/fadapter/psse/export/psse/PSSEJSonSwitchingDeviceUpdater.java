package org.interpss.fadapter.psse.export.psse;

import java.util.List;
import java.util.Set;

import org.ieee.odm.adapter.psse.bean.PSSESchema;

import com.interpss.core.aclf.BaseAclfNetwork;

/**
 * PSSE JSon format data updater
 * 
 * @author mzhou
 *
 */
public class PSSEJSonSwitchingDeviceUpdater extends BasePSSEJSonUpdater{	
	// the PSSE acline json object
	private PSSESchema.Sysswd sysswd;
	
	/**
	 * Constructor
	 * 
	 * @param fieldDef field name definitions
	 * @param aclfNet  the AclfNetwork object
	 */
	public PSSEJSonSwitchingDeviceUpdater(PSSESchema.Sysswd sysswd, BaseAclfNetwork<?,?> aclfNet) {
		super(sysswd.getFields(), aclfNet, (lst) -> {
			// fields appended at the end of the list
		});
		this.sysswd = sysswd;
	}
	
	/**
	 * filter the data based on the given bus id set
	 * 
	 * @param busIdSet  the bus id set to keep
	 */
	public void filter(Set<String> busIdSet) {
		sysswd.getData().removeIf(data -> {
	 		   @SuppressWarnings("unchecked")
			   List<Object> lst = (List<Object>)data;
	 		   String fid = getBusIdFromDataList(lst, "ibus");
	 		   String tid = getBusIdFromDataList(lst, "jbus");
	 		   return !busIdSet.contains(fid) || !busIdSet.contains(tid);
	 		});
	}
	
	/**
	 * update the data list based on the AclfNetwork simulation results 
	 */
	public void update() {
		/*
		"fields":["ibus", "jbus", "ckt", "xpu", "rsetnam", "stat", "nstat", "met", "stype", "name"], 
        "data":  [151, 201, "*1", 0.1000000E-03, "SWD_SF6_DOUBLEE_PUFFER", 1, 1, 2, 3, "SWD-DEVICE-REGION-X-SF6"], */	
	}
}