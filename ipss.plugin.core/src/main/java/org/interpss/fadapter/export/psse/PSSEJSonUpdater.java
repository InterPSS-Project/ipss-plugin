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

import org.ieee.odm.model.IODMModelParser;
import org.interpss.numeric.datatype.Unit.UnitType;

import com.interpss.core.aclf.BaseAclfNetwork;

/**
 * PSSE JSon format data updater
 * 
 * @author mzhou
 *
 */
public class PSSEJSonUpdater {
	// the AclfNetwork object
	private BaseAclfNetwork<?,?> aclfNet;
	
	/**
	 * Constructor
	 * 
	 * @param aclfNet the AclfNetwork object
	 */
	public PSSEJSonUpdater(BaseAclfNetwork<?,?> aclfNet) {
		this.aclfNet = aclfNet;
	}
	
	/**
	 * update the data list based on the AclfNetwork simulation results 
	 * 
	 * @param data
	 */
	public void updateBus(List<Object> data) {
		/*
 		"fields":["ibus", "name", "baskv", "ide", "area", "zone", "owner", "vm", "va", "nvhi", 	
 					"nvlo", "evhi", "evlo"], 
        "data":  [1, "BUS-1", 16.50000, 3, 1, 1, 1, 1.040000, 0.000000, 1.100000, 0.9000000, 
        			1.100000, 0.9000000], 
		 */		
 		data.forEach(b -> {
   		   //System.out.println(b.getClass());
   		   @SuppressWarnings("unchecked")
 		   List<Object> lst = (List<Object>)b;
   		   int idIdx = 0;
   		   int vmIdx = 7;
   		   int vaIdx = 8;
   		   String id = IODMModelParser.BusIdPreFix+((Double)lst.get(idIdx)).intValue();
   		   System.out.print(" id: " + id); 
   		   System.out.print(" vm: " + lst.get(vmIdx)); 
   		   System.out.print(" va: " + lst.get(vaIdx)); 
   		   System.out.println();
   		   
   		   lst.set(vmIdx, this.aclfNet.getBus(id).getVoltageMag());
   		   lst.set(vaIdx, this.aclfNet.getBus(id).getVoltageAng(UnitType.Deg));
   		   System.out.print(" vm: " + lst.get(vmIdx)); 
		   System.out.print(" va: " + lst.get(vaIdx)); 
		   System.out.println();
   		});
	}
}