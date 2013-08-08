/*
 * @(#)AbstractODMDStabDataMapper.java   
 *
 * Copyright (C) 2008-2010 www.interpss.org
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
 * @Author Mike Zhou, Stephen Hau
 * @Version 1.0
 * @Date 02/15/2008
 * 
 *   Revision History
 *   ================
 *
 */

package org.interpss.mapper.odm.impl.dstab;

import static com.interpss.common.util.IpssLogger.ipssLogger;

import javax.xml.bind.JAXBElement;

import org.ieee.odm.model.dstab.DStabModelParser;
import org.ieee.odm.schema.AnalysisCategoryEnumType;
import org.ieee.odm.schema.BaseBranchXmlType;
import org.ieee.odm.schema.BranchXmlType;
import org.ieee.odm.schema.BusXmlType;
import org.ieee.odm.schema.DStabBusXmlType;
import org.ieee.odm.schema.DStabGenDataXmlType;
import org.ieee.odm.schema.DStabNetXmlType;
import org.ieee.odm.schema.ExciterModelXmlType;
import org.ieee.odm.schema.GovernorModelXmlType;
import org.ieee.odm.schema.IpssStudyScenarioXmlType;
import org.ieee.odm.schema.LFGenCodeEnumType;
import org.ieee.odm.schema.LineBranchXmlType;
import org.ieee.odm.schema.LineDStabXmlType;
import org.ieee.odm.schema.LineShortCircuitXmlType;
import org.ieee.odm.schema.LoadflowGenDataXmlType;
import org.ieee.odm.schema.MachineModelXmlType;
import org.ieee.odm.schema.NetworkCategoryEnumType;
import org.ieee.odm.schema.OriginalDataFormatEnumType;
import org.ieee.odm.schema.PSXfrBranchXmlType;
import org.ieee.odm.schema.PSXfrDStabXmlType;
import org.ieee.odm.schema.PSXfrShortCircuitXmlType;
import org.ieee.odm.schema.StabilizerModelXmlType;
import org.ieee.odm.schema.XfrBranchXmlType;
import org.ieee.odm.schema.XfrDStabXmlType;
import org.ieee.odm.schema.XfrShortCircuitXmlType;
import org.interpss.mapper.odm.ODMAclfNetMapper;
import org.interpss.mapper.odm.ODMHelper;
import org.interpss.mapper.odm.impl.aclf.AclfBusDataHelper;
import org.interpss.mapper.odm.impl.acsc.AbstractODMAcscDataMapper;

import com.interpss.CoreObjectFactory;
import com.interpss.DStabObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.msg.IPSSMsgHub;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.DStabBranch;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.mach.Machine;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

/**
 * abstract mapper implementation to map ODM DStab parser object to InterPSS DStabNetwork object
 * 
 * @author mzhou
 *
 * @param <Tfrom>
 */
public abstract class AbstractODMDStabDataMapper<Tfrom> extends AbstractODMAcscDataMapper<Tfrom> {
	protected IPSSMsgHub msg = null;
	
	/**
	 * constructor
	 */
	public AbstractODMDStabDataMapper() {
	}
		
	/**
	 * transfer info stored in the parser object into simuCtx object
	 * 
	 * @param p an ODM parser object, representing an ODM xml file.
	 * @param simuCtx
	 * @return
	 */
	@Override public boolean map2Model(Tfrom p, SimuContext simuCtx) {
		boolean noError = true;
		
		DStabModelParser parser = (DStabModelParser) p;
		if (parser.getStudyCase().getNetworkCategory() == NetworkCategoryEnumType.TRANSMISSION
				&& parser.getStudyCase().getAnalysisCategory() == AnalysisCategoryEnumType.TRANSIENT_STABILITY) {
			// get the base net xml record from the parser object
			DStabNetXmlType xmlNet = parser.getDStabNet();
			
			//XformerZTableXmlType xfrZTable = xmlNet.getXfrZTable();
			
			try {
				// create a DStabilityNetwork object and map the net info 
				DStabilityNetwork dstabNet = mapDStabNetworkData(xmlNet);
				simuCtx.setDStabilityNet(dstabNet);
				simuCtx.setNetType(SimuCtxType.DSTABILITY_NET);
				
/*				
				DynamicSimuAlgorithm dstabAlgo =DStabObjectFactory.createDynamicSimuAlgorithm(
						dstabNet, new DatabaseSimuOutputHandler(), this.msg);
*/						
				DynamicSimuAlgorithm dstabAlgo =DStabObjectFactory.createDynamicSimuAlgorithm(
						dstabNet, this.msg);
				simuCtx.setDynSimuAlgorithm(dstabAlgo);

				LoadflowAlgorithm lfAlgo = CoreObjectFactory.createLoadflowAlgorithm(dstabNet);
				dstabAlgo.setAclfAlgorithm(lfAlgo);

				// map the bus info
				AclfBusDataHelper helper = new AclfBusDataHelper(dstabNet);
				for (JAXBElement<? extends BusXmlType> bus : xmlNet.getBusList().getBus()) {
					DStabBusXmlType dstabBusXml = (DStabBusXmlType) bus.getValue();
					
					DStabBus dstabBus = DStabObjectFactory.createDStabBus(dstabBusXml.getId(), dstabNet);
						
					// base the base bus info part
					mapBaseBusData(dstabBusXml, dstabBus, dstabNet);
						
					// map the Aclf info part
					helper.setAclfBus(dstabBus);
					helper.setAclfBusData(dstabBusXml);
						
					// if the record includes Acsc bus info, do the mapping
					if (xmlNet.isHasShortCircuitData()) {
						setAcscBusData(dstabBusXml, dstabBus);
					}

					setDStabBusData(dstabBusXml, dstabBus);
				}

				// map the branch info
				ODMAclfNetMapper aclfNetMapper = new ODMAclfNetMapper();
				for (JAXBElement<? extends BaseBranchXmlType> branch : xmlNet.getBranchList().getBranch()) {
					if (branch.getValue() instanceof LineDStabXmlType || 
							branch.getValue() instanceof XfrDStabXmlType ||
								branch.getValue() instanceof PSXfrDStabXmlType) {
						DStabBranch dstabBranch = DStabObjectFactory.createDStabBranch();
						aclfNetMapper.mapAclfBranchData(branch.getValue(), dstabBranch, dstabNet);

						// if the record includes Acsc bus info, do the mapping
						if (xmlNet.isHasShortCircuitData()) {
							BranchXmlType acscBraXml = (BranchXmlType)branch.getValue(); 
							setAcscBranchData(acscBraXml, dstabBranch);
						}
					}
					else {
						ipssLogger.severe( "Error: only aclf<Branch>, acsc<Branch> and dstab<Branch> could be used for DStab study");
						noError = false;
					}
				}
				
				// map the dynamic simulation settings information
				if(parser.getStudyCase().getStudyScenario() !=null){
					IpssStudyScenarioXmlType s = (IpssStudyScenarioXmlType)parser.getStudyCase().getStudyScenario().getValue();
					new DStabScenarioHelper(dstabNet,dstabAlgo).
								mapOneFaultScenario(s);
				}
			} catch (InterpssException e) {
				ipssLogger.severe(e.toString());
				e.printStackTrace();
				noError = false;
			}
		} 
		else {
			ipssLogger.severe( "Error: wrong Transmission NetworkType and/or ApplicationType");
			return false;
		}
		
		OriginalDataFormatEnumType ofmt = parser.getStudyCase().getContentInfo().getOriginalDataFormat();
		simuCtx.getNetwork().setOriginalDataFormat(ODMHelper.map(ofmt));		
		return noError;
	}
	
	private DStabilityNetwork mapDStabNetworkData(DStabNetXmlType xmlNet) throws InterpssException {
		DStabilityNetwork dstabNet = DStabObjectFactory.createDStabilityNetwork();
		mapAcscNetworkData(dstabNet, xmlNet);
		dstabNet.setSaturatedMachineParameter(xmlNet.isSaturatedMachineParameter());
		dstabNet.setLfDataLoaded(true);
		return dstabNet;
	}	
	
	private void setDStabBusData(DStabBusXmlType dstabBusXml, DStabBus dstabBus)  throws InterpssException {
		int cnt = 0;
		/*
		 * It is assumed that contribute generators are consolidated to the equivGen, only the Aclf and Acsc part
		 */

		if(dstabBusXml.getGenData().getEquivGen()!=null){
			
			if(dstabBusXml.getGenData().getEquivGen().getValue().getCode()!=LFGenCodeEnumType.NONE_GEN){
		   
				//Please note: currently multi-generators are NOT allowed
				if (dstabBusXml.getGenData().getContributeGen() != null && dstabBusXml.getGenData().getContributeGen().size() > 1) {
					throw new InterpssException("Currently multiple contributing generators are not supported in DStab");
				}
		   
				DStabGenDataXmlType dyGen = null;
				if (dstabBusXml.getGenData().getContributeGen() != null && dstabBusXml.getGenData().getContributeGen().size() == 1) {
					dyGen = (DStabGenDataXmlType)dstabBusXml.getGenData().getContributeGen().get(0).getValue();
				}
				else 
					dyGen = (DStabGenDataXmlType)dstabBusXml.getGenData().getEquivGen().getValue();
				
				// create the machine model and added to the parent bus object
				MachineModelXmlType machXmlRec = dyGen.getMachineModel().getValue();
				String machId = dstabBus.getId() + "-mach" + ++cnt;
				Machine mach = new MachDataHelper(dstabBus, dyGen.getMvaBase(), dyGen.getRatedMachVoltage())
									.createMachine(machXmlRec, machId);
				
				if (dyGen.getExciter() != null) {
					// create the exc model and add to the parent machine object
					ExciterModelXmlType excXml = dyGen.getExciter().getValue();
					new ExciterDataHelper(mach).createExciter(excXml);

					if (dyGen.getStabilizer() != null) {
						// create the pss model and add to the parent
						// machine object
						StabilizerModelXmlType pssXml = dyGen.getStabilizer().getValue();
						new StabilizerDataHelper(mach).createStabilizer(pssXml);
					}
				}

				if (dyGen.getGovernor() != null) {
					// create the gov model and add to the parent machine object
					GovernorModelXmlType govXml = dyGen.getGovernor().getValue();
					new GovernorDataHelper(mach).createGovernor(govXml);
				}
			}
	   }	
    }
}