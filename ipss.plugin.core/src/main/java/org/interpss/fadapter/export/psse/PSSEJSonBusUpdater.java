/*
  * @(#)PSSEAreaDataMapper.java   
  *
  * Copyright (C) 2006 www.interpss.org
  *
  * This program is free software; you can redistribute it and/or
  * modify it under the terms of the GNU LESSER GENERAL PUBLIC LICENSE
  * as published by the Free Software Foundation; either version 2.1
  * of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  * GNU General Public License for more details.
  *
  * @Author Mike Zhou
  * @Version 1.0
  * @Date 09/15/2006
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.fadapter.export.psse;

import java.util.List;

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
	/**
	 * Constructor
	 * 
	 * @param fieldDef field name definitions
	 */
	public PSSEJSonBusUpdater(PSSESchema.Bus bus) {
		super(bus.getFields(), (lst) -> {
			// fields appended at the end of the list
			lst.add("vm1");
			lst.add("va1");
		});
	}
	
	/**
	 * update the data list based on the AclfNetwork simulation results 
	 * 
	 * @param data
	 */
	public void update(PSSESchema.Bus bus, BaseAclfNetwork<?,?> aclfNet) {
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
   		   int idIdx = this.positionTable.get("ibus");
   		   String id = IODMModelParser.BusIdPreFix+((Double)lst.get(idIdx)).intValue();
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
		    */
		   lst.add(aclfNet.getBus(id).getVoltageMag());    			// vm1
		   lst.add(aclfNet.getBus(id).getVoltageAng(UnitType.Deg));	// va1
   		});
	}
}