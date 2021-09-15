/*
 * @(#)AclfNetResultBean.java   
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

import org.interpss.datamodel.bean.BaseJSONBean;
import org.interpss.datamodel.bean.BaseJSONUtilBean;
import org.interpss.datamodel.bean.DefaultExtBean;
import org.interpss.datamodel.bean.datatype.ComplexValueBean;
import org.interpss.datamodel.bean.datatype.MismatchResultBean;

/**
 * Bean class for storing AclfNetwork result info
 * 
 * @author mzhou
 *
 */
public class AclfNetResultBean<TBusExt extends BaseJSONUtilBean, TBraExt extends BaseJSONUtilBean> extends BaseAclfNetBean<AclfBusResultBean<TBusExt>, AclfBranchResultBean<TBraExt>, TBusExt, TBraExt> {
	public boolean
		lf_converge;				// AC loadflow convergence
	
	public ComplexValueBean
		gen,						// total gen power
		load,						// total load power
		loss;						// total network power loss
	
	public MismatchResultBean
		max_mis;					// max mismatch
	
	public AclfNetResultBean() { super(); }
	
	@Override public int compareTo(BaseJSONBean<DefaultExtBean> b) {
		int eql = super.compareTo(b);
		
		AclfNetResultBean<TBusExt, TBraExt> bean = (AclfNetResultBean<TBusExt, TBraExt>)b;
		
		String str = "ID: " + this.id + " AclfNetResultBean.";

		if (this.lf_converge != bean.lf_converge) {
			logCompareMsg(str + "lf_converge is not equal, " + this.lf_converge + ", " + bean.lf_converge); eql = 1; }
		
		if (this.gen.compareTo(bean.gen) != 0) {
			logCompareMsg(str + "gen is not equal");	eql = 1; }		
		if (this.load.compareTo(bean.load) != 0) {
			logCompareMsg(str + "load is not equal");	eql = 1; }	
		if (this.loss.compareTo(bean.loss) != 0) {
			logCompareMsg(str + "loss is not equal");	eql = 1; }	
		
		// mismatch is random
		//if (this.max_mis.compareTo(bean.max_mis) != 0) {
		//	logCompareMsg("AclfNetResultBean.max_mis is not equal");	eql = 1; }			
		return eql;
	}	
}