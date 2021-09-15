/*
 * @(#)BaseJSONBean.java   
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
package org.interpss.datamodel.bean.aclf.adj;

import java.util.List;

import org.interpss.datamodel.bean.BaseJSONBean;
import org.interpss.datamodel.bean.BaseJSONUtilBean;
import org.interpss.numeric.util.NumericUtil;

/**
 * Bean class for storing Aclf two winding branch object info
 * 
 * @author sHou
 *
 */
public class BaseTapControlBean<TExt extends BaseJSONUtilBean> extends BaseJSONBean<TExt> {	
	 
	public static enum TapControlTypeBean {Point_Control, Range_Control,No_Control};
	public static enum TapControlModeBean {Bus_Voltage, Mva_Flow, MW_Flow, No_Control};
	
	public TapControlTypeBean controlType = TapControlTypeBean.No_Control; // control type
	
	public TapControlModeBean controlMode = TapControlModeBean.No_Control;
	
	public int status = 1;		// tap control status
	
	public double 
		maxTap = 1.1,					// max tap
		minTap = 0.9,					// min tap
		upperLimit = 1.1,				// tap control target upper limit (range control)
		lowerLimit = 0.9,				// tap control target lower limit (range control)
		desiredControlTarget = 1.0,	// tap control targeted value (point control)
		stepSize = 1.0;				// tap control step size	
	
	public boolean 
		measuredOnFromSide = true,		// mvar flow is measured on from side
	    controlOnFromSide = true;		// control is applied on from side	
		
	public int steps = 1;						// tap control steps		
	
	public BaseTapControlBean() {}
	
	@Override public int compareTo(BaseJSONBean<TExt> b) {
		int eql = super.compareTo(b);
		
		BaseTapControlBean<TExt> bean = (BaseTapControlBean<TExt>)b;

		String str = "ID: " + this.id + " BaseTapControlBean.";		
		
		if (this.status != bean.status) {
			logCompareMsg(str + "status is not equal, " + this.status + ", " + bean.status); eql = 1; }
		
		// compare double
		if (!NumericUtil.equals(this.maxTap, bean.maxTap, PU_ERR)) {
			logCompareMsg(str + "maxTap is not equal, " + this.maxTap + ", " + bean.maxTap); eql = 1; }
		if (!NumericUtil.equals(this.minTap, bean.minTap, PU_ERR)) {
			logCompareMsg(str + "minTap is not equal, " + this.minTap + ", " + bean.minTap); eql = 1;	}
		if (!NumericUtil.equals(this.upperLimit, bean.upperLimit, PU_ERR)) {
			logCompareMsg(str + "upperLimit is not equal, " + this.upperLimit + ", " + bean.upperLimit); eql = 1; }
		if (!NumericUtil.equals(this.lowerLimit, bean.lowerLimit, PU_ERR)) {
			logCompareMsg(str + "lowerLimit is not equal, " + this.lowerLimit + ", " + bean.lowerLimit); eql = 1;	}
		if (!NumericUtil.equals(this.stepSize, bean.stepSize, ANG_ERR)) {
			logCompareMsg(str + "stepSize is not equal, " + this.stepSize + ", " + bean.stepSize); eql = 1; }
		if (!NumericUtil.equals(this.desiredControlTarget, bean.desiredControlTarget, ANG_ERR)) {
			logCompareMsg(str + "desiredVoltage is not equal, " + this.desiredControlTarget + ", " + bean.desiredControlTarget); eql = 1; }
				
		if (this.controlType != bean.controlType) {
			logCompareMsg(str + "control type is not equal, " + this.controlType + ", " + bean.controlType); eql = 1; }
		
		
		if (this.steps != bean.steps) {
			logCompareMsg(str + "steps is not equal, " + this.steps + ", " + bean.steps); eql = 1; }
		if (this.measuredOnFromSide != bean.measuredOnFromSide) {
			logCompareMsg(str + "measuredOnFromSide is not equal, " + this.measuredOnFromSide + ", " + bean.measuredOnFromSide); eql = 1; }
		if (this.controlOnFromSide != bean.controlOnFromSide) {
			logCompareMsg(str + "controlOnFromSide is not equal, " + this.controlOnFromSide + ", " + bean.controlOnFromSide); eql = 1; }

		return eql;
	}	
	
	@Override
	public boolean validate(List<String> msgList) { return true; }
}
