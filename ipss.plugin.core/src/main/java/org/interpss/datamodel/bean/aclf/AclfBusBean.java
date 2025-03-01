/*
 * @(#)AclfBusBean.java   
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
package org.interpss.datamodel.bean.aclf;

import java.util.List;

import org.interpss.datamodel.bean.aclf.adj.SwitchShuntBean;
import org.interpss.datamodel.bean.base.BaseBusBean;
import org.interpss.datamodel.bean.base.BaseJSONBean;
import org.interpss.datamodel.bean.base.BaseJSONUtilBean;
import org.interpss.datamodel.bean.datatype.LimitValueBean;
import org.interpss.numeric.util.NumericUtil;

/**
 * Bean class for storing AclfBus info
 * 
 * @author mzhou 
 * @param <TExt> template for extension info 
 *
 */
public class AclfBusBean<TExt extends BaseJSONUtilBean>  extends BaseBusBean<TExt> {	

	/**
	 * bus generator type code 
	 */
	public static enum GenCode {
			Swing, 
			PV, 
			PQ, 
			NonGen};
	
	/**
	 * bus load type code 
	 */
	public static enum LoadCode {
			ConstP, 
			ConstI, 
			ConstZ, 
			NonLoad};	
	
	
	public GenCode 
		gen_code = GenCode.NonGen;				// bus generator code
	
	public LoadCode 
		load_code = LoadCode.NonLoad;				// bus load code	
    
	public double
		vDesired_mag= 1.0,          	// desired bus voltage in pu		
		vDesired_ang = 0.0;				// desired bus voltage angle in deg	
	
	public LimitValueBean 	
		pLimit,  // p limit in MW output
		qLimit;  // q limit in MVAR output

	public String remoteVControlBusId = "";  // remote control bus id	
	
	
	public SwitchShuntBean<TExt> switchShunt; // switch shunt bean connected to the bus
	
		
	public AclfBusBean() {}	
		
	
	@Override public int compareTo(BaseJSONBean<TExt> b) {
		int eql = super.compareTo(b);
		
		AclfBusBean<TExt> bean = (AclfBusBean<TExt>)b;
		
		String str = "ID: " + this.id + " AclfBusBean.";

		if (this.gen_code != bean.gen_code) {
			logCompareMsg(str + "gen_code is not equal, " + this.gen_code + ", " + bean.gen_code); eql = 1; }
		if (this.load_code != bean.load_code) {
			logCompareMsg(str + "load_code is not equal, " + this.load_code + ", " + bean.load_code); eql = 1; }
		
		if (!NumericUtil.equals(this.vDesired_mag, bean.vDesired_mag, PU_ERR)) {
			logCompareMsg(str + "vDesired_mag is not equal, " + this.vDesired_mag + ", " + bean.vDesired_mag); eql = 1; }
		if (!NumericUtil.equals(this.vDesired_ang, bean.vDesired_ang, PU_ERR)) {
			logCompareMsg(str + "vDesired_ang is not equal, " + this.vDesired_ang + ", " + bean.vDesired_ang); eql = 1;	}
		
		if (!NumericUtil.equals(this.pLimit.max, bean.pLimit.max, PU_ERR)) {
			logCompareMsg(str + "pmax is not equal, " + this.pLimit.max + ", " + bean.pLimit.max); eql = 1; }
		if (!NumericUtil.equals(this.qLimit.max, bean.qLimit.max, PU_ERR)) {
			logCompareMsg(str + "qmax is not equal, " + this.qLimit.max + ", " + bean.qLimit.max); eql = 1;	}
		
		if (!NumericUtil.equals(this.pLimit.min, bean.pLimit.min, PU_ERR)) {
			logCompareMsg(str + "pmin is not equal, " + this.pLimit.min + ", " + bean.pLimit.min); eql = 1; }
		if (!NumericUtil.equals(this.qLimit.min, bean.qLimit.min, PU_ERR)) {
			logCompareMsg(str + "qmin is not equal, " + this.qLimit.min + ", " + bean.qLimit.min); eql = 1;	}
				
		if(this.switchShunt == null && bean.switchShunt != null)
			eql = 1;
		
		if(this.switchShunt != null && bean.switchShunt == null)
			eql = 1;
		
		if(this.switchShunt != null && bean.switchShunt != null)		
			if(this.switchShunt.compareTo(bean.switchShunt) != 0 ) eql = 1;

		return eql;
	}	
	
	@Override public boolean validate(List<String> msgList) { 
		return true;
	}
}
