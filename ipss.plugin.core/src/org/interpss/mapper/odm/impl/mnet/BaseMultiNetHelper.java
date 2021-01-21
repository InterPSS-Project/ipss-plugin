/*
 * @(#)BaseMultiNetHelper.java   
 *
 * Copyright (C) 2008 www.interpss.org
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
 * @Date 11/15/2013
 * 
 *   Revision History
 *   ================
 *
 */

package org.interpss.mapper.odm.impl.mnet;

import org.ieee.odm.model.base.BaseJaxbHelper;
import org.ieee.odm.schema.BranchBusSideEnumType;
import org.ieee.odm.schema.ChildNetInterfaceBranchXmlType;
import org.ieee.odm.schema.ChildNetworkDefXmlType;

import com.interpss.core.CoreObjectFactory;
import com.interpss.core.net.BranchBusSide;
import com.interpss.core.net.childnet.ChildNetInterfaceBranch;
import com.interpss.core.net.childnet.ChildNetwork;

/**
 * 
 * 
 * @author mzhou
 *
 */
public class BaseMultiNetHelper {
	protected void mapInterfaceBranch(ChildNetwork<?,?> childNetContainer, ChildNetworkDefXmlType xmlChildDef) {
		for (ChildNetInterfaceBranchXmlType xmlInterBranch : xmlChildDef.getInterfaceBranch()) {
			ChildNetInterfaceBranch intBranch = CoreObjectFactory.createChildNetInerfaceBranch(childNetContainer);
			intBranch.setBranchId(BaseJaxbHelper.getRecId(xmlInterBranch.getBranch()));
			intBranch.setInterfaceBusSide(xmlInterBranch.getInterfaceBusSide() == BranchBusSideEnumType.FROM_SIDE?
					BranchBusSide.FROM_SIDE : BranchBusSide.TO_SIDE);		
			intBranch.setChildNetSide(xmlInterBranch.getChildNetSide() == BranchBusSideEnumType.FROM_SIDE?
					BranchBusSide.FROM_SIDE : BranchBusSide.TO_SIDE);
			if (xmlInterBranch.getInterfaceBusIdChildNet() != null)
				intBranch.setInterfaceBusIdChildNet(BaseJaxbHelper.getRecId(xmlInterBranch.getInterfaceBusIdChildNet()));
			//childNetContainer.getInterfaceBranches().add(intBranch);
		}		
	}
}
