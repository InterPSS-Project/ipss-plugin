/*
 * @(#)AclfScenarioHelper.java   
 *
 * Copyright (C) 2010 www.interpss.org
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
 * @Date 09/15/2010
 * 
 *   Revision History
 *   ================
 *
 */

package org.interpss.mapper.odm.impl.aclf;

import org.ieee.odm.schema.AclfAlgorithmXmlType;
import org.ieee.odm.schema.ApparentPowerXmlType;
import org.ieee.odm.schema.LfMethodEnumType;

import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;

/**
 * Aclf scenario helper functions
 * 
 * @author mzhou
 *
 */
public class AclfScenarioHelper {
	LoadflowAlgorithm aclfAlgo = null;
	
	/**
	 * constructor
	 * 
	 * @param aclfAlgo
	 */
	public AclfScenarioHelper(LoadflowAlgorithm aclfAlgo) {
		this.aclfAlgo = aclfAlgo;
	}

	/**
	 * get the LF algorithm object
	 * 
	 * @return
	 */
	public LoadflowAlgorithm getAclfAlgo() {
		return this.aclfAlgo;
	}

	/**
	 * map the ODM AclfAlgo document to InterPSS Aclf algo model
	 * 
	 * @param xmlLfInit
	 */
	public void mapAclfAlgorithm(AclfAlgorithmXmlType xmlLfInit){
		
		// set lf method
		LfMethodEnumType lfMethod = xmlLfInit.getLfMethod();
		if(lfMethod == LfMethodEnumType.PQ){
			aclfAlgo.setLfMethod(AclfMethodType.PQ);			
		}else if(lfMethod == LfMethodEnumType.GS){
			aclfAlgo.setLfMethod(AclfMethodType.GS);			
		}
		else 
			aclfAlgo.setLfMethod(AclfMethodType.NR);			

		int maxInt =xmlLfInit.getMaxIterations();
		aclfAlgo.setMaxIterations(maxInt);
		ApparentPowerXmlType tol = xmlLfInit.getTolerance();
		aclfAlgo.setTolerance(tol.getValue());
		aclfAlgo.setInitBusVoltage(xmlLfInit.isInitBusVoltage());
		
		if(xmlLfInit.getAccFactor()!=null){
			aclfAlgo.setGsAccFactor(xmlLfInit.getAccFactor());
		}
		
		if(xmlLfInit.isNonDivergent()!=null){
			aclfAlgo.setNonDivergent(xmlLfInit.isNonDivergent());
		}	
	}
	
	/**
	 * map BranchChangeRecSetXmlType to a Contingency object
	 * 
	 * @param contingency
	 * @param net
	 * @return
	 */
	/**
	public static DepContingency mapContingency(BranchChangeRecSetXmlType contingency, AclfNetwork net) {
		DepContingency cont = CoreObjectFactory.createDepContingency(contingency.getId());
		for (BranchChangeRecXmlType bra : contingency.getBranchChangeRec()) {
			AclfBranch branch = net.getBranch(bra.getFromBusId(), bra.getToBusId(), bra.getCircuitId());
			OutageBranch outageBranch = CoreObjectFactory.createOutageBranch(branch, cont);
			cont.addOutageBranch(outageBranch);
			outageBranch.setOutageType(bra.getOutage()==BranchOutageEnumType.OPEN?BranchOutageType.OPEN:BranchOutageType.CLOSE);
		}			
		return cont;
	}
	*/
	
	/**
	 * add a contingency to a Contingency object
	 * 
	 * @param contId contingency id
	 * @param outageBranchList outage branch list defined in the contingency
	 * @param net
	 * @return
	 */
	/*
	public static DepContingency mapContingency(String contId,List<String> outageBranchList, AclfNetwork net) {
		DepContingency cont = CoreObjectFactory.createDepContingency(contId);
		for (String id : outageBranchList) {
			AclfBranch branch = net.getBranch(id);
			cont.addOutageBranch(CoreObjectFactory.createOutageBranch(branch, cont));
		}			
		return cont;
	}
	*/
}