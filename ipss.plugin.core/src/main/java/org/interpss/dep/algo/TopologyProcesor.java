/*
  * @(#)TopologyProcesor.java   
  *
  * Copyright (C) 2006-2012 www.interpss.org
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
  * @Date 10/30/2012
  * 
  *   Revision History
  *   ================
  *
  */
package org.interpss.dep.algo;

import java.util.ArrayList;
import java.util.List;

import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.StringUtil;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.net.Branch;
import com.interpss.core.net.Bus;

/**
 * Class for Network topology processing functions. Use SimuNetTopologyProcessor instead
 * 
 * @author mzhou
 *
 */
@Deprecated
public class TopologyProcesor {
	private BaseAclfNetwork<?,?> aclfNet = null;
	private List<Bus> groupBusList = null;	
	private Bus refBus = null;	
	private List<String> islandedBusList = null;
	private boolean byzone = false;
	private boolean byArea = false;	
	
	/**
	 * constructor
	 * 
	 * @param net
	 */
	public TopologyProcesor(BaseAclfNetwork<?,?> net) {
		this.aclfNet = net;
		// in the findBranchSubStation(), branch.visited status is used for branch search
		for (Branch branch : net.getBranchList())
			if (branch.isActive())
				branch.setBooleanFlag(false);
		// the status is used to find branches between substations
		for (Bus bus : net.getBusList())
			bus.setBooleanFlag(false);
	}
	
	
	/**
	 * Starting from the Breaker branch identified by branchId, find all breakers in 
	 * the substation, plus the connecting Line/Xfr/PsXfr
	 * 
	 * @param branchId
	 * @return
	 * @exception
	 */
	public List<String> findBranchInSubStation(String branchId) throws InterpssException {
		List<String> branchIdList = new ArrayList<String>();
		
		AclfBranch branch = this.aclfNet.getBranch(branchId);
		branch.setBooleanFlag(true);
		
		if (branch.getBranchCode() != AclfBranchCode.BREAKER)
			throw new InterpssException("The starting branch to findBranchInSubstation should be Breaker, "
					+ branchId + "[" + branch.getBranchCode() + "]");
		/* for branch closure outage, the aclf branch is inactive pre-contingency
		if (!branch.isActive())
			throw new InterpssException("The starting branch to findBranchInSubstation() is inactive, "
					+ branchId + "[" + branch.getBranchCode() + "]");
        */
		
		// first the branchId is added to the list
		branchIdList.add(branchId);
	
		// recursively search the from bus side
		searchBranchInSubstation(branch, branch.getFromAclfBus(), branchIdList);
		
		// recursively search the to bus side
		searchBranchInSubstation(branch, branch.getToAclfBus(), branchIdList);
		
		// reset branch.visited status to its original value (false)
		for (String id : branchIdList) {
			Branch bra = this.aclfNet.getBranch(id);
			bra.setBooleanFlag(false);
			bra.getFromBus().setBooleanFlag(true);
			/*
			for (Bus bus : bra.getFromBus().getBusSecList())
				bus.setBooleanFlag(true);
			bra.getToBus().setBooleanFlag(true);
			for (Bus bus : bra.getToBus().getBusSecList())
				bus.setBooleanFlag(true);
			*/	
		}		
		return branchIdList;
	}
	
	/**
	 * find branches between substations and add to the branchList
	 * 
	 * @param branchList
	 */
	public void addBranchBetweenSubstation(List<String> branchList, List<String> islandBusList) {
		for (String busId : islandBusList)
			this.aclfNet.getBus(busId).setBooleanFlag(true);		
		
		for (Branch branch : aclfNet.getBranchList()) {
			if (branch.isActive())
				if (branch.getFromBus().isBooleanFlag() && branch.getToBus().isBooleanFlag()) {
					if (!StringUtil.contain(branchList, branch.getId())) {
						branchList.add(branch.getId());
					}
				}
		}
		
		for (Bus bus : this.aclfNet.getBusList())
			bus.setBooleanFlag(false);		
	}	
	
	/**
	 * Starting from the refBranch (not including), search the refBus side for branches in the substation. 
	 * Add found branches to the branchIdList
	 * 
	 * @param refBranch
	 * @param refBus
	 * @param branchIdList
	 * @throws InterpssException 
	 */
	private void searchBranchInSubstation(AclfBranch refBranch, Bus refBus, List<String> branchIdList) throws InterpssException {
		for (Branch bra : refBus.getBranchList()) {
			if (!bra.getId().equals(refBranch.getId()) &&   // do not include the refBranch
					bra.isActive() &&                       // branch has to be active
					!bra.isBooleanFlag() &&                     // make sure the branch has not been visisted to prevent loop situation 
					!bra.isGroundBranch()) {                // do not include ground branches
				AclfBranch branch = (AclfBranch)bra;
				branchIdList.add(branch.getId());
				branch.setBooleanFlag(true);
				if (branch.getBranchCode() == AclfBranchCode.BREAKER)
					// if the branch is a Breaker, continue the search
					searchBranchInSubstation(branch, branch.getOppositeBus(refBus).get(), branchIdList);
			}
		}
	}
	
	
	/*
	 *  ====================================================
	 */
	/**
	 * find islanding buses for the contingency, only used for QA purpose
	 * 
	 * @param contingency
	 * @return
	 */
	
	private int distance = 7;

	private boolean distanceToNeighbourLargerThanDis(AclfBranch bra, int dis, boolean byZone, boolean byArea	) throws InterpssException{
		long zoneNum = 0;
		long areaNum = 0;
		if(byZone)
		    zoneNum = bra.getFromBus().getZone().getNumber();
		else
			areaNum = bra.getFromBus().getArea().getNumber();
		for (Branch branch : this.aclfNet.getBranchList())
			if (branch.isActive())
				branch.setBooleanFlag(false);		
		bra.setBooleanFlag(true);
		int i = 0;
		List<Bus> busList = new ArrayList<Bus>();
		List<Bus> temBusList = new ArrayList<Bus>();
		busList.add(bra.getFromBus());
		busList.add(bra.getToBus());
		temBusList = busList;
		
		while(i<dis ){
			busList = temBusList;
			temBusList = new ArrayList<Bus>();
			for(Bus b: busList){
				for (Branch bran: b.getBranchList()){
					if(bran.isActive()&& !bran.isBooleanFlag()){							
						bran.setBooleanFlag(true);
						Bus oppBus = bran.getOppositeBus(b).get();
						if(byZone){
							if(oppBus.getZone().getNumber()!=zoneNum){								
								return false;
							}else
							    temBusList.add(oppBus);
						}else{
							if(oppBus.getArea().getNumber()!=areaNum){								
								return false;
							}else
							    temBusList.add(oppBus);
						}						
					}					
				}
			}
			busList = new ArrayList<Bus>();
			i++;
		}	
		
		return true;
	}
	
	
	/**
	 * check network connectivity from within a zone or a area	 * 
	 * @param num zone/area number
	 * @param byZone search within a zone
	 * @param byArea search within a area
	 * @throws InterpssException 
	 */
	// TAG : NetworkTopologyAnalysis	
	public boolean checkConnectivity( Long num, boolean byZone, boolean byArea) throws InterpssException{
		this.groupBusList = new ArrayList<Bus>();		
		this.byArea = byArea;
		this.byzone = byZone;
		if (byZone){			
			for (Bus bus : aclfNet.getBusList()){
				if(bus.isActive() && bus.getZone().getNumber() == num)
					this.groupBusList.add(bus);
			}			
		}else{			
			for (Bus bus : aclfNet.getBusList()){
				if(bus.isActive() && bus.getArea().getNumber() == num)
					this.groupBusList.add(bus);
			}				
		}		
		// need to find the proper starting point within that zone or area
		findRefBus(num);
		//System.out.println("Ref bus is " + this.refBus.getId());
		
		return checkConnectivity(num);
	}
	
	/**
	 * find that reference bus within a zone/area as the starting porint for the search 
	 * 
	 * @param num zone/area number	 
	 * @throws InterpssException 
	 */
	private boolean findRefBus( Long num) throws InterpssException{	
		// If the swing bus is in the zone/area, use it as the ref bus
		for (Bus b: this.groupBusList){
			AclfBus bus = (AclfBus) b;				
			if(bus.isSwing()){
				this.refBus = b;
				b.setIntFlag(0);
				return true;				
			}
		}
		
		for(Bus b: this.groupBusList){
			// Othersie, find the bus that connecting other zones/areas as the ref bus
			for(Branch bra: b.getBranchList()){
				if(bra.isActive()){
					Bus oppBus = bra.getOppositeBus(b).get();
					if(this.byzone){
						if (oppBus.getZone().getNumber()!=num){
							this.refBus = b;
							b.setIntFlag(0);
							return true;								
						}							
					}else{
						if (oppBus.getArea().getNumber() != num){
							this.refBus = b;	
							b.setIntFlag(0);
							return true;							
						}
					}
				}					
			}
		}
		return false;
	}
	/**
	 * recursively check network connectivity from within a zone or a area 
	 * @param num zone/area number	 
	 * @throws InterpssException 
	 */
	private boolean checkConnectivity(Long num) throws InterpssException {
		
		initForWalk();
		
		List<Bus> islandedBusList = visitBuses();

		if (islandedBusList.isEmpty())
			return true;
		else {
			// recursively check the connectivity while the size of the group of buses being searched is
			// reduced
			this.groupBusList = islandedBusList;
			while (findRefBus(num) == true){				
				islandedBusList = visitBuses();				
				this.groupBusList = islandedBusList;				
				//System.out.println("Number of islanded buses is "+ islandedBusList.size());				
			}
				
		}
		
		this.islandedBusList = new ArrayList<String>();
		for (int i = 0; i < this.groupBusList.size(); i++) {
			Bus b = this.groupBusList.get(i);
			this.islandedBusList.add(b.getId());
		}
		return false;

	}	
	
	/**
	 * return the islanded bus ids
	 *  
	 * @return List<String> islanded bus id list	 
	 */
	public List<String> getslandedBuses() {		
		return this.islandedBusList;
	}
	

	/**
	 * initialize the network for walk through
	 * 
	 * @param 
	 * @return 	 
	 */
	private void initForWalk(){
		// walk through bus and its connected branches to mark swing bus and all connected buses
				//    bus.intFlag = 1    unmarked
				//    bus.intFlag = 0    marked
				//    bus.intFlag = -1   marked and the opposite buses of all active connected branches are marked
		for (Bus bus : this.groupBusList){
			bus.setIntFlag(bus.getId().equals(this.refBus.getId())? 0 : 1);
		}			
	}
	
	/**
	 * visit all the buses within the searching group and find out the islanded buses
	 * 
	 * @param 
	 * @return 	List<Bus> a Bus list contains all the islanded buses for this search
	 * @throws InterpssException 
	 */
	private List<Bus> visitBuses() throws InterpssException {

		this.busBranchWalkThrough();

		List<Bus> islandedBusList = new ArrayList<Bus>();
		for (Bus b : this.groupBusList) {
			if (b.isActive() && b.getIntFlag() == 1) {
				islandedBusList.add(b);
			}
		}
		return islandedBusList;
	}
	
	private void busBranchWalkThrough() throws InterpssException {		
		// then walk through the bus list until it is done
		boolean done = false;
		while (!done) {
			done = true;
			for (Bus bus : this.groupBusList) {
				// visit the bus and connected branches 
				// return false if the walk is not done
				if (!this.visitBus(bus))
					done = false;
			}			
		}			
	}
	private boolean visitBus(Bus elem) throws InterpssException { 
		boolean done = true;
		AclfBus aclfBus = (AclfBus)elem;
		if (aclfBus.getIntFlag() == 0) {  // if the bus is marked, mark the opposite buses
			for (Branch branch : aclfBus.getBranchList()) {
				if (branch.isActive()) {  // only count active branch
					Bus bus = branch.getOppositeBus(aclfBus).get();
					if (bus.getIntFlag() == 1) {
						bus.setIntFlag(0);  // mark the bus
						done = false;						
					}
				}
			}
			// after the opposite buses are marked, set the bus status as processed
			aclfBus.setIntFlag(-1);			
		}
		return done;
	}	
	
	/**
	 * @throws InterpssException 
	 * Starting from the bus, find all the connecting branches within range steps
	 * 
	 * @param busId starting bus id
	 * @param range the number of buses away from the starting bus
	 * @return List<String> a list of branches
	 * @exception
	 */
	public List<String> findBranchAroundBusWithinRange(String busId, double range) throws InterpssException{
		Bus bus = this.aclfNet.getBus(busId);
		
		for (Branch branch : this.aclfNet.getBranchList())
			if (branch.isActive())
				branch.setBooleanFlag(false);
		List<String> branchList = new ArrayList<String>();
		int i = 0;
		List<Bus> busList = new ArrayList<Bus>();
		List<Bus> temBusList = new ArrayList<Bus>();
		busList.add(bus);
		temBusList = busList;
		while(i<range){
			busList = temBusList;
			temBusList = new ArrayList<Bus>();
			for(Bus b: busList){
				for (Branch bra: b.getBranchList()){
					if(bra.isActive()&& !bra.isBooleanFlag()){
						branchList.add(bra.getId());
						bra.setBooleanFlag(true);
						Bus oppBus = bra.getOppositeBus(b).get();
						temBusList.add(oppBus);
					}					
				}
			}
			busList = new ArrayList<Bus>();
			i++;
		}		
		return branchList;
	}
}
