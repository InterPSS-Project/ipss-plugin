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

package org.interpss.datamodel.mapper.base;

import org.apache.commons.math3.complex.Complex;
import org.interpss.datamodel.bean.aclf.AclfBranchBean;
import org.interpss.datamodel.bean.aclf.AclfBusBean;
import org.interpss.datamodel.bean.aclf.AclfNetBean;
import org.interpss.datamodel.bean.aclf.BaseAclfNetBean;
import org.interpss.datamodel.bean.aclf.ext.AclfNetResultBean;
import org.interpss.datamodel.bean.base.BaseJSONUtilBean;
import org.interpss.datamodel.bean.datatype.ComplexValueBean;
import org.interpss.datamodel.bean.datatype.MismatchResultBean;
import org.interpss.datamodel.mapper.base.AclfNet2BeanUtilFunc;
import org.interpss.numeric.datatype.Unit.UnitType;

import com.interpss.common.exp.InterpssException;
import com.interpss.common.mapper.AbstractMapper;
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
public abstract class BaseAclfNet2ResultBeanMapper<
					TBusExt extends BaseJSONUtilBean, 
					TBraExt extends BaseJSONUtilBean,
					TNetExt extends BaseJSONUtilBean> extends AbstractMapper<
										AclfNetwork, 
                                        BaseAclfNetBean<AclfBusBean<TBusExt>, 
                                                        AclfBranchBean<TBraExt>,
                                                        TBusExt,TBraExt,TNetExt>> {
	/**
	 * constructor
	 */
	public BaseAclfNet2ResultBeanMapper() {
	}
	
	/**
	 * map info stored in the AclfNet object into AclfNetResultBean object
	 * 
	 * @param aclfNet AclfNetwork object
	 * @return AclfNetResultBean object
	 */
	@Override public BaseAclfNetBean<AclfBusBean<TBusExt>,AclfBranchBean<TBraExt>,TBusExt,TBraExt,TNetExt> map2Model(AclfNetwork aclfNet) throws InterpssException {
		BaseAclfNetBean<AclfBusBean<TBusExt>,AclfBranchBean<TBraExt>,TBusExt,TBraExt,TNetExt> aclfResult = new BaseAclfNetBean<>();

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
	@Override public boolean map2Model(AclfNetwork aclfNet, BaseAclfNetBean<AclfBusBean<TBusExt>,AclfBranchBean<TBraExt>,TBusExt,TBraExt,TNetExt> aclfBean) {
		boolean noError = true;
		
		super.map2Model(aclfNet, aclfBean);
		
		AclfNetResultBean aclfResult = (AclfNetResultBean)aclfBean.extension;
		
		aclfResult.lf_converge = aclfNet.isLfConverged();
		
		MismatchResultBean misBean = new MismatchResultBean();
		Mismatch mis = aclfNet.maxMismatch(AclfMethodType.NR);
		aclfResult.max_mis = misBean;
		misBean.err = new ComplexValueBean(AclfNet2BeanUtilFunc.format(mis.maxMis.getReal()), AclfNet2BeanUtilFunc.format(mis.maxMis.getImaginary()));
		misBean.p_bus_id = mis.maxPBus.getId(); 
		misBean.q_bus_id = mis.maxQBus.getId();
		
		Complex gen = aclfNet.totalGeneration(UnitType.PU);
		Complex load = aclfNet.totalLoad(UnitType.PU);
		Complex loss = aclfNet.totalLoss(UnitType.PU);
		aclfResult.gen = new ComplexValueBean(AclfNet2BeanUtilFunc.format(gen));
		aclfResult.load = new ComplexValueBean(AclfNet2BeanUtilFunc.format(load));
		aclfResult.loss = new ComplexValueBean(AclfNet2BeanUtilFunc.format(loss));
		
		for (AclfBus bus : aclfNet.getBusList()) {
			AclfBusBean<TBusExt> bean = aclfBean.getBus(bus.getId());
			AclfNet2BeanUtilFunc.mapAclfBusResult(bus, bean);
		}
		
		for (AclfBranch branch : aclfNet.getBranchList()) {
			AclfBranchBean<TBraExt> bean = aclfBean.getBranch(branch.getId());
			AclfNet2BeanUtilFunc.mapAclfBranchResult(branch, bean);
		}

		return noError;
	}	
}