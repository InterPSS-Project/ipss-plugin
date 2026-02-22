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
public class PSSEJSonGenUpdater extends BasePSSEJSonUpdater{	
	// the PSSE gen json object
	private PSSESchema.Generator gen;
	
	/**
	 * Constructor
	 * 
	 * @param fieldDef field name definitions
	 * @param aclfNet  the AclfNetwork object
	 */
	public PSSEJSonGenUpdater(PSSESchema.Generator gen, BaseAclfNetwork<?,?> aclfNet) {
		super(gen.getFields(), aclfNet, (lst) -> {
			// fields appended at the end of the list
		});
		this.gen = gen;
	}
	
	/**
	 * filter the data based on the given bus id set
	 * 
	 * @param busIdSet  the bus id set to keep
	 */
	public void filter(Set<String> busIdSet) {
		gen.getData().removeIf(data -> {
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
        "fields":["ibus", "machid", "pg", "qg", "qt", "qb", "vs", "ireg", "nreg", "mbase", 
        			"zr", "zx", "rt", "xt", "gtap", "stat", "rmpct", "pt", "pb", "baslod", 
        			"o1", "f1", "o2", "f2", "o3", "f3", "o4", "f4", "wmod", "wpf", "droopname", "name"], 
        "data":  [1, "1", 71.64000, 27.05000, 9999.000, -9999.000, 1.040000, 1, 0, 100.0000,
         			0.000000, 0.4000000E-01, 0.000000, 0.000000, 1.000000, 1, 100.0000, 9999.000, 
         			-9999.000, 0, 1, 1.000000, null, null, null, null, null, null, null, null, null, null], 
		 */
	}
}