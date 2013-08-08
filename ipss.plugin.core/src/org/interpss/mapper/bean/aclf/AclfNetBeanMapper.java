/*
 * @(#) AclfNetBeanMapper.java   
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
import org.interpss.datamodel.bean.BaseBusBean;
import org.interpss.datamodel.bean.BaseNetBean;
import org.interpss.datamodel.bean.aclf.AclfBranchBean;
import org.interpss.datamodel.bean.aclf.AclfBusBean;
import org.interpss.datamodel.bean.aclf.AclfNetBean;
import org.interpss.numeric.datatype.LimitType;

import com.interpss.CoreObjectFactory;
import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.mapper.AbstractMapper;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfPQGenBus;
import com.interpss.core.aclf.adpter.AclfPVGenBus;
import com.interpss.core.aclf.adpter.AclfSwingBus;
import com.interpss.core.aclf.adpter.AclfXformer;
import com.interpss.core.net.Area;
import com.interpss.core.net.Bus;
import com.interpss.core.net.Zone;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;


public class AclfNetBeanMapper extends AbstractMapper<AclfNetBean, SimuContext> {
	/**
	 * constructor
	 */
	public AclfNetBeanMapper() {
	}
	
	/**
	 * map into store in the BaseNetBean object into simuCtx object
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
				pvBus.setVoltMag(busBean.v_mag);
			}
			else {
				bus.setGenCode(AclfGenCode.SWING);
				AclfSwingBus swingBus = bus.toSwingBus();
				swingBus.setVoltMag(busBean.v_mag);
				swingBus.setVoltAngDeg(busBean.v_ang);
			}
			
		}
		
		if (busBean.load_code != null) {
			bus.setLoadCode(busBean.load_code==AclfBusBean.LoadCode.ConstP? AclfLoadCode.CONST_P :
				(busBean.load_code==AclfBusBean.LoadCode.ConstI? AclfLoadCode.CONST_I : AclfLoadCode.CONST_Z));
			if (busBean.load != null) {
				bus.setLoadP(busBean.load.re);
				bus.setLoadQ(busBean.load.im);
			}
		}
		
		if(busBean.shunt != null){
			bus.setShuntY(new Complex(busBean.shunt.re, busBean.shunt.im));
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
			(branchBean.bra_code == BaseBranchBean.BranchCode.ZBR? AclfBranchCode.ZBR: AclfBranchCode.PS_XFORMER)));
		if (branchBean.z != null)
			branch.setZ(branchBean.z.toComplex());
		if (branch.getBranchCode() == AclfBranchCode.LINE) {
			if (branchBean.shunt_y != null)
				branch.setHShuntY(new Complex(0.0, branchBean.shunt_y.im*0.5));
		}
		else {
			AclfXformer xfr = branch.toXfr();
			if (branchBean.ratio != null) {
				xfr.setFromTurnRatio(branchBean.ratio.f);
				xfr.setToTurnRatio(branchBean.ratio.t);
			}
		}
		
		// rating		
		branch.setRatingMva1(branchBean.mvaRatingA);
		branch.setRatingMva2(branchBean.mvaRatingB);
		branch.setRatingMva3(branchBean.mvaRatingC);
	}	
}