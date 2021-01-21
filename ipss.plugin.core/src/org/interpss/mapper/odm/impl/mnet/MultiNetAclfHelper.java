/*
 * @(#)MultiNetAclfHelper.java   
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

import static com.interpss.common.util.IpssLogger.ipssLogger;
import static org.interpss.CorePluginFunction.AclfXmlNet2AclfNet;
import static org.interpss.CorePluginFunction.DistXmlNet2DistNet;

import java.util.List;

import org.ieee.odm.schema.ChildNetworkDefXmlType;
import org.ieee.odm.schema.DistributionNetXmlType;
import org.ieee.odm.schema.LoadflowNetXmlType;
import org.ieee.odm.schema.NetworkXmlType;
import org.interpss.mapper.odm.ODMAclfNetMapper;

import com.interpss.DistObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.net.childnet.ChildNetwork;
import com.interpss.dist.DistBranch;
import com.interpss.dist.DistBus;
import com.interpss.dist.DistNetwork;

/**
 * AclfNet parent net Supported child net types: 1) AclfNet; 2) DistNet 
 * 
 * @author mzhou
 *
 */
public class MultiNetAclfHelper extends BaseMultiNetHelper {
	private AclfNetwork parentAclfNet;
	
	public MultiNetAclfHelper(AclfNetwork net) {
		this.parentAclfNet = net;
	}

	public boolean mapChildNet(List<ChildNetworkDefXmlType> childNetDefList, ODMAclfNetMapper.XfrBranchModel xfrBranchModel) {
		try {
			for (ChildNetworkDefXmlType xmlChildDef : childNetDefList) {
				NetworkXmlType xmlChildNet = (NetworkXmlType)xmlChildDef.getChildNetRef().getIdRef();
				if (xmlChildNet != null) {
					if (xmlChildNet instanceof LoadflowNetXmlType) {
						mapAclfChildNet(parentAclfNet, xmlChildDef, xfrBranchModel);
					}
					else if (xmlChildNet instanceof DistributionNetXmlType) {
						mapDistChildNet(parentAclfNet, xmlChildDef);
					} 
					else
						ipssLogger.warning("Only AclfNet and DistNet could be defined as Child network of AclfNet");
				}
				else {
					ipssLogger.severe("Child network reference cannot be located in the ChileNetList");
					return false;
				}
			}
			return true;
		} catch (InterpssException e) {
			ipssLogger.severe(e.toString());
		}
		return false;
	}
	
	private void mapAclfChildNet(AclfNetwork parentAclfNet, ChildNetworkDefXmlType xmlChildDef, ODMAclfNetMapper.XfrBranchModel xfrBranchModel) throws InterpssException {
		NetworkXmlType xmlChildNet = (NetworkXmlType)xmlChildDef.getChildNetRef().getIdRef();
		ChildNetwork<AclfBus,AclfBranch> childNetContainer = CoreObjectFactory.createChildAclfNet(parentAclfNet, xmlChildNet.getId());
		AclfNetwork childAclfNet = AclfXmlNet2AclfNet.fx((LoadflowNetXmlType)xmlChildNet, xfrBranchModel);
		childNetContainer.setNetwork(childAclfNet);	
		
		mapInterfaceBranch(childNetContainer, xmlChildDef);		
	}
	
	private void mapDistChildNet(AclfNetwork parentAclfNet, ChildNetworkDefXmlType xmlChildDef) throws InterpssException {
		NetworkXmlType xmlChildNet = (NetworkXmlType)xmlChildDef.getChildNetRef().getIdRef();
		ChildNetwork<DistBus,DistBranch> childNetContainer = DistObjectFactory.createChildDistNet(parentAclfNet, xmlChildNet.getId());
		DistNetwork childDistNet = DistXmlNet2DistNet.fx((DistributionNetXmlType)xmlChildNet);
		childNetContainer.setNetwork(childDistNet);	
		
		mapInterfaceBranch(childNetContainer, xmlChildDef);		
	}	
}
