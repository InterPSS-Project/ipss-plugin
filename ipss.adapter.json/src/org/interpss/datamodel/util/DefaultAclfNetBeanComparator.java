/*
 * @(#)DefaultAclfNetBeanComparator.java   
 *
 * Copyright (C) 2008-2019 www.interpss.org
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
 * @Date 12/10/2019
 * 
 *   Revision History
 *   ================
 *
 */
package org.interpss.datamodel.util;

import org.interpss.datamodel.bean.aclf.AclfNetBean;
import org.interpss.datamodel.bean.base.DefaultExtBean;

/**
 * Default AclfNet comparator implementation.
 * 
 * @author mzhou
 *
 */
public class DefaultAclfNetBeanComparator extends BaseNetBeanComparator<AclfNetBean<DefaultExtBean,DefaultExtBean,DefaultExtBean>> {
	
	public DefaultAclfNetBeanComparator(CompareLog opt) {
		super(opt);
	}
	
	@Override public int compare(AclfNetBean<DefaultExtBean,DefaultExtBean,DefaultExtBean> net1, AclfNetBean<DefaultExtBean,DefaultExtBean,DefaultExtBean> net2) {
		super.compare(net1, net2);
		
		return net1.compareTo(net2);
	}	
}
