/*
 * @(#) AclfBean2NetMapper.java   
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

import static com.interpss.common.util.IpssLogger.ipssLogger;

import org.apache.commons.math3.complex.Complex;
import org.interpss.datamodel.bean.BaseBranchBean;
import org.interpss.datamodel.bean.aclf.AclfBranchBean;
import org.interpss.datamodel.bean.aclf.AclfBusBean;
import org.interpss.datamodel.bean.aclf.AclfNetBean;
import org.interpss.datamodel.bean.aclf.adj.BaseTapControlBean.TapControlModeBean;
import org.interpss.datamodel.bean.aclf.adj.BaseTapControlBean.TapControlTypeBean;
import org.interpss.datamodel.bean.aclf.adj.PsXfrTapControlBean;
import org.interpss.datamodel.bean.aclf.adj.QBankBean;
import org.interpss.datamodel.bean.aclf.adj.SwitchShuntBean;
import org.interpss.datamodel.bean.aclf.adj.SwitchShuntBean.VarCompensatorControlModeBean;
import org.interpss.datamodel.bean.aclf.adj.XfrTapControlBean;
import org.interpss.numeric.datatype.LimitType;

import com.interpss.CoreObjectFactory;
import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.exp.InterpssRuntimeException;
import com.interpss.common.mapper.AbstractMapper;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adj.AdjControlType;
import com.interpss.core.aclf.adj.PSXfrPControl;
import com.interpss.core.aclf.adj.QBank;
import com.interpss.core.aclf.adj.RemoteQBus;
import com.interpss.core.aclf.adj.RemoteQControlType;
import com.interpss.core.aclf.adj.SwitchedShunt;
import com.interpss.core.aclf.adj.TapControl;
import com.interpss.core.aclf.adj.VarCompensationMode;
import com.interpss.core.aclf.adpter.AclfPVGenBus;
import com.interpss.core.aclf.adpter.AclfSwingBus;
import com.interpss.core.net.Area;
import com.interpss.core.net.Bus;
import com.interpss.core.net.Zone;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;


/**
 * Mapper to map an AclfNetBean object to an AclfNetwork (SimuContext) object
 * 
 * @author mzhou
 *
 */
public class AclfBean2NetMapper extends AbstractMapper<AclfNetBean, SimuContext> {
	/**
	 * constructor
	 */
	public AclfBean2NetMapper() {
	}
	
	/**
	 * map info store in the BAclfNetBean object into simuCtx object
	 * 
	 * @param netBean AclfNetBean object
	 * @return SimuContext object
	 */
	@Override public SimuContext map2Model(AclfNetBean netBean) throws InterpssException {
		final SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.NOT_DEFINED);
		if (this.map2Model(netBean, simuCtx)) {
  	  		simuCtx.setId("InterPSS_SimuCtx");
  	  		simuCtx.setName("InterPSS SimuContext Object");
  	  		simuCtx.setDesc("InterPSS SimuContext Object - created from JSON Bean model");
  			return simuCtx;
		}	
		throw new InterpssException("Error in map ODM model to SimuContext object");
	}	
	
	/**
	 * map the AclfNetBean object into simuCtx object
	 * 
	 * @param netBean an AclfNetBean object, representing a aclf base network
	 * @param simuCtx
	 */
	@Override public boolean map2Model(AclfNetBean netBean, SimuContext simuCtx) {
		boolean noError = true;
		simuCtx.setNetType(SimuCtxType.ACLF_NETWORK);
		AclfNetwork aclfNet = CoreObjectFactory.createAclfNetwork();
			
		simuCtx.setAclfNet(aclfNet);
		
		aclfNet.setBaseKva(netBean.base_kva);
		
		try {
			for (AclfBusBean busBean : netBean.getBusBeanList()) {
				mapBusBean(busBean, aclfNet);
			}

			for (AclfBranchBean branchBean : netBean.getBranchBeanList()) {
				mapBranchBean(branchBean, aclfNet);
			}
		} catch (InterpssException e) {
			IpssLogger.ipssLogger.severe(e.toString());
			noError = false;	
		}

		return noError;
	}
	
	/**
	 * map the AclfBusBean object to an AclfBus object and added the AclfBus object 
	 * into the AclfNet
	 * 
	 * @param busBean AclfBusBean object to be mapped
	 * @param aclfNet AclfNetwork object
	 */
	private void mapBusBean(AclfBusBean busBean, AclfNetwork aclfNet) throws InterpssException {
		AclfBus bus = CoreObjectFactory.createAclfBus(busBean.id, aclfNet);
		bus.setNumber(busBean.number);
		
		bus.setName(busBean.name);
		
		int status = busBean.status;
		if(status ==0)
			bus.setStatus(false);
		else
			bus.setStatus(true);
		
		bus.setVLimit(new LimitType(busBean.vmax, busBean.vmin));
		
		Area area = CoreObjectFactory.createArea(busBean.area, aclfNet);
		area.setName(busBean.areaName);
		bus.setArea(area);			
		Zone zone = CoreObjectFactory.createZone(busBean.zone, aclfNet);
		zone.setName(busBean.zoneName);
		bus.setZone(zone);
		
		bus.setBaseVoltage(busBean.base_v*1000);
		
		bus.setVoltage(busBean.v_mag, Math.toRadians(busBean.v_ang));
		if(busBean.gen != null){
			bus.setGenP(busBean.gen.re);
			bus.setGenQ(busBean.gen.im);
		}			
		
		if (busBean.gen_code != null) {
			if (busBean.gen_code==AclfBusBean.GenCode.PQ) {
				bus.setGenCode(AclfGenCode.GEN_PQ);
				/*AclfPQGenBus pqBus = bus.toPQBus();
				if(busBean.gen != null){					
					pqBus.setGen(busBean.gen.toComplex());
				}*/
								    
			}
			else if (busBean.gen_code==AclfBusBean.GenCode.PV) {
				bus.setGenCode(AclfGenCode.GEN_PV);
				AclfPVGenBus pvBus = bus.toPVBus();
				/*if(busBean.gen != null)
				      pvBus.setGenP(busBean.gen.re);*/
				
				pvBus.setDesiredVoltMag(busBean.vDesired_mag);				
			}
			else if (busBean.gen_code==AclfBusBean.GenCode.Swing) {
				bus.setGenCode(AclfGenCode.SWING);
				AclfSwingBus swingBus = bus.toSwingBus();
				swingBus.setDesiredVoltMag(busBean.vDesired_mag);
				swingBus.setDesiredVoltAngDeg(Math.toRadians(busBean.vDesired_ang));
			}
			else {
				bus.setGenCode(AclfGenCode.NON_GEN);
				/*bus.setVoltageMag(busBean.v_mag);
				bus.setVoltageAng(Math.toRadians(busBean.v_ang));*/
			}			
			
			//bus.setDesiredVoltMag(busBean.vDesired_mag);
			//bus.setDesiredVoltAng(busBean.vDesired_ang);
			
			bus.setPGenLimit(new LimitType(busBean.pmax,busBean.pmin));
			bus.setQGenLimit(new LimitType(busBean.qmax,busBean.qmin));
			
			String remoteBusId = busBean.remoteVControlBusId;
			if( !remoteBusId.equals("")){
				RemoteQBus reQBus;
				try {
					reQBus = CoreObjectFactory.createRemoteQBus(
							bus, RemoteQControlType.BUS_VOLTAGE, remoteBusId);
					reQBus.setAccFactor(0.5);
				} catch (InterpssException e) {
					throw new InterpssRuntimeException(e.toString());
				}
				
			}
			
			
		}
		
		if (busBean.load_code != null) {
			bus.setLoadCode(busBean.load_code==AclfBusBean.LoadCode.ConstP? AclfLoadCode.CONST_P :
				(busBean.load_code==AclfBusBean.LoadCode.ConstI? AclfLoadCode.CONST_I : 
					(busBean.load_code==AclfBusBean.LoadCode.ConstZ? AclfLoadCode.CONST_Z : 
						AclfLoadCode.NON_LOAD)));
			if(busBean.load != null)
				bus.setLoadPQ(new Complex(busBean.load.re, busBean.load.im));
		}	
		
		
		if(busBean.shunt != null){
			bus.setShuntY(new Complex(busBean.shunt.re, busBean.shunt.im));
		}		
		// switch shunt
		
		if (busBean.switchShunt != null){
			SwitchShuntBean ssb = busBean.switchShunt;
			
			try {
				SwitchedShunt ss = CoreObjectFactory.createSwitchedShunt(bus);
				ss.setVSpecified(ssb.vSpecified);
				ss.setBInit(ssb.bInit);
				VarCompensationMode mode = ssb.controlMode == VarCompensatorControlModeBean.Continuous?VarCompensationMode.CONTINUOUS:
					ssb.controlMode == VarCompensatorControlModeBean.Discrete?VarCompensationMode.DISCRETE:
						VarCompensationMode.FIXED;
				ss.setControlMode(mode);
				ss.setDesiredVoltageRange(new LimitType(ssb.vmax, ssb.vmin));
				ss.setQLimit(new LimitType(ssb.qmax, ssb.qmin));
				for(QBankBean qbb: ssb.varBankList){
					QBank qb = CoreObjectFactory.createQBank(ss);
					qb.setSteps(qbb.step);
					qb.setUnitQMvar(qbb.UnitQMvar);
				}				
				ss.setRemoteBus(aclfNet.getBus(ssb.remoteBusId));
			} catch (InterpssException e) {
				throw new InterpssRuntimeException(e.toString());
			}
			
		}
	}
	
	/**
	 * map the AclfBranchBean object to an AclfBranch object and added the AclfBranch object 
	 * into the AclfNet
	 * 
	 * @param branchBean AclfBranchBean object to be mapped
	 * @param aclfNet AclfNetwork object
	 */
	private void mapBranchBean(AclfBranchBean branchBean, AclfNetwork aclfNet) throws InterpssException {
		AclfBranch branch = CoreObjectFactory.createAclfBranch();
		branch.setId(branchBean.id);
		branch.setName(branchBean.name);
		aclfNet.addBranch(branch, branchBean.f_id, branchBean.t_id, branchBean.cir_id);
		Bus fBus = aclfNet.getBus(branchBean.f_id);
		Bus tBus = aclfNet.getBus(branchBean.t_id);
		branch.setFromBus(fBus);
		branch.setToBus(tBus);		
		
		branch.setStatus(true);
		if(branchBean.status == 0)
			branch.setStatus(false);
		
		branch.setBranchCode(branchBean.bra_code == BaseBranchBean.BranchCode.Line? AclfBranchCode.LINE :
			(branchBean.bra_code == BaseBranchBean.BranchCode.Xfr? AclfBranchCode.XFORMER :
				(branchBean.bra_code == BaseBranchBean.BranchCode.ZBR? AclfBranchCode.ZBR: 
					AclfBranchCode.PS_XFORMER)));
		if (branchBean.z != null)
			branch.setZ(branchBean.z.toComplex());
		if (branchBean.shunt_y != null)
			branch.setHShuntY(new Complex(branchBean.shunt_y.re*0.5, branchBean.shunt_y.im*0.5));
		if (branchBean.ratio != null) {
			branch.setFromTurnRatio(branchBean.ratio.f);
			branch.setToTurnRatio(branchBean.ratio.t);
		}
		if (branchBean.ang != null){
			branch.setFromPSXfrAngle(branchBean.ang.f);
			branch.setToPSXfrAngle(branchBean.ang.t);
		}
		if (branch.getBranchCode() == AclfBranchCode.XFORMER ) {
			if (branchBean.shunt_y != null)
				branch.setFromShuntY(new Complex(branchBean.shunt_y.re*0.5, branchBean.shunt_y.im*0.5) );
			setXfrData(branchBean, branch, aclfNet);
		}else if (branch.getBranchCode() == AclfBranchCode.PS_XFORMER) {
			if (branchBean.shunt_y != null)
				branch.setFromShuntY(new Complex(branchBean.shunt_y.re*0.5, branchBean.shunt_y.im*0.5) );
			setPsXfrData(branchBean, branch, aclfNet);
		}
		// rating		
		branch.setRatingMva1(branchBean.mvaRatingA);
		branch.setRatingMva2(branchBean.mvaRatingB);
		branch.setRatingMva3(branchBean.mvaRatingC);
	}	
	
	private void setXfrData(AclfBranchBean branchBean, AclfBranch branch,AclfNetwork aclfNet){		
		// control/adjustment
		if(branchBean.xfrTapControl != null){
			XfrTapControlBean tcb = branchBean.xfrTapControl;
			try{
				if(tcb.controlMode == TapControlModeBean.Bus_Voltage){					
					TapControl tap = null;
					if(tcb.controlType == TapControlTypeBean.Point_Control){
						tap = CoreObjectFactory.createTapVControlBusVoltage(branch, 
					            AdjControlType.POINT_CONTROL, aclfNet, tcb.controlledBusId);
						tap.setVSpecified(tcb.desiredControlTarget);
					}else{ // range control
						tap=CoreObjectFactory.createTapVControlBusVoltage(branch, 
					            AdjControlType.RANGE_CONTROL, aclfNet, tcb.controlledBusId);
							tap.setControlRange(new LimitType(tcb.upperLimit, tcb.lowerLimit));
					}
					tap.setStatus(tcb.status == 1? true : false);
					AclfBus vcBus = aclfNet.getBus(tcb.controlledBusId);				
					tap.setVcBusOnFromSide(branch.isFromBus(vcBus));
					tap.setControlOnFromSide(tcb.controlOnFromSide);
					tap.setTurnRatioLimit(new LimitType(tcb.maxTap, tcb.minTap));
					tap.setTapSteps(tcb.steps);
					tap.setTapStepSize(tcb.stepSize);
					
				}else if(tcb.controlMode == TapControlModeBean.Mva_Flow){					
					//TODO add Range Control
					TapControl tap = CoreObjectFactory.createTapVControlMvarFlow(branch, 
							            AdjControlType.POINT_CONTROL);
					tap.setMeteredOnFromSide(tcb.measuredOnFromSide);
					tap.setMvarSpecified(tcb.desiredControlTarget);
					tap.setTurnRatioLimit(new LimitType(tcb.maxTap, tcb.minTap));
					tap.setControlOnFromSide(tcb.controlOnFromSide);
					tap.setTapSteps(tcb.steps);
					tap.setTapStepSize(tcb.stepSize);
				}
			} catch (InterpssException e) {
				ipssLogger.severe("Error in mapping Xfr tap control data, " + e.toString());
			}
			
		}
	}
	
	private void setPsXfrData(AclfBranchBean branchBean, AclfBranch branch,AclfNetwork aclfNet){		
		// control/adjustment
		if(branchBean.psXfrTapControl != null){
			PsXfrTapControlBean tcb = branchBean.psXfrTapControl;			
			try{				
				if(tcb.controlType == TapControlTypeBean.Point_Control){
					PSXfrPControl psxfr = CoreObjectFactory.createPSXfrPControl(branch, AdjControlType.POINT_CONTROL);
					psxfr.setStatus(tcb.status == 1? true : false);
					psxfr.setPSpecified(tcb.desiredControlTarget);
					psxfr.setAngLimit(new LimitType(Math.toRadians(tcb.maxAngle), Math.toRadians(tcb.minAngle)));
					psxfr.setControlOnFromSide(tcb.controlOnFromSide);
					psxfr.setMeteredOnFromSide(tcb.measuredOnFromSide);
					psxfr.setFlowFrom2To(tcb.flowFrom2To);
					
				}else if (tcb.controlType == TapControlTypeBean.Range_Control ){ // range control
					PSXfrPControl psxfr = CoreObjectFactory.createPSXfrPControl(branch, AdjControlType.RANGE_CONTROL);
					psxfr.setStatus(tcb.status == 1? true : false);
					psxfr.setControlRange(new LimitType(tcb.upperLimit, tcb.lowerLimit));
					psxfr.setPSpecified(tcb.desiredControlTarget);
					psxfr.setAngLimit(new LimitType(Math.toRadians(tcb.maxAngle), Math.toRadians(tcb.minAngle)));
					psxfr.setControlOnFromSide(tcb.controlOnFromSide);
					psxfr.setMeteredOnFromSide(tcb.measuredOnFromSide);
					psxfr.setFlowFrom2To(tcb.flowFrom2To);
				}
			} catch (InterpssException e) {
				ipssLogger.severe("Error in mapping PsXfr tap control data, " + e.toString());
			}
			
		}
	}
}