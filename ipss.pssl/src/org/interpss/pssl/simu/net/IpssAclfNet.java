 /*
  * @(#)IpssAclf.java   
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

import static com.interpss.common.util.IpssLogger.ipssLogger;
import static com.interpss.common.util.NetUtilFunc.ToBranchId;
import static com.interpss.core.funcImpl.AclfFunction.loadBusAptr;
import static com.interpss.core.funcImpl.AclfFunction.swingBusAptr;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.ComplexFunc;
import org.interpss.numeric.datatype.LimitType;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.pssl.simu.BaseDSL;

import com.interpss.CoreObjectFactory;
import com.interpss.common.datatype.UnitHelper;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.aclf.adj.AclfAdjustFactory;
import com.interpss.core.aclf.adj.AdjControlType;
import com.interpss.core.aclf.adj.FunctionLoad;
import com.interpss.core.aclf.adj.PQBusLimit;
import com.interpss.core.aclf.adj.PSXfrPControl;
import com.interpss.core.aclf.adj.PVBusLimit;
import com.interpss.core.aclf.adj.RemoteQBus;
import com.interpss.core.aclf.adj.RemoteQControlType;
import com.interpss.core.aclf.adj.TapControl;
import com.interpss.core.aclf.adj.XfrTapControlType;
import com.interpss.core.aclf.adpter.AclfCapacitorBus;
import com.interpss.core.aclf.adpter.AclfLine;
import com.interpss.core.aclf.adpter.AclfLoadBusAdapter;
import com.interpss.core.aclf.adpter.AclfPQGenBus;
import com.interpss.core.aclf.adpter.AclfPSXformer;
import com.interpss.core.aclf.adpter.AclfPVGenBus;
import com.interpss.core.aclf.adpter.AclfSwingBus;
import com.interpss.core.aclf.adpter.AclfXformer;
import com.interpss.core.net.Branch;
import com.interpss.core.net.Bus;
import com.interpss.core.net.Network;

/**
 * DSL (domain specific language) for defining AC Loadflow network parameters
 * 
 *   1) The method should be called in sequence following the sequence defined, for example
 *       Bus.setGenCode() should be called before bus.setVoltageSpec() 
 * 
 * 
	 * Add a load bus to the network
	 * 
		addAclfBus(id, "Bus-"+id)
			.areaNumber(noArea)
			.zoneNumber(noZone)
			.baseVoltage(baseV)
			.loadCode(code)
			.load(new Complex(p,q), pUnit);

	 * Add a non-load and non-gen bus
	 * 
		addAclfBus(id, "Bus-"+id)
			.areaNumber(noArea)
			.zoneNumber(noZone)
			.baseVoltage(baseV, unit);
			
	 * Add a PQ Gen bus to the network
	 * 
		addAclfBus(id, "Bus-"+id)
			.areaNumber(noArea)
			.zoneNumber(noZone)
			.baseVoltage(baseV)
			.genCode(AclfGenCode.GEN_PQ)
			.gen(new Complex(p,q), pUnit);

	 * Add a PV bus to the network object
	 * 
  	  	addAclfBus(id, "Bus-"+id)
  	  		.areaNumber(noArea)
  	  		.zoneNumber(noZone)
  	  		.baseVoltage(baseV)
  	  		.genCode(AclfGenCode.GEN_PV)
  	  		.genP_vMag(p, pUnit, v, vUnit);
  	  		
	 * Add a swing bus into the network 
	 * 
  	  	addAclfBus(id, "Bus-"+id)
			.areaNumber(noArea)
			.zoneNumber(noZone)
			.baseVoltage(baseV)
			.genCode(AclfGenCode.SWING)
			.voltageSpec(vMag, vUnit, vAng, angUnit);
			
	 * Add a capacitor bus to the network
	 * 
  	  	addAclfBus(id, "Bus-"+id)
  	  		.areaNumber(noArea)
  	  		.zoneNumber(noZone)
  	  		.baseVoltage(baseV)
  	  		.genCode(AclfGenCode.CAPACITOR)
  	  		.capacitorQ(q, qUnit);

	 * Add a line branch to the network
	 * 
		addAclfBranch(fid, tid)
			.branchCode(AclfBranchCode.LINE)
			.z(new Complex(r,x), zUnit)
			.shuntB(hB*2.0, bUnit);
			
	 * Add a Xfr branch to the network
	 * 
		addAclfBranch(fid, tid)
			.branchCode(AclfBranchCode.XFORMER)
			.z(new Complex(r,x), zUnit)
			.turnRatio(fTap, tTap, ratioUnit);
			
  	 * Add a PsXfr branch to the network
  	 * 
		addAclfBranch(fid, tid)
			.branchCode(AclfBranchCode.PS_XFORMER)
			.z(new Complex(r,x), zUnit)
			.turnRatio(fRatio, tRatio, ratioUnit)
			.shiftAngle(fAng, tAng, angUnit);
			
	 * Add a FunctionLoad control to the network
		addFunctionLoad(busId)
			.initLoad(new Complex(loadP0,loadQ0), unit)
			.pCoefficient(p_a, p_b, unit)
			.qCoefficient(q_a, q_b, unit);
			
	 * add PQ bus control
	 * 
		addPQBusLimit(busId)
			.qSpecified(qSpec, qUnit)
			.vLimit(vMax, vMin, vUnit);

	 * Add PV Bus control
	 * 
		addPVBusLimit(busId)
			.vSpecified(vSpec, vUnit)
			.qLimit(qMax, qMin, qUnit);
			
	 * add PS Xfr control of p flow

		addPSXfrPControl(fromId, toId, "1")
			.pSpecified(pSpec, pUnit)
			.angLimit(angMax, angMin, angUnit)
			.flowFrom2To(flowFrom2To)
			.controlOnFromSide(conOnFromSide);
	
	 * add bus Q control of a remote bus voltage
	 * 
  		addRemoteQBus(busId, adjNet)
  			.controlType(RemoteQControlType.BUS_VOLTAGE)
  			.adjBusBranchId(remoteBusId)
  			.qLimit(qMax, qMin, qUnit)
  			.vSpecified(vSpec, vUnit);
  			
	 *  Add Bus Q control of a remote branch mvar flow
	 * 
  		addRemoteQBus(busId)
			.controlType(RemoteQControlType.BRANCH_Q)
			.adjBusBranchId(remoteBranchId)
			.qLimit(qMax, qMin, qUnit)
			.vSpecified(vSpec, vUnit)
			.mvarOnFromSide(onFromSide)
			.flowFrom2To(flowFrom2To);
			
	 * Add Xfr branch tap control for bus voltage
	 * 
  		addTapControl(fromId, toId, "1")
  			.controlType(XfrTapControlType.BUS_VOLTAGE)
  			.adjBusBranchId(vcBusId)
  			.flowControlType(FlowControlType.POINT_CONTROL)
  			.vSpecified(vSpec, vUnit)
  			.turnRatioLimit(tapMax, tapMin)
  			.adjSteps(steps)
  			.vcBusOnFromSide(vcBusOnFromSide)
  			.tapOnFromSide(tapOnFromSide);
  			
	 * Add Xfr Tap Control for branch mvar flow
	 * 
  		addTapControl(fromId, toId, "1")
			.controlType(XfrTapControlType.MVAR_FLOW)
			.flowControlType(FlowControlType.POINT_CONTROL)
			.mvarSpecified(mvaSpec, mvaUnit)
			.turnRatioLimit(tapMax, tapMin)
			.adjSteps(steps)
			.tapOnFromSide(tapOnFromSide)
			.flowFrom2To(flowFrom2To)
			.meteredOnFromSide(mvarSpecOnFromSide);
 */
public class IpssAclfNet extends BaseDSL {
	/*
	 *   AclfNetwork creation
	 */

	// ================ public methods =======================
	
	/**
	 * create an AclfNetwork DSL object
	 * 
	 * @param id
	 * @return
	 */
	public static AclfNetworkDSL createAclfNetwork(String id) {
		return new AclfNetworkDSL(id);
	}
	
	/**
	 * wrap an AclfNetwork object into a DSL object
	 * 
	 * @param net
	 * @return
	 */
	public static AclfNetworkDSL wrapAclfNetwork(AclfNetwork net) {
		return new AclfNetworkDSL(net);
	}

	// ================ private implementation =======================

	/**
	 * AclfNetwork DSL for defining AclfNetwork parameters
	 * 
	 * @author mzhou
	 *
	 */
	public static class AclfNetworkDSL extends AclfBaseNetDSL<AclfNetwork> {
		/**
		 * constructor
		 * 
		 * @param id
		 */
		public AclfNetworkDSL(String id) {
			super(id, CoreObjectFactory.createAclfNetwork());
		}

		/**
		 * constructor
		 * 
		 * @param net
		 */
		public AclfNetworkDSL(AclfNetwork net) {
			super(net.getId(), net);
		}
	}
	
	/**
	 * Base AclfNetwork DSL for defining AclfNetwork parameters
	 * 
	 * @author mzhou
	 *
	 * @param <T>
	 */
	public static class AclfBaseNetDSL<T extends BaseAclfNetwork<?,?,?,?>> {
		T net = null;
		
		/**
		 * constrcutor
		 * 
		 * @param id
		 * @param net
		 */
		public AclfBaseNetDSL(String id, T net) {
			this.net = net;
			this.net.setLfConverged(false);
			this.net.setId(id);
		}

		public AclfBaseNetDSL<T> setName(String name) { this.net.setName(name); return this; }  
		public AclfBaseNetDSL<T> name(String name) { this.net.setName(name); return this; }  
		public AclfBaseNetDSL<T> setDesc(String desc) { this.net.setDesc(desc); return this; }  
		public AclfBaseNetDSL<T> description(String desc) { this.net.setDesc(desc); return this; }  
		public AclfBaseNetDSL<T> setBaseKva(double kva) { this.net.setBaseKva(kva); return this; }  
		public AclfBaseNetDSL<T> baseKva(double kva) { this.net.setBaseKva(kva); return this; }  
		public AclfBaseNetDSL<T> baseMva(double kva) { this.net.setBaseKva(kva*0.0001); return this; }  
		//public AclfBaseNetDSL<T> setAllowParallelBranch(boolean b) {this.net.setAllowParallelBranch(b); return this; }
		//public AclfBaseNetDSL<T> allowParallelBranch(boolean b) {this.net.setAllowParallelBranch(b); return this; }
		
		
		public T getAclfNet() {return this.net; }

		public AclfBusDSL addAclfBus(String busId, String busName) throws InterpssException{
			return new AclfBusDSL(busId, busName, getAclfNet()); }
		public AclfBusDSL getAclfBus(String busId) throws Exception {
			return new AclfBusDSL(busId, getAclfNet());	}
		
		public AclfBranchDSL addAclfBranch(String fromBusId, String toBusId) throws InterpssException {
			return new AclfBranchDSL(fromBusId, toBusId, getAclfNet());	}
		public AclfBranchDSL addAclfBranch(String fromBusId, String toBusId, String cirId) throws InterpssException {
			return new AclfBranchDSL(fromBusId, toBusId, cirId, getAclfNet());	}
		public AclfBranchDSL getAclfBranch(String fromBusId, String toBusId) throws Exception {
			return new AclfBranchDSL((AclfBranch)getAclfNet().getBranch(fromBusId, toBusId), getAclfNet());	}
		public AclfBranchDSL getAclfBranch(String fromBusId, String toBusId, String cirId) throws Exception {
			return new AclfBranchDSL((AclfBranch)getAclfNet().getBranch(fromBusId, toBusId, cirId), getAclfNet());	}
	}

	/*
	 * 	Add Aclf Bus
	 */
	
	// ================ public methods =======================
	
	public static AclfBusDSL addAclfBus(String busId, String busName, AclfNetwork net) throws InterpssException {
		return new AclfBusDSL(busId, busName, net);
	}

	public static AclfBusDSL getAclfBus(String busId, AclfNetwork net) throws Exception {
		return new AclfBusDSL(busId, net);
	}

	public static AclfBusDSL wrapAclfBus(AclfBus obj, AclfNetwork net) {
		try { return new AclfBusDSL(obj, net); } 
		catch (Exception e) { ipssLogger.severe(e.toString()); return null; }
	}

	// ================ private implementation =======================

	public static class AclfBusDSL extends AclfBusBaseDSL<BaseAclfBus<?,?>, BaseAclfNetwork<?,?,?,?>, AclfBusDSL>{
		// for addAclfBus()
		public AclfBusDSL(String busId, String busName, BaseAclfNetwork<?,?,?,?> net) throws InterpssException {
			super(busId, busName, CoreObjectFactory.createAclfBus(busId, net), net);
		}
		// for getAclfBus()
		public AclfBusDSL(String busId, BaseAclfNetwork<?,?,?,?> net)  throws Exception {
			super(net.getBus(busId), net);
		}
		public AclfBusDSL(AclfBus bus, BaseAclfNetwork<?,?,?,?> net)  throws Exception {
			super(bus, net);
		}
	}
	
	public static class AclfBusBaseDSL<TBus extends Bus, TNet extends Network<?,?>, TAclfDSL> 
							extends BaseNetDSL<TBus, TNet, AclfBusBaseDSL<TBus,TNet, TAclfDSL>>{
		// for getAclfBus()
		public AclfBusBaseDSL(TBus bus, TNet net)  throws Exception {
			super(net);
			if (bus != null)
				setObject(bus);
			else
				throw new Exception("AclfBus not found");
		}

		// for addAclfBus()
		public AclfBusBaseDSL(String busId, String busName, TBus bus, TNet net) {
			super(net);
			//AclfBus bus = CoreObjectFactory.createAclfBus(busId);
			bus.setName(busName);
			setObject(bus);
			//getAclfNet().addBus(bus);
			AclfBus aclfBus = ((AclfBus)bus);
			aclfBus.setGenCode(AclfGenCode.NON_GEN);
			aclfBus.setLoadCode(AclfLoadCode.NON_LOAD);
		}
		
		@SuppressWarnings(value="unchecked")
  		public TAclfDSL setBaseVoltage(double v) { getAclfBus().setBaseVoltage(v); return (TAclfDSL)this; }
		@SuppressWarnings(value="unchecked")
  		public TAclfDSL baseVoltage(double v) { getAclfBus().setBaseVoltage(v); return (TAclfDSL)this; }
		@SuppressWarnings(value="unchecked")
  		public TAclfDSL setBaseVoltage(double v, UnitType unit) { getAclfBus().setBaseVoltage(v, unit); return (TAclfDSL)this; }
		@SuppressWarnings(value="unchecked")
  		public TAclfDSL baseVoltage(double v, UnitType unit) { getAclfBus().setBaseVoltage(v, unit); return (TAclfDSL)this; }

		@SuppressWarnings(value="unchecked")
  		public TAclfDSL setSectionNumber(int n) { getAclfBus().setSecNo(n); return (TAclfDSL)this; }
		@SuppressWarnings(value="unchecked")
  		public TAclfDSL sectionNumber(int n) { getAclfBus().setSecNo(n); return (TAclfDSL)this; }
  		
		@SuppressWarnings(value="unchecked")
  		public TAclfDSL setGenCode(AclfGenCode code ) { getAclfBus().setGenCode(code); return (TAclfDSL)this; }
		@SuppressWarnings(value="unchecked")
  		public TAclfDSL genCode(AclfGenCode code ) { getAclfBus().setGenCode(code); return (TAclfDSL)this; }
  		public TAclfDSL voltageSpec(double vmsg, UnitType magUnit, double vang, UnitType degUnit) { return setVoltageSpec(vmsg, magUnit, vang, degUnit); } 
		@SuppressWarnings(value="unchecked")
  		public TAclfDSL setVoltageSpec(double vmsg, UnitType magUnit, double vang, UnitType degUnit) {
  	  						if (getAclfBus().getGenCode() == AclfGenCode.SWING) {
  	  							AclfSwingBus swingBus = swingBusAptr.apply(getAclfBus());
  	  							swingBus.setDesiredVoltMag(vmsg, magUnit);	swingBus.setDesiredVoltAng(vang, degUnit);	
  	  						} return (TAclfDSL)this;	}
  		public TAclfDSL genP_vMag(double p, UnitType punit, double v, UnitType vunit) { return setGenP_VMag(p, punit, v, vunit); }
		@SuppressWarnings(value="unchecked")
  		public TAclfDSL setGenP_VMag(double p, UnitType punit, double v, UnitType vunit) {
  	  						if (getAclfBus().getGenCode() == AclfGenCode.GEN_PV) {
  	  							AclfPVGenBus pv = getAclfBus().toPVBus();
  	  							pv.setGenP(p, punit);
  	  							pv.setDesiredVoltMag( v, vunit );
  	  						} return (TAclfDSL)this;		}
  		public TAclfDSL gen(Complex gen, UnitType unit) { return setGen(gen, unit); }
		@SuppressWarnings(value="unchecked")
  		public TAclfDSL setGen(Complex gen, UnitType unit) { 
  							AclfPQGenBus pq = getAclfBus().toPQBus();
  							pq.setGen(gen, unit );
  							return (TAclfDSL)this;  		}
  		public TAclfDSL capacitorQ(double q, UnitType unit) { return setCapacitorQ(q, unit); } 
		@SuppressWarnings(value="unchecked")
  		public TAclfDSL setCapacitorQ(double q, UnitType unit) { 
					  	  	AclfCapacitorBus capBus = getAclfBus().toCapacitorBus();
					  	  	capBus.setQ(q, unit);
							return (TAclfDSL)this;  		}
  		
		@SuppressWarnings(value="unchecked")
  		public TAclfDSL setLoadCode(AclfLoadCode code) { getAclfBus().setLoadCode(code); return (TAclfDSL)this; }
		@SuppressWarnings(value="unchecked")
  		public TAclfDSL loadCode(AclfLoadCode code) { getAclfBus().setLoadCode(code); return (TAclfDSL)this; }
  		public TAclfDSL load(Complex load, UnitType unit) { return setLoad(load, unit); }
		@SuppressWarnings(value="unchecked")
  		public TAclfDSL setLoad(Complex load, UnitType unit) { 
  	  						AclfLoadBusAdapter loadBus = loadBusAptr.apply(getAclfBus());
  	  						loadBus.setLoad(load, unit); 
  	  						return (TAclfDSL)this;  		}
  		public TAclfDSL shuntY(Complex y, UnitType unit) { return setShuntY(y, unit); } 
		@SuppressWarnings(value="unchecked")
  		public TAclfDSL setShuntY(Complex y, UnitType unit) { 
  							getAclfBus().setShuntY(UnitHelper.yConversion(y, getAclfBus().getBaseVoltage(), getAclfNet().getBaseKva(), unit, UnitType.PU)); 
  							return (TAclfDSL)this; }
  		public TAclfDSL initVoltage(double vpu, double angRad) { return setInitVoltage(vpu, angRad); }
		@SuppressWarnings(value="unchecked")
  		public TAclfDSL setInitVoltage(double vpu, double angRad) {
  							getAclfBus().setVoltage(vpu, angRad);
							return (TAclfDSL)this; }
  		public AclfBus getAclfBus() {return (AclfBus)getObject(); }
  		public AclfNetwork getAclfNet() { return (AclfNetwork)getNet(); }
	}

	/*
	 *   Add Aclf Branch
	 */
	
	// ================ public methods =======================

	public static AclfBranchDSL addAclfBranch(String fromBusId, String toBusId, AclfNetwork net) throws InterpssException {
		return new AclfBranchDSL(fromBusId, toBusId, net);
	}
	
	public static AclfBranchDSL addAclfBranch(String fromBusId, String toBusId, String cirId, AclfNetwork net) throws InterpssException {
		return new AclfBranchDSL(fromBusId, toBusId, cirId, net);
	}

	public static AclfBranchDSL getAclfBranch(String fromBusId, String toBusId, AclfNetwork net) throws Exception {
		return new AclfBranchDSL((AclfBranch)net.getBranch(fromBusId, toBusId), net);
	}
	
	public static AclfBranchDSL getAclfBranch(String fromBusId, String toBusId, String cirId, AclfNetwork net) throws Exception {
		return new AclfBranchDSL((AclfBranch)net.getBranch(fromBusId, toBusId, cirId), net);
	}

	public static AclfBranchDSL wrapAclfBranch(AclfBranch obj, AclfNetwork net) {
		try { return new AclfBranchDSL(obj, net);
		} catch (Exception e) {	ipssLogger.severe(e.toString()); return null; }
	}

	// ================ private implementation =======================

	public static class AclfBranchDSL extends AclfBranchBaseDSL<AclfBranch, BaseAclfNetwork<?,?,?,?>, AclfBranchDSL>{
		// for addAclfBranch()
		public AclfBranchDSL(AclfBranch branch, BaseAclfNetwork<?,?,?,?> net) throws Exception {
			super(branch, net);
		}
		// for addAclfBranch()
		public AclfBranchDSL(String fromBusId, String toBusId, BaseAclfNetwork<?,?,?,?> net) throws InterpssException {
			this(fromBusId, toBusId, "1", net);
		}
		public AclfBranchDSL(String fromBusId, String toBusId, AclfBranch branch, BaseAclfNetwork<?,?,?,?> net) throws InterpssException {
			this(fromBusId, toBusId, "1", branch, net);
		}
		// for addAclfBranch()
		public AclfBranchDSL(String fromBusId, String toBusId, String cirId, BaseAclfNetwork<?,?,?,?> net) throws InterpssException {
			super(fromBusId, toBusId, cirId, CoreObjectFactory.createAclfBranch(), net);
		}

		public AclfBranchDSL(String fromBusId, String toBusId, String cirId, AclfBranch branch, BaseAclfNetwork<?,?,?,?> net) throws InterpssException {
			super(fromBusId, toBusId, cirId, branch, net);
		}
	}
	
	public static class AclfBranchBaseDSL<TBra extends Branch, TNet extends Network<?,?>, TAclfDSL> extends BaseNetDSL<TBra, TNet, TAclfDSL>{
		// for getAclfBranch()
		public AclfBranchBaseDSL() {
		}
		// for getAclfBranch()
		public AclfBranchBaseDSL(TBra branch, TNet net) throws Exception {
			super(net);
			if (branch != null)
				setObject(branch);
			else
				throw new Exception("AclfBranch not found");
		}

		// for addAclfBranch()
		public AclfBranchBaseDSL(String fromBusId, String toBusId, String cirId, TBra branch, TNet net) throws InterpssException {
			super(net);
			//AclfBranch branch = CoreObjectFactory.createAclfBranch();
			branch.setCircuitNumber(cirId);
			setObject(branch);
			getAclfNet().addBranch((AclfBranch)branch, fromBusId, toBusId);
		}
		
		@SuppressWarnings(value="unchecked")
  		public TAclfDSL setBranchCode(AclfBranchCode code) { getAclfBranch().setBranchCode(code); return (TAclfDSL)this; }
		@SuppressWarnings(value="unchecked")
  		public TAclfDSL branchCode(AclfBranchCode code) { getAclfBranch().setBranchCode(code); return (TAclfDSL)this; }

  		public TAclfDSL z(Complex z, UnitType unit) { return setZ(z, unit); } 
		@SuppressWarnings(value="unchecked")
  		public TAclfDSL setZ(Complex z, UnitType unit) { 
								if (getAclfBranch().getBranchCode() == AclfBranchCode.LINE) {
									AclfLine lineBranch = getAclfBranch().toLine();
									lineBranch.setZ(z, unit, getAclfBranch().getFromAclfBus().getBaseVoltage());
								} 
								else if (getAclfBranch().getBranchCode() == AclfBranchCode.XFORMER || 
										 getAclfBranch().getBranchCode() == AclfBranchCode.PS_XFORMER) {
									AclfXformer xfr = getAclfBranch().toXfr();;
									double baseV = getAclfBranch().getFromAclfBus().getBaseVoltage() > getAclfBranch().getToAclfBus().getBaseVoltage() ?
											getAclfBranch().getFromAclfBus().getBaseVoltage() : getAclfBranch().getToAclfBus().getBaseVoltage();
									xfr.setZ(z, unit, baseV);
								} return (TAclfDSL)this; }		
		public TAclfDSL shuntB(double b, UnitType unit) { return setShuntB(b, unit); } 
 		public TAclfDSL setShuntB(double b, UnitType unit) { 
								return setShuntY(new Complex(0.0, b), unit); }		
		public TAclfDSL shuntY(Complex y, UnitType unit) { return setShuntY(y, unit); }
		@SuppressWarnings(value="unchecked")
 		public TAclfDSL setShuntY(Complex y, UnitType unit) { 
								if (getAclfBranch().isLine()) {
									AclfLine lineBranch = getAclfBranch().toLine();
									lineBranch.setHShuntY(ComplexFunc.mul(0.5,y), unit, getAclfBranch().getFromAclfBus().getBaseVoltage());
								} return (TAclfDSL)this; }		
		public TAclfDSL turnRatio(double fromTurnRatio, double toTurnRatio, UnitType unit) { return setTap(fromTurnRatio, toTurnRatio, unit); }
		@SuppressWarnings(value="unchecked")
		public TAclfDSL setTap(double fromTap, double toTap, UnitType unit) { 
								if (getAclfBranch().getBranchCode() == AclfBranchCode.XFORMER || 
										getAclfBranch().getBranchCode() == AclfBranchCode.PS_XFORMER) {
									AclfXformer xfr = getAclfBranch().toXfr();;
									xfr.setFromTurnRatio(fromTap, unit);
									xfr.setToTurnRatio(toTap, unit);
								} return (TAclfDSL)this; }		
		public TAclfDSL shiftAngle(double fromAng, double toAng, UnitType unit) { return setShiftAngle(fromAng, toAng, unit); } 
		@SuppressWarnings(value="unchecked")
 		public TAclfDSL setShiftAngle(double fromAng, double toAng, UnitType unit) { 
								if (getAclfBranch().getBranchCode() == AclfBranchCode.PS_XFORMER) {
									AclfPSXformer xfr = getAclfBranch().toPSXfr();
									xfr.setFromAngle(fromAng, unit);
									xfr.setToAngle(toAng, unit);
								} return (TAclfDSL)this; }	
		public TAclfDSL fromShiftAngle(double ang, UnitType unit) { return setFromShiftAngle(ang, unit); }
		@SuppressWarnings(value="unchecked")
 		public TAclfDSL setFromShiftAngle(double ang, UnitType unit) { 
								if (getAclfBranch().getBranchCode() == AclfBranchCode.PS_XFORMER) {
									AclfPSXformer xfr = getAclfBranch().toPSXfr();
									xfr.setFromAngle(ang, unit);
								} return (TAclfDSL)this; }	
		public TAclfDSL toShiftAngle(double ang, UnitType unit) { return setToShiftAngle(ang, unit); } 
		@SuppressWarnings(value="unchecked")
		public TAclfDSL setToShiftAngle(double ang, UnitType unit) { 
								if (getAclfBranch().getBranchCode() == AclfBranchCode.PS_XFORMER) {
									AclfPSXformer xfr = getAclfBranch().toPSXfr();
									xfr.setToAngle(ang, unit);
								} return (TAclfDSL)this; }	

		@SuppressWarnings(value="unchecked")
		public TAclfDSL setRatingMva1(double x) { getAclfBranch().setRatingMva1(x); return (TAclfDSL)this; }
		@SuppressWarnings(value="unchecked")
		public TAclfDSL setRatingMva2(double x) { getAclfBranch().setRatingMva2(x); return (TAclfDSL)this; }
		@SuppressWarnings(value="unchecked")
		public TAclfDSL setRatingMva3(double x) { getAclfBranch().setRatingMva3(x); return (TAclfDSL)this; }
		@SuppressWarnings(value="unchecked")
		public TAclfDSL ratingMva1(double x) { getAclfBranch().setRatingMva1(x); return (TAclfDSL)this; }
		@SuppressWarnings(value="unchecked")
		public TAclfDSL ratingMva2(double x) { getAclfBranch().setRatingMva2(x); return (TAclfDSL)this; }
		@SuppressWarnings(value="unchecked")
		public TAclfDSL ratingMva3(double x) { getAclfBranch().setRatingMva3(x); return (TAclfDSL)this; }
		
		@SuppressWarnings(value="unchecked")
		public TAclfDSL fromShuntY(Complex y) { getAclfBranch().setFromShuntY(y); return (TAclfDSL)this; }
		public TAclfDSL fromShuntY(Complex y, UnitType unit) { return setFromShuntY(y, unit); }  
		@SuppressWarnings(value="unchecked")
		public TAclfDSL setFromShuntY(Complex y) { getAclfBranch().setFromShuntY(y); return (TAclfDSL)this; }
		public TAclfDSL setFromShuntY(Complex y, UnitType unit) { 
						Complex ypu = UnitHelper.yConversion(new Complex(y.getReal(),	y.getImaginary()),
												getAclfBranch().getFromAclfBus().getBaseVoltage(), getAclfNet().getBaseKva(), 
												unit, UnitType.PU);
						return setFromShuntY(ypu); }
		
		@SuppressWarnings(value="unchecked")
		public TAclfDSL toShuntY(Complex y) { getAclfBranch().setToShuntY(y); return (TAclfDSL)this; }
		public TAclfDSL toShuntY(Complex y, UnitType unit) { return setToShuntY(y, unit); }  
		@SuppressWarnings(value="unchecked")
		public TAclfDSL setToShuntY(Complex y) { getAclfBranch().setToShuntY(y); return (TAclfDSL)this; }
		public TAclfDSL setToShuntY(Complex y, UnitType unit) { 
						Complex ypu = UnitHelper.yConversion(new Complex(y.getReal(),	y.getImaginary()),
												getAclfBranch().getToAclfBus().getBaseVoltage(), getAclfNet().getBaseKva(), 
												unit, UnitType.PU);
						return setToShuntY(ypu); }
		
  		public AclfBranch getAclfBranch() {return (AclfBranch)getObject(); }
  		public AclfNetwork getAclfNet() { return (AclfNetwork)getNet(); }
	}

	/*
	 * Aclf adjustments
	 * ================
	 */

	/*
	 * FunctionLoad
	 */

	// ================ public methods =======================

	public static FunctionLoadDSL addFunctionLoad(String busId, AclfNetwork net) throws InterpssException {
		return new FunctionLoadDSL(busId, net);
	}

	// ================ private implementation =======================

	public static class FunctionLoadDSL extends BaseAdjustmentDSL<FunctionLoad>{
		public FunctionLoadDSL(String busId, AclfNetwork net) throws InterpssException  {
			super(net);
			AclfBus bus = net.getBus(busId);
			setObject(CoreObjectFactory.createFunctionLoad(bus));
		}
  		public FunctionLoadDSL setInitLoad(Complex load0, UnitType unit) { 
  								getObject().getP().setLoad0(load0.getReal(), unit, getAclfAdjNet().getBaseKva());
  								getObject().getQ().setLoad0(load0.getImaginary(), unit, getAclfAdjNet().getBaseKva());
  								return this; }
  		public FunctionLoadDSL setPCoefficient(double a, double b, UnitType unit) { 
  								getObject().getP().setA(a, unit);
  								getObject().getP().setB(b, unit);
  								return this; }
  		public FunctionLoadDSL setQCoefficient(double a, double b, UnitType unit) { 
  								getObject().getQ().setA(a, unit);
  								getObject().getQ().setB(b, unit);
								return this; }
  		
  		public FunctionLoadDSL initLoad(Complex load0, UnitType unit) { return setInitLoad(load0, unit); }
  		public FunctionLoadDSL pCoefficient(double a, double b, UnitType unit) { return setPCoefficient(a, b, unit); } 
  		public FunctionLoadDSL qCoefficient(double a, double b, UnitType unit) { return setQCoefficient(a, b, unit); } 
	}
	
	/*
	 * PQLimit
	 */
	
	// ================ public methods =======================

	public static PQBusLimitDSL addPQBusLimit(String busId, AclfNetwork net) throws InterpssException {
		return new PQBusLimitDSL(busId, net);
	}

	// ================ private implementation =======================

	public static class PQBusLimitDSL extends BaseAdjustmentDSL<PQBusLimit>{
		public PQBusLimitDSL(String busId, AclfNetwork net) throws InterpssException {
			super(net);
			AclfBus bus = net.getBus(busId);
			setObject(CoreObjectFactory.createPQBusLimit(bus));
		}
  		public PQBusLimitDSL setQSpecified(double qSpec, UnitType unit) { 
  								getObject().setQSpecified(qSpec, unit);
								return this; }
  		public PQBusLimitDSL setVLimit(double vMax, double vMin, UnitType unit) { 
								getObject().setVLimit(new LimitType(vMax, vMin), unit);	  
								return this; }
  		
  		public PQBusLimitDSL qSpecified(double qSpec, UnitType unit) { return setQSpecified(qSpec, unit); }
  		public PQBusLimitDSL vLimit(double vMax, double vMin, UnitType unit) { return setVLimit(vMax, vMin, unit); }  
	}

	/*
	 * PVLimit
	 */

	// ================ public methods =======================

	public static PVBusLimitDSL addPVBusLimit(String busId, AclfNetwork net) {
		return new PVBusLimitDSL(busId, net);
	}

	// ================ private implementation =======================

	public static class PVBusLimitDSL extends BaseAdjustmentDSL<PVBusLimit>{
		public PVBusLimitDSL(String busId, AclfNetwork net) {
			super(net);
			AclfBus bus = net.getBus(busId);
			setObject(CoreObjectFactory.createPVBusLimit(bus));
		}
  		public PVBusLimitDSL setVSpecified(double vSpec, UnitType unit) { 
								getObject().setVSpecified(vSpec, unit);
								return this; }
  		public PVBusLimitDSL setQLimit(double qMax, double qMin, UnitType unit) { 
  								getObject().setQLimit(new LimitType(qMax, qMin), unit);	  
  								return this; }

  		public PVBusLimitDSL vSpecified(double vSpec, UnitType unit) { return setVSpecified(vSpec, unit); }
  		public PVBusLimitDSL qLimit(double qMax, double qMin, UnitType unit) { return setQLimit(qMax, qMin, unit); } 
	}

	/*
	 * PSXfrPControl
	 */

	// ================ public methods =======================

	public static PSXfrPControlDSL addPSXfrPControl(String fromBusId, String toBusId, String cirId, AclfNetwork net) throws InterpssException {
		return new PSXfrPControlDSL(fromBusId, toBusId, cirId, net);
	}

	// ================ private implementation =======================

	public static class PSXfrPControlDSL extends BaseAdjustmentDSL<PSXfrPControl>{
		public PSXfrPControlDSL(String fromBusId, String toBusId, String cirId, AclfNetwork net) throws InterpssException {
			super(net);
			String branchId = ToBranchId.f(fromBusId, toBusId, cirId);
			AclfBranch branch = net.getBranch(branchId);
			setObject(CoreObjectFactory.createPSXfrPControl(branch, AdjControlType.POINT_CONTROL));
		}
  		public PSXfrPControlDSL setFlowControlType(AdjControlType type) { 
								getObject().setFlowControlType(type);
								return this; }
  		public PSXfrPControlDSL setPSpecified(double pSpec, UnitType unit) { 
  								getObject().setPSpecified(pSpec, unit, getAclfAdjNet().getBaseKva());
								return this; }
  		public PSXfrPControlDSL setAngLimit(double angMax, double angMin, UnitType unit) { 
  								getObject().setAngLimit(new LimitType(angMax, angMin), unit);
  								return this; }
  		public PSXfrPControlDSL setControlOnFromSide(boolean conOnFromSide) { 
  								getObject().setControlOnFromSide(conOnFromSide);		
  								return this; }
  		public PSXfrPControlDSL setFlowFrom2To(boolean flowFrom2To) { 
  								getObject().setFlowFrom2To(flowFrom2To);	
  								return this; }

  		public PSXfrPControlDSL flowControlType(AdjControlType type) { return setFlowControlType(type); }
  		public PSXfrPControlDSL pSpecified(double pSpec, UnitType unit) { return setPSpecified(pSpec, unit); }
  		public PSXfrPControlDSL angLimit(double angMax, double angMin, UnitType unit) { return setAngLimit(angMax, angMin, unit); }
  		public PSXfrPControlDSL controlOnFromSide(boolean conOnFromSide) { return controlOnFromSide(conOnFromSide); }
  		public PSXfrPControlDSL flowFrom2To(boolean flowFrom2To) { return setFlowFrom2To(flowFrom2To); }
	}

	/*
	 * RemoteQBus - use Q at Bus(busId) to control remote bus voltage or branch mva flow
	 */
	
	// ================ public methods =======================

	public static RemoteQBusDSL addRemoteQBus(String busId, AclfNetwork net) {
		return new RemoteQBusDSL(busId, net);
	}

	// ================ private implementation =======================

	public static class RemoteQBusDSL extends BaseAdjustmentDSL<RemoteQBus>{
		public RemoteQBusDSL(String busId, AclfNetwork net) {
			super(net);
			setObject(AclfAdjustFactory.eINSTANCE.createRemoteQBus());
			getObject().setId(busId);
		}
  		public RemoteQBusDSL setControlType(RemoteQControlType type) { 
  								getObject().setControlType(type);
  								return this; }
  		public RemoteQBusDSL setAdjBusBranchId(String reId) { 
// TODO							this.getAclfAdjNet().addRemoteQBus(getObject(), reId);
  								return this; }
  		public RemoteQBusDSL setQLimit(double qMax, double qMin, UnitType unit) { 
  								getObject().setQLimit(new LimitType(qMax, qMin), unit);
  								return this; }
  		public RemoteQBusDSL setVSpecified(double vSpec, UnitType unit) { 
  								getObject().setVSpecified(vSpec, unit);
  								return this; }
  		public RemoteQBusDSL setMvarOnFromSide(boolean onFromSide) { 
  								getObject().setMvarOnFromSide(onFromSide);
  								return this; }
  		public RemoteQBusDSL setFlowFrom2To(boolean flowFrom2To) { 
								getObject().setFlowFrom2To(flowFrom2To);
								return this; }

  		public RemoteQBusDSL controlType(RemoteQControlType type) { return setControlType(type); } 
  		public RemoteQBusDSL adjBusBranchId(String reId) { return setAdjBusBranchId(reId); } 
  		public RemoteQBusDSL qLimit(double qMax, double qMin, UnitType unit) { return setQLimit(qMax, qMin, unit); } 
  		public RemoteQBusDSL vSpecified(double vSpec, UnitType unit) { return setVSpecified(vSpec, unit); } 
  		public RemoteQBusDSL mvarOnFromSide(boolean onFromSide) { return setMvarOnFromSide(onFromSide); }
  		public RemoteQBusDSL flowFrom2To(boolean flowFrom2To) { return setFlowFrom2To(flowFrom2To); } 
	}

	/*
	 * TapControl : control a bus voltage to branch mva flow
	 */
	
	// ================ public methods =======================

	public static TapControlDSL addTapControl(String fromBusId, String toBusId, String cirId, AclfNetwork net) {
		return new TapControlDSL(fromBusId, toBusId, cirId, net);
	}

	// ================ private implementation =======================

	public static class TapControlDSL extends BaseAdjustmentDSL<TapControl>{
		public TapControlDSL(String fromBusId, String toBusId, String cirId, AclfNetwork net) {
			super(net);
			String branchId = ToBranchId.f(fromBusId, toBusId, cirId);
			setObject(AclfAdjustFactory.eINSTANCE.createTapControl());
			getObject().setId(branchId);
		}
  		public TapControlDSL setControlType(XfrTapControlType type) { 
								getObject().setControlType(type);
								return this; }
//  		public TapControlDSL setAdjBusBranchId(String id) { 
//  								this.getAclfAdjNet().addTapControl(getObject(), id);
//  								return this; }
  		public TapControlDSL setVSpecified(double vSpec, UnitType unit) { 
  								getObject().setVSpecified(vSpec, unit);
  								return this; }
  		public TapControlDSL setMvarSpecified(double mvaSpec, UnitType unit) { 
								getObject().setMvarSpecified(mvaSpec, unit, getAclfAdjNet().getBaseKva());
								return this; }
  		public TapControlDSL setFlowControlType(AdjControlType type) { 
  								getObject().setFlowControlType(type);
  								return this; }
  		public TapControlDSL setTapLimit(double rMax, double rMin) { 
  	  							getObject().setTurnRatioLimit(new LimitType(rMax, rMin));
  								return this; }
  		public TapControlDSL setAdjSteps(int n) { 
  	  							if (n > 0) {
  	  								double tapMax = getObject().getTurnRatioLimit().getMax();
  	  								double tapMin = getObject().getTurnRatioLimit().getMin();
  	  								getObject().setTapStepSize((tapMax-tapMin)/n);
  	  							}
  	  							else
  	  								getObject().setTapStepSize(0.0);
  								return this; }
  		public TapControlDSL setTapStepSize(double x) {	getObject().setTapStepSize(x); return this; }
  		public TapControlDSL setControlOnFromSide(boolean tapOnFromSide) { 
								getObject().setControlOnFromSide(tapOnFromSide);
								return this; }
  		public TapControlDSL setVcBusOnFromSide(boolean vcBusOnFromSide) { 
								getObject().setVcBusOnFromSide(vcBusOnFromSide);
								return this; }
  		public TapControlDSL setFlowFrom2To(boolean flowFrom2To) { 
  	  							getObject().setFlowFrom2To(flowFrom2To);
  								return this; }
  		public TapControlDSL setMeteredOnFromSide(boolean mvarSpecOnFromSide) { 
  								getObject().setMeteredOnFromSide(mvarSpecOnFromSide);
  								return this; }

  		public TapControlDSL controlType(XfrTapControlType type) { return setControlType(type); } 
//  		public TapControlDSL adjBusBranchId(String id) { return setAdjBusBranchId(id); }
  		public TapControlDSL vSpecified(double vSpec, UnitType unit) { return setVSpecified(vSpec, unit); } 
  		public TapControlDSL mvarSpecified(double mvaSpec, UnitType unit) { return setMvarSpecified(mvaSpec, unit); }
  		public TapControlDSL flowControlType(AdjControlType type) { return setFlowControlType(type); }
  		public TapControlDSL turnRatioLimit(double rMax, double rMin) { return setTapLimit(rMax, rMin); }
  		public TapControlDSL adjSteps(int n) { return setAdjSteps(n); }
  		public TapControlDSL tapStepSize(double x) {	return setTapStepSize(x); }
  		public TapControlDSL tapOnFromSide(boolean tapOnFromSide) { return setControlOnFromSide(tapOnFromSide); }
  		public TapControlDSL vcBusOnFromSide(boolean vcBusOnFromSide) { return setVcBusOnFromSide(vcBusOnFromSide); }
  		public TapControlDSL flowFrom2To(boolean flowFrom2To) { return setFlowFrom2To(flowFrom2To); }
  		public TapControlDSL meteredOnFromSide(boolean mvarSpecOnFromSide) { return setMeteredOnFromSide(mvarSpecOnFromSide); } 
	}

	private static class BaseAdjustmentDSL<T> {
		private AclfNetwork net = null;
		private T adjObj = null;
		
		public BaseAdjustmentDSL(AclfNetwork net) {
			this.net = net;
		}
		AclfNetwork getAclfAdjNet() { return this.net; }
		public T getObject() { return this.adjObj; }
		void setObject(T obj) { this.adjObj = obj; }
	}
}
