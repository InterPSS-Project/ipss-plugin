/*
  * @(#)ContingencyDslODMRunner.java   
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
  * @Date 12/15/2011
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.pssl.plugin.odm;

import org.ieee.odm.schema.ContingencyAnalysisEnumType;
import org.ieee.odm.schema.ContingencyAnalysisXmlType;
import org.interpss.display.ContingencyOutFunc;

import com.interpss.CoreObjectFactory;
import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.simu.SimuCtxType;
import com.interpss.simu.multicase.aclf.ContingencyAnalysis;
import com.interpss.simu.multicase.aclf.ContingencyAnalysisType;

/**
 * contingency DSL ODM runner implementation
 * 
 * @author mzhou
 *
 */
public class ContingencyDslODMRunner {
	private AclfNetwork net;
	
	/**
	 * constructor
	 * 
	 * @param net
	 */
	public ContingencyDslODMRunner(AclfNetwork net) {
		this.net = net;
	}
	
	/**
	 * run CA analysis using the ODM case definition
	 * 
	 * @param algoXml
	 * @return
	 */
	public StringBuffer runAnalysis(ContingencyAnalysisXmlType algoXml) {
	  	ContingencyAnalysis mscase = SimuObjectFactory.createContingencyAnalysis(SimuCtxType.ACLF_NETWORK, net);
	  	
	  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
		algo.setNonDivergent(algoXml.getDefaultAclfAlgorithm().isNonDivergent());
		algo.setTolerance(algoXml.getDefaultAclfAlgorithm().getTolerance().getValue());
		
		try {
			mscase.analysis(algo, 
					( algoXml.getType() == ContingencyAnalysisEnumType.N_1? ContingencyAnalysisType.N1 :
						(algoXml.getType() == ContingencyAnalysisEnumType.N_2? ContingencyAnalysisType.N2 :
							ContingencyAnalysisType.N11)));
		} catch (InterpssException e) {
			e.printStackTrace();
		}

		return ContingencyOutFunc.securityMargin(mscase);	
	}
}
