/*
 * @(#)DistBusHelper.java   
 *
 * Copyright (C) 2011 www.interpss.org
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

package org.interpss.odm.mapper.impl.dist;

import static org.interpss.odm.mapper.base.ODMUnitHelper.toActivePowerUnit;
import static org.interpss.odm.mapper.base.ODMUnitHelper.toAngleUnit;
import static org.interpss.odm.mapper.base.ODMUnitHelper.toApparentPowerUnit;
import static org.interpss.odm.mapper.base.ODMUnitHelper.toFactorUnit;
import static org.interpss.odm.mapper.base.ODMUnitHelper.toVoltageUnit;
import static org.interpss.odm.mapper.base.ODMUnitHelper.toZUnit;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.schema.FactorUnitType;
import org.ieee.odm.schema.GeneratorDistBusXmlType;
import org.ieee.odm.schema.GroundingXmlType;
import org.ieee.odm.schema.InductionMotorDistBusXmlType;
import org.ieee.odm.schema.MixedLoadDistBusXmlType;
import org.ieee.odm.schema.MotorDistBusXmlType;
import org.ieee.odm.schema.NamedZXmlType;
import org.ieee.odm.schema.NonContributingDistBusXmlType;
import org.ieee.odm.schema.RotatingMachineDistBusXmlType;
import org.ieee.odm.schema.SynchronousMotorDistBusXmlType;
import org.ieee.odm.schema.UtilityDistBusXmlType;
import org.interpss.numeric.NumericConstant;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.odm.mapper.base.ODMHelper;

import com.interpss.core.acsc.BusGroundCode;
import com.interpss.core.acsc.BusScGrounding;
import com.interpss.core.net.Bus;
import com.interpss.dist.DistBus;
import com.interpss.dist.adpter.DistGenerator;
import com.interpss.dist.adpter.DistIndMotor;
import com.interpss.dist.adpter.DistMixedLoad;
import com.interpss.dist.adpter.DistMotor;
import com.interpss.dist.adpter.DistRotatingMachine;
import com.interpss.dist.adpter.DistSynMotor;
import com.interpss.dist.adpter.DistUtility;

public class DistBusHelper {
	private DistBus bus = null;
	
	public DistBusHelper(DistBus bus) {
		this.bus = bus;
	}
	
	public void setUtilityBusData(UtilityDistBusXmlType busRec) {
		DistUtility util = this.bus.toUtility();
		double v = busRec.getVoltage().getValue(),
	           ang = busRec.getAngle().getValue();
		UnitType vunit = toVoltageUnit.apply(busRec.getVoltage().getUnit()),
	         angunit = toAngleUnit.apply(busRec.getAngle().getUnit());
		util.setVoltage(v, vunit, ang, angunit);
	
		double scMva3P = busRec.getSource().getScMva3Phase(),
	           scMva1P = busRec.getSource().getScMva1Phase() != null? 
	        		   		busRec.getSource().getScMva1Phase(): 0.0;
	        		   		UnitType mvaunit = UnitType.mVA;
		util.setMvaRating(scMva3P, scMva1P, mvaunit);
	
		double xr3P = busRec.getSource().getXOverR3Phase(),
	           xr1P = busRec.getSource().getXOverR1Phase() != null ? 
	        		   busRec.getSource().getXOverR1Phase() : 0.0;
		util.setX_R(xr3P, xr1P);
	}
	
	private void setRotatingMachineBusData(RotatingMachineDistBusXmlType busRec, DistRotatingMachine gen) {
		double v = busRec.getRatedVoltage().getValue();
		UnitType vunit = toVoltageUnit.apply(busRec.getRatedVoltage().getUnit());
		gen.setRatedVoltage(v, vunit);
		
		double pf = busRec.getPFactor().getValue();
		UnitType pfunit = toFactorUnit.apply(busRec.getPFactor().getUnit());
		gen.setPFactor(pf, pfunit);
		
		if (busRec.getZ1() != null && busRec.getZ1().size() > 0) {
			NamedZXmlType z = busRec.getZ1().get(0);
			Complex z1 = new Complex(z.getRe(), z.getIm());
			UnitType zunit = toZUnit.apply(z.getUnit());
			gen.setZ1(z1);
			gen.setZUnit(zunit);
		}
		
		Complex z0 = busRec.getZ0() != null? new Complex(busRec.getZ0().getRe(), busRec.getZ0().getIm()) : NumericConstant.LargeBusZ;
		Complex z2 = busRec.getZ2() != null? new Complex(busRec.getZ2().getRe(), busRec.getZ2().getIm()) : NumericConstant.LargeBusZ;
		gen.setZ0_2(z0, z2);

		// grounding
		setGroundingData(busRec.getGrounding(), gen.getGrounding(), gen.getBus());
	}
	
	public static void setGroundingData(GroundingXmlType xmlGrounding, BusScGrounding objGrounding, Bus bus) {
		BusGroundCode code = ODMHelper.toBusGroundCode(xmlGrounding.getGroundingConnection());
		objGrounding.setGroundCode(code);
		if (xmlGrounding.getGroundingZ() != null) {
			Complex zg = new Complex(xmlGrounding.getGroundingZ().getRe(), xmlGrounding.getGroundingZ().getIm());
			UnitType zgunit = toZUnit.apply(xmlGrounding.getGroundingZ().getUnit());
			objGrounding.setZ(zg, zgunit, bus.getBaseVoltage(), bus.getNetwork().getBaseKva());		
		}
	}

	public void setGeneratorBusData(GeneratorDistBusXmlType busRec) {
		DistGenerator gen = this.bus.toGenerator();
		setRotatingMachineBusData(busRec, gen);
		
		double kw = busRec.getRetedMva().getValue();
		UnitType kwunit = toApparentPowerUnit.apply(busRec.getRetedMva().getUnit());
		gen.setRatedKW(kw, kwunit);
		
		// set loading in percent
		double loading = busRec.getLoading().getValue();
		if (busRec.getLoading().getUnit() == FactorUnitType.PU)
			loading *= 100.0;
		gen.setLoading(loading);
	}
	
	private void setMotorBusData(MotorDistBusXmlType busRec, DistMotor motor) {
		setRotatingMachineBusData(busRec, motor);

		double hp = busRec.getRatedPower().getValue();
		UnitType hpunit = toActivePowerUnit.apply(busRec.getRatedPower().getUnit());
		motor.setRatedHP(hp, hpunit);

		double eff = busRec.getEfficiency().getValue(),
		       loading = busRec.getLoading().getValue();
		if (busRec.getEfficiency().getUnit() == FactorUnitType.PU)
			eff *= 100.0;
		if (busRec.getLoading().getUnit() == FactorUnitType.PU)
			loading *= 100.0;
		motor.setEffLoading(eff, loading);
	}

	public void setSynchronousMotorBusData(SynchronousMotorDistBusXmlType busRec) {
		DistSynMotor motor = this.bus.toSynMotor();
		setMotorBusData(busRec, motor);
	}
	
	public void setInductionMotorBusData(InductionMotorDistBusXmlType busRec) {
		DistIndMotor motor = this.bus.toIndMotor();
		setMotorBusData(busRec, motor);
	}

	public void setMixedLoadBusData(MixedLoadDistBusXmlType busRec) {
		DistMixedLoad load = this.bus.toMixedLoad();
		setRotatingMachineBusData(busRec, load);
		
		double kva = busRec.getTotalKva().getValue();
		UnitType kvaunit = toApparentPowerUnit.apply(busRec.getTotalKva().getUnit());
		load.setTotalKva(kva, kvaunit);

		double percent = busRec.getMotorPercent();
		load.setMotorPercent(percent);
	}
	
	public void setNonContributingBusData(NonContributingDistBusXmlType busRec) {
		// no data 
	}
}
