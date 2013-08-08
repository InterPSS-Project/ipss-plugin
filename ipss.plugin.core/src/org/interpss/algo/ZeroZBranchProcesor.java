/*
 * @(#)ZeroZBranchProcesor.java   
 *
 * Copyright (C) 2006-2012 www.interpss.com
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
 * @Date 04/15/2012
 * 
 *   Revision History
 *   ================
 *
 */
package org.interpss.algo;

import static com.interpss.common.util.IpssLogger.ipssLogger;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.contingency.Contingency;
import com.interpss.core.aclf.contingency.OutageBranch;
import com.interpss.core.common.visitor.IAclfNetBVisitor;
import com.interpss.core.net.Branch;
import com.interpss.core.net.Bus;

/**
 * A processor, a AclfNetwork visitor, to process Small/Zero Z branches.
 * 
 * @author mzhou
 * 
 */
public class ZeroZBranchProcesor implements IAclfNetBVisitor {
	private double threshold = 1.0e-10;
	private boolean allowZeroZBranchLoop = true;
	
	private List<Contingency> contingencyList = null;
	private List<String> protectedBranchIds = new ArrayList<String>();

	/**
	 * constructor
	 * 
	 * @param threshold
	 *            zero impedance is define as abs(Z) < threshold
	 */
	public ZeroZBranchProcesor(boolean allowZeroZBranchLoop) {
		this.allowZeroZBranchLoop = allowZeroZBranchLoop;
	}

	/**
	 * constructor
	 * 
	 * @param threshold
	 *            zero impedance is define as abs(Z) < threshold
	 */
	public ZeroZBranchProcesor(double threshold, boolean allowZeroZBranchLoop) {
		this.threshold = threshold;
		this.allowZeroZBranchLoop = allowZeroZBranchLoop;
	}

	/**
	 * constructor
	 * 
	 * @param threshold
	 *            zero impedance is define as abs(Z) < threshold
	 */
	public ZeroZBranchProcesor(double threshold, boolean allowZeroZBranchLoop, boolean debug) {
		this(threshold, allowZeroZBranchLoop);
		this.debug = debug;		 
	}

	/**
	 * constructor
	 * 
	 * @param threshold
	 *            zero impedance is define as abs(Z) < threshold
	 */
	public ZeroZBranchProcesor(double threshold, boolean allowZeroZBranchLoop,
			boolean debug, int busDebugNumber) {			 
		this(threshold, allowZeroZBranchLoop, debug);
		this.busDebugNumber = busDebugNumber;
	}

	/**
	 * set protected branch id list
	 * 
	 * @param list
	 */
	public void setProtectedBranchIdList(List<String> list) {
		this.protectedBranchIds = list;
	}

	/**
	 * set contingency list for protected branch
	 * 
	 * @param list
	 */
	public void setContingencyList(List<Contingency> list) {
		this.contingencyList = list;
	}

	/**
	 * constructor
	 * 
	 * @param threshold
	 *            zero impedance is define as abs(Z) < threshold
	 */
	public ZeroZBranchProcesor(double threshold) {
		this.threshold = threshold;
	}
	
	@Override
	public boolean visit(AclfNetwork net) {
		try {
			// bus and branch visited status will be used
			// in mark the zero branch
			net.setVisitedStatus(false);

			// marked those protected branches with visited = true
			if (this.protectedBranchIds.size() > 0)
				for (String id : this.protectedBranchIds)
					net.getBranch(id).setVisited(true);

			// marked contingency outage branches with visited = true
			if (this.contingencyList != null)
				for (Contingency cont : this.contingencyList) {
					for (OutageBranch outBranch : cont.getOutageBranches())
						if (outBranch.isActive())
							outBranch.getAclfBranch().setVisited(true);
				}

			// mark small Z branch with regarding to the threshold
			// line branch will be turned to ZERO_IMPEDENCE branch
			// if threshold = 0.0, Breaker branches are turned to zero-z branch
			net.markSmallZBranch(this.threshold, true);

			if (this.debug)
				debugBusBasedSearch(net);
			else
				busBasedSearch(net);

			net.setZeroZBranchProcessed(true);
			net.setBusNumberArranged(false);
		} catch (InterpssException e) {
			ipssLogger.severe(e.toString());
			return false;
		}
		return true;
	}

	private void busBasedSearch(AclfNetwork net) throws InterpssException {
		// bus and branch visited status will be used
		// in the zero Z branch processing
		net.setVisitedStatus(false);

		for (AclfBus bus : net.getBusList()) {
			if (bus.isStatus()) {
				if (!bus.isVisited()) {
					// find all buses on the zero z branch path of the bus,
					// including the bus itself
					List<Bus> list = bus.findZeroZPathBuses(this.allowZeroZBranchLoop);
					// if more than one, meaning there is zero-z branch(es),
					// process zero-z branch
					if (list.size() > 1) {
						ipssLogger.info("Select parent bus, total buses: "
								+ list.size());
						Bus parentBus = selectParentBus(list);
						ipssLogger.info("Selected parent bus: "	+ parentBus.getId());
						for (Bus childBus : list) {
							if (!childBus.getId().equals(parentBus.getId())) {
								ipssLogger.info("child bus: " + childBus.getId());
								parentBus.addSection(childBus);
							}
						}
					}
				}
			}
		}

		// turn-off processed zero-z branches
		for (AclfBranch branch : net.getBranchList()) {
			if ( branch.getBranchCode() == AclfBranchCode.ZERO_IMPEDENCE) {
				if (branch.isVisited()) {
					ipssLogger.info("Turn processed small Z branch off, " + branch.getId());
					branch.setStatus(false);
					branch.setChildBranch(true);
				} else
					ipssLogger.warning("Small Z branch not processed, " + branch.getId());
			}
		}
	}
	
	/**
	 * Select parent bus from the bus list
	 * 
	 * @param busList
	 * @return
	 */
	private Bus selectParentBus(List<Bus> busList) {
		// first select Swing or PV
		for (Bus b : busList) {
			if (b.isStatus()) {
				AclfBus bus = (AclfBus) b;
				// if the bus is a gen/load bus
				if (bus.isSwing() || bus.isGenPV())
					return b;
			}

		}
		// next select PQ bus
		for (Bus b : busList) {
			if (b.isStatus()) {
				AclfBus bus = (AclfBus) b;
				// if the bus is a gen/load bus
				if (bus.isGenPQ())
					return b;
			}

		}
		// then select Load bus
		for (Bus b : busList) {
			if (b.isStatus()) {
				AclfBus bus = (AclfBus) b;
				// if the bus is a gen/load bus
				if (bus.isLoad())
					return b;
			}
		}
		// at the end, select bus with largest zero-z branches
		Bus bus = busList.get(0);
		int cnt = 0;
		boolean found = false;
		for (Bus b : busList) {
			if (b.isStatus()) {
				found = true;
				AclfBus aclfBus = (AclfBus) b;
				// if the bus is a gen/load bus
				if (aclfBus.noConnectedBranch(AclfBranchCode.ZERO_IMPEDENCE) > cnt) {
					bus = b;
					cnt = aclfBus
							.noConnectedBranch(AclfBranchCode.ZERO_IMPEDENCE);
				}
			}
		}
		if (found == false)
			ipssLogger.warning("No parent bus was found for the bus group of: "
					+ busList.toString());

		return bus;
	}

	/*
	 * debug stuff
	 * ===========
	 */

	private boolean debug = false;
	private int busDebugNumber;

	private List<BusBasedSeaerchResult> busBasedResults = new ArrayList<BusBasedSeaerchResult>();
	private Hashtable<String, BusBasedSeaerchResult> busBasedResultTable = new Hashtable<String, BusBasedSeaerchResult>();


	public static class BusBasedSeaerchResult {
		private String processingBusId;
		private String parentBusId;
		private List<String> childBusList = new ArrayList<String>();

		public BusBasedSeaerchResult(String processingBusId) {
			this.processingBusId = processingBusId;
		}

		public void setParentBusId(String parentBusId) {
			this.parentBusId = parentBusId;
		}

		public void setParentBus(String parentBusId) {
			this.parentBusId = parentBusId;
		}

		public void addChildBus(String busId) {
			this.childBusList.add(busId);
		}

		public String getProcessingBusId() {
			return processingBusId;
		}

		public String getParentBusId() {
			return parentBusId;
		}

		public List<String> getChildBusList() {
			return childBusList;
		}

		public String toString(){
			String st = "Processing bus: "+ processingBusId + "\n";
			st = st + "Selected parent bus: "+ parentBusId + "\n";
			for (String child : this.childBusList)
				st = st + "Child bus:" + child + "\n";
			
			return st; 
		}
	}

	public List<BusBasedSeaerchResult> getBusBasedSearchResultList() {
		return this.busBasedResults;
	}

	public BusBasedSeaerchResult getBusBasedSearchResult(String busId) throws Exception {	
		if(busBasedResultTable.isEmpty())
			throw new Exception("Debug mode of ZeroZBranchProcesor needs to be enabled in order to plot the bus consolidation.");
		return busBasedResultTable.get(busId);
	}

	private void debugBusBasedSearch(AclfNetwork net) throws InterpssException {
		// bus and branch visited status will be used
		// in the zero Z branch processing
		net.setVisitedStatus(false);
		int cnt = 0;
		for (Bus b : net.getBusList()) {
			cnt++;
			// System.out.println("cnt " + ++cnt);			
			if (b.isStatus()) {
				if (!b.isVisited()) {
					if (cnt < this.busDebugNumber) {
						BusBasedSeaerchResult result = new BusBasedSeaerchResult(
								b.getId());
						System.out.println("Processing bus " + b.getId());

						List<Bus> list = ((AclfBus) b)
								.findZeroZPathBuses(allowZeroZBranchLoop);
						// if more than one, meaning there is zero-z branch(es),
						// process
						// zero-z branch
						if (list.size() > 1) {
							ipssLogger.info("Select parent bus, total buses: "
									+ list.size());
							Bus parentBus = selectParentBus(list);
							System.out.println("Select parent bus "
									+ parentBus.getId() + " total buses: "
									+ list.size());
							ipssLogger.info("Selected parent bus: "
									+ parentBus.getId());
							result.setParentBus(parentBus.getId());
							for (Bus bus : list) {
								if (!bus.getId().equals(parentBus.getId())) {
									ipssLogger
											.info("child bus: " + bus.getId());
									System.out.println("child bus: "
											+ bus.getId());
									parentBus.addSection(parentBus
											.getBusSecList().size() + 1, bus);
									result.addChildBus(bus.getId());
								}
							}
						}
						this.busBasedResults.add(result);
						for (Bus bus : list) {
							this.busBasedResultTable.put(bus.getId(), result);
						}
					}
				}
			}
		}

		// turn-off processed zero-z branches
		for (Branch b : net.getBranchList()) {
			if (((AclfBranch) b).getBranchCode() == AclfBranchCode.ZERO_IMPEDENCE) {
				if (b.isVisited()) {
					ipssLogger.info("Turn processed small Z branch off, "
							+ b.getId());
					b.setStatus(false);
				} else
					;// ipssLogger.warning("Small Z branch not processed, " +
						// b.getId());
			}
		}
	}
	
/*
	private void branchBasedSearch(AclfNetwork net) {
		// bus and branch visited status will be used
		// in the zero Z branch processing
		net.setVisitedStatus(false);

		int cnt = 0;
		for (AclfBranch b : net.getBranchList()) {
			if (b.getBranchCode() != AclfBranchCode.ZERO_IMPEDENCE) {
				cnt++;
				if (cnt == 13 || cnt == 14 // || cnt == 15
				// cnt != 14 && cnt != 15 &&
				// cnt < 20
				) {
					// System.out.println("cnt " + cnt + " " + b.getId());
					if (!b.getFromAclfBus().isVisited())
						processZeroPath(b, b.getFromAclfBus());
					if (!b.getToAclfBus().isVisited())
						processZeroPath(b, b.getToAclfBus());
				}
			}
		}
		// System.out.println("Branch processed: " + cnt);

		// turn-off processed zero-z branches
		for (Branch b : net.getBranchList()) {
			if (((AclfBranch) b).getBranchCode() == AclfBranchCode.ZERO_IMPEDENCE) {
				if (b.isVisited()) {
					ipssLogger.info("Turn processed small Z branch off, " + b.getId());
					b.setStatus(false);
					b.setChildBranch(true);
				}
			}
		}
	}
*/
	/**
	 * process branch zero Z branch path on the bus side
	 * 
	 * @param branch
	 * @param parentBus
	
	private void processZeroPath(AclfBranch branch, AclfBus bus) {
		List<Bus> list = new ArrayList<>();

		bus.setVisited(true);
		list.add(bus);

		System.out.println("Search branch: " + branch.getId() + " on side "
				+ bus.getId());
		findZeroPathBuses(list, branch, bus);

		if (list.size() > 1) {
			ipssLogger.info("Select parent bus, total buses: " + list.size());
			Bus parentBus = selectParentBus(list);
			System.out.println("Select parent bus, " + parentBus.getId()
					+ " total buses: " + list.size());
			for (Bus childBus : list) {
				if (!childBus.getId().equals(parentBus.getId())) {
					ipssLogger.info("child bus: " + childBus.getId());
					System.out.println("child bus: " + childBus.getId());
					parentBus.addSection(parentBus.getBusSecList().size() + 1,
							childBus);
				}
			}
		}
	}
*/
	/**
	 * recursively find buses along the zero Z path on the bus side. The
	 * following is the search pattern. <br>
	 * 
	 * |--- ref Branch ---|---- Zero Z Branch ---| <br>
	 * refBus optBus
	 * 
	 * @param list
	 *            bus list
	 * @param refBranch
	 *            reference branch
	 * @param refBus
	 *            the bus object for the search
	private void findZeroPathBuses(List<Bus> list, Branch refBranch, Bus refBus) {
		// check if the bus has only two active branches connected
		if (refBus.nActiveBranchConnected() == 2) {
			for (Branch bra : refBus.getBranchList()) {
				if (bra.getId() != refBranch.getId() && // make sure we get the
														// other branch
														// connected to the bus
						((AclfBranch) bra).getBranchCode() == AclfBranchCode.ZERO_IMPEDENCE) { // make
																								// sure
																								// the
																								// other
																								// branch
																								// is
																								// a
																								// zero
																								// Z
																								// branch
					if (!bra.isVisited()) { // make the other branch has not
											// been visited
						bra.setVisited(true);

						// get the bus on the opposite side regarding to the bus
						// object
						Bus optBus = bra.getOppositeBus(refBus);
						if (!optBus.isVisited()) { // make sure the other bus
													// has not been visited
							list.add(optBus);
							optBus.setVisited(true);
							// recursively search along the branch path with the
							// optBus as the ref bus
							findZeroPathBuses(list, bra, optBus);
						}
					}
				}
			}
		}
	}
	*/
}
