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
public class PSSEJSonFixedShuntUpdater extends BasePSSEJSonUpdater{	
	// the PSSE gen json object
	private PSSESchema.Fixshunt fshunt;
	
	/**
	 * Constructor
	 * 
	 * @param fieldDef field name definitions
	 * @param aclfNet  the AclfNetwork object
	 */
	public PSSEJSonFixedShuntUpdater(PSSESchema.Fixshunt fshunt, BaseAclfNetwork<?,?> aclfNet) {
		super(fshunt.getFields(), aclfNet, (lst) -> {
			// fields appended at the end of the list
		});
		this.fshunt = fshunt;
	}
	
	/**
	 * filter the data based on the given bus id set
	 * 
	 * @param busIdSet  the bus id set to keep
	 */
	public void filter(Set<String> busIdSet) {
		fshunt.getData().removeIf(data -> {
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
         "fields":["ibus", "shntid", "stat", "gl", "bl", "name"], 
         "data"  :[151, "F1", 1, 5.000000, -400.0000, "FXSH_200001"], 
        */
	}
}