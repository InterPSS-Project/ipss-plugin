/*
 * @(#)AbstractODMOpfDataMapper.java   
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
import static org.interpss.CorePluginFunction.AclfXmlNet2AclfNet;
import static org.interpss.CorePluginFunction.DistXmlNet2DistNet;

import java.util.List;

import javax.xml.bind.JAXBElement;

import org.ieee.odm.model.base.BaseJaxbHelper;
import org.ieee.odm.schema.BranchBusSideEnumType;
import org.ieee.odm.schema.ChildNetInterfaceBranchXmlType;
import org.ieee.odm.schema.ChildNetworkDefXmlType;
import org.ieee.odm.schema.DistributionNetXmlType;
import org.ieee.odm.schema.LoadflowNetXmlType;
import org.ieee.odm.schema.NetworkXmlType;
import org.interpss.mapper.odm.ODMAclfNetMapper;

import com.interpss.CoreObjectFactory;
import com.interpss.DistObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.net.BranchBusSide;
import com.interpss.core.net.childnet.ChildNetInterfaceBranch;
import com.interpss.core.net.childnet.ChildNetwork;
import com.interpss.dist.DistBranch;
import com.interpss.dist.DistBus;
import com.interpss.dist.DistNetwork;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

/**
 * 
 * 
 * @author mzhou
 *
 */
public class MultiNetMapHelper {
	private SimuContext simuCtx;
	
	public MultiNetMapHelper(SimuContext simuCtx) {
		this.simuCtx = simuCtx;
	}

	public boolean mapChildNet(List<JAXBElement<? extends NetworkXmlType>> childNets, List<ChildNetworkDefXmlType> childNetDefList) {
		return mapChildNet(childNets, childNetDefList, ODMAclfNetMapper.XfrBranchModel.InterPSS);
	}
	
	public boolean mapChildNet(List<JAXBElement<? extends NetworkXmlType>> childNets, List<ChildNetworkDefXmlType> childNetDefList, ODMAclfNetMapper.XfrBranchModel xfrBranchModel) {
		if (this.simuCtx.getNetType() == SimuCtxType.ACLF_NETWORK) {
			AclfNetwork parentAclfNet = this.simuCtx.getAclfNet();
			for (ChildNetworkDefXmlType xmlChildDef : childNetDefList) {
				NetworkXmlType xmlChildNet = (NetworkXmlType)xmlChildDef.getChildNetRef().getIdRef();
				if (xmlChildNet != null) {
					try {
						if (xmlChildNet instanceof LoadflowNetXmlType) {
							mapAclfChildNet(parentAclfNet, xmlChildDef, xfrBranchModel);
						}
						else if (xmlChildNet instanceof DistributionNetXmlType) {
							mapDistChildNet(parentAclfNet, xmlChildDef);
						} 
					} catch (InterpssException e) {
						ipssLogger.severe(e.toString());
						return false;
					}
				}
				else {
					ipssLogger.severe("Child network reference cannot be located in the ChileNetList");
					return false;
				}
			}
			return true;
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
		DistNetwork childAclfNet = DistXmlNet2DistNet.fx((DistributionNetXmlType)xmlChildNet);
		childNetContainer.setNetwork(childAclfNet);	
		
		mapInterfaceBranch(childNetContainer, xmlChildDef);		
	}	
	
	private void mapInterfaceBranch(ChildNetwork<?,?> childNetContainer, ChildNetworkDefXmlType xmlChildDef) {
		for (ChildNetInterfaceBranchXmlType xmlInterBranch : xmlChildDef.getInterfaceBranch()) {
			ChildNetInterfaceBranch intBranch = CoreObjectFactory.createChildNetInerfaceBranch(childNetContainer);
			intBranch.setBranchId(BaseJaxbHelper.getRecId(xmlInterBranch.getBranch()));
			intBranch.setInterfaceBusSide(xmlInterBranch.getInterfaceBusSide() == BranchBusSideEnumType.FROM_SIDE?
					BranchBusSide.FROM_SIDE : BranchBusSide.TO_SIDE);		
			intBranch.setChildNetSide(xmlInterBranch.getChildNetSide() == BranchBusSideEnumType.FROM_SIDE?
					BranchBusSide.FROM_SIDE : BranchBusSide.TO_SIDE);
			if (xmlInterBranch.getInterfaceBusIdChildNet() != null)
				intBranch.setInterfaceBusIdChineNet(BaseJaxbHelper.getRecId(xmlInterBranch.getInterfaceBusIdChildNet()));
			childNetContainer.getInterfaceBranches().add(intBranch);
		}		
	}
}
