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
public class PSSEJSonDc2TVSCUpdater extends BasePSSEJSonUpdater{	
	// the PSSE VSC 2T DcLine json object
	private PSSESchema.Vscdc dc2TVsc;
	
	/**
	 * Constructor
	 * 
	 * @param fieldDef field name definitions
	 * @param aclfNet  the AclfNetwork object
	 */
	public PSSEJSonDc2TVSCUpdater(PSSESchema.Vscdc dc2TVsc, BaseAclfNetwork<?,?> aclfNet) {
		super(dc2TVsc.getFields(), aclfNet, (lst) -> {
			// fields appended at the end of the list
		});
		this.dc2TVsc = dc2TVsc;
	}
	
	/**
	 * filter the data based on the given bus id set
	 * 
	 * @param busIdSet  the bus id set to keep
	 */
	public void filter(Set<String> busIdSet) {
		dc2TVsc.getData().removeIf(data -> {
	 		   @SuppressWarnings("unchecked")
			   List<Object> lst = (List<Object>)data;
	 		   String fid = getBusIdFromDataList(lst, "ibus1");
	 		   String tid = getBusIdFromDataList(lst, "ibus2");
	 		   return !busIdSet.contains(fid) || !busIdSet.contains(tid);
	 		});
	}
	
	/**
	 * update the data list based on the AclfNetwork simulation results 
	 */
	public void update() {
		/*
		"fields":["name", "mdc", "rdc", "o1", "f1", "o2", "f2", "o3", "f3", "o4", "f4", 
					"ibus1", "type1", "mode1", "dcset1", "acset1", "aloss1", "bloss1", "minloss1", "smax1", 
					"imax1", "pwf1", "maxq1", "minq1", "vsreg1", "nreg1", "rmpct1", 
					"ibus2", "type2", "mode2", "dcset2", "acset2", "aloss2", "bloss2", "minloss2", "smax2", 
					"imax2", "pwf2", "maxq2", "minq2", "vsreg2", "nreg2", "rmpct2"], 
        "data":  ["VDCLINE1", 1, 0.7100000, 1, 0.3204000, 2, 0.3883000, 3, 0.1942000, 4, 0.9710000E-01, 3005, 2, 2, -209.0000, 0.9500000, 100.0000, 0.1000000, 50.00000, 400.0000, 1200.000, 0.1000000, 100.0000, -110.0000, 3005, 0, 100.0000, 3008, 1, 1, 100.0000, 0.9900000, 90.00000, 0.1500000, 40.00000, 350.0000, 1200.000, 0.1500000, 150.0000, -140.0000, 3008, 0, 100.0000], 
                 
	 */	
	}
}