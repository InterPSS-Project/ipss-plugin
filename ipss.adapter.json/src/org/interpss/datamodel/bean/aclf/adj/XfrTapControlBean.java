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

import org.interpss.datamodel.bean.base.BaseJSONBean;
import org.interpss.datamodel.bean.base.BaseJSONUtilBean;

/**
 * Bean class for storing Aclf two winding branch object info
 * 
 * @author sHou
 *
 */
public class XfrTapControlBean<TExt extends BaseJSONUtilBean> extends BaseTapControlBean<TExt> {
		
	//public TapControlModeBean controlMode = TapControlModeBean.Bus_Voltage;	// control mode		
	
	public String controlledBusId = "0";		// controlled bus id
	
	public XfrTapControlBean() {}
	
	@Override public int compareTo(BaseJSONBean<TExt> b) {
		
		int eql = super.compareTo(b);
		
		XfrTapControlBean<TExt> bean = (XfrTapControlBean<TExt>)b;

		String str = "ID: " + this.id + " XfrTapControlBean.";				
		
		if (this.controlMode != bean.controlMode) {
			logCompareMsg(str + "control mode is not equal, " + this.controlMode + ", " + bean.controlMode); eql = 1; }
		
		if (!this.controlledBusId.equals(bean.controlledBusId)) {
			logCompareMsg(str + "controlledBusNumber is not equal, " + this.controlledBusId + ", " + bean.controlledBusId); eql = 1; }
		
		
		return eql;
	}	
	
	@Override
	public boolean validate(List<String> msgList) { return true; }
}
