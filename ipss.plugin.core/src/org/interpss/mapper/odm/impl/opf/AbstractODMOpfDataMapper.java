/*
 * @(#)AbstractODMOpfDataMapper.java   
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

package org.interpss.mapper.odm.impl.opf;

import static com.interpss.common.util.IpssLogger.ipssLogger;
import static org.interpss.mapper.odm.ODMUnitHelper.ToActivePowerUnit;
import static org.interpss.mapper.odm.ODMUnitHelper.ToReactivePowerUnit;
import static org.interpss.mapper.odm.ODMUnitHelper.ToApparentPowerUnit;
import static org.interpss.mapper.odm.ODMUnitHelper.ToVoltageUnit;

import javax.xml.bind.JAXBElement;

import org.ieee.odm.model.opf.OpfModelParser;
import org.ieee.odm.schema.ActivePowerLimitXmlType;
import org.ieee.odm.schema.ActivePowerPriceEnumType;
import org.ieee.odm.schema.ActivePowerUnitType;
import org.ieee.odm.schema.AnalysisCategoryEnumType;
import org.ieee.odm.schema.BaseBranchXmlType;
import org.ieee.odm.schema.BaseOpfNetworkXmlType;
import org.ieee.odm.schema.BranchXmlType;
import org.ieee.odm.schema.BusXmlType;
import org.ieee.odm.schema.ConstraintsXmlType;
import org.ieee.odm.schema.CostModelEnumType;
import org.ieee.odm.schema.IncCostXmlType;
import org.ieee.odm.schema.LinCoeffXmlType;
import org.ieee.odm.schema.LoadflowBusXmlType;
import org.ieee.odm.schema.NetworkCategoryEnumType;
import org.ieee.odm.schema.OpfDclfGenBusXmlType;
import org.ieee.odm.schema.OpfDclfNetworkXmlType;
import org.ieee.odm.schema.OpfGenBusXmlType;
import org.ieee.odm.schema.OpfGenOperatingModeEnumType;
import org.ieee.odm.schema.OpfNetworkEnumType;
import org.ieee.odm.schema.OpfNetworkXmlType;
import org.ieee.odm.schema.OriginalDataFormatEnumType;
import org.ieee.odm.schema.PieceWiseLinearModelXmlType;
import org.ieee.odm.schema.QuadraticModelXmlType;
import org.ieee.odm.schema.ReactivePowerLimitXmlType;
import org.ieee.odm.schema.ReactivePowerUnitType;
import org.ieee.odm.schema.SqrCoeffXmlType;
import org.ieee.odm.schema.StairStepXmlType;
import org.ieee.odm.schema.VoltageLimitXmlType;
import org.ieee.odm.schema.VoltageUnitType;
import org.ieee.odm.schema.XformerZTableXmlType;
import org.interpss.mapper.odm.ODMAclfNetMapper;
import org.interpss.mapper.odm.ODMHelper;
import org.interpss.mapper.odm.impl.aclf.AbstractODMAclfParserMapper;
import org.interpss.mapper.odm.impl.aclf.AclfBusDataHelper;
import org.interpss.numeric.datatype.LimitType;
import org.interpss.numeric.datatype.Point;
import org.interpss.numeric.datatype.Unit.UnitType;

import com.interpss.OpfObjectFactory;
import com.interpss.common.datatype.UnitHelper;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.common.curve.CommonCurveFactory;
import com.interpss.core.common.curve.NumericCurveModel;
import com.interpss.core.common.curve.PieceWiseCurve;
import com.interpss.core.common.curve.QuadraticCurve;
import com.interpss.core.common.curve.impl.PieceWiseCurveImpl;
import com.interpss.opf.BaseOpfNetwork;
import com.interpss.opf.Constraint;
import com.interpss.opf.IncrementalCost;
import com.interpss.opf.OpfBranch;
import com.interpss.opf.OpfBus;
import com.interpss.opf.OpfFactory;
import com.interpss.opf.OpfGenBus;
import com.interpss.opf.OpfGenOperatingMode;
import com.interpss.opf.OpfNetwork;
import com.interpss.opf.dclf.DclfOpfBranch;
import com.interpss.opf.dclf.DclfOpfBus;
import com.interpss.opf.dclf.DclfOpfGenBus;
import com.interpss.opf.dclf.DclfOpfNetwork;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

/**
 * abstract mapper implementation to map ODM to InterPSS object model for Opf
 * 
 * @author mzhou
 *
 * @param <Tfrom>
 */
public abstract class AbstractODMOpfDataMapper <Tfrom> extends AbstractODMAclfParserMapper<Tfrom> {
	/**
	 * constructor
	 * 
	 */
	public AbstractODMOpfDataMapper() {
	}
	
	/**
	 * transfer info stored in the parser object into simuCtx object
	 * 
	 * @param p an ODM parser object, representing an ODM xml file
	 * @param simuCtx
	 * @return
	 */
	@Override public boolean map2Model(Tfrom p, SimuContext simuCtx) {
		boolean noError = true;
		
		OpfModelParser parser = (OpfModelParser) p;
		
		if (parser.getStudyCase().getNetworkCategory() == NetworkCategoryEnumType.TRANSMISSION
				&& parser.getStudyCase().getAnalysisCategory() == AnalysisCategoryEnumType.OPF) {			
			BaseOpfNetworkXmlType xmlNet = parser.getBaseOpfNet();
			simuCtx.setNetType(SimuCtxType.OPF_NET);
			
			//XformerZTableXmlType xfrZTable = xmlNet.getXfrZTable();
			
			try {
				BaseOpfNetwork opfNet = null;
				if (xmlNet.getOpfNetType() == OpfNetworkEnumType.SIMPLE_DCLF) 
					opfNet = mapDclfOpfNetData((OpfDclfNetworkXmlType)xmlNet);
				else 
					opfNet = mapOpfNetData((OpfNetworkXmlType)xmlNet);
				simuCtx.setOpfNet(opfNet);

				
				AclfBusDataHelper busHelper = new AclfBusDataHelper(opfNet);
				ODMAclfNetMapper aclfNetMapper = new ODMAclfNetMapper();
				for (JAXBElement<? extends BusXmlType> bus : xmlNet.getBusList().getBus()) {
					if (bus.getValue() instanceof OpfDclfGenBusXmlType ||
							bus.getValue() instanceof OpfGenBusXmlType) {
						if (xmlNet.getOpfNetType() == OpfNetworkEnumType.SIMPLE_DCLF) {
							OpfDclfGenBusXmlType opfDclfGen = (OpfDclfGenBusXmlType) bus.getValue();
							mapDclfOpfGenBusData(opfDclfGen, (DclfOpfNetwork)opfNet);
						}
						else {
							OpfGenBusXmlType opfGen = (OpfGenBusXmlType) bus.getValue();
							mapOpfGenBusData(opfGen, (OpfNetwork)opfNet);
						}
					} 
					else {
						LoadflowBusXmlType busRec = (LoadflowBusXmlType) bus.getValue();
						if (xmlNet.getOpfNetType() == OpfNetworkEnumType.SIMPLE_DCLF) {
							DclfOpfBus opfDclfBus = OpfObjectFactory.createDclfOpfBus(busRec.getId(), (DclfOpfNetwork)opfNet);
							aclfNetMapper.mapAclfBusData(busRec, opfDclfBus, opfNet, busHelper);
						}
						else {
							OpfBus opfBus = OpfObjectFactory.createOpfBus(busRec.getId(), (OpfNetwork)opfNet);
							aclfNetMapper.mapAclfBusData(busRec, opfBus, opfNet, busHelper);
						}
					}
				}

				for (JAXBElement<? extends BaseBranchXmlType> b : xmlNet.getBranchList().getBranch()) {
					if (xmlNet.getOpfNetType() == OpfNetworkEnumType.SIMPLE_DCLF) {
						DclfOpfBranch opfDclfBranch = OpfObjectFactory.createDclfOpfBranch();
						aclfNetMapper.mapAclfBranchData(b.getValue(), opfDclfBranch, (DclfOpfNetwork)opfNet);
					}
					else {
						OpfBranch opfBranch = OpfObjectFactory.createOpfBranch();
						aclfNetMapper.mapAclfBranchData(b.getValue(), opfBranch, (OpfNetwork)opfNet);
						// map MW rating
						BranchXmlType branchXml = (BranchXmlType)b.getValue();
						if(branchXml.getRatingLimit()!=null){
							if (branchXml.getRatingLimit().getMw()!=null){
								OpfBranchDataHelper helper = new OpfBranchDataHelper(opfBranch, b.getValue());
								
								helper.setMwRating();
							}
						}
					}
				}
			} catch (InterpssException e) {
				e.printStackTrace();
				ipssLogger.severe(e.toString());
				noError = false;
			}
		} 
		else {
			ipssLogger.severe( "Error: wrong Transmission NetworkType and/or ApplicationType");
			noError = false;
		}
		
		if (parser.getStudyCase().getContentInfo() != null) {
			OriginalDataFormatEnumType ofmt = parser.getStudyCase().getContentInfo().getOriginalDataFormat();
			simuCtx.getNetwork().setOriginalDataFormat(ODMHelper.map(ofmt));		
		} 
		else {
			ipssLogger.severe( "Error: StudyCase.ContentInfo were not entered");
			noError = false;
		}
		return noError;
	}
	
	/*
	 *    OPF model mapper
	 *    ================
	 */
	private OpfNetwork mapOpfNetData(OpfNetworkXmlType xmlNet) throws InterpssException {
		OpfNetwork opfNet = OpfObjectFactory.createOpfNetwork();
		new ODMAclfNetMapper().mapAclfNetworkData(opfNet, xmlNet);
		
		// mapping details
		opfNet.setAnglePenaltyFactor(xmlNet.getAnglePenaltyFactor());	
		
		return opfNet;
	}

	/**
	 * map OPF gen bus data
	 * 
	 * @param busRec
	 * @param net
	 * @return
	 * @throws InterpssException
	 */
	public OpfGenBus mapOpfGenBusData(OpfGenBusXmlType busRec, OpfNetwork net) throws InterpssException {
		OpfGenBus opfGenBus = OpfObjectFactory.createOpfGenBus(busRec.getId(), net);
		mapBaseBusData(busRec, opfGenBus, net);

		AclfBusDataHelper helper = new AclfBusDataHelper(net);
		helper.setAclfBus(opfGenBus);
		helper.setAclfBusData(busRec);
		

		OpfGenOperatingModeEnumType genMode = busRec.getOperatingMode();
		if (genMode.equals(OpfGenOperatingModeEnumType.PV_GENERATOR)){
			opfGenBus.setOperatingMode(OpfGenOperatingMode.PV_GENERATOR);
			
		}else if(genMode.equals(OpfGenOperatingModeEnumType.PUMPING)) {
			opfGenBus.setOperatingMode(OpfGenOperatingMode.PUMPING);
			
		}else if(genMode.equals(OpfGenOperatingModeEnumType.PQ_GENERATOR)) {
			opfGenBus.setOperatingMode(OpfGenOperatingMode.PQ_GENERATOR);
		}else {
			// synchronized condensor
			opfGenBus.setOperatingMode(OpfGenOperatingMode.SYCHRONOUS_COMPENSATOR);
		}
		
		// set gen incremental cost model
		IncCostXmlType incCostRec = busRec.getIncCost();
		CostModelEnumType costModelRec = incCostRec.getCostModel();
		IncrementalCost inc = OpfFactory.eINSTANCE.createIncrementalCost();
		if (costModelRec.equals(CostModelEnumType.PIECE_WISE_LINEAR_MODEL)){
			inc.setCostModel(NumericCurveModel.PIECE_WISE);
			if (busRec.getIncCost().getPieceWiseLinearModel()!=null){
				PieceWiseLinearModelXmlType pw = busRec.getIncCost().getPieceWiseLinearModel();
				boolean isConvex = checkConvex(pw);
				if(isConvex == false){
					ipssLogger.severe("Gen cost function must be in ascending order. Please check gen cost function at bus: "
							+ busRec.getNumber());
				}
				PieceWiseCurve pwIpss = CommonCurveFactory.eINSTANCE.createPieceWiseCurve();
				for (StairStepXmlType stair : pw.getStairStep()){
					double price = stair.getPrice().getValue(); // price in $/MWh
					double mw = stair.getAmount().getValue();  // amount in MW
					Point costPoint = new Point();
					// point in format of: (mw, price),  converted to pu
					double baseKva = net.getBaseKva(); // in KW
					baseKva = baseKva/1000; // in MW
					
					costPoint.x = mw/baseKva;
					costPoint.y = price*baseKva;		
					pwIpss.getPoints().add(costPoint);					
				}
				
				inc.setPieceWiseCurve(pwIpss);				
				
			}else{
				ipssLogger.severe("Can not find a piece-wise cost model for bus: "+ opfGenBus.getId());
			}

		}else {
			inc.setCostModel(NumericCurveModel.QUADRATIC);
			if (busRec.getIncCost().getQuadraticModel()!=null){
				QuadraticModelXmlType quaXml = busRec.getIncCost().getQuadraticModel();
				QuadraticCurve quaIpss = CommonCurveFactory.eINSTANCE.createQuadraticCurve();
				
				// quadratic function in the form of: C = Ax^2+Bx+C;
				// convert to pu: squCoeff: $/(mw*mw)
				//                linCoeff: $/mw	
				
				SqrCoeffXmlType sqrCoeffXml=   quaXml.getSqrCoeff();
				ActivePowerPriceEnumType unit = sqrCoeffXml.getUnit();				
				double sqrCoeff = sqrCoeffXml.getValue();
				double baseKva = net.getBaseKva(); // in KW
				//baseKva = baseKva/1000; // in MW
				if(unit.equals(ActivePowerPriceEnumType.DOLLAR_PER_KW_SQUARE)){
					sqrCoeff = sqrCoeff*baseKva*baseKva;
				}else if(unit.equals(ActivePowerPriceEnumType.DOLLAR_PER_MW_SQUARE)){
					sqrCoeff = sqrCoeff*baseKva*baseKva/1000/1000;
				}				
				
				LinCoeffXmlType linCoeffXml = quaXml.getLinCoeff();
				unit = linCoeffXml.getUnit();
				double linCoeff = linCoeffXml.getValue();
				if(unit.equals(ActivePowerPriceEnumType.DOLLAR_PER_KW)){
					linCoeff = linCoeff*baseKva;
				}else if(unit.equals(ActivePowerPriceEnumType.DOLLAR_PER_MW)){
					linCoeff = linCoeff*baseKva/1000;
				}
				
				
				double constCoeff = quaXml.getConstCoeff();
							
				quaIpss.setA(sqrCoeff);
				quaIpss.setB(linCoeff);
				quaIpss.setC(constCoeff);				
				inc.setQuadraticCurve(quaIpss);
			}else{
				ipssLogger.severe("Can not find a quadratic cost model for bus: "+ opfGenBus.getId());
			}			
		}
		opfGenBus.setIncCost(inc);
		
		// set constraints
		if(busRec.getConstraints()!=null){
			ConstraintsXmlType ctrtXml = busRec.getConstraints();		
			Constraint ctrtIpss = OpfFactory.eINSTANCE.createConstraint();			
			
			double baseKva = net.getBaseKva();
			double factor = net.getBaseKva()*0.001;
			if(ctrtXml.getActivePowerLimit()!=null){			
				ActivePowerLimitXmlType pLmt = ctrtXml.getActivePowerLimit();			
				double pmax = pLmt.getMax();
				double pmin = pLmt.getMin();
				ActivePowerUnitType unit= pLmt.getUnit();
				UnitType ipssUnit = ToActivePowerUnit.f(unit);
				// convert all to pu				
				LimitType limit = UnitHelper.pConversion(new LimitType(pmax, pmin), baseKva, ipssUnit, UnitType.PU);
				ctrtIpss.setPLimit( limit);			
			}else if(ctrtXml.getReactivePowerLimit()!=null){
				ReactivePowerLimitXmlType qLmt = ctrtXml.getReactivePowerLimit();
				double qmax = qLmt.getMax();
				double qmin = qLmt.getMin();
				ReactivePowerUnitType unit = qLmt.getUnit();
				UnitType ipssUnit = ToReactivePowerUnit.f(unit);
				LimitType limit = UnitHelper.pConversion(new LimitType(qmax, qmin), baseKva, ipssUnit, UnitType.PU);
				ctrtIpss.setQLimit( limit);					
			}else if(ctrtXml.getVolLimit()!=null){
				VoltageLimitXmlType vLmt = ctrtXml.getVolLimit();
				double vmax = vLmt.getMax();
				double vmin = vLmt.getMin();
				VoltageUnitType unit = vLmt.getUnit();
				UnitType ipssUnit = ToVoltageUnit.f(unit);
				LimitType limit = UnitHelper.pConversion(new LimitType(vmax, vmin), baseKva, ipssUnit, UnitType.PU);
				ctrtIpss.setVLimit(limit);						
			}			
			opfGenBus.setConstraints(ctrtIpss);
			
		}
		return opfGenBus;
	}
	
	/*
	 *    DCLF OPF model mapper
	 *    =====================
	 */
	
	private DclfOpfNetwork mapDclfOpfNetData(OpfDclfNetworkXmlType xmlNet) throws InterpssException {
		DclfOpfNetwork opfNet = OpfObjectFactory.createDclfOpfNetwork();
		new ODMAclfNetMapper().mapAclfNetworkData(opfNet, xmlNet);
		opfNet.setAnglePenaltyFactor(xmlNet.getAnglePenaltyFactor());	
		return opfNet;
	}

	/**
	 * Map a bus record
	 * 
	 * @param busRec
	 * @param adjNet
	 * @return
	 * @throws Exception
	 */
	public DclfOpfGenBus mapDclfOpfGenBusData(OpfDclfGenBusXmlType busRec, DclfOpfNetwork net) throws InterpssException {
		DclfOpfGenBus opfGenBus = OpfObjectFactory.createDclfOpfGenBus(busRec.getId(), net);
		mapBaseBusData(busRec, opfGenBus, net);

		AclfBusDataHelper helper = new AclfBusDataHelper(net);
		helper.setAclfBus(opfGenBus);
		helper.setAclfBusData(busRec);
		
		/*
    		<pss:coeffA>37.8896</pss:coeffA>
    		<pss:coeffB>0.01433</pss:coeffB>
    		<pss:capacityLimit max="0.2" min="0.05" unit="PU"></pss:capacityLimit>
    		<pss:fixedCost>118.821</pss:fixedCost>  
    	*/
		opfGenBus.setCoeffA(busRec.getCoeffA());
		opfGenBus.setCoeffB(busRec.getCoeffB());
		opfGenBus.setFixedCost(busRec.getFixedCost());
		String unit=busRec.getCapacityLimit().getUnit().value();
		if(unit.equalsIgnoreCase("PU")) {// model mappering consistence, all are converted to MVA unit;
			double factor=net.getBaseKva()*0.001;
		    opfGenBus.setCapacityLimit(new LimitType(busRec.getCapacityLimit().getMax()*factor, 
				              busRec.getCapacityLimit().getMin()*factor));
		}
		else {   //the default unit is MVA
			opfGenBus.setCapacityLimit(new LimitType(busRec.getCapacityLimit().getMax(), busRec.getCapacityLimit().getMin()));
		}
		return opfGenBus;
	}
	
	private boolean checkConvex(PieceWiseLinearModelXmlType pw){
		boolean isConvex = true;
		int n = pw.getStairStep().size();
		
		if(n>2){			
			for (int i =0; i<n-1; i++){
				double mw0 = pw.getStairStep().get(i).getAmount().getValue();
				double price0 = pw.getStairStep().get(i).getPrice().getValue();
				double mw1 = pw.getStairStep().get(i+1).getAmount().getValue();
				double price1 = pw.getStairStep().get(i+1).getPrice().getValue();
				if(mw1 < mw0 || price1 < price0){
					return false;
				}
			}
		}		
		
		return isConvex;
	}
}