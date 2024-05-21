/*
 * @(#)ODMDcSysNetMapper.java   
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
 * @Date 11/15/2010
 * 
 *   Revision History
 *   ================
 *
 */

package org.interpss.mapper.odm.impl.dcsys;

import static com.interpss.common.util.IpssLogger.ipssLogger;
import static org.interpss.mapper.odm.ODMUnitHelper.toActivePowerUnit;
import static org.interpss.mapper.odm.ODMUnitHelper.toVoltageUnit;
import static org.interpss.mapper.odm.ODMUnitHelper.toZUnit;

import javax.xml.bind.JAXBElement;

import org.eclipse.emf.common.util.EList;
import org.ieee.odm.schema.ActivePowerUnitType;
import org.ieee.odm.schema.BaseBranchXmlType;
import org.ieee.odm.schema.BasePVModelXmlType;
import org.ieee.odm.schema.BusXmlType;
import org.ieee.odm.schema.DcBranchXmlType;
import org.ieee.odm.schema.DcBusCodeEnumType;
import org.ieee.odm.schema.DcBusXmlType;
import org.ieee.odm.schema.DcFeederXmlType;
import org.ieee.odm.schema.DcNetworkXmlType;
import org.ieee.odm.schema.FactorUnitType;
import org.ieee.odm.schema.InverterLossEqnXmlType;
import org.ieee.odm.schema.InverterLossParamXmlType;
import org.ieee.odm.schema.LengthUnitType;
import org.ieee.odm.schema.PVModelIVCurveXmlType;
import org.ieee.odm.schema.PVModelIVFunctionXmlType;
import org.ieee.odm.schema.PVModelPointXmlType;
import org.ieee.odm.schema.PVModuleDataEnumType;
import org.ieee.odm.schema.PVModuleItemXmlType;
import org.ieee.odm.schema.VoltageUnitType;
import org.interpss.mapper.odm.AbstractODMNetDataMapper;
import org.interpss.numeric.NumericConstant;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.datatype.Vector_xy;

import com.interpss.common.exp.InterpssException;
import com.interpss.dc.DcBranch;
import com.interpss.dc.DcBusCode;
import com.interpss.dc.DcNetwork;
import com.interpss.dc.DcSysObjectFactory;
import com.interpss.dc.common.IpssDcSysException;
import com.interpss.dc.pv.PVDcBus;
import com.interpss.dc.pv.PVDcNetwork;
import com.interpss.dc.pv.inverter.DcAcInverter;
import com.interpss.dc.pv.inverter.InverterLossDataType;
import com.interpss.dc.pv.inverter.LossEqnParamItem;
import com.interpss.dc.pv.module.PVModel;
import com.interpss.dc.pv.module.PVModelIVCurveType;
import com.interpss.dc.pv.module.PVModelIVFunction;
import com.interpss.dc.pv.module.PVModule;
import com.interpss.dc.pv.module.PVModuleItem;
import com.interpss.dc.pv.module.impl.PVModelHelper;

public abstract class AbstractODMDcSysNetMapper<T> extends AbstractODMNetDataMapper<T, PVDcNetwork> {
	public AbstractODMDcSysNetMapper() {
	}
	
	/**
	 * transfer info stored in the parser object into simuCtx object
	 * 
	 * @param p an ODM parser object, representing an ODM xml file
	 * @return DcNetwork object
	 */
	@Override
	public PVDcNetwork map2Model(T p) throws IpssDcSysException {
//		if (!License.getInstance().isValid()) {
//			throw new IpssDcSysException("Invalid license");
//		}
		
		PVDcNetwork dcNet = DcSysObjectFactory.createPVDcNetwork();
		if (map2Model(p, dcNet))
			return dcNet;
		else
			throw new IpssDcSysException("Error - map ODM model to create DcNetwork object");
	}
	
	/**
	 * transfer info stored in the parser object into simuCtx object
	 * 
	 * @param p an ODM parser object, representing an ODM xml file
	 * @param dcNet
	 * @return
	 */
	@Override
	public boolean map2Model(T from, PVDcNetwork dcNet) {
		DcNetworkXmlType xmlNet = (DcNetworkXmlType)from;
		boolean noError = true;
		
		try {
			mapDcNetworkData(dcNet, xmlNet);

			for (JAXBElement<? extends BusXmlType> bus : xmlNet.getBusList().getBus()) {
				DcBusXmlType busRec = (DcBusXmlType) bus.getValue();
				mapDcBusData(busRec, dcNet);
			}

			for (JAXBElement<? extends BaseBranchXmlType> b : xmlNet.getBranchList().getBranch()) {
				DcBranchXmlType braRec = (DcBranchXmlType) b.getValue();
				mapDcBranchData(braRec, dcNet);
			}
			
			/*
			 * a child dc sys net cannot contain any child network 
			 */
		} catch (IpssDcSysException e) {
			ipssLogger.severe(e.toString());
			noError = false;
		}
		
		return noError;
	}
	
	/**
	 * Map the network info only
	 * 
	 * @param dcNet
	 * @param xmlNet
	 * @return
	 */
	private void mapDcNetworkData(PVDcNetwork dcNet, DcNetworkXmlType xmlNet) throws IpssDcSysException {
		super.mapNetworkData(dcNet, xmlNet);
		
		if (xmlNet.getRatedVoltage().getUnit() != VoltageUnitType.VOLT)
			throw new IpssDcSysException("Wrong dcNet.RatedVoltage unit type. VOLT must be used");
		dcNet.setRatedVoltage(xmlNet.getRatedVoltage().getValue());
		
		if (xmlNet.getPvModelList() != null && xmlNet.getPvModelList().getPvModel().size() > 0) {
			for ( JAXBElement<? extends BasePVModelXmlType> xml : xmlNet.getPvModelList().getPvModel()) {
				BasePVModelXmlType xmlModel = xml.getValue();
				PVModelIVCurveType type = xmlModel.getDataType() == PVModuleDataEnumType.FUNCTION?
						PVModelIVCurveType.FUNCTION : PVModelIVCurveType.DATA_POINTS;
				PVModel model = DcSysObjectFactory.createPVModel(xmlModel.getId(), type, dcNet);
				if (type == PVModelIVCurveType.FUNCTION) {
					PVModelIVFunctionXmlType ivFuncModel = (PVModelIVFunctionXmlType)xmlModel;
					PVModelIVFunction fun = model.getIvFunction();
					fun.setSign(ivFuncModel.getSign());
					fun.setShadeFactor(ivFuncModel.getShadingFactor());
					fun.setA(ivFuncModel.getA());
					fun.setB(ivFuncModel.getB());
					fun.setC(ivFuncModel.getC());
					fun.setD(ivFuncModel.getD());
					fun.setE(ivFuncModel.getE());
					fun.setF(ivFuncModel.getF());	
					PVModelHelper helper = new PVModelHelper(model);
					fun.setVoc(helper.calVoc());
					fun.setVmaxp(helper.calVmaxp());				
				}
				else {
					PVModelIVCurveXmlType ivCurveModel = (PVModelIVCurveXmlType)xmlModel;
					EList<Vector_xy> list = model.getIvCurvePoints();
					double vPre = -1.0, iPre = 1.0e10;
					for ( PVModelPointXmlType p : ivCurveModel.getIvPoint()) {
						double v = p.getVolt(), 
						       i = p.getAmp();
						// make sure - volt in ascending order and amp in descending order;
						if (v > vPre && i < iPre) {
							vPre = v;
							iPre = i;
						}
						else
							throw new IpssDcSysException("Error: PVModel I-V curve volt in ascending order and amp in descending order");
						list.add(new Vector_xy(v, i));
					}					
				}
			}
		}
	}
	
	/**
	 * Map DcBus record
	 * 
	 * @param busRec
	 * @param dcNet
	 * @return
	 * @throws Exception
	 */
	private void mapDcBusData(DcBusXmlType busRec, PVDcNetwork dcNet) throws IpssDcSysException {
		// create DcBus object, bus.baseVoltage initialized with net.RatedVoltage
		PVDcBus bus = null;
		try {
			bus = DcSysObjectFactory.createPVDcBus(busRec.getId(), dcNet);
		} catch (InterpssException e) {
			throw new IpssDcSysException(e.toString());
		}
		
		super.mapBaseBusData(busRec, bus, dcNet);
		
		bus.setCode(busRec.getCode() == DcBusCodeEnumType.VOLTAGE_SOURCE ? DcBusCode.VOLTAGE_SOURCE :
					(busRec.getCode() == DcBusCodeEnumType.LOAD ? DcBusCode.LOAD : 
						(busRec.getCode() == DcBusCodeEnumType.POWER_SOURCE ? DcBusCode.POWER_SOURCE : 
							(busRec.getCode() == DcBusCodeEnumType.PV_MODULE ? DcBusCode.PV_MODULE : 
								(busRec.getCode() == DcBusCodeEnumType.INVERTER ? DcBusCode.INVERTER : 
									DcBusCode.CONNECTION)))));

		// voltage can be defined for all bus types
		if (busRec.getVoltage() != null)
			bus.setVoltage(busRec.getVoltage().getValue(), 
					toVoltageUnit.apply(busRec.getVoltage().getUnit()));		
		
		if (bus.getCode() == DcBusCode.POWER_SOURCE) {
			if (busRec.getPower() != null)
				bus.setSourcePower(busRec.getPower().getValue(), 
						toActivePowerUnit.apply(busRec.getPower().getUnit()));		
			else 
				throw new IpssDcSysException("For PowerSource bus type, the power field needs specified");
		}

		// load can be defined for all bus types
		if(busRec.getCode() == DcBusCodeEnumType.CONNECTION)
			bus.setLoad(0.0);		
		else if (busRec.getLoad() != null)
			bus.setLoad(busRec.getLoad().getValue(), 
					toActivePowerUnit.apply(busRec.getLoad().getUnit()));		
		
		if (bus.getCode() == DcBusCode.PV_MODULE) {
			if (busRec.getPvModule() != null) {
				PVModelIVCurveType type = busRec.getPvModule().getDataType() == PVModuleDataEnumType.FUNCTION?
					PVModelIVCurveType.FUNCTION : PVModelIVCurveType.DATA_POINTS;
				PVModule module = DcSysObjectFactory.createPVModule("PVModule@"+bus.getId(), type, bus);
				for ( PVModuleItemXmlType xmlItem : busRec.getPvModule().getPvModuleItem()) {
					PVModelIVFunctionXmlType ivFunc = (PVModelIVFunctionXmlType)(xmlItem.getPvModelRef());
					PVModel model = dcNet.getPVModel(ivFunc.getId());
					// create ModuleItem and added to the parent module
					PVModuleItem item = DcSysObjectFactory.createPVModuleItem(xmlItem.getId(), module, model);
					if (xmlItem.getShadingFactor() != null)
						item.setShadingFactor(xmlItem.getShadingFactor());
					else
						item.setShadingFactor(model.getIvFunction().getShadeFactor());
					
				}
				
				module.initData();
//				try {
//					new PVModuleHelper(module).buildPVModelIVCurve(type);	
//				} catch (InterpssException e) {
//					IpssLogger.getLogger().severe(e.toString());
//					throw new IpssDcSysException(e.toString());					
//				}
			}
			else
				throw new IpssDcSysException("For PVModel bus type, the pvModelList needs specified");
		}

		if (bus.getCode() == DcBusCode.INVERTER) {
			if (busRec.getInverter() != null) {
				InverterLossDataType type = busRec.getInverter().getLoss() != null?
						InverterLossDataType.FUNCTION : InverterLossDataType.DATA_POINTS;
				DcAcInverter inv = DcSysObjectFactory.createDcAcInverter(bus, type);
				
				// parse Paco (power rating) 
				double paco = 0.0;
				if (busRec.getInverter().getPowerRating().getUnit() == ActivePowerUnitType.W)
					paco = busRec.getInverter().getPowerRating().getValue();
				else if (busRec.getInverter().getPowerRating().getUnit() == ActivePowerUnitType.KW)
					paco = busRec.getInverter().getPowerRating().getValue() * 1000.0;
				else
					throw new IpssDcSysException("Wrong Inveter power rating unit type, use W or KW");
				inv.setPowerRatingW(paco);

				// acPFactor
				if (busRec.getInverter().getAcPFactor() != null) {
					double factor = busRec.getInverter().getAcPFactor().getUnit() == FactorUnitType.PERCENT ? 0.01 : 1.0;
					inv.setPFactor(busRec.getInverter().getAcPFactor().getValue() * factor);
				}
				else 
					inv.setPFactor(1.0);

				// parse vdcmax, idcmax
				if (busRec.getInverter().getVdcmax() != null)
					inv.setVdcmax(busRec.getInverter().getVdcmax());
				if (busRec.getInverter().getIdcmax() != null)
					inv.setIdcmax(busRec.getInverter().getIdcmax());

				// parse mppt high and low
				if (busRec.getInverter().getMpptHigh() != null)
					inv.setMpptHigh(busRec.getInverter().getMpptHigh());
				if (busRec.getInverter().getMpptLow() != null)
					inv.setMpptLow(busRec.getInverter().getMpptLow());

				// parse vac
				if (busRec.getInverter().getVac() != null)
					if (busRec.getInverter().getVac().getUnit() == VoltageUnitType.VOLT)
						inv.setRatedVac(busRec.getInverter().getVac().getValue());
					else
						throw new IpssDcSysException("Wrong investor ac side voltage unit");

				// parse loss eqn parameters
				if (busRec.getInverter().getLoss() != null) {
					InverterLossEqnXmlType loss = busRec.getInverter().getLoss(); 
					inv.getLossEqn().setA1(loss.getA1());
					inv.getLossEqn().setA2(loss.getA2());
					inv.getLossEqn().setB1(loss.getB1());
					inv.getLossEqn().setB2(loss.getB2());
					inv.getLossEqn().setC1(loss.getC1());
					inv.getLossEqn().setC2(loss.getC2());							
				}
				else {
					for ( InverterLossParamXmlType p : busRec.getInverter().getLossParamList().getLossParam()) {
						LossEqnParamItem item = DcSysObjectFactory.createLossEqnParamItem(inv);
						item.setVdcVolt(p.getVdc());
						item.setA(p.getA());
						item.setB(p.getB());
						item.setC(p.getC());
					}
				}
			}
			else 
				throw new IpssDcSysException("For Inverter bus type, the inverter field needs specified");
		}
	}
	
	/**
	 * Set DcBranch data
	 * 
	 * @param branchRec
	 * @param dcNet
	 * @throws InterpssException
	 */
	private void mapDcBranchData(DcBranchXmlType branchRec, DcNetwork dcNet) throws IpssDcSysException {
		DcBranch branch = DcSysObjectFactory.createDcBranch();
		try {
			super.mapBaseBranchRec(branchRec, branch, dcNet);
		} catch (InterpssException e) {
			throw new IpssDcSysException(e.toString());
		}
		DcFeederXmlType fdr = branchRec.getFeeder() != null?
				branchRec.getFeeder() : branchRec.getHomeRun();
		branch.setR(getROhm(fdr), UnitType.Ohm);
	}
	
	private double getROhm(DcFeederXmlType feeder) throws IpssDcSysException {
		double r = feeder.getR().getR();
		UnitType unit = toZUnit.apply(feeder.getR().getUnit());
		if (unit == UnitType.Ohm)
			;  // doing nothing
		else if (unit == UnitType.OhmPerFt || unit == UnitType.OhmPerM) {
			double length = feeder.getLength().getValue();
			double factor = 1.0;
			if (feeder.getLength().getUnit() == LengthUnitType.KM)
				factor = 0.001;
			else if (feeder.getLength().getUnit() == LengthUnitType.KM)
				factor = NumericConstant.Mile_Ft;
			r *= length * factor;
		}
		else
			throw new IpssDcSysException("Wrong feeder r unit, use Ohm, OhmPerFt or OhmPerM" + feeder.getR().getUnit());
		return r;
	}
}