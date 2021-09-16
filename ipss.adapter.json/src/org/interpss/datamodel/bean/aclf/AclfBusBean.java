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
import org.interpss.numeric.util.NumericUtil;

/**
 * Bean class for storing AclfBus info
 * 
 * @author mzhou
 *
 */
public class AclfBusBean<TExt extends BaseJSONUtilBean>  extends BaseBusBean<TExt> {	

	/**
	 * bus generator type code 
	 */
	public static enum GenCode {Swing, PV, PQ, NonGen};
	
	/**
	 * bus load type code 
	 */
	public static enum LoadCode {ConstP, ConstI, ConstZ, NonLoad};	
	
	
	public GenCode 
		gen_code = GenCode.NonGen;				// bus generator code
	
	public LoadCode 
		load_code = LoadCode.NonLoad;				// bus load code	
    
	public double
		vDesired_mag= 1.0,          	// desired bus voltage in pu		
		vDesired_ang = 0.0;				// desired bus voltage angle in deg	
	
	public double 	
		pmax, // max MW output
		pmin, // min MW output
		qmax, // max MVAR output
		qmin; // min MVAR output	

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
		
		if (!NumericUtil.equals(this.pmax, bean.pmax, PU_ERR)) {
			logCompareMsg(str + "pmax is not equal, " + this.pmax + ", " + bean.pmax); eql = 1; }
		if (!NumericUtil.equals(this.qmax, bean.qmax, PU_ERR)) {
			logCompareMsg(str + "qmax is not equal, " + this.qmax + ", " + bean.qmax); eql = 1;	}
		
		if (!NumericUtil.equals(this.pmin, bean.pmin, PU_ERR)) {
			logCompareMsg(str + "pmin is not equal, " + this.pmin + ", " + bean.pmin); eql = 1; }
		if (!NumericUtil.equals(this.qmin, bean.qmin, PU_ERR)) {
			logCompareMsg(str + "qmin is not equal, " + this.qmin + ", " + bean.qmin); eql = 1;	}
				
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
