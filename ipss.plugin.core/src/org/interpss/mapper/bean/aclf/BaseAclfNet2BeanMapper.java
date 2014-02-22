/*
 * @(#) BaseAclfNet2BeanMapper.java   
 *
 * Copyright (C) 2008-2013 www.interpss.org
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
 * @Date 01/15/2013
 * 
 *   Revision History
 *   ================
 *
 */

package org.interpss.mapper.bean.aclf;

import org.apache.commons.math3.complex.Complex;
import org.interpss.datamodel.bean.BaseBranchBean;
import org.interpss.datamodel.bean.aclf.AclfBranchBean;
import org.interpss.datamodel.bean.aclf.AclfBusBean;
import org.interpss.datamodel.bean.aclf.adj.AclfGenBean;
import org.interpss.datamodel.bean.aclf.adj.AclfLoadBean;
import org.interpss.datamodel.bean.aclf.adj.BaseTapControlBean.TapControlTypeBean;
import org.interpss.datamodel.bean.aclf.adj.PsXfrTapControlBean;
import org.interpss.datamodel.bean.aclf.adj.QBankBean;
import org.interpss.datamodel.bean.aclf.adj.SwitchShuntBean;
import org.interpss.datamodel.bean.aclf.adj.XfrTapControlBean;
import org.interpss.datamodel.bean.aclf.adj.SwitchShuntBean.VarCompensatorControlModeBean;
import org.interpss.datamodel.bean.aclf.adj.XfrTapControlBean.TapControlModeBean;
import org.interpss.datamodel.bean.datatype.BranchValueBean;
import org.interpss.datamodel.bean.datatype.ComplexBean;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.Number2String;

import com.interpss.common.mapper.AbstractMapper;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfLoad;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adj.AdjControlType;
import com.interpss.core.aclf.adj.PSXfrPControl;
import com.interpss.core.aclf.adj.QBank;
import com.interpss.core.aclf.adj.SwitchedShunt;
import com.interpss.core.aclf.adj.TapControl;
import com.interpss.core.aclf.adj.VarCompensatorControlMode;
import com.interpss.core.aclf.adj.XfrTapControlType;
import com.interpss.core.aclf.adpter.AclfPSXformer;
import com.interpss.core.aclf.adpter.AclfXformer;

/**
 * base mapper functions for mapping AclfNetwork object to BaseNetBean (AclfNetBean and AclfNetResultBean)
 * 
 * 
 */
public abstract class BaseAclfNet2BeanMapper<TBean> extends AbstractMapper<AclfNetwork, TBean> {
	/**
	 * map an AclfBus object to an AclfBusBean 
	 * 
	 * @param bus
	 * @param bean
	 */
	public static void mapBaseBus(AclfBus bus, AclfBusBean bean) {
		bean.number = bus.getNumber();
		bean.id = bus.getId();
		bean.name = bus.getName();
		boolean status = bus.isActive();
		bean.status = 1;
		if(!status)
			bean.status = 0;
		bean.base_v = bus.getBaseVoltage()/1000;
		bean.v_mag = format(bus.getVoltageMag());
		bean.v_ang = format(bus.getVoltageAng(UnitType.Deg));
		bean.vDesired_mag = format(bus.getDesiredVoltMag());
		bean.vDesired_ang = format(Math.toDegrees(bus.getDesiredVoltAng()));
		bean.vmax = format(bus.getVLimit().getMax()) == 0? bean.vmax : format(bus.getVLimit().getMax());
		bean.vmin = format(bus.getVLimit().getMin()) == 0? bean.vmin : format(bus.getVLimit().getMin());

		bean.gen_code = bus.isGenPQ()? AclfBusBean.GenCode.PQ :
			(bus.isGenPV() ? AclfBusBean.GenCode.PV : 
				(bus.isSwing()? AclfBusBean.GenCode.Swing : 
					AclfBusBean.GenCode.NonGen));
				
		Complex gen = bus.getNetGenResults();
		bean.gen = new ComplexBean(format(gen));

		bean.load_code = bus.isConstPLoad() ? AclfBusBean.LoadCode.ConstP :
			(bus.isConstZLoad() ? AclfBusBean.LoadCode.ConstZ : 
				(bus.isConstILoad() ? AclfBusBean.LoadCode.ConstI : 
					AclfBusBean.LoadCode.NonLoad));

		Complex load = bus.getNetLoadResults();
		bean.load = new ComplexBean(format(load));
		
		Complex sh = bus.getShuntY();
		bean.shunt = new ComplexBean(format(sh));
		
		bean.area = 1;
		bean.zone = 1;
		bean.areaName = "";
		bean.zoneName = "";
		if(bus.getArea() !=null){
			bean.area = bus.getArea().getNumber();
			bean.areaName = bus.getArea().getName();
		}
		if(bus.getZone() != null){
			bean.zone = bus.getZone().getNumber();
			bean.zoneName = bus.getZone().getName();
		}
		
		// map gen data
		for (AclfGen g: bus.getGenList()){
			AclfGenBean gbean = new AclfGenBean();
			mapAclfGenData(g, gbean);				
			bean.gen_list.add(gbean);
		}
		// map load data
		for(AclfLoad ld: bus.getLoadList()){
			AclfLoadBean ldb = new AclfLoadBean();
			mapAclfLoadData(ld, ldb);			
			bean.load_list.add(ldb);
		}
		// map switched shunt data
		if (bus.getSwitchedShunt() != null) {
			SwitchedShunt ss = bus.getSwitchedShunt();
			SwitchShuntBean ssb = new SwitchShuntBean();
			mapSwitchShuntData(ss,ssb);			
			bean.switchShunt = ssb;
		}
	}	
	
	

	/**
	 * map an AclfBranch object to an AclfBranchBean 
	 * 
	 * @param branch
	 * @param bean
	 */
	public static void mapBaseBranch(AclfBranch branch, AclfBranchBean bean) {
		bean.id = branch.getId();
		bean.name = branch.getName();
		bean.f_id = branch.getFromBus().getId();
		bean.f_num = branch.getFromBus().getNumber();
		bean.t_id = branch.getToBus().getId();
		bean.t_num = branch.getToBus().getNumber();		
		bean.cir_id = branch.getCircuitNumber();
		bean.f_name = branch.getFromBus().getName();
		bean.t_name = branch.getToBus().getName();
		
		bean.status = branch.isActive()? 1 : 0; 				
		
		bean.bra_code = branch.isLine() ? BaseBranchBean.BranchCode.Line :
			(branch.isXfr() ? BaseBranchBean.BranchCode.Xfr : 
				(branch.isPSXfr() ? BaseBranchBean.BranchCode.PsXfr:
					BaseBranchBean.BranchCode.ZBR ));
		
		Complex z = branch.getZ();
		bean.z = new ComplexBean(z);
		bean.shunt_y = new ComplexBean(format(new Complex(0, 0)));	
		bean.ratio = new BranchValueBean(1.0,1.0);		
		if (branch.getBranchCode() == AclfBranchCode.LINE ||
				branch.getBranchCode() == AclfBranchCode.ZBR) {
			if (branch.getHShuntY() != null)				
				bean.shunt_y = new ComplexBean(format(new Complex(0, branch.getHShuntY().getImaginary()*2)));				
				
		}
		else if (branch.getBranchCode() == AclfBranchCode.XFORMER ){
			AclfXformer xfr = branch.toXfr();			
			bean.ratio.f = xfr.getFromTurnRatio();
			bean.ratio.t = xfr.getToTurnRatio();	
			if(branch.getTapControl() != null){
				TapControl tap = branch.getTapControl();
				XfrTapControlBean tapBean = new XfrTapControlBean();
				bean.xfrTapControlBean = tapBean;
				mapXfrData(tap, tapBean);
			}
		}else if (	branch.getBranchCode() == AclfBranchCode.PS_XFORMER){
			AclfPSXformer xfr = branch.toPSXfr();			
			bean.ratio.f = xfr.getFromTurnRatio();
			bean.ratio.t = xfr.getToTurnRatio();	
			if(branch.getPSXfrPControl() != null){
				PSXfrPControl tap = branch.getPSXfrPControl();
				PsXfrTapControlBean tb = new PsXfrTapControlBean();
				bean.psXfrTapControlBean = tb;
				mapPsXfrData(tap, tb);
			}
		}
		
		bean.mvaRatingA = branch.getRatingMva1();
		bean.mvaRatingB = branch.getRatingMva2();
		bean.mvaRatingC = branch.getRatingMva3();			
	}	
	
	private static void mapSwitchShuntData(SwitchedShunt ss, SwitchShuntBean ssb) {
		ssb.bInit = ss.getBInit();
		ssb.controlMode = ss.getControlMode() == VarCompensatorControlMode.CONTINUOUS? VarCompensatorControlModeBean.Continuous:
			ss.getControlMode() == VarCompensatorControlMode.DISCRETE? VarCompensatorControlModeBean.Discrete:
				VarCompensatorControlModeBean.Fixed;
		ssb.remoteBusId = ss.getRemoteBus().getId();
		ssb.vmax = ss.getDesiredVoltageRange().getMax();
		ssb.vmin = ss.getDesiredVoltageRange().getMin();
		for(QBank qb: ss.getVarBankArray()){
			QBankBean qbb =  new QBankBean();
			qbb.step = qb.getSteps();
			qbb.UnitQMvar = qb.getUnitQMvar();
			ssb.varBankList.add(qbb);
		}
		
	}

	private static void mapAclfGenData(AclfGen g, AclfGenBean gbean) {
		gbean.pgen = g.getGen().getReal();
		gbean.qgen = g.getGen().getImaginary();
		gbean.pmax = g.getPGenLimit().getMax();
		gbean.pmin = g.getPGenLimit().getMin();
		gbean.qmax = g.getQGenLimit().getMax();
		gbean.qmin = g.getQGenLimit().getMin();
		gbean.remoteVControlBusId = g.getRemoteVControlBusId();
		gbean.scheduledVol = g.getDesiredVoltMag();
		gbean.status = g.isStatus() == true? 1 : 0;
		gbean.cirId = g.getId();		
	}

	private static void mapAclfLoadData(AclfLoad ld, AclfLoadBean ldb) {
		ldb.cirId = ld.getId();
		ldb.status = ld.isStatus() == true? 1 : 0;
		ldb.constIload = new ComplexBean(ld.getLoadCI().getReal(), ld.getLoadCI().getImaginary());
		ldb.constPload = new ComplexBean(ld.getLoadCP().getReal(), ld.getLoadCP().getImaginary());
		ldb.constZload = new ComplexBean(ld.getLoadCZ().getReal(), ld.getLoadCZ().getImaginary());			
	}
	
	private static void mapPsXfrData(PSXfrPControl tap, PsXfrTapControlBean tb) {
		tb.controlOnFromSide = tap.isControlOnFromSide();
		tb.controlType = tap.getFlowControlType()==AdjControlType.POINT_CONTROL? TapControlTypeBean.Point_Control:
			TapControlTypeBean.Range_Control;
		tb.desiredControlTarget = tap.getPSpecified();
		tb.lowerLimit = tap.getControlRange().getMin();
		tb.upperLimit = tap.getControlRange().getMax();
		tb.maxAngle = tap.getAngLimit().getMax();
		tb.minAngle = tap.getAngLimit().getMin();
		tb.measuredOnFromSide = tap.isMeteredOnFromSide();
		tb.maxTap = tap.getControlLimit().getMax();
		tb.minTap = tap.getControlLimit().getMin();
		tb.status = tap.isStatus() == true? 1: 0;		
	}

	private static void mapXfrData(TapControl tap, XfrTapControlBean tapBean) {
		tapBean.controlledBusId = tap.getVcBus().getId();
		if(tap.getControlType() == XfrTapControlType.BUS_VOLTAGE){
			tapBean.controlMode = TapControlModeBean.Bus_Voltage;
			tapBean.desiredControlTarget = tap.getVSpecified();
			
		}else{
			tapBean.controlMode = TapControlModeBean.Mva_Flow;
			tapBean.desiredControlTarget = tap.getMvarSpecified();
		}
		
		tapBean.controlOnFromSide = tap.isControlOnFromSide();
		tapBean.controlType = tap.getFlowControlType()== AdjControlType.POINT_CONTROL? TapControlTypeBean.Point_Control:
			TapControlTypeBean.Range_Control;
		tapBean.lowerLimit = tap.getControlRange().getMin();
		tapBean.upperLimit = tap.getControlRange().getMax();
		tapBean.maxTap = tap.getControlLimit().getMax();
		tapBean.minTap = tap.getControlLimit().getMin();
		tapBean.measuredOnFromSide = tap.isMeteredOnFromSide();
		tapBean.status = tap.isStatus() == true? 1: 0;
		tapBean.steps = tap.getTapSteps();
		tapBean.stepSize = tap.getTapStepSize();		
	}
	

	protected static Complex format(Complex x) {
		return new Complex(new Double(Number2String.toStr(x.getReal())).doubleValue(), 
				           new Double(Number2String.toStr(x.getImaginary())).doubleValue());
	}

	protected static double format(double x) {
		return new Double(Number2String.toStr(x)).doubleValue();
	}

	protected static double format2(double x) {
		return new Double(Number2String.toStr(x, "#0.0#")).doubleValue();
	}	
}
