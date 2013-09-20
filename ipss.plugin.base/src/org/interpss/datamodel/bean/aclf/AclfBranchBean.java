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
package org.interpss.datamodel.bean.aclf;

import java.util.List;

import org.interpss.datamodel.bean.BaseBranchBean;
import org.interpss.datamodel.bean.BaseJSONBean;
import org.interpss.datamodel.bean.datatype.BranchValueBean;

import com.interpss.common.util.IpssLogger;

/**
 * Bean class for storing AclfBranch object info
 * 
 * @author mzhou
 *
 */
public class AclfBranchBean extends BaseBranchBean {
	
	public BranchValueBean 
			ratio = new BranchValueBean(1.0,1.0),			// xfr branch turn ratio
			ang = new BranchValueBean(0.0,0.0);				// PsXfr shifting angle, in rad
	
	
	public AclfBranchBean() {}
	
	@Override public int compareTo(BaseJSONBean b) {
		int eql = super.compareTo(b);
		
		AclfBranchBean bean = (AclfBranchBean)b;

		String str = "ID: " + this.id + " AclfBranchBean.";
		
		if (this.ratio.compareTo(bean.ratio) != 0) {
			IpssLogger.ipssLogger.warning(str + "ratio is not equal");	eql = 1; }

		if (this.ang.compareTo(bean.ang) != 0) {
			IpssLogger.ipssLogger.warning(str + "ang is not equal");	eql = 1; }

		return eql;
	}	
	
	public boolean validate(List<String> msgList) { return true; }
}
