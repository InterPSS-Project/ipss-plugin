/*
 * @(#)MachDataHelper.java   
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
 * @Author Mike Zhou
 * @Version 1.0
 * @Date 08/15/2010
 * 
 *   Revision History
 *   ================
 *
 */

package org.interpss.mapper.odm.impl.dstab;

import static org.interpss.mapper.odm.ODMUnitHelper.ToApparentPowerUnit;
import static org.interpss.mapper.odm.ODMUnitHelper.ToVoltageUnit;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.schema.ActivePowerXmlType;
import org.ieee.odm.schema.ApparentPowerXmlType;
import org.ieee.odm.schema.ClassicMachineXmlType;
import org.ieee.odm.schema.Eq11Ed11MachineXmlType;
import org.ieee.odm.schema.Eq11MachineXmlType;
import org.ieee.odm.schema.Eq1Ed1MachineXmlType;
import org.ieee.odm.schema.Eq1MachineXmlType;
import org.ieee.odm.schema.EquiMachineXmlType;
import org.ieee.odm.schema.MachineModelXmlType;
import org.ieee.odm.schema.ScEquivSourceXmlType;
import org.ieee.odm.schema.VoltageXmlType;
import org.interpss.numeric.datatype.Unit.UnitType;

import com.interpss.DStabObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.funcImpl.CoreUtilFunc;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.mach.EConstMachine;
import com.interpss.dstab.mach.Eq1Ed1Machine;
import com.interpss.dstab.mach.Eq1Machine;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.MachineType;
import com.interpss.dstab.mach.RoundRotorMachine;
import com.interpss.dstab.mach.SalientPoleMachine;

/**
 * Class for mapping ODM machine xml document to an InterPSS machine model
 * 
 * @author mzhou
 *
 */
public class MachDataHelper {
	private DStabBus dstabBus = null;
	private ApparentPowerXmlType ratedPower = null;
	VoltageXmlType ratedVoltage = null;
	
	/**
	 * constructor
	 * 
	 * @param dstabBus
	 * @param ratedP
	 * @param ratedV
	 */
	public MachDataHelper(DStabBus dstabBus, ApparentPowerXmlType ratedP,	VoltageXmlType ratedV) {
		this.dstabBus = dstabBus;
		this.ratedPower = ratedP;
		this.ratedVoltage = ratedV;
	}
	
	/**
	 * Create machine model and added to the parent DStab bus
	 * 
	 * @param machXmlRec machine ODM xml record
	 * @param machId machine Id, has to be unique for retrieval by id
	 * @return
	 */
	public Machine createMachine(MachineModelXmlType machXmlRec, String machId)  throws InterpssException {
		// make source considering the inheritance relationship
		if (machXmlRec instanceof Eq11Ed11MachineXmlType) {
			Eq11Ed11MachineXmlType machXml = (Eq11Ed11MachineXmlType)machXmlRec;
			// create a machine and connect to the bus 
			RoundRotorMachine mach = (RoundRotorMachine)DStabObjectFactory.
								createMachine(machId, machXml.getName(), MachineType.EQ11_ED11_ROUND_ROTOR, 
								(DStabilityNetwork)this.dstabBus.getNetwork(), dstabBus.getId());
			setEq11Ed11Data(mach, machXml);
			return mach;
		}
		else if (machXmlRec instanceof Eq11MachineXmlType) {
			Eq11MachineXmlType machXml = (Eq11MachineXmlType)machXmlRec;
			// create a machine and connect to the bus
			SalientPoleMachine mach = (SalientPoleMachine)DStabObjectFactory.
								createMachine(machId, machXml.getName(), MachineType.EQ11_SALIENT_POLE, 
								(DStabilityNetwork)this.dstabBus.getNetwork(), dstabBus.getId());
			setEq11Data(mach, machXml);
			return mach;
		}
		else if (machXmlRec instanceof Eq1Ed1MachineXmlType) {
			Eq1Ed1MachineXmlType machXml = (Eq1Ed1MachineXmlType)machXmlRec;
			// create a machine and connect to the bus
			Eq1Ed1Machine mach = (Eq1Ed1Machine)DStabObjectFactory.
								createMachine(machId, machXml.getName(), MachineType.EQ1_ED1_MODEL, 
								(DStabilityNetwork)this.dstabBus.getNetwork(), dstabBus.getId());
			setEq1Ed1Data(mach, machXml);
			return mach;
		}
		else if (machXmlRec instanceof Eq1MachineXmlType) {
			Eq1MachineXmlType machXml = (Eq1MachineXmlType)machXmlRec;
			// create a machine and connect to the bus
			Eq1Machine mach = (Eq1Machine)DStabObjectFactory.
								createMachine(machId, machXml.getName(), MachineType.EQ1_MODEL, 
								(DStabilityNetwork)this.dstabBus.getNetwork(), dstabBus.getId());
			setEq1Data(mach, machXml);
			return mach;
		}
		else if (machXmlRec instanceof ClassicMachineXmlType) {
			ClassicMachineXmlType machXml = (ClassicMachineXmlType)machXmlRec;
			// create a machine and connect to the bus
			EConstMachine mach = (EConstMachine)DStabObjectFactory.
								createMachine(machId, machXml.getName(), MachineType.ECONSTANT, 
								(DStabilityNetwork)this.dstabBus.getNetwork(), dstabBus.getId());
			setClassicData(mach, machXml);
			return mach;
		}
		else if (machXmlRec instanceof EquiMachineXmlType) {
			EquiMachineXmlType machXml = (EquiMachineXmlType)machXmlRec;
			Complex z1 = calSourceZ1(machXml);
			Complex z0 = calSourceZ0(machXml, z1);
			return DStabObjectFactory.createInfiniteMachine(machId, machXml.getName(), 
					z1, z0, (DStabilityNetwork)this.dstabBus.getNetwork(), this.dstabBus.getId());
		}
		
		throw new InterpssException("Error : Wrong mach model type, bus id: " + this.dstabBus.getId());
	}
	
	private void setClassicData(EConstMachine mach, ClassicMachineXmlType machXml) throws InterpssException {
		// set machine data
		if (this.ratedPower != null)
			mach.setRating(this.ratedPower.getValue(), ToApparentPowerUnit.f(this.ratedPower.getUnit()), dstabBus.getNetwork().getBaseKva());
		else
			throw new InterpssException("ratedPower is required, bus Id: " + mach.getDStabBus().getId());
		if (this.ratedVoltage != null)
			mach.setRatedVoltage(this.ratedVoltage.getValue(), ToVoltageUnit.f(this.ratedVoltage.getUnit()));
		else
			mach.setRatedVoltage(dstabBus.getBaseVoltage(), UnitType.Volt);
		// the multiply factor is calculated using machine ratedP and ratedV against system 
		// base kva and bus base voltage
		mach.setMultiFactors(dstabBus);
		mach.setPoles(machXml.getPoles()==null?2:machXml.getPoles());
		mach.setH(machXml.getH());
		mach.setD(machXml.getD());
		mach.setXd1(machXml.getXd1());
	}
	
	private void setEq1Data(Eq1Machine mach, Eq1MachineXmlType machXml) throws InterpssException {
		// set machine data
		if (this.ratedPower != null)
			mach.setRating(this.ratedPower.getValue(), ToApparentPowerUnit.f(this.ratedPower.getUnit()), dstabBus.getNetwork().getBaseKva());
		else
			throw new InterpssException("ratedPower is required, bus Id: " + mach.getDStabBus().getId());
		if (this.ratedVoltage != null)
			mach.setRatedVoltage(this.ratedVoltage.getValue(), ToVoltageUnit.f(this.ratedVoltage.getUnit()));
		else
			mach.setRatedVoltage(dstabBus.getBaseVoltage(), UnitType.Volt);
		// the multiply factor is calculated using machine ratedP and ratedV against system 
		// base kva and bus base voltage
		mach.setMultiFactors(dstabBus);
		// There is no poles info for some data format,such as BPA
		mach.setPoles(machXml.getPoles()==null?2:machXml.getPoles());
		mach.setH(machXml.getH());
		mach.setD(machXml.getD());
		mach.setXd1(machXml.getXd1());
		mach.setX0(machXml.getX0() == null ? 0.0 : machXml.getX0());
		mach.setX2(machXml.getX2() == null ? 0.0 : machXml.getX2());
		mach.setRa(machXml.getRa());
		mach.setXl(machXml.getXl() == null ? 0.0 : machXml.getXl());
		mach.setXd(machXml.getXd());
		mach.setXq(machXml.getXq());
		mach.setTd01(machXml.getTd01().getValue());
		if (machXml.getSeFmt1() != null) {
			mach.setSliner(machXml.getSeFmt1().getSliner());
			mach.setSe100(machXml.getSeFmt1().getSe100());
			mach.setSe120(machXml.getSeFmt1().getSe120());					
		}
		else if (machXml.getSeFmt2() != null) {
			// TODO
		}
	}
	
	private void setEq1Ed1Data(Eq1Ed1Machine mach, Eq1Ed1MachineXmlType machXml)  throws InterpssException {
		setEq1Data(mach, machXml);
		mach.setXq1(machXml.getXq1());
		mach.setTq01(machXml.getTq01().getValue());
	}
	
	private void setEq11Data(SalientPoleMachine mach, Eq11MachineXmlType machXml)  throws InterpssException {
		setEq1Data(mach, machXml);
		mach.setXd11(machXml.getXd11());
		mach.setTd011(machXml.getTd011().getValue());
		mach.setXq11(machXml.getXq11());
		mach.setTq011(machXml.getTq011().getValue());
	}

	private void setEq11Ed11Data(RoundRotorMachine mach, Eq11Ed11MachineXmlType machXml) throws InterpssException {
		setEq1Ed1Data(mach, machXml);
		mach.setXq11(machXml.getXq11());
		mach.setTq011(machXml.getTq011().getValue());
		mach.setXd11(machXml.getXd11());
		mach.setTd011(machXml.getTd011().getValue());
	}
	
	private Complex calSourceZ1(EquiMachineXmlType machXml) {
		if ( machXml.getEquivSource() != null) {
			ScEquivSourceXmlType source = machXml.getEquivSource(); 
			if (source.getScMva3Phase() > 0.0 && source.getXOverR3Phase() > 0.0)
				return CoreUtilFunc.calUitilityZ1PU(source.getScMva3Phase() * 1000,
						source.getXOverR3Phase(), this.dstabBus.getNetwork().getBaseKva());
		}
		else if (machXml.getEquivGen() != null) {
			// TODO
		}
		return new Complex(0.0,0.0);
	}
	
	private Complex calSourceZ0(EquiMachineXmlType machXml, Complex z1) {
		if ( machXml.getEquivSource() != null) {
			ScEquivSourceXmlType source = machXml.getEquivSource(); 
			if (source.getXOverR1Phase() != null && source.getXOverR1Phase() != null 
					&& source.getXOverR1Phase() > 0.0 && source.getXOverR1Phase() > 0.0) {
				return CoreUtilFunc.calUitilityZ0PU(source.getScMva1Phase() * 1000,
						source.getXOverR1Phase(), this.dstabBus.getNetwork().getBaseKva(), z1);
			}
		}
		else if (machXml.getEquivGen() != null) {
			// TODO
		}
		return new Complex(0.0,0.0);
	}
}