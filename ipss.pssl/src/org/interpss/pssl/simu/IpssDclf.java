 /*
  * @(#)IpssDclf.java   
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
  * @Date 08/15/2012
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.pssl.simu;

import static com.interpss.common.util.IpssLogger.ipssLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

import org.eclipse.emf.common.util.EList;
import org.ieee.odm.model.IODMModelParser;
import org.interpss.datatype.DblBranchValue;
import org.interpss.datatype.DblBusValue;
import org.interpss.display.DclfOutFunc;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.numeric.sparse.ISparseEqnDouble;
import org.interpss.pssl.common.PSSLException;

import com.interpss.CoreObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.DclfObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.contingency.BranchOutageType;
import com.interpss.core.aclf.contingency.BusbarOutageContingency;
import com.interpss.core.aclf.contingency.Contingency;
import com.interpss.core.aclf.contingency.MultiOutageContingency;
import com.interpss.core.aclf.contingency.OutageBranch;
import com.interpss.core.aclf.contingency.Xfr3WOutageContingency;
import com.interpss.core.aclf.flow.FlowInterface;
import com.interpss.core.aclf.flow.FlowInterfaceBranch;
import com.interpss.core.common.visitor.IAclfNetBVisitor;
import com.interpss.core.dclf.BusSenAnalysisType;
import com.interpss.core.dclf.DclfAlgorithm;
import com.interpss.core.dclf.DclfFactory;
import com.interpss.core.dclf.LODFSenAnalysisType;
import com.interpss.core.dclf.SFactorMonitorType;
import com.interpss.core.dclf.SenAnalysisBus;
import com.interpss.core.dclf.SenAnalysisType;
import com.interpss.core.dclf.common.OutageConnectivityException;
import com.interpss.core.dclf.common.ReferenceBusException;
import com.interpss.core.net.Area;
import com.interpss.core.net.Branch;


/**
 * DSL (domain specific language) for Dclf analysis

 * Key concepts:
 * 
 *    - Single Bus or Multi-bus injection/withdraw
 *    
 *        Sensitivity is always calculated based on one bus injection and one bus withdraw. 
 *        However, power transfer could be calculated with multiple bus injection and/or bus withdraw.
 *        
 */
public class IpssDclf extends BaseDSL {
	/**
	 *	GSF calculation options 
	 */
	public static enum GSFOptions { 
			LargestGSF, 
			ExcludeZeroGenP, 
			LargestBranchFlow 
	}
	
	// ================ public methods =======================

	/**
	 * constructor - create a DclfAlgorithmDSL object
	 * 
	 * @param net Aclf network object
	 */
	public static DclfAlgorithmDSL createDclfAlgorithm(AclfNetwork net) {
		return new DclfAlgorithmDSL(net, false);
	}
	
	/**
	 * constructor - create a DclfAlgorithmDSL object
	 * 
	 * @param net Aclf network object
	 * @param applyAdjust if true, apply adjustment 
	 */
	public static DclfAlgorithmDSL createDclfAlgorithm(AclfNetwork net, boolean applyAdjust) {
		return new DclfAlgorithmDSL(net, applyAdjust);
	}

	/**
	 * constructor - create a DclfAlgorithmDSL object by wrapping an existing
	 * DclfAlgo object
	 * 
	 * @param algo
	 * @return
	 */
	public static DclfAlgorithmDSL wrapAlgorithm(DclfAlgorithm algo) {
		return new DclfAlgorithmDSL(algo);
	}

	/**
	 * constructor - create a DclfAlgorithmDSL object by coping an existing
	 * DclfAlgo object for parallel processing purpose
	 * 
	 * @param algo
	 * @return
	 */
	public static DclfAlgorithmDSL copyAlgorithm(DclfAlgorithm algo) {
		DclfAlgorithm newAlgo = DclfObjectFactory.createDclfAlgorithm(algo.getNetwork(), false);
		newAlgo.setDclfSolver(algo.getDclfSolver());
		newAlgo.setDclfCalculated(algo.isDclfCalculated());
		return new DclfAlgorithmDSL(newAlgo);
	}	
	// ================ DclfAlgorithmDSL implementation =======================

	/**
	 * DclfAlgorithmDSL dclf algorithm DSL
	 */
	public static class DclfAlgorithmDSL {
		private DclfAlgorithm algo = null;
		
		private SenAnalysisType type = SenAnalysisType.PANGLE;
		private String injectBusId = null, 
		               withdrawBusId = null;
		
		// monitoring branch and interface
		private SFactorMonitorType sfMonitorType = SFactorMonitorType.BRANCH;
		//private String branchFromBusId = null, 
		//               branchToBusId = null, 
		//               branchCirId = "1";
		private AclfBranch monitoringBranch = null; 

		// monitoring flow interface
		private FlowInterface flowInterface = null;
		
		// outage branch branch
		private LODFSenAnalysisType lodfAnalysisType = LODFSenAnalysisType.SINGLE_BRANCH;
		private OutageBranch outageBranch = null; 

		/*
		 * Constructor
		 * ===========
		 */

		/**
		 * constructor
		 */
		public DclfAlgorithmDSL(AclfNetwork net, boolean applyAdjust) {
			this.algo = DclfObjectFactory.createDclfAlgorithm(net, applyAdjust);
			this.algo.setInjectBusType(BusSenAnalysisType.NOT_DEFINED);
			this.algo.setWithdrawBusType(BusSenAnalysisType.NOT_DEFINED);
		}
		
		/**
		 * constructor
		 * 
		 * @param algo
		 */
		public DclfAlgorithmDSL(DclfAlgorithm algo) {
			this.algo = algo;
		}
		
		
		/**
		 * accept the AclfNetwork visitor
		 * 
		 * @param visitor
		 * @return
		 * @throws InterpssException 
		 */
		public DclfAlgorithmDSL acceptAclfNetVisitor(IAclfNetBVisitor visitor) throws InterpssException {
			this.algo.getNetwork().accept(visitor);
			return this;
		}
		
		/*
		 * Setter/getter method
		 * ====================
		 */
		
		/**
		 * get the Dclf algo object
		 */
  		public DclfAlgorithm algo() { return this.algo; }
		/**
		 * get the Dclf algo object
		 */
  		public DclfAlgorithm getAlgorithm() { return this.algo(); }

  		/**
  		 * get the AclfNet object
  		 * 
  		 * @return
  		 */
  		public AclfNetwork aclfNet() { return this.algo().getNetwork(); }
  		/**
  		 * get the AclfNet object
  		 * 
  		 * @return
  		 */
  		public AclfNetwork getAclfNetwork() { return this.aclfNet(); }

  		/**
  		 * set the reference bus id
  		 * 
  		 * @param id
  		 * @return
  		 */
  		public DclfAlgorithmDSL setRefBus(String id) { this.algo.setRefBus(id); return this; } 
  		
  		/**
  		 * Apply the reference bus selection algorithm and set network reference bus. The buses in
  		 * the id bus array are excluded. 
  		 * 
  		 * @param idAry buses to be excluded in the reference bus selection process
  		 * @return
  		 */
  		public DclfAlgorithmDSL setRefBus(String[] idAry) { this.algo.setRefBus(idAry); return this; }
  		
  		/**
  		 * Apply the reference bus selection algorithm and set network reference bus. 
  		 * 
  		 * @return
  		 */
  		public DclfAlgorithmDSL setRefBus() { this.algo.setRefBus(new String[] {}); return this; }

  		/**
  		 * get the cached B1 matrix
  		 * 
  		 * @return
  		 * @throws InterpssException 
  		 */
  		public ISparseEqnDouble getB1Matrix() throws InterpssException { return this.algo.getB1Matrix(); }
  		/**
  		 * get the cached B11 matrix
  		 * 
  		 * @return
  		 * @throws InterpssException 
  		 */
  		public ISparseEqnDouble getB11Matrix() throws InterpssException { return this.algo.getB11Matrix(); }

  		// set sensitivity analysis type
		
		/**
		 * set	SenAnalysisType [PANGLE, QVOLTAGE]
		 */
  		public DclfAlgorithmDSL setSenAnalysisType(SenAnalysisType type) { this.type = type; return this; } 
		/**
		 * set	SenAnalysisType [PANGLE, QVOLTAGE]
		 */
  		public DclfAlgorithmDSL senAnalysisType(SenAnalysisType type) { return setSenAnalysisType(type); } 

		/**
  		 * set injection BusSenAnalysisType [SINGLE_BUS, MULTIPLE_BUS, NO_BUS] 
		 */
  		public DclfAlgorithmDSL setInjectionBusType(BusSenAnalysisType type) { this.algo.setInjectBusType(type); return this; } 
		/**
  		 * set injection BusSenAnalysisType [SINGLE_BUS, MULTIPLE_BUS, NO_BUS] 
		 */
  		public DclfAlgorithmDSL injectionBusType(BusSenAnalysisType type) { return setInjectionBusType(type); } 

		/**
  		 * set withdraw BusSenAnalysisType [SINGLE_BUS, MULTIPLE_BUS, NO_BUS]. If type = MULTIPLE_BUS,
  		 * clear the withdraw bus list 
		 */
  		public DclfAlgorithmDSL setWithdrawBusType(BusSenAnalysisType type) { 
  			this.algo.setWithdrawBusType(type); 
  			if (type == BusSenAnalysisType.MULTIPLE_BUS)
  				this.algo.getWithdrawBusList().clear();
  			return this; } 
		/**
  		 * set withdraw BusSenAnalysisType [SINGLE_BUS, MULTIPLE_BUS, NO_BUS]. If type = MULTIPLE_BUS,
  		 * clear the withdraw bus list 
		 */
  		public DclfAlgorithmDSL withdrawBusType(BusSenAnalysisType type) { return setWithdrawBusType(type); } 

  		// set injection/withdraw bus info
  		
  		/**
  		 * set injection bus id
  		 */
  		public DclfAlgorithmDSL setInjectionBusId(String id) { return injectionBusId(id); } 
  		/**
  		 * set injection bus id
  		 */
  		public DclfAlgorithmDSL injectionBusId(String id) { 
				this.injectBusId = id; this.algo.setInjectBusType(BusSenAnalysisType.SINGLE_BUS); return this; } 

  		/**
  		 * set injection bus number
  		 */
  		public DclfAlgorithmDSL setInjectionBusNo(int n) { return setInjectionBusId(IODMModelParser.BusIdPreFix+n);} 
  		/**
  		 * set injection bus number
  		 */
  		public DclfAlgorithmDSL injectionBusNo(int n) { return setInjectionBusNo(n);} 

  		/**
  		 * set withdraw bus id
  		 */
  		public DclfAlgorithmDSL setWithdrawBusId(String id) { 
  					this.withdrawBusId = id; this.algo.setWithdrawBusType(BusSenAnalysisType.SINGLE_BUS); return this; } 
  		/**
  		 * set withdraw bus id
  		 */
  		public DclfAlgorithmDSL withdrawBusId(String id) { return this.setWithdrawBusId(id); } 

  		/**
  		 * set withdraw bus number
  		 */
  		public DclfAlgorithmDSL setWithdrawBusNo(int n) { return setWithdrawBusId(IODMModelParser.BusIdPreFix+n);} 
  		/**
  		 * set withdraw bus number
  		 */
  		public DclfAlgorithmDSL withdrawBusNo(int n) { return this.setWithdrawBusId(IODMModelParser.BusIdPreFix+n); } 

  		/**
  		 *  add injection bus info for multiple bus analysis scenario, with contribution factor

  		 * @param id
  		 * @param contriPercent contribution factor in %
  		 * @return
  		 */
		public DclfAlgorithmDSL addInjectionBus(String id, double contriPercent) { 
  			if (this.algo.getInjectBusType() == BusSenAnalysisType.MULTIPLE_BUS) {
  				addBus(this.algo.getInjectBusList(), id, contriPercent);
  			} else 
  				ipssLogger.warning("addInjectionBus() can only be used when BusType = MULTIPLE_BUS");
  			return this; 
  		} 
  		/**
  		 *  add injection bus info for multiple bus analysis scenario, with contribution factor

  		 * @param n
  		 * @param contriPercent contribution factor in %
  		 * @return
  		 */
		public DclfAlgorithmDSL addInjectionBus(int n, double contriPercent) { 
			return addInjectionBus(IODMModelParser.BusIdPreFix+n, contriPercent);} 
  		
  		/**
  		 *  add withdraw bus info for multiple bus analysis scenario, with contribution factor

  		 * @param id
  		 * @param contriPercent contribution factor in %
  		 * @return
  		 */
		public DclfAlgorithmDSL addWithdrawBus(String id, double contriPercent) { 
  			if (this.algo.getWithdrawBusType() == BusSenAnalysisType.MULTIPLE_BUS) {
  				addBus(this.algo.getWithdrawBusList(), id, contriPercent);
  			} else 
  				ipssLogger.warning("addWithdrawBus() can only be used when BusType = MULTIPLE_BUS");
  			return this; } 
  		/**
  		 *  add withdraw bus info for multiple bus analysis scenario, with contribution factor

  		 * @param n
  		 * @param contriPercent contribution factor in %
  		 * @return
  		 */
		public DclfAlgorithmDSL addWithdrawBus(int n, double contriPercent) { 
			return addWithdrawBus(IODMModelParser.BusIdPreFix+n, contriPercent);} 

		/**
		 * set withdraw buses. Bus genPartFactor is used for contribution percent
		 * 
		 * @param busList
		 * @return
		 */
		public DclfAlgorithmDSL setWithdrawBus(List<AclfBus> busList) { 
			setWithdrawBusType(BusSenAnalysisType.MULTIPLE_BUS);
			// this.algo.getWithdrawBusList().clear(); - the list is cleared in the setWithdrawType() method
			for (AclfBus bus : busList) {
				addWithdrawBus(bus.getId(), bus.getGenPartFactor()*100.0);
			}
			return this;
		} 

		/**
		 * set withdraw buses
		 * 
		 * @param busList
		 * @return
		 */
		public DclfAlgorithmDSL setWithdrawBusList(EList<SenAnalysisBus> list) { 
			setWithdrawBusType(BusSenAnalysisType.MULTIPLE_BUS);
			this.algo.setWithdrawBusList(list);
			return this;
		} 
		
		/**
		 * This method first calculates bus distribution factor regarding to the load threshhold. 
		 * Then add the load bus with dist factor as contriPercent as withdraw bus
		 * 
		 * @param loadThreshhold threshhold for small load
		 * @param unit
		 * @return
		 */
		public DclfAlgorithmDSL addNetLoadBasedWithdrawBus(double loadThreshhold, UnitType unit) { 
			return _addLoadWithdrawBus(loadThreshhold, unit); }
  		
		// set monitoring branch info
		
		/**
		 * set Monitoring branch info
		 */
  		public DclfAlgorithmDSL monitorBranch(String fromId, String toId, String cirId) {
  			//this.branchFromBusId = fromId; this.branchToBusId = toId; this.branchCirId = cirId;
  			this.monitoringBranch = this.getAclfNetwork().getBranch(fromId, toId, cirId);
  			return this; } 
		/**
		 * set Monitoring branch info
		 */
  		public DclfAlgorithmDSL monitorBranch(String fromId, String toId) { 
  			return monitorBranch(fromId, toId, "1"); } 
		/**
		 * set Monitoring branch info
		 */
  		public DclfAlgorithmDSL monitorBranch(AclfBranch branch) { 
  			this.monitoringBranch = branch;
  			return this; } 
  		
  		/**
  		 * get monitor branch
  		 * 
  		 * @return
  		 */
  		public AclfBranch getMontorBranch() {
  			//AclfBranch branch = this.getAclfNetwork()
			//	.getAclfBranch(this.branchFromBusId, this.branchToBusId, this.branchCirId); 
  			return this.monitoringBranch;		}

  		/**
  		 * set monitor flow interface
  		 * 
  		 * @param inf
  		 */
  		public DclfAlgorithmDSL monitorFlowInterface(FlowInterface inf) {
  			this.flowInterface = inf; 
  			this.sfMonitorType = SFactorMonitorType.FLOW_INTERFACE; 
  			return this; } 

  		/**
  		 * set monitor flow interface
  		 * 
  		 * @param intfId
  		 */
  		public DclfAlgorithmDSL monitorFlowInterface(String intfId) {
  			return monitorFlowInterface(this.getAlgorithm().getNetwork().getFlowInterface(intfId)); 
  		} 

  		// set outage branch info

  		/**
  		 * set LODF sensitivity analysis type [SingleBranch, MultiBranch]
  		 */
  		public DclfAlgorithmDSL setLODFAnalysisType(LODFSenAnalysisType type) { 
  			this.lodfAnalysisType = type; 
  			this.algo.getOutageBranchList().clear();
  			return this; } 
  		
  		/**
  		 * set outage branch info
  		 * 
  		 * @param fromId
  		 * @param toId
  		 * @param cirId
  		 * @return
  		 * @throws PSSLException
  		 */
  		public DclfAlgorithmDSL outageBranch(String fromId, String toId, String cirId) throws PSSLException {
  			this.outageBranch(this.getAclfNetwork().getBranch(fromId, toId, cirId));
  			if (this.outageBranch == null) throw new PSSLException("Branch not found, " + fromId + "->" + toId + "(" + cirId + ")"); 
  			return this; } 
  		public DclfAlgorithmDSL outageBranch(String fromId, String toId, String cirId, BranchOutageType type) throws PSSLException {
  			this.outageBranch(this.getAclfNetwork().getBranch(fromId, toId, cirId), type);
  			if (this.outageBranch == null) throw new PSSLException("Branch not found, " + fromId + "->" + toId + "(" + cirId + ")"); 
  			return this; } 
  		/**
  		 * set outage branch info
  		 * 
  		 * @param fromId
  		 * @param toId
  		 * @return
  		 * @throws PSSLException
  		 */
  		public DclfAlgorithmDSL outageBranch(String fromId, String toId)  throws PSSLException {
  			return outageBranch(fromId, toId, "1"); } 
  		/**
  		 * set outage branch info
  		 * 
  		 * @param branch
  		 * @return
  		 * @throws PSSLException
  		 */
  		public DclfAlgorithmDSL outageBranch(AclfBranch branch)  throws PSSLException {
  			this.outageBranch = CoreObjectFactory.createOutageBranch(branch, BranchOutageType.OPEN); 
  			return this; } 
  		public DclfAlgorithmDSL outageBranch(AclfBranch branch, BranchOutageType type)  throws PSSLException {
  			this.outageBranch = CoreObjectFactory.createOutageBranch(branch, type); 
  			return this; } 
  		public OutageBranch outageBranch() { return this.outageBranch; } 

  		/**
  		 * add outage branch in the case of multi-branch-outage LODF calculation 
  		 * 
  		 * @param fromId
  		 * @param toId
  		 * @param cirId
  		 * @return
  		 */
  		public DclfAlgorithmDSL addOutageBranch(String fromId, String toId, String cirId) {
  			AclfBranch branch = this.getAclfNetwork().getBranch(fromId, toId, cirId);
  			if (branch != null)
  				addOutageBranch(CoreObjectFactory.createOutageBranch(branch)); 
  			else
  				ipssLogger.warning("Branch not found in the network " + fromId+"->"+toId+"("+cirId+")");
  			return this; } 
  		public DclfAlgorithmDSL addOutageBranch(String fromId, String toId, String cirId, BranchOutageType type) {
  			AclfBranch branch = this.getAclfNetwork().getBranch(fromId, toId, cirId);
  			if (branch != null)
  				addOutageBranch(CoreObjectFactory.createOutageBranch(branch, type)); 
  			else
  				ipssLogger.warning("Branch not found in the network " + fromId+"->"+toId+"("+cirId+")");
  			return this; } 
  		
  		/**
  		 * add outage branch in the case of multi-branch-outage LODF calculation 
  		 * 
  		 * @param fromId
  		 * @param toId
  		 * @return
  		 */
  		public DclfAlgorithmDSL addOutageBranch(String fromId, String toId) { 
  			return addOutageBranch(fromId, toId, "1"); } 
  		public DclfAlgorithmDSL addOutageBranch(String fromId, String toId, BranchOutageType type) { 
  			return addOutageBranch(fromId, toId, "1", type); } 
  		/**
  		 * add outage branch in the case of multi-branch-outage LODF calculation 
  		 * 
  		 * @param branch
  		 * @return
  		 */
  		public DclfAlgorithmDSL addOutageBranch(OutageBranch branch) { 
				this.algo.getOutageBranchList().add(branch); return this; } 
  		/**
  		 * Check if branch (id) is already an outage branch
  		 * 
  		 * @param id AclfBranch id
  		 * @return
  		 */
  		public Branch getOutageBranch(String id) {
  			for (OutageBranch branch : this.algo.getOutageBranchList()) {
  				if (id.equals(branch.getBranch().getId()))
  					return branch.getBranch();
  			}
			return null; } 
  		
  		/**
  		 * get outage branch list for multi-branch-outage LODF calculation 
  		 * 
  		 * @return
  		 */
  		public List<OutageBranch> outageBranchList() {
  			return this.algo.getOutageBranchList();	}

  		/*
		 * Analysis methods
		 * ================
		 */
  		
  		/**
  		 * run Dclf loadflow
  		 * 
  		 * @param checkCondition
  		 * @throws IpssNumericException 
  		 * @throws ReferenceBusException 
  		 * @throws InterpssException 
  		 */
  		public DclfAlgorithmDSL runDclfAnalysis(boolean checkCondition) throws InterpssException, ReferenceBusException, IpssNumericException { 
  			if (!checkCondition || this.algo.checkCondition())
  				this.algo.calculateDclf(); 
  			return this; }
  		/**
  		 * run Dclf loadflow
  		 * @throws IpssNumericException 
  		 * @throws ReferenceBusException 
  		 * @throws InterpssException 
  		 */
  		public DclfAlgorithmDSL runDclfAnalysis() throws InterpssException, ReferenceBusException, IpssNumericException { 
			return runDclfAnalysis(false);	}

  		/**
  		 * get branch Dclf flow after the Dclf calculation
  		 * 
  		 * @param fromBusId
  		 * @param toBusId
  		 * @param cirId
  		 * @param unit
  		 * @return
  		 */
  		public double branchFlow(String fromBusId, String toBusId, String cirId, UnitType unit) {
  			return algo.getBranchFlow(fromBusId, toBusId, cirId, unit);	}
  		
  		/**
  		 * get branch Dclf flow after the Dclf calculation
  		 * 
  		 * @param branch
  		 * @param unit
  		 * @return
  		 */
  		public double branchFlow(AclfBranch branch, UnitType unit) {
  			return algo.getBranchFlow(branch, unit); }
  		
  		/*
  		 * Contingency analysis
  		 * ====================
  		 */
  		
  		/**
  		 * perform contingency analysis
  		 * 
  		 * @param cont the contingency object
  		 * @param resultProcessor result processing function
  		 * @return true if there is not problme
  		 */
  		public boolean contingencyAanlysis(Contingency cont, BiConsumer<AclfBranch, Double> resultProcessor) {
  			AclfNetwork net = getAclfNetwork();
  			double baseMva = net.getBaseMva();

  			try {
  				outageBranch(cont.getOutageBranch().getBranch());

  				double outBanchPreFlow = cont.getOutageBranch().getBranch().getDclfFlow()*baseMva;		
  				for (AclfBranch branch : net.getBranchList()) {
  					double 	preFlow = branch.getDclfFlow()*baseMva,
  							LODF = monitorBranch(branch).lineOutageDFactor(),
  							postFlow = preFlow + LODF * outBanchPreFlow;
  					resultProcessor.accept(branch, postFlow);
  				}
  			} catch (ReferenceBusException | PSSLException e) {
  				ipssLogger.severe(e.toString());
  				return false;
  			}
  			return true;
  		} 

  		/**
  		 * perform multi-outage contingency analysis
  		 * 
  		 * @param cont the contingency object
  		 * @param resultProcessor result processing function
  		 * @return
  		 */
  		public boolean multiOutageContingencyAanlysis(MultiOutageContingency cont, BiConsumer<AclfBranch, Double> resultProcessor) {
  			setLODFAnalysisType(LODFSenAnalysisType.MULTI_BRANCH);
  			cont.getOutageBranches().forEach(outBranch -> {
  				addOutageBranch(outBranch);
  			});

  			try {
  	  			AclfNetwork net = getAclfNetwork();
  	  			double baseMva = net.getBaseMva();
  	  			
  	  			calLineOutageDFactors(cont.getId());

  	  			for (AclfBranch branch : net.getBranchList()) {
  	  				double preFlow = branch.getDclfFlow()*baseMva,
  	  						postFlow = 0.0;

  	  				double[] factors = monitorBranch(branch)
  	  				  					  .getLineOutageDFactors();
  	  				if (factors != null) {  // factors = null if branch is an outage branch
  	  					double sum = 0.0;
  	  					int cnt = 0;
  	  					for (OutageBranch outBranch : outageBranchList()) {
  	  						double flow = outBranch.getBranch().getDclfFlow();
  	  						sum += flow * factors[cnt++];
  	  					}
  	  					postFlow = sum*baseMva + preFlow;
  	  				}

  	  				resultProcessor.accept(branch, postFlow);
  	  			}
  			} catch (InterpssException | ReferenceBusException | IpssNumericException | OutageConnectivityException e) {
  				ipssLogger.severe(e.toString());
  				return false;
  			}
  			
  			return true;
  		}  		

  		/**
  		 * perform busbar -outage contingency analysis
  		 * 
  		 * @param cont the contingency object
  		 * @param resultProcessor result processing function
  		 * @return
  		 */
  		public boolean busbarOutageContingencyAanlysis(BusbarOutageContingency cont, BiConsumer<AclfBranch, Double> resultProcessor) {
  			// TODO
  			return true;
  		}

  		/**
  		 * perform 3W xformer outage contingency analysis
  		 * 
  		 * @param cont the contingency object
  		 * @param resultProcessor result processing function
  		 * @return
  		 */
  		public boolean xfr3WOutageContingencyAanlysis(Xfr3WOutageContingency cont, BiConsumer<AclfBranch, Double> resultProcessor) {
  			// TODO
  			return true;
  		}
  		
  		/*
  		 * Sensitivity analysis
  		 * ====================
  		 */
  		
  		/**
  		 * calculate bus sensitivity. The result is reference bus location dependent.
  		 * 
  		 * @param type sensitivity type
  		 * @param injectBusId inject bus id
  		 * @param busId bus id where sensitivity is measured
  		 */
  		public double busSensitivity(SenAnalysisType type, String injectBusId, String busId)  throws ReferenceBusException {
  			return algo.calBusSensitivity(type, injectBusId, busId); }

  		/**
  		 * calculate bus sensitivity. The result is reference bus location dependent.
  		 * 
  		 * @param type sensitivity type
  		 * @param injectBusId inject bus id
  		 * @param busSortNumber bus number where sensitivity is measured
  		 * @throws IpssNumericException 
  		 * @throws InterpssException 
  		 */
  		public double busSensitivity(SenAnalysisType type, String injectBusId, int busSortNumber)   throws ReferenceBusException, InterpssException, IpssNumericException  {
  			return this.algo.calBusSensitivity(type, injectBusId, busSortNumber); }
  		
  		/** 
  		 * line outage distribution factor for single outage line case 
  		*
  		* @param outageBranch
  		* @param monitorBranch    
  		*/
  		public double lineOutageDFactor(AclfBranch outageBranch, AclfBranch monitorBranch)   throws ReferenceBusException {
  			this.outageBranch = CoreObjectFactory.createOutageBranch(outageBranch);
  			this.monitorBranch(monitorBranch); 
  			this.sfMonitorType = SFactorMonitorType.BRANCH;
  			return this.lineOutageDFactor(); 
  		}
  		
  		/** 
  		 * line outage distribution factor for single outage line case 
  		*
  		*/
  		public double lineOutageDFactor()   throws ReferenceBusException {
  			if (this.sfMonitorType == SFactorMonitorType.BRANCH)
  	  			return algo.lineOutageDFactor(this.outageBranch.getBranch(), this.getMontorBranch());	
  			else {
  				// LODF for flow interface
  				double sum = 0.0;
  				for (FlowInterfaceBranch b : this.flowInterface.getInterfaceBranches()) {
  					this.monitorBranch(b.getBranch());
  	  	  			double sf = algo.lineOutageDFactor(this.outageBranch.getBranch(), this.getMontorBranch());	
  					sum += (b.isBranchDir()? 1.0 : -1.0) * b.getWeight() * sf;
  				}
  				return sum; 
  			}
  		}

  		/**
  		 * for the outage branch, find the branch with largest LODF
  		 * 
  		 * @return
  		 */
  		public DblBranchValue largestLODF() {
  			List<DblBranchValue> list = largestLODFs(1);
  			return list.get(0);	}

  		/**
  		 * for the outage branch, find the branch with largest LODFs
  		 * 
  		 * @param size number of largest LODFs to be calculated
  		 * @return
  		 */
  		public List<DblBranchValue> largestLODFs(int size) {
  			return _largestLODFs(size);	
  		}
  		
  		/**
  		 * calculate LODF for all branches in the network with regarding to
  		   the outage branch or branches. The following are sample code to 
  		   use the method
  		   
  		   <code>
  		    // set LODF to multiple outage branch
			setLODFAnalysisType(LODFSenAnalysisType.MULTI_BRANCH);
			// define outage branches
			for ( outage branch ) 
				addOutageBranch(outage branch);
			// calculate LODF for all branches	
			calLineOutageDFactors();
			for ( each branch) {
				double[] fAry = algoDsl.monitorBranch(monitor branch)
									.getLineOutageDFactors();
				...					
			}  		   
  		   </code>
  		 * @throws InterpssException 
  		 */
  		public void calLineOutageDFactors(String contId)   throws ReferenceBusException, OutageConnectivityException, IpssNumericException, InterpssException  {
  			this.invE_PTDF = algo.calLineOutageDFactors(contId);	}
  		private Object invE_PTDF = null;

  		/**
  		 * This method applies to multi-outage branch situation. get LODFs
  		 * of the monitor branch for the outage branches. Pre-condition : calLineOutageDFactors() called
  		 * 
  		 * @return
  		 */
  		public double[] getLineOutageDFactors() throws ReferenceBusException, IpssNumericException {
  			if (this.sfMonitorType == SFactorMonitorType.BRANCH)
  				return algo.getLineOutageDFactors(this.getMontorBranch(), this.invE_PTDF);
  			else {
  				double[] sumAry = null;
  				for (FlowInterfaceBranch b : this.flowInterface.getInterfaceBranches()) {
  					this.monitorBranch(b.getBranch());
  	  				double[] xAry = algo.getLineOutageDFactors(this.getMontorBranch(), this.invE_PTDF);
  	  				if (sumAry == null)
  	  					sumAry = new double[xAry.length];
  					double f = (b.isBranchDir()? 1.0 : -1.0) * b.getWeight();
  					int cnt = 0;
  					for (double x : xAry)
  						sumAry[cnt++] += f * x;
  				}
  				return sumAry;
  			}
  		}

  		// generator shifting factor
  		
  		/**
  		 * for the defined injection bus and monitoring branch or interface, calculate gsf
  		 */
  		public double genShiftFactor()  throws ReferenceBusException  {
  			if (this.sfMonitorType == SFactorMonitorType.BRANCH)
  				return calBranchGSF(); 
  			else {
  				// this.sfMonitorType == SFactorMonitorType.FLOW_INTERFACE
  				double sum = 0.0;
  				for (FlowInterfaceBranch b : this.flowInterface.getInterfaceBranches()) {
  					this.monitorBranch(b.getBranch());
  					sum += (b.isBranchDir()? 1.0 : -1.0) * b.getWeight() * calBranchGSF();
  				}
  				return sum; 
  			}
  		}
  		
  		private double calBranchGSF() throws ReferenceBusException {
  			if (this.algo.getInjectBusType() == BusSenAnalysisType.SINGLE_BUS) { 
  	  			if (this.algo.getWithdrawBusType() == BusSenAnalysisType.REF_BUS)
  	  				return this.algo.calGenShiftFactor(injectBusId, this.getMontorBranch());   				
  	  			else
  	  				return this.pTransferDFactor();
  			}
  			else
  				return this.multiBusTransferFactor();
  		}  		
  		
  		/**
  		 * For the defined monitoring branch, find a gen bus with largest shifting factor
  		 * 
  		 * @return
  		 */
  		//public DblBusValue largestGSF() { return largestGSF(e); }
  		/**
  		 * For the defined monitoring branch, find a gen bus with largest shifting factor
  		 * 
  		 * @param senCacheOff true to turn sensitivity cache off
  		 * @return
  		 */
  		public DblBusValue largestGSF() {
		  	List<DblBusValue> list = largestGSFs(1);
  			return list.get(0); }

  		/**
  		 * For the monitoring branch, find a set of gen buses with largest shifting factors
  		 * 
  		 * @param size number of largest GSFs
  		 * @return
  		 */
  		//public List<DblBusValue> largestGSFs(int size) { return _largestGSFs(size, GSFOptions.LargestGSF); }
  		/**
  		 * For the monitoring branch, find a set of gen buses with largest shifting factors
  		 * 
  		 * @param size number of largest GSFs
  		 * @param opt
  		 * @return
  		 */
  		public List<DblBusValue> largestGSFs(int size, GSFOptions opt)   throws ReferenceBusException { return _largestGSFs(size, opt); }
  		/**
  		 * For the monitoring branch, find a set of gen buses with largest shifting factors
  		 * 
  		 * @param size number of largest GSFs
  		 * @param senCacheOff
  		 * @return
  		 */
  		public List<DblBusValue> largestGSFs(int size) {
  			return _largestGSFs(size, GSFOptions.LargestGSF); }
  		
  		// power transfer analysis results 
  		
  		/**
  		 * calculate power transfer factor for the defined injection bus, withdraw bus(es) 
  		 * and the monitor branch
  		 */
  		public double pTransferDFactor()   throws ReferenceBusException { 
  			if (this.algo.getWithdrawBusType() == BusSenAnalysisType.SINGLE_BUS) 
  				return this.algo.pTransferDistFactor(this.injectBusId, this.withdrawBusId, this.monitoringBranch);
  			else
  				return this.algo.pTransferDistFactor(this.injectBusId, this.monitoringBranch);  // withdraw buses are defined by the withdraw bus list 
  		}

  		/**
  		 * calculate power transfer factor for the monitor branch for multiple injection
  		 * buses
  		 */
  		public double multiBusTransferFactor()  throws ReferenceBusException  { 
  			if (this.algo.getInjectBusType() == BusSenAnalysisType.MULTIPLE_BUS)
  				return this.algo.getAreaTransferFactor(this.monitoringBranch);
  			else {
				ipssLogger.warning("Wrong BusSenAnalysis type, " + this.algo.getInjectBusType()
								+ ", " + this.algo.getWithdrawBusType());
  				return 0.0;
  			}
  		}
  		
  		// loss factor
  		
  		/**
  		 * calculating loss factor with regarding to the injection bus and withdraw
  		 * bus(es) or the ref bus for the area
  		 */
  		public double lossFactor(Area area)  throws ReferenceBusException, InterpssException  { 
  			if (this.algo.getWithdrawBusType() == BusSenAnalysisType.SINGLE_BUS )
  				return this.algo.lossFactor(area, this.injectBusId, this.withdrawBusId);
  			else
  				return this.algo.lossFactor(area, this.injectBusId);	}

  		/**
  		 * calculating loss factor with regarding to the injection bus and withdraw
  		 * bus(es) or the ref bus
  		 */
  		public double lossFactor()  throws ReferenceBusException, InterpssException  { 
  			if (this.algo.getWithdrawBusType() == BusSenAnalysisType.SINGLE_BUS )
  				return this.algo.lossFactor(this.injectBusId, this.withdrawBusId);
  			else
  				return this.algo.lossFactor(this.injectBusId);	}

  		/**
  		 * calculate loss factor for the area  
  		 *  
  		 * @param injectBusId
  		 * @return
  		 */
  		public double lossFactor(Area area, String injectBusId)  throws ReferenceBusException, InterpssException  { 
  			return this.algo.lossFactor(area, injectBusId); }
  		
  		/**
  		 * calculate loss factor  
  		 *  
  		 * @param injectBusId
  		 * @return
  		 */
  		public double lossFactor(String injectBusId)  throws ReferenceBusException, InterpssException  { 
  			return this.algo.lossFactor(injectBusId); }

  		/**
  		 * calculate loss factor for the area 
  		 *  
  		 * @param injectBusId
  		 * @param withdrawBusId
  		 * @return
  		 */
  		public double lossFactor(Area area, String injectBusId, String withdrawBusId)  throws ReferenceBusException, InterpssException  { 
  			return this.algo.lossFactor(injectBusId, withdrawBusId); }
  		
  		/**
  		 * calculate loss factor  
  		 *  
  		 * @param injectBusId
  		 * @param withdrawBusId
  		 * @return
  		 */
  		public double lossFactor(String injectBusId, String withdrawBusId)  throws ReferenceBusException , InterpssException { 
  			return this.algo.lossFactor(injectBusId, withdrawBusId); }

  		/**
  		 * a utility function, add the bus to the sen analysis bus list
  		 * 
  		 * @param list
  		 * @param busId
  		 * @param percent
  		 */
  		private void addBus(EList<SenAnalysisBus> list, String busId, double percent) {
  			SenAnalysisBus bus = DclfFactory.eINSTANCE.createSenAnalysisBus();
  			bus.setBusId(busId);
  			bus.setPercent(percent);
  			list.add(bus);
  		}

  		@Override public String toString() {
  			String str = "";
  			str += "algo: " + (this.algo == null? " null" : " exists") + ", ";
  			str += "type: " + this.type + "\n";
  			str += "injectBusId: " + this.injectBusId + ", "; 
  			str += "withdrawBusId: " + this.withdrawBusId + "\n";
  			str += "sfMonitorType: " + this.sfMonitorType + ", ";
  			str += "monitoringBranchId: " + this.monitoringBranch.getId() + ", "; 
  			str += "flowInterface: " + (this.flowInterface == null? " null" : " exists") + ", ";
  			str += "lodfAnalysisType: " + this.lodfAnalysisType + ", ";
  			str += "outageBranch: " + (this.outageBranch == null? " null" : this.outageBranch.getId()) + "\n";   			
  			return str;
  		}
  		
  		////////////////////////////////////////////////
  		/////  private implementation    ///////////////
  		////////////////////////////////////////////////
		private DclfAlgorithmDSL _addLoadWithdrawBus(double loadThreshhold, UnitType unit) { 
			this.aclfNet().calLoadDFactor(loadThreshhold, unit);
			this.setWithdrawBusType(BusSenAnalysisType.MULTIPLE_BUS);
			for (AclfBus bus : this.aclfNet().getBusList()){
				if (bus.isLoad() && bus.getLoadDistFactor() > 0.0)
					addWithdrawBus(bus.getId(), bus.getLoadDistFactor()*100.0);
			}
			return this;
		} 

  		private List<DblBranchValue> _largestLODFs(int size) {
  			List<DblBranchValue> list = new ArrayList<DblBranchValue>();
  			for (int cnt = 0; cnt < size; cnt++)
  				list.add(new DblBranchValue(0.0));
  			for (Branch b : this.getAclfNetwork().getBranchList()) {
  				AclfBranch branch = (AclfBranch)b;
  				if (!branch.getId().equals(this.outageBranch.getId())) {
  					try {
  						double f = algo.lineOutageDFactor(this.outageBranch.getBranch(), branch);
  	  					DblBranchValue v = list.get(size-1);
  	  					if (Math.abs(f) > Math.abs(v.value)) {
  	  	  					v.value = f;
  	  	  					v.branch = branch;
  	  	  					Collections.sort(list, DblBranchValue.getAbsComparator());
  	  	  				}
  					} catch ( ReferenceBusException e ) {
  						ipssLogger.severe(e.toString());
  					}
  				}
  			}
  			return list;
  		}
  		
  		private List<DblBusValue> _largestGSFs(int size, GSFOptions opt) {
  			this.injectionBusType(BusSenAnalysisType.SINGLE_BUS);
  			List<DblBusValue> list = new ArrayList<DblBusValue>();
  			for (int cnt = 0; cnt < size; cnt++)
  				list.add(new DblBusValue(0.0));
		  	//if (senCacheOff)
		  	//	this.getAlgorithm().setCacheSensitivity(false);
  			for (AclfBus bus : this.getAclfNetwork().getBusList()) {
  				if (bus.isGen()) {
  					try {
  	  					if (opt == GSFOptions.LargestGSF ||
  	  							opt == GSFOptions.ExcludeZeroGenP && bus.getGenP() > 0.0) {
  	  	  					double f = injectionBusId(bus.getId()).genShiftFactor();
  	  	  					DblBusValue v = list.get(size-1);
  	  	  	  				if (Math.abs(f) > Math.abs(v.value)) {
  	  	  	  					v.value = f;
  	  	  	  					v.id = bus.getId();
  	  	  	  					v.bus = bus;
  	  	  	  					Collections.sort(list, DblBusValue.getAbsComparator());
  	  	  	  				}
  	  					}
  	  					else if (opt == GSFOptions.LargestBranchFlow && bus.getGenP() > 0.0) {
  	  						// in this case 
  	  	  					double f = bus.getGenP() * injectionBusId(bus.getId()).genShiftFactor();
  	  	  					DblBusValue v = list.get(size-1);
  	  	  	  				if (Math.abs(f) > Math.abs(v.value)) {
  	  	  	  					v.value = f;
  	  	  	  					v.id = bus.getId();
  	  	  	  					v.bus = bus;
  	  	  	  					Collections.sort(list, DblBusValue.getAbsComparator());
  	  	  	  				}
  	  					}
  					} catch ( ReferenceBusException e ) {
  						ipssLogger.severe(e.toString());
  					}
  				}
  			}
  			
		  	//if (senCacheOff)
		  	//	this.getAlgorithm().setCacheSensitivity(true);
		  	
		  	while (list.size() > 0 && Math.abs(list.get(list.size()-1).value) < DclfOutFunc.SmallBranchFlowPU)
		  		list.remove(list.size()-1);
		  	
			if (opt == GSFOptions.LargestBranchFlow) {
				for (DblBusValue v : list) {
					v.value = v.value / v.bus.getGenP();
				}
			}
		  	
  			return list; 
  		}  		
	}
}
