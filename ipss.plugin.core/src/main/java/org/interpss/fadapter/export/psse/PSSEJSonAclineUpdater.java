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
public class PSSEJSonAclineUpdater extends BasePSSEJSonUpdater{	
	// the PSSE acline json object
	private PSSESchema.Acline acline;
	
	/**
	 * Constructor
	 * 
	 * @param fieldDef field name definitions
	 * @param aclfNet  the AclfNetwork object
	 */
	public PSSEJSonAclineUpdater(PSSESchema.Acline acline, BaseAclfNetwork<?,?> aclfNet) {
		super(acline.getFields(), aclfNet, (lst) -> {
			// fields appended at the end of the list
		});
		this.acline = acline;
	}
	
	/**
	 * filter the data based on the given bus id set
	 * 
	 * @param busIdSet  the bus id set to keep
	 */
	public void filter(Set<String> busIdSet) {
		acline.getData().removeIf(data -> {
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
			"fields":["ibus", "jbus", "ckt", "rpu", "xpu", "bpu", "name", "rate1", 
					"rate2", "rate3", "rate4", "rate5", "rate6", "rate7", "rate8", "rate9", 
					"rate10", "rate11", "rate12", "gi", "bi", "gj", "bj", "stat", "bp", "met", 
					"len", "o1", "f1", "o2", "f2", "o3", "f3", "o4", "f4"], 
        "data":  [4, 5, "0", 0.1000000E-01, 0.8500000E-01, 0.1760000, "", 0.000000, 0.000000, 
        		0.000000, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000, 
        		0.000000, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000, 1, 0, 2, 0.000000, 
        		1, 1.000000, null, null, null, null, null, null], 
	 */	
	}
}