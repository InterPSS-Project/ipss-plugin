 /*
  * @(#) ContingencyAnalysisHelper.java   
  *
  * Copyright (C) 2008-2012 www.interpss.org
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
  * @Date 09/15/2012
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.pssl.util;

import static com.interpss.common.util.IpssLogger.ipssLogger;

import org.eclipse.emf.common.util.EList;
import org.interpss.ext.pwd.AclfBranchPWDExtension;
import org.interpss.numeric.NumericConstant;
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.numeric.util.Number2String;
import org.interpss.pssl.common.PSSLException;
import org.interpss.pssl.simu.IpssDclf.DclfAlgorithmDSL;

import com.interpss.CoreObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IDataValidation;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.aclf.contingency.BranchOutageType;
import com.interpss.core.aclf.contingency.MonitoringBranch;
import com.interpss.core.aclf.contingency.OutageBranch;
import com.interpss.core.aclf.contingency.dep.DepContingency;
import com.interpss.core.algo.sec.SecAnalysisBranchRatingType;
import com.interpss.core.dclf.BusSenAnalysisType;
import com.interpss.core.dclf.LODFSenAnalysisType;
import com.interpss.core.dclf.SenAnalysisBus;
import com.interpss.core.dclf.common.BranchAngleSenException;
import com.interpss.core.dclf.common.OutageConnectivityException;
import com.interpss.core.dclf.common.ReferenceBusException;
import com.interpss.core.dclf.funcImpl.DclfFunction;
import com.interpss.core.net.Bus;

/**
 * Helper class for perform contingency analysis using the DclfAlgorithmDSL object,
 * 
 *   - It is assumed that DCLF has been run before the contingency analysis.
 *   - If findContraintBranches true, find constraint branches against the caRating,
 *     else perform CA for the defined monitoring branch of the contingency object 
 * 
 * 
 * @author mzhou
 *
 */
public class ContingencyAnalysisHelper {
	private SecAnalysisBranchRatingType caRatingType = SecAnalysisBranchRatingType.MVA_RATING2;

	private boolean findConstraintBranches = false; 
	private boolean useCAMonitoringStatus = false;  // use branch monitoring status defined in PWD AUX file
	private double violationThreshold = 1.0;
	
	private DclfAlgorithmDSL algoDsl = null;
	
	/**
	 * constructor
	 * 
	 * @param algoDsl dclf algorithm DSL object
	 * @throws InterpssException
	 */
	public ContingencyAnalysisHelper(DclfAlgorithmDSL algoDsl) throws InterpssException {
		this.algoDsl = algoDsl;
		if (!algoDsl.algo().isDclfCalculated())
			throw new InterpssException("Error, run DCLF before performing contingency analysis");
	}
	
	/**
	 * constructor
	 * 
	 * @param algoDsl dclf algorithm DSL object
	 * @param findContraintBranches if true, scan all branches to find constraint branches
	 * @throws InterpssException
	 */
	public ContingencyAnalysisHelper(DclfAlgorithmDSL algoDsl, boolean findContraintBranches)  throws InterpssException {
		this(algoDsl);
		this.findConstraintBranches = findContraintBranches;
	}

	/**
	 * set threshold for violation check.
	 * 
	 * @param violationThreshold
	 */
	public void setViolationThreshold(double violationThreshold) {
		this.violationThreshold = violationThreshold;
	}

	/**
	 * set use CA monitoring status.
	 * 
	 * @param b
	 */
	public void setUseCAMonitoringStatus(boolean b) {
		this.useCAMonitoringStatus = b;
	}
	
	/**
	 * Perform analysis for the contingency. 
	 * 		If findContraintBranches = false, calculate only for the monitor 
	 * 				branches defined in the contingency object. 
	 * 		If findContraintBranches = true, calculate all branches for the outage
	 * 				and stored branches with rating violation to the contingency as monitor branch.
	 * 
	 * @param cont contingency to be analyzed
	 * @return false if there is any calculation related issue 
	 * @throws ReferenceBusException
	 * @throws InterpssException
	 * @throws PSSLException
	 */
	public boolean contAnalysis(DepContingency cont) throws InterpssException {
		return contAnalysis(cont, null);
	}
	
	/**
	 * Perform analysis for the contingency. 
	 * 		If findContraintBranches = false, calculate only for the monitor 
	 * 				branches defined in the contingency object. 
	 * 		If findContraintBranches = true, calculate all branches for the outage
	 * 				and stored branches with rating violation to the contingency as monitor branch.
	 * 
	 * @param cont contingency to be analyzed
	 * @param validator for contingency data validation
	 * @return false if there is any calculation related issue 
	 * @throws ReferenceBusException
	 * @throws InterpssException
	 * @throws PSSLException
	 */
	public boolean contAnalysis(DepContingency cont, IDataValidation<DepContingency> validator) throws InterpssException {
		prepareContAnalysis(cont, validator);

		// if aclfNet.distGenList defined, they are used as the distributed ref buses
		AclfNetwork aclfNet = this.algoDsl.aclfNet();
		EList<SenAnalysisBus> distRefBusList = null;
		if (aclfNet.getDistGenList().size() > 0) {
			distRefBusList = this.algoDsl.algo().getWithdrawBusList();
			DclfFunction.mapDistGenBus2SenBus(aclfNet.getDistGenList(), distRefBusList);
		}
		
		/*
		 * If findContraintBranches = true, calculate all branches for the outage
		 * and stored branches with rating violation to the contingency as monitor branch.
		 *	 */
		if (this.findConstraintBranches) {
			cont.getMonitoringBranches().clear();
			cont.setMaxShiftedFlow(0.0);
		}
		
		if (cont.isActive()) {
			int nEquivOutageBranches = cont.nEquivOutageBranches();
			if (nEquivOutageBranches == 1) {
				try {
					if (this.findConstraintBranches) {
						/*
						 * calculate all branches for the outage and stored branches with rating violation 
						 * to the contingency as monitor branch.
						 */
						for (AclfBranch branch : this.algoDsl.algo().getNetwork().getBranchList()) {
							// bypass outage branches and island branches, and those monitoring disabled defined in AUX file 
							if (branch.isActive() && branch.getIntFlag() == 0) {
								// calculate single outage contingency for the monitor branch
								double shiftedFlow = this.singleOutageMonitorBranchFlow(cont, branch, distRefBusList);
								this.checkViolationSetMaxShiftedFlow(branch, shiftedFlow, cont);
							}
						}
					}
					else {
						/*
						 * calculation only for the monitor branches defined in the contingency object.
						 */
						for (MonitoringBranch mon : cont.getMonitoringBranches()) {
							double shiftedFlow = singleOutageMonitorBranchFlow(cont, mon.getBranch(), distRefBusList);
						    mon .setShiftedFlow(shiftedFlow);
						}
					}
				} catch ( ReferenceBusException | PSSLException |  IpssNumericException e) {
					ipssLogger.severe("Contingency: " + cont.getId() + "  " + e.toString());
					return false;
				}
			}
			else {
				// for the case cont.getNOfEquivOutageBranches() > 1
				algoDsl.setLODFAnalysisType(LODFSenAnalysisType.MULTI_BRANCH);

				//for (AclfBranch outBranch : cont.getOutageBranches()) {
				for (OutageBranch outBranch : cont.getEquivOutageBranches()) {
					if (outBranch.isActive()) {
						algoDsl.addOutageBranch(outBranch);
					}
				}

				try {
					// calculate LODF for multi-outage
					algoDsl.calLineOutageDFactors(cont.getId());

					if (this.findConstraintBranches) {
						for (AclfBranch branch : this.algoDsl.algo().getNetwork().getBranchList()) {
							// bypass outage branches and island branches, and those monitoring disabled defined in AUX file 
							if (branch.isActive() && branch.getIntFlag() == 0) {
								double shiftedFlow = this.multiOutageMonitorBranchFlow(cont, branch, distRefBusList);
								this.checkViolationSetMaxShiftedFlow(branch, shiftedFlow, cont);
							}
						}
					}
					else {
						for (MonitoringBranch mon : cont.getMonitoringBranches()) {
							double shiftedFlow = this.multiOutageMonitorBranchFlow(cont, mon.getBranch(), distRefBusList);
							mon.setShiftedFlow(shiftedFlow);
						}
					}
				} catch (OutageConnectivityException |  ReferenceBusException | PSSLException |  IpssNumericException e) {
					ipssLogger.severe("Contingency: " + cont.getId() + "  " + e.toString());
					return false;
				}
			}
		}
		return true;
	}

	private void prepareContAnalysis(DepContingency cont, IDataValidation<DepContingency> validator) throws InterpssException {
		if (validator != null)
			// check and validate the contingency object
			if (!validator.validate(cont))
				throw new InterpssException("Contingency data validation error");
		
		// search island and island bus for the contingency, search result stored
		// in the contingency object
		cont.searchIslandBus(this.algoDsl.aclfNet());
		//System.out.println("Contingency " + cont.getId() + "  " + cnt);
		
		// compute equivalent outage branch
		cont.computeEquivOutageBranch(this.algoDsl.aclfNet());
		
		if (cont.nEquivOutageBranches() == 0) 
			cont.setStatus(false);
		
		// in the following calculation, we use branch.intFlag == 0 to indicate that the branch should be included
		// in the scanning for constraint
		for (AclfBranch branch : this.algoDsl.algo().getNetwork().getBranchList()) {
			if (branch.isActive()) {
				AclfBranchPWDExtension ext = (AclfBranchPWDExtension)branch.getExtensionObject();
				boolean isMonBranch = this.useCAMonitoringStatus? ext.isCaMonitoring():true;
				if (isMonBranch) {
					boolean islandBranch = cont.isIslandBranch(branch);
					boolean active = branch.isActive() && branch.getFromBus().isActive() && branch.getToBus().isActive();
					branch.setIntFlag(active && !islandBranch ? 0 : 1);
				}
				else
					branch.setIntFlag(1);
			}
		}
		
		// mark outage branch to make sure not being processed in the
		// find-constraint-branch process
		for (OutageBranch bra : cont.getOutageBranches()) 
			bra.getBranch().setIntFlag(1);		

		for (OutageBranch bra : cont.getEquivOutageBranches()) 
			bra.getBranch().setIntFlag(1);		
	}
	
	/**
	 * Check branch post contingency rating violation. If violated, add the branch to Contingency 
	 * monitoringBranchList
	 * 
	 * @param branch
	 * @param shiftedFlow
	 * @param cont
	 */
	private void checkViolationSetMaxShiftedFlow(AclfBranch branch, double shiftedFlow, DepContingency cont) {
		AclfNetwork aclfNet = this.algoDsl.aclfNet();

		// check monitor branch rating limit violation
		boolean violation = this.algoDsl.algo().ratingViolation(branch,
						shiftedFlow, this.caRatingType, this.violationThreshold);
		if (violation) {
			// if violation, add the branch to the contingency constraint branch list
			MonitoringBranch monBranch = CoreObjectFactory.createMonitoringBranch(aclfNet, branch);
			monBranch.setShiftedFlow(shiftedFlow);
			// use cached result
			monBranch.setLoading( this.algoDsl.algo().loading(branch, shiftedFlow, this.caRatingType));
			cont.addMonitoringBranch(monBranch);
		}		
		
		if (Math.abs(shiftedFlow) > Math.abs(cont.getMaxShiftedFlow())) {
			cont.setMaxShiftedFlow(shiftedFlow);
			cont.setMaxShiftFlowBranchId(branch.getId());
		}		
	}
	
	/**
	 * Calculate branch flow on the monBranch as the result of the outage
	 * 
	 * @param cont
	 * @param outBranch
	 * @param monBranch
	 * @throws ReferenceBusException
	 */
	private double singleOutageMonitorBranchFlow(DepContingency cont, AclfBranch monBranch, EList<SenAnalysisBus> withdrawBusList) throws ReferenceBusException, InterpssException, PSSLException, IpssNumericException {
		// use equivalent outage branch for the outage branch 
		OutageBranch outBranch = cont.getActiveEquivOutageBranch();

		double flowLODF = 0.0;
		if (outBranch.getOutageType() == BranchOutageType.OPEN) {
			algoDsl.setLODFAnalysisType(LODFSenAnalysisType.SINGLE_BRANCH);
			
			AclfBranch branch = outBranch.getBranch();
			//outBranch.setIntFlag(1);
			algoDsl.outageBranch(branch);

			// calculate flow caused by the branch outage
			double lodf = algoDsl.monitorBranch(monBranch)
								 .lineOutageDFactor();
			double x = branch.getDclfFlow(),                                  // pre-dclf flow on the outage branch
				   y = shiftedFlowIslanding(cont, branch, withdrawBusList);   // flow on the outage branch caused by islanding
			flowLODF = lodf * ( x + y );
		}
		else 
			flowLODF = shiftFlowBranchClose(outBranch, monBranch);

		// calculate flow on the monitoring branch caused by islanding
		double flowLoss = shiftedFlowIslanding(cont, monBranch, withdrawBusList);
			
		return flowLODF + flowLoss;
	}
	
	/**
	 * Calculate branch flow on the monBranch as the result of the outage (multi-outage CA)
	 * 
	 * @param cont
	 * @param monBranch
	 * @throws ReferenceBusException
	 * @throws InterpssException
	 */
	private double multiOutageMonitorBranchFlow(DepContingency cont, AclfBranch monBranch, EList<SenAnalysisBus> withdrawBusList) throws ReferenceBusException, InterpssException, PSSLException, IpssNumericException {
		// calculate flow caused by outage of the branches
		double[] factors = algoDsl.monitorBranch(monBranch)
							      .getLineOutageDFactors();
		double flowLODF = 0.0;
		if (factors != null) // factors = null if branch is an outage branch
			for (OutageBranch bra : algoDsl.outageBranchList()) {
				AclfBranch aclfBra = bra.getBranch();
				if ( aclfBra.getSortNumber() >= 0) {         
					double f = factors[aclfBra.getSortNumber()];
					if (bra.getOutageType() == BranchOutageType.OPEN ) {
						/*
						 * for multiple outage with islanding case, one outage is excluded (relaxed) to avoid the 
						 * singular matrix situation. The relaxed branch is designated with sort number = -1 
						 */
						double preFlow = aclfBra.getDclfFlow(),                 // pre-dclf flow on the outage branch
							   flowDueToIslanding = shiftedFlowIslanding(cont, aclfBra, withdrawBusList);  // flow on the outage branch caused by islanding
						flowLODF += f * ( preFlow + flowDueToIslanding ); 
					}
					else {
						double equivFlow = algoDsl.algo().getBranchClosureEquivPreFlow(aclfBra);
						flowLODF += f * equivFlow;
					}
				}
			}
		
		// calculate flow of the monitoring branch caused by islanding
		double flowLoss = shiftedFlowIslanding(cont, monBranch, withdrawBusList);
			
		return flowLODF + flowLoss;
	}

	/**
	 * calculate shifted power due to Breaker Close
	 * 
	 * @param cont
	 * @param monBranch
	 * @return
	 * @throws ReferenceBusException
	 * @throws InterpssException
	 * @throws PSSLException
	 * @throws IpssNumericException
	 */
	private double shiftFlowBranchClose(OutageBranch outBranch, AclfBranch monBranch) throws ReferenceBusException, InterpssException, PSSLException, IpssNumericException {
		//algoDsl.setLODFAnalysisType(LODFSenAnalysisType.SINGLE_BRANCH);
		
		AclfBranch branch = outBranch.getBranch();
		//algoDsl.outageBranch(branch);

		// calculate flow caused by the branch outage
		double ptdf = algoDsl.algo().pTransferDistFactor(branch.getFromBus().getId(), branch.getToBus().getId(), monBranch);
		
		double pij0 = algoDsl.algo().getBranchClosureEquivPreFlow(branch);
		double x = 1.0 + algoDsl.algo().getBranchClosurePTDFactor(branch);
		double pij = pij0 / x;
		
		//double pij = algoDsl.algo().calBranchClosureFlow(branch);
		return pij / ( 1.0 - ptdf);
	}
	
	/**
	 * Calculate shifted flow for the monitoring branch because of islanding
	 * 
	 * @param contingency
	 * @param monBranch
	 * @return
	 * @throws ReferenceBusException
	 */
	private double shiftedFlowIslanding(DepContingency contingency, AclfBranch monBranch, EList<SenAnalysisBus> withdrawBusList) throws ReferenceBusException, InterpssException {
		AclfNetwork aclfNet = this.algoDsl.aclfNet();
		double flowMw = 0.0;
		
		for (BaseAclfBus bus : contingency.getIslandBuses()) {
			if (bus.isActive()) {
				// use cached result
				double gen = bus.getDclfInjectP(); // bus.getDclfBusP();
				if (Math.abs(gen) > NumericConstant.SmallDoubleNumber) {
					// island bus gen/load is balanced by the distributed Generators. If
					// distGenList not defined, use the ref bus to balance.
					if (withdrawBusList != null) {
						algoDsl.setWithdrawBusList(withdrawBusList);
					}
					else
						algoDsl.setWithdrawBusId(aclfNet.getRefBusId());
					
					double gsf = algoDsl.injectionBusId(bus.getId())
								        .monitorBranch(monBranch)
								        .genShiftFactor();		
					flowMw += -gen*gsf;
				}
			}
		}
		//ipssLogger.info("Islanding gen : " + contingency.getTotalIslandGen()*aclfNet.getBaseMva() + 
		//		"  load: " + contingency.getTotalIslandLoad()*aclfNet.getBaseMva());		
		return flowMw;
	}
	
	/**
	 * calculate post contingency PsXfr shift factor for the monitoring branch and the contingency
	 * 
	 * @param branch
	 * @param monitorBranch
	 * @param cont
	 * @return
	 * @throws BranchAngleSenException
	 * @throws InterpssException
	 * @throws ReferenceBusException
	 * @throws PSSLException
	 */
	public double postContingencyPsXfrShiftFactor(AclfBranch branch, AclfBranch monitorBranch, DepContingency cont) throws BranchAngleSenException, InterpssException, ReferenceBusException, PSSLException, IpssNumericException  {
		double sf = algoDsl.algo().psXfrShiftFactor(branch, monitorBranch);
		if ( cont.nEquivOutageBranches() == 1) {
			algoDsl.setLODFAnalysisType(LODFSenAnalysisType.SINGLE_BRANCH);
			AclfBranch outBranch = cont.getActiveEquivOutageBranch().getBranch();
			double lodf = algoDsl.outageBranch(outBranch)
								.monitorBranch(monitorBranch)
								.lineOutageDFactor();
			sf += lodf * algoDsl.algo().psXfrShiftFactor(branch, outBranch);
		}
		else {
			algoDsl.setLODFAnalysisType(LODFSenAnalysisType.MULTI_BRANCH);
			for (OutageBranch eq : cont.getEquivOutageBranches()) {
				if (eq.isActive()) 
					algoDsl.addOutageBranch(eq);
			}

			try {
				// calculate LODF for multi-outage
				algoDsl.calLineOutageDFactors(cont.getId());
				double[] factors = algoDsl.monitorBranch(monitorBranch)
					      				.getLineOutageDFactors();

				if (factors != null) // factors = null if branch is an outage branch
					for (OutageBranch bra : algoDsl.outageBranchList()) {
						if (bra.getBranch().getSortNumber() >= 0) {
							AclfBranch outageBranch = bra.getBranch();
							sf += factors[outageBranch.getSortNumber()] * algoDsl.algo().psXfrShiftFactor(branch, outageBranch);
						}
					}				
			} catch (OutageConnectivityException e) {
				//System.out.println("\"" + cont.getId() + "\",");
				ipssLogger.warning(e.toString());
			}
		}
		return sf;
	}

	/**
	 * calculate post contingency GSF for the bus and the contingency
	 * 
	 *  
	 * @param injBus
	 * @param monitorBranch
	 * @param cont
	 * @return
	 * @throws InterpssException
	 * @throws ReferenceBusException
	 * @throws PSSLException
	 */
	public double postContingencyGSF(Bus injBus, AclfBranch monitorBranch, DepContingency cont) throws InterpssException, ReferenceBusException, PSSLException, IpssNumericException {
		double gsf = algoDsl.withdrawBusType(BusSenAnalysisType.REF_BUS)
							.injectionBusId(injBus.getId())
							.monitorBranch(monitorBranch)
							.genShiftFactor();	

		if ( cont.nEquivOutageBranches() == 1) {
			algoDsl.setLODFAnalysisType(LODFSenAnalysisType.SINGLE_BRANCH);
			AclfBranch outBranch = cont.getActiveEquivOutageBranch().getBranch();
			double lodf = algoDsl.outageBranch(outBranch)
								.monitorBranch(monitorBranch)
								.lineOutageDFactor();
			gsf += lodf * algoDsl.withdrawBusType(BusSenAnalysisType.REF_BUS)
								.injectionBusId(injBus.getId())
								.monitorBranch(outBranch)
								.genShiftFactor();			
		}
		else {
			algoDsl.setLODFAnalysisType(LODFSenAnalysisType.MULTI_BRANCH);
			for (OutageBranch eq : cont.getEquivOutageBranches()) {
				if (eq.isActive()) 
					algoDsl.addOutageBranch(eq);
			}

			try {
				// calculate LODF for multi-outage
				algoDsl.calLineOutageDFactors(cont.getId());
				double[] factors = algoDsl.monitorBranch(monitorBranch)
					      				.getLineOutageDFactors();
				if (factors != null) // factors = null if branch is an outage branch
					for (OutageBranch outBranch : algoDsl.outageBranchList()) {
						if (outBranch.getBranch().getSortNumber() >= 0) {
							AclfBranch outageBranch = outBranch.getBranch();
							double gsf_i = algoDsl.withdrawBusType(BusSenAnalysisType.REF_BUS)
											.injectionBusId(injBus.getId())
											.monitorBranch(outageBranch)
											.genShiftFactor();
							gsf += gsf_i * factors[outageBranch.getSortNumber()];
						}
					}				
			} catch (OutageConnectivityException e) {
				//System.out.println("\"" + cont.getId() + "\",");
				ipssLogger.warning(e.toString());
			}
		}
		return gsf;		
	}
	
	/**
	 * Output CA results to std console
	 * 
	 * @param cont
	 */
	public void outResult(DepContingency cont) {
		AclfNetwork aclfNet = this.algoDsl.aclfNet();
		System.out.println("\n\nContingency : " + cont.getId() 
				+ ", no of outage branches: " + cont.getOutageBranches().size());
		
		for (OutageBranch bra : cont.getOutageBranches()) {
			System.out.println("Outage branch : " + bra.getId() + " " + bra.getOutageType() + 
					           "  outage branch status:" + bra.isActive() +
					           "  aclf branch status:" + bra.getBranch().isActive() +
					           "  island#:" + bra.getIslandNumber());
		}
		
		for (OutageBranch bra : cont.getEquivOutageBranches()) {
			System.out.println("Equiv Outage branch : " + bra.getId() + (bra.isActive()? " active" : " off")
					+ " " + bra.getOutageType());
		}
		
		System.out.println("Islanding gen : " + Number2String.toStr(cont.getTotalIslandGen()*aclfNet.getBaseMva()) + 
				"  load: " + Number2String.toStr(cont.getTotalIslandLoad()*aclfNet.getBaseMva()));		

		for (MonitoringBranch mon : cont.getMonitoringBranches()) {
			double pre_flow = algoDsl.algo().getBranchFlow(mon.getBranch());
			double post_flow = pre_flow + mon.getShiftedFlow();
			System.out.println("\nMonitoring branch: " + mon.getBranch().getId() + 
					"   Shifted power flow (Mw): " + Number2String.toStr(mon.getShiftedFlow()*aclfNet.getBaseMva()));
			System.out.println("branch flow(mW) [pre, post]: " + Number2String.toStr(pre_flow*aclfNet.getBaseMva()) + 
					", " + Number2String.toStr(post_flow*aclfNet.getBaseMva()) + 
					"\nloading(%): " + Number2String.toStr(Math.abs(mon.getLoading())));
			double limit = mon.getBranch().getRatingMva2();
			double pct = ((post_flow*aclfNet.getBaseMva() - limit)/limit+1)*100;
			pct = Math.abs(pct);
			System.out.println("branch flow (mw) [post, limit]: "+ Number2String.toStr(post_flow*aclfNet.getBaseMva())+
					", "+ Number2String.toStr(limit) +"\npercentage(%): "+Number2String.toStr(pct));
		}		
	}
}
