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
public class PSSEJSonDc2TLCCUpdater extends BasePSSEJSonUpdater{	
	// the PSSE LCC 2T Dcline json object
	private PSSESchema.Twotermdc dc2TLcc;
	
	/**
	 * Constructor
	 * 
	 * @param fieldDef field name definitions
	 * @param aclfNet  the AclfNetwork object
	 */
	public PSSEJSonDc2TLCCUpdater(PSSESchema.Twotermdc dc2TLcc, BaseAclfNetwork<?,?> aclfNet) {
		super(dc2TLcc.getFields(), aclfNet, (lst) -> {
			// fields appended at the end of the list
		});
		this.dc2TLcc = dc2TLcc;
	}
	
	/**
	 * filter the data based on the given bus id set
	 * 
	 * @param busIdSet  the bus id set to keep
	 */
	public void filter(Set<String> busIdSet) {
		dc2TLcc.getData().removeIf(data -> {
	 		   @SuppressWarnings("unchecked")
			   List<Object> lst = (List<Object>)data;
	 		   String fid = getBusIdFromDataList(lst, "ipr");
	 		   String tid = getBusIdFromDataList(lst, "ipi");
	 		   return !busIdSet.contains(fid) || !busIdSet.contains(tid);
	 		});
	}
	
	/**
	 * update the data list based on the AclfNetwork simulation results 
	 */
	public void update() {
		/*
		"fields":["name", "mdc", "rdc", "setvl", "vschd", "vcmod", "rcomp", "delti", "met", "dcvmin", 
					"cccitmx", "cccacc", "ipr", "nbr", "anmxr", "anmnr", "rcr", "xcr", "ebasr", "trr", 
					"tapr", "tmxr", "tmnr", "stpr", "icr", "ndr", "ifr", "itr", "idr", "xcapr", "ipi", 
					"nbi", "anmxi", "anmni", "rci", "xci", "ebasi", "tri", "tapi", "tmxi", "tmni", 
					"stpi", "ici", "ndi", "ifi", "iti", "idi", "xcapi"], 
        "data":  ["TWO_TERM_DC1", 1, 7.854300, 1490.650, 525.0000, 400.0000, 3.942000, 0.1556000, "I", 0.000000, 20, 1.000000, 301, 2, 13.00000, 7.500000, 0.1110000E-01, 3.880000, 500.0000, 0.4400000, 1.062750, 1.100000, 0.9000000, 0.5250000E-02, 0, 0, 0, 0, "1", 2.003400, 3021, 2, 21.00000, 18.50000, 0.000000, 3.047000, 230.0000, 0.9565200, 1.075000, 1.100000, 0.8000000, 0.6250000E-02, 0, 0, 152, 3021, "T4", 2.000000], 
	 */	
	}
}