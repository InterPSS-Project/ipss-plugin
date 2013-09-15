/*
 * @(#)BaseNetBean.java   
 *
 * Copyright (C) 2008-2013 www.interpss.org
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
 * @Date 01/10/2013
 * 
 *   Revision History
 *   ================
 *
 */
package org.interpss.datamodel.bean;

import java.util.List;

import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.NumericUtil;

import com.interpss.common.util.IpssLogger;

/**
 * Base Network Bean class
 * 
 * @author mzhou
 *
 */
public abstract class BaseNetBean extends BaseJSONBean {
	public double base_kva; // network base kva

	/**
	 * units
	 */
	public UnitType 
	 		unit_ang = UnitType.Deg, // angle unit for voltage, PsXfr shifting angle
			unit_bus_v = UnitType.PU, // bus voltage unit
			unit_bus_p = UnitType.PU, // bus power (gen/load) unit
			unit_branch_z = UnitType.PU, // branch impedance unit
			unit_branch_cur = UnitType.Amp, // branch current unit
			unit_branch_b = UnitType.PU; // branch shunt Y unit
	
	public BaseNetBean() {  }
	
	@Override public int compareTo(BaseJSONBean b) {
		int eql = super.compareTo(b);
		
		BaseNetBean bean = (BaseNetBean)b;

		String str = "ID: " + this.id + " BaseNetBean.";
		
		if (!NumericUtil.equals(this.base_kva, bean.base_kva, PU_ERR)) {
			IpssLogger.ipssLogger.warning(str + "base_kva is not equal, " + this.base_kva + ", " + bean.base_kva);	eql = 1; }
		
		if (this.unit_ang != bean.unit_ang) {
			IpssLogger.ipssLogger.warning(str + "unit_ang is not equal, " + this.unit_ang + ", " + bean.unit_ang); eql = 1; }
		if (this.unit_bus_v != bean.unit_bus_v) {
			IpssLogger.ipssLogger.warning(str + "unit_bus_v is not equal, " + this.unit_bus_v + ", " + bean.unit_bus_v); eql = 1; }
		if (this.unit_bus_p != bean.unit_bus_p) {
			IpssLogger.ipssLogger.warning(str + "unit_bus_p is not equal, " + this.unit_bus_p + ", " + bean.unit_bus_p); eql = 1; }
		if (this.unit_branch_z != bean.unit_branch_z) {
			IpssLogger.ipssLogger.warning(str + "unit_branch_z is not equal, " + this.unit_branch_z + ", " + bean.unit_branch_z); eql = 1; }
		if (this.unit_branch_cur != bean.unit_branch_cur) {
			IpssLogger.ipssLogger.warning(str + "unit_ang is not equal, " + this.unit_branch_cur + ", " + bean.unit_branch_cur); eql = 1; }
		if (this.unit_branch_b != bean.unit_branch_b) {
			IpssLogger.ipssLogger.warning(str + "unit_branch_b is not equal, " + this.unit_branch_b + ", " + bean.unit_branch_b); eql = 1; }

		return eql;
	}	

	@Override public boolean validate(List<String> msgList) {
		boolean noErr = true;
		if (this.base_kva == 0.0) {
			msgList.add("NetBean data error: baseKva not defined");
			noErr = false;
		}
		return noErr;
	}

}
