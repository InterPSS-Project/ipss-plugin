package org.interpss.threePhase.odm;

import static com.interpss.common.util.IpssLogger.ipssLogger;

import javax.xml.bind.JAXBElement;

import org.ieee.odm.model.dstab.DStabModelParser;
import org.ieee.odm.schema.AnalysisCategoryEnumType;
import org.ieee.odm.schema.BaseBranchXmlType;
import org.ieee.odm.schema.BranchXmlType;
import org.ieee.odm.schema.BusXmlType;
import org.ieee.odm.schema.DStabBusXmlType;
import org.ieee.odm.schema.DStabNetXmlType;
import org.ieee.odm.schema.IpssStudyScenarioXmlType;
import org.ieee.odm.schema.LineDStabXmlType;
import org.ieee.odm.schema.NetworkCategoryEnumType;
import org.ieee.odm.schema.OriginalDataFormatEnumType;
import org.ieee.odm.schema.PSXfr3WDStabXmlType;
import org.ieee.odm.schema.PSXfrDStabXmlType;
import org.ieee.odm.schema.Xfr3WDStabXmlType;
import org.ieee.odm.schema.XfrDStabXmlType;
import org.interpss.mapper.odm.ODMAclfNetMapper;
import org.interpss.mapper.odm.ODMHelper;
import org.interpss.mapper.odm.impl.aclf.AbstractODMAclfNetMapper;
import org.interpss.mapper.odm.impl.aclf.AclfBusDataHelper;
import org.interpss.mapper.odm.impl.dstab.AbstractODMDStabParserMapper;
import org.interpss.mapper.odm.impl.dstab.DStabScenarioHelper;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.basic.dstab.DStab3PGen;
import org.interpss.threePhase.basic.dstab.DStab3PLoad;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;

import com.interpss.DStabObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.msg.IPSSMsgHub;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.net.Branch;
import com.interpss.dstab.DStab3WBranch;
import com.interpss.dstab.DStabBranch;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

public class AbstractODM3PhaseDStabParserMapper<Tfrom>  extends
		AbstractODMDStabParserMapper<Tfrom> {
	
protected IPSSMsgHub msg = null;
	
	/**
	 * constructor
	 */
	public AbstractODM3PhaseDStabParserMapper() {
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
//				if(xmlNet.isPositiveSeqDataOnly()){
//					throw new Exception("The negative and zero sequence data must be provided so as to create three-phase network");
//				}
				DStabNetwork3Phase dstabNet = mapDStabNetworkData(xmlNet);
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
				AclfBusDataHelper<DStab3PGen,DStab3PLoad> helper = new AclfBusDataHelper<>(dstabNet);
				for (JAXBElement<? extends BusXmlType> bus : xmlNet.getBusList().getBus()) {
					DStabBusXmlType dstabBusXml = (DStabBusXmlType) bus.getValue();
					
					DStab3PBus dstabBus = ThreePhaseObjectFactory.create3PDStabBus(dstabBusXml.getId(), dstabNet);
						
					// base the base bus info part
					mapBaseBusData(dstabBusXml, dstabBus, dstabNet);
						
					// map the Aclf info part
					helper.setBus(dstabBus);
					helper.setAclfBusData(dstabBusXml);
						
					// if the record includes Acsc bus info, do the mapping
					if (xmlNet.isHasShortCircuitData()) {
						setAcscBusData(dstabBusXml, dstabBus);
					}

					setDStabBusData(dstabBusXml, dstabBus);
				}

				// map the branch info
				ODMAclfNetMapper aclfNetMapper = new ODMAclfNetMapper();
				for (JAXBElement<? extends BaseBranchXmlType> branchElem : xmlNet.getBranchList().getBranch()) {
					BaseBranchXmlType branch= branchElem.getValue();
					Branch dstabBranch = null;
					if   (branch instanceof Xfr3WDStabXmlType ||
							branch instanceof PSXfr3WDStabXmlType){
						dstabBranch = ThreePhaseObjectFactory.createBranch3W3Phase();
					}
					else if (branch instanceof LineDStabXmlType || 
							branch instanceof XfrDStabXmlType ||
								branch instanceof PSXfrDStabXmlType) {
						//dstabBranch = DStabObjectFactory.createDStabBranch();
						dstabBranch = ThreePhaseObjectFactory.create3PBranch();
					}
					
					if(dstabBranch != null){
						aclfNetMapper.mapAclfBranchData(branch, dstabBranch, dstabNet);

						// if the record includes Acsc bus info, do the mapping
						if (xmlNet.isHasShortCircuitData()) {
							BranchXmlType acscBraXml = (BranchXmlType)branch;
							if(branch instanceof Xfr3WDStabXmlType ||
									branch instanceof PSXfr3WDStabXmlType){
								setAcsc3WBranchData(acscBraXml, (DStab3WBranch)dstabBranch);
							}
							else
							   setAcscBranchData(acscBraXml, (DStabBranch)dstabBranch);
						}
					}
					else {
						ipssLogger.severe( "Error: only aclf<Branch>, acsc<Branch> and dstab<Branch> could be used for DStab study, \n"
								+ "branch #"+branch.getId());
						noError = false;
					}
				}
				
				/*
				 * a parent dstab net cannot contain any child network 
				 */
				
				// map the dynamic simulation settings information
				if(parser.getStudyCase().getStudyScenario() !=null){
					IpssStudyScenarioXmlType s = (IpssStudyScenarioXmlType)parser.getStudyCase().getStudyScenario().getValue();
					new DStabScenarioHelper(dstabNet,dstabAlgo).
								mapOneFaultScenario(s);
				}
				
				AbstractODMAclfNetMapper.postAclfNetProcessing(dstabNet);
			} catch (InterpssException e) {
				ipssLogger.severe(e.toString());
				e.printStackTrace();
				noError = false;
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
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
	
	private DStabNetwork3Phase mapDStabNetworkData(DStabNetXmlType xmlNet) throws InterpssException {
		DStabNetwork3Phase dstabNet = ThreePhaseObjectFactory.create3PhaseDStabNetwork();
		mapAcscNetworkData(dstabNet, xmlNet);
		dstabNet.setSaturatedMachineParameter(xmlNet.isSaturatedMachineParameter());
		dstabNet.setLfDataLoaded(true);
		return dstabNet;
	}	
	
}
