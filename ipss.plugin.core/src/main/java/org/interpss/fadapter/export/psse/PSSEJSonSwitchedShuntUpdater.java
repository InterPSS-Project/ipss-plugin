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
public class PSSEJSonSwitchedShuntUpdater extends BasePSSEJSonUpdater{	
	// the PSSE gen json object
	private PSSESchema.Swshunt swshunt;
	
	/**
	 * Constructor
	 * 
	 * @param fieldDef field name definitions
	 * @param aclfNet  the AclfNetwork object
	 */
	public PSSEJSonSwitchedShuntUpdater(PSSESchema.Swshunt swshunt, BaseAclfNetwork<?,?> aclfNet) {
		super(swshunt.getFields(), aclfNet, (lst) -> {
			// fields appended at the end of the list
		});
		this.swshunt = swshunt;
	}
	
	/**
	 * filter the data based on the given bus id set
	 * 
	 * @param busIdSet  the bus id set to keep
	 */
	public void filter(Set<String> busIdSet) {
		swshunt.getData().removeIf(data -> {
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
         "fields":["ibus", "shntid", "modsw", "adjm", "stat", "vswhi", "vswlo", "swreg", "nreg", 
         			"rmpct", "rmidnt", "binit", "name", "s1", "n1", "b1", "s2", "n2", "b2", "s3", 
         			"n3", "b3", "s4", "n4", "b4", "s5", "n5", "b5", "s6", "n6", "b6", "s7", "n7", 
         			"b7", "s8", "n8", "b8"], 
         "data":  [152, "1", 1, 0, 1, 1.045000, 0.9550000, 152, 0, 100.0000, "", -115.0000, "", 
         			1, 1, -15.00000, 1, 2, -5.000000, 1, 3, -10.00000, 1, 4, -8.000000, 1, 5, -7.000000, 
         			1, 6, -5.000000, 1, 7, -7.000000, 1, 8, -4.000000], 
                */
	}
}