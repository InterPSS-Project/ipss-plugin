/*
 * @(#)AbstractODMAclfDataMapper.java   
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

package org.interpss.mapper.odm.impl.aclf;

import static com.interpss.common.util.IpssLogger.ipssLogger;
import static org.interpss.mapper.odm.ODMUnitHelper.toActivePowerUnit;

import java.util.List;

import javax.xml.bind.JAXBElement;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.schema.ApparentPowerUnitType;
import org.ieee.odm.schema.BaseBranchXmlType;
import org.ieee.odm.schema.BranchBusSideEnumType;
import org.ieee.odm.schema.BranchXmlType;
import org.ieee.odm.schema.BusXmlType;
import org.ieee.odm.schema.DCLineData2TXmlType;
import org.ieee.odm.schema.FlowInterfaceBranchXmlType;
import org.ieee.odm.schema.FlowInterfaceEnumType;
import org.ieee.odm.schema.FlowInterfaceRecXmlType;
import org.ieee.odm.schema.LineBranchXmlType;
import org.ieee.odm.schema.LoadflowBusXmlType;
import org.ieee.odm.schema.LoadflowNetXmlType;
import org.ieee.odm.schema.NameValuePairXmlType;
import org.ieee.odm.schema.PSXfr3WBranchXmlType;
import org.ieee.odm.schema.PSXfrBranchXmlType;
import org.ieee.odm.schema.PWDNetworkExtXmlType;
import org.ieee.odm.schema.VSCHVDC2TXmlType;
import org.ieee.odm.schema.XformerZTableXmlType;
import org.ieee.odm.schema.Xfr3WBranchXmlType;
import org.ieee.odm.schema.XfrBranchXmlType;
import org.interpss.ext.pwd.AclfBranchPWDExtension;
import org.interpss.ext.pwd.AclfBusPWDExtension;
import org.interpss.mapper.odm.AbstractODMSimuCtxDataMapper;
import org.interpss.mapper.odm.ODMAclfNetMapper;
import org.interpss.numeric.datatype.LimitType;
import org.interpss.numeric.datatype.Point;
import org.interpss.numeric.datatype.Unit.UnitType;

import com.interpss.common.datatype.UnitHelper;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.aclf.XfrZTableEntry;
import com.interpss.core.aclf.flow.FlowInterface;
import com.interpss.core.aclf.flow.FlowInterfaceBranch;
import com.interpss.core.aclf.flow.FlowInterfaceLimit;
import com.interpss.core.aclf.flow.FlowInterfaceType;
import com.interpss.core.aclf.hvdc.HvdcLine2TVSC;
import com.interpss.core.aclf.hvdc.HvdcOperationMode;
import com.interpss.core.aclf.hvdc.HvdcLine2TLCC;
import com.interpss.core.net.Branch;
import com.interpss.core.net.BranchBusSide;
import com.interpss.core.net.OriginalDataFormat;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

/**
 * abstract mapper implementation to map ODM LoadflowNetXmlType object to InterPSS AclfNetwork object
 * 
 * @author mzhou
 * @param Tfrom from object type
 */
public abstract class AbstractODMAclfNetMapper<Tfrom> extends AbstractODMSimuCtxDataMapper<Tfrom> {
	private ODMAclfNetMapper.XfrBranchModel xfrBranchModel = ODMAclfNetMapper.XfrBranchModel.InterPSS;
	private OriginalDataFormat originalFormat = OriginalDataFormat.IPSS_API;
	
	/**
	 * constructor
	 * 
	 */
	public AbstractODMAclfNetMapper() {
	}
	
	/**
	 * set xformer branch model
	 * 
	 * @param xfrBranchModel
	 */
	public void setXfrBranchModel(ODMAclfNetMapper.XfrBranchModel xfrBranchModel) {
		this.xfrBranchModel = xfrBranchModel;
	}

	/**
	 * set the original format 
	 * 
	 * @param format
	 */
	public void setOriginalDataFormat(OriginalDataFormat format) {
		this.originalFormat = format;
	}

	/**
	 * map into store in the ODM parser into simuCtx object
	 * 
	 * @param p a LoadflowNetXmlType object, representing a aclf base network
	 * @param simuCtx
	 */
	@Override public boolean map2Model(Tfrom p, SimuContext simuCtx) {
		boolean noError = true;
		LoadflowNetXmlType xmlNet = (LoadflowNetXmlType)p; 
		simuCtx.setNetType(SimuCtxType.ACLF_NETWORK);
		try {
			AclfNetwork aclfNet = CoreObjectFactory.createAclfNetwork();
			aclfNet.setOriginalDataFormat(this.originalFormat);		
			
			mapAclfNetworkData(aclfNet, xmlNet);
			simuCtx.setAclfNet((AclfNetwork)aclfNet);

			//XformerZTableXmlType xfrZTable = xmlNet.getXfrZTable();
			AclfBusDataHelper busHelper = new AclfBusDataHelper(aclfNet);
			for (JAXBElement<? extends BusXmlType> bus : xmlNet.getBusList().getBus()) {
				LoadflowBusXmlType busRec = (LoadflowBusXmlType) bus.getValue();
				AclfBus aclfBus = CoreObjectFactory.createAclfBus(busRec.getId(), aclfNet);
				mapAclfBusData(busRec, aclfBus, aclfNet, busHelper);
				//System.out.println("map bus " + aclfBus.getId());
			}
			// TODO 
			// process remote bus reference. Remote reference bus of a bus might be located behind 
			// the bus date record
			
			int cnt = 0;
			for (JAXBElement<? extends BaseBranchXmlType> b : xmlNet.getBranchList().getBranch()) {
				/*
				if (b == null) {
					System.out.println("xxxxxxxxxx");
				}
				*/
				
				BaseBranchXmlType xmlBranch = b.getValue();
				//System.out.println(xmlBranch.getName() + ", " + xmlBranch.getId() + ", " + cnt++);
				Branch branch = null;
				if (xmlBranch instanceof PSXfr3WBranchXmlType || xmlBranch instanceof Xfr3WBranchXmlType)
					branch = CoreObjectFactory.createAclf3WXformer();
				else if(xmlBranch instanceof DCLineData2TXmlType)
					branch = CoreObjectFactory.createHvdcLine2TLCC(HvdcOperationMode.REC1_INV1);
				else if(xmlBranch instanceof VSCHVDC2TXmlType)
					branch = CoreObjectFactory.createHvdc2TVSC();
				else 
					branch = CoreObjectFactory.createAclfBranch();
				
				if(	xmlBranch instanceof DCLineData2TXmlType || xmlBranch instanceof VSCHVDC2TXmlType)
					mapAclfHVDC2TData(xmlBranch, branch, aclfNet);
					
			    else 
				   mapAclfBranchData(xmlBranch, branch, aclfNet);
				
				if (this.originalFormat == OriginalDataFormat.IEEECDF) {
					aclfNet.getBusList().forEach(bus -> {
						if (bus.isGenPQ() && 
								new Complex(bus.getGenP(), bus.getGenQ()).abs() == 0.0 &&
								bus.getContributeGenList().size() == 0)
							bus.setGenCode(AclfGenCode.NON_GEN);
					});					
				}
				
				//System.out.println("map branch " + branch.getId());
			}
			
			/*
			 * a child aclf net cannot contain any child network 
			 */
			
			// process CA monitoring status. The function only applies to PWD
			if (this.originalFormat == OriginalDataFormat.PWD) {
				if (xmlNet.getExtension() != null) {
					PWDNetworkExtXmlType pwdNetExt = (PWDNetworkExtXmlType)xmlNet.getExtension();
					PWDNetworkExtXmlType.LimitSets limitSets = pwdNetExt.getLimitSets();
					if (limitSets != null) {
						LimitSetHelper helper = new LimitSetHelper(limitSets);
						for (AclfBranch branch : aclfNet.getBranchList()) {
							AclfBranchPWDExtension ext = (AclfBranchPWDExtension)branch.getExtensionObject();
							boolean isDisabled = helper.isDisabled(ext.getLSName());
							ext.setCaMonitoring(!isDisabled && ext.getLineMonEle().toLowerCase().equals("yes"));
						}
					}
				}
			}
			
			postAclfNetProcessing(aclfNet);
			
		} catch (InterpssException e) {
			ipssLogger.severe(e.toString());
			noError = false;
		}

		return noError;
	}
	
	public static void postAclfNetProcessing(BaseAclfNetwork<?,?> aclfNet) throws InterpssException {
		aclfNet.adjustXfrZ();
		
		aclfNet.initContributeGenLoad(false);
	}

	/**
	 * Map the network info only
	 * 
	 * @param xmlNet
	 * @return
	 */
	public void mapAclfNetworkData(BaseAclfNetwork<?,?> net, LoadflowNetXmlType xmlNet) throws InterpssException {
		mapNetworkData(net, xmlNet);
		
		// map Xfr Z Table
		XformerZTableXmlType xfrZTable = xmlNet.getXfrZTable();
		if (xfrZTable != null) {
			net.setXfrZAdjustSide(xfrZTable.getAdjustSide() == BranchBusSideEnumType.FROM_SIDE?
					         BranchBusSide.FROM_SIDE : BranchBusSide.TO_SIDE);
			for (XformerZTableXmlType.XformerZTableItem item : xfrZTable.getXformerZTableItem()) {
				XfrZTableEntry elem = CoreObjectFactory.createXfrZTableEntry(item.getNumber(), net);
				for (XformerZTableXmlType.XformerZTableItem.Lookup point : item.getLookup()) {
					elem.getPointSet().getPoints().add(new Point(point.getTurnRatioShiftAngle(), point.getScaleFactor()));
				}
			}	
		}
		
		if (xmlNet.getBusVLimit() != null)
			net.setDefaultVoltageLimit(new LimitType(xmlNet.getBusVLimit().getMax(), xmlNet.getBusVLimit().getMin()));
	}
	
	/**
	 * map interface info to the AclfNet object
	 * 
	 * @param net
	 * @param xmlIntList
	 */
	public void mapInterfaceData(AclfNetwork net, List<FlowInterfaceRecXmlType> xmlIntList) {
		if (net.isFlowInterfaceLoaded()) {
			net.getFlowInterfaceList().clear();
			net.setFlowInterfaceLoaded(false);
		}
		
		for (FlowInterfaceRecXmlType xmlIntf : xmlIntList ) {
			FlowInterface intf = CoreObjectFactory.createInterface(net, xmlIntf.getId());

			for ( FlowInterfaceBranchXmlType xmlBra : xmlIntf.getBranchList()) {
				FlowInterfaceBranch branch = CoreObjectFactory.createInterfaceBranch(intf);
				AclfBranch b = net.getBranch(xmlBra.getFromBusId(), xmlBra.getToBusId(), xmlBra.getCircuitId());
				if (b == null) {
					b = net.getBranch(xmlBra.getToBusId(), xmlBra.getFromBusId(), xmlBra.getCircuitId());
					branch.setBranchDir(false);
				}
				else
					branch.setBranchDir(true);
				
				if (b == null) {
					ipssLogger.severe("Branch in the interface not found, " +
							xmlBra.getFromBusId() + ", " + xmlBra.getToBusId() + ", " + xmlBra.getCircuitId());
				}
				else {
					branch.setBranch(b);
					branch.setWeight(xmlBra.getWeight());
				}
			}
			
			/*
			 * It is possible the interface is loaded without the interface limit
			 * info.
			 */
			if (xmlIntf.getOffPeakLimit() != null) {
				FlowInterfaceLimit onPeak = CoreObjectFactory.createInterfaceLimit();
				intf.setOnPeakLimit(onPeak);
				map(xmlIntf, onPeak, net.getBaseKva());
			}
			
			if (xmlIntf.getOnPeakLimit() != null) {
				FlowInterfaceLimit offPeak = CoreObjectFactory.createInterfaceLimit();
				intf.setOffPeakLimit(offPeak);
				map(xmlIntf, offPeak, net.getBaseKva());
			}
			
			net.setFlowInterfaceLoaded(true);
		}
	}

	private void map(FlowInterfaceRecXmlType xmlIntf, FlowInterfaceLimit peak, double baseKav) {
		peak.setStatus(xmlIntf.getOnPeakLimit().isStatus());
		peak.setType(xmlIntf.getOnPeakLimit().getType()==FlowInterfaceEnumType.BG? FlowInterfaceType.BG : 
				xmlIntf.getOnPeakLimit().getType()==FlowInterfaceEnumType.NG? FlowInterfaceType.NG : FlowInterfaceType.TOR);
		peak.setRefDirExportLimit(UnitHelper.pConversion(
				xmlIntf.getOnPeakLimit().getRefDirExportLimit().getValue(), baseKav, 
				toActivePowerUnit.apply(xmlIntf.getOnPeakLimit().getRefDirExportLimit().getUnit()), UnitType.PU));
		peak.setOppsiteRefDirImportLimit(UnitHelper.pConversion(
				xmlIntf.getOnPeakLimit().getOppsiteRefDirImportLimit().getValue(), baseKav, 
				toActivePowerUnit.apply(xmlIntf.getOnPeakLimit().getOppsiteRefDirImportLimit().getUnit()), UnitType.PU));
	}
	
	/**
	 * Map the bus record
	 * 
	 * @param xmlBusRec
	 * @param adjNet
	 * @return
	 * @throws Exception
	 */
	public AclfBus mapAclfBusData(LoadflowBusXmlType xmlBusRec, AclfBus aclfBus, AclfNetwork adjNet, AclfBusDataHelper helper) throws InterpssException {
		if (adjNet.getOriginalDataFormat() == OriginalDataFormat.PWD) {
			AclfBusPWDExtension ext = new AclfBusPWDExtension();
			aclfBus.setExtensionObject(ext);
			for ( NameValuePairXmlType nv : xmlBusRec.getNvPair()) {
				ext.put(nv.getName(), nv.getValue());
			}
		}

		mapBaseBusData(xmlBusRec, aclfBus, adjNet);

		helper.setBus(aclfBus);
		helper.setAclfBusData(xmlBusRec);
		
		return aclfBus;
	}
	
	/**
	 * mapp the branch record
	 * 
	 * @param xmlBranch
	 * @param adjNet
	 * @param msg
	 * @throws Exception
	 */
	public void mapAclfBranchData(BaseBranchXmlType xmlBranch, Branch branch, BaseAclfNetwork<?,?> adjNet) throws InterpssException {
		if (adjNet.getOriginalDataFormat() == OriginalDataFormat.PWD) {
			AclfBranchPWDExtension ext = new AclfBranchPWDExtension();
			branch.setExtensionObject(ext);
			for ( NameValuePairXmlType nv : xmlBranch.getNvPair()) {
				ext.put(nv.getName(), nv.getValue());
			}
		}

		setAclfBranchData((BranchXmlType)xmlBranch, branch, adjNet);
		AclfBranchDataHelper helper = new AclfBranchDataHelper(adjNet, branch, this.xfrBranchModel);
		if (xmlBranch instanceof LineBranchXmlType) {
			LineBranchXmlType branchRec = (LineBranchXmlType) xmlBranch;
			helper.setLineBranchData(branchRec);
		}
		else if (xmlBranch instanceof PSXfr3WBranchXmlType) {
			PSXfr3WBranchXmlType branchRec = (PSXfr3WBranchXmlType) xmlBranch;
			helper.setPsXfr3WBranchData(branchRec);
		}		
		else if (xmlBranch instanceof Xfr3WBranchXmlType) {
			Xfr3WBranchXmlType branchRec = (Xfr3WBranchXmlType) xmlBranch;
			helper.setXfr3WBranchData(branchRec);
		}
		else if (xmlBranch instanceof PSXfrBranchXmlType) {
			PSXfrBranchXmlType branchRec = (PSXfrBranchXmlType) xmlBranch;
			helper.setPsXfrBranchData(branchRec);
		}		
		else if (xmlBranch instanceof XfrBranchXmlType) {
			XfrBranchXmlType branchRec = (XfrBranchXmlType) xmlBranch;
			helper.setXfrBranchData(branchRec);
		}
		else if (xmlBranch instanceof DCLineData2TXmlType){
			DCLineData2TXmlType branchRec =  (DCLineData2TXmlType) xmlBranch;
			AclfHvdcDataHelper  hvdcHelper =new AclfHvdcDataHelper(adjNet, (HvdcLine2TLCC) branch);
			hvdcHelper.setLCCHvdcData(branchRec);
		}
		else if(xmlBranch instanceof VSCHVDC2TXmlType){
			VSCHVDC2TXmlType   branchRec =   (VSCHVDC2TXmlType) xmlBranch;
			AclfHvdcDataHelper  hvdcHelper =new AclfHvdcDataHelper(adjNet, (HvdcLine2TVSC) branch);
			hvdcHelper.setVSCHvdcData(branchRec);
			
		}
	}
	
	
	/**
	 * mapp the branch record
	 * 
	 * @param xmlBranch
	 * @param adjNet
	 * @param msg
	 * @throws Exception
	 */
	public void mapAclfHVDC2TData(BaseBranchXmlType xmlBranch, Branch branch, BaseAclfNetwork<?,?> adjNet) throws InterpssException {
		//net.addHvdcLine2T(dcLine, fromBusId, toBusId, dcLineNo);
		//dcLine.setDcLineNumber(dcLineNo);
		mapBaseBranchRec(xmlBranch, branch, adjNet);	
		if (xmlBranch instanceof DCLineData2TXmlType){
			DCLineData2TXmlType branchRec =  (DCLineData2TXmlType) xmlBranch;
			AclfHvdcDataHelper  hvdcHelper =new AclfHvdcDataHelper(adjNet, (HvdcLine2TLCC) branch);
			hvdcHelper.setLCCHvdcData(branchRec);
		}
		else if(xmlBranch instanceof VSCHVDC2TXmlType){
			VSCHVDC2TXmlType   branchRec =   (VSCHVDC2TXmlType) xmlBranch;
			AclfHvdcDataHelper  hvdcHelper =new AclfHvdcDataHelper(adjNet, (HvdcLine2TVSC) branch);
			hvdcHelper.setVSCHvdcData(branchRec);
			
		}
	}
	
	private void setAclfBranchData(BranchXmlType xmlBranchRec, Branch branch, BaseAclfNetwork<?,?> adjNet) throws InterpssException {
		mapBaseBranchRec(xmlBranchRec, branch, adjNet);		
		if (branch instanceof AclfBranch) {
			AclfBranch aclfBranch = (AclfBranch)branch;
			if (xmlBranchRec.getRatingLimit() != null && xmlBranchRec.getRatingLimit().getMva() != null) {
				double factor = 1.0;
				if (xmlBranchRec.getRatingLimit().getMva().getUnit() == ApparentPowerUnitType.PU)
					factor = adjNet.getBaseKva() * 0.001;
				else if (xmlBranchRec.getRatingLimit().getMva().getUnit() == ApparentPowerUnitType.KVA)
					factor = 0.001;
				aclfBranch.setRatingMva1(xmlBranchRec.getRatingLimit().getMva().getRating1() * factor);
				if (xmlBranchRec.getRatingLimit().getMva().getRating2() != null)
					aclfBranch.setRatingMva2(xmlBranchRec.getRatingLimit().getMva().getRating2() * factor);
				if (xmlBranchRec.getRatingLimit().getMva().getRating3() != null)
					aclfBranch.setRatingMva3(xmlBranchRec.getRatingLimit().getMva().getRating3() * factor);
				//if (branchRec.getRatingLimit().getMva().getRating4())
				//	aclfBranch.setRatingMva4(branchRec.getRatingLimit().getMva().getRating3() * factor);
			}
		}
	}
}