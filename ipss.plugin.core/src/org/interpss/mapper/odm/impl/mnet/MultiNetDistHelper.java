/*
 * @(#)MultiNetDistHelper.java   
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
 * @Date 02/15/2008
 * 
 *   Revision History
 *   ================
 *
 */

package org.interpss.mapper.odm.impl.mnet;

import static com.interpss.common.util.IpssLogger.ipssLogger;
import static org.interpss.CorePluginFunction.DcSysXmlNet2DcSysNet;

import java.util.List;

import org.ieee.odm.schema.ChildNetworkDefXmlType;
import org.ieee.odm.schema.DcNetworkXmlType;
import org.ieee.odm.schema.NetworkXmlType;

import com.interpss.DistObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.net.childnet.ChildNetwork;
import com.interpss.dc.DcBranch;
import com.interpss.dc.DcBus;
import com.interpss.dc.DcNetwork;
import com.interpss.dist.DistNetwork;

/**
 * for processing DistNet parent net. Supported child net types : 1) DcSysNet
 * 
 * @author mzhou
 *
 */
public class MultiNetDistHelper extends BaseMultiNetHelper {
	private DistNetwork parentDistNet;
	
	public MultiNetDistHelper(DistNetwork net) {
		this.parentDistNet = net;
	}

	public boolean mapChildNet(List<ChildNetworkDefXmlType> childNetDefList) {
		try {
			for (ChildNetworkDefXmlType xmlChildDef : childNetDefList) {
				NetworkXmlType xmlChildNet = (NetworkXmlType)xmlChildDef.getChildNetRef().getIdRef();
				if (xmlChildNet != null) {
					if (xmlChildNet instanceof DcNetworkXmlType) {
						mapDcSysChildNet(parentDistNet, xmlChildDef);
					} 
					else 
						ipssLogger.warning("Only DcSysNet could be defined as Child network of DistNet");
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

	private void mapDcSysChildNet(DistNetwork parentDistNet, ChildNetworkDefXmlType xmlChildDef) throws InterpssException {
		NetworkXmlType xmlChildNet = (NetworkXmlType)xmlChildDef.getChildNetRef().getIdRef();
		ChildNetwork<DcBus,DcBranch> childNetContainer = DistObjectFactory.createChildDcSysNet(parentDistNet, xmlChildNet.getId());
		DcNetwork childDcSysNet = DcSysXmlNet2DcSysNet.fx((DcNetworkXmlType)xmlChildNet);
		childNetContainer.setNetwork(childDcSysNet);	
		
		mapInterfaceBranch(childNetContainer, xmlChildDef);		
	}
}
