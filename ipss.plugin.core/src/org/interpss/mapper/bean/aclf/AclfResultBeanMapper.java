/*
 * @(#) AclfResultBeanMapper.java   
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
import org.interpss.datamodel.bean.aclf.AclfBranchResultBean;
import org.interpss.datamodel.bean.aclf.AclfBusBean;
import org.interpss.datamodel.bean.aclf.AclfNetResultBean;
import org.interpss.datamodel.bean.datatype.BranchValueBean;
import org.interpss.datamodel.bean.datatype.ComplexBean;
import org.interpss.datamodel.bean.datatype.MismatchResultBean;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.Number2String;

import com.interpss.common.exp.InterpssException;
import com.interpss.common.mapper.AbstractMapper;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfXformer;
import com.interpss.core.algo.AclfMethod;
import com.interpss.core.datatype.Mismatch;

/**
 * mapper implementation to map AclfNetwork object to AclfNetResultBean
 * 
 * @author mzhou
 */
public class AclfResultBeanMapper extends AbstractMapper<AclfNetwork, AclfNetResultBean> {
	/**
	 * constructor
	 */
	public AclfResultBeanMapper() {
	}
	
	/**
	 * map into store in the AclfNetBean object into simuCtx object
	 * 
	 * @param netBean AclfNetBean object
	 * @return SimuContext object
	 */
	@Override public AclfNetResultBean map2Model(AclfNetwork aclfNet) throws InterpssException {
		AclfNetResultBean aclfResult = new AclfNetResultBean();

		map2Model(aclfNet, aclfResult);
		
		return aclfResult;
	}	
	
	/**
	 * map the AclfNetBean object into simuCtx object
	 * 
	 * @param netBean an AclfNetBean object, representing a aclf base network
	 * @param simuCtx
	 */
	@Override public boolean map2Model(AclfNetwork aclfNet, AclfNetResultBean aclfResult) {
		boolean noError = true;
		
		aclfResult.base_kva = aclfNet.getBaseKva();
		aclfResult.lf_converge = aclfNet.isLfConverged();
		
		MismatchResultBean misBean = new MismatchResultBean();
		Mismatch mis = aclfNet.maxMismatch(AclfMethod.NR);
		aclfResult.max_mis = misBean;
		misBean.err = new ComplexBean(format(mis.maxMis.getReal()), format(mis.maxMis.getImaginary()));
		misBean.p_bus_id = mis.maxPBus.getId(); 
		misBean.q_bus_id = mis.maxQBus.getId();
		
		Complex gen = aclfNet.totalGeneration(UnitType.PU);
		Complex load = aclfNet.totalLoad(UnitType.PU);
		Complex loss = aclfNet.totalLoss(UnitType.PU);
		aclfResult.gen = new ComplexBean(format(gen));
		aclfResult.load = new ComplexBean(format(load));
		aclfResult.loss = new ComplexBean(format(loss));
		
		/*BaseNetBeanMapper mapper = new BaseNetBeanMapper();
		mapper.map2Model(aclfNet, aclfResult);	*/	
		
		for (AclfBus bus : aclfNet.getBusList()) {
			AclfBusBean bean = new AclfBusBean();
			aclfResult.bus_list.add(bean);
			mapBaseBus(bus, bean);
		}
		
		for (AclfBranch branch : aclfNet.getBranchList()) {
			AclfBranchResultBean bean = new AclfBranchResultBean();
			aclfResult.branch_list.add(bean);
			mapBaseBranch(branch, bean);
		}
		

		return noError;
	}	
	
	private void mapBaseBus(AclfBus bus, AclfBusBean bean) {
		bean.number = bus.getNumber();
		bean.id = bus.getId();
		bean.name = bus.getName();
		bean.status = 1;
		boolean status = bus.isActive();
		if(!status)
			bean.status = 0;
		bean.base_v = bus.getBaseVoltage()/1000;
		bean.v_mag = format(bus.getVoltageMag());
		bean.v_ang = format(bus.getVoltageAng(UnitType.Deg));
		bean.vmax = format(bus.getVLimit().getMax()) == 0? bean.vmax : format(bus.getVLimit().getMax());
		bean.vmin = format(bus.getVLimit().getMin()) == 0? bean.vmin : format(bus.getVLimit().getMin());

		bean.gen_code = bus.isGenPQ() || !bus.isGen() ? AclfBusBean.GenCode.PQ :
			(bus.isGenPV() ? AclfBusBean.GenCode.PV : AclfBusBean.GenCode.Swing);
				
		Complex gen = bus.getGenResults();
		bean.gen = new ComplexBean(format(gen));

		bean.load_code = bus.isConstPLoad() ? AclfBusBean.LoadCode.ConstP :
			(bus.isConstZLoad() ? AclfBusBean.LoadCode.ConstZ : AclfBusBean.LoadCode.ConstI);

		Complex load = bus.getLoadResults();
		bean.load = new ComplexBean(format(load));
		
		Complex sh = bus.getShuntY();
		bean.shunt = new ComplexBean(format(sh));
		
		bean.area = (long) 1;
		bean.zone = (long) 1;
		if(bus.getArea() !=null)
		bean.area = bus.getArea().getNumber();
		if(bus.getZone() != null)
		bean.zone = bus.getZone().getNumber();
		
	}
	
	private void mapBaseBranch(AclfBranch branch, AclfBranchResultBean bean) {
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
			(branch.isPSXfr() ? BaseBranchBean.BranchCode.PsXfr:BaseBranchBean.BranchCode.ZBR ));
		
		Complex z = branch.getZ();
		bean.z = new ComplexBean(z);
		bean.shunt_y = new ComplexBean(format(new Complex(0, 0)));	
		bean.ratio = new BranchValueBean(1.0,1.0);		
		if (branch.getBranchCode() == AclfBranchCode.LINE) {
			if (branch.getHShuntY() != null)				
				bean.shunt_y = new ComplexBean(format(new Complex(0, branch.getHShuntY().getImaginary()*2)));				
				
		}
		else if (branch.getBranchCode() == AclfBranchCode.XFORMER ||
				branch.getBranchCode() == AclfBranchCode.PS_XFORMER){
			AclfXformer xfr = branch.toXfr();			
			bean.ratio.f = xfr.getFromTurnRatio();
			bean.ratio.t = xfr.getToTurnRatio();			
		}
		
		bean.mvaRatingA = branch.getRatingMva1();
		bean.mvaRatingB = branch.getRatingMva2();
		bean.mvaRatingC = branch.getRatingMva3();
				
		Complex flow = branch.powerFrom2To();
		bean.flow_f2t = new ComplexBean(format(flow));

		flow = branch.powerTo2From();
		bean.flow_t2f = new ComplexBean(format(flow));
		
		Complex loss = branch.loss();
		bean.loss = new ComplexBean(format(loss));
		
		bean.cur = format2(branch.current(UnitType.Amp));
	}	
	
	private Complex format(Complex x) {
		return new Complex(new Double(Number2String.toStr(x.getReal())).doubleValue(), 
				           new Double(Number2String.toStr(x.getImaginary())).doubleValue());
	}

	private double format(double x) {
		return new Double(Number2String.toStr(x)).doubleValue();
	}

	private double format2(double x) {
		return new Double(Number2String.toStr(x, "#0.0#")).doubleValue();
	}
	
}