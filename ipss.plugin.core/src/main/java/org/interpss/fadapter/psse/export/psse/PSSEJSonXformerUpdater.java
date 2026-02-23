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
public class PSSEJSonXformerUpdater extends BasePSSEJSonUpdater{	
	// the PSSE xformer json object
	private PSSESchema.Transformer xformer;
	
	/**
	 * Constructor
	 * 
	 * @param fieldDef field name definitions
	 * @param aclfNet  the AclfNetwork object
	 */
	public PSSEJSonXformerUpdater(PSSESchema.Transformer xformer, BaseAclfNetwork<?,?> aclfNet) {
		super(xformer.getFields(), aclfNet, (lst) -> {
			// fields appended at the end of the list
		});
		this.xformer = xformer;
	}
	
	/**
	 * filter the data based on the given bus id set
	 * 
	 * @param busIdSet  the bus id set to keep
	 */
	public void filter(Set<String> busIdSet) {
		xformer.getData().removeIf(data -> {
	 		   @SuppressWarnings("unchecked")
			   List<Object> lst = (List<Object>)data;
	 		   String fid = getBusIdFromDataList(lst, "ibus");
	 		   String tid = getBusIdFromDataList(lst, "jbus");
	 		   String kid = getBusIdFromDataList(lst, "kbus"); 
	 		   return kid.equals("Bus0") ?    // not a 3WXfr
	 				   !busIdSet.contains(fid) || !busIdSet.contains(tid) :
	 				   !busIdSet.contains(fid) || !busIdSet.contains(tid) || !busIdSet.contains(kid);		   
	 		});
	}
	
	/**
	 * update the data list based on the AclfNetwork simulation results 
	 */
	public void update() {
		/*
        "fields":["ibus", "jbus", "kbus", "ckt", "cw", "cz", "cm", "mag1", "mag2", "nmet", 
        		"name", "stat", "o1", "f1", "o2", "f2", "o3", "f3", "o4", "f4", "vecgrp", 
        		"zcod", "r1_2", "x1_2", "sbase1_2", "r2_3", "x2_3", "sbase2_3", "r3_1", "x3_1", 
        		"sbase3_1", "vmstar", "anstar", "windv1", "nomv1", "ang1", "wdg1rate1", "wdg1rate2", 
        		"wdg1rate3", "wdg1rate4", "wdg1rate5", "wdg1rate6", "wdg1rate7", "wdg1rate8", 
        		"wdg1rate9", "wdg1rate10", "wdg1rate11", "wdg1rate12", "cod1", "cont1", "node1", 
        		"rma1", "rmi1", "vma1", "vmi1", "ntp1", "tab1", "cr1", "cx1", "cnxa1", "windv2", 
        		"nomv2", "ang2", "wdg2rate1", "wdg2rate2", "wdg2rate3", "wdg2rate4", "wdg2rate5", 
        		"wdg2rate6", "wdg2rate7", "wdg2rate8", "wdg2rate9", "wdg2rate10", "wdg2rate11", 
        		"wdg2rate12", "cod2", "cont2", "node2", "rma2", "rmi2", "vma2", "vmi2", "ntp2", 
        		"tab2", "cr2", "cx2", "cnxa2", "windv3", "nomv3", "ang3", "wdg3rate1", "wdg3rate2", 
        		"wdg3rate3", "wdg3rate4", "wdg3rate5", "wdg3rate6", "wdg3rate7", "wdg3rate8", "wdg3rate9", 
        		"wdg3rate10", "wdg3rate11", "wdg3rate12", "cod3", "cont3", "node3", "rma3", "rmi3", "vma3", 
        		"vmi3", "ntp3", "tab3", "cr3", "cx3", "cnxa3"], 
        "data": [1, 4, 0, "1", 1, 1, 1, 0.000000, 0.000000, 2, "GEN1_TO_BUS1_CIRID_1", 1, 1, 1.000000, 
        		0, 1.000000, 0, 1.000000, 0, 1.000000, "", null, 0.000000, 0.5670000E-01, 100.0000, null, 
        		null, null, null, null, null, null, null, 1.000000, 16.50000, 0.000000, 0.000000, 0.000000, 
        		0.000000, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000, 0.000000, 
        		0.000000, 0, 0, 0, 1.100000, 0.9000000, 1.100000, 0.9000000, 33, 0, 0.000000, 0.000000, 
        		0.000000, 1.000000, 230.0000, null, null, null, null, null, null, null, null, null, null, 
        		null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, 
        		null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, 
        		null, null, null, null, null, null, null, null, null, null, null, null], 
	 */	
	}
}