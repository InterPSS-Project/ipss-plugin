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
package org.interpss.dep.datamodel.bean.aclf;

import java.util.List;

import org.interpss.dep.datamodel.bean.aclf.adj.PsXfrTapControlBean;
import org.interpss.dep.datamodel.bean.aclf.adj.XfrTapControlBean;
import org.interpss.dep.datamodel.bean.base.BaseBranchBean;
import org.interpss.dep.datamodel.bean.base.BaseJSONBean;
import org.interpss.dep.datamodel.bean.base.BaseJSONUtilBean;
import org.interpss.dep.datamodel.bean.datatype.BranchValueBean;

/**
 * Bean class for storing AclfBranch object info
 * 
 * @author mzhou 
 * @param <TExt> template for extension info 
 *
 */
public class AclfBranchBean<TExt extends BaseJSONUtilBean> extends BaseBranchBean<TExt> {
	
	public BranchValueBean 
			turnRatio = new BranchValueBean(1.0,1.0),			    // xfr branch turn ratio, it is assumed on the from bus side per PSSE
			shiftAng = new BranchValueBean(0.0,0.0);				// PsXfr shifting angle, in rad, it is assumed on the from bus side per PSSE
	
	public XfrTapControlBean<TExt> xfrTapControl;					// control bean for xfr
	
	public PsXfrTapControlBean<TExt> psXfrTapControl;				// control bean for phase shiter control
	
	public AclfBranchBean() { }
	
	@Override public int compareTo(BaseJSONBean<TExt> b) {
		int eql = super.compareTo(b);
		
		AclfBranchBean<TExt> bean = (AclfBranchBean<TExt>)b;

		String str = "ID: " + this.id + " AclfBranchBean.";
		
		if (this.turnRatio.compareTo(bean.turnRatio) != 0) {
			logCompareMsg(str + "ratio is not equal");	eql = 1; }

		if (this.shiftAng.compareTo(bean.shiftAng) != 0) {
			logCompareMsg(str + "ang is not equal");	eql = 1; }
		
		if (this.xfrTapControl != null && bean.xfrTapControl == null)
			eql = 1;
		if (this.xfrTapControl == null && bean.xfrTapControl != null)
			eql = 1;
		if (this.xfrTapControl != null && bean.xfrTapControl != null)
			if (this.xfrTapControl.compareTo(bean.xfrTapControl) != 0) eql = 1;
		
		if (this.psXfrTapControl != null && bean.psXfrTapControl == null)
			eql = 1;
		if (this.psXfrTapControl == null && bean.psXfrTapControl != null)
			eql = 1;
		if (this.psXfrTapControl != null && bean.psXfrTapControl != null)
			if (this.psXfrTapControl.compareTo(bean.psXfrTapControl) != 0) eql = 1;

		return eql;
	}	
	
	@Override
	public boolean validate(List<String> msgList) { return true; }
}
