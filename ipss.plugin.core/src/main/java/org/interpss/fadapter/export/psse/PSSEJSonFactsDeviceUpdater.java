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
public class PSSEJSonFactsDeviceUpdater extends BasePSSEJSonUpdater{	
	// the PSSE gen json object
	private PSSESchema.Facts facts;
	
	/**
	 * Constructor
	 * 
	 * @param fieldDef field name definitions
	 * @param aclfNet  the AclfNetwork object
	 */
	public PSSEJSonFactsDeviceUpdater(PSSESchema.Facts facts, BaseAclfNetwork<?,?> aclfNet) {
		super(facts.getFields(), aclfNet, (lst) -> {
			// fields appended at the end of the list
		});
		this.facts = facts;
	}
	
	/**
	 * filter the data based on the given bus id set
	 * 
	 * @param busIdSet  the bus id set to keep
	 */
	public void filter(Set<String> busIdSet) {
		facts.getData().removeIf(data -> {
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
          "fields":["name", "ibus", "jbus", "mode", "pdes", "qdes", "vset", "shmx", "trmx", 
          			"vtmn", "vtmx", "vsmx", "imx", "linx", "rmpct", "owner", "set1", "set2", 
          			"vsref", "fcreg", "nreg", "mname"], 
          "data"  :["FACTS_DVCE_1", 153, 0, 1, 0.000000, 0.000000, 1.015000, 50.00000, 100.0000, 
          			0.9263000, 1.134000, 1.000000, 0.000000, 0.5652000E-01, 100.0000, 1, 0.000000, 
          			0.000000, 0, 153, 0, ""], 
                */
	}
}