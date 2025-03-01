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
package org.interpss.datamodel.bean.base;

import java.util.List;

import org.interpss.datamodel.bean.datatype.ComplexValueBean;
import org.interpss.numeric.util.NumericUtil;

/**
 * Base Branch Bean class
 * 
 * @author mzhou
 * @param <TExt> template for extension info 
 *
 */
public abstract class BaseBranchBean<TExt extends BaseJSONUtilBean> extends BaseJSONBean<TExt> {
	/**
	 * branch type code
	 */
	public static enum BranchCode {
			Line, 			// transmission line
			Xfr, 			// transformer
			PsXfr, 			// phase-shifting transformer
			ZBR,   			// zero impedance line
			Breaker         // breaker
	};

	public String 
			f_id, 			// branch from side bus id
			t_id, 			// branch to side bus id
			cir_id = "1"; 	// branch circuit id/number

	public long 
			f_num, 			// branch from side bus number 
			t_num; 			// branch to side bus number
	
	public String 
			f_name, 		// branch from side bus name
			t_name;			// branch to side bus number

	public int status = 1;		// branch in-service status, 1 in service; 0 out of service

	public ComplexValueBean 
	    	z,					// branch z
	    	shunt_y ;     		// branch total shunt y
	
	public double 
			mvaRatingA, 		// branch mva rating 
			mvaRatingB, 		// branch mva rating 
			mvaRatingC;			// branch mva rating 

	public BranchCode bra_code = BranchCode.Line; // branch type code

	public BaseBranchBean() {
	}

	@Override public int compareTo(BaseJSONBean<TExt> b) {
		int eql = super.compareTo(b);
		
		BaseBranchBean<TExt> bean = (BaseBranchBean<TExt>)b;

		String str = "ID: " + this.id + " BaseBranchBean.";
		
		if (!this.f_id.equals(bean.f_id)) {
			logCompareMsg(str + "f_id is not equal, " + this.f_id + ", " + bean.f_id); eql = 1; }
		if (!this.t_id.equals(bean.t_id)) {
			logCompareMsg(str + "t_id is not equal, " + this.t_id + ", " + bean.t_id); eql = 1; }
		if (!this.cir_id.equals(bean.cir_id)) {
			logCompareMsg(str + "cir_id is not equal, " + this.cir_id + ", " + bean.cir_id); eql = 1; }

		if (this.f_num != bean.f_num) {
			logCompareMsg(str + "f_num is not equal, " + this.f_num + ", " + bean.f_num); eql = 1; }
		if (this.t_num != bean.t_num) {
			logCompareMsg(str + "t_num is not equal, " + this.t_num + ", " + bean.t_num); eql = 1; }

		if (this.status != bean.status) {
			logCompareMsg("BaseBranchBean.status is not equal, " + this.status + ", " + bean.status); eql = 1; }

		if (this.z.compareTo(bean.z) != 0) {
			logCompareMsg(str + "z is not equal"); eql = 1; }
		if (this.shunt_y.compareTo(bean.shunt_y) != 0) {
			logCompareMsg(str + "shunt_y is not equal"); eql = 1; }

		if (!NumericUtil.equals(this.mvaRatingA, bean.mvaRatingA, PU_ERR)) {
			logCompareMsg(str + "basemvaRatingA is not equal, " + this.mvaRatingA + ", " + bean.mvaRatingA); eql = 1; }
		if (!NumericUtil.equals(this.mvaRatingB, bean.mvaRatingB, PU_ERR)) {
			logCompareMsg(str + "basemvaRatingB is not equal, " + this.mvaRatingB + ", " + bean.mvaRatingB); eql = 1; }
		if (!NumericUtil.equals(this.mvaRatingC, bean.mvaRatingC, PU_ERR)) {
			logCompareMsg(str + "basemvaRatingC is not equal, " + this.mvaRatingC + ", " + bean.mvaRatingC); eql = 1; }
		
		if (this.bra_code != bean.bra_code) {
			logCompareMsg(str + "bra_code is not equal, " + this.bra_code + ", " + bean.bra_code); eql = 1; }

		return eql;
	}	
	
	@Override public boolean validate(List<String> msgList) {
		return true;
	}
}
