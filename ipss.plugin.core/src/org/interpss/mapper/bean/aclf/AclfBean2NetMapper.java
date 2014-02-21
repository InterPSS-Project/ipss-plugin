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
import static org.interpss.mapper.odm.ODMUnitHelper.ToActivePowerUnit;
import static org.interpss.mapper.odm.ODMUnitHelper.ToAngleUnit;

import org.apache.commons.math3.complex.Complex;
import org.interpss.datamodel.bean.BaseBranchBean;
import org.interpss.datamodel.bean.aclf.AclfBranchBean;
import org.interpss.datamodel.bean.aclf.AclfBusBean;
import org.interpss.datamodel.bean.aclf.AclfNetBean;
import org.interpss.datamodel.bean.aclf.adj.AclfGenBean;
import org.interpss.datamodel.bean.aclf.adj.AclfLoadBean;
import org.interpss.datamodel.bean.aclf.adj.QBankBean;
import org.interpss.datamodel.bean.aclf.adj.SwitchShuntBean;
import org.interpss.datamodel.bean.aclf.adj.SwitchShuntBean.VarCompensatorControlModeBean;
import org.interpss.datamodel.bean.aclf.adj.TapControlBean;
import org.interpss.datamodel.bean.aclf.adj.TapControlBean.TapControlModeBean;
import org.interpss.datamodel.bean.aclf.adj.TapControlBean.TapControlTypeBean;
import org.interpss.numeric.datatype.LimitType;
import org.interpss.numeric.datatype.Unit.UnitType;

import com.interpss.CoreObjectFactory;
import com.interpss.SimuObjectFactory;
import com.interpss.common.datatype.UnitHelper;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.mapper.AbstractMapper;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoad;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adj.AdjControlType;
import com.interpss.core.aclf.adj.PSXfrPControl;
import com.interpss.core.aclf.adj.QBank;
import com.interpss.core.aclf.adj.SwitchedShunt;
import com.interpss.core.aclf.adj.TapControl;
import com.interpss.core.aclf.adj.VarCompensatorControlMode;
import com.interpss.core.aclf.adpter.AclfPQGenBus;
import com.interpss.core.aclf.adpter.AclfPSXformer;
import com.interpss.core.aclf.adpter.AclfPVGenBus;
import com.interpss.core.aclf.adpter.AclfSwingBus;
import com.interpss.core.aclf.adpter.AclfXformer;
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
		
		for (AclfBusBean busBean : netBean.bus_list) {
			mapBusBean(busBean, aclfNet);
		}

		for (AclfBranchBean branchBean : netBean.branch_list) {
			mapBranchBean(branchBean, aclfNet);
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
	private void mapBusBean(AclfBusBean busBean, AclfNetwork aclfNet) {
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
		bus.setArea(area);		
		Zone zone = CoreObjectFactory.createZone(busBean.zone, aclfNet);
		bus.setZone(zone);
		
		bus.setBaseVoltage(busBean.base_v*1000);
		
		if (busBean.gen_code != null) {
			if (busBean.gen_code==AclfBusBean.GenCode.PQ) {
				bus.setGenCode(AclfGenCode.GEN_PQ);
				AclfPQGenBus pqBus = bus.toPQBus();
				if(busBean.gen != null){					
					pqBus.setGen(busBean.gen.toComplex());
				}				    
			}
			else if (busBean.gen_code==AclfBusBean.GenCode.PV) {
				bus.setGenCode(AclfGenCode.GEN_PV);
				AclfPVGenBus pvBus = bus.toPVBus();
				if(busBean.gen != null)
				      pvBus.setGenP(busBean.gen.re);
				pvBus.setDesiredVoltMag(busBean.vDesired_mag);				
			}
			else if (busBean.gen_code==AclfBusBean.GenCode.Swing) {
				bus.setGenCode(AclfGenCode.SWING);
				AclfSwingBus swingBus = bus.toSwingBus();
				swingBus.setDesiredVoltMag(busBean.vDesired_mag);
				swingBus.setDesiredVoltAngDeg(busBean.vDesired_ang);
			}
			else {
				bus.setGenCode(AclfGenCode.NON_GEN);
				bus.setVoltageMag(busBean.v_mag);
				bus.setVoltageAng(Math.toRadians(busBean.v_ang));
			}
			
			// gen data
			for(AclfGenBean genBean : busBean.gen_list){
				String id = genBean.id;
				AclfGen gen = CoreObjectFactory.createAclfGen(id);
				gen.setStatus(genBean.status == 1? true: false);
				gen.setDesiredVoltMag(genBean.scheduledVol);
				gen.setGen(new Complex(genBean.pgen, genBean.qgen));
				gen.setPGenLimit(new LimitType(genBean.pmax,genBean.pmin));
				gen.setQGenLimit(new LimitType(genBean.qmax,genBean.qmin));
				gen.setRemoteVControlBusId(genBean.remoteVControlBusId);
				bus.getGenList().add(gen);
			}			
			
		}
		
		if (busBean.load_code != null) {
			bus.setLoadCode(busBean.load_code==AclfBusBean.LoadCode.ConstP? AclfLoadCode.CONST_P :
				(busBean.load_code==AclfBusBean.LoadCode.ConstI? AclfLoadCode.CONST_I : 
					(busBean.load_code==AclfBusBean.LoadCode.ConstZ? AclfLoadCode.CONST_Z : 
						AclfLoadCode.NON_LOAD)));
			if (busBean.load != null) {
				bus.setLoadPQ(new Complex(busBean.load.re, busBean.load.im));
			}
		}
		
		// load data
		for (AclfLoadBean loadbean : busBean.load_list) {
			String id = loadbean.id;
			AclfLoad ld = CoreObjectFactory.createAclfLoad(id);
			ld.setStatus(loadbean.status == 1 ? true : false);
			AclfLoadCode code = AclfLoadCode.NON_LOAD;
			if (loadbean.constPload != null) {
				code = code == AclfLoadCode.NON_LOAD ? AclfLoadCode.CONST_P
						: AclfLoadCode.ZIP;
				ld.setLoadCP(new Complex(loadbean.constPload.re,
						loadbean.constPload.im));
			}
			if (loadbean.constIload != null) {
				code = code == AclfLoadCode.NON_LOAD ? AclfLoadCode.CONST_I
						: AclfLoadCode.ZIP;
				ld.setLoadCI(new Complex(loadbean.constIload.re,
						loadbean.constIload.im));
			}
			if (loadbean.constZload != null) {
				code = code == AclfLoadCode.NON_LOAD ? AclfLoadCode.CONST_Z
						: AclfLoadCode.ZIP;
				ld.setLoadCZ(new Complex(loadbean.constZload.re,
						loadbean.constZload.im));
			}
			ld.setCode(code);
			bus.getLoadList().add(ld);
		}
		
		if(busBean.shunt != null){
			bus.setShuntY(new Complex(busBean.shunt.re, busBean.shunt.im));
		}		
		// switch shunt
		for(SwitchShuntBean ssb: busBean.switchShunt_List){
			try {
				SwitchedShunt ss = CoreObjectFactory.createSwitchedShunt(bus);
				ss.setBInit(ssb.bInit);
				VarCompensatorControlMode mode = ssb.controlMode == VarCompensatorControlModeBean.Continuous?VarCompensatorControlMode.CONTINUOUS:
					ssb.controlMode == VarCompensatorControlModeBean.Discrete?VarCompensatorControlMode.DISCRETE:
						VarCompensatorControlMode.FIXED;
				ss.setControlMode(mode);
				ss.setDesiredVoltageRange(new LimitType(ssb.vmax, ssb.vmin));
				for(QBankBean qbb: ssb.varBankList){
					QBank qb = CoreObjectFactory.createQBank(ss);
					qb.setSteps(qbb.step);
					qb.setUnitQMvar(qbb.UnitQMvar);
				}				
				ss.setRemoteBus(aclfNet.getBus(ssb.remoteBusId));
			} catch (InterpssException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
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
	private void mapBranchBean(AclfBranchBean branchBean, AclfNetwork aclfNet) {
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
			branch.setHShuntY(new Complex(0.0, branchBean.shunt_y.im*0.5));
		if (branch.getBranchCode() == AclfBranchCode.XFORMER ||
				branch.getBranchCode() == AclfBranchCode.PS_XFORMER) {
			setXfrData(branchBean, branch, aclfNet);
		}		
		// rating		
		branch.setRatingMva1(branchBean.mvaRatingA);
		branch.setRatingMva2(branchBean.mvaRatingB);
		branch.setRatingMva3(branchBean.mvaRatingC);
	}	
	
	private void setXfrData(AclfBranchBean branchBean, AclfBranch branch,AclfNetwork aclfNet){		
		// control/adjustment
		if(branchBean.tapControlBean != null){
			TapControlBean tcb = branchBean.tapControlBean;
			try{
				if(tcb.controlMode == TapControlModeBean.Bus_Voltage){
					AclfXformer xfr = branch.toXfr();
					if (branchBean.ratio != null) {
						xfr.setFromTurnRatio(branchBean.ratio.f);
						xfr.setToTurnRatio(branchBean.ratio.t);
					}
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
					AclfXformer xfr = branch.toXfr();
					if (branchBean.ratio != null) {
						xfr.setFromTurnRatio(branchBean.ratio.f);
						xfr.setToTurnRatio(branchBean.ratio.t);
					}
					//TODO add Range Control
					TapControl tap = CoreObjectFactory.createTapVControlMvarFlow(branch, 
							            AdjControlType.POINT_CONTROL);
					tap.setMeteredOnFromSide(tcb.measuredOnFromSide);
					tap.setMvarSpecified(tcb.desiredControlTarget);
					tap.setTurnRatioLimit(new LimitType(tcb.maxTap, tcb.minTap));
					tap.setControlOnFromSide(tcb.controlOnFromSide);
					tap.setTapSteps(tcb.steps);
					tap.setTapStepSize(tcb.stepSize);
				}else{// phase shifter control
					AclfPSXformer psXfr = branch.toPSXfr();
					if(branchBean.ang != null){
						psXfr.setFromAngle(branchBean.ang.f);
						psXfr.setToAngle(branchBean.ang.t);
					}
					if(tcb.controlType == TapControlTypeBean.Point_Control){
						PSXfrPControl psxfr = CoreObjectFactory.createPSXfrPControl(branch, AdjControlType.POINT_CONTROL);
						psxfr.setStatus(tcb.status == 1? true : false);
						psxfr.setPSpecified(tcb.desiredControlTarget);
						psxfr.setAngLimit(new LimitType(tcb.maxTap, tcb.minTap));
						psxfr.setControlOnFromSide(tcb.controlOnFromSide);
						psxfr.setMeteredOnFromSide(tcb.measuredOnFromSide);
						
						
					}else{ // range control
						PSXfrPControl psxfr = CoreObjectFactory.createPSXfrPControl(branch, AdjControlType.RANGE_CONTROL);
						psxfr.setStatus(tcb.status == 1? true : false);
						psxfr.setControlRange(new LimitType(tcb.upperLimit, tcb.lowerLimit));
						psxfr.setPSpecified(tcb.desiredControlTarget);
						psxfr.setAngLimit(new LimitType(tcb.maxTap, tcb.minTap));
						psxfr.setControlOnFromSide(tcb.controlOnFromSide);
						psxfr.setMeteredOnFromSide(tcb.measuredOnFromSide);
					}
					
					
				}
			} catch (InterpssException e) {
				ipssLogger.severe("Error in mapping Xfr tap control data, " + e.toString());
			}
			
		}
	}
}