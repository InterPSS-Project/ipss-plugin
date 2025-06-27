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

package org.interpss.dep.datamodel.mapper.base;

import org.apache.commons.math3.complex.Complex;
import org.interpss.dep.datamodel.bean.aclf.AclfBranchBean;
import org.interpss.dep.datamodel.bean.aclf.AclfBusBean;
import org.interpss.dep.datamodel.bean.aclf.adj.BaseTapControlBean.TapControlModeBean;
import org.interpss.dep.datamodel.bean.aclf.adj.BaseTapControlBean.TapControlTypeBean;
import org.interpss.dep.datamodel.bean.aclf.adj.PsXfrTapControlBean;
import org.interpss.dep.datamodel.bean.aclf.adj.QBankBean;
import org.interpss.dep.datamodel.bean.aclf.adj.SwitchShuntBean;
import org.interpss.dep.datamodel.bean.aclf.adj.SwitchShuntBean.VarCompensatorControlModeBean;
import org.interpss.dep.datamodel.bean.aclf.adj.XfrTapControlBean;
import org.interpss.dep.datamodel.bean.aclf.ext.AclfBranchResultBean;
import org.interpss.dep.datamodel.bean.aclf.ext.AclfBusResultBean;
import org.interpss.dep.datamodel.bean.base.BaseBranchBean;
import org.interpss.dep.datamodel.bean.base.BaseJSONUtilBean;
import org.interpss.dep.datamodel.bean.datatype.BranchValueBean;
import org.interpss.dep.datamodel.bean.datatype.ComplexValueBean;
import org.interpss.dep.datamodel.bean.datatype.LimitValueBean;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.Number2String;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.ShuntCompensator;
import com.interpss.core.aclf.adj.AdjustControlType;
import com.interpss.core.aclf.adj.BusBranchControlType;
import com.interpss.core.aclf.adj.PSXfrPControl;
import com.interpss.core.aclf.adj.SwitchedShunt;
import com.interpss.core.aclf.adj.TapControl;
import com.interpss.core.aclf.adj.VarCompensationMode;
import com.interpss.core.aclf.adpter.AclfPSXformerAdapter;
import com.interpss.core.aclf.adpter.AclfXformerAdapter;

/**
 * Util functions for mapping AclfNetwork object to BaseNetBean (AclfNetBean and AclfNetResultBean)
 * 
 * 
 */
public class AclfNet2BeanUtilFunc {
	/**
	 * map an AclfBus object to an AclfNet2BeanHelper. 
	 * 
	 * @param bus
	 * @param bean
	 */
	public static void mapAclfBusResult(AclfBus bus, AclfBusBean<? extends BaseJSONUtilBean> bean) {
		AclfBusResultBean rbean = (AclfBusResultBean)bean.extension;
		
		Complex gen = bus.calNetGenResults();
		rbean.lfGenResult = new ComplexValueBean(format(gen));
		
		Complex load = bus.calNetLoadResults();
		rbean.lfLoadResult = new ComplexValueBean(format(load));
	}
	
	/**
	 * map an AclfBus object to an AclfBusBean 
	 * 
	 * @param bus
	 * @param bean
	 */
	public static void mapAclfBus(AclfBus bus, AclfBusBean<? extends BaseJSONUtilBean> bean) {
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
		//bean.vmax = format(bus.getVLimit().getMax()) == 0? bean.vmax : format(bus.getVLimit().getMax());
		//bean.vmin = format(bus.getVLimit().getMin()) == 0? bean.vmin : format(bus.getVLimit().getMin());
		
		bean.vLimit = new LimitValueBean(format(bus.getVLimit().getMax()), format(bus.getVLimit().getMin()));

		bean.gen_code = bus.isGenPQ()? AclfBusBean.GenCode.PQ :
			(bus.isGenPV() ? AclfBusBean.GenCode.PV : 
				(bus.isSwing()? AclfBusBean.GenCode.Swing : 
					AclfBusBean.GenCode.NonGen));
		/*		
		Complex gen = bus.calNetGenResults();
		bean.lfGenResult = new ComplexValueBean(format(gen));
		*/
		double genp = bus.getGenP();
		double genq = bus.getGenQ();
		bean.gen = new ComplexValueBean(format(new Complex(genp, genq)));		
		
		if(bus.getPGenLimit() != null){
			bean.pLimit = new LimitValueBean(bus.getPGenLimit().getMax(), bus.getPGenLimit().getMin());
		}		
		if(bus.getQGenLimit() != null){
			bean.qLimit = new LimitValueBean(bus.getQGenLimit().getMax(), bus.getQGenLimit().getMin());
		}
		if (bus.isRemoteQBus()){
			if (bus.getRemoteQBus() != null)
				if(bus.getRemoteQBus().getRemoteBus() != null){
					String remoteBusId = bus.getRemoteQBus().getRemoteBus().getId();
					bean.remoteVControlBusId = remoteBusId;
				}
		}				

		bean.load_code = bus.isConstPLoad() ? AclfBusBean.LoadCode.ConstP :
			(bus.isConstZLoad() ? AclfBusBean.LoadCode.ConstZ : 
				(bus.isConstILoad() ? AclfBusBean.LoadCode.ConstI : 
					AclfBusBean.LoadCode.NonLoad));
		/*
		Complex load = bus.calNetLoadResults();
		bean.lfLoadResult = new ComplexValueBean(format(load));
		*/
		double loadp = bus.getLoadP();
		double loadq = bus.getLoadQ();
		bean.load = new ComplexValueBean(format(new Complex(loadp, loadq)));
				
		Complex sh = bus.getShuntY();
		bean.shunt = new ComplexValueBean(format(sh));
		
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
		
		// map switched shunt data
		if (bus.isSwitchedShunt()) {
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
	public static void mapAclfBranch(AclfBranch branch, AclfBranchBean<? extends BaseJSONUtilBean> bean) {
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
		bean.z = new ComplexValueBean(z);
		bean.shunt_y = new ComplexValueBean(format(new Complex(0, 0)));	
		bean.turnRatio = new BranchValueBean(1.0,1.0);		
		if (branch.getBranchCode() == AclfBranchCode.LINE ||
				branch.getBranchCode() == AclfBranchCode.ZBR) {
			if (branch.getHShuntY() != null)				
				bean.shunt_y = new ComplexValueBean(format(new Complex(branch.getHShuntY().getReal()*2,
						branch.getHShuntY().getImaginary()*2)));				
				
		}
		else if (branch.getBranchCode() == AclfBranchCode.XFORMER ){
			AclfXformerAdapter xfr = branch.toXfr();			
			bean.turnRatio.f = xfr.getFromTurnRatio();
			bean.turnRatio.t = xfr.getToTurnRatio();	
			bean.shunt_y = new ComplexValueBean(format(new Complex(branch.getFromShuntY().getReal()*2,
					branch.getFromShuntY().getImaginary()*2)));
			XfrTapControlBean tapBean = new XfrTapControlBean();
			bean.xfrTapControl = tapBean;
			if(branch.getTapControl() != null){
				TapControl tap = branch.getTapControl();				
				mapXfrData(tap, tapBean);
			}
		}else if (	branch.getBranchCode() == AclfBranchCode.PS_XFORMER){
			AclfPSXformerAdapter xfr = branch.toPSXfr();			
			bean.turnRatio.f = xfr.getFromTurnRatio();
			bean.turnRatio.t = xfr.getToTurnRatio();	
			bean.shunt_y = new ComplexValueBean(format(new Complex(branch.getFromShuntY().getReal()*2,
					branch.getFromShuntY().getImaginary()*2)));
			bean.shiftAng.f = branch.getFromPSXfrAngle();
			bean.shiftAng.t = branch.getToPSXfrAngle();
			PsXfrTapControlBean tb = new PsXfrTapControlBean();
			bean.psXfrTapControl = tb;
			if(branch.getPSXfrPControl() != null){
				PSXfrPControl tap = branch.getPSXfrPControl();					
				mapPsXfrData(tap, tb);
			}
		}
		
		bean.mvaRatingA = branch.getRatingMva1();
		bean.mvaRatingB = branch.getRatingMva2();
		bean.mvaRatingC = branch.getRatingMva3();			
	}	
	
	public static void mapAclfBranchResult(AclfBranch branch, AclfBranchBean<? extends BaseJSONUtilBean> bean) {
		AclfBranchResultBean rbean = (AclfBranchResultBean)bean.extension;
		
		Complex flow = branch.powerFrom2To();
		rbean.flow_f2t = new ComplexValueBean(AclfNet2BeanUtilFunc.format(flow));

		flow = branch.powerTo2From();
		rbean.flow_t2f = new ComplexValueBean(AclfNet2BeanUtilFunc.format(flow));
		
		Complex loss = branch.loss();
		rbean.loss = new ComplexValueBean(AclfNet2BeanUtilFunc.format(loss));
		
		rbean.cur = AclfNet2BeanUtilFunc.format2(branch.current(UnitType.Amp));
	}	
	
	private static void mapSwitchShuntData(SwitchedShunt ss, SwitchShuntBean<? extends BaseJSONUtilBean> ssb) {
		ssb.bInit = ss.getBInit();
		ssb.controlMode = ss.getControlMode() == VarCompensationMode.CONTINUOUS? VarCompensatorControlModeBean.Continuous:
			ss.getControlMode() == VarCompensationMode.DISCRETE? VarCompensatorControlModeBean.Discrete:
				VarCompensatorControlModeBean.Fixed;
		if(ss.getRemoteBus() != null)
			ssb.remoteBusId = ss.getRemoteBus().getId();
		if(ss.getDesiredControlRange() != null){
			ssb.vmax = ss.getDesiredControlRange().getMax();
			ssb.vmin = ss.getDesiredControlRange().getMin();
		}
		
		if(ss.getQLimit() != null){ 
			ssb.qmax = ss.getQLimit().getMax();
			ssb.qmin = ss.getQLimit().getMin();
		}
		for(ShuntCompensator qb: ss.getShuntCompensatorList()){
			QBankBean qbb =  new QBankBean();
			qbb.step = qb.getSteps();
			qbb.UnitQMvar = qb.getUnitQMvar();
			ssb.varBankList.add(qbb);
		}
		ssb.vSpecified = ss.getVSpecified();
		
	}	

	
	private static void mapPsXfrData(PSXfrPControl tap, PsXfrTapControlBean<? extends BaseJSONUtilBean> tb) {
		tb.controlOnFromSide = tap.isControlOnFromSide();
		tb.controlType = tap.getAdjControlType()==AdjustControlType.POINT_CONTROL? TapControlTypeBean.Point_Control:
			TapControlTypeBean.Range_Control;
		tb.desiredControlTarget = tap.getPSpecified();
		tb.flowFrom2To = tap.isFlowFrom2To();
		if(tap.getDesiredControlRange() != null){
			tb.lowerLimit = tap.getDesiredControlRange().getMin();
			tb.upperLimit = tap.getDesiredControlRange().getMax();
		}
		if(tap.getAngLimit() != null){
			tb.maxAngle = Math.toDegrees(tap.getAngLimit().getMax());
			tb.minAngle = Math.toDegrees(tap.getAngLimit().getMin());
		}
		if(tap.getControlLimit() != null){
			tb.maxTap = tap.getControlLimit().getMax();
			tb.minTap = tap.getControlLimit().getMin();
		}
		tb.measuredOnFromSide = tap.isMeteredOnFromSide();		
		tb.status = tap.isStatus() == true? 1: 0;		
	}

	private static void mapXfrData(TapControl tap, XfrTapControlBean<? extends BaseJSONUtilBean> tapBean) {
		tapBean.controlledBusId = tap.getVcBus().getId();
		if(tap.getTapControlType() == BusBranchControlType.BUS_VOLTAGE){
			tapBean.controlMode = TapControlModeBean.Bus_Voltage;
			tapBean.desiredControlTarget = tap.getVSpecified();
			
		}else{
			tapBean.controlMode = TapControlModeBean.Mva_Flow;
			tapBean.desiredControlTarget = tap.getMvarSpecified();
		}
		
		tapBean.controlOnFromSide = tap.isControlOnFromSide();
		tapBean.controlType = tap.getAdjControlType()== AdjustControlType.POINT_CONTROL? TapControlTypeBean.Point_Control:
			TapControlTypeBean.Range_Control;
		if(tap.getDesiredControlRange() != null){
			tapBean.lowerLimit = tap.getDesiredControlRange().getMin();
			tapBean.upperLimit = tap.getDesiredControlRange().getMax();
		}
		if(tap.getControlLimit() != null){
			tapBean.maxTap = tap.getControlLimit().getMax();
			tapBean.minTap = tap.getControlLimit().getMin();
		}		
		tapBean.measuredOnFromSide = tap.isMeteredOnFromSide();
		tapBean.status = tap.isStatus() == true? 1: 0;
		tapBean.steps = tap.getTapSteps();
		tapBean.stepSize = tap.getTapStepSize();		
	}
	

	public static Complex format(Complex x) {
		return new Complex(new Double(Number2String.toStr(x.getReal())).doubleValue(), 
				           new Double(Number2String.toStr(x.getImaginary())).doubleValue());
	}

	public static double format(double x) {
		return new Double(Number2String.toStr(x)).doubleValue();
	}

	public static double format2(double x) {
		return new Double(Number2String.toStr(x, "#0.0#")).doubleValue();
	}	
}

