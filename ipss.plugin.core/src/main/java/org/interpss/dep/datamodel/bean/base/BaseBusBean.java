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
package org.interpss.dep.datamodel.bean.base;

import java.util.List;

import org.interpss.dep.datamodel.bean.datatype.ComplexValueBean;
import org.interpss.dep.datamodel.bean.datatype.LimitValueBean;
import org.interpss.numeric.util.NumericUtil;

/**
 * Base Bus Bean class
 * 
 * @author mzhou
 * @param <TExt> template for extension info 
 *
 */
public abstract class BaseBusBean<TExt extends BaseJSONUtilBean> extends BaseJSONBean<TExt> {
		
	public long number;    				// bus number
	
	public int status;			// bus in-service status
	
	public double
		base_v,					// bus base voltage in kv
		v_mag= 1.0,          	// bus voltage in pu		
		v_ang = 0.0;			// bus voltage angle in deg
	public LimitValueBean
	    vLimit = new LimitValueBean(1.1, 0.9);	  	// bus voltage limit
		
	public ComplexValueBean
	    gen, 					// bus generation
	    load, 					// bus load
	    shunt;					// bus shunt Y
	
	public long 
		area =1, 				// bus area number/id
		zone =1;				// bus zone number/id	
	
	public String
		areaName = "",				// bus area name
		zoneName = "";				// bus zone name
		
	public BaseBusBean() {}
	
	@Override public int compareTo(BaseJSONBean<TExt> b) {
		int eql = super.compareTo(b);
		
		BaseBusBean<TExt> bean = (BaseBusBean<TExt>)b;

		String str = "ID: " + this.id + " BaseBusBean.";
		
		if (this.number != bean.number) {
			logCompareMsg(str + "number is not equal, " + this.number + ", " + bean.number); eql = 1; }

		if (!NumericUtil.equals(this.base_v, bean.base_v, PU_ERR)) {
			logCompareMsg(str + "base_v is not equal, " + this.base_v + ", " + bean.base_v); eql = 1; }
		if (!NumericUtil.equals(this.v_mag, bean.v_mag, PU_ERR)) {
			logCompareMsg(str + "v_mag is not equal, " + this.v_mag + ", " + bean.v_mag); eql = 1;	}
		if (!NumericUtil.equals(this.v_ang, bean.v_ang, ANG_ERR)) {
			logCompareMsg(str + "v_ang is not equal, " + this.v_ang + ", " + bean.v_ang); eql = 1; }
		if (!NumericUtil.equals(this.vLimit.max, bean.vLimit.max, PU_ERR)) {
			logCompareMsg(str + "vmax is not equal, " + this.vLimit.max + ", " + bean.vLimit.max); eql = 1; }
		if (!NumericUtil.equals(this.vLimit.min, bean.vLimit.min, PU_ERR)) {
			logCompareMsg(str + "vmin is not equal, " + this.vLimit.min + ", " + bean.vLimit.min); eql = 1; }

		if (this.gen.compareTo(bean.gen) != 0) {
			logCompareMsg(str + "gen is not equal"); eql = 1; }
		if (this.load.compareTo(bean.load) != 0) {
			logCompareMsg(str + "load is not equal"); eql = 1; }
		if (this.shunt.compareTo(bean.shunt) != 0) {
			logCompareMsg(str + "shunt is not equal"); eql = 1; }
		
		if (this.area != bean.area) {
			logCompareMsg(str + "area is not equal, " + this.area + ", " + bean.area); eql = 1; }
		if (this.zone != bean.zone) {
			logCompareMsg(str + "zone is not equal, " + this.zone + ", " + bean.zone); eql = 1; }
				
		if (!this.areaName.equals(bean.areaName)){
			logCompareMsg(str + "area name is not equal, " + this.areaName + ", " + bean.areaName); eql = 1; }
		if (!this.zoneName.equals(bean.zoneName)){
			logCompareMsg(str + "zone name is not equal, " + this.zoneName + ", " + bean.zoneName); eql = 1;} 

		
		return eql;
	}	

	@Override public boolean validate(List<String> msgList) { 
		return true;
	}
}
