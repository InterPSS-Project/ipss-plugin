/*
 * @(#)BaseBusBean.java   
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

import org.interpss.datamodel.bean.datatype.ComplexBean;
import org.interpss.numeric.util.NumericUtil;

import com.interpss.common.util.IpssLogger;

/**
 * Base Bus Bean class
 * 
 * @author mzhou
 *
 */
public class BaseBusBean extends BaseJSONBean {
		
	public long 
	    number;    				// bus number
	
	public int status;			// bus in-service status
	
	public double
		base_v,					// bus base voltage
		v_mag= 1.0,          	// bus voltage in pu		
		v_ang = 0.0,			// bus voltage angle
	    vmax = 1.1,				// bus voltage upper limit
	    vmin = 0.9;				// bus voltage lower limit
		
	public ComplexBean
	    gen, 					// bus generation
	    load, 					// bus load
	    shunt;					// bus shunt Y
	
	public long 
		area =1, 				// bus area number/id
		zone =1;				// bus zone number/id	
		
	public BaseBusBean() {}
	
	@Override public int compareTo(BaseJSONBean b) {
		int eql = super.compareTo(b);
		
		BaseBusBean bean = (BaseBusBean)b;

		if (this.number != bean.number) {
			IpssLogger.ipssLogger.warning("BaseBusBean.number is not equal, " + this.number + ", " + bean.number); eql = 1; }

		if (!NumericUtil.equals(this.base_v, bean.base_v, PU_ERR)) {
			IpssLogger.ipssLogger.warning("BaseBusBean.base_v is not equal, " + this.base_v + ", " + bean.base_v); eql = 1; }
		if (!NumericUtil.equals(this.v_mag, bean.v_mag, PU_ERR)) {
			IpssLogger.ipssLogger.warning("BaseBusBean.v_mag is not equal, " + this.v_mag + ", " + bean.v_mag); eql = 1;	}
		if (!NumericUtil.equals(this.v_ang, bean.v_ang, PU_ERR)) {
			IpssLogger.ipssLogger.warning("BaseBusBean.v_ang is not equal, " + this.v_ang + ", " + bean.v_ang); eql = 1; }
		if (!NumericUtil.equals(this.vmax, bean.vmax, PU_ERR)) {
			IpssLogger.ipssLogger.warning("BaseBusBean.vmax is not equal, " + this.vmax + ", " + bean.vmax); eql = 1; }
		if (!NumericUtil.equals(this.vmin, bean.vmin, PU_ERR)) {
			IpssLogger.ipssLogger.warning("BaseBusBean.vmin is not equal, " + this.vmin + ", " + bean.vmin); eql = 1; }

		if (this.gen.compareTo(bean.gen) != 0) {
			IpssLogger.ipssLogger.warning("BaseBusBean.gen is not equal"); eql = 1; }
		if (this.load.compareTo(bean.load) != 0) {
			IpssLogger.ipssLogger.warning("BaseBusBean.load is not equal"); eql = 1; }
		if (this.shunt.compareTo(bean.shunt) != 0) {
			IpssLogger.ipssLogger.warning("BaseBusBean.shunt is not equal"); eql = 1; }
		
		if (this.area != bean.area) {
			IpssLogger.ipssLogger.warning("BaseBusBean.area is not equal, " + this.area + ", " + bean.area); eql = 1; }
		if (this.zone != bean.zone) {
			IpssLogger.ipssLogger.warning("BaseBusBean.zone is not equal, " + this.zone + ", " + bean.zone); eql = 1; }
		
		return eql;
	}	

	@Override public boolean validate(List<String> msgList) { 
		return true;
	}
}
