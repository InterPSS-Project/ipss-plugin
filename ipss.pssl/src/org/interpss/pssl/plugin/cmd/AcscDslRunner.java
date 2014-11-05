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

package org.interpss.pssl.plugin.cmd;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.schema.AcscBaseFaultXmlType;
import org.ieee.odm.schema.AcscBranchFaultXmlType;
import org.ieee.odm.schema.AcscBusFaultXmlType;
import org.ieee.odm.schema.AcscFaultAnalysisXmlType;
import org.ieee.odm.schema.AcscFaultCategoryEnumType;
import org.ieee.odm.schema.AcscFaultTypeEnumType;
import org.ieee.odm.schema.ComplexXmlType;
import org.interpss.pssl.plugin.cmd.json.AcscRunConfigBean;
import org.interpss.pssl.plugin.cmd.json.BaseJSONBean;
import org.interpss.pssl.simu.IpssAcsc;
import org.interpss.pssl.simu.IpssAcsc.FaultAlgoDSL;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.acsc.AcscNetwork;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.acsc.fault.SimpleFaultType;
import com.interpss.core.datatype.IFaultResult;
import com.interpss.core.net.Network;

/**
 * Acsc Dsl ODM runner implementation
 * 
 * @author mzhou
 *
 */
public class AcscDslRunner implements IDslRunner {
	private AcscNetwork net;
	
	/**
	 * constructor
	 * 
	 * @param net AcscNetwork object
	 */
	public AcscDslRunner() {
	}
	
	public IDslRunner net(Network<?,?> net) {
		this.net = (AcscNetwork)net; return this;
	}
	
	/**
	 * run the acsc analysis case and return the analysis results
	 * 
	 * @param acscCaseXml
	 * @return
	 */
	public IFaultResult runAcsc(AcscFaultAnalysisXmlType acscCaseXml)  throws InterpssException {
  		FaultAlgoDSL algo = IpssAcsc.createAcscAlgo(this.net);
  		
		AcscBaseFaultXmlType faultXml = acscCaseXml.getAcscFault().getValue();
  		if (faultXml.getFaultType() == AcscFaultTypeEnumType.BUS_FAULT) {
  			AcscBusFaultXmlType busFault = (AcscBusFaultXmlType)faultXml;
  	  		algo.createBusFault(busFault.getRefBus().getBusId())
  	  			.faultCode(mapCode(busFault.getFaultCategory()))
  	  			.zLGFault(toComplex(busFault.getZLG()))
  	  			.zLLFault(toComplex(busFault.getZLL()))
  	  			.calculateFault();
  	  		return algo.getResult();
  		}
  		else if (faultXml.getFaultType() == AcscFaultTypeEnumType.BRANCH_FAULT) {
  			AcscBranchFaultXmlType braFault = (AcscBranchFaultXmlType)faultXml;
  	  		algo.createBranchFault(braFault.getRefBranch().getFromBusId(), braFault.getRefBranch().getToBusId(), braFault.getRefBranch().getCircuitId())
  	  			.faultCode(mapCode(braFault.getFaultCategory()))
  	  			.zLGFault(toComplex(braFault.getZLG()))
  	  			.zLLFault(toComplex(braFault.getZLL()))
  	  			.distance(braFault.getDistance())
  	  			.calculateFault();
  	  		return algo.getResult();
  		}
  		else if (faultXml.getFaultType() == AcscFaultTypeEnumType.BRANCH_OUTAGE) {
  			// TODO
  		}
		return null;
	}
	
	/**
	 * run the acsc analysis case and return the analysis results
	 * 
	 * @param acscConfigBean
	 * @return
	 */
	public <T> T run(BaseJSONBean bean)  throws InterpssException {
		AcscRunConfigBean acscBean = (AcscRunConfigBean) bean;
		FaultAlgoDSL algo = IpssAcsc.createAcscAlgo(this.net);

		if (acscBean.type==SimpleFaultType.BUS_FAULT) {
			
	  		algo.createBusFault(acscBean.faultBusId)
	  			.faultCode(acscBean.category)
	  			.zLGFault(acscBean.zLG.toComplex())
	  			.zLLFault(acscBean.zLL.toComplex())
	  			.calculateFault();
	  		return (T)algo.getResult();
		}
		else if (acscBean.type==SimpleFaultType.BRANCH_FAULT) {
			
	  		algo.createBranchFault(acscBean.faultBranchFromId, acscBean.faultBranchToId, acscBean.faultBranchCirId)
	  			.faultCode(acscBean.category)
	  			.zLGFault(acscBean.zLG.toComplex())
	  			.zLLFault(acscBean.zLL.toComplex())
	  			.distance(acscBean.distance)
	  			.calculateFault();
	  		return (T)algo.getResult();
		}
		
		return null;
	}
	
	private SimpleFaultCode mapCode(AcscFaultCategoryEnumType caty) {
		return caty == AcscFaultCategoryEnumType.FAULT_3_PHASE ? SimpleFaultCode.GROUND_3P : 
					(caty == AcscFaultCategoryEnumType.LINE_LINE_TO_GROUND ? SimpleFaultCode.GROUND_LLG :
						(caty == AcscFaultCategoryEnumType.LINE_TO_GROUND ? 
								SimpleFaultCode.GROUND_LG : SimpleFaultCode.GROUND_LL));
		
	}
	
	private Complex toComplex(ComplexXmlType x) {
		return new Complex(x.getRe(), x.getIm());
	}
}
