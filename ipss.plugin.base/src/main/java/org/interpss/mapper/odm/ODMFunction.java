/*
 * @(#)ODMFunctionr.java   
 *
 * Copyright (C) 2008 www.interpss.org
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
 * @Date 02/15/2008
 * 
 *   Revision History
 *   ================
 *
 */

package org.interpss.mapper.odm;

import org.ieee.odm.schema.ActivePowerUnitType;
import org.ieee.odm.schema.ActivePowerXmlType;
import org.ieee.odm.schema.BusXmlType;
import org.ieee.odm.schema.IDRefRecordXmlType;
import org.interpss.numeric.NumericConstant;

import com.interpss.common.exp.InterpssException;
import com.interpss.common.func.Function2Adapter;
import com.interpss.common.func.FunctionAdapter;
import com.interpss.common.func.IFunction;
import com.interpss.common.func.IFunction2;

/**
 * ODM functions. To use functions in this class, 
 * 
 * 	import static org.interpss.mapper.odm.ODMFunction.*;

 * @author mzhou
 *
 */
public class ODMFunction {
	/**
	 * convert ActivePower ODM object to a MW value
	 * 
	 */
	public static IFunction2<ActivePowerXmlType, Double, Double> AcivePower2Mw = 
		new Function2Adapter<ActivePowerXmlType, Double, Double>() {
			@Override public Double f(ActivePowerXmlType from, Double baseKva) {
				if (from.getUnit() == ActivePowerUnitType.HP)
					return from.getValue() * NumericConstant.HPtoKW * 0.001;
				else if (from.getUnit() == ActivePowerUnitType.KW)
					return from.getValue() * 0.001;
				else if (from.getUnit() == ActivePowerUnitType.MW)
					return from.getValue();
				else if (from.getUnit() == ActivePowerUnitType.W)
					return from.getValue() * 0.001 * 0.0001;
				else if (from.getUnit() == ActivePowerUnitType.PU)
					return from.getValue() * baseKva * 0.001;
				return 0.0;
			}
		};

	/**
	 * get bus id from a bus ref xml object	
	 */
	public static IFunction<IDRefRecordXmlType, String> BusXmlRef2BusId = 
		new FunctionAdapter<IDRefRecordXmlType, String>() {
			@Override public String fx(IDRefRecordXmlType busXmlRef) throws InterpssException {
				if (busXmlRef.getIdRef() == null)
					throw new InterpssException("Bus ref is null");
				else
					return ((BusXmlType)busXmlRef.getIdRef()).getId();
			}
		};

}
