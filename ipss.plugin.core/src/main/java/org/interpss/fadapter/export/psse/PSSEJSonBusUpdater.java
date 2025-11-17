package org.interpss.fadapter.export.psse;

import java.util.List;
import java.util.Set;

import org.ieee.odm.adapter.psse.bean.PSSESchema;
import org.ieee.odm.model.IODMModelParser;
import org.interpss.numeric.datatype.Unit.UnitType;

import com.interpss.core.aclf.BaseAclfNetwork;

/**
 * PSSE JSon format data updater
 * 
 * @author mzhou
 *
 */
public class PSSEJSonBusUpdater extends BasePSSEJSonUpdater{	
	// the PSSE bus json object
	private PSSESchema.Bus bus;
	
	/**
	 * Constructor
	 * 
	 * @param fieldDef field name definitions
	 * @param aclfNet  the AclfNetwork object
	 */
	public PSSEJSonBusUpdater(PSSESchema.Bus bus, BaseAclfNetwork<?,?> aclfNet) {
		super(bus.getFields(), aclfNet, (lst) -> {
			/*
			// fields appended at the end of the list
			lst.add("vm1");
			lst.add("va1");
			*/
		});
		this.bus = bus;
	}
	
	/**
	 * filter the bus data based on the given bus id set
	 * 
	 * @param busIdSet  the bus id set to keep
	 */
	public void filter(Set<String> busIdSet) {
		bus.getData().removeIf(data -> {
 		   @SuppressWarnings("unchecked")
		   List<Object> lst = (List<Object>)data;
 		   String id = getBusIdFromDataList(lst);
 		   return !busIdSet.contains(id);
 		});
	}
	
	private String getBusIdFromDataList(List<Object> dataList) {
	   int idIdx = this.positionTable.get("ibus");
	   String id = IODMModelParser.BusIdPreFix+((Double)dataList.get(idIdx)).intValue();
	   return id;
	}
	
	/**
	 * update the data list based on the AclfNetwork simulation results 
	 */
	public void update() {
		/*
 		"fields":["ibus", "name", "baskv", "ide", "area", "zone", "owner", "vm", "va", "nvhi", 	
 					"nvlo", "evhi", "evlo"], 
        "data":  [1, "BUS-1", 16.50000, 3, 1, 1, 1, 1.040000, 0.000000, 1.100000, 0.9000000, 
        			1.100000, 0.9000000], 
		 */		
 		bus.getData().forEach(b -> {
   		   //System.out.println(b.getClass());
   		   @SuppressWarnings("unchecked")
 		   List<Object> lst = (List<Object>)b;
   		   String id = getBusIdFromDataList(lst);
   		   /*
   		   System.out.print(" id: " + id); 
   		   System.out.print(" vm: " + lst.get(vmIdx)); 
   		   System.out.print(" va: " + lst.get(vaIdx)); 
   		   System.out.println();
   		   */
   		   
   		   /*
   		    * We can update the existing vm and va values in the list
   		    */
   		   int vmIdx = this.positionTable.get("vm");
   		   int vaIdx = this.positionTable.get("va");
   		   lst.set(vmIdx, aclfNet.getBus(id).getVoltageMag());
   		   lst.set(vaIdx, aclfNet.getBus(id).getVoltageAng(UnitType.Deg));
   		   /*
   		   System.out.print(" vm: " + lst.get(vmIdx)); 
		   System.out.print(" va: " + lst.get(vaIdx)); 
		   System.out.println();
		   */
   		   /*
		    * Or we can append the values to the end of the list
		    *
		   lst.add(aclfNet.getBus(id).getVoltageMag());    			// vm1
		   lst.add(aclfNet.getBus(id).getVoltageAng(UnitType.Deg));	// va1
		   */
   		});
	}
}