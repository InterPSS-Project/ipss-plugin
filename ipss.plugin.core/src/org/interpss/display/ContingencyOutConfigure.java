/*
 * @(#)ContingencyOutFunc.java   
 *
 * Copyright (C) 2006 www.interpss.org
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
 * @Date 10/27/2009
 * 
 *   Revision History
 *   ================
 *
 */

package org.interpss.display;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.simu.multicase.aclf.ContingencyAnalysis;
import com.interpss.simu.multicase.result.AclfBranchResultRec;
import com.interpss.simu.multicase.result.AclfBusResultRec;

/**
 * Contingency analysis output configuration, a default implementation
 * 
 * @author mzhou
 *
 */
public class ContingencyOutConfigure implements ContingencyOutFunc.IConfigure {
	private ContingencyAnalysis mcase;
	private AclfNetwork aclfNet;
	private boolean useIndividualBusLimit = false;
	private double busVoltageUpperLimit = 1.2;
	private double busVoltageLowerLimit = 0.8;
	
	public enum BranchSSARatingType{ratingA, ratingB, ratingC};
	
	private boolean useBranchRatingIndividualLimit = false;
	private BranchSSARatingType branchRatingType = BranchSSARatingType.ratingB;
	private double branchRating = 100;
	
	public ContingencyOutConfigure(ContingencyAnalysis mcase, AclfNetwork aclfNet) {
		this.mcase = mcase;
		this.aclfNet = aclfNet;
	}
	public ContingencyOutConfigure(ContingencyAnalysis mcase, AclfNetwork aclfNet,
			boolean useIndividualBusLimit, boolean useBranchRatingIndividualLimit) {
		this.mcase = mcase;
		this.aclfNet = aclfNet;
		this.useIndividualBusLimit = useIndividualBusLimit;
		this.useBranchRatingIndividualLimit = useBranchRatingIndividualLimit;
	}
	public void useIndividualBusLimit(boolean useIndividualBusLimit){
		this.useIndividualBusLimit = useIndividualBusLimit;
	}
	public void setBusVoltageLimit(double upperLimit, double lowerLimit){
		this.busVoltageUpperLimit = upperLimit;
		this.busVoltageLowerLimit = lowerLimit;
	}
	
	public void useBranchRatingIndividualLimit( boolean useBranchRatingIndividualLimit){
		this.useBranchRatingIndividualLimit = useBranchRatingIndividualLimit;
	}
	public void setBranchSSARatingType (BranchSSARatingType type){
		this.branchRatingType = type;
	}
	public void setBranchRating(double rating){
		this.branchRating = rating;
	}
	
	@Override public void setBusLimit() {
		if(this.useIndividualBusLimit){
			for (AclfBus aclfBus : aclfNet.getBusList()) {
				AclfBusResultRec rec = mcase.getBusResultSummary().get(aclfBus.getId());
				rec.getLimit().setUpperVoltLimit(aclfBus.getVLimit().getMax());
				rec.getLimit().setLowerVoltLimit(aclfBus.getVLimit().getMin());
			}
		}else{
			for (AclfBus aclfBus : aclfNet.getBusList()) {
				AclfBusResultRec rec = mcase.getBusResultSummary().get(aclfBus.getId());
				rec.getLimit().setUpperVoltLimit(this.busVoltageUpperLimit);
				rec.getLimit().setLowerVoltLimit(this.busVoltageLowerLimit);
			}
		}
		
		
  		
	}

	@Override public void setBranchRating() {
		if(this.useBranchRatingIndividualLimit){
			for (AclfBranch bra : aclfNet.getBranchList()) {
	  			AclfBranchResultRec rec = mcase.getBranchResultSummary().get(bra.getId());
	  			double rating = branchRatingType == BranchSSARatingType.ratingA? bra.getRatingMva1():
	  				(branchRatingType == BranchSSARatingType.ratingB? bra.getRatingMva2(): bra.getRatingMva3());	  			
	  			rec.getRating().setThermalMvaRating(rating);
	  			rec.getRating().setThermalAmpsRating(0.0);
	  		}
		}else{
			for (AclfBranch bra : aclfNet.getBranchList()) {
	  			AclfBranchResultRec rec = mcase.getBranchResultSummary().get(bra.getId());
	  			rec.getRating().setThermalMvaRating(this.branchRating);
	  			rec.getRating().setThermalAmpsRating(0.0);
	  		}
		}
		
  		
	}
}
