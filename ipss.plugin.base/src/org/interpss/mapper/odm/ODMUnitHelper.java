/*
 * @(#)ODMUnitHelper.java   
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
import org.ieee.odm.schema.AngleUnitType;
import org.ieee.odm.schema.ApparentPowerUnitType;
import org.ieee.odm.schema.CurrentUnitType;
import org.ieee.odm.schema.FactorUnitType;
import org.ieee.odm.schema.LengthUnitType;
import org.ieee.odm.schema.ReactivePowerUnitType;
import org.ieee.odm.schema.VoltageUnitType;
import org.ieee.odm.schema.YUnitType;
import org.ieee.odm.schema.ZUnitType;
import org.interpss.numeric.datatype.Unit.UnitType;

import com.interpss.common.func.FunctionAdapter;
import com.interpss.common.func.IFunction;

/**
 * ODM unit help functions. To use functions in this class, 
 * 
 * 	import static org.interpss.mapper.odm.ODMUnitHelper.*;

 * @author mzhou
 *
 */
public class ODMUnitHelper {
	/**
	 * convert XML power unit to InterPSS UnitType
	 * 
	 */
	public static IFunction<ApparentPowerUnitType, UnitType> ToApparentPowerUnit = 
		new FunctionAdapter<ApparentPowerUnitType, UnitType>() {
			@Override public UnitType f(ApparentPowerUnitType from) {
				return toApparentPowerUnit(from);
			}
			private UnitType toApparentPowerUnit(ApparentPowerUnitType unit) {
				if (unit == ApparentPowerUnitType.KVA)
					return UnitType.kVA;
				else if (unit == ApparentPowerUnitType.MVA)
					return UnitType.mVA;
				return UnitType.PU;
			}
		};

	/**
	 * convert XML power unit to InterPSS UnitType
	 * 
	 */
	public static IFunction<ActivePowerUnitType, UnitType> ToActivePowerUnit = 
		new FunctionAdapter<ActivePowerUnitType, UnitType>() {
			@Override public UnitType f(ActivePowerUnitType from) {
				return toActivePowerUnit(from);
			}
			private UnitType toActivePowerUnit(ActivePowerUnitType unit) {
				if (unit == ActivePowerUnitType.KW)
					return UnitType.kW;
				else if (unit == ActivePowerUnitType.MW)
					return UnitType.mW;
				return UnitType.PU;
			}
		};

	/**
	 * convert XML power unit to InterPSS UnitType
	 * 
	 */
	public static IFunction<ReactivePowerUnitType, UnitType> ToReactivePowerUnit = 
		new FunctionAdapter<ReactivePowerUnitType, UnitType>() {
			@Override public UnitType f(ReactivePowerUnitType from) {
				return toReactivePowerUnit(from);
			}
			private UnitType toReactivePowerUnit(ReactivePowerUnitType unit) {
				if (unit == ReactivePowerUnitType.KVAR)
					return UnitType.kVar;
				else if (unit == ReactivePowerUnitType.MVAR)
					return UnitType.mVar;
				return UnitType.PU;
			}		
		};

	/**
	 * convert XML Z unit to InterPSS UnitType
	 * 
	 */
	public static IFunction<ZUnitType, UnitType> ToZUnit = 
		new FunctionAdapter<ZUnitType, UnitType>() {
			@Override public UnitType f(ZUnitType from) {
				return toZUnit(from);
			}
			private UnitType toZUnit(ZUnitType unit) {
				if (unit == ZUnitType.OHM)
					return UnitType.Ohm;
				else if (unit == ZUnitType.OHM_PER_FT)
					return UnitType.OhmPerFt;
				else if (unit == ZUnitType.OHM_PER_M)
					return UnitType.OhmPerM;
				else if (unit == ZUnitType.PERCENT)
					return UnitType.Percent;
				else if (unit == ZUnitType.MVA)
					return UnitType.mVA;
				else if (unit == ZUnitType.KVA)
					return UnitType.kVA;
				return UnitType.PU;
			}
		};
  
	/**
	 * convert XML Y unit to Ipss UnitType
	 * 
	 * @param unit power unit 
	 * @return
	 */
	public static IFunction<YUnitType, UnitType> ToYUnit = 
		new FunctionAdapter<YUnitType, UnitType>() {
			@Override public UnitType f(YUnitType from) {
				return toYUnit(from);
			}
			private UnitType toYUnit(YUnitType unit) {
				if (unit == YUnitType.MHO)
					return UnitType.Mho;
				else if (unit == YUnitType.MICROMHO)
					return UnitType.MicroMho;
				else if (unit == YUnitType.MVAR)
					return UnitType.mVar;
				else if (unit == YUnitType.KVAR)
					return UnitType.kVar;
				return UnitType.PU;
			}
		};


	/**
	 * convert XML voltage unit to Ipss UnitType
	 * 
	 * @param unit power unit 
	 * @return
	 */
	public static IFunction<VoltageUnitType, UnitType> ToVoltageUnit = 
		new FunctionAdapter<VoltageUnitType, UnitType>() {
			@Override public UnitType f(VoltageUnitType from) {
				return toVoltageUnit(from);
			}
			private UnitType toVoltageUnit(VoltageUnitType unit) {
				if (unit == VoltageUnitType.VOLT)
					return UnitType.Volt;
				else if (unit == VoltageUnitType.KV)
					return UnitType.kV;
				return UnitType.PU;
			}
		};

	/**
	 * convert XML current unit to Ipss UnitType
	 * 
	 * @param unit power unit 
	 * @return
	 */
	public static IFunction<CurrentUnitType, UnitType> ToCurrentUnit = 
		new FunctionAdapter<CurrentUnitType, UnitType>() {
			@Override public UnitType f(CurrentUnitType from) {
				return toCurrentUnit(from);
			}
			private UnitType toCurrentUnit(CurrentUnitType unit) {
				if (unit == CurrentUnitType.AMP)
					return UnitType.Amp;
				else if (unit == CurrentUnitType.KA)
					return UnitType.kAmp;
				return UnitType.PU;
			}
		};

	
	/**
	 * convert XML angle unit to Ipss UnitType
	 * 
	 * @param unit power unit 
	 * @return
	 */
	public static IFunction<AngleUnitType, UnitType> ToAngleUnit = 
		new FunctionAdapter<AngleUnitType, UnitType>() {
			@Override public UnitType f(AngleUnitType from) {
				return toAngleUnit(from);
			}
			private UnitType toAngleUnit(AngleUnitType unit) {
				if (unit == AngleUnitType.DEG)
					return UnitType.Deg;
				return UnitType.Rad;
			}
		};

	
	/**
	 * convert XML factor unit to Ipss UnitType
	 * 
	 * @param unit power unit 
	 * @return
	 */
	public static IFunction<FactorUnitType, UnitType> ToFactorUnit = 
		new FunctionAdapter<FactorUnitType, UnitType>() {
			@Override public UnitType f(FactorUnitType from) {
				return toFactorUnit(from);
			}
			private UnitType toFactorUnit(FactorUnitType unit) {
				if (unit == FactorUnitType.PERCENT)
					return UnitType.Percent;
				return UnitType.PU;
			}
		};
	
	/**
	 * convert XML length unit to Ipss UnitType
	 * 
	 * @param unit power unit 
	 * @return
	 */
	public static IFunction<LengthUnitType, UnitType> ToLengthUnit = 
		new FunctionAdapter<LengthUnitType, UnitType>() {
			@Override public UnitType f(LengthUnitType from) {
				return toLengthUnit(from);
			}
			private UnitType toLengthUnit(LengthUnitType unit) {
				if (unit == LengthUnitType.FT)
					return UnitType.Ft;
				else if (unit == LengthUnitType.M)
					return UnitType.M;
				else if (unit == LengthUnitType.KM)
					return UnitType.kM;
				else if (unit == LengthUnitType.MILE)
					return UnitType.Mile;
				return UnitType.Ft;
			}		
		};
}
