/*
 * @(#)AbstractODMAcscDataMapper.java   
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
 * @Date 02/15/2008
 * 
 *   Revision History
 *   ================
 *
 */

package org.interpss.mapper.odm.impl.acsc;

import static com.interpss.common.util.IpssLogger.ipssLogger;
import static com.interpss.core.funcImpl.AcscFunction.AcscLineAptr;
import static com.interpss.core.funcImpl.AcscFunction.AcscXfrAptr;
import static org.interpss.mapper.odm.ODMUnitHelper.ToYUnit;
import static org.interpss.mapper.odm.ODMUnitHelper.ToZUnit;

import javax.activation.UnsupportedDataTypeException;
import javax.xml.bind.JAXBElement;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.model.acsc.AcscModelParser;
import org.ieee.odm.schema.AnalysisCategoryEnumType;
import org.ieee.odm.schema.BaseBranchXmlType;
import org.ieee.odm.schema.BranchXmlType;
import org.ieee.odm.schema.BusXmlType;
import org.ieee.odm.schema.GroundingEnumType;
import org.ieee.odm.schema.GroundingXmlType;
import org.ieee.odm.schema.LFGenCodeEnumType;
import org.ieee.odm.schema.LineShortCircuitXmlType;
import org.ieee.odm.schema.NetworkCategoryEnumType;
import org.ieee.odm.schema.OriginalDataFormatEnumType;
import org.ieee.odm.schema.PSXfrShortCircuitXmlType;
import org.ieee.odm.schema.ShortCircuitBusEnumType;
import org.ieee.odm.schema.ShortCircuitBusXmlType;
import org.ieee.odm.schema.ShortCircuitGenDataXmlType;
import org.ieee.odm.schema.ShortCircuitLoadDataXmlType;
import org.ieee.odm.schema.ShortCircuitNetXmlType;
import org.ieee.odm.schema.XformerConnectionXmlType;
import org.ieee.odm.schema.XformrtConnectionEnumType;
import org.ieee.odm.schema.XfrShortCircuitXmlType;
import org.ieee.odm.schema.YXmlType;
import org.ieee.odm.schema.ZXmlType;
import org.interpss.mapper.odm.ODMAclfNetMapper;
import org.interpss.mapper.odm.ODMHelper;
import org.interpss.mapper.odm.impl.aclf.AbstractODMAclfParserMapper;
import org.interpss.mapper.odm.impl.aclf.AclfBusDataHelper;
import org.interpss.numeric.NumericConstant;
import org.interpss.numeric.datatype.Unit.UnitType;

import com.interpss.CoreObjectFactory;
import com.interpss.common.datatype.UnitHelper;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.acsc.AcscBranch;
import com.interpss.core.acsc.AcscBus;
import com.interpss.core.acsc.AcscNetwork;
import com.interpss.core.acsc.BusGroundCode;
import com.interpss.core.acsc.BusScCode;
import com.interpss.core.acsc.SequenceCode;
import com.interpss.core.acsc.XfrConnectCode;
import com.interpss.core.acsc.adpter.AcscLine;
import com.interpss.core.acsc.adpter.AcscXformer;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

/**
 * abstract mapper implementation to map ODM parser object to InterPSS AcscNetwork object
 * 
 * @author mzhou
 *
 * @param <Tfrom>
 */
public abstract class AbstractODMAcscDataMapper<Tfrom> extends AbstractODMAclfParserMapper<Tfrom> {
	/**
	 * constructor
	 * 
	 */
	public AbstractODMAcscDataMapper() {
	}
	
	/**
	 * transfer info stored in the parser object into simuCtx object. 
	 * 
	 * @param p a ODM parser object, representign an ODM xml file
	 * @param simuCtx
	 * @return
	 */
	@Override public boolean map2Model(Tfrom p, SimuContext simuCtx) {
		boolean noError = true;

		AcscModelParser parser = (AcscModelParser) p;
		if (simuCtx.getNetType() != SimuCtxType.ACSC_NET) {
			ipssLogger.severe("SimuNetwork type should be set to ACSC_FAULT_NET for mapping ODM to SimpleFaultNetwork");
			return false;
		}
		
		if (parser.getStudyCase().getNetworkCategory() == NetworkCategoryEnumType.TRANSMISSION
				&& parser.getStudyCase().getAnalysisCategory() == AnalysisCategoryEnumType.SHORT_CIRCUIT) {
			// get the base net xml record from the parser object
			ShortCircuitNetXmlType xmlNet = parser.getAcscNet();
			
			//XformerZTableXmlType xfrZTable = xmlNet.getXfrZTable();
			
			try {
				// create a AcscFaultNetwork object and map the net info 
				AcscNetwork acscFaultNet =  CoreObjectFactory.createAcscNetwork();						
				simuCtx.setAcscNet(acscFaultNet);

				mapAcscNetworkData(acscFaultNet,xmlNet);

				// map the bus info
				AclfBusDataHelper helper = new AclfBusDataHelper(acscFaultNet);
				for (JAXBElement<? extends BusXmlType> busXml : xmlNet.getBusList().getBus()) {
					ShortCircuitBusXmlType acscBusXml = (ShortCircuitBusXmlType)busXml.getValue();
					// for short circuit, the bus could be acscBus or acscNoLFBus 
					AcscBus acscBus = CoreObjectFactory.createAcscBus(acscBusXml.getId(), acscFaultNet);		
					// add the acscBus object into acscNet and build bus <-> net relationship
					//acscNet.addBus(acscBus);

					// map the base bus info part
					mapBaseBusData(acscBusXml, acscBus, acscFaultNet);

					// map the Aclf info part		
					if (acscFaultNet.isLfDataLoaded()) {
						helper.setAclfBus(acscBus);
						helper.setAclfBusData(acscBusXml);
					}
					
					setAcscBusData(acscBusXml, acscBus);
				}

				// map the branch info
				ODMAclfNetMapper aclfNetMapper = new ODMAclfNetMapper();
				for (JAXBElement<? extends BaseBranchXmlType> branch : xmlNet.getBranchList().getBranch()) {
					if (branch.getValue() instanceof LineShortCircuitXmlType || 
							branch.getValue() instanceof XfrShortCircuitXmlType ||
							branch.getValue() instanceof PSXfrShortCircuitXmlType) {
						AcscBranch acscBranch = CoreObjectFactory.createAcscBranch();
						BranchXmlType acscBraXml = (BranchXmlType)branch.getValue();
						// the branch is added into acscNet in the mapAclfBranchData() method
						aclfNetMapper.mapAclfBranchData(branch.getValue(), acscBranch, acscFaultNet);
						setAcscBranchData(acscBraXml, acscBranch);
					}
					else {
						ipssLogger.severe( "Error: only acsc<Branch> could be used for SC study");
						noError = false;
					}
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
		simuCtx.getAcscNet().setOriginalDataFormat(ODMHelper.map(ofmt));		
		return noError;
	}

	/**
	 * Map the network info only
	 * 
	 * @param xmlNet
	 * @return
	 */
	public void mapAcscNetworkData(AcscNetwork net, ShortCircuitNetXmlType xmlNet) throws InterpssException {
		new ODMAclfNetMapper().mapAclfNetworkData(net, xmlNet);
		net.setPositiveSeqDataOnly(xmlNet.isPositiveSeqDataOnly());		
		net.setLfDataLoaded(xmlNet.isHasLoadflowData());
		net.setScDataLoaded(true);	
	}	

	/**
	 * Set SC bus info only
	 * 
	 * @param acscBusXml
	 * @param acscBus
	 */
	public void setAcscBusData(ShortCircuitBusXmlType acscBusXml, AcscBus acscBus) throws InterpssException {
		// acscBusXml.getScCode() is optional
		if (acscBusXml.getScCode() == null) {
			// we check if acscGenData is defined
			//if (acscBusXml.getGenData() != null && acscBusXml.getGenData().getEquivGen().getValue().getCode()!=LFGenCodeEnumType.NONE_GEN) 
			// we do not assume any Lf info. The gen could be defined as a none-gen for Lf, yet a contributing gen for SC
			
			if (acscBusXml.getGenData() != null && acscBusXml.getGenData().getEquivGen() != null) 
				acscBusXml.setScCode(ShortCircuitBusEnumType.CONTRIBUTING);
			else
				acscBusXml.setScCode(ShortCircuitBusEnumType.NON_CONTRIBUTING);
		}
		
		if (acscBusXml.getScCode() == ShortCircuitBusEnumType.CONTRIBUTING) 
			setContributeBusInfo(acscBusXml, acscBus);
		else  // non-contributing
			setNonContributeBusFormInfo(acscBus);
		
		if (acscBusXml.getLoadData() != null && acscBusXml.getLoadData().getEquivLoad() != null)
			setBusLoadEquivShuntY(acscBusXml, acscBus);
		
		if (acscBusXml.getSwithedShuntLoadZeroY() != null) {
			YXmlType y = acscBusXml.getSwithedShuntLoadZeroY();
			acscBus.setScSwitchedShuntY0(new Complex(y.getRe(), y.getIm()));
		}
	}

	private void setNonContributeBusFormInfo(AcscBus acscBus) {
		acscBus.setScCode(BusScCode.NON_CONTRI);
		acscBus.setScGenZ(NumericConstant.LargeBusZ, SequenceCode.POSITIVE);
		acscBus.setScGenZ(NumericConstant.LargeBusZ, SequenceCode.NEGATIVE);
		acscBus.setScGenZ(NumericConstant.LargeBusZ, SequenceCode.ZERO);
		acscBus.getGrounding().setCode(BusGroundCode.UNGROUNDED);
		acscBus.getGrounding().setZ(NumericConstant.LargeBusZ);
	}

	private void setContributeBusInfo(ShortCircuitBusXmlType busDataXml, AcscBus acscBus) {
		acscBus.setScCode(BusScCode.CONTRIBUTE);
		// at this point it is assumed that contribute generators have been consolidated to the 
		// acscEquivGen. The consolidation logic is implemented in AcscParserHelper.createBusScEquivGenData()
		ShortCircuitGenDataXmlType scGenData = (ShortCircuitGenDataXmlType)busDataXml.getGenData().getEquivGen().getValue();
		setBusScZ(acscBus, acscBus.getNetwork().getBaseKva(), 
					scGenData.getPotiveZ(),
					scGenData.getNegativeZ(),
					scGenData.getZeroZ());
		if(scGenData.getGrounding()==null){//no grounding provided, supposed to be ungrounded
			acscBus.getGrounding().setCode(BusGroundCode.UNGROUNDED);
		}
		else
			setBusScZg(acscBus, acscBus.getBaseVoltage(), acscBus.getNetwork().getBaseKva(), 
					scGenData.getGrounding());
	}

	private void setBusLoadEquivShuntY(ShortCircuitBusXmlType acscBusXml, AcscBus acscBus) {
		// at this point we assume that acscContributeLoadList has been consolidated to
		// the acscEquivLoad. The consolidation logic is implemented in AcscParserHelper.createBusScEquivLoadData()
		
		// we should not check condition here, since by arriving here acscLoadData should be of type ShortCircuitLoadDataXmlType 
		//if(acscBusXml.getLoadData().getEquivLoad().getValue() instanceof ShortCircuitLoadDataXmlType){
			
		ShortCircuitLoadDataXmlType acscLoadData = (ShortCircuitLoadDataXmlType)acscBusXml.getLoadData().getEquivLoad().getValue();
		
		// 1) positive sequence 
		if(acscBus.isConstPLoad()||acscBus.isConstILoad()){	
			/*
			 * Use unit voltage vmag=1.0 to initialize the equivalent shuntY
			 * 
			 * For load flow-based short circuit analysis, 
			 *  equivY_actual = equivY_0/v^2   for Constant Power load
			 *                = equivY_0/v    for Constant current load
			 * 
			 */
			Complex eqivShuntY1= acscBus.getLoad().conjugate();
			acscBus.setScLoadShuntY1(eqivShuntY1);
		}
		else if(acscBus.isFunctionLoad()){
			try {
				throw new UnsupportedDataTypeException("ZIP function load is not supported for converting to positive sequence shunt load");
			} catch (UnsupportedDataTypeException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		//2) Negative part
		  //2.1) if sequence data provided, it represents all loads connected to the bus
		
		
		if(acscLoadData.getShuntLoadNegativeY()!=null){
			YXmlType y2 = acscLoadData.getShuntLoadNegativeY();
			UnitType unit = ToYUnit.f(y2.getUnit());
			Complex ypu = UnitHelper.yConversion(new Complex(y2.getRe(), y2.getIm()),
					acscBus.getBaseVoltage(), acscBus.getNetwork().getBaseKva(), unit, UnitType.PU);
		    acscBus.setScLoadShuntY2(ypu);
		}
		//1.2) else, shuntY2 = shuntY1 for the constant MVA and/or current part.
		else{
			if(acscBus.isConstPLoad()||acscBus.isConstILoad()){	
				/*
				 * Use unit voltage vmag=1.0 to initialize the equivalent shuntY
				 * 
				 * For load flow-based short circuit analysis, 
				 *  equivY_actual = equivY_0/v^2   for Constant Power load
				 *                = equivY_0/v    for Constant current load
				 * 
				 */
				Complex eqivShuntY2= acscBus.getLoad().conjugate();
				acscBus.setScLoadShuntY2(eqivShuntY2);
			}
			else if(acscBus.isFunctionLoad()){
				try {
					throw new UnsupportedDataTypeException("ZIP function load is not supported for converting to negative sequence shunt load");
				} catch (UnsupportedDataTypeException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
		}
		
		//2) Zero sequence part
		if(acscLoadData.getShuntLoadZeroY()!=null){
			YXmlType y0 = acscLoadData.getShuntLoadNegativeY();
			UnitType unit = ToYUnit.f(y0.getUnit());
			Complex ypu = UnitHelper.yConversion(new Complex(y0.getRe(), y0.getIm()),
					acscBus.getBaseVoltage(), acscBus.getNetwork().getBaseKva(), unit, UnitType.PU);
		    acscBus.setScLoadShuntY0(ypu);
		}
		// If not provided ,then the load is open from the zero sequence network
		//}
	}

	private void setBusScZ(AcscBus bus, double baseKVA, 
			ZXmlType z1, ZXmlType z2, ZXmlType z0) {
		UnitType zUnit = ToZUnit.f(z1.getUnit());
		bus.setScGenZ(new Complex(z1.getRe(), z1.getIm()), SequenceCode.POSITIVE, zUnit);
		bus.setScGenZ(new Complex(z2.getRe(), z2.getIm()), SequenceCode.NEGATIVE, zUnit);
		bus.setScGenZ(new Complex(z0.getRe(), z0.getIm()), SequenceCode.ZERO, zUnit);
	}

	private void setBusScZg(AcscBus bus, double baseV, double baseKVA, GroundingXmlType g) {
		bus.getGrounding().setCode(ODMHelper.toBusGroundCode(g.getGroundingConnection()));
		ZXmlType z = g.getGroundingZ();
		if(z != null){
			UnitType zgUnit = ToZUnit.f(z.getUnit());			
			bus.getGrounding().setZ(new Complex(z.getRe(), z.getIm()), zgUnit, baseV, baseKVA);
		}
	}

	/**
	 * Set SC branch info only
	 * 
	 * @param acscBraXml
	 * @param acscBra
	 * @param msg
	 * @return
	 */
	public void setAcscBranchData(BranchXmlType acscBraXml, AcscBranch acscBra) {

		if (acscBraXml instanceof LineShortCircuitXmlType) { // line branch
			setAcscLineFormInfo((LineShortCircuitXmlType)acscBraXml, acscBra);
		} 
		else if ( acscBraXml instanceof XfrShortCircuitXmlType ||
				acscBraXml instanceof PSXfrShortCircuitXmlType) { // xfr or psxfr branch
			setAcscXfrFormInfo((XfrShortCircuitXmlType)acscBraXml, acscBra);
		}
	}

	private void setAcscLineFormInfo(LineShortCircuitXmlType braXml, AcscBranch acscBra) {
		double baseV = acscBra.getFromAclfBus().getBaseVoltage();
		AcscLine line = AcscLineAptr.f(acscBra);
		ZXmlType z0 = braXml.getZ0();
		if (z0 != null)
			line.setZ0(new Complex(z0.getRe(), z0.getIm()),	ToZUnit.f(z0.getUnit()), baseV);
		YXmlType y0 = braXml.getY0Shunt();
		if (y0 != null)
			line.setHB0(0.5*y0.getIm(), ToYUnit.f(y0.getUnit()), baseV);
	}

	// for SC, Xfr and PSXfr behave the same
	private void setAcscXfrFormInfo(XfrShortCircuitXmlType braXml, AcscBranch acscBra) {
		double baseV = acscBra.getFromAclfBus().getBaseVoltage() > acscBra
		.getToAclfBus().getBaseVoltage() ? acscBra.getFromAclfBus()
				.getBaseVoltage() : acscBra.getToAclfBus().getBaseVoltage();
				AcscXformer xfr = AcscXfrAptr.f(acscBra);
				ZXmlType z0 = braXml.getZ0();
				if (z0 != null)
					xfr.setZ0(new Complex(z0.getRe(), z0.getIm()), ToZUnit.f(z0.getUnit()), baseV);

				XformerConnectionXmlType connect = braXml.getFromSideConnection();
				if(connect != null){
					XfrConnectCode conCode=calXfrConnectCode(connect);
					acscBra.setXfrFromConnectCode(conCode);
					if(connect.getGrounding() != null){
						ZXmlType z = connect.getGrounding().getGroundingZ();
						if (z != null) 
							xfr.setFromConnectGroundZ(calXfrConnectCode(connect), new Complex(z.getRe(), z.getIm()),
									ToZUnit.f(z.getUnit()));
					}
				}				

				connect = braXml.getToSideConnection();
				if(connect != null){
					XfrConnectCode conCode=calXfrConnectCode(connect);
					acscBra.setXfrToConnectCode(conCode);
					if(connect.getGrounding() != null){
						ZXmlType z = connect.getGrounding().getGroundingZ();
						if (z != null) 
							xfr.setFromConnectGroundZ(calXfrConnectCode(connect), new Complex(z.getRe(), z.getIm()),
									ToZUnit.f(z.getUnit()));
					}
				}	
	}

	private XfrConnectCode calXfrConnectCode(XformerConnectionXmlType connect) {
		// connectCode : [Delta | Wye]
		// groundCode : [SolidGrounded | ZGrounded | Ungrounded ]
		if (connect.getXfrConnection() == XformrtConnectionEnumType.DELTA)
			return XfrConnectCode.DELTA;
		else {  // Wye connection
			if (connect.getGrounding().getGroundingConnection() == GroundingEnumType.SOLID_GROUNDED)
				return XfrConnectCode.WYE_SOLID_GROUNDED;
			else if (connect.getGrounding().getGroundingConnection() == GroundingEnumType.Z_GROUNDED)
				return XfrConnectCode.WYE_ZGROUNDED;
			else 
				return XfrConnectCode.WYE_UNGROUNDED;
		}
	}
}