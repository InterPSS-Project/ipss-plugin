 /*
  * @(#)IpssDist.java   
  *
  * Copyright (C) 2006-2011 www.interpss.com
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
  * @Date 04/15/2009
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.pssl.simu.net;

import org.interpss.numeric.datatype.Unit.UnitType;

import com.interpss.CoreObjectFactory;
import com.interpss.DistObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.acsc.AcscNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.net.Area;
import com.interpss.core.net.Zone;
import com.interpss.dist.DistBranch;
import com.interpss.dist.DistBranchCode;
import com.interpss.dist.DistBus;
import com.interpss.dist.DistBusCode;
import com.interpss.dist.DistNetwork;
import com.interpss.dist.adpter.DistBreaker;
import com.interpss.dist.adpter.DistFeeder;
import com.interpss.dist.adpter.DistGenerator;
import com.interpss.dist.adpter.DistIndMotor;
import com.interpss.dist.adpter.DistMixedLoad;
import com.interpss.dist.adpter.DistNonContribBus;
import com.interpss.dist.adpter.DistReactor;
import com.interpss.dist.adpter.DistSynMotor;
import com.interpss.dist.adpter.DistUtility;
import com.interpss.dist.adpter.DistXformer;

/**
 * a wrapper of DistNetwork for defining network parameters
 * 
 * @author mzhou
 *
 */
public class IpssDistNet {
	// ================ public methods =======================
	
	/**
	 * create DistNetwork DSL
	 * 
	 * @param id
	 * @return
	 */
	public static DistNetDSL createDistNetwork(String id) {
		return new DistNetDSL(id);
	}
	
	/**
	 * wrap the DistNetwork object
	 * 
	 * @param net
	 * @return
	 */
	public static DistNetDSL wrapAclfNetwork(DistNetwork net) {
		return new DistNetDSL(net);
	}
	
	// ================ DistNetDSL implementation =======================

	/**
	 * DSL for distribution network analysis
	 * 
	 * @author mzhou
	 *
	 */
	public static class DistNetDSL {
		private DistNetwork distNet;
		private boolean acscNetDataCreated = false;
		
		/**
		 * constructor
		 * 
		 * @param id
		 */
		public DistNetDSL(String id) {
			distNet = DistObjectFactory.createDistNetwork();
			distNet.setId(id);
			distNet.setDesc("DistNetDesc");
			distNet.setBaseKva(100000.0);
			distNet.setFrequency(50.0);
			distNet.setAllowParallelBranch(false);
			distNet.setCheckElementDuplication(true);				
		}

		/**
		 * constructor
		 * 
		 * @param net
		 */
		public DistNetDSL(DistNetwork net) {
			this.distNet = net;
		}
		
		public DistNetDSL setBaseKva(double kva) {this.distNet.setBaseKva(kva); return this;}
		public DistNetDSL setFrequencyHz(double f) {this.distNet.setFrequency(f); return this;}
		public DistNetDSL setName(String s) {this.distNet.setName(s); return this;}
		public DistNetDSL setDescription(String s) {this.distNet.setDesc(s); return this;}
		
		public DistNetwork getDistNetwork() { return this.distNet; }
		public AcscNetwork getAcscNetwork() {
			if (!this.acscNetDataCreated) {
				this.createAcscNetData();
				distNet.getFaultNet().setLfDataLoaded(true);
			}	
			//this.distNet.setNameplateAclfNetData();
			return distNet.getAcscNet();
		}
		
		public boolean loadflow() {
			AcscNetwork aclfNet = this.getAcscNetwork();
		  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(aclfNet);
		  	return algo.loadflow();			
		}

		/*
		 * Bus elements
		 * ============
		 */
		public DistUtility addUtility(String id, double baseV, UnitType unit) {
			DistBus bus = addBus(id, baseV, unit);
			bus.setBusCode(DistBusCode.UTILITY);
			return bus.toUtility();}

		public DistGenerator addGenerator(String id, double baseV, UnitType unit) {
			DistBus bus = addBus(id, baseV, unit);
			bus.setBusCode(DistBusCode.GENERATOR);
			return bus.toGenerator();}
		
		public DistSynMotor addSynMotor(String id, double baseV, UnitType unit) {
			DistBus bus = addBus(id, baseV, unit);
			bus.setBusCode(DistBusCode.SYN_MOTOR);
			return bus.toSynMotor();}

		public DistIndMotor addIndMotor(String id, double baseV, UnitType unit) {
			DistBus bus = addBus(id, baseV, unit);
			bus.setBusCode(DistBusCode.IND_MOTOR);
			return bus.toIndMotor();}

		public DistMixedLoad addMixedLoad(String id, double baseV, UnitType unit) {
			DistBus bus = addBus(id, baseV, unit);
			bus.setBusCode(DistBusCode.MIXED_LOAD);
			return bus.toMixedLoad();}

		public DistNonContribBus addNonContributeBus(String id, double baseV, UnitType unit) {
			DistBus bus = addBus(id, baseV, unit);
			bus.setBusCode(DistBusCode.NON_CONTRIBUTE);
			return bus.toNonContribBus();}

		/*
		 * Branch elements
		 * ===============
		 */
		public DistFeeder addFeeder(String fromId, String toId) {
			DistBranch branch = addBranch(fromId, toId);
			branch.setBranchCode(DistBranchCode.FEEDER);		
			return branch.toFeeder();}

		public DistBreaker addBreaker(String fromId, String toId) {
			DistBranch branch = addBranch(fromId, toId);
			branch.setBranchCode(DistBranchCode.BREAKER);		
			return branch.toBreaker();}

		public DistReactor addReactor(String fromId, String toId) {
			DistBranch branch = addBranch(fromId, toId);
			branch.setBranchCode(DistBranchCode.REACTOR);		
			return branch.toReactor();}

		public DistXformer addXformer(String fromId, String toId) {
			DistBranch branch = addBranch(fromId, toId);
			branch.setBranchCode(DistBranchCode.TRANSFROMER);		
			return branch.toXFormer();}

		/*
		 * Private functions
		 * =================
		 */
		private void createAcscNetData() {
			distNet.createAcscNetData();
			distNet.getFaultNet().setLfDataLoaded(true);
			distNet.getFaultNet().setScDataLoaded(true);
			this.acscNetDataCreated = true;}
		
		private DistBus addBus(String id, double baseV, UnitType unit) {
			DistBus bus = DistObjectFactory.createDistBus(id, this.distNet);
			//bus.setId(id);
			bus.setStatus(true);
			Area area = CoreObjectFactory.createArea(1, this.distNet);
			Zone zone = CoreObjectFactory.createZone(1, this.distNet);
			bus.setArea(area);
			bus.setZone(zone);	
			bus.setBaseVoltage(baseV, unit);
			//this.distNet.addBus(bus);
			this.acscNetDataCreated = false;
			return bus;
		}
	
		private DistBranch addBranch(String from_id, String to_id) {
			DistBranch branch = DistObjectFactory.createDistBranch();
			branch.setStatus(true);
			Area area = CoreObjectFactory.createArea(1, this.distNet);
			Zone zone = CoreObjectFactory.createZone(1, this.distNet);
			branch.setArea(area);
			branch.setZone(zone);
			this.distNet.addBranch(branch, from_id, to_id);
			this.acscNetDataCreated = false;
			return branch;
		}
	}
}
