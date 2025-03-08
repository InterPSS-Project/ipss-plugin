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

package org.interpss.odm.mapper.base;

import java.util.function.Function;

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
	public static Function<ApparentPowerUnitType, UnitType> toApparentPowerUnit = unit -> {
				if (unit == ApparentPowerUnitType.KVA)
					return UnitType.kVA;
				else if (unit == ApparentPowerUnitType.MVA)
					return UnitType.mVA;
				return UnitType.PU;
			};

	/**
	 * convert XML power unit to InterPSS UnitType
	 * 
	 */
	public static Function<ActivePowerUnitType, UnitType> toActivePowerUnit = unit -> {
				if (unit == ActivePowerUnitType.KW)
					return UnitType.kW;
				else if (unit == ActivePowerUnitType.MW)
					return UnitType.mW;
				return UnitType.PU;
			};

	/**
	 * convert XML power unit to InterPSS UnitType
	 * 
	 */
	public static Function<ReactivePowerUnitType, UnitType> toReactivePowerUnit = unit -> {
				if (unit == ReactivePowerUnitType.KVAR)
					return UnitType.kVar;
				else if (unit == ReactivePowerUnitType.MVAR)
					return UnitType.mVar;
				return UnitType.PU;
			};

	/**
	 * convert XML Z unit to InterPSS UnitType
	 * 
	 */
	public static Function<ZUnitType, UnitType> toZUnit = unit -> {
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
			};
  
	/**
	 * convert XML Y unit to Ipss UnitType
	 * 
	 * @param unit power unit 
	 * @return
	 */
	public static Function<YUnitType, UnitType> toYUnit = unit -> {
				if (unit == YUnitType.MHO)
					return UnitType.Mho;
				else if (unit == YUnitType.MICROMHO)
					return UnitType.MicroMho;
				else if (unit == YUnitType.MVAR)
					return UnitType.mVar;
				else if (unit == YUnitType.KVAR)
					return UnitType.kVar;
				return UnitType.PU;
			};


	/**
	 * convert XML voltage unit to Ipss UnitType
	 * 
	 * @param unit power unit 
	 * @return
	 */
	public static Function<VoltageUnitType, UnitType> toVoltageUnit = unit -> {
				if (unit == VoltageUnitType.VOLT)
					return UnitType.Volt;
				else if (unit == VoltageUnitType.KV)
					return UnitType.kV;
				return UnitType.PU;
			};

	/**
	 * convert XML current unit to Ipss UnitType
	 * 
	 * @param unit power unit 
	 * @return
	 */
	public static Function<CurrentUnitType, UnitType> toCurrentUnit = unit -> {
				if (unit == CurrentUnitType.AMP)
					return UnitType.Amp;
				else if (unit == CurrentUnitType.KA)
					return UnitType.kAmp;
				return UnitType.PU;
			};

	
	/**
	 * convert XML angle unit to Ipss UnitType
	 * 
	 * @param unit power unit 
	 * @return
	 */
	public static Function<AngleUnitType, UnitType> toAngleUnit = unit -> {
				if (unit == AngleUnitType.DEG)
					return UnitType.Deg;
				return UnitType.Rad;
			};

	
	/**
	 * convert XML factor unit to Ipss UnitType
	 * 
	 * @param unit power unit 
	 * @return
	 */
	public static Function<FactorUnitType, UnitType> toFactorUnit = unit -> {
				if (unit == FactorUnitType.PERCENT)
					return UnitType.Percent;
				return UnitType.PU;
			};
	
	/**
	 * convert XML length unit to Ipss UnitType
	 * 
	 * @param unit power unit 
	 * @return
	 */
	public static Function<LengthUnitType, UnitType> toLengthUnit = unit -> {
				if (unit == LengthUnitType.FT)
					return UnitType.Ft;
				else if (unit == LengthUnitType.M)
					return UnitType.M;
				else if (unit == LengthUnitType.KM)
					return UnitType.kM;
				else if (unit == LengthUnitType.MILE)
					return UnitType.Mile;
				return UnitType.Ft;
			};
}
