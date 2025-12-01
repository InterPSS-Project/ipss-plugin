package org.interpss.fadapter.export.psse;

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
public class PSSEJSonLoadUpdater extends BasePSSEJSonUpdater{	
	// the PSSE gen json object
	private PSSESchema.Load load;
	
	/**
	 * Constructor
	 * 
	 * @param fieldDef field name definitions
	 * @param aclfNet  the AclfNetwork object
	 */
	public PSSEJSonLoadUpdater(PSSESchema.Load load, BaseAclfNetwork<?,?> aclfNet) {
		super(load.getFields(), aclfNet, (lst) -> {
			// fields appended at the end of the list
		});
		this.load = load;
	}
	
	/**
	 * filter the data based on the given bus id set
	 * 
	 * @param busIdSet  the bus id set to keep
	 */
	public void filter(Set<String> busIdSet) {
		load.getData().removeIf(data -> {
	 		   @SuppressWarnings("unchecked")
			   List<Object> lst = (List<Object>)data;
	 		   String id = getBusIdFromDataList(lst, "ibus");
	 		   return !busIdSet.contains(id);
	 		});
	}
	
	/**
	 * update the data list based on the AclfNetwork simulation results 
	 */
	public void update() {
		/*
        "fields":["ibus", "loadid", "stat", "area", "zone", "pl", "ql", "ip", "iq", "yp", "yq", 
        			"owner", "scale", "intrpt", "dgenp", "dgenq", "dgenm", "loadtype", "name"], 
         "data": [5, "1", 1, 1, 1, 125.0000, 50.00000, 0.000000, 0.000000, 0.000000, 0.000000, 
         		1, 1, 0, 0.000000, 0.000000, 0, "", ""], 
            
		 */
	}
}