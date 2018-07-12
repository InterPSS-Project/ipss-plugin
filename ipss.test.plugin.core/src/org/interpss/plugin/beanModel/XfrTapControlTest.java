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
import org.interpss.mapper.bean.aclf.AclfBean2NetMapper;
import org.interpss.mapper.bean.aclf.AclfNet2BeanMapper;
import org.interpss.numeric.datatype.LimitType;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adj.AdjControlType;
import com.interpss.core.aclf.adj.TapControl;
import com.interpss.core.aclf.adpter.AclfSwingBus;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.simu.util.sample.SampleCases;

public class XfrTapControlTest extends CorePluginTestSetup {
	
	@Test
	public void testCase1() throws Exception {
		AclfNetwork net = SampleCases.sample2BusXfr();

		AclfBranch branch = net.getBranch("0001->0002(1)");
		TapControl tap = CoreObjectFactory.createTapVControlBusVoltage(branch, 
							AdjControlType.POINT_CONTROL, net, "0002");
		tap.setControlLimit(new LimitType(1.10, 0.9));
		tap.setControlOnFromSide(false);
		//tap.setTapOnFromSide(false);
		tap.setControlSpec(0.90);

		assertTrue(net.getTapControlList().size() == 1);
		
		assertTrue(Math.abs(branch.getToTurnRatio()-1.0)<0.0001);
		
		// map AclfNet to AclfNetBean
		AclfNetBean netBean = new AclfNet2BeanMapper().map2Model(net);

		// map AclfNetBean back to an AclfNet object
		AclfNetwork aclfNet = new AclfBean2NetMapper().map2Model(netBean)
				.getAclfNet();

		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(aclfNet);
	  	algo.loadflow();
  		//System.out.println(net.net2String());

  		AclfBus swingBus = (AclfBus)aclfNet.getBus("0001");
		AclfSwingBus swing = swingBus.toSwingBus();
		//      gen       : 1.12 + 1.03i pu   111,529.19 + 103,059.25i kva
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()-1.1153)<0.0001);
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary()-1.0306)<0.0001);
		
		assertTrue(Math.abs(branch.getToTurnRatio()-1.06717)<0.0001);
		assertTrue(tap.isActive());
		
		//tap = aclfNet.getTapControlList().get(0);
		
		assertTrue(Math.abs(tap.getVcBus().getVoltageMag()-0.9)<0.0001);
		assertTrue(Math.abs(aclfNet.getBus("0002").getVoltageMag()-0.9)<0.0001);
	}
	
	@Test
	public void testCase2() throws Exception {
		AclfNetwork net = SampleCases.sample2BusXfr();

		AclfBranch branch = net.getBranch("0001->0002(1)");
		TapControl tap = CoreObjectFactory.createTapVControlBusVoltage(branch, 
							AdjControlType.POINT_CONTROL, net, "0002");
		tap.setControlLimit(new LimitType(1.10, 0.9));
		tap.setControlOnFromSide(false);
		//tap.setTapOnFromSide(false);
		tap.setControlSpec(0.90);

		assertTrue(net.getTapControlList().size() == 1);
		
		assertTrue(Math.abs(branch.getToTurnRatio()-1.0)<0.0001);
		
		// map AclfNet to AclfNetBean
		AclfNetBean netBean = new AclfNet2BeanMapper().map2Model(net);

		// map AclfNetBean back to an AclfNet object
		AclfNetwork aclfNet = new AclfBean2NetMapper().map2Model(netBean)
				.getAclfNet();

		// map AclfNet to AclfNetBean
		AclfNetBean netBean1 = new AclfNet2BeanMapper().map2Model(aclfNet);

		/*
		 * compare two AclfNetBean objects
		 * 
		 * netBean - mapped from the original AclfNet object net netBean1 -
		 * mapped from aclfNet object, which is mapped from the netBean object
		 */
		assertTrue(netBean1.compareTo(netBean) == 0);
		
		
	}
	
	@Test
	public void testCase3() throws Exception {
		AclfNetwork net = SampleCases.sample2BusXfr();

		AclfBranch branch = net.getBranch("0001->0002(1)");
		TapControl tap = CoreObjectFactory.createTapVControlBusVoltage(branch, 
							AdjControlType.POINT_CONTROL, net, "0002");
		tap.setControlLimit(new LimitType(1.10, 0.9));
		tap.setControlOnFromSide(false);
		//tap.setTapOnFromSide(false);
		tap.setControlSpec(0.90);
		tap.setStatus(true);

		assertTrue(net.getTapControlList().size() == 1);
		
		assertTrue(Math.abs(branch.getToTurnRatio()-1.0)<0.0001);

		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	algo.loadflow();
  		System.out.println(net.net2String());

  		AclfBus swingBus = (AclfBus)net.getBus("0001");
		AclfSwingBus swing = swingBus.toSwingBus();
		//      gen       : 1.12 + 1.03i pu   111,529.19 + 103,059.25i kva
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getReal()-1.1153)<0.0001);
		assertTrue(Math.abs(swing.getGenResults(UnitType.PU).getImaginary()-1.0306)<0.0001);

		assertTrue(Math.abs(branch.getToTurnRatio()-1.06717)<0.0001);
		System.out.println("branch ratio: "+ branch.getFromTurnRatio()+"; t: "+branch.getToTurnRatio());
		assertTrue(tap.isActive());

		assertTrue(Math.abs(tap.getVcBus().getVoltageMag()-0.9)<0.0001);
		System.out.println("tap: "+tap.getVcBus().getVoltageMag());
		assertTrue(Math.abs(net.getBus("0002").getVoltageMag()-0.9)<0.0001);
		System.out.println("bus2: "+net.getBus("0002").getVoltageMag());
	}
}

