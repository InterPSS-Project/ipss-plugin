/*
  * @(#)AclfDslODMRunner.java   
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

import static org.interpss.mapper.odm.ODMUnitHelper.toApparentPowerUnit;

import org.ieee.odm.schema.IpssAclfAlgorithmXmlType;
import org.ieee.odm.schema.LfMethodEnumType;
import org.interpss.pssl.simu.IpssAclf;
import org.interpss.pssl.simu.IpssAclf.LfAlgoDSL;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethod;

/**
 * Run aclf DSL using ODM case definition
 * 
 * @author mzhou
 *
 */
public class AclfDslODMRunner {
	private AclfNetwork net;
	
	/**
	 * constructor
	 * 
	 * @param net AclfNetwork object
	 */
	public AclfDslODMRunner(AclfNetwork net) {
		this.net = net;
	}
	
	/**
	 * run aclf using the ODM case definition
	 * 
	 * @param algoXml
	 * @return
	 * @throws InterpssException 
	 */
	public boolean runAclf(IpssAclfAlgorithmXmlType algoXml) throws InterpssException {
		LfAlgoDSL algoDsl = IpssAclf.createAclfAlgo(net);
		
		algoDsl.lfMethod(algoXml.getLfMethod() == LfMethodEnumType.NR ? AclfMethod.NR
					: (algoXml.getLfMethod() == LfMethodEnumType.PQ ? AclfMethod.PQ
							: (algoXml.getLfMethod() == LfMethodEnumType.CUSTOM ? AclfMethod.CUSTOM 
									: AclfMethod.GS)));
		
		if (algoXml.getMaxIterations() > 0)
			algoDsl.setMaxIterations(algoXml.getMaxIterations());
		
		if (algoXml.getTolerance() != null)
			algoDsl.setTolerance(algoXml.getTolerance().getValue(), 
					toApparentPowerUnit.apply(algoXml.getTolerance().getUnit()));
		
		if (algoXml.isNonDivergent() != null)
			algoDsl.nonDivergent(algoXml.isNonDivergent());

		if (algoXml.isInitBusVoltage() != null)
			algoDsl.initBusVoltage(algoXml.isInitBusVoltage());

		if (algoXml.getAccFactor() != null)
			algoDsl.gsAccFactor(algoXml.getAccFactor());		
		
		return algoDsl.runLoadflow();	
	}
}
