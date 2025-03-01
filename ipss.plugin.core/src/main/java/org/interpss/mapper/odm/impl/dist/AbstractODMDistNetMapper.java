/*
 * @(#)ODMDistNetDataMapper.java   
 *
 * Copyright (C) 2008 www.interpss.org
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
 * @Date 02/15/2011
 * 
 *   Revision History
 *   ================
 *
 */
package org.interpss.mapper.odm.impl.dist;

import static com.interpss.common.util.IpssLogger.ipssLogger;

import javax.xml.bind.JAXBElement;

import org.ieee.odm.schema.BaseBranchXmlType;
import org.ieee.odm.schema.BreakerDistBranchXmlType;
import org.ieee.odm.schema.BusXmlType;
import org.ieee.odm.schema.DistBranchXmlType;
import org.ieee.odm.schema.DistBusXmlType;
import org.ieee.odm.schema.DistScPointXmlType;
import org.ieee.odm.schema.DistributionNetXmlType;
import org.ieee.odm.schema.FeederDistBranchXmlType;
import org.ieee.odm.schema.GeneratorDistBusXmlType;
import org.ieee.odm.schema.InductionMotorDistBusXmlType;
import org.ieee.odm.schema.MixedLoadDistBusXmlType;
import org.ieee.odm.schema.NonContributingDistBusXmlType;
import org.ieee.odm.schema.ReactorDistBranchXmlType;
import org.ieee.odm.schema.ScAnalysisStdEnumType;
import org.ieee.odm.schema.SynchronousMotorDistBusXmlType;
import org.ieee.odm.schema.UtilityDistBusXmlType;
import org.ieee.odm.schema.XFormerDistBranchXmlType;
import org.interpss.mapper.odm.base.AbstractODMNetDataMapper;
import org.interpss.mapper.odm.impl.mnet.MultiNetDistHelper;

import com.interpss.common.exp.InterpssException;
import com.interpss.dist.DistBranch;
import com.interpss.dist.DistBranchCode;
import com.interpss.dist.DistBus;
import com.interpss.dist.DistBusCode;
import com.interpss.dist.DistNetwork;
import com.interpss.dist.DistObjectFactory;
import com.interpss.dist.ScStanderd;

public abstract class AbstractODMDistNetMapper<T> extends AbstractODMNetDataMapper<T, DistNetwork> {
	public AbstractODMDistNetMapper() {
	}
	
	/**
	 * transfer info stored in the parser object into the distNet object
	 * 
	 * @param p an ODM parser object, representing an ODM xml file
	 * @return DistNetwork object
	 */
	@Override
	public DistNetwork map2Model(T p) throws InterpssException {
		DistNetwork distNet = DistObjectFactory.createDistNetwork();      
		if (map2Model(p, distNet))
			return distNet;
		else
			throw new InterpssException("Error - map ODM model to create DistNetwork object");
	}
	
	/**
	 * transfer info stored in the parser object into this distNet object
	 * 
	 * @param p an ODM parser object, representing an ODM xml file
	 * @param distNet
	 * @return
	 */
	@Override
	public boolean map2Model(T from, DistNetwork distNet) {
		DistributionNetXmlType xmlNet = (DistributionNetXmlType)from;
		boolean noError = true;
		
		try {
			mapDistNetworkData(distNet, xmlNet);

			for (JAXBElement<? extends BusXmlType> bus : xmlNet.getBusList().getBus()) {
				DistBusXmlType busRec = (DistBusXmlType) bus.getValue();
				mapDistBusData(busRec, distNet);
			}

			for (JAXBElement<? extends BaseBranchXmlType> b : xmlNet.getBranchList().getBranch()) {
				DistBranchXmlType braRec = (DistBranchXmlType) b.getValue();
				mapDistBranchData(braRec, distNet);
			}
			
			/*
			 * a child dist net may contain DcSys child network(s) 
			 */
			
			if (xmlNet.isHasChildNet() != null && xmlNet.isHasChildNet()) {
				if (!new MultiNetDistHelper(distNet).mapChildNet(xmlNet.getChildNetDef()))
					noError = false;
			}			
		} catch (InterpssException e) {
			ipssLogger.severe(e.toString());
			noError = false;
		}
		
		return noError;
	}	
	
	/**
	 * Map the network info only
	 * 
	 * @param distNet
	 * @param xmlNet
	 * @return
	 */
	public void mapDistNetworkData(DistNetwork distNet, DistributionNetXmlType xmlNet) throws InterpssException {
		// map base net info
		mapNetworkData(distNet, xmlNet);
		
		distNet.setPositiveSeqDataOnly(xmlNet.isPositiveSeqDataOnly());
		
		distNet.setScStd(xmlNet.getScStd() == ScAnalysisStdEnumType.ANSI? ScStanderd.ANSI : 
					( xmlNet.getScStd() == ScAnalysisStdEnumType.IEC? 
							ScStanderd.IEC : ScStanderd.GENERIC));

		for ( DistScPointXmlType point : xmlNet.getScPoint()) {
			// TODO
		}
	}	
	
	/**
	 * Map DistBus record
	 * 
	 * @param busRec
	 * @param distNet
	 * @return
	 * @throws Exception
	 */
	public void mapDistBusData(DistBusXmlType busRec, DistNetwork distNet) throws InterpssException {
		DistBus bus = DistObjectFactory.createDistBus(busRec.getId(), distNet);
		
		// map base bus info
		mapBaseBusData(busRec, bus, distNet);

		DistBusHelper helper = new DistBusHelper(bus);
		if (busRec instanceof UtilityDistBusXmlType) {
			bus.setBusCode(DistBusCode.UTILITY);
			helper.setUtilityBusData((UtilityDistBusXmlType)busRec);
		}
		else if (busRec instanceof GeneratorDistBusXmlType) {
			bus.setBusCode(DistBusCode.GENERATOR);
			helper.setGeneratorBusData((GeneratorDistBusXmlType)busRec);
		} 
		else if (busRec instanceof SynchronousMotorDistBusXmlType) {
			bus.setBusCode(DistBusCode.SYN_MOTOR);
			helper.setSynchronousMotorBusData((SynchronousMotorDistBusXmlType)busRec);
		} 
		else if (busRec instanceof InductionMotorDistBusXmlType) {
			bus.setBusCode(DistBusCode.IND_MOTOR);
			helper.setInductionMotorBusData((InductionMotorDistBusXmlType)busRec);
		} 
		else if (busRec instanceof MixedLoadDistBusXmlType) {
			bus.setBusCode(DistBusCode.MIXED_LOAD);
			helper.setMixedLoadBusData((MixedLoadDistBusXmlType)busRec);
		} 
		else if (busRec instanceof NonContributingDistBusXmlType) {
			bus.setBusCode(DistBusCode.NON_CONTRIBUTE);
			helper.setNonContributingBusData((NonContributingDistBusXmlType)busRec);			
		} 
	}

	/**
	 * Set DistBranch data
	 * 
	 * @param branchRec
	 * @param distNet
	 * @throws InterpssException
	 */
	public void mapDistBranchData(DistBranchXmlType branchRec, DistNetwork distNet) throws InterpssException {
		DistBranch branch = DistObjectFactory.createDistBranch();
		
		// map base branch info
		mapBaseBranchRec(branchRec, branch, distNet);

		DistBranchHelper helper = new DistBranchHelper(branch);
		if (branchRec instanceof FeederDistBranchXmlType) {
			branch.setBranchCode(DistBranchCode.FEEDER);
			helper.setFeederBranchData((FeederDistBranchXmlType)branchRec);
		}		
		else if (branchRec instanceof XFormerDistBranchXmlType) {
			branch.setBranchCode(DistBranchCode.TRANSFROMER);
			helper.setXFormerBranchData((XFormerDistBranchXmlType)branchRec);
		}		
		else if (branchRec instanceof ReactorDistBranchXmlType) {
			branch.setBranchCode(DistBranchCode.REACTOR);
			helper.setReactorBranchData((ReactorDistBranchXmlType)branchRec);			
		}		
		else if (branchRec instanceof BreakerDistBranchXmlType) {
			branch.setBranchCode(DistBranchCode.BREAKER);
			helper.setBreakerBranchData((BreakerDistBranchXmlType)branchRec);			
		}		
	}
}