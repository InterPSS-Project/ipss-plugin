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

import java.util.logging.Level;

import org.ieee.odm.common.ODMLogger;
import org.interpss.CorePluginTestSetup;
import org.interpss.IpssCorePlugin;
import org.interpss.datamodel.bean.BaseBranchBean.BranchCode;
import org.interpss.datamodel.bean.aclf.AclfBranchResultBean;
import org.interpss.datamodel.bean.aclf.AclfBusBean;
import org.interpss.datamodel.bean.aclf.AclfNetBean;
import org.interpss.datamodel.bean.aclf.AclfNetResultBean;
import org.interpss.mapper.bean.aclf.AclfBean2NetMapper;
import org.interpss.mapper.bean.aclf.AclfNet2BeanMapper;
import org.interpss.mapper.bean.aclf.AclfNet2ResultBeanMapper;
import org.interpss.numeric.datatype.LimitType;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.NumericUtil;
import org.interpss.pssl.plugin.IpssAdapter;
import org.interpss.pssl.plugin.IpssAdapter.PsseVersion;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adj.AdjControlType;
import com.interpss.core.aclf.adj.PSXfrPControl;
import com.interpss.core.aclf.adj.TapControl;
import com.interpss.core.aclf.adpter.AclfSwingBus;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.simu.util.sample.SampleCases;

public class PSXfrPControlTest_Inv extends CorePluginTestSetup {
	// TODO: Mike, please try to use the following two input resprectively to run the test.
			// Input 1 will fail. However, I compared the print-out of the network, they are almost
			// identical. Any clue?
	
	//    Direction   : To->From , should be From->To
	
	@Test
	////////////////////////////////////////////////////////////////////
	// Test 1: map back and forth through the Bean model
	////////////////////////////////////////////////////////////////////
	public void fromSideCaseTest_Bean() throws Exception {				
		
		AclfNetwork aclfNet = SampleCases.sample3BusPSXfrPControl();
		//System.out.println(aclfNet.net2String());
		// map AclfNet to AclfNetBean
		AclfNetBean netBean = new AclfNet2BeanMapper().map2Model(aclfNet);

		// map AclfNetBean back to an AclfNet object
		AclfNetwork net = new AclfBean2NetMapper().map2Model(netBean)
				.getAclfNet();
				
		//System.out.println(net.net2String());		
				
		assertTrue(net.getPsXfrPControlList().size() == 1);
		
		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	algo.loadflow();
  		System.out.println(net.net2String());

  		AclfBus swingBus = (AclfBus)net.getBus("0001");
		AclfSwingBus swing = swingBus.toSwingBus();
//		//     gen       : 0.4 + 0.51i pu   39,997.07 + 50,711.88i kva
		System.out.println(swing.getGenResults(UnitType.PU).getReal());
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()- 0.40026)<0.0001);
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary()-0.50697)<0.0001);

		PSXfrPControl psxfr = net.getBranch("0001->0002(1)").getPSXfrPControl(); 
		assertTrue(Math.abs(psxfr.getParentBranch().getFromPSXfrAngle()-Math.toRadians(1.89023))<0.0001);
		// PSXfr flow controlled to be 0.4
		assertTrue(Math.abs(psxfr.getParentBranch().powerFrom2To().getReal()-0.40026)<0.0001);
	}
	
	@Test
	////////////////////////////////////////////////////////////////////
	//   Test2: directly from source
	////////////////////////////////////////////////////////////////////
	public void fromSideCaseTest_NoBean() throws Exception {		
				
		AclfNetwork net = SampleCases.sample3BusPSXfrPControl();		
				
		assertTrue(net.getPsXfrPControlList().size() == 1);
		
		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	algo.loadflow();
  		//System.out.println(net.net2String());

  		AclfBus swingBus = (AclfBus)net.getBus("0001");
		AclfSwingBus swing = swingBus.toSwingBus();
//		//     gen       : 0.4 + 0.51i pu   39,997.07 + 50,711.88i kva
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()- 0.40026)<0.0001);
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary()-0.50697)<0.0001);

		PSXfrPControl psxfr = net.getBranch("0001->0002(1)").getPSXfrPControl(); 
		assertTrue(Math.abs(psxfr.getParentBranch().getFromPSXfrAngle()-Math.toRadians(1.89023))<0.0001);
		// PSXfr flow controlled to be 0.4
		assertTrue(Math.abs(psxfr.getParentBranch().powerFrom2To().getReal()-0.40026)<0.0001);
	}
}

