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

import static org.junit.Assert.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.datamodel.bean.aclf.AclfNetBean;
import org.interpss.datamodel.mapper.aclf.AclfBean2AclfNetMapper;
import org.interpss.datamodel.mapper.aclf.AclfNet2AclfBeanMapper;
import org.interpss.datamodel.mapper.base.BaseAclfBean2AclfNetMapper;
import org.interpss.datamodel.mapper.base.BaseAclfNet2AclfBeanMapper;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adj.PSXfrPControl;
import com.interpss.core.aclf.adpter.AclfSwingBusAdapter;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.funcImpl.AclfFunction;
import com.interpss.simu.util.sample.SampleTestingCases;

public class PSXfrPControlTest extends CorePluginTestSetup {
	
	@Test
	public void baseCaseNoControlTest() throws InterpssException {
		AclfNetwork aclfNet = SampleTestingCases.sample3BusPSXfr();
		//System.out.println(aclfNet.net2String());
		
		// map AclfNet to AclfNetBean
		AclfNetBean netBean = new AclfNet2AclfBeanMapper().map2Model(aclfNet);

		// map AclfNetBean back to an AclfNet object
		AclfNetwork net = new AclfBean2AclfNetMapper().map2Model(netBean)
				.getAclfNet();
		
		//System.out.println(net.net2String());

		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	algo.loadflow();
  		//System.out.println(net.net2String());

  		AclfBus swingBus = (AclfBus)net.getBus("0001");
		AclfSwingBusAdapter swing = swingBus.toSwingBus();
		//     gen       : 0.2 + 0.61i pu   20,230.59 + 61,421.94i kva
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()-0.20230)<0.0001);
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary()-0.61421)<0.0001);

		AclfBus swingBus2 = (AclfBus)net.getBus("0003");
		AclfSwingBusAdapter swing2 = swingBus2.toSwingBus();
		//     gen       : 0.86 + 0.31i pu   86,047.05 + 31,133.37i kva
		assertTrue(Math.abs(swing2.getGenResults(UnitType.PU).getImaginary()-0.31133)<0.0001);
	}

	@Test
	public void baseCaseBeanModelVerification() throws Exception {
		AclfNetwork net = SampleTestingCases.sample3BusPSXfr();		
		
		// map AclfNet to AclfNetBean
		AclfNetBean netBean = new AclfNet2AclfBeanMapper().map2Model(net);

		// map AclfNetBean back to an AclfNet object
		AclfNetwork aclfNet = new AclfBean2AclfNetMapper().map2Model(netBean)
				.getAclfNet();

		// map AclfNet to AclfNetBean
		AclfNetBean netBean1 = new AclfNet2AclfBeanMapper().map2Model(aclfNet);

		/*
		 * compare two AclfNetBean objects
		 * 
		 * netBean - mapped from the original AclfNet object net netBean1 -
		 * mapped from aclfNet object, which is mapped from the netBean object
		 */
		assertTrue(netBean1.compareTo(netBean) == 0);		
		
	}		
	
	@Test	
	public void fromSideCaseTest_Bean() throws Exception {				
		
		AclfNetwork aclfNet = SampleTestingCases.sample3BusPSXfrPControl();
		//System.out.println(aclfNet.net2String());
		// map AclfNet to AclfNetBean
		AclfNetBean netBean = new AclfNet2AclfBeanMapper().map2Model(aclfNet);

		// map AclfNetBean back to an AclfNet object
		AclfNetwork net = new AclfBean2AclfNetMapper().map2Model(netBean)
				.getAclfNet();
				
		//System.out.println(net.net2String());		
				
		assertTrue("", AclfFunction.nOfPSXfrPControl.apply(net) == 1);
		
		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	algo.loadflow();
  		//System.out.println(net.net2String());

  		AclfBus swingBus = (AclfBus)net.getBus("0001");
		AclfSwingBusAdapter swing = swingBus.toSwingBus();
//		//     gen       : 0.4 + 0.51i pu   39,997.07 + 50,711.88i kva
		//System.out.println(swing.getGenResults(UnitType.PU).getReal());
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()- 0.40026)<0.0001);
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary()-0.50697)<0.0001);

		PSXfrPControl psxfr = net.getBranch("0001->0002(1)").getPSXfrPControl(); 
		assertTrue(Math.abs(psxfr.getParentBranch().getFromPSXfrAngle()-Math.toRadians(1.89023))<0.0001);
		// PSXfr flow controlled to be 0.4
		assertTrue(Math.abs(psxfr.getParentBranch().powerFrom2To().getReal()-0.40026)<0.0001);
	}
	
	
	@Test
	public void fromSideCaseTestBeanModelVerification() throws Exception {
		AclfNetwork net = SampleTestingCases.sample3BusPSXfrPControl();	
		
		// map AclfNet to AclfNetBean
		AclfNetBean netBean = new AclfNet2AclfBeanMapper().map2Model(net);

		// map AclfNetBean back to an AclfNet object
		AclfNetwork aclfNet = new AclfBean2AclfNetMapper().map2Model(netBean)
				.getAclfNet();

		// map AclfNet to AclfNetBean
		AclfNetBean netBean1 = new AclfNet2AclfBeanMapper().map2Model(aclfNet);

		/*
		 * compare two AclfNetBean objects
		 * 
		 * netBean - mapped from the original AclfNet object net netBean1 -
		 * mapped from aclfNet object, which is mapped from the netBean object
		 */
		assertTrue(netBean1.compareTo(netBean) == 0);		
		
	}
	
	
}

