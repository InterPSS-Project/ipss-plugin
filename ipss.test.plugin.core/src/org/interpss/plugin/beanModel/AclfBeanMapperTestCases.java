 /*
  * @(#)CR_UserTestCases.java   
  *
  * Copyright (C) 2008 www.interpss.org
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
  * @Date 02/15/2008
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.plugin.beanModel;

import static org.interpss.CorePluginFunction.AclfResultBusStyle;
import static org.junit.Assert.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.datamodel.bean.aclf.AclfNetBean;
import org.interpss.datamodel.bean.aclf.AclfNetResultBean;
import org.interpss.display.AclfOutFunc;
import org.interpss.mapper.bean.aclf.AclfBean2NetMapper;
import org.interpss.mapper.bean.aclf.AclfNet2BeanMapper;
import org.interpss.mapper.bean.aclf.AclfNet2ResultBeanMapper;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfSwingBus;
import com.interpss.simu.util.sample.SampleCases;

public class AclfBeanMapperTestCases extends CorePluginTestSetup {
	@Test
	public void testCase1() throws Exception {
  		AclfNetwork net = CoreObjectFactory.createAclfNetwork();
		SampleCases.load_LF_5BusSystem(net);
		
		// map AclfNet to AclfNetBean
		AclfNetBean netBean = new AclfNet2BeanMapper().map2Model(net);		
		
		// map AclfNetBean back to an AclfNet object
		AclfNetwork aclfNet = new AclfBean2NetMapper()
			.map2Model(netBean)
			.getAclfNet();
		
	  	aclfNet.accept(CoreObjectFactory.createLfAlgoVisitor());
  		//System.out.println(net.net2String());
		//System.out.println(AclfOutFunc.loadFlowSummary(aclfNet));
  		System.out.println(AclfResultBusStyle.f(aclfNet));
		
  		assertTrue(aclfNet.isLfConverged());	
  		
  		AclfBus swingBus = aclfNet.getBus("5");
		AclfSwingBus swing = swingBus.toSwingBus();
  		//System.out.println(swing.getGenResults(UnitType.PU, net.getBaseKva()));
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()-2.57943)<0.0001);
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary()-2.2994)<0.0001);
		
		AclfNetResultBean aclfResult = new AclfNet2ResultBeanMapper()
				.map2Model(aclfNet);
/*		
------------------------------------------------------------------------------------------------------------------------------------------
 Bus ID             Bus Voltage         Generation           Load             To             Branch P+jQ          Xfr Ratio   PS-Xfr Ang
              baseKV    Mag   Ang     (mW)    (mVar)    (mW)    (mVar)      Bus ID      (mW)    (mVar)   (kA)   (From)  (To) (from)   (to)
------------------------------------------------------------------------------------------------------------------------------------------
1             13.800  0.8622  -4.8     0.00     0.00   160.00    80.00  2             -146.62   -40.91   7.387 
                                                                        3              -13.38   -39.09   2.005 
2             13.800  1.0779  17.9     0.00     0.00   200.00   100.00  1              158.45    67.26   7.387 
                                                                        4             -500.00  -142.82  20.183   1.05       
                                                                        3              141.55   -24.43   5.575 
3             13.800  1.0364  -4.3     0.00     0.00   370.00   130.00  1               15.68    47.13   2.005 
                                                                        2             -127.74    20.32   5.575 
                                                                        5             -257.94  -197.45  13.113   1.05       
4              1.000  1.0500  21.8   500.00   181.31     0.00     0.00  2              500.00   181.31  278.52          1.05
5              4.000  1.0500   0.0   257.94   229.94     0.00     0.00  3              257.94   229.94  45.239          1.05
------------------------------------------------------------------------------------------------------------------------------------------	
*/		
		//System.out.println(new Gson().toJson(aclfResult));
	}
	
	@Test
	public void testCase2() throws Exception {
  		AclfNetwork net = CoreObjectFactory.createAclfNetwork();
		SampleCases.load_LF_5BusSystem(net);
		
		// map AclfNet to AclfNetBean
		AclfNetBean netBean = new AclfNet2BeanMapper().map2Model(net);		
		
		// map AclfNetBean back to an AclfNet object
		AclfNetwork aclfNet = new AclfBean2NetMapper()
			.map2Model(netBean)
			.getAclfNet();
		
		// map AclfNet to AclfNetBean
		AclfNetBean netBean1 = new AclfNet2BeanMapper().map2Model(aclfNet);		

		/*
		 * compare two AclfNetBean objects
		 * 
		 *    netBean - mapped from the original AclfNet object net
		 *    netBean1 - mapped from aclfNet object, which is mapped from the netBean object
		 */
		assertTrue(netBean1.compareTo(netBean) == 0);
	}
}

