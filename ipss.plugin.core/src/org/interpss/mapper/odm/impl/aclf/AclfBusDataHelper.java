/*
 * @(#)AAclfBusDataHelper.java   
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
 * @Date 02/01/2011
 * 
 *   Revision History
 *   ================
 *
 */

package org.interpss.mapper.odm.impl.aclf;

import static org.interpss.mapper.odm.ODMFunction.BusXmlRef2BusId;
import static org.interpss.mapper.odm.ODMUnitHelper.ToActivePowerUnit;
import static org.interpss.mapper.odm.ODMUnitHelper.ToAngleUnit;
import static org.interpss.mapper.odm.ODMUnitHelper.ToApparentPowerUnit;
import static org.interpss.mapper.odm.ODMUnitHelper.ToReactivePowerUnit;
import static org.interpss.mapper.odm.ODMUnitHelper.ToVoltageUnit;
import static org.interpss.mapper.odm.ODMUnitHelper.ToYUnit;

import javax.xml.bind.JAXBElement;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.model.aclf.AclfParserHelper;
import org.ieee.odm.schema.ApparentPowerUnitType;
import org.ieee.odm.schema.BusGenDataXmlType;
import org.ieee.odm.schema.BusLoadDataXmlType;
import org.ieee.odm.schema.LFGenCodeEnumType;
import org.ieee.odm.schema.LoadflowBusXmlType;
import org.ieee.odm.schema.LoadflowGenDataXmlType;
import org.ieee.odm.schema.LoadflowLoadDataXmlType;
import org.ieee.odm.schema.PowerXmlType;
import org.ieee.odm.schema.ReactivePowerUnitType;
import org.ieee.odm.schema.ReactivePowerXmlType;
import org.ieee.odm.schema.ShuntCompensatorBlockXmlType;
import org.ieee.odm.schema.ShuntCompensatorModeEnumType;
import org.ieee.odm.schema.ShuntCompensatorXmlType;
import org.ieee.odm.schema.VoltageUnitType;
import org.ieee.odm.schema.VoltageXmlType;
import org.ieee.odm.schema.YXmlType;
import org.interpss.numeric.datatype.LimitType;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.Number2String;

import com.interpss.CoreObjectFactory;
import com.interpss.DStabObjectFactory;
import com.interpss.common.datatype.UnitHelper;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoad;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.aclf.adj.QBank;
import com.interpss.core.aclf.adj.SwitchedShunt;
import com.interpss.core.aclf.adj.VarCompensatorControlMode;
import com.interpss.core.acsc.AcscBus;
import com.interpss.dstab.DStabBus;

/**
 * Aclf bus data ODM mapping helper functions
 * 
 * @author mzhou
 *
 */
public class AclfBusDataHelper<TBus extends AclfBus> {
	private BaseAclfNetwork<?,?> aclfNet = null;
	private TBus bus = null;
	
	/**
	 * constructor
	 * 
	 * @param aclfNet
	 */
	public AclfBusDataHelper(BaseAclfNetwork<?,?> aclfNet) {
		this.aclfNet = aclfNet;
	}
	
	/**
	 * set AclfBus object
	 * 
	 * @param bus
	 */
	public void setBus(TBus bus) {
		this.bus = bus;
	}
	
	/**
	 * map the Loadflow bus ODM object info to the AclfBus object
	 * 
	 * @param xmlBusData
	 * @throws InterpssException
	 */
	public void setAclfBusData(LoadflowBusXmlType xmlBusData) throws InterpssException {
		VoltageXmlType vXml = xmlBusData.getVoltage();
		double vpu = 1.0;
		if (vXml != null) {
			UnitType unit = ToVoltageUnit.f(vXml.getUnit());
			vpu = UnitHelper.vConversion(vXml.getValue(), bus.getBaseVoltage(), unit, UnitType.PU);
		}
		double angRad = 0.0;
		if (xmlBusData.getAngle() !=  null) {
			UnitType unit = ToAngleUnit.f(xmlBusData.getAngle().getUnit()); 
			angRad = UnitHelper.angleConversion(xmlBusData.getAngle().getValue(), unit, UnitType.Rad); 
		}
		bus.setVoltage(vpu, angRad);
		//System.out.println(bus.getId() + "  " + Number2String.toStr(bus.getVoltage()));

		if (xmlBusData.getVLimit() != null) {
			double factor = 10;
			if (xmlBusData.getVLimit().getUnit() == VoltageUnitType.KV)
				factor = 1000.0 / bus.getBaseVoltage();
			else if (xmlBusData.getVLimit().getUnit() == VoltageUnitType.VOLT)
				factor = 1.0 / bus.getBaseVoltage();
			bus.setVLimit(new LimitType(xmlBusData.getVLimit().getMax()*factor,xmlBusData.getVLimit().getMin()*factor));
		}
		
		if (xmlBusData.getGenData()!=null) {
			// map desired swing bus angle 
			if (xmlBusData.getGenData().getCode() == LFGenCodeEnumType.SWING)
				bus.toSwingBus().setDesiredVoltAng(angRad);
			//add check to make sure there is at least one  generator with the bus
			if(xmlBusData.getGenData().getCode()!=LFGenCodeEnumType.NONE_GEN && xmlBusData.getGenData().getContributeGen().size()>0)
				mapGenData(xmlBusData.getGenData());
		} else {
			bus.setGenCode(AclfGenCode.NON_GEN);
		}

		//System.out.println(bus.getId() + "  " + Number2String.toStr(bus.getVoltage()));		
		
		if (xmlBusData.getLoadData() != null && xmlBusData.getLoadData().getContributeLoad().size()>0) {
			mapLoadData(xmlBusData.getLoadData());
		} else {
			bus.setLoadCode(AclfLoadCode.NON_LOAD);
		}

		if (xmlBusData.getShuntYData() != null && xmlBusData.getShuntYData().getEquivY() != null) {
			YXmlType shuntY = xmlBusData.getShuntYData().getEquivY();
//			byte unit = shuntY.getUnit() == YUnitType.MVAR? UnitType.mVar : UnitType.PU;
			UnitType unit = ToYUnit.f(shuntY.getUnit());
			Complex ypu = UnitHelper.yConversion(new Complex(shuntY.getRe(), shuntY.getIm()),
					bus.getBaseVoltage(), aclfNet.getBaseKva(), unit, UnitType.PU);
			//System.out.println("----------->" + shuntY.getIm() + ", " + shuntY.getUnit() + ", " + ypu.getImaginary());
			bus.setShuntY(ypu);
		}
		
		if(xmlBusData.getShuntCompensator()!=null){
			mapSwitchShuntData(xmlBusData.getShuntCompensator());
		}
	}
	
	private void mapGenData(BusGenDataXmlType xmlGenData) throws InterpssException {
		LoadflowGenDataXmlType xmlDefaultGen = AclfParserHelper.getDefaultGen(xmlGenData);
		if (xmlGenData.getCode() == LFGenCodeEnumType.PQ) {
			bus.setGenCode(AclfGenCode.GEN_PQ);
		} else if (xmlGenData.getCode() == LFGenCodeEnumType.PV && xmlDefaultGen != null) {
			if (xmlDefaultGen.getRemoteVoltageControlBus() == null) {
				bus.setGenCode(AclfGenCode.GEN_PV);
			}
			else {
				bus.setGenCode(AclfGenCode.GEN_PQ);
			}
		} else if (xmlGenData.getCode() == LFGenCodeEnumType.SWING) {
			bus.setGenCode(AclfGenCode.SWING);
		} else {
			bus.setGenCode(AclfGenCode.NON_GEN);
		}

		//map contributing generator data 
		mapContributeGenListData(xmlGenData);
	}
	
	private void mapContributeGenListData(BusGenDataXmlType xmlGenData) throws InterpssException{
		//LoadflowGenDataXmlType xmlDefaultGen = AclfParserHelper.getDefaultGen(xmlGenData);
		
		double baseKva = bus.getNetwork().getBaseKva();
		if(xmlGenData.getContributeGen()!=null){
			/*
			 * in general, gen code is defined at the equivGen level.
			 */
			//LFGenCodeEnumType genCode = xmlGenData.getCode();
			int genCnt = 1;
			for(JAXBElement<? extends LoadflowGenDataXmlType> elem :xmlGenData.getContributeGen()){
				LoadflowGenDataXmlType xmlGen= elem.getValue();
				
				//Map load flow generator data
				String id = xmlGen.getId()!=null?xmlGen.getId():this.bus.getId()+"-G"+genCnt++;
				AclfGen gen= this.bus instanceof DStabBus? DStabObjectFactory.createDStabGen(id) :
								this.bus instanceof AcscBus? CoreObjectFactory.createAcscGen(id) : 
									CoreObjectFactory.createAclfGen(id);
				
				gen.setStatus(xmlGen.isOffLine()==null?true:!xmlGen.isOffLine());
				/*
				double Mva =xmlGen.getMvaBase().getValue();
				double MvaFactor = xmlGen.getMvaBase().getUnit()==ApparentPowerUnitType.MVA?1.0:
					    xmlGen.getMvaBase().getUnit()==ApparentPowerUnitType.KVA?1.0E-3:
						xmlGen.getMvaBase().getUnit()==ApparentPowerUnitType.VA?1.0E-6:
							100.0; //PU, assuming 100 MVA Base
				*/
				/*
				gen.setCode(genCode == LFGenCodeEnumType.SWING? AclfGenCode.SWING : 
								genCode == LFGenCodeEnumType.PQ? AclfGenCode.GEN_PQ :
									genCode == LFGenCodeEnumType.PV? AclfGenCode.GEN_PV : AclfGenCode.NON_GEN);
				*/
				double genMva = xmlGen.getMvaBase() != null? xmlGen.getMvaBase().getValue() : baseKva*0.001;
				gen.setMvaBase(genMva);
				
				if (xmlGen.getDesiredVoltage() != null)
					gen.setDesiredVoltMag(UnitHelper.vConversion(xmlGen.getDesiredVoltage().getValue(),
						bus.getBaseVoltage(), ToVoltageUnit.f(xmlGen.getDesiredVoltage().getUnit()), UnitType.PU));
				
				PowerXmlType genPower = xmlGen.getPower();
				
				Complex genPQ= genPower!=null? new Complex(genPower.getRe(),genPower.getIm()) : new Complex(0.0,0.0);
				/*
				double genFactor = genPower.getUnit()==ApparentPowerUnitType.MVA?1.0E-2:
							genPower.getUnit()==ApparentPowerUnitType.KVA?1.0E-5:
								genPower.getUnit()==ApparentPowerUnitType.VA?1.0E-8:
								1.0; //PU, assuming 100 MVA Base
				*/
				
				//AclfGen power is defined in pu, system MVA-based
				gen.setGen(UnitHelper.pConversion(genPQ, baseKva, 
						ToApparentPowerUnit.f(xmlGen.getPower()==null?ApparentPowerUnitType.PU:xmlGen.getPower().getUnit()), UnitType.PU ));
				
				if(xmlGen.getSourceZ()!=null)
				gen.setSourceZ(new Complex(xmlGen.getSourceZ().getRe(),xmlGen.getSourceZ().getIm()));
				
				// generator step-up transformer: z = 0, Tap =1.0 by default, which means
				// the transformer is modeled separately.
				if(xmlGen.getXfrZ()!=null){
				  if(xmlGen.getXfrZ().getIm()!=0 ||xmlGen.getXfrZ().getRe()!=0){
				      gen.setXfrZ(new Complex(xmlGen.getXfrZ().getRe(),xmlGen.getXfrZ().getIm()));
				      gen.setXfrTap(xmlGen.getXfrTap() != null? xmlGen.getXfrTap() : 1.0);
			        }
				}
				//AclfGen active power limit is defined in pu, system MVA-based
				if(xmlGen.getPLimit()!=null)
				gen.setPGenLimit(UnitHelper.pConversion( new LimitType(xmlGen.getPLimit().getMax(),xmlGen.getPLimit().getMin()), 
						baseKva, ToActivePowerUnit.f(xmlGen.getPLimit().getUnit()), UnitType.PU ));
				
				//AclfGen reactive power limit
				if(xmlGen.getQLimit()!=null)
				gen.setQGenLimit(UnitHelper.pConversion( new LimitType(xmlGen.getQLimit().getMax(),xmlGen.getQLimit().getMin()), 
						baseKva, ToReactivePowerUnit.f(xmlGen.getQLimit().getUnit()), UnitType.PU ));
				
				if(xmlGen.getRemoteVoltageControlBus()!=null){
					String remoteId = BusXmlRef2BusId.fx(xmlGen.getRemoteVoltageControlBus());
					gen.setRemoteVControlBusId(remoteId);
				}
				
				
				gen.setMvarControlPFactor(xmlGen.getMvarVControlParticipateFactor()!=null?xmlGen.getMvarVControlParticipateFactor():1.0);
				
				//MW pf is optional
				gen.setMwControlPFactor(xmlGen.getMwControlParticipateFactor()!=null?xmlGen.getMwControlParticipateFactor():1.0);
				
				//add the generator to the bus GenList
				bus.getGenList().add(gen);
			}
		}
	}
	
	
	private void mapLoadData(BusLoadDataXmlType xmlLoadData) throws InterpssException {
		double baseKva = bus.getNetwork().getBaseKva();
		bus.setLoadCode(AclfLoadCode.NON_LOAD);
		
        // map each connecting load
		if(xmlLoadData.getContributeLoad()!=null){
			if(xmlLoadData.getContributeLoad().size()>0){
				// we set parent bus load code to constant power
				bus.setLoadCode(AclfLoadCode.CONST_P);
				int loadCnt = 1;
				for(JAXBElement<? extends LoadflowLoadDataXmlType> elem: xmlLoadData.getContributeLoad()){
					if(elem!=null){
						LoadflowLoadDataXmlType loadElem = elem.getValue();
						
						String id = loadElem.getId()!=null?loadElem.getId():this.bus.getId()+"-L"+loadCnt++;
						AclfLoad load = CoreObjectFactory.createAclfLoad(id);
						
						load.setName(loadElem.getName());
						
						bus.getLoadList().add(load);
						//status
						load.setStatus(loadElem.isOffLine()!=null?!loadElem.isOffLine():true);
					    // load code		
						AclfLoadCode code = AclfLoadCode.NON_LOAD;
						PowerXmlType p = loadElem.getConstPLoad(),
									 i = loadElem.getConstILoad(),
									 z = loadElem.getConstZLoad();

						if (p != null) {
							code = code == AclfLoadCode.NON_LOAD? AclfLoadCode.CONST_P : AclfLoadCode.ZIP;
							load.setLoadCP(UnitHelper.pConversion( new Complex(p.getRe(),p.getIm()), 
									baseKva, ToApparentPowerUnit.f(p.getUnit()), UnitType.PU ));
						}
						
						if (i != null) {
							code = code == AclfLoadCode.NON_LOAD? AclfLoadCode.CONST_I : AclfLoadCode.ZIP;
							load.setLoadCI(UnitHelper.pConversion( new Complex(i.getRe(),i.getIm()), 
									baseKva, ToApparentPowerUnit.f(i.getUnit()), UnitType.PU ));
						}

						if (z != null) {
							code = code == AclfLoadCode.NON_LOAD? AclfLoadCode.CONST_Z : AclfLoadCode.ZIP;
							load.setLoadCZ(UnitHelper.pConversion( new Complex(z.getRe(),z.getIm()), 
									baseKva, ToApparentPowerUnit.f(z.getUnit()), UnitType.PU ));
						}

						load.setCode(code);
				   }
			   }
		   }
		}
	}
	
	private void mapSwitchShuntData(ShuntCompensatorXmlType xmlSwitchedShuntData){
		//TODO 
		SwitchedShunt swchShunt = CoreObjectFactory.createSwitchedShunt();
		//swithced shunt is a also a AclfControlBus
		this.bus.setBusControl(swchShunt);
		
		ReactivePowerXmlType binit = xmlSwitchedShuntData.getBInit();
		
		if (binit != null) {
			//cacluate the factor to convert binit to pu based.
			double factor = binit.getUnit()==ReactivePowerUnitType.PU?1.0:
				             binit.getUnit()==ReactivePowerUnitType.MVAR?0.01:
				            	 binit.getUnit()==ReactivePowerUnitType.KVAR?1.0E-5:
				            		 1.0E-8; // VAR->1.0E-8 with a 100 MVA base
			
			swchShunt.setBInit(binit.getValue()*factor);

			VarCompensatorControlMode mode = xmlSwitchedShuntData.getMode()==ShuntCompensatorModeEnumType.CONTINUOUS?
					VarCompensatorControlMode.CONTINUOUS:xmlSwitchedShuntData.getMode()==ShuntCompensatorModeEnumType.DISCRETE?
					VarCompensatorControlMode.DISCRETE:VarCompensatorControlMode.FIXED;
			
			swchShunt.setControlMode(mode);
			
			LimitType vLimit = new LimitType(xmlSwitchedShuntData.getDesiredVoltageRange().getMax(),
					xmlSwitchedShuntData.getDesiredVoltageRange().getMin());
			//TODO vLimit is missing
			//swchShunt.set
			for(ShuntCompensatorBlockXmlType varBankXml:xmlSwitchedShuntData.getBlock()){
				QBank varBank= CoreObjectFactory.createQBank();
				swchShunt.getVarBankArray().add(varBank);
				
				varBank.setSteps(varBankXml.getSteps());
				ReactivePowerXmlType unitVarXml = varBankXml.getIncrementB();
				
				factor = unitVarXml.getUnit()==ReactivePowerUnitType.PU?1.0:
					unitVarXml.getUnit()==ReactivePowerUnitType.MVAR?1.0E-2:
						unitVarXml.getUnit()==ReactivePowerUnitType.KVAR?1.0E-5:
		            		 1.0E-8; 
				//TODO UnitQMVar is in pu
				varBank.setUnitQMvar(unitVarXml.getValue()*factor);
				
			}
		}
	}
}
