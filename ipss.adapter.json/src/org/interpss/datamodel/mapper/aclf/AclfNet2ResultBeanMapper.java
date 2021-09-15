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

package org.interpss.datamodel.mapper.aclf;

import org.apache.commons.math3.complex.Complex;
import org.interpss.datamodel.bean.BaseJSONUtilBean;
import org.interpss.datamodel.bean.aclf.AclfBranchResultBean;
import org.interpss.datamodel.bean.aclf.AclfBusResultBean;
import org.interpss.datamodel.bean.aclf.AclfNetResultBean;
import org.interpss.datamodel.bean.datatype.ComplexValueBean;
import org.interpss.datamodel.bean.datatype.MismatchResultBean;
import org.interpss.numeric.datatype.Unit.UnitType;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.datatype.Mismatch;

/**
 * mapper implementation to map AclfNetwork object to AclfNetResultBean
 * 
 * @author mzhou
 */
public class AclfNet2ResultBeanMapper<TBusExt extends BaseJSONUtilBean, TBraExt extends BaseJSONUtilBean> extends BaseAclfNet2BeanMapper<AclfNetResultBean<TBusExt,TBraExt>> {
	/**
	 * constructor
	 */
	public AclfNet2ResultBeanMapper() {
	}
	
	/**
	 * map info stored in the AclfNet object into AclfNetResultBean object
	 * 
	 * @param aclfNet AclfNetwork object
	 * @return AclfNetResultBean object
	 */
	@Override public AclfNetResultBean<TBusExt,TBraExt> map2Model(AclfNetwork aclfNet) throws InterpssException {
		AclfNetResultBean<TBusExt,TBraExt> aclfResult = new AclfNetResultBean<>();

		if (map2Model(aclfNet, aclfResult))
			return aclfResult;
		else
			throw new InterpssException("Error during mapping AclfNetwork object to AclfNetResultBean");
	}	
	
	/**
	 * map the AclfNetwork object into AclfNetResultBean object
	 * 
	 * @param netBean an AclfNetBean object, representing a aclf base network
	 * @param aclfResult
	 */
	@Override public boolean map2Model(AclfNetwork aclfNet, AclfNetResultBean<TBusExt,TBraExt> aclfResult) {
		boolean noError = true;
		
		aclfResult.lf_converge = aclfNet.isLfConverged();
		
		MismatchResultBean misBean = new MismatchResultBean();
		Mismatch mis = aclfNet.maxMismatch(AclfMethodType.NR);
		aclfResult.max_mis = misBean;
		misBean.err = new ComplexValueBean(format(mis.maxMis.getReal()), format(mis.maxMis.getImaginary()));
		misBean.p_bus_id = mis.maxPBus.getId(); 
		misBean.q_bus_id = mis.maxQBus.getId();
		
		Complex gen = aclfNet.totalGeneration(UnitType.PU);
		Complex load = aclfNet.totalLoad(UnitType.PU);
		Complex loss = aclfNet.totalLoss(UnitType.PU);
		aclfResult.gen = new ComplexValueBean(format(gen));
		aclfResult.load = new ComplexValueBean(format(load));
		aclfResult.loss = new ComplexValueBean(format(loss));
		
		aclfResult.base_kva = aclfNet.getBaseKva();			
		
		for (AclfBus bus : aclfNet.getBusList()) {
			AclfBusResultBean<TBusExt> bean = new AclfBusResultBean<>();
			aclfResult.addBusBean(bean);
			mapBaseBus(bus, bean);
		}
		
		for (AclfBranch branch : aclfNet.getBranchList()) {
			AclfBranchResultBean<TBraExt> bean = new AclfBranchResultBean<>();
			aclfResult.addBranchBean(bean);
			mapBaseBranch(branch, bean);
		}

		return noError;
	}	
	
	protected void mapBaseBranch(AclfBranch branch, AclfBranchResultBean<TBraExt> bean) {
		super.mapBaseBranch(branch, bean);
		
		Complex flow = branch.powerFrom2To();
		bean.flow_f2t = new ComplexValueBean(format(flow));

		flow = branch.powerTo2From();
		bean.flow_t2f = new ComplexValueBean(format(flow));
		
		Complex loss = branch.loss();
		bean.loss = new ComplexValueBean(format(loss));
		
		bean.cur = format2(branch.current(UnitType.Amp));
	}	
}