 /*
  * @(#)QAAclfBusRec.java   
  *
  * Copyright (C) 2006-2011 www.interpss.com
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
  * @Date 04/15/2009
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.dep.QA.result;

import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.Number2String;

@Deprecated
public class QAAclfBusRec extends QABusRec {
	public static UnitType vUnit = UnitType.PU;
	public static UnitType pUnit = UnitType.PU;
	
	public double vmag; 	// in pu or Kv
	public double vang; 	// in rad
	public double genp; 	// 
	public double genq; 	// 
	public double loadp; 	// 
	public double loadq; 	// 
	public double shuntq; 	// 
	public double shuntg; 	// 
	
	public QAAclfBusRec(String id) {
		super(id);
	}
	
	public String toString() {
		return "Voltage: " + Number2String.toStr(vmag) + ", " + Number2String.toStr(vang) + "     " + 
		       "Gen: " + Number2String.toStr(genp) + ", " + Number2String.toStr(genq) + "     " +
		       "Load: " + Number2String.toStr(loadp) + ", " + Number2String.toStr(loadq) + "     " +
	       	   "Shunt: " + Number2String.toStr(shuntg) + ", " + Number2String.toStr(shuntq);
	}
}
