/*
 * @(#)AbstractODMNetDataMapper.java   
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

package org.interpss.mapper.odm;

import static com.interpss.common.util.IpssLogger.ipssLogger;
import static org.interpss.mapper.odm.ODMFunction.BusXmlRef2BusId;

import org.ieee.odm.schema.ApparentPowerUnitType;
import org.ieee.odm.schema.BaseBranchXmlType;
import org.ieee.odm.schema.BusXmlType;
import org.ieee.odm.schema.CimRdfXmlType;
import org.ieee.odm.schema.NetworkXmlType;
import org.ieee.odm.schema.VoltageUnitType;

import com.interpss.CoreObjectFactory;
import com.interpss.common.datatype.Constants;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.mapper.AbstractMapper;
import com.interpss.core.aclf.Aclf3WBranch;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.net.Area;
import com.interpss.core.net.Branch;
import com.interpss.core.net.Branch3W;
import com.interpss.core.net.Bus;
import com.interpss.core.net.CimRecord;
import com.interpss.core.net.Network;
import com.interpss.core.net.Zone;

/**
 * Base class for implementing mapper from ODM model to Network object 
 * 
 * @author mzhou
 *
 * @param <Tfrom> a ODM parser object 
 * @param <Tto> a Network object (AclfNetwork, AcscNetwork)
 */
public abstract class AbstractODMNetDataMapper<Tfrom, Tto> extends AbstractMapper<Tfrom, Tto> {
	/**
	 * constructor
	 */
	public AbstractODMNetDataMapper() {
	}
	
	/**
	 * Map the network info only
	 * 
	 * @param xmlNet
	 * @return
	 */
	public void mapNetworkData(Network<?,?> net, NetworkXmlType xmlNet) {
		net.setId(xmlNet.getId());
		net.setName(xmlNet.getName() == null? "ODM Loadflow Case" : xmlNet.getName());
		net.setDesc(xmlNet.getDesc());
		net.setBaseKva(xmlNet.getBasePower().getValue()*
				(xmlNet.getBasePower().getUnit()==ApparentPowerUnitType.MVA?1000.0:1.0));
				// BasePowerUnit [ MVA, KVA]
		//net.setAllowParallelBranch(true);
	}
	
	/**
	 * transfer the info stored in the bus xml record to the bus object
	 * 
	 * @param busRec
	 * @param bus
	 * @param net
	 */
	public void mapBaseBusData(BusXmlType busRec, Bus bus, Network<?,?> net) {
		if (busRec.getNumber() != null)
			bus.setNumber(busRec.getNumber());
		bus.setName(busRec.getName() == null? "Bus" : busRec.getName());
		bus.setDesc(busRec.getDesc() == null? "Bus Desc" : busRec.getDesc());
		bus.setStatus(busRec.isOffLine() != null? !busRec.isOffLine() : true);
		if (!bus.isActive()) {
			ipssLogger.info("Bus is not active, " + bus.getId());
		}
		
		if (busRec.getCimRdfRecords() != null && busRec.getCimRdfRecords().getRdfRec().size() > 0) {
			for (CimRdfXmlType cimRec : busRec.getCimRdfRecords().getRdfRec()) {
				CimRecord rec = CoreObjectFactory.createCimRecod(cimRec.getRdfId(), cimRec.getName());
				bus.getCimRec().add(rec);
			}
		}
		
		bus.setDesc(busRec.getDesc());
		if (busRec.getAreaNumber() != null) {
			Area area = CoreObjectFactory.createArea(busRec.getAreaNumber(), net);
			bus.setArea(area);
		}
		
		if (busRec.getZoneNumber() != null) {
			Zone zone = CoreObjectFactory.createZone(busRec.getZoneNumber(), net);
			bus.setZone(zone);
		}
		
		// for DcSystem, baseVoltage might defined at Net level
		if (busRec.getBaseVoltage() != null) {
			bus.setBaseVoltage(busRec.getBaseVoltage().getUnit()==VoltageUnitType.KV ?    // Base V unit [KV, Volt] 
					busRec.getBaseVoltage().getValue()*1000.0 : 
					busRec.getBaseVoltage().getValue());
		}
	}

	/**
	 * transfer the info stored in the branch xml rec to the branch object
	 * 
	 * @param branchRec
	 * @param branch
	 * @param net
	 * @throws InterpssException
	 */
	public void mapBaseBranchRec(BaseBranchXmlType branchRec, Branch branch, Network net) throws InterpssException {
		String cirId = branchRec.getCircuitId() != null ?
				branchRec.getCircuitId() : Constants.Token_DefaultBranchCirNo;
		branch.setCircuitNumber(cirId);
		try {
			String fromBusId = BusXmlRef2BusId.fx(branchRec.getFromBus());
			String toBusId = BusXmlRef2BusId.fx(branchRec.getToBus());
			if (branch instanceof Branch3W) {
				String tertBusId = BusXmlRef2BusId.fx(branchRec.getTertiaryBus());
				((BaseAclfNetwork)net).add3WXfr((Aclf3WBranch)branch, fromBusId, toBusId, tertBusId);
			}
			else
				net.addBranch(branch, fromBusId, toBusId);
		} catch (Exception e) {
			e.printStackTrace();
			throw new InterpssException(e.toString() + ", the branch is ignored " +
					(branchRec.getFromBus().getIdRef() == null? "null" : BusXmlRef2BusId.fx(branchRec.getFromBus()) + " -> " + 
					(branchRec.getToBus().getIdRef() == null? "null" : BusXmlRef2BusId.fx(branchRec.getToBus()))));
		}

		branch.setName(branchRec.getName() == null ? "" : branchRec.getName());
		branch.setDesc(branchRec.getDesc() == null ? "" : branchRec.getDesc());
		branch.setStatus(branchRec.isOffLine() != null ? !branchRec.isOffLine() : true);
		if (!branch.isActive()) {
			ipssLogger.info("Branch is not active, " + branch.getId());
		}
		if (branchRec.getAreaNumber() != null) {
			Area area = CoreObjectFactory.createArea(branchRec.getAreaNumber(), net);
			branch.setArea(area);
		}

		if (branchRec.getZoneNumber() != null) {
			Zone zone = CoreObjectFactory.createZone(branchRec.getZoneNumber(), net);
			branch.setZone(zone);
		}
	}
}