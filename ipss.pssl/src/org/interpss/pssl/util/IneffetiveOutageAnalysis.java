 /*
  * @(#) IneffetiveOutageAnalysis.java   
  *
  * Copyright (C) 2008-2013 www.interpss.org
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
  * @Date 06/15/2013
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.pssl.util;

import java.util.ArrayList;
import java.util.List;

import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.common.util.StringUtil;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.contingency.BranchOutageType;
import com.interpss.core.aclf.contingency.Contingency;
import com.interpss.core.aclf.contingency.OutageBranch;
import com.interpss.core.funcImpl.AclfFunction;
import com.interpss.core.funcImpl.ZeroZBranchProcesor;
import com.interpss.core.net.Branch;
import com.interpss.core.net.Bus;

/**
 * Utility class to search ineffective outage. Ineffective outage of a contingency is 
 * defined as a branch in a zero z branch loop with no real outage effect
 * 
 * @author mzhou
 *
 */
public class IneffetiveOutageAnalysis {
	private boolean debug = false;
	
	private AclfNetwork net = null;
	
	/**
	 * consolidated bus id set with zero Z loop on the bus 
	 */
	private List<String> zeroZLoopBusIdSet = new ArrayList<>();
	
	/**
	 * Constructor.  
	 * 
	 * @param net AclfNetwork object
	 * @throws InterpssException 
	 */
	public IneffetiveOutageAnalysis(AclfNetwork net) throws InterpssException {
		this(net,  0.0001);
	}
	
	/**
	 * Constructor. Network object bookmarked and consolidated if it is not 
	 * consolidated  
	 * 
	 * @param net AclfNetwork object
	 * @param smallBranchZ
	 * @throws InterpssException 
	 */
	public IneffetiveOutageAnalysis(AclfNetwork net, double smallBranchZ) throws InterpssException {
		if (!net.isZeroZBranchProcessed()) {
			this.net = net;
		  	IpssLogger.ipssLogger.info("Bookmar network for searching ineffective outage branch");
		  	this.net.setZeroZBranchThreshold(smallBranchZ);
			
		  	ZeroZBranchProcesor proc = new ZeroZBranchProcesor();
		  	net.accept(proc);			
		  	IpssLogger.ipssLogger.info("Network consolidation for searching ineffective outage branch");
		}
		
		// create the zeroZLoopBusIdSet
		for (AclfBus bus : net.getBusList()) {
			if (bus.isParent() && bus.getIntFlag() ==1) {
			  	this.zeroZLoopBusIdSet.add(bus.getId());
				if (this.debug)
					System.out.println("Bus with zero-Z branch loop: " + bus.getId());
			}
		}
	}

	/**
	 * constructor
	 * 
	 * @param net
	 * @param debug
	 * @throws InterpssException 
	 */
	public IneffetiveOutageAnalysis(AclfNetwork net, boolean debug) throws InterpssException {
		this(net);
		this.debug = debug;
	}
	
	/**
	 * search ineffective outage for the contingency  
	 * 
	 * @param cont
	 * @return ineffective outage branch set
	 */
	public List<OutageBranch> search(Contingency cont) {
		List<OutageBranch> branchList = new ArrayList<>();
		for (OutageBranch outBranch : cont.getOutageBranches()) {
			if (outBranch.isActive() && 
					outBranch.getOutageType() == BranchOutageType.OPEN) {  // ineffective outage branch only applies to OPEN outage type
				AclfBranch branch = outBranch.getBranch();
				String fid = branch.getFromBus().getId();    // consolidated bus
				if (branch.isChildBranch() &&                          // make sure the outage branch is a child branch of a consolidated bus
						StringUtil.contain(zeroZLoopBusIdSet, fid)) {      
					// at this point we check if the outage branch is in a zero z loop. All outage
					// branch in the contingency need to be considered also
					if (isBranchInLoop(branch, cont)) {
						branchList.add(outBranch);
						if (this.debug)
							System.out.println("Contingency " + cont.getId() + " Outage branch " + branch.getId() + 
								" in zero-Z branch loop");
					}
				}
			}
		}		
		return branchList;
	}

	private boolean isBranchInLoop(Branch branch, Contingency cont) {
		if (this.debug)
			System.out.println("Check if outage branch " + branch.getId() + " is in a zero z loop for contingency " + cont.getId());
		
		// bus/branch visited status is used to detect loop situation
		Bus parentBus = branch.getFromBus();
		parentBus.setVisited(false);
		for (Branch bra : parentBus.getConnectedPhysicalBranchList())
			bra.setVisited(false);
		for (Bus bus : parentBus.getBusSecList()) {
			bus.setVisited(false);
			for (Branch bra : bus.getConnectedPhysicalBranchList())
				bra.setVisited(false);
		}

		// start from the from side to see if the walk along the child branch path arrive at 
		// the to side
		return isBranchInLoop(branch, branch.getFromPhysicalBusConnected(), branch.getToPhysicalBusConnected(), cont);
	}

	/**
	 * For the ref branch, walk into the startBus direction, along child branch path to 
	 * recursively identify loop situation 
	 * 
	 * @param refBranch
	 * @param startBus physical bus id
	 * @param endBus physical bus id
	 * @param cont
	 * @return
	 */
	private boolean isBranchInLoop(Branch refBranch, Bus startBus, Bus endBus, Contingency cont) {
		if (this.debug)
			System.out.println("Ref branch " + refBranch.getId() + " startBus " + startBus.getId());
		
		startBus.setVisited(true);
		refBranch.setVisited(true);

		for (Branch branch : startBus.getConnectedPhysicalBranchList()) {
			if (!branch.isVisited() && 
					!branch.getId().equals(refBranch.getId())) {  // exclude ref branch from the search path
				if (!AclfFunction.isOpenOutageBranch(branch.getId(), cont.getOutageBranches())) {
					  // stop the search path if meet a outage branch
					if (branch.isChildBranch()) {    // make sure the branch to be searched is a child branch
						Bus optBus;
						try {
							optBus = branch.getConnectedPhysicalOppositeBus(startBus);
							if (optBus.getId().equals(endBus.getId())) {
								// meet the original outage branch to side, loop situation detected
								if (this.debug)
									System.out.println("Branch" + branch.getId() + " is in a zero z loop");
								return true;  
							}
							else
								// continue search
								// 
								//   condition:
								//       - branch is not a outage branch
								//       - branch is a child branch
								if (isBranchInLoop(branch, optBus, endBus, cont))
									return true;							
						} catch (InterpssException e) {
							IpssLogger.ipssLogger.severe("Programming error: " + e.toString());
						}
					}
					else {
						if (this.debug)
							System.out.println("Branch " + branch.getId() + " is not a child branch");
					}
				}
				else {
					if (this.debug)
						System.out.println("Branch " + branch.getId() + " is an outage branch");
				}
			}
		}
		return false;
	}
}
